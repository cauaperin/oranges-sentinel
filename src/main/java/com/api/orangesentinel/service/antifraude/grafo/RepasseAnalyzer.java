package com.api.orangesentinel.service.antifraude.grafo;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Mede a <b>velocidade de repasse</b> de uma conta: quanto tempo o dinheiro fica parado entre entrar e sair.
 * <p>
 * Algoritmo FIFO: cada entrada forma um "lote" de dinheiro; cada saída consome os lotes mais antigos ainda
 * disponíveis. Para cada parcela consumida mede-se Δt = saída − entrada. Contas laranja repassam quase tudo em
 * minutos; contas normais mantêm saldo por dias.
 */
public final class RepasseAnalyzer {

	private static final double EPS = 0.005;

	private RepasseAnalyzer() {
	}

	public record Fluxo(Instant timestamp, double valor) {
	}

	/**
	 * @param medianaSegundos mediana de Δt das parcelas repassadas (null se nada foi repassado)
	 * @param fracRapida fração do valor total enviado que saiu até {@code limite} depois de ter entrado
	 */
	public record Resultado(Double medianaSegundos, double fracRapida) {

		public static final Resultado VAZIO = new Resultado(null, 0);

	}

	public static Resultado analisar(List<Fluxo> entradas, List<Fluxo> saidas, Duration limite) {
		if (entradas.isEmpty() || saidas.isEmpty()) {
			return Resultado.VAZIO;
		}
		List<Fluxo> ent = entradas.stream().sorted(Comparator.comparing(Fluxo::timestamp)).toList();
		List<Fluxo> sai = saidas.stream().sorted(Comparator.comparing(Fluxo::timestamp)).toList();

		Deque<double[]> lotes = new ArrayDeque<>(); // [epochSegundos, restante]
		List<Long> deltas = new ArrayList<>();
		double totalEnviado = 0;
		double rapido = 0;
		long limiteSeg = limite.toSeconds();
		int i = 0;
		for (Fluxo saida : sai) {
			// Entradas no mesmo instante ou antes da saída ficam disponíveis para consumo
			while (i < ent.size() && !ent.get(i).timestamp().isAfter(saida.timestamp())) {
				lotes.addLast(new double[] { ent.get(i).timestamp().getEpochSecond(), ent.get(i).valor() });
				i++;
			}
			totalEnviado += saida.valor();
			double restante = saida.valor();
			while (restante > EPS && !lotes.isEmpty()) {
				double[] lote = lotes.peekFirst();
				double consumido = Math.min(restante, lote[1]);
				long delta = saida.timestamp().getEpochSecond() - (long) lote[0];
				deltas.add(delta);
				if (delta <= limiteSeg) {
					rapido += consumido;
				}
				lote[1] -= consumido;
				restante -= consumido;
				if (lote[1] <= EPS) {
					lotes.pollFirst();
				}
			}
		}
		if (deltas.isEmpty() || totalEnviado <= 0) {
			return Resultado.VAZIO;
		}
		return new Resultado(mediana(deltas), Math.min(1.0, rapido / totalEnviado));
	}

	private static double mediana(List<Long> valores) {
		List<Long> ordenados = valores.stream().sorted().toList();
		int n = ordenados.size();
		return n % 2 == 1 ? ordenados.get(n / 2) : (ordenados.get(n / 2 - 1) + ordenados.get(n / 2)) / 2.0;
	}

}
