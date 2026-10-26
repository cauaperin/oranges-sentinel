package com.api.orangesentinel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.api.orangesentinel.model.AlertaFraude;
import com.api.orangesentinel.model.enums.StatusAlerta;

public interface AlertaFraudeRepository extends JpaRepository<AlertaFraude, Long> {

	Optional<AlertaFraude> findByContaIdAndStatus(Long contaId, StatusAlerta status);

	@EntityGraph(attributePaths = { "conta", "conta.titular", "analista" })
	Page<AlertaFraude> findByStatus(StatusAlerta status, Pageable pageable);

	@EntityGraph(attributePaths = { "conta", "conta.titular", "analista" })
	Page<AlertaFraude> findAllBy(Pageable pageable);

	@EntityGraph(attributePaths = { "analista" })
	List<AlertaFraude> findByContaIdOrderByCriadoEmDesc(Long contaId);

}
