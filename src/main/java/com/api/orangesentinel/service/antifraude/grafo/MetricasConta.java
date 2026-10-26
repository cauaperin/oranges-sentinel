package com.api.orangesentinel.service.antifraude.grafo;

import java.math.BigDecimal;

/**
 * Métricas extraídas do grafo para uma conta (vértice).
 *
 * @param inDegree contas distintas que enviaram para esta (fan-in)
 * @param outDegree contas distintas que receberam desta (fan-out)
 * @param taxaRepasse min(recebido, enviado) / max(recebido, enviado): próximo de 1 = a conta não retém saldo
 * @param medianaRepasseSeg mediana do tempo entre receber e repassar (null se não houve repasse)
 * @param fracRepasseRapido fração do valor enviado que saiu até N minutos após ter entrado
 * @param menorCiclo comprimento do menor ciclo temporal em que a conta participa (0 = nenhum)
 * @param centralidadeRelativa betweenness dividida pela maior betweenness do grafo (0..1)
 */
public record MetricasConta(Long contaId, int inDegree, int outDegree, int qtdRecebidas, int qtdEnviadas,
		BigDecimal totalRecebido, BigDecimal totalEnviado, double taxaRepasse, Double medianaRepasseSeg,
		double fracRepasseRapido, boolean emCiclo, int menorCiclo, double betweenness, double centralidadeRelativa,
		double pageRank, int idadeContaDias) {

	public int totalTransacoes() {
		return qtdRecebidas + qtdEnviadas;
	}

}
