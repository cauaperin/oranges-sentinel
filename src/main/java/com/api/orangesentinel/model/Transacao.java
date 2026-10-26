package com.api.orangesentinel.model;

import java.math.BigDecimal;
import java.time.Instant;

import com.api.orangesentinel.model.enums.StatusTransacao;
import com.api.orangesentinel.model.enums.TipoTransacao;

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

@Entity
@Table(name = "transacao")
public class Transacao {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "conta_origem_id")
	private Conta contaOrigem;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "conta_destino_id")
	private Conta contaDestino;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal valor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TipoTransacao tipo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StatusTransacao status;

	@Column(length = 140)
	private String descricao;

	/** Justificativa quando a transação foi recusada, retida ou estornada. */
	@Column(length = 500)
	private String motivo;

	@Column(name = "idempotency_key", nullable = false, length = 80, unique = true)
	private String idempotencyKey;

	@Column(name = "criada_em", nullable = false)
	private Instant criadaEm;

	protected Transacao() {
	}

	public Transacao(Conta contaOrigem, Conta contaDestino, BigDecimal valor, TipoTransacao tipo,
			StatusTransacao status, String descricao, String idempotencyKey, Instant criadaEm) {
		this.contaOrigem = contaOrigem;
		this.contaDestino = contaDestino;
		this.valor = valor;
		this.tipo = tipo;
		this.status = status;
		this.descricao = descricao;
		this.idempotencyKey = idempotencyKey;
		this.criadaEm = criadaEm;
	}

	public Long getId() {
		return id;
	}

	public Conta getContaOrigem() {
		return contaOrigem;
	}

	public Conta getContaDestino() {
		return contaDestino;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public TipoTransacao getTipo() {
		return tipo;
	}

	public StatusTransacao getStatus() {
		return status;
	}

	public void setStatus(StatusTransacao status) {
		this.status = status;
	}

	public String getDescricao() {
		return descricao;
	}

	public String getMotivo() {
		return motivo;
	}

	public void setMotivo(String motivo) {
		this.motivo = motivo == null || motivo.length() <= 500 ? motivo : motivo.substring(0, 500);
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public Instant getCriadaEm() {
		return criadaEm;
	}

}
