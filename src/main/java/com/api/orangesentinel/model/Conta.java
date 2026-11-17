package com.api.orangesentinel.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.DynamicUpdate;

import com.api.orangesentinel.exception.RegraNegocioException;
import com.api.orangesentinel.model.enums.Classificacao;
import com.api.orangesentinel.model.enums.StatusConta;
import com.api.orangesentinel.model.enums.TipoConta;

@Entity
@DynamicUpdate
@Table(name = "conta")
public class Conta {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Dono da conta no serviço auth (aqui só o id) e a cópia dos dados do titular tirada na abertura. */
	@Column(name = "usuario_id", nullable = false)
	private Long usuarioId;

	@Column(name = "titular_nome", nullable = false, length = 120)
	private String titularNome;

	@Column(name = "titular_cpf", nullable = false, length = 11)
	private String titularCpf;

	@Column(nullable = false, length = 4)
	private String agencia = "0001";

	@Column(nullable = false, length = 10, unique = true)
	private String numero;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TipoConta tipo;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal saldo = BigDecimal.ZERO;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StatusConta status = StatusConta.ATIVA;

	/** Último score calculado pelo motor de grafos (desnormalizado para consulta rápida). */
	@Column(name = "score_risco")
	private Integer scoreRisco;

	@Enumerated(EnumType.STRING)
	@Column(name = "classificacao_risco", length = 20)
	private Classificacao classificacaoRisco;

	@Column(name = "analisado_em")
	private Instant analisadoEm;

	@Column(name = "criada_em", nullable = false)
	private Instant criadaEm;

	@Version
	private long version;

	protected Conta() {
	}

	public Conta(Long usuarioId, String titularNome, String titularCpf, String numero, TipoConta tipo) {
		this.usuarioId = usuarioId;
		this.titularNome = titularNome;
		this.titularCpf = titularCpf;
		this.numero = numero;
		this.tipo = tipo;
		this.criadaEm = Instant.now();
	}

	public void creditar(BigDecimal valor) {
		saldo = saldo.add(valor);
	}

	public void debitar(BigDecimal valor) {
		if (saldo.compareTo(valor) < 0) {
			throw new RegraNegocioException("Saldo insuficiente");
		}
		saldo = saldo.subtract(valor);
	}

	public void registrarRisco(int score, Classificacao classificacao, Instant quando) {
		this.scoreRisco = score;
		this.classificacaoRisco = classificacao;
		this.analisadoEm = quando;
	}

	public Long getId() {
		return id;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public String getTitularNome() {
		return titularNome;
	}

	public String getTitularCpf() {
		return titularCpf;
	}

	public String getAgencia() {
		return agencia;
	}

	public String getNumero() {
		return numero;
	}

	public TipoConta getTipo() {
		return tipo;
	}

	public BigDecimal getSaldo() {
		return saldo;
	}

	public void setSaldo(BigDecimal saldo) {
		this.saldo = saldo;
	}

	public StatusConta getStatus() {
		return status;
	}

	public void setStatus(StatusConta status) {
		this.status = status;
	}

	public Integer getScoreRisco() {
		return scoreRisco;
	}

	public Classificacao getClassificacaoRisco() {
		return classificacaoRisco;
	}

	public Instant getAnalisadoEm() {
		return analisadoEm;
	}

	public Instant getCriadaEm() {
		return criadaEm;
	}

	public void setCriadaEm(Instant criadaEm) {
		this.criadaEm = criadaEm;
	}

}
