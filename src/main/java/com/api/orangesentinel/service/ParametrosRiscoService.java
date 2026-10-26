package com.api.orangesentinel.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api.orangesentinel.dto.ParametrosDtos.ParametrosRiscoRequest;
import com.api.orangesentinel.exception.RegraNegocioException;
import com.api.orangesentinel.model.ParametrosRisco;
import com.api.orangesentinel.repository.ParametrosRiscoRepository;
import com.api.orangesentinel.service.antifraude.score.ConfiguracaoRisco;

@Service
public class ParametrosRiscoService {

	private final ParametrosRiscoRepository repository;

	public ParametrosRiscoService(ParametrosRiscoRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public ParametrosRisco obter() {
		return repository.findById(ParametrosRisco.ID_UNICO)
			.orElseThrow(() -> new IllegalStateException("Parâmetros de risco não inicializados (migration V2)"));
	}

	@Transactional(readOnly = true)
	public ConfiguracaoRisco configuracao() {
		return ConfiguracaoRisco.of(obter());
	}

	@Transactional
	public ParametrosRisco atualizar(ParametrosRiscoRequest req) {
		if (req.limiarSuspeita() >= req.limiarLaranja()) {
			throw new RegraNegocioException("limiarSuspeita deve ser menor que limiarLaranja");
		}
		ParametrosRisco p = repository.findById(ParametrosRisco.ID_UNICO).orElseThrow();
		p.setIntercepto(req.intercepto());
		p.setPesoFanIn(req.pesoFanIn());
		p.setPesoFanOut(req.pesoFanOut());
		p.setPesoTaxaRepasse(req.pesoTaxaRepasse());
		p.setPesoRepasseRapido(req.pesoRepasseRapido());
		p.setPesoCiclo(req.pesoCiclo());
		p.setPesoCentralidade(req.pesoCentralidade());
		p.setPesoContaNova(req.pesoContaNova());
		p.setLimiarSuspeita(req.limiarSuspeita());
		p.setLimiarLaranja(req.limiarLaranja());
		p.setJanelaDias(req.janelaDias());
		p.setRepasseRapidoMinutos(req.repasseRapidoMinutos());
		p.setCicloMaxComprimento(req.cicloMaxComprimento());
		p.setCicloJanelaHoras(req.cicloJanelaHoras());
		p.setMinTransacoes(req.minTransacoes());
		p.setAtualizadoEm(Instant.now());
		return p;
	}

}
