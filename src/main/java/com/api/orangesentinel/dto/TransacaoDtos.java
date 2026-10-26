package com.api.orangesentinel.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.api.orangesentinel.model.Conta;
import com.api.orangesentinel.model.Transacao;
import com.api.orangesentinel.model.enums.StatusTransacao;
import com.api.orangesentinel.model.enums.TipoTransacao;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TransacaoDtos {

	private TransacaoDtos() {
	}

	public record PixRequest(@NotNull Long contaOrigemId, @NotBlank @Size(max = 140) String chaveDestino,
			@NotNull @DecimalMin("0.01") @DecimalMax("1000000.00") @Digits(integer = 13, fraction = 2) BigDecimal valor,
			@Size(max = 140) String descricao) {
	}

	public record MovimentacaoRequest(
			@NotNull @DecimalMin("0.01") @DecimalMax("1000000.00") @Digits(integer = 13, fraction = 2) BigDecimal valor,
			@Size(max = 140) String descricao) {
	}

	public record ContaResumo(Long id, String agencia, String numero, String titular) {

		public static ContaResumo of(Conta c) {
			return c == null ? null
					: new ContaResumo(c.getId(), c.getAgencia(), c.getNumero(), c.getTitular().getNome());
		}

	}

	public enum Direcao {

		ENTRADA, SAIDA

	}

	public record TransacaoResponse(Long id, TipoTransacao tipo, StatusTransacao status, Direcao direcao,
			BigDecimal valor, ContaResumo origem, ContaResumo destino, String descricao, String motivo,
			Instant criadaEm) {

		/** @param contaReferencia conta do ponto de vista da qual a direção é calculada */
		public static TransacaoResponse of(Transacao t, Long contaReferencia) {
			Direcao direcao = t.getContaDestino() != null && t.getContaDestino().getId().equals(contaReferencia)
					? Direcao.ENTRADA : Direcao.SAIDA;
			return new TransacaoResponse(t.getId(), t.getTipo(), t.getStatus(), direcao, t.getValor(),
					ContaResumo.of(t.getContaOrigem()), ContaResumo.of(t.getContaDestino()), t.getDescricao(),
					t.getMotivo(), t.getCriadaEm());
		}

	}

}
