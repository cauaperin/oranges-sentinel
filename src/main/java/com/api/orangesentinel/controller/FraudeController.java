package com.api.orangesentinel.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.api.orangesentinel.dto.FraudeDtos.AlertaResponse;
import com.api.orangesentinel.dto.FraudeDtos.CicloResponse;
import com.api.orangesentinel.dto.FraudeDtos.ContaRiscoDetalhe;
import com.api.orangesentinel.dto.FraudeDtos.ContaRiscoResponse;
import com.api.orangesentinel.dto.FraudeDtos.ExecucaoResponse;
import com.api.orangesentinel.dto.FraudeDtos.GrafoResponse;
import com.api.orangesentinel.dto.FraudeDtos.ResolverAlertaRequest;
import com.api.orangesentinel.dto.PageResponse;
import com.api.orangesentinel.model.enums.Classificacao;
import com.api.orangesentinel.model.enums.StatusAlerta;
import com.api.orangesentinel.security.UsuarioLogado;
import com.api.orangesentinel.service.FraudeService;
import com.api.orangesentinel.service.antifraude.AnaliseRiscoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/fraude")
@Tag(name = "Antifraude", description = "Área do analista: scores, grafo, ciclos e alertas")
public class FraudeController {

	private final FraudeService service;

	private final AnaliseRiscoService analiseService;

	public FraudeController(FraudeService service, AnaliseRiscoService analiseService) {
		this.service = service;
		this.analiseService = analiseService;
	}

	@GetMapping("/contas")
	@Operation(summary = "Ranking de contas por score de risco")
	public PageResponse<ContaRiscoResponse> ranking(@RequestParam(required = false) Classificacao classificacao,
			@RequestParam(defaultValue = "0") int minScore,
			@PageableDefault(size = 20, sort = "scoreRisco", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.of(service.ranking(classificacao, minScore, pageable));
	}

	@GetMapping("/contas/{contaId}")
	@Operation(summary = "Detalhe de risco: última análise com métricas e motivos, histórico e alertas")
	public ContaRiscoDetalhe detalhe(@PathVariable Long contaId) {
		return service.detalhe(contaId);
	}

	@GetMapping("/contas/{contaId}/grafo")
	@Operation(summary = "Subgrafo de transações ao redor da conta (nodes/edges para visualização)")
	public GrafoResponse grafo(@PathVariable Long contaId, @RequestParam(defaultValue = "2") int profundidade) {
		return service.grafo(contaId, profundidade);
	}

	@GetMapping("/ciclos")
	@Operation(summary = "Ciclos de lavagem encontrados na última análise")
	public List<CicloResponse> ciclos() {
		return service.ciclos();
	}

	@PostMapping("/analises")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Executa agora a análise completa do grafo")
	public ExecucaoResponse executarAnalise(@AuthenticationPrincipal Jwt jwt) {
		return ExecucaoResponse.of(analiseService.executar("usuario:" + UsuarioLogado.id(jwt)));
	}

	@GetMapping("/analises")
	public PageResponse<ExecucaoResponse> execucoes(
			@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.of(service.execucoes(pageable));
	}

	@GetMapping("/alertas")
	public PageResponse<AlertaResponse> alertas(@RequestParam(required = false) StatusAlerta status,
			@PageableDefault(size = 20, sort = "score", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.of(service.alertas(status, pageable));
	}

	@PatchMapping("/alertas/{alertaId}")
	@Operation(summary = "Resolve um alerta",
			description = "CONFIRMADO bloqueia a conta e estorna Pix retidos; FALSO_POSITIVO reativa a conta e libera os Pix retidos.")
	public AlertaResponse resolver(@AuthenticationPrincipal Jwt jwt, @PathVariable Long alertaId,
			@Valid @RequestBody ResolverAlertaRequest req) {
		return service.resolverAlerta(alertaId, UsuarioLogado.id(jwt), req.decisao(), req.observacao());
	}

}
