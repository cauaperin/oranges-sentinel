package com.api.orangesentinel.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(RecursoNaoEncontradoException.class)
	ProblemDetail naoEncontrado(RecursoNaoEncontradoException ex) {
		return problema(HttpStatus.NOT_FOUND, "Recurso não encontrado", ex.getMessage());
	}

	@ExceptionHandler(RegraNegocioException.class)
	ProblemDetail regraNegocio(RegraNegocioException ex) {
		return problema(HttpStatus.UNPROCESSABLE_CONTENT, "Regra de negócio violada", ex.getMessage());
	}

	@ExceptionHandler(ConflitoException.class)
	ProblemDetail conflito(ConflitoException ex) {
		return problema(HttpStatus.CONFLICT, "Conflito", ex.getMessage());
	}

	@ExceptionHandler(AcessoNegadoException.class)
	ProblemDetail acessoNegado(AcessoNegadoException ex) {
		return problema(HttpStatus.FORBIDDEN, "Acesso negado", ex.getMessage());
	}

	@ExceptionHandler({ BadCredentialsException.class, DisabledException.class })
	ProblemDetail credenciais(RuntimeException ex) {
		return problema(HttpStatus.UNAUTHORIZED, "Não autorizado", "E-mail ou senha inválidos");
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ProblemDetail concorrencia(ObjectOptimisticLockingFailureException ex) {
		return problema(HttpStatus.CONFLICT, "Conflito de concorrência",
				"O recurso foi alterado por outra operação. Tente novamente.");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail integridade(DataIntegrityViolationException ex) {
		log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());
		return problema(HttpStatus.CONFLICT, "Conflito de dados",
				"A operação viola uma restrição de unicidade ou integridade.");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> erros = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors().forEach(e -> erros.putIfAbsent(e.getField(), e.getDefaultMessage()));
		ProblemDetail pd = problema(HttpStatus.BAD_REQUEST, "Dados inválidos", "Um ou mais campos são inválidos.");
		pd.setProperty("erros", erros);
		return ResponseEntity.badRequest().body(pd);
	}

	private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detalhe);
		pd.setTitle(titulo);
		return pd;
	}

}
