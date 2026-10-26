package com.api.orangesentinel.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.api.orangesentinel.dto.TransacaoDtos.PixRequest;
import com.api.orangesentinel.dto.TransacaoDtos.TransacaoResponse;
import com.api.orangesentinel.security.UsuarioLogado;
import com.api.orangesentinel.service.TransferenciaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/pix")
@Tag(name = "Pix")
public class PixController {

	private final TransferenciaService service;

	public PixController(TransferenciaService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Envia um Pix",
			description = "O status retornado pode ser CONCLUIDA, RETIDA (bloqueio cautelar) ou RECUSADA pelo motor antifraude. "
					+ "Reenvios com o mesmo header Idempotency-Key retornam a transação original.")
	public TransacaoResponse enviar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PixRequest req,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
		return service.pix(UsuarioLogado.id(jwt), req, idempotencyKey);
	}

}
