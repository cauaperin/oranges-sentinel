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

import com.api.orangesentinel.dto.ChavePixDtos.ChaveResponse;
import com.api.orangesentinel.dto.ChavePixDtos.CriarChaveRequest;
import com.api.orangesentinel.security.UsuarioLogado;
import com.api.orangesentinel.service.ChavePixService;

@RestController
@RequestMapping("/api/contas/{contaId}/chaves-pix")
@Tag(name = "Chaves Pix")
public class ChavePixController {

	private final ChavePixService service;

	public ChavePixController(ChavePixService service) {
		this.service = service;
	}

	@GetMapping
	public List<ChaveResponse> listar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long contaId) {
		return service.listar(contaId, UsuarioLogado.id(jwt)).stream().map(ChaveResponse::of).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ChaveResponse criar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long contaId,
			@Valid @RequestBody CriarChaveRequest req) {
		return ChaveResponse.of(service.criar(contaId, UsuarioLogado.id(jwt), req.tipo(), req.valor()));
	}

	@DeleteMapping("/{chaveId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remover(@AuthenticationPrincipal Jwt jwt, @PathVariable Long contaId, @PathVariable Long chaveId) {
		service.remover(contaId, chaveId, UsuarioLogado.id(jwt));
	}

}
