package com.api.orangesentinel.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import com.api.orangesentinel.dto.UsuarioDtos.AtualizarPerfilRequest;
import com.api.orangesentinel.dto.UsuarioDtos.UsuarioResponse;
import com.api.orangesentinel.security.UsuarioLogado;
import com.api.orangesentinel.service.UsuarioService;

@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuários", description = "Perfil do usuário autenticado")
public class UsuarioController {

	private final UsuarioService service;

	public UsuarioController(UsuarioService service) {
		this.service = service;
	}

	@GetMapping("/me")
	public UsuarioResponse me(@AuthenticationPrincipal Jwt jwt) {
		return UsuarioResponse.of(service.buscar(UsuarioLogado.id(jwt)));
	}

	@PutMapping("/me")
	public UsuarioResponse atualizar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AtualizarPerfilRequest req) {
		return UsuarioResponse.of(service.atualizarPerfil(UsuarioLogado.id(jwt), req));
	}

}
