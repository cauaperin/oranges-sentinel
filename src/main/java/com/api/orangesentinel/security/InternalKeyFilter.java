package com.api.orangesentinel.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Protege {@code /internal/**} (chamadas entre serviços) com a chave compartilhada no header
 * {@value #HEADER}. Sem chave configurada, a API interna fica fechada.
 */
public class InternalKeyFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Internal-Key";

	private final byte[] chave;

	public InternalKeyFilter(String chave) {
		this.chave = chave == null ? new byte[0] : chave.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/internal/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String recebida = request.getHeader(HEADER);
		boolean valida = chave.length > 0 && recebida != null
				&& MessageDigest.isEqual(chave, recebida.getBytes(StandardCharsets.UTF_8));
		if (!valida) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		chain.doFilter(request, response);
	}

}
