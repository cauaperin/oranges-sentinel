package com.api.orangesentinel.security;

import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;

import com.api.orangesentinel.model.enums.Role;

/**
 * Extrai os dados do usuário autenticado a partir do JWT.
 */
public final class UsuarioLogado {

	private UsuarioLogado() {
	}

	public static Long id(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}

	public static boolean temRole(Jwt jwt, Role role) {
		List<String> roles = jwt.getClaimAsStringList(TokenService.CLAIM_ROLES);
		return roles != null && roles.contains(role.name());
	}

	/** Analistas e administradores podem consultar dados de qualquer conta. */
	public static boolean ehEquipeAntifraude(Jwt jwt) {
		return temRole(jwt, Role.ANALISTA) || temRole(jwt, Role.ADMIN);
	}

}
