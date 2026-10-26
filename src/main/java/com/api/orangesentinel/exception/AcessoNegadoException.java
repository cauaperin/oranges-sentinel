package com.api.orangesentinel.exception;

/**
 * Usuário autenticado tentando acessar recurso de outro usuário. Mapeada para HTTP 403.
 */
public class AcessoNegadoException extends RuntimeException {

	public AcessoNegadoException(String mensagem) {
		super(mensagem);
	}

}
