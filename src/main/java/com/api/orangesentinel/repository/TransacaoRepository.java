package com.api.orangesentinel.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.api.orangesentinel.dto.ArestaPix;
import com.api.orangesentinel.dto.SaqueRegistrado;
import com.api.orangesentinel.model.Transacao;
import com.api.orangesentinel.model.enums.StatusTransacao;
import com.api.orangesentinel.dto.ArestaPix;
import com.api.orangesentinel.dto.SaqueRegistrado;

public interface TransacaoRepository extends JpaRepository<Transacao, Long> {

	Optional<Transacao> findByIdempotencyKey(String idempotencyKey);

	@Query(value = """
			select t from Transacao t
			left join fetch t.contaOrigem o
			left join fetch t.contaDestino d
			where (o.id = :contaId or d.id = :contaId)
			  and t.criadaEm >= :de and t.criadaEm < :ate
			""", countQuery = """
			select count(t) from Transacao t
			where (t.contaOrigem.id = :contaId or t.contaDestino.id = :contaId)
			  and t.criadaEm >= :de and t.criadaEm < :ate
			""")
	Page<Transacao> extrato(@Param("contaId") Long contaId, @Param("de") Instant de, @Param("ate") Instant ate,
			Pageable pageable);

	/** Arestas do grafo: Pix concluídos dentro da janela de análise. */
	@Query("""
			select new com.api.orangesentinel.dto.ArestaPix(
			    t.id, t.contaOrigem.id, t.contaDestino.id, t.valor, t.criadaEm)
			from Transacao t
			where t.tipo = com.api.orangesentinel.model.enums.TipoTransacao.PIX
			  and t.status = com.api.orangesentinel.model.enums.StatusTransacao.CONCLUIDA
			  and t.criadaEm >= :desde
			""")
	List<ArestaPix> arestasPix(@Param("desde") Instant desde);

	@Query("""
			select new com.api.orangesentinel.dto.SaqueRegistrado(
			    t.contaOrigem.id, t.valor, t.criadaEm)
			from Transacao t
			where t.tipo = com.api.orangesentinel.model.enums.TipoTransacao.SAQUE
			  and t.status = com.api.orangesentinel.model.enums.StatusTransacao.CONCLUIDA
			  and t.criadaEm >= :desde
			""")
	List<SaqueRegistrado> saques(@Param("desde") Instant desde);

	/** Pix concluídos recebidos recentemente por uma conta (enviados ao motor na avaliação em tempo real). */
	@Query("""
			select t.contaOrigem.id as remetenteId, t.valor as valor, t.criadaEm as em
			from Transacao t
			where t.contaDestino.id = :contaId
			  and t.tipo = com.api.orangesentinel.model.enums.TipoTransacao.PIX
			  and t.status = com.api.orangesentinel.model.enums.StatusTransacao.CONCLUIDA
			  and t.criadaEm >= :desde
			""")
	List<RecebimentoRecente> recebimentosRecentes(@Param("contaId") Long contaId, @Param("desde") Instant desde);

	List<Transacao> findByContaDestinoIdAndStatus(Long contaDestinoId, StatusTransacao status);

	interface RecebimentoRecente {

		Long getRemetenteId();

		BigDecimal getValor();

		Instant getEm();

	}

}
