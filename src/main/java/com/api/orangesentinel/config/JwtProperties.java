package com.api.orangesentinel.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration expiracao) {

	public JwtProperties {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException(
					"app.jwt.secret (JWT_SECRET) deve ter pelo menos 32 bytes para HS256");
		}
		if (expiracao == null) {
			expiracao = Duration.ofHours(8);
		}
	}

}
