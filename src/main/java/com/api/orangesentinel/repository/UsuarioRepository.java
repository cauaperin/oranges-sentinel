package com.api.orangesentinel.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.api.orangesentinel.model.Usuario;
import com.api.orangesentinel.model.enums.Role;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	boolean existsByCpf(String cpf);

	boolean existsBySimuladoTrue();

	Page<Usuario> findByRole(Role role, Pageable pageable);

}
