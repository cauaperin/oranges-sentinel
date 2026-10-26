package com.api.orangesentinel.model.enums;

public enum TipoTransacao {

	/** Transferência entre contas do banco (aresta do grafo). */
	PIX,
	/** Entrada de dinheiro externo (fora do grafo). */
	DEPOSITO,
	/** Saída de dinheiro para fora do banco (fora do grafo, mas conta como repasse). */
	SAQUE

}
