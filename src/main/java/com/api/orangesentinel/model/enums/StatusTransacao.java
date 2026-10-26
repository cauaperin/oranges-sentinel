package com.api.orangesentinel.model.enums;

public enum StatusTransacao {

	CONCLUIDA,
	/** Recusada pelo motor em tempo real (ex.: destino bloqueado). Nenhum valor foi movimentado. */
	RECUSADA,
	/** Bloqueio cautelar: debitada da origem e retida até a decisão do analista. */
	RETIDA,
	/** Retida e devolvida à origem após confirmação de fraude no destino. */
	ESTORNADA

}
