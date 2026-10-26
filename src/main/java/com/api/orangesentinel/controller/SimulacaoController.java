package com.api.orangesentinel.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.api.orangesentinel.dto.SimulacaoDtos.AvaliacaoResponse;
import com.api.orangesentinel.dto.SimulacaoDtos.SimulacaoRequest;
import com.api.orangesentinel.dto.SimulacaoDtos.SimulacaoResponse;
import com.api.orangesentinel.service.simulacao.SimulacaoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/simulacao")
@Tag(name = "Admin - Simulador", description = "Gera dados sintéticos com redes de laranjas e avalia o motor")
public class SimulacaoController {

	private final SimulacaoService service;

	public SimulacaoController(SimulacaoService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Gera clientes normais, empresas e redes de laranjas (funil, dispersão, cadeia, ciclo)",
			description = "Mesma seed ⇒ mesmos dados. Depois, rode POST /api/fraude/analises e consulte a avaliação.")
	public SimulacaoResponse gerar(@Valid @RequestBody(required = false) SimulacaoRequest req) {
		return service.gerar(req != null ? req : new SimulacaoRequest(null, null, null, null, null, null, null));
	}

	@DeleteMapping
	@Operation(summary = "Remove todos os dados simulados")
	public Map<String, Integer> limpar() {
		return Map.of("usuariosRemovidos", service.limpar());
	}

	@GetMapping("/avaliacao")
	@Operation(summary = "Precisão, recall e F1 do motor contra o rótulo verdadeiro das contas simuladas")
	public AvaliacaoResponse avaliacao() {
		return service.avaliar();
	}

}
