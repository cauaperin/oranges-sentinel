package com.api.orangesentinel.exception;

/**
 * Violação de regra de negócio (saldo insuficiente, conta bloqueada, etc.). Mapeada para HTTP 422.
 */
public class RegraNegocioException extends RuntimeException {

	public RegraNegocioException(String mensagem) {
		super(mensagem);
	}

}
