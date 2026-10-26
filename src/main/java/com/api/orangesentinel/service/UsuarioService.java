package com.api.orangesentinel.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api.orangesentinel.dto.UsuarioDtos.AlterarUsuarioRequest;
import com.api.orangesentinel.dto.UsuarioDtos.AtualizarPerfilRequest;
import com.api.orangesentinel.exception.ConflitoException;
import com.api.orangesentinel.exception.RecursoNaoEncontradoException;
import com.api.orangesentinel.model.Usuario;
import com.api.orangesentinel.model.enums.Role;
import com.api.orangesentinel.repository.UsuarioRepository;

@Service
public class UsuarioService {

	private final UsuarioRepository repository;

	private final PasswordEncoder passwordEncoder;

	public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public Usuario criar(String nome, String cpf, String email, String senha, Role role) {
		if (repository.existsByCpf(cpf)) {
			throw new ConflitoException("CPF já cadastrado");
		}
		if (repository.existsByEmailIgnoreCase(email)) {
			throw new ConflitoException("E-mail já cadastrado");
		}
		return repository.save(new Usuario(nome.trim(), cpf, email.trim().toLowerCase(),
				passwordEncoder.encode(senha), role));
	}

	@Transactional(readOnly = true)
	public Usuario buscar(Long id) {
		return repository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", id));
	}

	@Transactional
	public Usuario atualizarPerfil(Long id, AtualizarPerfilRequest req) {
		Usuario usuario = buscar(id);
		String email = req.email().trim().toLowerCase();
		if (!email.equals(usuario.getEmail()) && repository.existsByEmailIgnoreCase(email)) {
			throw new ConflitoException("E-mail já cadastrado");
		}
		usuario.setNome(req.nome().trim());
		usuario.setEmail(email);
		if (req.novaSenha() != null && !req.novaSenha().isBlank()) {
			usuario.setSenhaHash(passwordEncoder.encode(req.novaSenha()));
		}
		return usuario;
	}

	@Transactional(readOnly = true)
	public Page<Usuario> listar(Role role, Pageable pageable) {
		return role == null ? repository.findAll(pageable) : repository.findByRole(role, pageable);
	}

	@Transactional
	public Usuario alterar(Long id, AlterarUsuarioRequest req) {
		Usuario usuario = buscar(id);
		if (req.role() != null) {
			usuario.setRole(req.role());
		}
		if (req.ativo() != null) {
			usuario.setAtivo(req.ativo());
		}
		return usuario;
	}

}
