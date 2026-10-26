package com.api.orangesentinel.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.api.orangesentinel.model.enums.TipoChave;

@Entity
@Table(name = "chave_pix")
public class ChavePix {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "conta_id")
	private Conta conta;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TipoChave tipo;

	@Column(nullable = false, length = 140, unique = true)
	private String valor;

	@Column(name = "criada_em", nullable = false)
	private Instant criadaEm;

	protected ChavePix() {
	}

	public ChavePix(Conta conta, TipoChave tipo, String valor) {
		this.conta = conta;
		this.tipo = tipo;
		this.valor = valor;
		this.criadaEm = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Conta getConta() {
		return conta;
	}

	public TipoChave getTipo() {
		return tipo;
	}

	public String getValor() {
		return valor;
	}

	public Instant getCriadaEm() {
		return criadaEm;
	}

}
