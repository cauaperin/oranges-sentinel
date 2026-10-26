package com.api.orangesentinel.model;

import com.api.orangesentinel.model.enums.CenarioSimulacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Rótulo verdadeiro (ground truth) de uma conta gerada pelo simulador.
 */
@Entity
@Table(name = "rotulo_simulacao")
public class RotuloSimulacao {

	@Id
	@Column(name = "conta_id")
	private Long contaId;

	@Column(name = "eh_laranja", nullable = false)
	private boolean ehLaranja;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private CenarioSimulacao cenario;

	protected RotuloSimulacao() {
	}

	public RotuloSimulacao(Long contaId, CenarioSimulacao cenario) {
		this.contaId = contaId;
		this.cenario = cenario;
		this.ehLaranja = cenario.isLaranja();
	}

	public Long getContaId() {
		return contaId;
	}

	public boolean isEhLaranja() {
		return ehLaranja;
	}

	public CenarioSimulacao getCenario() {
		return cenario;
	}

}
