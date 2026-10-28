package com.api.orangesentinel.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api.orangesentinel.dto.InternalDtos.Decisao;
import com.api.orangesentinel.dto.InternalDtos.ResultadoDecisao;
import com.api.orangesentinel.exception.RecursoNaoEncontradoException;
import com.api.orangesentinel.model.Conta;
import com.api.orangesentinel.model.Transacao;
import com.api.orangesentinel.model.enums.StatusConta;
import com.api.orangesentinel.model.enums.StatusTransacao;
import com.api.orangesentinel.repository.ContaRepository;
import com.api.orangesentinel.repository.TransacaoRepository;

/**
 * Aplica no dinheiro a decisão que o analista tomou no motor sobre uma conta suspeita.
 * <ul>
 * <li>CONFIRMADO: conta BLOQUEADA e todos os Pix retidos para ela são estornados às origens;</li>
 * <li>FALSO_POSITIVO: conta volta a ATIVA e os Pix retidos são creditados normalmente.</li>
 * </ul>
 */
@Service
public class DecisaoFraudeService {

	private final ContaRepository contaRepository;

	private final TransacaoRepository transacaoRepository;

	public DecisaoFraudeService(ContaRepository contaRepository, TransacaoRepository transacaoRepository) {
		this.contaRepository = contaRepository;
		this.transacaoRepository = transacaoRepository;
	}

	@Transactional
	public ResultadoDecisao decidir(Long contaId, Decisao decisao, Long alertaId) {
		List<Transacao> retidas = transacaoRepository.findByContaDestinoIdAndStatus(contaId, StatusTransacao.RETIDA);

		// Trava todas as contas envolvidas em ordem crescente de id (mesma ordem do Pix: evita deadlock)
		TreeSet<Long> ids = new TreeSet<>();
		ids.add(contaId);
		retidas.forEach(t -> ids.add(t.getContaOrigem().getId()));
		Map<Long, Conta> travadas = new HashMap<>();
		for (Long id : ids) {
			travadas.put(id, contaRepository.findByIdParaAtualizacao(id)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Conta", id)));
		}
		Conta conta = travadas.get(contaId);

		if (decisao == Decisao.CONFIRMADO) {
			conta.setStatus(StatusConta.BLOQUEADA);
			for (Transacao t : retidas) {
				travadas.get(t.getContaOrigem().getId()).creditar(t.getValor());
				t.setStatus(StatusTransacao.ESTORNADA);
				t.setMotivo("Estornada: destino confirmado como conta laranja (alerta #" + alertaId + ")");
			}
			return new ResultadoDecisao(retidas.size(), 0);
		}
		if (conta.getStatus() == StatusConta.SOB_ANALISE) {
			conta.setStatus(StatusConta.ATIVA);
		}
		for (Transacao t : retidas) {
			conta.creditar(t.getValor());
			t.setStatus(StatusTransacao.CONCLUIDA);
			t.setMotivo("Liberada após análise (alerta #" + alertaId + " considerado falso positivo)");
		}
		return new ResultadoDecisao(0, retidas.size());
	}

}
