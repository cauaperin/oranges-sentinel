package com.api.orangesentinel.service.antifraude.grafo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Ciclo fechado A → B → ... → A, temporalmente coerente (as transferências ocorreram em sequência dentro da
 * janela configurada).
 *
 * @param contas sequência de contas na ordem em que o dinheiro circulou (a primeira se repete implicitamente no
 * final)
 * @param valorCirculado menor valor ao longo do ciclo (quanto, no mínimo, deu a volta completa)
 */
public record Ciclo(List<Long> contas, BigDecimal valorCirculado, Instant inicio, Instant fim) {

	public int comprimento() {
		return contas.size();
	}

}
