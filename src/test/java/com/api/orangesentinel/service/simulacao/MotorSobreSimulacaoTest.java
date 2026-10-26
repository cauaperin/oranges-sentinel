package com.api.orangesentinel.service.simulacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.api.orangesentinel.dto.SimulacaoDtos.AvaliacaoResponse;
import com.api.orangesentinel.model.enums.TipoTransacao;
import com.api.orangesentinel.service.antifraude.grafo.ArestaTransacao;
import com.api.orangesentinel.service.antifraude.grafo.GrafoTransacoes;
import com.api.orangesentinel.service.antifraude.grafo.GraphMetricsCalculator;
import com.api.orangesentinel.service.antifraude.grafo.MetricasConta;
import com.api.orangesentinel.service.antifraude.grafo.MovimentoExterno;
import com.api.orangesentinel.service.antifraude.grafo.ResultadoGrafo;
import com.api.orangesentinel.service.antifraude.score.ConfiguracaoRisco;
import com.api.orangesentinel.service.antifraude.score.LogisticRiskScorer;
import com.api.orangesentinel.service.antifraude.score.ScoreResultado;
import com.api.orangesentinel.service.simulacao.GeradorSimulacao.ContaSim;
import com.api.orangesentinel.service.simulacao.GeradorSimulacao.DadosSimulados;
import com.api.orangesentinel.service.simulacao.GeradorSimulacao.TxSim;

/**
 * Executa o motor completo (grafo → métricas → score) sobre dados do simulador, sem banco, e mede a qualidade da
 * detecção contra o rótulo verdadeiro.
 */
class MotorSobreSimulacaoTest {

	@ParameterizedTest
	@ValueSource(longs = { 42, 7, 2026 })
	void detectaRedesDeLaranjasComBoaPrecisaoERecall(long seed) {
		Instant agora = Instant.parse("2026-10-26T12:00:00Z");
		DadosSimulados dados = GeradorSimulacao
			.gerar(new GeradorSimulacao.Parametros(seed, 200, 4, 3, 3, 3, 30), agora);
		ConfiguracaoRisco config = ConfiguracaoRisco.padrao();

		List<ArestaTransacao> arestas = new ArrayList<>();
		List<MovimentoExterno> saques = new ArrayList<>();
		Instant desde = agora.minus(config.janelaDias(), ChronoUnit.DAYS);
		long id = 1;
		for (TxSim t : dados.transacoes()) {
			if (t.timestamp().isBefore(desde)) {
				continue;
			}
			if (t.tipo() == TipoTransacao.PIX) {
				arestas.add(new ArestaTransacao(id++, (long) t.origem(), (long) t.destino(), t.valor(), t.timestamp()));
			}
			else if (t.tipo() == TipoTransacao.SAQUE) {
				saques.add(new MovimentoExterno((long) t.origem(), t.valor(), t.timestamp()));
			}
		}
		Map<Long, Instant> criacao = new HashMap<>();
		dados.contas().forEach(c -> criacao.put((long) c.idx(), c.criadaEm()));

		ResultadoGrafo resultado = GraphMetricsCalculator.calcular(GrafoTransacoes.construir(arestas), saques,
				criacao, config, agora);
		LogisticRiskScorer scorer = new LogisticRiskScorer();
		List<AvaliacaoCalculator.Amostra> amostras = new ArrayList<>();
		for (ContaSim c : dados.contas()) {
			MetricasConta m = resultado.metricas().get((long) c.idx());
			ScoreResultado s = m == null ? null : scorer.avaliar(m, config);
			amostras.add(new AvaliacaoCalculator.Amostra((long) c.idx(), c.cenario(), c.cenario().isLaranja(),
					s == null ? null : s.score(), s == null ? null : s.classificacao()));
		}
		AvaliacaoResponse avaliacao = AvaliacaoCalculator.avaliar(amostras);
		System.out.printf("seed=%d laranja=%s suspeitaOuMais=%s%n", seed, avaliacao.laranja(),
				avaliacao.suspeitaOuMais());

		assertThat(avaliacao.laranja().recall()).isGreaterThanOrEqualTo(0.9);
		assertThat(avaliacao.laranja().precisao()).isGreaterThanOrEqualTo(0.9);
		assertThat(resultado.ciclos()).isNotEmpty();
	}

}
