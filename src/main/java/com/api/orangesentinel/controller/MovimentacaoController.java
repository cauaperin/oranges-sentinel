package com.api.orangesentinel.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.api.orangesentinel.dto.PageResponse;
import com.api.orangesentinel.dto.TransacaoDtos.MovimentacaoRequest;
import com.api.orangesentinel.dto.TransacaoDtos.TransacaoResponse;
import com.api.orangesentinel.security.UsuarioLogado;
import com.api.orangesentinel.service.TransferenciaService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/contas/{contaId}")
@Tag(name = "Movimentações", description = "Depósito, saque e extrato")
public class MovimentacaoController {

	private final TransferenciaService service;

	public MovimentacaoController(TransferenciaService service) {
		this.service = service;
	}

	@PostMapping("/deposito")
	@ResponseStatus(HttpStatus.CREATED)
	public TransacaoResponse depositar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long contaId,
			@Valid @RequestBody MovimentacaoRequest req) {
		return service.depositar(contaId, UsuarioLogado.id(jwt), req);
	}

	@PostMapping("/saque")
	@ResponseStatus(HttpStatus.CREATED)
	public TransacaoResponse sacar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long contaId,
			@Valid @RequestBody MovimentacaoRequest req) {
		return service.sacar(contaId, UsuarioLogado.id(jwt), req);
	}

	/** Extrato paginado. Sem datas, retorna os últimos 30 dias. */
	@GetMapping("/extrato")
	public PageResponse<TransacaoResponse> extrato(@AuthenticationPrincipal Jwt jwt, @PathVariable Long contaId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
			@PageableDefault(size = 20, sort = "criadaEm", direction = Sort.Direction.DESC) Pageable pageable) {
		Instant fim = ate != null ? ate : Instant.now().plusSeconds(1);
		Instant inicio = de != null ? de : fim.minus(30, ChronoUnit.DAYS);
		return PageResponse.of(service.extrato(contaId, UsuarioLogado.id(jwt), inicio, fim, pageable));
	}

}
