package com.api.orangesentinel.client;

import java.net.http.HttpClient;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.api.orangesentinel.client.MotorDtos.AvaliacaoPixRequest;
import com.api.orangesentinel.client.MotorDtos.AvaliacaoPixResponse;
import com.api.orangesentinel.security.InternalKeyFilter;

@Component
public class MotorRestClient implements MotorClient {

	private static final Logger log = LoggerFactory.getLogger(MotorRestClient.class);

	private final RestClient client;

	/** Vazio desativa a avaliação em tempo real (todo Pix é aprovado sem análise). */
	public MotorRestClient(@Value("${app.motor.url:}") String url, @Value("${app.internal.chave:}") String chave,
			@Value("${app.motor.timeout:PT3S}") Duration timeout) {
		if (url.isBlank()) {
			this.client = null;
			return;
		}
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(timeout).build());
		factory.setReadTimeout(timeout);
		this.client = RestClient.builder()
			.baseUrl(url)
			.requestFactory(factory)
			.defaultHeader(InternalKeyFilter.HEADER, chave)
			.build();
	}

	@Override
	public AvaliacaoPixResponse avaliarPix(AvaliacaoPixRequest req) {
		if (client == null) {
			return AvaliacaoPixResponse.aprovarSemAnalise();
		}
		try {
			AvaliacaoPixResponse resposta = client.post()
				.uri("/internal/avaliacoes/pix")
				.body(req)
				.retrieve()
				.body(AvaliacaoPixResponse.class);
			return resposta == null ? AvaliacaoPixResponse.aprovarSemAnalise() : resposta;
		}
		catch (RuntimeException e) {
			log.warn("Motor antifraude indisponível; Pix aprovado sem análise em tempo real: {}", e.getMessage());
			return AvaliacaoPixResponse.aprovarSemAnalise();
		}
	}

}
