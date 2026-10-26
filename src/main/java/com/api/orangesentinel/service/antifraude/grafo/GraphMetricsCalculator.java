package com.api.orangesentinel.service.antifraude.grafo;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jgrapht.Graph;
import org.jgrapht.alg.scoring.BetweennessCentrality;
import org.jgrapht.alg.scoring.PageRank;
import org.jgrapht.graph.DefaultWeightedEdge;

import com.api.orangesentinel.service.antifraude.score.ConfiguracaoRisco;

/**
 * Extrai, para cada conta (vértice), as métricas usadas pelo modelo de risco:
 * <ol>
 * <li><b>Fan-in / fan-out</b>: in-degree e out-degree no grafo agregado (contrapartes distintas);</li>
 * <li><b>Velocidade de repasse</b>: via {@link RepasseAnalyzer} (Pix enviados + saques);</li>
 * <li><b>Ciclos</b>: via {@link CycleDetector};</li>
 * <li><b>Centralidade</b>: betweenness (intermediários críticos) e PageRank, ambos do JGraphT.</li>
 * </ol>
 */
public final class GraphMetricsCalculator {

	private GraphMetricsCalculator() {
	}

	public static ResultadoGrafo calcular(GrafoTransacoes grafo, Collection<MovimentoExterno> saques,
			Map<Long, Instant> criacaoContas, ConfiguracaoRisco config, Instant agora) {
		Graph<Long, ArestaTransacao> multi = grafo.multigrafo();
		Graph<Long, DefaultWeightedEdge> agregado = grafo.agregado();
		if (multi.vertexSet().isEmpty()) {
			return new ResultadoGrafo(Map.of(), List.of(), 0, 0);
		}

		Map<Long, List<MovimentoExterno>> saquesPorConta = saques.stream()
			.collect(Collectors.groupingBy(MovimentoExterno::contaId));

		List<Ciclo> ciclos = CycleDetector.detectar(grafo, config.cicloMaxComprimento(), config.janelaCiclo());
		Map<Long, Integer> menorCiclo = new HashMap<>();
		for (Ciclo c : ciclos) {
			for (Long conta : c.contas()) {
				menorCiclo.merge(conta, c.comprimento(), Math::min);
			}
		}

		Map<Long, Double> betweenness = new BetweennessCentrality<>(agregado, true).getScores();
		double maxBetweenness = betweenness.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
		Map<Long, Double> pageRank = new PageRank<>(agregado).getScores();
		Duration limiteRapido = config.limiteRepasseRapido();

		Map<Long, MetricasConta> resultado = new HashMap<>();
		for (Long conta : multi.vertexSet()) {
			List<RepasseAnalyzer.Fluxo> entradas = new ArrayList<>();
			BigDecimal recebido = BigDecimal.ZERO;
			for (ArestaTransacao a : multi.incomingEdgesOf(conta)) {
				entradas.add(new RepasseAnalyzer.Fluxo(a.timestamp(), a.valor().doubleValue()));
				recebido = recebido.add(a.valor());
			}
			List<RepasseAnalyzer.Fluxo> saidas = new ArrayList<>();
			BigDecimal enviado = BigDecimal.ZERO;
			for (ArestaTransacao a : multi.outgoingEdgesOf(conta)) {
				saidas.add(new RepasseAnalyzer.Fluxo(a.timestamp(), a.valor().doubleValue()));
				enviado = enviado.add(a.valor());
			}
			List<MovimentoExterno> saquesConta = saquesPorConta.getOrDefault(conta, List.of());
			for (MovimentoExterno s : saquesConta) {
				saidas.add(new RepasseAnalyzer.Fluxo(s.timestamp(), s.valor().doubleValue()));
				enviado = enviado.add(s.valor());
			}

			RepasseAnalyzer.Resultado repasse = RepasseAnalyzer.analisar(entradas, saidas, limiteRapido);
			double b = betweenness.getOrDefault(conta, 0.0);
			Instant criada = criacaoContas.getOrDefault(conta, agora);
			int idadeDias = (int) Math.max(0, Duration.between(criada, agora).toDays());

			resultado.put(conta,
					new MetricasConta(conta, agregado.inDegreeOf(conta), agregado.outDegreeOf(conta),
							multi.inDegreeOf(conta), multi.outDegreeOf(conta) + saquesConta.size(), recebido, enviado,
							taxaRepasse(recebido, enviado), repasse.medianaSegundos(), repasse.fracRapida(),
							menorCiclo.containsKey(conta), menorCiclo.getOrDefault(conta, 0), b,
							maxBetweenness > 0 ? b / maxBetweenness : 0.0, pageRank.getOrDefault(conta, 0.0),
							idadeDias));
		}
		return new ResultadoGrafo(resultado, ciclos, multi.vertexSet().size(), multi.edgeSet().size());
	}

	/** min(recebido, enviado) / max(recebido, enviado); 0 se a conta só recebe ou só envia. */
	static double taxaRepasse(BigDecimal recebido, BigDecimal enviado) {
		double r = recebido.doubleValue();
		double e = enviado.doubleValue();
		if (r <= 0 || e <= 0) {
			return 0.0;
		}
		return Math.min(r, e) / Math.max(r, e);
	}

}
