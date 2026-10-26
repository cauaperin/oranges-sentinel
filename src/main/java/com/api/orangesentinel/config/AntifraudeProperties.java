package com.api.orangesentinel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração de infraestrutura do motor antifraude. Os parâmetros do modelo de risco (pesos, limiares) ficam
 * na tabela {@code parametros_risco}, editáveis em tempo de execução.
 */
@ConfigurationProperties("antifraude")
public record AntifraudeProperties(boolean agendamentoHabilitado, String cron, int grafoMaxNosEgo) {

	public AntifraudeProperties {
		if (grafoMaxNosEgo <= 0) {
			grafoMaxNosEgo = 300;
		}
	}

}
