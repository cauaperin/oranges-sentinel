package com.api.orangesentinel.service.antifraude;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.api.orangesentinel.model.Conta;
import com.api.orangesentinel.model.enums.Classificacao;
import com.api.orangesentinel.model.enums.StatusConta;
import com.api.orangesentinel.repository.TransacaoRepository;
import com.api.orangesentinel.repository.TransacaoRepository.RecebimentosRecentes;
import com.api.orangesentinel.service.ParametrosRiscoService;
import com.api.orangesentinel.service.antifraude.score.ConfiguracaoRisco;

/**
 * Checagem leve executada em cada Pix, sem montar o grafo global. Usa o último score calculado pela análise
 * completa e uma consulta local de "repasse rápido".
 */
@Component
public class RealTimeRiskGuard {

	/** Mínimo de remetentes distintos recentes para caracterizar funil em tempo real. */
	static final int MIN_REMETENTES_FUNIL = 3;

	/** Fração do valor do Pix que precisa ter acabado de chegar para caracterizar repasse imediato. */
	static final BigDecimal FRACAO_REPASSE = new BigDecimal("0.8");

	public enum Acao {

		APROVAR, APROVAR_COM_ALERTA, RETER, RECUSAR

	}

	/**
	 * @param contaAlertada conta que deve receber/atualizar alerta (null se nenhuma)
	 */
	public record Decisao(Acao acao, Conta contaAlertada, Integer score, List<String> motivos) {

		static Decisao aprovar() {
			return new Decisao(Acao.APROVAR, null, null, List.of());
		}

		public String motivoTexto() {
			return String.join("; ", motivos);
		}

	}

	private final TransacaoRepository transacaoRepository;

	private final ParametrosRiscoService parametrosService;

	public RealTimeRiskGuard(TransacaoRepository transacaoRepository, ParametrosRiscoService parametrosService) {
		this.transacaoRepository = transacaoRepository;
		this.parametrosService = parametrosService;
	}

	public Decisao avaliar(Conta origem, Conta destino, BigDecimal valor, Instant agora) {
		if (destino.getStatus() == StatusConta.BLOQUEADA || destino.getStatus() == StatusConta.ENCERRADA) {
			return new Decisao(Acao.RECUSAR, null, null,
					List.of("Conta de destino " + destino.getStatus().name().toLowerCase() + " para recebimentos"));
		}
		if (origem.getStatus() == StatusConta.SOB_ANALISE) {
			return new Decisao(Acao.RECUSAR, null, null,
					List.of("Conta de origem em análise antifraude: envios suspensos até a decisão do analista"));
		}
		if (destino.getClassificacaoRisco() == Classificacao.LARANJA || destino.getStatus() == StatusConta.SOB_ANALISE) {
			return new Decisao(Acao.RETER, destino, destino.getScoreRisco(),
					List.of("Tempo real: Pix para conta sinalizada como possível laranja (score %s) foi retido"
						.formatted(destino.getScoreRisco() == null ? "n/d" : destino.getScoreRisco())));
		}

		ConfiguracaoRisco cfg = parametrosService.configuracao();
		Duration janela = cfg.limiteRepasseRapido();
		RecebimentosRecentes recentes = transacaoRepository.recebimentosRecentes(origem.getId(), agora.minus(janela));
		if (recentes.getRemetentes() >= MIN_REMETENTES_FUNIL
				&& recentes.getTotal().compareTo(valor.multiply(FRACAO_REPASSE)) >= 0) {
			return new Decisao(Acao.APROVAR_COM_ALERTA, origem, null, List.of(String.format(Locale.ROOT,
					"Tempo real: recebeu R$ %.2f de %d contas nos últimos %d min e repassou R$ %.2f em seguida",
					recentes.getTotal(), recentes.getRemetentes(), cfg.repasseRapidoMinutos(), valor)));
		}
		return Decisao.aprovar();
	}

}
