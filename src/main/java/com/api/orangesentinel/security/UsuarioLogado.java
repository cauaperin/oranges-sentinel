package com.api.orangesentinel.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Extrai os dados do usuário autenticado a partir do JWT emitido pelo serviço auth.
 */
public final class UsuarioLogado {

	private UsuarioLogado() {
	}

	public static Long id(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}

	public static String nome(Jwt jwt) {
		return jwt.getClaimAsString("nome");
	}

	public static String cpf(Jwt jwt) {
		return jwt.getClaimAsString("cpf");
	}

}
