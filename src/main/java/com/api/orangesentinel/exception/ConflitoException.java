package com.api.orangesentinel.exception;

/**
 * Conflito com o estado atual do recurso (duplicidade, operação já em andamento). Mapeada para HTTP 409.
 */
public class ConflitoException extends RuntimeException {

	public ConflitoException(String mensagem) {
		super(mensagem);
	}

}
