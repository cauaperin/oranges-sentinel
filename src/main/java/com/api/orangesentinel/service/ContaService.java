package com.api.orangesentinel.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api.orangesentinel.exception.AcessoNegadoException;
import com.api.orangesentinel.exception.RecursoNaoEncontradoException;
import com.api.orangesentinel.exception.RegraNegocioException;
import com.api.orangesentinel.model.Conta;
import com.api.orangesentinel.model.Usuario;
import com.api.orangesentinel.model.enums.StatusConta;
import com.api.orangesentinel.model.enums.TipoConta;
import com.api.orangesentinel.repository.ContaRepository;
import com.api.orangesentinel.repository.UsuarioRepository;

@Service
public class ContaService {

	private static final int MAX_CONTAS_POR_CLIENTE = 3;

	private final ContaRepository repository;

	private final UsuarioRepository usuarioRepository;

	public ContaService(ContaRepository repository, UsuarioRepository usuarioRepository) {
		this.repository = repository;
		this.usuarioRepository = usuarioRepository;
	}

	@Transactional
	public Conta abrir(Long usuarioId, TipoConta tipo) {
		Usuario titular = usuarioRepository.getReferenceById(usuarioId);
		List<Conta> existentes = repository.findByTitularIdOrderByIdAsc(usuarioId);
		long ativas = existentes.stream().filter(c -> c.getStatus() != StatusConta.ENCERRADA).count();
		if (ativas >= MAX_CONTAS_POR_CLIENTE) {
			throw new RegraNegocioException("Limite de " + MAX_CONTAS_POR_CLIENTE + " contas por cliente atingido");
		}
		return repository.save(new Conta(titular, gerarNumero(), tipo));
	}

	public String gerarNumero() {
		return String.valueOf(repository.proximoNumero());
	}

	@Transactional(readOnly = true)
	public List<Conta> listarDoUsuario(Long usuarioId) {
		return repository.findByTitularIdOrderByIdAsc(usuarioId);
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
		if (!conta.getTitular().getId().equals(usuarioId)) {
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

}
