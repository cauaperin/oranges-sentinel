package com.api.orangesentinel.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api.orangesentinel.exception.RecursoNaoEncontradoException;
import com.api.orangesentinel.exception.RegraNegocioException;
import com.api.orangesentinel.model.AlertaFraude;
import com.api.orangesentinel.model.AnaliseRisco;
import com.api.orangesentinel.model.Conta;
import com.api.orangesentinel.model.Transacao;
import com.api.orangesentinel.model.enums.OrigemAlerta;
import com.api.orangesentinel.model.enums.StatusAlerta;
import com.api.orangesentinel.model.enums.StatusConta;
import com.api.orangesentinel.model.enums.StatusTransacao;
import com.api.orangesentinel.repository.AlertaFraudeRepository;
import com.api.orangesentinel.repository.ContaRepository;
import com.api.orangesentinel.repository.TransacaoRepository;
import com.api.orangesentinel.repository.UsuarioRepository;

@Service
public class AlertaService {

	private final AlertaFraudeRepository repository;

	private final ContaRepository contaRepository;

	private final TransacaoRepository transacaoRepository;

	private final UsuarioRepository usuarioRepository;

	public AlertaService(AlertaFraudeRepository repository, ContaRepository contaRepository,
			TransacaoRepository transacaoRepository, UsuarioRepository usuarioRepository) {
		this.repository = repository;
		this.contaRepository = contaRepository;
		this.transacaoRepository = transacaoRepository;
		this.usuarioRepository = usuarioRepository;
	}

	/** Abre um alerta para a conta ou agrega a evidência ao alerta já aberto. */
	@Transactional
	public AlertaFraude registrar(Conta conta, OrigemAlerta origem, Integer score, List<String> motivos,
			AnaliseRisco analise) {
		AlertaFraude alerta = repository.findByContaIdAndStatus(conta.getId(), StatusAlerta.ABERTO)
			.orElseGet(() -> repository.save(new AlertaFraude(conta, origem)));
		alerta.registrarEvidencia(score, motivos, analise);
		return alerta;
	}

	@Transactional(readOnly = true)
	public Page<AlertaFraude> listar(StatusAlerta status, Pageable pageable) {
		return status == null ? repository.findAllBy(pageable) : repository.findByStatus(status, pageable);
	}

	@Transactional(readOnly = true)
	public List<AlertaFraude> daConta(Long contaId) {
		return repository.findByContaIdOrderByCriadoEmDesc(contaId);
	}

	/**
	 * Decisão do analista.
	 * <ul>
	 * <li>CONFIRMADO: conta BLOQUEADA e todos os Pix retidos para ela são estornados às origens;</li>
	 * <li>FALSO_POSITIVO: conta volta a ATIVA e os Pix retidos são creditados normalmente.</li>
	 * </ul>
	 */
	@Transactional
	public AlertaFraude resolver(Long alertaId, Long analistaId, StatusAlerta decisao, String observacao) {
		if (decisao == StatusAlerta.ABERTO) {
			throw new RegraNegocioException("Decisão deve ser CONFIRMADO ou FALSO_POSITIVO");
		}
		AlertaFraude alerta = repository.findById(alertaId)
			.orElseThrow(() -> new RecursoNaoEncontradoException("Alerta", alertaId));
		if (alerta.getStatus() != StatusAlerta.ABERTO) {
			throw new RegraNegocioException("Alerta já resolvido");
		}
		Long contaId = alerta.getConta().getId();
		List<Transacao> retidas = transacaoRepository.findByContaDestinoIdAndStatus(contaId, StatusTransacao.RETIDA);

		// Trava todas as contas envolvidas em ordem crescente de id (mesma ordem do Pix: evita deadlock)
		TreeSet<Long> ids = new TreeSet<>();
		ids.add(contaId);
		retidas.forEach(t -> ids.add(t.getContaOrigem().getId()));
		Map<Long, Conta> travadas = new HashMap<>();
		for (Long id : ids) {
			travadas.put(id, contaRepository.findByIdParaAtualizacao(id).orElseThrow());
		}
		Conta conta = travadas.get(contaId);

		if (decisao == StatusAlerta.CONFIRMADO) {
			conta.setStatus(StatusConta.BLOQUEADA);
			for (Transacao t : retidas) {
				travadas.get(t.getContaOrigem().getId()).creditar(t.getValor());
				t.setStatus(StatusTransacao.ESTORNADA);
				t.setMotivo("Estornada: destino confirmado como conta laranja (alerta #" + alertaId + ")");
			}
		}
		else {
			if (conta.getStatus() == StatusConta.SOB_ANALISE) {
				conta.setStatus(StatusConta.ATIVA);
			}
			for (Transacao t : retidas) {
				conta.creditar(t.getValor());
				t.setStatus(StatusTransacao.CONCLUIDA);
				t.setMotivo("Liberada após análise (alerta #" + alertaId + " considerado falso positivo)");
			}
		}
		alerta.resolver(decisao, usuarioRepository.getReferenceById(analistaId), observacao);
		return alerta;
	}

}
