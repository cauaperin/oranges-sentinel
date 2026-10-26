package com.api.orangesentinel.model.enums;

/**
 * Papel de uma conta gerada pelo simulador (ground truth para avaliar o motor).
 */
public enum CenarioSimulacao {

	CLIENTE_NORMAL(false),
	EMPREGADOR(false),
	COMERCIO(false),
	/** Laranja que recebe de muitas vítimas e repassa rapidamente. */
	FUNIL(true),
	/** Conta que dispersa valores para várias laranjas. */
	DISPERSAO(true),
	/** Elo de uma cadeia de camadas (layering) A→B→C→D. */
	CADEIA(true),
	/** Participante de um ciclo fechado de lavagem A→B→C→A. */
	CICLO(true);

	private final boolean laranja;

	CenarioSimulacao(boolean laranja) {
		this.laranja = laranja;
	}

	public boolean isLaranja() {
		return laranja;
	}

}
