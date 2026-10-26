package com.api.orangesentinel.service.antifraude.grafo;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Saída de dinheiro para fora do banco (saque). Não vira aresta, mas conta como repasse da conta.
 */
public record MovimentoExterno(Long contaId, BigDecimal valor, Instant timestamp) {
}
