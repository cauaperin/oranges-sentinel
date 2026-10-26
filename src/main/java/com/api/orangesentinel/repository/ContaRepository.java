package com.api.orangesentinel.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.api.orangesentinel.model.Conta;
import com.api.orangesentinel.model.enums.Classificacao;

public interface ContaRepository extends JpaRepository<Conta, Long> {

	List<Conta> findByTitularIdOrderByIdAsc(Long usuarioId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Conta c where c.id = :id")
	Optional<Conta> findByIdParaAtualizacao(@Param("id") Long id);

	@Query(value = "select nextval('conta_numero_seq')", nativeQuery = true)
	long proximoNumero();

	/** Data de criação de cada conta, usada para a feature "idade da conta" do modelo de risco. */
	@Query("select c.id as id, c.criadaEm as criadaEm from Conta c where c.id in :ids")
	List<ContaCriacao> buscarCriacao(@Param("ids") Collection<Long> ids);

	@Query(value = """
			select c from Conta c join fetch c.titular
			where c.scoreRisco is not null
			  and (:classificacao is null or c.classificacaoRisco = :classificacao)
			  and c.scoreRisco >= :minScore
			""", countQuery = """
			select count(c) from Conta c
			where c.scoreRisco is not null
			  and (:classificacao is null or c.classificacaoRisco = :classificacao)
			  and c.scoreRisco >= :minScore
			""")
	Page<Conta> ranking(@Param("classificacao") Classificacao classificacao, @Param("minScore") int minScore,
			Pageable pageable);

	@Query("select c from Conta c join fetch c.titular where c.id in :ids")
	List<Conta> findComTitular(@Param("ids") Collection<Long> ids);

	/**
	 * Atualização direta (sem carregar a entidade) para não conflitar com Pix concorrentes; a entidade usa
	 * {@code @DynamicUpdate}, então um Pix só regrava saldo/versão.
	 */
	@Modifying
	@Query("""
			update Conta c set c.scoreRisco = :score, c.classificacaoRisco = :classificacao, c.analisadoEm = :quando
			where c.id = :id
			""")
	void atualizarRisco(@Param("id") Long id, @Param("score") int score,
			@Param("classificacao") Classificacao classificacao, @Param("quando") Instant quando);

	@Modifying
	@Query("""
			update Conta c set c.status = com.api.orangesentinel.model.enums.StatusConta.SOB_ANALISE
			where c.id = :id and c.status = com.api.orangesentinel.model.enums.StatusConta.ATIVA
			""")
	int colocarSobAnalise(@Param("id") Long id);

	interface ContaCriacao {

		Long getId();

		Instant getCriadaEm();

	}

}
