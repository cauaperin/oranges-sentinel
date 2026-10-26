package com.api.orangesentinel.service.antifraude.grafo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.api.orangesentinel.service.antifraude.score.ConfiguracaoRisco;

class GraphMetricsCalculatorTest {

	private static final Instant T0 = Instant.parse("2026-10-20T10:00:00Z");

	private final List<ArestaTransacao> arestas = new ArrayList<>();

	private final List<MovimentoExterno> saques = new ArrayList<>();

	private long seq = 1;

	private void pix(long origem, long destino, String valor, Duration aposT0) {
		arestas.add(new ArestaTransacao(seq++, origem, destino, new BigDecimal(valor), T0.plus(aposT0)));
	}

	private ResultadoGrafo calcular(Map<Long, Instant> criacao) {
		return GraphMetricsCalculator.calcular(GrafoTransacoes.construir(arestas), saques, criacao,
				ConfiguracaoRisco.padrao(), T0.plus(Duration.ofDays(2)));
	}

	@Test
	void funilDe15ContasTemInDegree15ERepasseRapido() {
		long laranja = 100;
		for (long vitima = 1; vitima <= 15; vitima++) {
			pix(vitima, laranja, "1000.00", Duration.ofMinutes(vitima * 10));
		}
		pix(laranja, 200, "14500.00", Duration.ofMinutes(160));

		MetricasConta m = calcular(Map.of()).metricas().get(laranja);

		assertThat(m.inDegree()).isEqualTo(15);
		assertThat(m.outDegree()).isEqualTo(1);
		assertThat(m.qtdRecebidas()).isEqualTo(15);
		assertThat(m.taxaRepasse()).isCloseTo(14500.0 / 15000.0, org.assertj.core.data.Offset.offset(1e-9));
		// Parte do dinheiro entrou há mais de 60 min do repasse: só as entradas dos últimos 60 min contam como rápidas
		assertThat(m.fracRepasseRapido()).isBetween(0.3, 0.5);
	}

	@Test
	void dispersaoTemOutDegreeAlto() {
		pix(1, 50, "10000.00", Duration.ZERO);
		for (long laranja = 2; laranja <= 9; laranja++) {
			pix(50, laranja, "1200.00", Duration.ofMinutes(15));
		}

		MetricasConta m = calcular(Map.of()).metricas().get(50L);

		assertThat(m.outDegree()).isEqualTo(8);
		assertThat(m.inDegree()).isEqualTo(1);
		assertThat(m.fracRepasseRapido()).isEqualTo(1.0);
		assertThat(m.medianaRepasseSeg()).isEqualTo(15 * 60.0);
	}

	@Test
	void saqueContaComoRepasse() {
		pix(1, 10, "500.00", Duration.ZERO);
		pix(2, 10, "500.00", Duration.ofMinutes(5));
		saques.add(new MovimentoExterno(10L, new BigDecimal("980.00"), T0.plus(Duration.ofMinutes(20))));

		MetricasConta m = calcular(Map.of()).metricas().get(10L);

		assertThat(m.outDegree()).isZero();
		assertThat(m.qtdEnviadas()).isEqualTo(1);
		assertThat(m.totalEnviado()).isEqualByComparingTo("980.00");
		assertThat(m.fracRepasseRapido()).isEqualTo(1.0);
	}

	@Test
	void detectaCicloTemporalETodosOsMembros() {
		pix(1, 2, "10000.00", Duration.ofMinutes(0));
		pix(2, 3, "9900.00", Duration.ofMinutes(20));
		pix(3, 1, "9800.00", Duration.ofMinutes(45));

		ResultadoGrafo r = calcular(Map.of());

		assertThat(r.ciclos()).hasSize(1);
		Ciclo ciclo = r.ciclos().getFirst();
		assertThat(ciclo.contas()).containsExactly(1L, 2L, 3L);
		assertThat(ciclo.valorCirculado()).isEqualByComparingTo("9800.00");
		assertThat(r.metricas().values()).allMatch(MetricasConta::emCiclo);
		assertThat(r.metricas().get(2L).menorCiclo()).isEqualTo(3);
	}

	@Test
	void cicloForaDeOrdemTemporalNaoContaComoLavagem() {
		// Amigos que se pagaram em dias diferentes e em ordem que não fecha o ciclo dentro da janela de 72h
		pix(1, 2, "50.00", Duration.ofDays(1));
		pix(2, 3, "40.00", Duration.ZERO);
		pix(3, 1, "30.00", Duration.ofDays(10));

		assertThat(calcular(Map.of()).ciclos()).isEmpty();
	}

	@Test
	void intermediarioTemMaiorCentralidade() {
		// 1,2,3 -> 10 -> 20,21,22: a conta 10 está em todos os caminhos
		for (long origem = 1; origem <= 3; origem++) {
			pix(origem, 10, "100.00", Duration.ofMinutes(origem));
		}
		for (long destino = 20; destino <= 22; destino++) {
			pix(10, destino, "90.00", Duration.ofMinutes(10 + destino));
		}

		ResultadoGrafo r = calcular(Map.of());

		assertThat(r.metricas().get(10L).centralidadeRelativa()).isEqualTo(1.0);
		assertThat(r.metricas().get(1L).centralidadeRelativa()).isZero();
	}

	@Test
	void idadeDaContaUsaDataDeCriacao() {
		pix(1, 2, "10.00", Duration.ZERO);
		Map<Long, Instant> criacao = new HashMap<>();
		criacao.put(1L, T0.minus(Duration.ofDays(100)));
		criacao.put(2L, T0.minus(Duration.ofDays(3)));

		ResultadoGrafo r = calcular(criacao);

		assertThat(r.metricas().get(1L).idadeContaDias()).isEqualTo(102);
		assertThat(r.metricas().get(2L).idadeContaDias()).isEqualTo(5);
	}

}
