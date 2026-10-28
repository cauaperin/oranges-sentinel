package com.api.orangesentinel.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api.orangesentinel.client.MotorClient;
import com.api.orangesentinel.client.MotorDtos.Acao;
import com.api.orangesentinel.client.MotorDtos.AvaliacaoPixRequest;
import com.api.orangesentinel.client.MotorDtos.AvaliacaoPixResponse;
import com.api.orangesentinel.client.MotorDtos.ContaPix;
import com.api.orangesentinel.client.MotorDtos.Recebimento;
import com.api.orangesentinel.dto.TransacaoDtos.MovimentacaoRequest;
import com.api.orangesentinel.dto.TransacaoDtos.PixRequest;
import com.api.orangesentinel.dto.TransacaoDtos.TransacaoResponse;
import com.api.orangesentinel.exception.ConflitoException;
import com.api.orangesentinel.exception.RecursoNaoEncontradoException;
import com.api.orangesentinel.exception.RegraNegocioException;
import com.api.orangesentinel.model.Conta;
import com.api.orangesentinel.model.Transacao;
import com.api.orangesentinel.model.enums.StatusConta;
import com.api.orangesentinel.model.enums.StatusTransacao;
import com.api.orangesentinel.model.enums.TipoTransacao;
import com.api.orangesentinel.repository.ContaRepository;
import com.api.orangesentinel.repository.TransacaoRepository;

/**
 * Movimentações financeiras do banco simulado: Pix, depósito e saque. Todo Pix é avaliado pelo motor antifraude
 * antes de ser efetivado (ver {@link MotorClient}).
 */
@Service
public class TransferenciaService {

	private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

	/** Quanto do histórico de recebimentos da origem é enviado ao motor (o parâmetro padrão de repasse rápido é 60 min). */
	private static final Duration JANELA_RECEBIMENTOS = Duration.ofMinutes(60);

	private final TransacaoRepository repository;

	private final ContaRepository contaRepository;

	private final ChavePixService chavePixService;

	private final MotorClient motorClient;

	public TransferenciaService(TransacaoRepository repository, ContaRepository contaRepository,
			ChavePixService chavePixService, MotorClient motorClient) {
		this.repository = repository;
		this.contaRepository = contaRepository;
		this.chavePixService = chavePixService;
		this.motorClient = motorClient;
	}

	@Transactional
	public TransacaoResponse pix(Long usuarioId, PixRequest req, String idempotencyKey) {
		String chave = normalizarChave(idempotencyKey);
		var existente = repository.findByIdempotencyKey(chave);
		if (existente.isPresent()) {
			Transacao t = existente.get();
			if (t.getContaOrigem() == null || !t.getContaOrigem().getUsuarioId().equals(usuarioId)) {
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
		AvaliacaoPixResponse decisao = motorClient.avaliarPix(avaliacao(origem, destino, req.valor(), agora));
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
		if (decisao.motivos() != null && !decisao.motivos().isEmpty()) {
			t.setMotivo(decisao.motivoTexto());
		}
		repository.save(t);
		if (decisao.acao() != Acao.APROVAR) {
			log.info("Pix {} avaliado pelo motor como {}: {}", t.getId(), decisao.acao(), decisao.motivoTexto());
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

	/** Fatos que o banco possui e o motor precisa para decidir: status e risco das contas e recebimentos recentes. */
	private AvaliacaoPixRequest avaliacao(Conta origem, Conta destino, BigDecimal valor, Instant agora) {
		List<Recebimento> recebimentos = repository
			.recebimentosRecentes(origem.getId(), agora.minus(JANELA_RECEBIMENTOS))
			.stream()
			.map(r -> new Recebimento(r.getRemetenteId(), r.getValor(), r.getEm()))
			.toList();
		return new AvaliacaoPixRequest(contaPix(origem), contaPix(destino), valor, agora, recebimentos);
	}

	private static ContaPix contaPix(Conta c) {
		return new ContaPix(c.getId(), c.getStatus(), c.getScoreRisco(), c.getClassificacaoRisco());
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
