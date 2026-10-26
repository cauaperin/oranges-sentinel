package com.api.orangesentinel.service.antifraude.score;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.api.orangesentinel.model.enums.Classificacao;
import com.api.orangesentinel.service.antifraude.grafo.MetricasConta;

class LogisticRiskScorerTest {

	private final LogisticRiskScorer scorer = new LogisticRiskScorer();

	private final ConfiguracaoRisco config = ConfiguracaoRisco.padrao();

	private static MetricasConta metricas(int in, int out, int recebidas, int enviadas, double taxa, Double mediana,
			double fracRapida, boolean ciclo, double centralidade, int idade) {
		return new MetricasConta(1L, in, out, recebidas, enviadas, BigDecimal.valueOf(10_000),
				BigDecimal.valueOf(10_000 * taxa), taxa, mediana, fracRapida, ciclo, ciclo ? 3 : 0, centralidade,
				centralidade, 0.01, idade);
	}

	@Test
	void laranjaClassicaTemScoreAlto() {
		ScoreResultado r = scorer.avaliar(metricas(15, 1, 15, 3, 0.96, 300.0, 0.95, false, 0.3, 7), config);

		assertThat(r.score()).isGreaterThanOrEqualTo(80);
		assertThat(r.classificacao()).isEqualTo(Classificacao.LARANJA);
		assertThat(r.motivos()).anyMatch(m -> m.contains("15 contas distintas"));
		assertThat(r.contribuicoes()).containsKeys("fanIn", "repasseRapido", "taxaRepasse");
	}

	@Test
	void clienteNormalTemScoreBaixo() {
		ScoreResultado r = scorer.avaliar(metricas(3, 6, 6, 9, 0.75, 4.0 * 86_400, 0.05, false, 0.1, 900), config);

		assertThat(r.score()).isLessThan(50);
		assertThat(r.classificacao()).isEqualTo(Classificacao.NORMAL);
	}

	@Test
	void poucaEvidenciaLimitaOScore() {
		// Duas transações apenas: mesmo com repasse rápido não deve virar alerta
		ScoreResultado r = scorer.avaliar(metricas(1, 1, 1, 1, 1.0, 60.0, 1.0, false, 1.0, 1), config);

		assertThat(r.score()).isLessThan(config.limiarSuspeita());
	}

	@Test
	void regraDeCicloComRepasseEmHorasForcaLaranja() {
		ScoreResultado r = scorer.avaliar(metricas(1, 1, 2, 3, 0.66, 2.5 * 3600, 0.0, true, 0.0, 23), config);

		assertThat(r.score()).isEqualTo(RegrasDecisao.SCORE_CICLO_LAVAGEM);
		assertThat(r.motivos().getFirst()).startsWith("Regra: ciclo fechado");
	}

	@Test
	void regraDeContaDePassagem() {
		ScoreResultado r = scorer.avaliar(metricas(1, 0, 2, 2, 0.97, 1200.0, 0.97, false, 0.0, 18), config);

		assertThat(r.score()).isEqualTo(RegrasDecisao.SCORE_CONTA_PASSAGEM);
		assertThat(r.classificacao()).isEqualTo(Classificacao.LARANJA);
	}

	@Test
	void scoreEhMonotonoNoRepasseRapido() {
		int lento = scorer.avaliar(metricas(5, 2, 6, 3, 0.8, 7200.0, 0.1, false, 0.2, 200), config).score();
		int rapido = scorer.avaliar(metricas(5, 2, 6, 3, 0.8, 600.0, 0.9, false, 0.2, 200), config).score();

		assertThat(rapido).isGreaterThan(lento);
	}

}
