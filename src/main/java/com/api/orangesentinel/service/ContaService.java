package com.api.orangesentinel.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api.orangesentinel.dto.InternalDtos.ContaRisco;
import com.api.orangesentinel.exception.AcessoNegadoException;
import com.api.orangesentinel.exception.RecursoNaoEncontradoException;
import com.api.orangesentinel.exception.RegraNegocioException;
import com.api.orangesentinel.model.Conta;
import com.api.orangesentinel.model.enums.Classificacao;
import com.api.orangesentinel.model.enums.StatusConta;
import com.api.orangesentinel.model.enums.TipoConta;
import com.api.orangesentinel.repository.ContaRepository;

@Service
public class ContaService {

	private static final int MAX_CONTAS_POR_CLIENTE = 3;

	private final ContaRepository repository;

	public ContaService(ContaRepository repository) {
		this.repository = repository;
	}

	/** @param titularNome nome e CPF do titular vêm do JWT (serviço auth) e ficam copiados na conta */
	@Transactional
	public Conta abrir(Long usuarioId, String titularNome, String titularCpf, TipoConta tipo) {
		if (titularNome == null || titularCpf == null) {
			throw new RegraNegocioException("Token sem os dados do titular. Entre novamente para abrir a conta.");
		}
		List<Conta> existentes = repository.findByUsuarioIdOrderByIdAsc(usuarioId);
		long ativas = existentes.stream().filter(c -> c.getStatus() != StatusConta.ENCERRADA).count();
		if (ativas >= MAX_CONTAS_POR_CLIENTE) {
			throw new RegraNegocioException("Limite de " + MAX_CONTAS_POR_CLIENTE + " contas por cliente atingido");
		}
		return repository.save(new Conta(usuarioId, titularNome, titularCpf, gerarNumero(), tipo));
	}

	/**
	 * Conta corrente aberta pelo auth no cadastro. Idempotente: se o usuário já tem conta (o auth pode repetir a
	 * chamada), devolve a primeira em vez de abrir outra.
	 */
	@Transactional
	public Conta abrirInicial(Long usuarioId, String titularNome, String titularCpf) {
		List<Conta> existentes = repository.findByUsuarioIdOrderByIdAsc(usuarioId);
		if (!existentes.isEmpty()) {
			return existentes.getFirst();
		}
		return abrir(usuarioId, titularNome, titularCpf, TipoConta.CORRENTE);
	}

	public String gerarNumero() {
		return String.valueOf(repository.proximoNumero());
	}

	@Transactional(readOnly = true)
	public List<Conta> listarDoUsuario(Long usuarioId) {
		return repository.findByUsuarioIdOrderByIdAsc(usuarioId);
	}

	@Transactional(readOnly = true)
	public Conta buscar(Long contaId) {
		return repository.findById(contaId).orElseThrow(() -> new RecursoNaoEncontradoException("Conta", contaId));
	}

	/** Busca a conta garantindo que pertence ao usuário autenticado. */
	@Transactional(readOnly = true)
	public Conta buscarDoUsuario(Long contaId, Long usuarioId) {
		Conta conta = buscar(contaId);
		verificarTitular(conta, usuarioId);
		return conta;
	}

	public static void verificarTitular(Conta conta, Long usuarioId) {
		if (!conta.getUsuarioId().equals(usuarioId)) {
			throw new AcessoNegadoException("A conta não pertence ao usuário autenticado");
		}
	}

	@Transactional
	public Conta encerrar(Long contaId, Long usuarioId) {
		Conta conta = repository.findByIdParaAtualizacao(contaId)
			.orElseThrow(() -> new RecursoNaoEncontradoException("Conta", contaId));
		verificarTitular(conta, usuarioId);
		if (conta.getStatus() == StatusConta.ENCERRADA) {
			throw new RegraNegocioException("Conta já encerrada");
		}
		if (conta.getStatus() == StatusConta.BLOQUEADA || conta.getStatus() == StatusConta.SOB_ANALISE) {
			throw new RegraNegocioException("Conta em análise ou bloqueada não pode ser encerrada");
		}
		if (conta.getSaldo().compareTo(BigDecimal.ZERO) != 0) {
			throw new RegraNegocioException("Para encerrar, o saldo da conta deve ser zero");
		}
		conta.setStatus(StatusConta.ENCERRADA);
		return conta;
	}

	// ------------------------------------------------------------------ uso interno (motor)

	@Transactional(readOnly = true)
	public List<Conta> buscarVarias(List<Long> ids) {
		return repository.findAllById(ids);
	}

	@Transactional(readOnly = true)
	public Page<Conta> ranking(Classificacao classificacao, int minScore, Pageable pageable) {
		return repository.ranking(classificacao, minScore, pageable);
	}

	/** Grava o risco calculado pelo motor e suspende as contas que ele marcou como laranja. */
	@Transactional
	public void registrarRiscos(List<ContaRisco> riscos) {
		for (ContaRisco r : riscos) {
			repository.atualizarRisco(r.contaId(), r.score(), r.classificacao(), r.analisadoEm());
			if (r.sobAnalise()) {
				repository.colocarSobAnalise(r.contaId());
			}
		}
	}

}
