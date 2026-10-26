package com.api.orangesentinel.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.NotNull;

import com.api.orangesentinel.model.Conta;
import com.api.orangesentinel.model.enums.StatusConta;
import com.api.orangesentinel.model.enums.TipoConta;

public final class ContaDtos {

	private ContaDtos() {
	}

	public record ContaResponse(Long id, String agencia, String numero, TipoConta tipo, BigDecimal saldo,
			StatusConta status, Instant criadaEm) {

		public static ContaResponse of(Conta c) {
			return new ContaResponse(c.getId(), c.getAgencia(), c.getNumero(), c.getTipo(), c.getSaldo(),
					c.getStatus(), c.getCriadaEm());
		}

	}

	public record AbrirContaRequest(@NotNull TipoConta tipo) {
	}

}
