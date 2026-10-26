package com.api.orangesentinel.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Parâmetros do modelo de risco (linha única, id = 1). Pesos da regressão logística simplificada, limiares de
 * classificação e parâmetros de construção do grafo.
 */
@Entity
@Table(name = "parametros_risco")
public class ParametrosRisco {

	public static final short ID_UNICO = 1;

	@Id
	private Short id;

	@Column(nullable = false)
	private double intercepto;

	@Column(name = "peso_fan_in", nullable = false)
	private double pesoFanIn;

	@Column(name = "peso_fan_out", nullable = false)
	private double pesoFanOut;

	@Column(name = "peso_taxa_repasse", nullable = false)
	private double pesoTaxaRepasse;

	@Column(name = "peso_repasse_rapido", nullable = false)
	private double pesoRepasseRapido;

	@Column(name = "peso_ciclo", nullable = false)
	private double pesoCiclo;

	@Column(name = "peso_centralidade", nullable = false)
	private double pesoCentralidade;

	@Column(name = "peso_conta_nova", nullable = false)
	private double pesoContaNova;

	@Column(name = "limiar_suspeita", nullable = false)
	private int limiarSuspeita;

	@Column(name = "limiar_laranja", nullable = false)
	private int limiarLaranja;

	@Column(name = "janela_dias", nullable = false)
	private int janelaDias;

	@Column(name = "repasse_rapido_minutos", nullable = false)
	private int repasseRapidoMinutos;

	@Column(name = "ciclo_max_comprimento", nullable = false)
	private int cicloMaxComprimento;

	@Column(name = "ciclo_janela_horas", nullable = false)
	private int cicloJanelaHoras;

	@Column(name = "min_transacoes", nullable = false)
	private int minTransacoes;

	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	protected ParametrosRisco() {
	}

	public Short getId() {
		return id;
	}

	public double getIntercepto() {
		return intercepto;
	}

	public void setIntercepto(double intercepto) {
		this.intercepto = intercepto;
	}

	public double getPesoFanIn() {
		return pesoFanIn;
	}

	public void setPesoFanIn(double pesoFanIn) {
		this.pesoFanIn = pesoFanIn;
	}

	public double getPesoFanOut() {
		return pesoFanOut;
	}

	public void setPesoFanOut(double pesoFanOut) {
		this.pesoFanOut = pesoFanOut;
	}

	public double getPesoTaxaRepasse() {
		return pesoTaxaRepasse;
	}

	public void setPesoTaxaRepasse(double pesoTaxaRepasse) {
		this.pesoTaxaRepasse = pesoTaxaRepasse;
	}

	public double getPesoRepasseRapido() {
		return pesoRepasseRapido;
	}

	public void setPesoRepasseRapido(double pesoRepasseRapido) {
		this.pesoRepasseRapido = pesoRepasseRapido;
	}

	public double getPesoCiclo() {
		return pesoCiclo;
	}

	public void setPesoCiclo(double pesoCiclo) {
		this.pesoCiclo = pesoCiclo;
	}

	public double getPesoCentralidade() {
		return pesoCentralidade;
	}

	public void setPesoCentralidade(double pesoCentralidade) {
		this.pesoCentralidade = pesoCentralidade;
	}

	public double getPesoContaNova() {
		return pesoContaNova;
	}

	public void setPesoContaNova(double pesoContaNova) {
		this.pesoContaNova = pesoContaNova;
	}

	public int getLimiarSuspeita() {
		return limiarSuspeita;
	}

	public void setLimiarSuspeita(int limiarSuspeita) {
		this.limiarSuspeita = limiarSuspeita;
	}

	public int getLimiarLaranja() {
		return limiarLaranja;
	}

	public void setLimiarLaranja(int limiarLaranja) {
		this.limiarLaranja = limiarLaranja;
	}

	public int getJanelaDias() {
		return janelaDias;
	}

	public void setJanelaDias(int janelaDias) {
		this.janelaDias = janelaDias;
	}

	public int getRepasseRapidoMinutos() {
		return repasseRapidoMinutos;
	}

	public void setRepasseRapidoMinutos(int repasseRapidoMinutos) {
		this.repasseRapidoMinutos = repasseRapidoMinutos;
	}

	public int getCicloMaxComprimento() {
		return cicloMaxComprimento;
	}

	public void setCicloMaxComprimento(int cicloMaxComprimento) {
		this.cicloMaxComprimento = cicloMaxComprimento;
	}

	public int getCicloJanelaHoras() {
		return cicloJanelaHoras;
	}

	public void setCicloJanelaHoras(int cicloJanelaHoras) {
		this.cicloJanelaHoras = cicloJanelaHoras;
	}

	public int getMinTransacoes() {
		return minTransacoes;
	}

	public void setMinTransacoes(int minTransacoes) {
		this.minTransacoes = minTransacoes;
	}

	public Instant getAtualizadoEm() {
		return atualizadoEm;
	}

	public void setAtualizadoEm(Instant atualizadoEm) {
		this.atualizadoEm = atualizadoEm;
	}

}
