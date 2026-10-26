package com.api.orangesentinel.service.simulacao;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.api.orangesentinel.dto.SimulacaoDtos.AvaliacaoResponse;
import com.api.orangesentinel.dto.SimulacaoDtos.ErroClassificacao;
import com.api.orangesentinel.dto.SimulacaoDtos.MetricasClassificacao;
import com.api.orangesentinel.dto.SimulacaoDtos.ResumoCenario;
import com.api.orangesentinel.model.enums.CenarioSimulacao;
import com.api.orangesentinel.model.enums.Classificacao;

/**
 * Matriz de confusão, precisão, recall, F1 e acurácia do motor em relação ao rótulo verdadeiro.
 */
public final class AvaliacaoCalculator {

	private static final int MAX_ERROS_LISTADOS = 20;

	private AvaliacaoCalculator() {
	}

	/** @param score/classificacao nulos = conta não analisada (sem movimentação no grafo) ⇒ prevista NORMAL */
	public record Amostra(Long contaId, CenarioSimulacao cenario, boolean laranja, Integer score,
			Classificacao classificacao) {

		int scoreOuZero() {
			return score == null ? 0 : score;
		}

	}

	public static AvaliacaoResponse avaliar(List<Amostra> amostras) {
		Predicate<Amostra> previstaLaranja = a -> a.classificacao() == Classificacao.LARANJA;
		Predicate<Amostra> previstaSuspeita = a -> a.classificacao() == Classificacao.LARANJA
				|| a.classificacao() == Classificacao.SUSPEITA;

		Map<CenarioSimulacao, List<Amostra>> porCenario = new EnumMap<>(CenarioSimulacao.class);
		amostras.forEach(a -> porCenario.computeIfAbsent(a.cenario(), k -> new ArrayList<>()).add(a));
		List<ResumoCenario> resumos = porCenario.entrySet()
			.stream()
			.map(e -> new ResumoCenario(e.getKey(), e.getValue().size(),
					(int) e.getValue().stream().filter(previstaLaranja).count(),
					(int) e.getValue().stream().filter(previstaSuspeita).count(),
					arredondar(e.getValue().stream().mapToInt(Amostra::scoreOuZero).average().orElse(0))))
			.toList();

		List<ErroClassificacao> fps = amostras.stream()
			.filter(a -> !a.laranja() && previstaLaranja.test(a))
			.sorted(Comparator.comparing(Amostra::scoreOuZero).reversed())
			.limit(MAX_ERROS_LISTADOS)
			.map(AvaliacaoCalculator::erro)
			.toList();
		List<ErroClassificacao> fns = amostras.stream()
			.filter(a -> a.laranja() && !previstaLaranja.test(a))
			.sorted(Comparator.comparing(Amostra::scoreOuZero))
			.limit(MAX_ERROS_LISTADOS)
			.map(AvaliacaoCalculator::erro)
			.toList();

		return new AvaliacaoResponse(amostras.size(), (int) amostras.stream().filter(Amostra::laranja).count(),
				metricas(amostras, previstaLaranja), metricas(amostras, previstaSuspeita), resumos, fps, fns);
	}

	static MetricasClassificacao metricas(List<Amostra> amostras, Predicate<Amostra> positivo) {
		int vp = 0;
		int fp = 0;
		int fn = 0;
		int vn = 0;
		for (Amostra a : amostras) {
			boolean previsto = positivo.test(a);
			if (a.laranja() && previsto) {
				vp++;
			}
			else if (!a.laranja() && previsto) {
				fp++;
			}
			else if (a.laranja()) {
				fn++;
			}
			else {
				vn++;
			}
		}
		double precisao = vp + fp == 0 ? 0 : (double) vp / (vp + fp);
		double recall = vp + fn == 0 ? 0 : (double) vp / (vp + fn);
		double f1 = precisao + recall == 0 ? 0 : 2 * precisao * recall / (precisao + recall);
		double acuracia = amostras.isEmpty() ? 0 : (double) (vp + vn) / amostras.size();
		return new MetricasClassificacao(vp, fp, fn, vn, arredondar(precisao), arredondar(recall), arredondar(f1),
				arredondar(acuracia));
	}

	private static ErroClassificacao erro(Amostra a) {
		return new ErroClassificacao(a.contaId(), a.cenario(), a.score(), a.classificacao());
	}

	private static double arredondar(double v) {
		return Math.round(v * 10_000) / 10_000.0;
	}

}
