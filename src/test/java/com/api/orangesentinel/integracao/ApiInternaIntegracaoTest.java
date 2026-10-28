package com.api.orangesentinel.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

import com.api.orangesentinel.client.MotorDtos.Acao;
import com.api.orangesentinel.client.MotorDtos.AvaliacaoPixResponse;

/**
 * API {@code /internal/**} usada pelo auth e pelo motor: conta inicial, consulta, risco, decisão do analista e
 * simulador.
 */
class ApiInternaIntegracaoTest extends IntegracaoTestBase {

	@Test
	void apiInternaExigeAChaveCompartilhada() throws Exception {
		String corpo = """
				{"ids": [1]}""";
		mvc.perform(post("/internal/contas/consulta").contentType("application/json").content(corpo))
			.andExpect(status().isForbidden());
		mvc.perform(post("/internal/contas/consulta").header("X-Internal-Key", "errada")
			.contentType("application/json")
			.content(corpo)).andExpect(status().isForbidden());
		// Um token de usuário comum não substitui a chave interna
		Cliente cliente = registrarCliente("Mara");
		mvc.perform(autenticado(post("/internal/contas/consulta"), cliente.token()).contentType("application/json")
			.content(corpo)).andExpect(status().isForbidden());
	}

	@Test
	void contaInicialEIdempotenteEConsultavel() throws Exception {
		Cliente nina = registrarCliente("Nina");
		String repetida = """
				{"usuarioId": %d, "titular": "Nina", "cpf": "12345678901"}""".formatted(nina.usuarioId());
		mvc.perform(jsonInterno(post("/internal/contas/inicial"), repetida))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(nina.contaId()));
		mvc.perform(autenticado(get("/api/contas"), nina.token())).andExpect(jsonPath("$.length()").value(1));

