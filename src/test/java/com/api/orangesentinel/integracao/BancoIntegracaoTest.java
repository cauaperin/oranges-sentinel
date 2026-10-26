package com.api.orangesentinel.integracao;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

/**
 * Fluxo do banco simulado: cadastro, depósito, chave Pix, transferência, extrato, idempotência e autorização.
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
	void rotasProtegidasPorPerfil() throws Exception {
		Cliente cliente = registrarCliente("Gabriela");

		mvc.perform(get("/api/contas")).andExpect(status().isUnauthorized());
		mvc.perform(autenticado(get("/api/fraude/contas"), cliente.token())).andExpect(status().isForbidden());
		mvc.perform(autenticado(get("/api/admin/usuarios"), cliente.token())).andExpect(status().isForbidden());

		String admin = login(ADMIN_EMAIL, ADMIN_SENHA);
		mvc.perform(autenticado(get("/api/fraude/contas"), admin)).andExpect(status().isOk());
		mvc.perform(autenticado(get("/api/admin/parametros-risco"), admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.limiarLaranja").value(80));
	}

	@Test
	void validacaoDeEntrada() throws Exception {
		mvc.perform(post("/api/auth/registro").contentType("application/json").content("""
				{"nome":"","cpf":"123","email":"invalido","senha":"1"}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.erros.cpf").exists())
			.andExpect(jsonPath("$.erros.email").exists());
	}

}
