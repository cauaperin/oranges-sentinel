package com.api.orangesentinel.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import com.api.orangesentinel.dto.AuthDtos.LoginRequest;
import com.api.orangesentinel.dto.AuthDtos.RegistroRequest;
import com.api.orangesentinel.dto.AuthDtos.TokenResponse;
import com.api.orangesentinel.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@SecurityRequirements
@Tag(name = "Autenticação")
public class AuthController {

	private final AuthService service;

	public AuthController(AuthService service) {
		this.service = service;
	}

	@PostMapping("/registro")
	@ResponseStatus(HttpStatus.CREATED)
	public TokenResponse registrar(@Valid @RequestBody RegistroRequest req) {
		return service.registrar(req);
	}

	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest req) {
		return service.login(req);
	}

}
