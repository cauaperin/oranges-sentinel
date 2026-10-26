package com.api.orangesentinel.model;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.api.orangesentinel.model.enums.OrigemAlerta;
import com.api.orangesentinel.model.enums.StatusAlerta;

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

/**
 * Alerta de possível conta laranja aguardando (ou já com) decisão de um analista. Há no máximo um alerta ABERTO
 * por conta; novas evidências são agregadas a ele.
 */
@Entity
@Table(name = "alerta_fraude")
public class AlertaFraude {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "conta_id")
	private Conta conta;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "analise_id")
	private AnaliseRisco analise;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrigemAlerta origem;

	private Integer score;

	@Column(columnDefinition = "text")
	private String motivos;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StatusAlerta status = StatusAlerta.ABERTO;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "analista_id")
	private Usuario analista;

	@Column(length = 500)
	private String observacao;

	@Column(name = "criado_em", nullable = false)
	private Instant criadoEm;

	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	@Column(name = "resolvido_em")
	private Instant resolvidoEm;

	protected AlertaFraude() {
	}

	public AlertaFraude(Conta conta, OrigemAlerta origem) {
		this.conta = conta;
		this.origem = origem;
		this.criadoEm = Instant.now();
		this.atualizadoEm = criadoEm;
	}

	/** Agrega nova evidência: mantém o maior score e acumula motivos sem repetição. */
	public void registrarEvidencia(Integer novoScore, List<String> novosMotivos, AnaliseRisco novaAnalise) {
		if (novoScore != null && (score == null || novoScore > score)) {
			score = novoScore;
		}
		if (novaAnalise != null) {
			analise = novaAnalise;
		}
		Set<String> todos = new LinkedHashSet<>();
		if (motivos != null && !motivos.isBlank()) {
			todos.addAll(List.of(motivos.split("\n")));
		}
		todos.addAll(novosMotivos);
		motivos = String.join("\n", todos);
		atualizadoEm = Instant.now();
	}

	public void resolver(StatusAlerta decisao, Usuario analista, String observacao) {
		this.status = decisao;
		this.analista = analista;
		this.observacao = observacao;
		this.resolvidoEm = Instant.now();
		this.atualizadoEm = resolvidoEm;
	}

	public Long getId() {
		return id;
	}

	public Conta getConta() {
		return conta;
	}

	public AnaliseRisco getAnalise() {
		return analise;
	}

	public OrigemAlerta getOrigem() {
		return origem;
	}

	public Integer getScore() {
		return score;
	}

	public String getMotivos() {
		return motivos;
	}

	public StatusAlerta getStatus() {
		return status;
	}

	public Usuario getAnalista() {
		return analista;
	}

	public String getObservacao() {
		return observacao;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	public Instant getAtualizadoEm() {
		return atualizadoEm;
	}

	public Instant getResolvidoEm() {
		return resolvidoEm;
	}

}
