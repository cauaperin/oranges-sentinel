package com.api.orangesentinel.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.api.orangesentinel.model.ChavePix;
import com.api.orangesentinel.model.enums.TipoChave;

public final class ChavePixDtos {

	private ChavePixDtos() {
	}

	/** {@code valor} é ignorado para CPF (usa o do titular) e ALEATORIA (gerada pelo sistema). */
	public record CriarChaveRequest(@NotNull TipoChave tipo, @Size(max = 140) String valor) {
	}

	public record ChaveResponse(Long id, TipoChave tipo, String valor, Instant criadaEm) {

		public static ChaveResponse of(ChavePix c) {
			return new ChaveResponse(c.getId(), c.getTipo(), c.getValor(), c.getCriadaEm());
		}

	}

}
