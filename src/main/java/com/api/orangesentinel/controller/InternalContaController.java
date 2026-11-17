package com.api.orangesentinel.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;

import com.api.orangesentinel.dto.InternalDtos.AberturaInicialRequest;
import com.api.orangesentinel.dto.InternalDtos.ConsultaContasRequest;
import com.api.orangesentinel.dto.InternalDtos.ContaInfo;
import com.api.orangesentinel.dto.InternalDtos.ContaRisco;
import com.api.orangesentinel.dto.InternalDtos.DecisaoFraudeRequest;
import com.api.orangesentinel.dto.InternalDtos.ResultadoDecisao;
import com.api.orangesentinel.dto.PageResponse;
import com.api.orangesentinel.model.enums.Classificacao;
import com.api.orangesentinel.service.ContaService;
import com.api.orangesentinel.service.DecisaoFraudeService;

/**
 * API entre serviços sobre contas, usada pelo auth (conta inicial) e pelo motor (consulta, risco e decisão).
 * Protegida por {@code X-Internal-Key} (ver {@code InternalKeyFilter}).
 */
@Hidden
@RestController
@RequestMapping("/internal/contas")
public class InternalContaController {

	private final ContaService contaService;

	private final DecisaoFraudeService decisaoService;

	public InternalContaController(ContaService contaService, DecisaoFraudeService decisaoService) {
		this.contaService = contaService;
		this.decisaoService = decisaoService;
	}

	@PostMapping("/inicial")
	@ResponseStatus(HttpStatus.CREATED)
	public ContaInfo abrirInicial(@Valid @RequestBody AberturaInicialRequest req) {
		return ContaInfo.of(contaService.abrirInicial(req.usuarioId(), req.titular(), req.cpf()));
	}

	/** POST (e não GET) porque o motor consulta lotes de até 1000 ids. */
	@PostMapping("/consulta")
	public List<ContaInfo> consultar(@Valid @RequestBody ConsultaContasRequest req) {
		return contaService.buscarVarias(req.ids()).stream().map(ContaInfo::of).toList();
	}

	@GetMapping("/ranking")
	public PageResponse<ContaInfo> ranking(@RequestParam(required = false) Classificacao classificacao,
			@RequestParam(defaultValue = "0") int minScore,
			@PageableDefault(size = 20, sort = "scoreRisco", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.of(contaService.ranking(classificacao, minScore, pageable).map(ContaInfo::of));
	}

	@PutMapping("/riscos")
	public void registrarRiscos(@Valid @RequestBody List<@Valid ContaRisco> riscos) {
		contaService.registrarRiscos(riscos);
	}

	@PostMapping("/{id}/decisao-fraude")
	public ResultadoDecisao decidirFraude(@PathVariable Long id, @Valid @RequestBody DecisaoFraudeRequest req) {
		return decisaoService.decidir(id, req.decisao(), req.alertaId());
	}

}
