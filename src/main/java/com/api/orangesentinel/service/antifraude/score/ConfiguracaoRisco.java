package com.api.orangesentinel.service.antifraude.score;

import java.time.Duration;

import com.api.orangesentinel.model.ParametrosRisco;

/**
 * Snapshot imutável dos parâmetros do modelo de risco, usado durante uma execução do motor.
 */
public record ConfiguracaoRisco(double intercepto, double pesoFanIn, double pesoFanOut, double pesoTaxaRepasse,
		double pesoRepasseRapido, double pesoCiclo, double pesoCentralidade, double pesoContaNova, int limiarSuspeita,
		int limiarLaranja, int janelaDias, int repasseRapidoMinutos, int cicloMaxComprimento, int cicloJanelaHoras,
		int minTransacoes) {

	public static ConfiguracaoRisco of(ParametrosRisco p) {
		return new ConfiguracaoRisco(p.getIntercepto(), p.getPesoFanIn(), p.getPesoFanOut(), p.getPesoTaxaRepasse(),
				p.getPesoRepasseRapido(), p.getPesoCiclo(), p.getPesoCentralidade(), p.getPesoContaNova(),
				p.getLimiarSuspeita(), p.getLimiarLaranja(), p.getJanelaDias(), p.getRepasseRapidoMinutos(),
				p.getCicloMaxComprimento(), p.getCicloJanelaHoras(), p.getMinTransacoes());
	}

	/** Valores padrão (iguais à migration V2), úteis em testes. */
	public static ConfiguracaoRisco padrao() {
		return new ConfiguracaoRisco(-6.5, 3.0, 2.0, 2.0, 3.5, 2.5, 1.5, 1.5, 50, 80, 30, 60, 6, 72, 3);
	}

	public Duration limiteRepasseRapido() {
		return Duration.ofMinutes(repasseRapidoMinutos);
	}

	public Duration janelaCiclo() {
		return Duration.ofHours(cicloJanelaHoras);
	}

}
