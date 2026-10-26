package com.api.orangesentinel.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.api.orangesentinel.dto.ParametrosDtos.ParametrosRiscoRequest;
import com.api.orangesentinel.dto.ParametrosDtos.ParametrosRiscoResponse;
import com.api.orangesentinel.service.ParametrosRiscoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/parametros-risco")
@Tag(name = "Admin - Parâmetros de risco")
public class ParametrosRiscoController {

	private final ParametrosRiscoService service;

	public ParametrosRiscoController(ParametrosRiscoService service) {
		this.service = service;
	}

	@GetMapping
	public ParametrosRiscoResponse obter() {
		return ParametrosRiscoResponse.of(service.obter());
	}

	@PutMapping
	@Operation(summary = "Atualiza pesos, limiares e janelas do modelo (vale a partir da próxima análise)")
	public ParametrosRiscoResponse atualizar(@Valid @RequestBody ParametrosRiscoRequest req) {
		return ParametrosRiscoResponse.of(service.atualizar(req));
	}

}
