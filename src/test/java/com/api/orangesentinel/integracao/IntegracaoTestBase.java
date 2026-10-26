package com.api.orangesentinel.integracao;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

/**
 * Base dos testes de integração: sobe a aplicação completa contra um PostgreSQL real (Testcontainers). Os testes são
 * ignorados automaticamente quando o Docker não está disponível.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegracaoTestBase.Containers.class)
@Testcontainers(disabledWithoutDocker = true)
abstract class IntegracaoTestBase {

	static final String ADMIN_EMAIL = "admin@orangesentinel.dev";

	static final String ADMIN_SENHA = "admin123";

	@Autowired
	MockMvc mvc;

	@TestConfiguration(proxyBeanMethods = false)
	static class Containers {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer("postgres:17-alpine");
		}

	}

	// ------------------------------------------------------------------ helpers

	record Cliente(String token, long contaId, String email) {
	}

	static String cpfAleatorio() {
		return "1" + String.format("%010d", ThreadLocalRandom.current().nextLong(10_000_000_000L));
	}

	Cliente registrarCliente(String nome) throws Exception {
		String email = nome.toLowerCase() + "." + ThreadLocalRandom.current().nextInt(1_000_000) + "@teste.dev";
		String body = """
				{"nome":"%s","cpf":"%s","email":"%s","senha":"senha123"}""".formatted(nome, cpfAleatorio(), email);
		String resp = mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String token = JsonPath.read(resp, "$.token");
		String contas = mvc.perform(autenticado(get("/api/contas"), token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Number contaId = JsonPath.read(contas, "$[0].id");
		return new Cliente(token, contaId.longValue(), email);
	}

	String login(String email, String senha) throws Exception {
		String resp = mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","senha":"%s"}""".formatted(email, senha)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(resp, "$.token");
	}

	static MockHttpServletRequestBuilder autenticado(MockHttpServletRequestBuilder req, String token) {
		return req.header("Authorization", "Bearer " + token);
	}

	static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String token, String body) {
		return autenticado(req, token).contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
