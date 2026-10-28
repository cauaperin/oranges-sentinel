package com.api.orangesentinel.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import com.api.orangesentinel.dto.ContaDtos.AbrirContaRequest;
import com.api.orangesentinel.dto.ContaDtos.ContaResponse;
import com.api.orangesentinel.security.UsuarioLogado;
import com.api.orangesentinel.service.ContaService;

@RestController
@RequestMapping("/api/contas")
@Tag(name = "Contas")
public class ContaController {

	private final ContaService service;

	public ContaController(ContaService service) {
		this.service = service;
	}

	@GetMapping
	public List<ContaResponse> listar(@AuthenticationPrincipal Jwt jwt) {
		return service.listarDoUsuario(UsuarioLogado.id(jwt)).stream().map(ContaResponse::of).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ContaResponse abrir(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AbrirContaRequest req) {
		return ContaResponse.of(service.abrir(UsuarioLogado.id(jwt), UsuarioLogado.nome(jwt), UsuarioLogado.cpf(jwt), req.tipo()));
	}

	@GetMapping("/{id}")
	public ContaResponse buscar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return ContaResponse.of(service.buscarDoUsuario(id, UsuarioLogado.id(jwt)));
	}

	/** Encerra a conta (soft delete). Exige saldo zerado. */
	@DeleteMapping("/{id}")
	public ContaResponse encerrar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return ContaResponse.of(service.encerrar(id, UsuarioLogado.id(jwt)));
	}

}
