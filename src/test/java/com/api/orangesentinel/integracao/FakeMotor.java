package com.api.orangesentinel.integracao;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.api.orangesentinel.client.MotorClient;
import com.api.orangesentinel.client.MotorDtos.Acao;
import com.api.orangesentinel.client.MotorDtos.AvaliacaoPixRequest;
import com.api.orangesentinel.client.MotorDtos.AvaliacaoPixResponse;

/**
 * Motor falso: devolve a resposta configurada pelo teste e guarda os pedidos recebidos.
 */
class FakeMotor implements MotorClient {

	final List<AvaliacaoPixRequest> pedidos = new ArrayList<>();

	Function<AvaliacaoPixRequest, AvaliacaoPixResponse> regra = req -> AvaliacaoPixResponse.aprovarSemAnalise();

	void reset() {
		pedidos.clear();
		regra = req -> AvaliacaoPixResponse.aprovarSemAnalise();
	}

	/** Retém todo Pix para a conta de destino informada. */
	void reterPixPara(Long destinoId) {
		regra = req -> req.destino().id().equals(destinoId)
				? new AvaliacaoPixResponse(Acao.RETER, destinoId, 91, List.of("Tempo real: destino sinalizado como laranja"))
				: AvaliacaoPixResponse.aprovarSemAnalise();
	}

	@Override
	public AvaliacaoPixResponse avaliarPix(AvaliacaoPixRequest req) {
		pedidos.add(req);
		return regra.apply(req);
	}

}
