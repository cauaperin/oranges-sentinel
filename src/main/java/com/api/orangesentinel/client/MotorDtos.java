package com.api.orangesentinel.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.api.orangesentinel.model.enums.Classificacao;
import com.api.orangesentinel.model.enums.StatusConta;

/**
 * Contrato da API interna do serviço motor ({@code POST /internal/avaliacoes/pix}).
 */
public final class MotorDtos {

	private MotorDtos() {
	}

	public enum Acao {

		APROVAR, APROVAR_COM_ALERTA, RETER, RECUSAR

	}

	public record ContaPix(Long id, StatusConta status, Integer scoreRisco, Classificacao classificacaoRisco) {
	}

	public record Recebimento(Long remetenteId, BigDecimal valor, Instant em) {
	}

	public record AvaliacaoPixRequest(ContaPix origem, ContaPix destino, BigDecimal valor, Instant agora,
			List<Recebimento> recebimentosOrigem) {
	}

	public record AvaliacaoPixResponse(Acao acao, Long contaAlertadaId, Integer score, List<String> motivos) {

		/** Decisão usada quando o motor não responde: o Pix segue e a análise em lote revisa depois. */
		public static AvaliacaoPixResponse aprovarSemAnalise() {
			return new AvaliacaoPixResponse(Acao.APROVAR, null, null, List.of());
		}

		public String motivoTexto() {
			return motivos == null ? "" : String.join("; ", motivos);
		}

	}

}
