package com.api.orangesentinel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.api.orangesentinel.model.AlertaFraude;
import com.api.orangesentinel.model.AnaliseRisco;
import com.api.orangesentinel.model.Conta;
import com.api.orangesentinel.model.ExecucaoAnalise;
import com.api.orangesentinel.model.enums.Classificacao;
import com.api.orangesentinel.model.enums.OrigemAlerta;
import com.api.orangesentinel.model.enums.StatusAlerta;
import com.api.orangesentinel.model.enums.StatusConta;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class FraudeDtos {

	private FraudeDtos() {
	}

	static List<String> linhas(String texto) {
		return texto == null || texto.isBlank() ? List.of() : Arrays.asList(texto.split("\n"));
	}

	public record ContaRiscoResponse(Long contaId, String agencia, String numero, String titular, StatusConta status,
			Integer score, Classificacao classificacao, Instant analisadoEm) {

		public static ContaRiscoResponse of(Conta c) {
			return new ContaRiscoResponse(c.getId(), c.getAgencia(), c.getNumero(), c.getTitular().getNome(),
					c.getStatus(), c.getScoreRisco(), c.getClassificacaoRisco(), c.getAnalisadoEm());
		}

	}

	public record AnaliseResponse(Long id, Long execucaoId, int score, Classificacao classificacao, int inDegree,
			int outDegree, int qtdRecebidas, int qtdEnviadas, BigDecimal totalRecebido, BigDecimal totalEnviado,
			double taxaRepasse, Double medianaRepasseSeg, double fracRepasseRapido, boolean emCiclo,
			double betweenness, double centralidadeRelativa, double pageRank, int idadeContaDias,
			String contribuicoes, List<String> motivos, Instant criadaEm) {

		public static AnaliseResponse of(AnaliseRisco a) {
			return new AnaliseResponse(a.getId(), a.getExecucao().getId(), a.getScore(), a.getClassificacao(),
					a.getInDegree(), a.getOutDegree(), a.getQtdRecebidas(), a.getQtdEnviadas(), a.getTotalRecebido(),
					a.getTotalEnviado(), a.getTaxaRepasse(), a.getMedianaRepasseSeg(), a.getFracRepasseRapido(),
					a.isEmCiclo(), a.getBetweenness(), a.getCentralidadeRelativa(), a.getPageRank(),
					a.getIdadeContaDias(), a.getContribuicoes(), linhas(a.getMotivos()), a.getCriadaEm());
		}

	}

	public record PontoHistorico(Instant data, int score, Classificacao classificacao) {
	}

	public record ContaRiscoDetalhe(ContaRiscoResponse conta, AnaliseResponse ultimaAnalise,
			List<PontoHistorico> historico, List<AlertaResponse> alertas) {
	}

	public record AlertaResponse(Long id, Long contaId, String contaNumero, String titular, OrigemAlerta origem,
			Integer score, List<String> motivos, StatusAlerta status, String analista, String observacao,
			Instant criadoEm, Instant atualizadoEm, Instant resolvidoEm) {

		public static AlertaResponse of(AlertaFraude a) {
			Conta c = a.getConta();
			return new AlertaResponse(a.getId(), c.getId(), c.getNumero(), c.getTitular().getNome(), a.getOrigem(),
					a.getScore(), linhas(a.getMotivos()), a.getStatus(),
					a.getAnalista() == null ? null : a.getAnalista().getNome(), a.getObservacao(), a.getCriadoEm(),
					a.getAtualizadoEm(), a.getResolvidoEm());
		}

	}

	public record ResolverAlertaRequest(@NotNull StatusAlerta decisao, @Size(max = 500) String observacao) {
	}

	public record ExecucaoResponse(Long id, Instant inicio, Instant fim, Instant janelaInicio, int contas,
			int arestas, int ciclos, int suspeitas, int laranjas, Long duracaoMs, String disparadaPor) {

		public static ExecucaoResponse of(ExecucaoAnalise e) {
			return new ExecucaoResponse(e.getId(), e.getInicio(), e.getFim(), e.getJanelaInicio(), e.getNContas(),
					e.getNArestas(), e.getNCiclos(), e.getNSuspeitas(), e.getNLaranjas(), e.getDuracaoMs(),
					e.getDisparadaPor());
		}

	}

	/** Nó do grafo no formato esperado por bibliotecas como Cytoscape.js / D3. */
	public record NoGrafo(Long id, String numero, String titular, StatusConta status, Integer score,
			Classificacao classificacao, boolean centro) {
	}

	public record ArestaGrafo(Long id, Long source, Long target, BigDecimal valor, Instant timestamp) {
	}

	public record GrafoResponse(Long centro, int profundidade, Instant janelaInicio, List<NoGrafo> nodes,
			List<ArestaGrafo> edges) {
	}

	public record ContaCiclo(Long contaId, String numero, String titular, Integer score) {
	}

	public record CicloResponse(int comprimento, List<ContaCiclo> contas, BigDecimal valorCirculado, Instant inicio,
			Instant fim) {
	}

}
