package com.api.orangesentinel.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.api.orangesentinel.client.MotorDtos.Acao;
import com.api.orangesentinel.client.MotorDtos.AvaliacaoPixRequest;
import com.api.orangesentinel.client.MotorDtos.ContaPix;
import com.api.orangesentinel.client.MotorRestClient;
import com.api.orangesentinel.model.enums.StatusConta;

/**
 * Fail-open: sem motor (desativado ou fora do ar) o Pix segue sem análise em tempo real.
 */
class MotorRestClientTest {

	private static final AvaliacaoPixRequest PEDIDO = new AvaliacaoPixRequest(
			new ContaPix(1L, StatusConta.ATIVA, null, null), new ContaPix(2L, StatusConta.ATIVA, null, null),
			BigDecimal.TEN, Instant.now(), List.of());

	@Test
	void motorDesativadoAprovaSemAnalise() {
		var resposta = new MotorRestClient("", "chave", Duration.ofSeconds(1)).avaliarPix(PEDIDO);

		assertThat(resposta.acao()).isEqualTo(Acao.APROVAR);
		assertThat(resposta.contaAlertadaId()).isNull();
	}

	@Test
	void motorForaDoArAprovaSemAnaliseSemLancarExcecao() {
		// Porta 1: ninguém escuta, a conexão é recusada na hora
		var resposta = new MotorRestClient("http://localhost:1", "chave", Duration.ofSeconds(1)).avaliarPix(PEDIDO);

		assertThat(resposta.acao()).isEqualTo(Acao.APROVAR);
	}

}
