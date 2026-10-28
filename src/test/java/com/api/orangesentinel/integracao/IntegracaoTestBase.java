package com.api.orangesentinel.integracao;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import com.api.orangesentinel.client.MotorClient;

/**
 * Base dos testes de integração: sobe o banco completo contra um PostgreSQL real (Testcontainers; ignorado sem
 * Docker). O motor é falso e os tokens são assinados por uma chave de teste, no formato que o serviço auth emite.
 */
@SpringBootTest(properties = { "app.internal.chave=chave-de-teste", "app.jwt.jwk-set-uri=http://localhost:1/jwks" })
@AutoConfigureMockMvc
@Import(IntegracaoTestBase.Infra.class)
@Testcontainers(disabledWithoutDocker = true)
abstract class IntegracaoTestBase {

	static final String CHAVE_INTERNA = "chave-de-teste";

	private static final RSAKey CHAVE = chave();

	private static final AtomicLong PROXIMO_USUARIO = new AtomicLong(1000);

	@Autowired
	MockMvc mvc;

	@Autowired
	FakeMotor motor;

	@TestConfiguration(proxyBeanMethods = false)
	static class Infra {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer("postgres:17-alpine");
		}

		@Bean
		FakeMotor motorFalso() {
			return new FakeMotor();
		}

		@Bean
		@Primary
		MotorClient motorClient(FakeMotor fake) {
			return fake;
		}

		@Bean
		@Primary
		JwtDecoder decoderDeTeste() throws Exception {
			return NimbusJwtDecoder.withPublicKey(CHAVE.toRSAPublicKey()).build();
		}

	}

	@BeforeEach
	void resetarMotor() {
		motor.reset();
	}

	// ------------------------------------------------------------------ helpers

	record Cliente(String token, long usuarioId, long contaId, String email) {
	}

	static String cpfAleatorio() {
		return "1" + String.format("%010d", ThreadLocalRandom.current().nextLong(10_000_000_000L));
	}

	/** Cria o usuário "no auth" (só o token) e abre a conta inicial pela API interna, como o auth faz no cadastro. */
	Cliente registrarCliente(String nome) throws Exception {
		long usuarioId = PROXIMO_USUARIO.incrementAndGet();
		String cpf = cpfAleatorio();
		String email = nome.toLowerCase() + "." + ThreadLocalRandom.current().nextInt(1_000_000) + "@teste.dev";
		String resp = mvc.perform(interno(post("/internal/contas/inicial")).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"usuarioId": %d, "titular": "%s", "cpf": "%s"}""".formatted(usuarioId, nome, cpf)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Number contaId = JsonPath.read(resp, "$.id");
		return new Cliente(token(usuarioId, nome, cpf), usuarioId, contaId.longValue(), email);
	}

	/** Token no formato do serviço auth (RS256, claims sub/nome/cpf/roles/iss). */
	static String token(long usuarioId, String nome, String cpf) {
		Instant agora = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer("orangesentinel-auth")
			.subject(String.valueOf(usuarioId))
			.issuedAt(agora)
			.expiresAt(agora.plusSeconds(600))
			.claim("nome", nome)
			.claim("cpf", cpf)
			.claim("roles", List.of("CLIENTE"))
			.build();
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(CHAVE.getKeyID()).build();
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(CHAVE)))
			.encode(JwtEncoderParameters.from(header, claims))
			.getTokenValue();
	}

	static MockHttpServletRequestBuilder interno(MockHttpServletRequestBuilder req) {
		return req.header("X-Internal-Key", CHAVE_INTERNA);
	}

	static MockHttpServletRequestBuilder autenticado(MockHttpServletRequestBuilder req, String token) {
		return req.header("Authorization", "Bearer " + token);
	}

	static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String token, String body) {
		return autenticado(req, token).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	static MockHttpServletRequestBuilder jsonInterno(MockHttpServletRequestBuilder req, String body) {
		return interno(req).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	String conta(Cliente c) throws Exception {
		return mvc.perform(autenticado(get("/api/contas/{id}", c.contaId()), c.token()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private static RSAKey chave() {
		try {
			return new RSAKeyGenerator(2048).keyID("teste").generate();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
