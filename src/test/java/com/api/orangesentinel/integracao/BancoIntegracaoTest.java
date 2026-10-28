package com.api.orangesentinel.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

/**
 * Fluxo do banco simulado: conta inicial, depósito, chave Pix, transferência, extrato, idempotência e autorização.
 */
class BancoIntegracaoTest extends IntegracaoTestBase {

	@Test
	void pixEntreClientesAtualizaSaldosEExtrato() throws Exception {
		Cliente ana = registrarCliente("Ana");
		Cliente bruno = registrarCliente("Bruno");

		mvc.perform(json(post("/api/contas/{id}/deposito", ana.contaId()), ana.token(), """
				{"valor": 1000.00, "descricao": "Depósito inicial"}"""))
			.andExpect(status().isCreated());
		mvc.perform(json(post("/api/contas/{id}/chaves-pix", bruno.contaId()), bruno.token(), """
				{"tipo": "EMAIL", "valor": "%s"}""".formatted(bruno.email())))
			.andExpect(status().isCreated());

		String pix = """
				{"contaOrigemId": %d, "chaveDestino": "%s", "valor": 300.00, "descricao": "Almoço"}"""
			.formatted(ana.contaId(), bruno.email());
		String resp = mvc.perform(json(post("/api/pix"), ana.token(), pix).header("Idempotency-Key", "pix-ana-1"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("CONCLUIDA"))
			.andExpect(jsonPath("$.direcao").value("SAIDA"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Number id = JsonPath.read(resp, "$.id");

		// Reenvio com a mesma Idempotency-Key não duplica a transferência
		mvc.perform(json(post("/api/pix"), ana.token(), pix).header("Idempotency-Key", "pix-ana-1"))
			.andExpect(jsonPath("$.id").value(id.longValue()));

		mvc.perform(autenticado(get("/api/contas/{id}", ana.contaId()), ana.token()))
			.andExpect(jsonPath("$.saldo").value(700.0));
		mvc.perform(autenticado(get("/api/contas/{id}", bruno.contaId()), bruno.token()))
			.andExpect(jsonPath("$.saldo").value(300.0));
		mvc.perform(autenticado(get("/api/contas/{id}/extrato", ana.contaId()), ana.token()))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content[0].tipo").value("PIX"));

		// A chave Pix do tipo CPF usa o CPF copiado do titular na abertura da conta
		mvc.perform(json(post("/api/contas/{id}/chaves-pix", bruno.contaId()), bruno.token(), """
				{"tipo": "CPF"}""")).andExpect(status().isCreated());
	}

	@Test
	void saldoInsuficienteEhRecusado() throws Exception {
		Cliente carla = registrarCliente("Carla");
		Cliente diego = registrarCliente("Diego");
		mvc.perform(json(post("/api/contas/{id}/chaves-pix", diego.contaId()), diego.token(), """
				{"tipo": "ALEATORIA"}""")).andExpect(status().isCreated());
		String chave = JsonPath.read(mvc.perform(autenticado(get("/api/contas/{id}/chaves-pix", diego.contaId()),
				diego.token())).andReturn().getResponse().getContentAsString(), "$[0].valor");

		mvc.perform(json(post("/api/pix"), carla.token(), """
				{"contaOrigemId": %d, "chaveDestino": "%s", "valor": 10.00}""".formatted(carla.contaId(), chave)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.detail").value("Saldo insuficiente"));
	}

	@Test
	void clienteNaoAcessaContaDeOutro() throws Exception {
		Cliente eduarda = registrarCliente("Eduarda");
		Cliente felipe = registrarCliente("Felipe");

		mvc.perform(autenticado(get("/api/contas/{id}", felipe.contaId()), eduarda.token()))
			.andExpect(status().isForbidden());
		mvc.perform(json(post("/api/contas/{id}/deposito", felipe.contaId()), eduarda.token(), """
				{"valor": 10.00}""")).andExpect(status().isForbidden());
	}

	@Test
	void clienteAbreSegundaContaComOsDadosDoToken() throws Exception {
		Cliente gabriela = registrarCliente("Gabriela");

		mvc.perform(json(post("/api/contas"), gabriela.token(), """
				{"tipo": "POUPANCA"}""")).andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("POUPANCA"));
		mvc.perform(autenticado(get("/api/contas"), gabriela.token())).andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void rotasExigemTokenValido() throws Exception {
		mvc.perform(get("/api/contas")).andExpect(status().isUnauthorized());
		mvc.perform(autenticado(get("/api/contas"), "token.invalido.aqui")).andExpect(status().isUnauthorized());
		// Rotas que não existem mais neste serviço (auth e antifraude moraram em outros)
		Cliente helena = registrarCliente("Helena");
		mvc.perform(autenticado(get("/api/fraude/contas"), helena.token())).andExpect(status().isNotFound());
	}

	@Test
	void validacaoDeEntrada() throws Exception {
		Cliente igor = registrarCliente("Igor");
		mvc.perform(json(post("/api/pix"), igor.token(), """
				{"contaOrigemId": %d, "chaveDestino": "", "valor": -5}""".formatted(igor.contaId())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.erros.chaveDestino").exists())
			.andExpect(jsonPath("$.erros.valor").exists());
	}

	@Test
	void pixEnviaAoMotorOsRecebimentosRecentesDaOrigem() throws Exception {
		Cliente joao = registrarCliente("Joao");
		Cliente karla = registrarCliente("Karla");
		Cliente luis = registrarCliente("Luis");
		for (Cliente c : new Cliente[] { joao, karla, luis }) {
			mvc.perform(json(post("/api/contas/{id}/chaves-pix", c.contaId()), c.token(), """
					{"tipo": "EMAIL", "valor": "%s"}""".formatted(c.email()))).andExpect(status().isCreated());
		}
		mvc.perform(json(post("/api/contas/{id}/deposito", joao.contaId()), joao.token(), """
				{"valor": 500.00}""")).andExpect(status().isCreated());
		mvc.perform(json(post("/api/pix"), joao.token(), """
				{"contaOrigemId": %d, "chaveDestino": "%s", "valor": 200.00}""".formatted(joao.contaId(), karla.email())))
			.andExpect(status().isCreated());

		// Karla acabou de receber de João e agora repassa a Luis: o motor recebe esse recebimento no pedido
		motor.pedidos.clear();
		mvc.perform(json(post("/api/pix"), karla.token(), """
				{"contaOrigemId": %d, "chaveDestino": "%s", "valor": 150.00}""".formatted(karla.contaId(), luis.email())))
			.andExpect(status().isCreated());

		assertThat(motor.pedidos).hasSize(1);
		var pedido = motor.pedidos.getFirst();
		assertThat(pedido.origem().id()).isEqualTo(karla.contaId());
		assertThat(pedido.destino().id()).isEqualTo(luis.contaId());
		assertThat(pedido.recebimentosOrigem()).singleElement().satisfies(r -> {
			assertThat(r.remetenteId()).isEqualTo(joao.contaId());
			assertThat(r.valor()).isEqualByComparingTo("200.00");
		});
	}

}