		mvc.perform(jsonInterno(post("/internal/contas/consulta"), """
				{"ids": [%d, 999999999]}""".formatted(nina.contaId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].titular").value("Nina"))
			.andExpect(jsonPath("$[0].usuarioId").value(nina.usuarioId()))
			.andExpect(jsonPath("$[0].status").value("ATIVA"));
	}

	@Test
	void riscoDoMotorAparecenoRankingESuspendeContaLaranja() throws Exception {
		Cliente otavio = registrarCliente("Otavio");
		mvc.perform(jsonInterno(put("/internal/contas/riscos"), """
				[{"contaId": %d, "score": 93, "classificacao": "LARANJA", "analisadoEm": "%s", "sobAnalise": true}]"""
			.formatted(otavio.contaId(), Instant.now()))).andExpect(status().isOk());

		mvc.perform(autenticado(get("/api/contas/{id}", otavio.contaId()), otavio.token()))
			.andExpect(jsonPath("$.status").value("SOB_ANALISE"));
		mvc.perform(interno(get("/internal/contas/ranking")).param("classificacao", "LARANJA").param("size", "50"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[?(@.id == %d)].scoreRisco".formatted(otavio.contaId())).value(93));
	}

	@Test
	void decisaoDeFraudeEstornaOuLiberaOsPixRetidos() throws Exception {
		Cliente laranja = registrarCliente("Laranja");
		Cliente vitima = registrarCliente("Vitima");
		Cliente outra = registrarCliente("Outra");
		mvc.perform(json(post("/api/contas/{id}/chaves-pix", laranja.contaId()), laranja.token(), """
				{"tipo": "EMAIL", "valor": "%s"}""".formatted(laranja.email()))).andExpect(status().isCreated());
		for (Cliente c : new Cliente[] { vitima, outra }) {
			mvc.perform(json(post("/api/contas/{id}/deposito", c.contaId()), c.token(), """
					{"valor": 500.00}""")).andExpect(status().isCreated());
		}
		motor.reterPixPara(laranja.contaId());
		String pix = "{\"contaOrigemId\": %d, \"chaveDestino\": \"%s\", \"valor\": 200.00}";
		mvc.perform(json(post("/api/pix"), vitima.token(), pix.formatted(vitima.contaId(), laranja.email())))
			.andExpect(jsonPath("$.status").value("RETIDA"));
		assertThat(JsonPath.<Number>read(conta(vitima), "$.saldo").doubleValue()).isEqualTo(300.0);

		// Falso positivo: o valor é liberado para a conta de destino
		mvc.perform(jsonInterno(post("/internal/contas/{id}/decisao-fraude", laranja.contaId()), """
				{"decisao": "FALSO_POSITIVO", "alertaId": 7}""")).andExpect(jsonPath("$.liberadas").value(1));
		mvc.perform(autenticado(get("/api/contas/{id}/extrato", vitima.contaId()), vitima.token()))
			.andExpect(jsonPath("$.content[0].status").value("CONCLUIDA"));
		assertThat(JsonPath.<Number>read(conta(laranja), "$.saldo").doubleValue()).isEqualTo(200.0);

		// Confirmado: outro Pix retido é estornado à origem e a conta é bloqueada
		mvc.perform(json(post("/api/pix"), outra.token(), pix.formatted(outra.contaId(), laranja.email())))
			.andExpect(jsonPath("$.status").value("RETIDA"));
		mvc.perform(jsonInterno(post("/internal/contas/{id}/decisao-fraude", laranja.contaId()), """
				{"decisao": "CONFIRMADO", "alertaId": 8}""")).andExpect(jsonPath("$.estornadas").value(1));
		assertThat(JsonPath.<Number>read(conta(outra), "$.saldo").doubleValue()).isEqualTo(500.0);
		mvc.perform(autenticado(get("/api/contas/{id}/extrato", outra.contaId()), outra.token()))
			.andExpect(jsonPath("$.content[0].status").value("ESTORNADA"));
		mvc.perform(autenticado(get("/api/contas/{id}", laranja.contaId()), laranja.token()))
			.andExpect(jsonPath("$.status").value("BLOQUEADA"));
	}

	@Test
	void motorPodeRecusarOPix() throws Exception {
		Cliente paulo = registrarCliente("Paulo");
		Cliente quiteria = registrarCliente("Quiteria");
		mvc.perform(json(post("/api/contas/{id}/chaves-pix", quiteria.contaId()), quiteria.token(), """
				{"tipo": "EMAIL", "valor": "%s"}""".formatted(quiteria.email()))).andExpect(status().isCreated());
		mvc.perform(json(post("/api/contas/{id}/deposito", paulo.contaId()), paulo.token(), """
				{"valor": 100.00}""")).andExpect(status().isCreated());
		motor.regra = req -> new AvaliacaoPixResponse(Acao.RECUSAR, null, null,
				List.of("Conta de destino bloqueada para recebimentos"));

		mvc.perform(json(post("/api/pix"), paulo.token(), """
				{"contaOrigemId": %d, "chaveDestino": "%s", "valor": 50.00}""".formatted(paulo.contaId(), quiteria.email())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("RECUSADA"))
			.andExpect(jsonPath("$.motivo").value("Conta de destino bloqueada para recebimentos"));
		assertThat(JsonPath.<Number>read(conta(paulo), "$.saldo").doubleValue()).isEqualTo(100.0);
	}

	@Test
	void simuladorCriaContasETransacoesEAsRemove() throws Exception {
		String contas = """
				[{"usuarioId": 9001, "titular": "Sim Um", "saldo": 1000.00, "criadaEm": "2026-10-01T10:00:00Z",
					"emailChave": "sim1@orangesentinel.sim"},
				 {"usuarioId": 9002, "titular": "Sim Dois", "saldo": 0, "criadaEm": "2026-10-01T10:00:00Z",
					"emailChave": "sim2@orangesentinel.sim"}]""";
		String resp = mvc.perform(jsonInterno(post("/internal/simulacao/contas"), contas))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.ids.length()").value(2))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Number a = JsonPath.read(resp, "$.ids[0]");
		Number b = JsonPath.read(resp, "$.ids[1]");

		String agora = Instant.now().toString();
		mvc.perform(jsonInterno(post("/internal/simulacao/transacoes"), """
				[{"origemId": %d, "destinoId": %d, "valor": 250.00, "tipo": "PIX", "descricao": "sim", "timestamp": "%s"},
				 {"origemId": %d, "destinoId": null, "valor": 100.00, "tipo": "SAQUE", "descricao": "sim", "timestamp": "%s"}]"""
			.formatted(a.longValue(), b.longValue(), agora, b.longValue(), agora))).andExpect(status().isCreated());

		String desde = Instant.now().minusSeconds(3600).toString();
		mvc.perform(interno(get("/internal/transacoes/arestas")).param("desde", desde))
			.andExpect(jsonPath("$[?(@.origem == %d)].destino".formatted(a.longValue())).value(b.intValue()));
		mvc.perform(interno(get("/internal/transacoes/saques")).param("desde", desde))
			.andExpect(jsonPath("$[?(@.contaId == %d)].valor".formatted(b.longValue())).value(100.0));

		mvc.perform(interno(delete("/internal/simulacao")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contasRemovidas").value(2));
		mvc.perform(jsonInterno(post("/internal/contas/consulta"), """
				{"ids": [%d, %d]}""".formatted(a.longValue(), b.longValue()))).andExpect(jsonPath("$.length()").value(0));
	}

}
