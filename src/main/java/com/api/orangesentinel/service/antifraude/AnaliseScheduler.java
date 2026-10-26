package com.api.orangesentinel.service.antifraude;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.api.orangesentinel.exception.ConflitoException;

/**
 * Dispara a análise completa do grafo periodicamente ({@code antifraude.cron}).
 */
@Component
@ConditionalOnProperty(name = "antifraude.agendamento-habilitado", havingValue = "true")
public class AnaliseScheduler {

	private static final Logger log = LoggerFactory.getLogger(AnaliseScheduler.class);

	private final AnaliseRiscoService service;

	public AnaliseScheduler(AnaliseRiscoService service) {
		this.service = service;
	}

	@Scheduled(cron = "${antifraude.cron}")
	public void executar() {
		try {
			service.executar("agendamento");
		}
		catch (ConflitoException e) {
			log.info("Análise agendada ignorada: {}", e.getMessage());
		}
		catch (RuntimeException e) {
			log.error("Falha na análise agendada", e);
		}
	}

}
