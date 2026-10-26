package com.api.orangesentinel.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api.orangesentinel.config.AntifraudeProperties;
import com.api.orangesentinel.dto.FraudeDtos.AlertaResponse;
import com.api.orangesentinel.dto.FraudeDtos.AnaliseResponse;
import com.api.orangesentinel.dto.FraudeDtos.ArestaGrafo;
import com.api.orangesentinel.dto.FraudeDtos.CicloResponse;
import com.api.orangesentinel.dto.FraudeDtos.ContaCiclo;
import com.api.orangesentinel.dto.FraudeDtos.ContaRiscoDetalhe;
import com.api.orangesentinel.dto.FraudeDtos.ContaRiscoResponse;
import com.api.orangesentinel.dto.FraudeDtos.ExecucaoResponse;
import com.api.orangesentinel.dto.FraudeDtos.GrafoResponse;
import com.api.orangesentinel.dto.FraudeDtos.NoGrafo;
import com.api.orangesentinel.dto.FraudeDtos.PontoHistorico;
import com.api.orangesentinel.exception.RecursoNaoEncontradoException;
import com.api.orangesentinel.model.AnaliseRisco;
import com.api.orangesentinel.model.Conta;
import com.api.orangesentinel.model.enums.Classificacao;
import com.api.orangesentinel.model.enums.StatusAlerta;
import com.api.orangesentinel.repository.AnaliseRiscoRepository;
import com.api.orangesentinel.repository.ContaRepository;
import com.api.orangesentinel.repository.ExecucaoAnaliseRepository;
import com.api.orangesentinel.repository.TransacaoRepository;
import com.api.orangesentinel.service.antifraude.AnaliseRiscoService;
import com.api.orangesentinel.service.antifraude.grafo.Ciclo;
import com.api.orangesentinel.service.antifraude.grafo.GrafoTransacoes;
import com.api.orangesentinel.service.antifraude.grafo.GrafoTransacoes.SubgrafoEgo;

/**
 * Consultas da área do analista antifraude (ranking de risco, detalhe da conta, grafo, ciclos e alertas).
 */
@Service
public class FraudeService {

	private static final int TAMANHO_HISTORICO = 30;

	private final ContaRepository contaRepository;

	private final AnaliseRiscoRepository analiseRepository;

	private final ExecucaoAnaliseRepository execucaoRepository;

	private final TransacaoRepository transacaoRepository;

	private final AlertaService alertaService;

	private final AnaliseRiscoService analiseRiscoService;

	private final ParametrosRiscoService parametrosService;

	private final AntifraudeProperties properties;

	public FraudeService(ContaRepository contaRepository, AnaliseRiscoRepository analiseRepository,
			ExecucaoAnaliseRepository execucaoRepository, TransacaoRepository transacaoRepository,
			AlertaService alertaService, AnaliseRiscoService analiseRiscoService,
			ParametrosRiscoService parametrosService, AntifraudeProperties properties) {
		this.contaRepository = contaRepository;
		this.analiseRepository = analiseRepository;
		this.execucaoRepository = execucaoRepository;
		this.transacaoRepository = transacaoRepository;
		this.alertaService = alertaService;
		this.analiseRiscoService = analiseRiscoService;
		this.parametrosService = parametrosService;
		this.properties = properties;
	}

	@Transactional(readOnly = true)
	public Page<ContaRiscoResponse> ranking(Classificacao classificacao, int minScore, Pageable pageable) {
		return contaRepository.ranking(classificacao, minScore, pageable).map(ContaRiscoResponse::of);
	}

	@Transactional(readOnly = true)
	public ContaRiscoDetalhe detalhe(Long contaId) {
		Conta conta = contaRepository.findComTitular(List.of(contaId))
			.stream()
			.findFirst()
			.orElseThrow(() -> new RecursoNaoEncontradoException("Conta", contaId));
		List<AnaliseRisco> analises = analiseRepository.findByContaIdOrderByCriadaEmDesc(contaId,
				PageRequest.of(0, TAMANHO_HISTORICO));
		AnaliseResponse ultima = analises.isEmpty() ? null : AnaliseResponse.of(analises.getFirst());
		List<PontoHistorico> historico = analises.reversed()
			.stream()
			.map(a -> new PontoHistorico(a.getCriadaEm(), a.getScore(), a.getClassificacao()))
			.toList();
		List<AlertaResponse> alertas = alertaService.daConta(contaId).stream().map(AlertaResponse::of).toList();
		return new ContaRiscoDetalhe(ContaRiscoResponse.of(conta), ultima, historico, alertas);
	}

	/** Subgrafo ao redor da conta (vizinhos até {@code profundidade} saltos) na janela de análise. */
	@Transactional(readOnly = true)
	public GrafoResponse grafo(Long contaId, int profundidade) {
		if (!contaRepository.existsById(contaId)) {
			throw new RecursoNaoEncontradoException("Conta", contaId);
		}
		int prof = Math.clamp(profundidade, 1, 4);
		Instant desde = Instant.now().minus(parametrosService.configuracao().janelaDias(), ChronoUnit.DAYS);
		GrafoTransacoes grafo = GrafoTransacoes.construir(transacaoRepository.arestasPix(desde));
		SubgrafoEgo ego = grafo.ego(contaId, prof, properties.grafoMaxNosEgo());
		Map<Long, Conta> contas = contaRepository.findComTitular(ego.contas())
			.stream()
			.collect(Collectors.toMap(Conta::getId, Function.identity()));
		List<NoGrafo> nos = ego.contas()
			.stream()
			.filter(contas::containsKey)
			.map(id -> {
				Conta c = contas.get(id);
				return new NoGrafo(id, c.getNumero(), c.getTitular().getNome(), c.getStatus(), c.getScoreRisco(),
						c.getClassificacaoRisco(), id.equals(contaId));
			})
			.toList();
		List<ArestaGrafo> arestas = ego.arestas()
			.stream()
			.map(a -> new ArestaGrafo(a.transacaoId(), a.origem(), a.destino(), a.valor(), a.timestamp()))
			.toList();
		return new GrafoResponse(contaId, prof, desde, nos, arestas);
	}

	@Transactional(readOnly = true)
	public List<CicloResponse> ciclos() {
		List<Ciclo> ciclos = analiseRiscoService.ultimosCiclos();
		Map<Long, Conta> contas = contaRepository
			.findComTitular(ciclos.stream().flatMap(c -> c.contas().stream()).distinct().toList())
			.stream()
			.collect(Collectors.toMap(Conta::getId, Function.identity()));
		return ciclos.stream()
			.map(c -> new CicloResponse(c.comprimento(), c.contas().stream().map(id -> {
				Conta conta = contas.get(id);
				return conta == null ? new ContaCiclo(id, null, null, null)
						: new ContaCiclo(id, conta.getNumero(), conta.getTitular().getNome(), conta.getScoreRisco());
			}).toList(), c.valorCirculado(), c.inicio(), c.fim()))
			.toList();
	}

	@Transactional(readOnly = true)
	public Page<ExecucaoResponse> execucoes(Pageable pageable) {
		return execucaoRepository.findAll(pageable).map(ExecucaoResponse::of);
	}

	@Transactional(readOnly = true)
	public Page<AlertaResponse> alertas(StatusAlerta status, Pageable pageable) {
		return alertaService.listar(status, pageable).map(AlertaResponse::of);
	}

	@Transactional
	public AlertaResponse resolverAlerta(Long alertaId, Long analistaId, StatusAlerta decisao, String observacao) {
		return AlertaResponse.of(alertaService.resolver(alertaId, analistaId, decisao, observacao));
	}

}
