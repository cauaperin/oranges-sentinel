package com.api.orangesentinel.model.enums;

public enum StatusAlerta {

	ABERTO,
	/** Analista confirmou a fraude: conta bloqueada e valores retidos estornados. */
	CONFIRMADO,
	/** Analista descartou o alerta: conta reativada e valores retidos liberados. */
	FALSO_POSITIVO

}
