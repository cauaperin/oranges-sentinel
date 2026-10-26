package com.api.orangesentinel.service.antifraude.score;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.api.orangesentinel.model.enums.Classificacao;

/**
 * Resultado explicável do score: valor final, classificação, contribuição de cada feature (wᵢ·xᵢ) e motivos em
 * linguagem natural para o analista.
 */
public record ScoreResultado(int score, Classificacao classificacao, Map<String, Double> contribuicoes,
		List<String> motivos) {

	public String contribuicoesJson() {
		return contribuicoes.entrySet()
			.stream()
			.map(e -> String.format(Locale.ROOT, "\"%s\":%.4f", e.getKey(), e.getValue()))
			.collect(Collectors.joining(",", "{", "}"));
	}

	public String motivosTexto() {
		return String.join("\n", motivos);
	}

}
