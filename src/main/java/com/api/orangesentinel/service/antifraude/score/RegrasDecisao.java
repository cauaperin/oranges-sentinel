package com.api.orangesentinel.service.antifraude.score;

import java.util.List;

import com.api.orangesentinel.service.antifraude.grafo.MetricasConta;

/**
 * Árvore de decisão simples aplicada sobre o score logístico. Cobre padrões inequívocos (que devem ser sinalizados
 * mesmo que a soma ponderada fique abaixo do limiar) e casos com pouca evidência (que não devem gerar falso
 * positivo).
 *
 * <pre>
 * total de transações &lt; mínimo?               → score ≤ limiarSuspeita − 1   (evidência insuficiente)
 * senão, em ciclo E (repasse rápido ≥ 70%
 *        OU mediana de repasse ≤ 6 h)?          → score ≥ 85                   (ciclo de lavagem)
 * senão, fan-in ≥ 8 E repasse rápido ≥ 80%
 *        E taxa de repasse ≥ 80%?              → score ≥ 90                   (funil clássico)
 * senão, conta &lt; 30 dias, ≥ 2 recebimentos,
 *        repasse rápido ≥ 80%
 *        E taxa de repasse ≥ 90%?              → score ≥ 82                   (conta de passagem)
 * </pre>
 */
public final class RegrasDecisao {

	static final int SCORE_CICLO_LAVAGEM = 85;

	static final int SCORE_FUNIL_CLASSICO = 90;

	static final int SCORE_CONTA_PASSAGEM = 82;

	static final int DIAS_CONTA_PASSAGEM = 30;

	/** Em um ciclo, a conta que injeta o dinheiro espera algumas horas entre as voltas. */
	static final double MEDIANA_REPASSE_CICLO_SEG = 6 * 3600;

	private RegrasDecisao() {
	}

	public static int aplicar(MetricasConta m, ConfiguracaoRisco c, int score, List<String> motivos) {
		if (m.totalTransacoes() < c.minTransacoes()) {
			if (score >= c.limiarSuspeita()) {
				motivos.add("Score limitado: apenas %d transação(ões) na janela (mínimo %d)"
					.formatted(m.totalTransacoes(), c.minTransacoes()));
			}
			return Math.min(score, c.limiarSuspeita() - 1);
		}
		boolean repasseEmHoras = m.medianaRepasseSeg() != null && m.medianaRepasseSeg() <= MEDIANA_REPASSE_CICLO_SEG;
		if (m.emCiclo() && (m.fracRepasseRapido() >= 0.7 || repasseEmHoras) && score < SCORE_CICLO_LAVAGEM) {
			motivos.add(0, "Regra: ciclo fechado com o dinheiro repassado em poucas horas");
			return SCORE_CICLO_LAVAGEM;
		}
		if (m.inDegree() >= 8 && m.fracRepasseRapido() >= 0.8 && m.taxaRepasse() >= 0.8
				&& score < SCORE_FUNIL_CLASSICO) {
			motivos.add(0, "Regra: padrão funil clássico (muitas origens, repasse quase total e imediato)");
			return SCORE_FUNIL_CLASSICO;
		}
		if (m.idadeContaDias() < DIAS_CONTA_PASSAGEM && m.qtdRecebidas() >= 2 && m.fracRepasseRapido() >= 0.8
				&& m.taxaRepasse() >= 0.9 && score < SCORE_CONTA_PASSAGEM) {
			motivos.add(0, "Regra: conta recém-aberta usada apenas como passagem de recursos");
			return SCORE_CONTA_PASSAGEM;
		}
		return score;
	}

}
