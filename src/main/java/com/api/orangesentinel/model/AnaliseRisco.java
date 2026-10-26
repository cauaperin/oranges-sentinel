package com.api.orangesentinel.model;

import java.math.BigDecimal;
import java.time.Instant;

import com.api.orangesentinel.model.enums.Classificacao;
import com.api.orangesentinel.service.antifraude.grafo.MetricasConta;
import com.api.orangesentinel.service.antifraude.score.ScoreResultado;

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
 * Resultado do motor de grafos para uma conta em uma execução: métricas, score e explicação.
 */
@Entity
@Table(name = "analise_risco")
public class AnaliseRisco {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "execucao_id")
	private ExecucaoAnalise execucao;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "conta_id")
	private Conta conta;

	@Column(nullable = false)
	private int score;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Classificacao classificacao;

	@Column(name = "in_degree", nullable = false)
	private int inDegree;

	@Column(name = "out_degree", nullable = false)
	private int outDegree;

	@Column(name = "qtd_recebidas", nullable = false)
	private int qtdRecebidas;

	@Column(name = "qtd_enviadas", nullable = false)
	private int qtdEnviadas;

	@Column(name = "total_recebido", nullable = false, precision = 19, scale = 2)
	private BigDecimal totalRecebido;

	@Column(name = "total_enviado", nullable = false, precision = 19, scale = 2)
	private BigDecimal totalEnviado;

	@Column(name = "taxa_repasse", nullable = false)
	private double taxaRepasse;

	@Column(name = "mediana_repasse_seg")
	private Double medianaRepasseSeg;

	@Column(name = "frac_repasse_rapido", nullable = false)
	private double fracRepasseRapido;

	@Column(name = "em_ciclo", nullable = false)
	private boolean emCiclo;

	@Column(nullable = false)
	private double betweenness;

	@Column(name = "centralidade_relativa", nullable = false)
	private double centralidadeRelativa;

	@Column(name = "page_rank", nullable = false)
	private double pageRank;

	@Column(name = "idade_conta_dias", nullable = false)
	private int idadeContaDias;

	/** JSON {feature: wᵢ·xᵢ}. */
	@Column(columnDefinition = "text")
	private String contribuicoes;

	/** Motivos legíveis, um por linha. */
	@Column(columnDefinition = "text")
	private String motivos;

	@Column(name = "criada_em", nullable = false)
	private Instant criadaEm;

	protected AnaliseRisco() {
	}

	public AnaliseRisco(ExecucaoAnalise execucao, Conta conta, MetricasConta m, ScoreResultado s, Instant criadaEm) {
		this.execucao = execucao;
		this.conta = conta;
		this.score = s.score();
		this.classificacao = s.classificacao();
		this.inDegree = m.inDegree();
		this.outDegree = m.outDegree();
		this.qtdRecebidas = m.qtdRecebidas();
		this.qtdEnviadas = m.qtdEnviadas();
		this.totalRecebido = m.totalRecebido();
		this.totalEnviado = m.totalEnviado();
		this.taxaRepasse = m.taxaRepasse();
		this.medianaRepasseSeg = m.medianaRepasseSeg();
		this.fracRepasseRapido = m.fracRepasseRapido();
		this.emCiclo = m.emCiclo();
		this.betweenness = m.betweenness();
		this.centralidadeRelativa = m.centralidadeRelativa();
		this.pageRank = m.pageRank();
		this.idadeContaDias = m.idadeContaDias();
		this.contribuicoes = s.contribuicoesJson();
		this.motivos = s.motivosTexto();
		this.criadaEm = criadaEm;
	}

	public Long getId() {
		return id;
	}

	public ExecucaoAnalise getExecucao() {
		return execucao;
	}

	public Conta getConta() {
		return conta;
	}

	public int getScore() {
		return score;
	}

	public Classificacao getClassificacao() {
		return classificacao;
	}

	public int getInDegree() {
		return inDegree;
	}

	public int getOutDegree() {
		return outDegree;
	}

	public int getQtdRecebidas() {
		return qtdRecebidas;
	}

	public int getQtdEnviadas() {
		return qtdEnviadas;
	}

	public BigDecimal getTotalRecebido() {
		return totalRecebido;
	}

	public BigDecimal getTotalEnviado() {
		return totalEnviado;
	}

	public double getTaxaRepasse() {
		return taxaRepasse;
	}

	public Double getMedianaRepasseSeg() {
		return medianaRepasseSeg;
	}

	public double getFracRepasseRapido() {
		return fracRepasseRapido;
	}

	public boolean isEmCiclo() {
		return emCiclo;
	}

	public double getBetweenness() {
		return betweenness;
	}

	public double getCentralidadeRelativa() {
		return centralidadeRelativa;
	}

	public double getPageRank() {
		return pageRank;
	}

	public int getIdadeContaDias() {
		return idadeContaDias;
	}

	public String getContribuicoes() {
		return contribuicoes;
	}

	public String getMotivos() {
		return motivos;
	}

	public Instant getCriadaEm() {
		return criadaEm;
	}

}
