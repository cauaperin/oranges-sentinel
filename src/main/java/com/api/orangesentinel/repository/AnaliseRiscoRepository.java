package com.api.orangesentinel.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.api.orangesentinel.model.AnaliseRisco;

public interface AnaliseRiscoRepository extends JpaRepository<AnaliseRisco, Long> {

	List<AnaliseRisco> findByContaIdOrderByCriadaEmDesc(Long contaId, Pageable pageable);

}
