package com.api.orangesentinel.service.antifraude;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.api.orangesentinel.exception.ConflitoException;
import com.api.orangesentinel.model.AnaliseRisco;
import com.api.orangesentinel.model.Conta;
import com.api.orangesentinel.model.ExecucaoAnalise;
import com.api.orangesentinel.model.enums.Classificacao;
import com.api.orangesentinel.model.enums.OrigemAlerta;
import com.api.orangesentinel.repository.AnaliseRiscoRepository;
import com.api.orangesentinel.repository.ContaRepository;
import com.api.orangesentinel.repository.ContaRepository.ContaCriacao;
import com.api.orangesentinel.repository.ExecucaoAnaliseRepository;
import com.api.orangesentinel.repository.TransacaoRepository;
import com.api.orangesentinel.service.AlertaService;
import com.api.orangesentinel.service.ParametrosRiscoService;
import com.api.orangesentinel.service.antifraude.grafo.ArestaTransacao;
import com.api.orangesentinel.service.antifraude.grafo.Ciclo;
import com.api.orangesentinel.service.antifraude.grafo.GrafoTransacoes;
import com.api.orangesentinel.service.antifraude.grafo.GraphMetricsCalculator;
import com.api.orangesentinel.service.antifraude.grafo.MetricasConta;
import com.api.orangesentinel.service.antifraude.grafo.MovimentoExterno;
import com.api.orangesentinel.service.antifraude.grafo.ResultadoGrafo;
import com.api.orangesentinel.service.antifraude.score.ConfiguracaoRisco;
import com.api.orangesentinel.service.antifraude.score.RiskScorer;
import com.api.orangesentinel.service.antifraude.score.ScoreResultado;

/**
 * Análise completa da rede: monta o grafo da janela em memória, calcula as métricas de cada conta, aplica o modelo
 * de risco e persiste os resultados. Contas classificadas como LARANJA passam a SOB_ANALISE e ganham alerta.
 */
@Service
public class AnaliseRiscoService {

	private static final Logger log = LoggerFactory.getLogger(AnaliseRiscoService.class);

	private static final int LOTE_IDS = 1000;

	private final AtomicBoolean emExecucao = new AtomicBoolean(false);

	private volatile List<Ciclo> ultimosCiclos = List.of();

	private final TransacaoRepository transacaoRepository;

	private final ContaRepository contaRepository;

	private final AnaliseRiscoRepository analiseRepository;

	private final ExecucaoAnaliseRepository execucaoRepository;

	private final ParametrosRiscoService parametrosService;

	private final AlertaService alertaService;

	private final RiskScorer scorer;

	private final TransactionTemplate tx;

	public AnaliseRiscoService(TransacaoRepository transacaoRepository, ContaRepository contaRepository,
			AnaliseRiscoRepository analiseRepository, ExecucaoAnaliseRepository execucaoRepository,
			ParametrosRiscoService parametrosService, AlertaService alertaService, RiskScorer scorer,
			TransactionTemplate tx) {
		this.transacaoRepository = transacaoRepository;
		this.contaRepository = contaRepository;
		this.analiseRepository = analiseRepository;
		this.execucaoRepository = execucaoRepository;
		this.parametrosService = parametrosService;
		this.alertaService = alertaService;
		this.scorer = scorer;
		this.tx = tx;
	}

	public boolean isEmExecucao() {
		return emExecucao.get();
	}

	/** Ciclos encontrados na última execução (mantidos em memória). */
	public List<Ciclo> ultimosCiclos() {
		return ultimosCiclos;
	}

	public ExecucaoAnalise executar(String disparadaPor) {
		if (!emExecucao.compareAndSet(false, true)) {
			throw new ConflitoException("Já existe uma análise em execução");
		}
		try {
			return executarInterno(disparadaPor);
		}
		finally {
			emExecucao.set(false);
		}
	}

	private ExecucaoAnalise executarInterno(String disparadaPor) {
		Instant inicio = Instant.now();
		ConfiguracaoRisco config = parametrosService.configuracao();
		Instant desde = inicio.minus(config.janelaDias(), ChronoUnit.DAYS);

		// 1. Leitura (fora da transação de escrita)
		List<ArestaTransacao> arestas = transacaoRepository.arestasPix(desde);
		List<MovimentoExterno> saques = transacaoRepository.saques(desde);
		GrafoTransacoes grafo = GrafoTransacoes.construir(arestas);
		Map<Long, Instant> criacao = criacaoDasContas(new ArrayList<>(grafo.contas()));

		// 2. Grafo + score em memória
		ResultadoGrafo resultado = GraphMetricsCalculator.calcular(grafo, saques, criacao, config, inicio);
		Map<Long, ScoreResultado> scores = new HashMap<>();
		resultado.metricas().forEach((conta, m) -> scores.put(conta, scorer.avaliar(m, config)));

		// 3. Persistência
		ExecucaoAnalise execucao = tx.execute(status -> persistir(inicio, desde, disparadaPor, resultado, scores));
		ultimosCiclos = resultado.ciclos();
		log.info("Análise #{} concluída: {} contas, {} arestas, {} ciclos, {} suspeitas, {} laranjas em {} ms",
				execucao.getId(), execucao.getNContas(), execucao.getNArestas(), execucao.getNCiclos(),
				execucao.getNSuspeitas(), execucao.getNLaranjas(), execucao.getDuracaoMs());
		return execucao;
	}

	private ExecucaoAnalise persistir(Instant inicio, Instant desde, String disparadaPor, ResultadoGrafo resultado,
			Map<Long, ScoreResultado> scores) {
		ExecucaoAnalise execucao = execucaoRepository.save(new ExecucaoAnalise(inicio, desde, disparadaPor));
		Instant agora = Instant.now();
		int suspeitas = 0;
		int laranjas = 0;
		for (Map.Entry<Long, MetricasConta> e : resultado.metricas().entrySet()) {
			Long contaId = e.getKey();
			ScoreResultado s = scores.get(contaId);
			Conta conta = contaRepository.getReferenceById(contaId);
			AnaliseRisco analise = analiseRepository.save(new AnaliseRisco(execucao, conta, e.getValue(), s, agora));
			contaRepository.atualizarRisco(contaId, s.score(), s.classificacao(), agora);
			if (s.classificacao() == Classificacao.SUSPEITA) {
				suspeitas++;
			}
			else if (s.classificacao() == Classificacao.LARANJA) {
				laranjas++;
				contaRepository.colocarSobAnalise(contaId);
				alertaService.registrar(conta, OrigemAlerta.BATCH, s.score(), s.motivos(), analise);
			}
		}
		execucao.finalizar(Instant.now(), resultado.nVertices(), resultado.nArestas(), resultado.ciclos().size(),
				suspeitas, laranjas);
		return execucao;
	}

	private Map<Long, Instant> criacaoDasContas(List<Long> ids) {
		Map<Long, Instant> criacao = new HashMap<>();
		for (int i = 0; i < ids.size(); i += LOTE_IDS) {
			for (ContaCriacao c : contaRepository.buscarCriacao(ids.subList(i, Math.min(ids.size(), i + LOTE_IDS)))) {
				criacao.put(c.getId(), c.getCriadaEm());
			}
		}
		return criacao;
	}

}
