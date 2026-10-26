package com.api.orangesentinel.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api.orangesentinel.dto.AuthDtos.LoginRequest;
import com.api.orangesentinel.dto.AuthDtos.RegistroRequest;
import com.api.orangesentinel.dto.AuthDtos.TokenResponse;
import com.api.orangesentinel.dto.UsuarioDtos.UsuarioResponse;
import com.api.orangesentinel.model.Usuario;
import com.api.orangesentinel.model.enums.Role;
import com.api.orangesentinel.model.enums.TipoConta;
import com.api.orangesentinel.repository.UsuarioRepository;
import com.api.orangesentinel.security.TokenService;

@Service
public class AuthService {

	private final UsuarioService usuarioService;

	private final UsuarioRepository usuarioRepository;

	private final ContaService contaService;

	private final TokenService tokenService;

	private final PasswordEncoder passwordEncoder;

	public AuthService(UsuarioService usuarioService, UsuarioRepository usuarioRepository, ContaService contaService,
			TokenService tokenService, PasswordEncoder passwordEncoder) {
		this.usuarioService = usuarioService;
		this.usuarioRepository = usuarioRepository;
		this.contaService = contaService;
		this.tokenService = tokenService;
		this.passwordEncoder = passwordEncoder;
	}

	/** Cadastro público de correntista: cria o usuário CLIENTE e uma conta corrente. */
	@Transactional
	public TokenResponse registrar(RegistroRequest req) {
		Usuario usuario = usuarioService.criar(req.nome(), req.cpf(), req.email(), req.senha(), Role.CLIENTE);
		contaService.abrir(usuario.getId(), TipoConta.CORRENTE);
		return token(usuario);
	}

	@Transactional(readOnly = true)
	public TokenResponse login(LoginRequest req) {
		Usuario usuario = usuarioRepository.findByEmailIgnoreCase(req.email().trim())
			.filter(u -> passwordEncoder.matches(req.senha(), u.getSenhaHash()))
			.filter(Usuario::isAtivo)
			.orElseThrow(() -> new BadCredentialsException("Credenciais inválidas"));
		return token(usuario);
	}

	private TokenResponse token(Usuario usuario) {
		TokenService.TokenGerado gerado = tokenService.gerar(usuario);
		return new TokenResponse(gerado.token(), "Bearer", gerado.expiraEm(), UsuarioResponse.of(usuario));
	}

}
