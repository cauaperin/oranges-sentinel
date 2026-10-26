package com.api.orangesentinel.dto;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.api.orangesentinel.model.Usuario;
import com.api.orangesentinel.model.enums.Role;

public final class UsuarioDtos {

	private UsuarioDtos() {
	}

	public record UsuarioResponse(Long id, String nome, String cpf, String email, Role role, boolean ativo,
			Instant criadoEm) {

		public static UsuarioResponse of(Usuario u) {
			return new UsuarioResponse(u.getId(), u.getNome(), mascararCpf(u.getCpf()), u.getEmail(), u.getRole(),
					u.isAtivo(), u.getCriadoEm());
		}

		private static String mascararCpf(String cpf) {
			return "***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**";
		}

	}

	public record AtualizarPerfilRequest(@NotBlank @Size(max = 120) String nome,
			@NotBlank @Email @Size(max = 160) String email, @Size(min = 6, max = 72) String novaSenha) {
	}

	public record CriarUsuarioRequest(@NotBlank @Size(max = 120) String nome,
			@NotBlank @Pattern(regexp = "\\d{11}", message = "CPF deve conter 11 dígitos") String cpf,
			@NotBlank @Email @Size(max = 160) String email, @NotBlank @Size(min = 6, max = 72) String senha,
			@NotNull Role role) {
	}

	public record AlterarUsuarioRequest(Role role, Boolean ativo) {
	}

}
