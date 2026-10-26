package com.api.orangesentinel.service.antifraude.grafo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.api.orangesentinel.service.antifraude.grafo.RepasseAnalyzer.Fluxo;
import com.api.orangesentinel.service.antifraude.grafo.RepasseAnalyzer.Resultado;

class RepasseAnalyzerTest {

	private static final Instant T0 = Instant.parse("2026-10-20T10:00:00Z");

	private static final Duration UMA_HORA = Duration.ofHours(1);

	private static Fluxo em(long minutos, double valor) {
		return new Fluxo(T0.plus(Duration.ofMinutes(minutos)), valor);
	}

	@Test
	void repasseEmDoisMinutosEhTotalmenteRapido() {
		Resultado r = RepasseAnalyzer.analisar(List.of(em(0, 1000)), List.of(em(2, 990)), UMA_HORA);

		assertThat(r.fracRapida()).isEqualTo(1.0);
		assertThat(r.medianaSegundos()).isEqualTo(120.0);
	}

	@Test
	void dinheiroParadoPorDiasNaoEhRapido() {
		Resultado r = RepasseAnalyzer.analisar(List.of(em(0, 3000)), List.of(em(3 * 24 * 60, 500)), UMA_HORA);

		assertThat(r.fracRapida()).isZero();
		assertThat(r.medianaSegundos()).isEqualTo(3 * 86_400.0);
	}

	@Test
	void consumoFifoUsaEntradasMaisAntigasPrimeiro() {
		// 100 entra em t=0, 100 entra em t=50; saída de 150 em t=55 consome 100 (Δ=55min) + 50 (Δ=5min)
		Resultado r = RepasseAnalyzer.analisar(List.of(em(0, 100), em(50, 100)), List.of(em(55, 150)),
				Duration.ofMinutes(10));

		assertThat(r.fracRapida()).isCloseTo(50.0 / 150.0, org.assertj.core.data.Offset.offset(1e-9));
	}

	@Test
	void saidaSemEntradaAnteriorNaoTemRepasse() {
		Resultado r = RepasseAnalyzer.analisar(List.of(em(10, 100)), List.of(em(0, 100)), UMA_HORA);

		assertThat(r).isEqualTo(Resultado.VAZIO);
	}

}
