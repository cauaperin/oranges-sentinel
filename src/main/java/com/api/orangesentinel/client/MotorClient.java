package com.api.orangesentinel.client;

import com.api.orangesentinel.client.MotorDtos.AvaliacaoPixRequest;
import com.api.orangesentinel.client.MotorDtos.AvaliacaoPixResponse;

/**
 * Acesso do banco ao motor antifraude.
 */
public interface MotorClient {

	/**
	 * Avalia um Pix antes de ele ser efetivado. Nunca lança exceção: se o motor estiver fora do ar, lento ou
	 * responder erro, devolve {@link AvaliacaoPixResponse#aprovarSemAnalise()} (fail-open) para o banco não parar por
	 * causa do motor.
	 */
	AvaliacaoPixResponse avaliarPix(AvaliacaoPixRequest req);

}
