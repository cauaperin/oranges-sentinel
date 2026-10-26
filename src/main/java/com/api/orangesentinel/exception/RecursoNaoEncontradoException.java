package com.api.orangesentinel.exception;

public class RecursoNaoEncontradoException extends RuntimeException {

	public RecursoNaoEncontradoException(String recurso, Object id) {
		super(recurso + " não encontrado(a): " + id);
	}

}
