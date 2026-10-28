package com.api.orangesentinel.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Saque concluído (dinheiro que sai do banco), enviado ao motor como parte do repasse da conta.
 */
public record SaqueRegistrado(Long contaId, BigDecimal valor, Instant timestamp) {
}
