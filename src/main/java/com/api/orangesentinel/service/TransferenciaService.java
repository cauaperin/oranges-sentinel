package com.api.orangesentinel.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api.orangesentinel.dto.TransacaoDtos.MovimentacaoRequest;
import com.api.orangesentinel.dto.TransacaoDtos.PixRequest;
import com.api.orangesentinel.dto.TransacaoDtos.TransacaoResponse;
import com.api.orangesentinel.exception.ConflitoException;
import com.api.orangesentinel.exception.RecursoNaoEncontradoException;
import com.api.orangesentinel.exception.RegraNegocioException;
import com.api.orangesentinel.model.Conta;
import com.api.orangesentinel.model.Transacao;
import com.api.orangesentinel.model.enums.OrigemAlerta;
import com.api.orangesentinel.model.enums.StatusConta;
import com.api.orangesentinel.model.enums.StatusTransacao;
import com.api.orangesentinel.model.enums.TipoTransacao;
import com.api.orangesentinel.repository.ContaRepository;
import com.api.orangesentinel.repository.TransacaoRepository;
import com.api.orangesentinel.service.antifraude.RealTimeRiskGuard;
import com.api.orangesentinel.service.antifraude.RealTimeRiskGuard.Decisao;

/**
 * Movimentações financeiras do banco simulado: Pix, depósito e saque. Todo Pix passa pelo
 * {@link RealTimeRiskGuard} antes de ser efetivado.
 */
@Service
public class TransferenciaService {

	private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

	private final TransacaoRepository repository;

	private final ContaRepository contaRepository;

	private final ChavePixService chavePixService;

	private final RealTimeRiskGuard guard;

	private final AlertaService alertaService;

	public TransferenciaService(TransacaoRepository repository, ContaRepository contaRepository,
			ChavePixService chavePixService, RealTimeRiskGuard guard, AlertaService alertaService) {
		this.repository = repository;
		this.contaRepository = contaRepository;
		this.chavePixService = chavePixService;
		this.guard = guard;
		this.alertaService = alertaService;
	}

	@Transactional
	public TransacaoResponse pix(Long usuarioId, PixRequest req, String idempotencyKey) {
		String chave = normalizarChave(idempotencyKey);
		var existente = repository.findByIdempotencyKey(chave);
		if (existente.isPresent()) {
			Transacao t = existente.get();
			if (t.getContaOrigem() == null || !t.getContaOrigem().getTitular().getId().equals(usuarioId)) {
				throw new ConflitoException("Idempotency-Key já utilizada em outra operação");
			}
			return TransacaoResponse.of(t, t.getContaOrigem().getId());
		}

		// Só ids aqui: as contas são carregadas já travadas, para nunca ler um saldo desatualizado
		Long origemId = req.contaOrigemId();
		Long destinoId = chavePixService.resolverContaId(req.chaveDestino());
		if (origemId.equals(destinoId)) {
			throw new RegraNegocioException("Origem e destino não podem ser a mesma conta");
		}

		// Lock pessimista sempre em ordem crescente de id para evitar deadlock entre Pix cruzados
		Conta origem;
		Conta destino;
		if (origemId < destinoId) {
			origem = travar(origemId);
			destino = travar(destinoId);
		}
		else {
			destino = travar(destinoId);
			origem = travar(origemId);
		}
		ContaService.verificarTitular(origem, usuarioId);

		if (origem.getStatus() == StatusConta.BLOQUEADA || origem.getStatus() == StatusConta.ENCERRADA) {
			throw new RegraNegocioException("Conta de origem " + origem.getStatus().name().toLowerCase());
		}
		if (origem.getSaldo().compareTo(req.valor()) < 0) {
			throw new RegraNegocioException("Saldo insuficiente");
		}

		Instant agora = Instant.now();
		Decisao decisao = guard.avaliar(origem, destino, req.valor(), agora);
		StatusTransacao status = switch (decisao.acao()) {
			case RECUSAR -> StatusTransacao.RECUSADA;
			case RETER -> {
				origem.debitar(req.valor());
				yield StatusTransacao.RETIDA;
			}
			case APROVAR, APROVAR_COM_ALERTA -> {
				origem.debitar(req.valor());
				destino.creditar(req.valor());
				yield StatusTransacao.CONCLUIDA;
			}
		};

		Transacao t = new Transacao(origem, destino, req.valor(), TipoTransacao.PIX, status, req.descricao(), chave,
				agora);
		if (!decisao.motivos().isEmpty()) {
			t.setMotivo(decisao.motivoTexto());
		}
		repository.save(t);

		if (decisao.contaAlertada() != null) {
			alertaService.registrar(decisao.contaAlertada(), OrigemAlerta.TEMPO_REAL, decisao.score(),
					decisao.motivos(), null);
			log.info("Alerta em tempo real para conta {}: {}", decisao.contaAlertada().getId(), decisao.motivoTexto());
		}
		return TransacaoResponse.of(t, origem.getId());
	}

	@Transactional
	public TransacaoResponse depositar(Long contaId, Long usuarioId, MovimentacaoRequest req) {
		Conta conta = travar(contaId);
		ContaService.verificarTitular(conta, usuarioId);
		if (!conta.getStatus().podeMovimentar()) {
			throw new RegraNegocioException("Conta " + conta.getStatus().name().toLowerCase());
		}
		conta.creditar(req.valor());
		Transacao t = repository.save(new Transacao(null, conta, req.valor(), TipoTransacao.DEPOSITO,
				StatusTransacao.CONCLUIDA, req.descricao(), UUID.randomUUID().toString(), Instant.now()));
		return TransacaoResponse.of(t, contaId);
	}

	@Transactional
	public TransacaoResponse sacar(Long contaId, Long usuarioId, MovimentacaoRequest req) {
		Conta conta = travar(contaId);
		ContaService.verificarTitular(conta, usuarioId);
		if (conta.getStatus() != StatusConta.ATIVA) {
			throw new RegraNegocioException("Saque não permitido: conta " + conta.getStatus().name().toLowerCase());
		}
		conta.debitar(req.valor());
		Transacao t = repository.save(new Transacao(conta, null, req.valor(), TipoTransacao.SAQUE,
				StatusTransacao.CONCLUIDA, req.descricao(), UUID.randomUUID().toString(), Instant.now()));
		return TransacaoResponse.of(t, contaId);
	}

	@Transactional(readOnly = true)
	public Page<TransacaoResponse> extrato(Long contaId, Long usuarioId, Instant de, Instant ate, Pageable pageable) {
		Conta conta = contaRepository.findById(contaId)
			.orElseThrow(() -> new RecursoNaoEncontradoException("Conta", contaId));
		ContaService.verificarTitular(conta, usuarioId);
		return repository.extrato(contaId, de, ate, pageable).map(t -> TransacaoResponse.of(t, contaId));
	}

	private Conta travar(Long contaId) {
		return contaRepository.findByIdParaAtualizacao(contaId)
			.orElseThrow(() -> new RecursoNaoEncontradoException("Conta", contaId));
	}

	private static String normalizarChave(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			return UUID.randomUUID().toString();
		}
		String chave = idempotencyKey.trim();
		if (chave.length() > 80) {
			throw new RegraNegocioException("Idempotency-Key deve ter no máximo 80 caracteres");
		}
		return chave;
	}

}
