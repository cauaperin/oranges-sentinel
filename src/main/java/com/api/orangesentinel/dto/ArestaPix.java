package com.api.orangesentinel.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pix concluído entre duas contas, enviado ao motor para montar o grafo de transações.
 */
public record ArestaPix(Long transacaoId, Long origem, Long destino, BigDecimal valor, Instant timestamp) {
}
