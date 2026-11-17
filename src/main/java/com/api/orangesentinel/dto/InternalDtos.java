package com.api.orangesentinel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.api.orangesentinel.model.Conta;
import com.api.orangesentinel.model.enums.Classificacao;
import com.api.orangesentinel.model.enums.StatusConta;
import com.api.orangesentinel.model.enums.TipoTransacao;

/**
 * Contrato da API interna ({@code /internal/**}) usada pelos serviços auth e motor.
 */
public final class InternalDtos {

	private InternalDtos() {
	}

	/** Abertura da conta corrente inicial, pedida pelo auth logo após o cadastro do usuário. */
	public record AberturaInicialRequest(@NotNull Long usuarioId, @NotBlank String titular,
			@NotBlank @Pattern(regexp = "\\d{11}") String cpf) {
	}

	public record ConsultaContasRequest(@NotNull List<Long> ids) {
	}

	/** Conta como o motor a enxerga, com o último risco calculado. */
	public record ContaInfo(Long id, Long usuarioId, String agencia, String numero, String titular, StatusConta status,
			Integer scoreRisco, Classificacao classificacaoRisco, Instant analisadoEm, Instant criadaEm) {

		public static ContaInfo of(Conta c) {
			return new ContaInfo(c.getId(), c.getUsuarioId(), c.getAgencia(), c.getNumero(), c.getTitularNome(),
					c.getStatus(), c.getScoreRisco(), c.getClassificacaoRisco(), c.getAnalisadoEm(), c.getCriadaEm());
		}

	}

	/** Risco calculado pelo motor; {@code sobAnalise} pede para suspender a conta. */
	public record ContaRisco(@NotNull Long contaId, int score, @NotNull Classificacao classificacao,
			@NotNull Instant analisadoEm, boolean sobAnalise) {
	}

	public enum Decisao {

		CONFIRMADO, FALSO_POSITIVO

	}

	public record DecisaoFraudeRequest(@NotNull Decisao decisao, Long alertaId) {
	}

	public record ResultadoDecisao(int estornadas, int liberadas) {
	}

	public record ContaSimulada(@NotNull Long usuarioId, @NotBlank String titular, @NotNull BigDecimal saldo,
			@NotNull Instant criadaEm, @NotBlank String emailChave) {
	}

	public record TransacaoSimulada(Long origemId, Long destinoId, @NotNull BigDecimal valor,
			@NotNull TipoTransacao tipo, String descricao, @NotNull Instant timestamp) {
	}

	public record IdsResponse(List<Long> ids) {
	}

}
