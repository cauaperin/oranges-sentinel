package com.api.orangesentinel.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Operações em massa sobre as contas geradas pelo simulador (coluna {@code conta.simulada}). Usa JDBC em lote
 * porque o simulador insere milhares de linhas de uma vez; com um banco remoto (Supabase) uma ida ao banco por
 * linha levaria minutos.
 */
@Repository
public class SimulacaoBancoRepository {

	private static final int TAMANHO_LOTE = 1000;

	private static final String CONTAS_SIMULADAS = "(select id from conta where simulada)";

	private final JdbcTemplate jdbc;

	public SimulacaoBancoRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Reserva {@code n} ids de conta e {@code n} números em uma consulta cada, para inserir em lote. */
	public List<Long> reservarContaIds(int n) {
		return jdbc.queryForList("select nextval('conta_id_seq') from generate_series(1, ?)", Long.class, n);
	}

	public List<Long> reservarNumeros(int n) {
		return jdbc.queryForList("select nextval('conta_numero_seq') from generate_series(1, ?)", Long.class, n);
	}

	public void inserirContas(List<Object[]> linhas) {
		lote("""
				insert into conta (id, usuario_id, titular_nome, titular_cpf, agencia, numero, tipo, saldo, status,
													 criada_em, version, simulada)
				values (?, ?, ?, ?, '0001', ?, 'CORRENTE', ?, 'ATIVA', ?, 0, true)""", linhas);
	}

	public void inserirChavesEmail(List<Object[]> linhas) {
		lote("insert into chave_pix (conta_id, tipo, valor, criada_em) values (?, 'EMAIL', ?, ?)", linhas);
	}

	public void inserirTransacoes(List<Object[]> linhas) {
		lote("""
				insert into transacao (conta_origem_id, conta_destino_id, valor, tipo, status, descricao,
															 idempotency_key, criada_em)
				values (?, ?, ?, ?, 'CONCLUIDA', ?, ?, ?)""", linhas);
	}

	public static Timestamp ts(Instant instante) {
		return Timestamp.from(instante);
	}

	/** Remove contas, chaves e transações simuladas (inclusive transações de contas reais com contas simuladas). */
	public int limpar() {
		jdbc.update("delete from transacao where conta_origem_id in " + CONTAS_SIMULADAS
				+ " or conta_destino_id in " + CONTAS_SIMULADAS);
		jdbc.update("delete from chave_pix where conta_id in " + CONTAS_SIMULADAS);
		return jdbc.update("delete from conta where simulada");
	}

	private void lote(String sql, List<Object[]> linhas) {
		for (int i = 0; i < linhas.size(); i += TAMANHO_LOTE) {
			jdbc.batchUpdate(sql, new ArrayList<>(linhas.subList(i, Math.min(linhas.size(), i + TAMANHO_LOTE))));
		}
	}

}
