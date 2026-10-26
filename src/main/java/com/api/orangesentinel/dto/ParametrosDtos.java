package com.api.orangesentinel.dto;

import java.time.Instant;

import com.api.orangesentinel.model.ParametrosRisco;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public final class ParametrosDtos {

	private ParametrosDtos() {
	}

	public record ParametrosRiscoRequest(double intercepto, double pesoFanIn, double pesoFanOut,
			double pesoTaxaRepasse, double pesoRepasseRapido, double pesoCiclo, double pesoCentralidade,
			double pesoContaNova, @Min(1) @Max(99) int limiarSuspeita, @Min(2) @Max(100) int limiarLaranja,
			@Min(1) @Max(365) int janelaDias, @Min(1) @Max(1440) int repasseRapidoMinutos,
			@Min(3) @Max(8) int cicloMaxComprimento, @Min(1) @Max(720) int cicloJanelaHoras,
			@Min(1) @Max(100) int minTransacoes) {
	}

	public record ParametrosRiscoResponse(double intercepto, double pesoFanIn, double pesoFanOut,
			double pesoTaxaRepasse, double pesoRepasseRapido, double pesoCiclo, double pesoCentralidade,
			double pesoContaNova, int limiarSuspeita, int limiarLaranja, int janelaDias, int repasseRapidoMinutos,
			int cicloMaxComprimento, int cicloJanelaHoras, int minTransacoes, Instant atualizadoEm) {

		public static ParametrosRiscoResponse of(ParametrosRisco p) {
			return new ParametrosRiscoResponse(p.getIntercepto(), p.getPesoFanIn(), p.getPesoFanOut(),
					p.getPesoTaxaRepasse(), p.getPesoRepasseRapido(), p.getPesoCiclo(), p.getPesoCentralidade(),
					p.getPesoContaNova(), p.getLimiarSuspeita(), p.getLimiarLaranja(), p.getJanelaDias(),
					p.getRepasseRapidoMinutos(), p.getCicloMaxComprimento(), p.getCicloJanelaHoras(),
					p.getMinTransacoes(), p.getAtualizadoEm());
		}

	}

}
