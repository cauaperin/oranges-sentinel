package com.api.orangesentinel.security;

import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.api.orangesentinel.config.JwtProperties;
import com.api.orangesentinel.model.Usuario;

@Service
public class TokenService {

	public static final String CLAIM_ROLES = "roles";

	public static final String ISSUER = "orangesentinel";

	private final JwtEncoder encoder;

	private final JwtProperties properties;

	public TokenService(JwtEncoder encoder, JwtProperties properties) {
		this.encoder = encoder;
		this.properties = properties;
	}

	public TokenGerado gerar(Usuario usuario) {
		Instant agora = Instant.now();
		Instant expiraEm = agora.plus(properties.expiracao());
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(ISSUER)
			.subject(usuario.getId().toString())
			.issuedAt(agora)
			.expiresAt(expiraEm)
			.claim("nome", usuario.getNome())
			.claim(CLAIM_ROLES, List.of(usuario.getRole().name()))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new TokenGerado(token, expiraEm);
	}

	public record TokenGerado(String token, Instant expiraEm) {
	}

}
