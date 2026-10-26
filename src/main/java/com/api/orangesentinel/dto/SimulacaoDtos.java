package com.api.orangesentinel.dto;

import java.util.List;

import com.api.orangesentinel.model.enums.CenarioSimulacao;
import com.api.orangesentinel.model.enums.Classificacao;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public final class SimulacaoDtos {

	private SimulacaoDtos() {
	}

	public record SimulacaoRequest(Long seed, @Min(10) @Max(2000) Integer clientesNormais,
			@Min(0) @Max(50) Integer redesFunil, @Min(0) @Max(50) Integer redesDispersao,
			@Min(0) @Max(50) Integer cadeias, @Min(0) @Max(50) Integer ciclos, @Min(7) @Max(90) Integer dias) {

		public SimulacaoRequest {
			seed = seed == null ? 42L : seed;
			clientesNormais = clientesNormais == null ? 150 : clientesNormais;
			redesFunil = redesFunil == null ? 3 : redesFunil;
			redesDispersao = redesDispersao == null ? 2 : redesDispersao;
			cadeias = cadeias == null ? 2 : cadeias;
			ciclos = ciclos == null ? 2 : ciclos;
			dias = dias == null ? 30 : dias;
		}

	}

	public record SimulacaoResponse(long seed, int usuarios, int contas, int transacoes, int contasLaranja,
			String senhaUsuariosSimulados) {
	}

	public record MetricasClassificacao(int verdadeirosPositivos, int falsosPositivos, int falsosNegativos,
			int verdadeirosNegativos, double precisao, double recall, double f1, double acuracia) {
	}

	public record ResumoCenario(CenarioSimulacao cenario, int contas, int classificadasLaranja,
			int classificadasSuspeitaOuMais, double scoreMedio) {
	}

	public record ErroClassificacao(Long contaId, CenarioSimulacao cenario, Integer score,
			Classificacao classificacao) {
	}

	/**
	 * Avaliação do motor contra o rótulo verdadeiro do simulador. {@code laranja} considera positiva apenas a
	 * classificação LARANJA; {@code suspeitaOuMais} considera SUSPEITA ou LARANJA.
	 */
	public record AvaliacaoResponse(int contasAvaliadas, int laranjasReais, MetricasClassificacao laranja,
			MetricasClassificacao suspeitaOuMais, List<ResumoCenario> porCenario,
			List<ErroClassificacao> falsosPositivos, List<ErroClassificacao> falsosNegativos) {
	}

}
