package com.api.orangesentinel.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import com.api.orangesentinel.dto.PageResponse;
import com.api.orangesentinel.dto.UsuarioDtos.AlterarUsuarioRequest;
import com.api.orangesentinel.dto.UsuarioDtos.CriarUsuarioRequest;
import com.api.orangesentinel.dto.UsuarioDtos.UsuarioResponse;
import com.api.orangesentinel.model.enums.Role;
import com.api.orangesentinel.service.UsuarioService;

@RestController
@RequestMapping("/api/admin/usuarios")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Usuários")
public class AdminUsuarioController {

	private final UsuarioService service;

	public AdminUsuarioController(UsuarioService service) {
		this.service = service;
	}

	@GetMapping
	public PageResponse<UsuarioResponse> listar(@RequestParam(required = false) Role role,
			@PageableDefault(size = 20, sort = "id") Pageable pageable) {
		return PageResponse.of(service.listar(role, pageable).map(UsuarioResponse::of));
	}

	@GetMapping("/{id}")
	public UsuarioResponse buscar(@PathVariable Long id) {
		return UsuarioResponse.of(service.buscar(id));
	}

	/** Cria usuários de qualquer perfil (ex.: analistas antifraude). */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UsuarioResponse criar(@Valid @RequestBody CriarUsuarioRequest req) {
		return UsuarioResponse.of(service.criar(req.nome(), req.cpf(), req.email(), req.senha(), req.role()));
	}

	@PatchMapping("/{id}")
	public UsuarioResponse alterar(@PathVariable Long id, @RequestBody AlterarUsuarioRequest req) {
		return UsuarioResponse.of(service.alterar(id, req));
	}

}
