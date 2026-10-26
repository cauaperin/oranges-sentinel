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

import com.api.orangesentinel.model.Transacao;
import com.api.orangesentinel.model.enums.StatusTransacao;
import com.api.orangesentinel.service.antifraude.grafo.ArestaTransacao;
import com.api.orangesentinel.service.antifraude.grafo.MovimentoExterno;

public interface TransacaoRepository extends JpaRepository<Transacao, Long> {

	Optional<Transacao> findByIdempotencyKey(String idempotencyKey);

	@Query(value = """
			select t from Transacao t
			left join fetch t.contaOrigem o left join fetch o.titular
			left join fetch t.contaDestino d left join fetch d.titular
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
			select new com.api.orangesentinel.service.antifraude.grafo.ArestaTransacao(
			    t.id, t.contaOrigem.id, t.contaDestino.id, t.valor, t.criadaEm)
			from Transacao t
			where t.tipo = com.api.orangesentinel.model.enums.TipoTransacao.PIX
			  and t.status = com.api.orangesentinel.model.enums.StatusTransacao.CONCLUIDA
			  and t.criadaEm >= :desde
			""")
	List<ArestaTransacao> arestasPix(@Param("desde") Instant desde);

	@Query("""
			select new com.api.orangesentinel.service.antifraude.grafo.MovimentoExterno(
			    t.contaOrigem.id, t.valor, t.criadaEm)
			from Transacao t
			where t.tipo = com.api.orangesentinel.model.enums.TipoTransacao.SAQUE
			  and t.status = com.api.orangesentinel.model.enums.StatusTransacao.CONCLUIDA
			  and t.criadaEm >= :desde
			""")
	List<MovimentoExterno> saques(@Param("desde") Instant desde);

	/** Pix recebidos recentemente por uma conta (checagem em tempo real). */
	@Query("""
			select count(distinct t.contaOrigem.id) as remetentes, coalesce(sum(t.valor), 0) as total
			from Transacao t
			where t.contaDestino.id = :contaId
			  and t.tipo = com.api.orangesentinel.model.enums.TipoTransacao.PIX
			  and t.status = com.api.orangesentinel.model.enums.StatusTransacao.CONCLUIDA
			  and t.criadaEm >= :desde
			""")
	RecebimentosRecentes recebimentosRecentes(@Param("contaId") Long contaId, @Param("desde") Instant desde);

	List<Transacao> findByContaDestinoIdAndStatus(Long contaDestinoId, StatusTransacao status);

	interface RecebimentosRecentes {

		long getRemetentes();

		BigDecimal getTotal();

	}

}
