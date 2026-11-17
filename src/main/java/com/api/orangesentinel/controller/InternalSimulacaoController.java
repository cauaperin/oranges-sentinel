package com.api.orangesentinel.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;

import com.api.orangesentinel.dto.InternalDtos.ContaSimulada;
import com.api.orangesentinel.dto.InternalDtos.IdsResponse;
import com.api.orangesentinel.dto.InternalDtos.TransacaoSimulada;
import com.api.orangesentinel.service.SimulacaoBancoService;

/**
 * API entre serviços usada pelo simulador do motor (protegida por {@code X-Internal-Key}).
 */
@Hidden
@RestController
@RequestMapping("/internal/simulacao")
public class InternalSimulacaoController {

	private final SimulacaoBancoService service;

	public InternalSimulacaoController(SimulacaoBancoService service) {
		this.service = service;
	}

	@PostMapping("/contas")
	@ResponseStatus(HttpStatus.CREATED)
	public IdsResponse criarContas(@Valid @RequestBody List<@Valid ContaSimulada> contas) {
		return new IdsResponse(service.criarContas(contas));
	}

	@PostMapping("/transacoes")
	@ResponseStatus(HttpStatus.CREATED)
	public void criarTransacoes(@Valid @RequestBody List<@Valid TransacaoSimulada> transacoes) {
		service.criarTransacoes(transacoes);
	}

	@DeleteMapping
	public Map<String, Integer> limpar() {
		return Map.of("contasRemovidas", service.limpar());
	}

}
