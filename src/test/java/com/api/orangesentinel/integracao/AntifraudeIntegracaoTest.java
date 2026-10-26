package com.api.orangesentinel.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

/**
 * Fluxo completo do motor: simulação → análise do grafo → avaliação → Pix retido em tempo real → decisão do
 * analista (estorno e bloqueio).
 */
class AntifraudeIntegracaoTest extends IntegracaoTestBase {

	private static final long SEED = 42;

	@AfterEach
	void limparSimulacao() throws Exception {
		mvc.perform(autenticado(delete("/api/admin/simulacao"), login(ADMIN_EMAIL, ADMIN_SENHA)));
	}

	@Test
	void detectaLaranjasRetemPixEEstornaAposConfirmacao() throws Exception {
		String admin = login(ADMIN_EMAIL, ADMIN_SENHA);

		mvc.perform(json(post("/api/admin/simulacao"), admin, """
				{"seed": %d, "clientesNormais": 80, "redesFunil": 2, "redesDispersao": 1, "cadeias": 1, "ciclos": 1}"""
			.formatted(SEED))).andExpect(status().isCreated()).andExpect(jsonPath("$.contasLaranja").isNumber());

		mvc.perform(autenticado(post("/api/fraude/analises"), admin))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.laranjas").value(org.hamcrest.Matchers.greaterThan(0)))
			.andExpect(jsonPath("$.ciclos").value(org.hamcrest.Matchers.greaterThan(0)));

		String avaliacao = mvc.perform(autenticado(get("/api/admin/simulacao/avaliacao"), admin))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(JsonPath.<Double>read(avaliacao, "$.laranja.recall")).isGreaterThanOrEqualTo(0.8);
		assertThat(JsonPath.<Double>read(avaliacao, "$.laranja.precisao")).isGreaterThanOrEqualTo(0.8);

		// Conta laranja com maior score, sua chave Pix (e-mail) e o grafo ao redor dela
		String ranking = mvc.perform(autenticado(get("/api/fraude/contas?classificacao=LARANJA"), admin))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long laranjaId = JsonPath.<Number>read(ranking, "$.content[0].contaId").longValue();
		mvc.perform(autenticado(get("/api/fraude/contas/{id}", laranjaId), admin))
			.andExpect(jsonPath("$.conta.status").value("SOB_ANALISE"))
			.andExpect(jsonPath("$.ultimaAnalise.motivos").isNotEmpty());
		mvc.perform(autenticado(get("/api/fraude/contas/{id}/grafo", laranjaId), admin))
			.andExpect(jsonPath("$.nodes").isNotEmpty())
			.andExpect(jsonPath("$.edges").isNotEmpty());
		mvc.perform(autenticado(get("/api/fraude/ciclos"), admin)).andExpect(jsonPath("$").isNotEmpty());

		// Tempo real: Pix de cliente comum para a laranja fica RETIDO
		Cliente vitima = registrarCliente("Vitima");
		mvc.perform(json(post("/api/contas/{id}/deposito", vitima.contaId()), vitima.token(), """
				{"valor": 500.00}""")).andExpect(status().isCreated());
		String chaveLaranja = "sim%d.%d@orangesentinel.sim".formatted(laranjaId, SEED);
		String pix = """
				{"contaOrigemId": %d, "chaveDestino": "%s", "valor": 200.00}"""
			.formatted(vitima.contaId(), chaveLaranja);
		mvc.perform(json(post("/api/pix"), vitima.token(), pix))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("RETIDA"));
		mvc.perform(autenticado(get("/api/contas/{id}", vitima.contaId()), vitima.token()))
			.andExpect(jsonPath("$.saldo").value(300.0));

		// Analista confirma a fraude: conta bloqueada e Pix estornado
		String alertas = mvc.perform(autenticado(get("/api/fraude/alertas?status=ABERTO&size=200"), admin))
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<Map<String, Object>> abertos = JsonPath.read(alertas, "$.content[?(@.contaId == %d)]".formatted(laranjaId));
		assertThat(abertos).hasSize(1);
		long alertaId = ((Number) abertos.getFirst().get("id")).longValue();
		mvc.perform(json(patch("/api/fraude/alertas/{id}", alertaId), admin, """
				{"decisao": "CONFIRMADO", "observacao": "Conta laranja confirmada"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CONFIRMADO"));

		mvc.perform(autenticado(get("/api/contas/{id}", vitima.contaId()), vitima.token()))
			.andExpect(jsonPath("$.saldo").value(500.0));
		mvc.perform(autenticado(get("/api/contas/{id}/extrato", vitima.contaId()), vitima.token()))
			.andExpect(jsonPath("$.content[0].status").value("ESTORNADA"));

		// Conta bloqueada: novo Pix é recusado sem movimentar dinheiro
		mvc.perform(json(post("/api/pix"), vitima.token(), pix))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("RECUSADA"));
		mvc.perform(autenticado(get("/api/contas/{id}", vitima.contaId()), vitima.token()))
			.andExpect(jsonPath("$.saldo").value(500.0));
	}

	@Test
	void simulacaoDuplicadaEhRejeitada() throws Exception {
		String admin = login(ADMIN_EMAIL, ADMIN_SENHA);
		String body = """
				{"seed": 1, "clientesNormais": 10, "redesFunil": 0, "redesDispersao": 0, "cadeias": 0, "ciclos": 0}""";
		mvc.perform(json(post("/api/admin/simulacao"), admin, body)).andExpect(status().isCreated());
		mvc.perform(json(post("/api/admin/simulacao"), admin, body)).andExpect(status().isConflict());
	}

}
