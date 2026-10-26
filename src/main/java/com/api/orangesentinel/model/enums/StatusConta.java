package com.api.orangesentinel.model.enums;

public enum StatusConta {

	ATIVA,
	/** Sinalizada pelo motor antifraude; aguardando decisão de um analista. */
	SOB_ANALISE,
	/** Bloqueio confirmado por analista (conta laranja). Não envia nem recebe. */
	BLOQUEADA,
	ENCERRADA;

	public boolean podeMovimentar() {
		return this == ATIVA || this == SOB_ANALISE;
	}

}
