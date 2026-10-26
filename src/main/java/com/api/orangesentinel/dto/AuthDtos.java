package com.api.orangesentinel.dto;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.api.orangesentinel.dto.UsuarioDtos.UsuarioResponse;

public final class AuthDtos {

	private AuthDtos() {
	}

	public record RegistroRequest(@NotBlank @Size(max = 120) String nome,
			@NotBlank @Pattern(regexp = "\\d{11}", message = "CPF deve conter 11 dígitos") String cpf,
			@NotBlank @Email @Size(max = 160) String email, @NotBlank @Size(min = 6, max = 72) String senha) {
	}

	public record LoginRequest(@NotBlank @Email String email, @NotBlank String senha) {
	}

	public record TokenResponse(String token, String tipo, Instant expiraEm, UsuarioResponse usuario) {
	}

}
