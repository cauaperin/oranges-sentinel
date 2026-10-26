package com.api.orangesentinel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.api.orangesentinel.model.enums.Role;
import com.api.orangesentinel.repository.UsuarioRepository;
import com.api.orangesentinel.service.UsuarioService;

/**
 * Cria o administrador inicial na primeira inicialização (senha via {@code ADMIN_PASSWORD}).
 */
@Component
public class AdminSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

	private static final String CPF_ADMIN = "00000000000";

	private final UsuarioRepository repository;

	private final UsuarioService service;

	private final String email;

	private final String senha;

	public AdminSeeder(UsuarioRepository repository, UsuarioService service, @Value("${app.admin.email}") String email,
			@Value("${app.admin.senha:}") String senha) {
		this.repository = repository;
		this.service = service;
		this.email = email;
		this.senha = senha;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (senha == null || senha.isBlank() || repository.existsByEmailIgnoreCase(email)
				|| repository.existsByCpf(CPF_ADMIN)) {
			return;
		}
		service.criar("Administrador", CPF_ADMIN, email, senha, Role.ADMIN);
		log.info("Administrador inicial criado: {}", email);
	}

}
