package com.api.orangesentinel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.api.orangesentinel.model.ChavePix;

public interface ChavePixRepository extends JpaRepository<ChavePix, Long> {

	@Query("select c.conta.id from ChavePix c where c.valor = :valor")
	Optional<Long> findContaIdByValor(@Param("valor") String valor);

	boolean existsByValor(String valor);

	List<ChavePix> findByContaIdOrderByIdAsc(Long contaId);

	long countByContaId(Long contaId);

}
