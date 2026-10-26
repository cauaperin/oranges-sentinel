package com.api.orangesentinel.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Registro de uma execução completa do motor de grafos.
 */
@Entity
@Table(name = "execucao_analise")
public class ExecucaoAnalise {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Instant inicio;

	private Instant fim;

	@Column(name = "janela_inicio", nullable = false)
	private Instant janelaInicio;

	@Column(name = "n_contas", nullable = false)
	private int nContas;

	@Column(name = "n_arestas", nullable = false)
	private int nArestas;

	@Column(name = "n_ciclos", nullable = false)
	private int nCiclos;

	@Column(name = "n_suspeitas", nullable = false)
	private int nSuspeitas;

	@Column(name = "n_laranjas", nullable = false)
	private int nLaranjas;

	@Column(name = "duracao_ms")
	private Long duracaoMs;

	@Column(name = "disparada_por", nullable = false, length = 80)
	private String disparadaPor;

	protected ExecucaoAnalise() {
	}

	public ExecucaoAnalise(Instant inicio, Instant janelaInicio, String disparadaPor) {
		this.inicio = inicio;
		this.janelaInicio = janelaInicio;
		this.disparadaPor = disparadaPor;
	}

	public void finalizar(Instant fim, int nContas, int nArestas, int nCiclos, int nSuspeitas, int nLaranjas) {
		this.fim = fim;
		this.nContas = nContas;
		this.nArestas = nArestas;
		this.nCiclos = nCiclos;
		this.nSuspeitas = nSuspeitas;
		this.nLaranjas = nLaranjas;
		this.duracaoMs = fim.toEpochMilli() - inicio.toEpochMilli();
	}

	public Long getId() {
		return id;
	}

	public Instant getInicio() {
		return inicio;
	}

	public Instant getFim() {
		return fim;
	}

	public Instant getJanelaInicio() {
		return janelaInicio;
	}

	public int getNContas() {
		return nContas;
	}

	public int getNArestas() {
		return nArestas;
	}

	public int getNCiclos() {
		return nCiclos;
	}

	public int getNSuspeitas() {
		return nSuspeitas;
	}

	public int getNLaranjas() {
		return nLaranjas;
	}

	public Long getDuracaoMs() {
		return duracaoMs;
	}

	public String getDisparadaPor() {
		return disparadaPor;
	}

}
