package com.api.orangesentinel.service.antifraude.score;

import com.api.orangesentinel.service.antifraude.grafo.MetricasConta;

/**
 * Estratégia de cálculo do score de risco (0–100) a partir das métricas de grafo de uma conta.
 */
public interface RiskScorer {

	ScoreResultado avaliar(MetricasConta metricas, ConfiguracaoRisco config);

}
