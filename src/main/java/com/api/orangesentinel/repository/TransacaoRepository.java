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
import com.api.orangesentinel.model.enums.TipoTransacao;
import com.api.orangesentinel.dto.ArestaPix;
import com.api.orangesentinel.dto.SaqueRegistrado;

public interface TransacaoRepository extends JpaRepository<Transacao, Long> {

	Optional<Transacao> findByIdempotencyKey(String idempotencyKey);

	/**
	 * Extrato da conta no período, com filtros opcionais: {@code tipo}/{@code status} nulos e {@code direcao}/
	 * {@code busca} vazios não filtram. A direção segue a regra da resposta: ENTRADA quando a conta é o destino.
	 * {@code busca} já chega em minúsculas e entre "%" (procura na descrição e no nome da outra parte).
	 */
	@Query(value = """
			select t from Transacao t
			left join fetch t.contaOrigem o
			left join fetch t.contaDestino d
			where (o.id = :contaId or d.id = :contaId)
			  and t.criadaEm >= :de and t.criadaEm < :ate
			  and (:tipo is null or t.tipo = :tipo)
			  and (:status is null or t.status = :status)
			  and (:direcao = '' or (:direcao = 'ENTRADA' and d.id = :contaId)
			       or (:direcao = 'SAIDA' and (d.id is null or d.id <> :contaId)))
			  and (:busca = '' or lower(coalesce(t.descricao, '')) like :busca
			       or lower(coalesce(o.titularNome, '')) like :busca or lower(coalesce(d.titularNome, '')) like :busca)
			""", countQuery = """
			select count(t) from Transacao t
			left join t.contaOrigem o
			left join t.contaDestino d
			where (o.id = :contaId or d.id = :contaId)
			  and t.criadaEm >= :de and t.criadaEm < :ate
			  and (:tipo is null or t.tipo = :tipo)
			  and (:status is null or t.status = :status)
			  and (:direcao = '' or (:direcao = 'ENTRADA' and d.id = :contaId)
			       or (:direcao = 'SAIDA' and (d.id is null or d.id <> :contaId)))
			  and (:busca = '' or lower(coalesce(t.descricao, '')) like :busca
			       or lower(coalesce(o.titularNome, '')) like :busca or lower(coalesce(d.titularNome, '')) like :busca)
			""")
	Page<Transacao> extrato(@Param("contaId") Long contaId, @Param("de") Instant de, @Param("ate") Instant ate,
			@Param("tipo") TipoTransacao tipo, @Param("status") StatusTransacao status,
			@Param("direcao") String direcao, @Param("busca") String busca, Pageable pageable);

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
