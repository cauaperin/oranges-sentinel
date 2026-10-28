package com.api.orangesentinel.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;

import com.api.orangesentinel.dto.ArestaPix;
import com.api.orangesentinel.dto.SaqueRegistrado;
import com.api.orangesentinel.repository.TransacaoRepository;

/**
 * Transações para o motor montar o grafo (protegida por {@code X-Internal-Key}).
 */
@Hidden
@RestController
@RequestMapping("/internal/transacoes")
public class InternalTransacaoController {

	private final TransacaoRepository repository;

	public InternalTransacaoController(TransacaoRepository repository) {
		this.repository = repository;
	}

	@GetMapping("/arestas")
	public List<ArestaPix> arestas(@RequestParam Instant desde) {
		return repository.arestasPix(desde);
	}

	@GetMapping("/saques")
	public List<SaqueRegistrado> saques(@RequestParam Instant desde) {
		return repository.saques(desde);
	}

}
