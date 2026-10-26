package com.api.orangesentinel.service.antifraude.grafo;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Aresta direcionada do grafo: uma transferência Pix concluída entre duas contas do banco.
 */
public record ArestaTransacao(Long transacaoId, Long origem, Long destino, BigDecimal valor, Instant timestamp) {
}
