package com.api.orangesentinel.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.api.orangesentinel.model.enums.CenarioSimulacao;
import com.api.orangesentinel.model.enums.Classificacao;
import com.api.orangesentinel.service.simulacao.AvaliacaoCalculator.Amostra;

/**
 * Operações em massa sobre os dados gerados pelo simulador (usuários com {@code simulado = true}). Usa JDBC em lote
 * porque o simulador insere milhares de linhas de uma vez; com um banco remoto (Supabase) uma ida ao banco por linha
 * levaria minutos.
 */
@Repository
public class SimulacaoRepository {

	private static final String CONTAS_SIMULADAS = """
			(select c.id from conta c join usuario u on u.id = c.usuario_id where u.simulado)""";

	private static final int TAMANHO_LOTE = 1000;

	private final JdbcClient jdbc;

	private final JdbcTemplate jdbcTemplate;

	public SimulacaoRepository(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
		this.jdbc = jdbc;
		this.jdbcTemplate = jdbcTemplate;
	}

	public enum Sequencia {

		USUARIO("usuario_id_seq"), CONTA("conta_id_seq"), NUMERO_CONTA("conta_numero_seq");

		private final String nome;

		Sequencia(String nome) {
			this.nome = nome;
		}

	}

	/** Reserva {@code n} valores de uma sequence em uma única consulta, para inserir em lote já com os ids. */
	public List<Long> reservarValores(Sequencia sequencia, int n) {
		return jdbc.sql("select nextval('" + sequencia.nome + "') from generate_series(1, :n)")
			.param("n", n)
			.query(Long.class)
			.list();
	}

	public void inserirUsuarios(List<Object[]> linhas) {
		lote("""
				insert into usuario (id, nome, cpf, email, senha_hash, role, ativo, simulado, criado_em)
				values (?, ?, ?, ?, ?, 'CLIENTE', true, true, ?)""", linhas);
	}

	public void inserirContas(List<Object[]> linhas) {
		lote("""
				insert into conta (id, usuario_id, agencia, numero, tipo, saldo, status, criada_em, version)
				values (?, ?, '0001', ?, 'CORRENTE', ?, 'ATIVA', ?, 0)""", linhas);
	}

	public void inserirChavesEmail(List<Object[]> linhas) {
		lote("insert into chave_pix (conta_id, tipo, valor, criada_em) values (?, 'EMAIL', ?, ?)", linhas);
	}

	public void inserirRotulos(List<Object[]> linhas) {
		lote("insert into rotulo_simulacao (conta_id, eh_laranja, cenario) values (?, ?, ?)", linhas);
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

	/** Remove todos os dados simulados (inclusive transações de contas reais com contas simuladas). */
	public int limpar() {
		jdbc.sql("delete from alerta_fraude where conta_id in " + CONTAS_SIMULADAS).update();
		jdbc.sql("delete from analise_risco where conta_id in " + CONTAS_SIMULADAS).update();
		jdbc.sql("delete from transacao where conta_origem_id in " + CONTAS_SIMULADAS + " or conta_destino_id in "
				+ CONTAS_SIMULADAS).update();
		jdbc.sql("delete from chave_pix where conta_id in " + CONTAS_SIMULADAS).update();
		jdbc.sql("delete from rotulo_simulacao where conta_id in " + CONTAS_SIMULADAS).update();
		jdbc.sql("delete from conta where usuario_id in (select id from usuario where simulado)").update();
		return jdbc.sql("delete from usuario where simulado").update();
	}

	/** Rótulo verdadeiro × classificação atual do motor, para cada conta simulada. */
	public List<Amostra> amostras() {
		return jdbc.sql("""
				select r.conta_id, r.cenario, r.eh_laranja, c.score_risco, c.classificacao_risco
				from rotulo_simulacao r join conta c on c.id = r.conta_id
				""")
			.query((rs, n) -> new Amostra(rs.getLong("conta_id"), CenarioSimulacao.valueOf(rs.getString("cenario")),
					rs.getBoolean("eh_laranja"), (Integer) rs.getObject("score_risco"),
					rs.getString("classificacao_risco") == null ? null
							: Classificacao.valueOf(rs.getString("classificacao_risco"))))
			.list();
	}

	private void lote(String sql, List<Object[]> linhas) {
		for (int i = 0; i < linhas.size(); i += TAMANHO_LOTE) {
			jdbcTemplate.batchUpdate(sql, linhas.subList(i, Math.min(linhas.size(), i + TAMANHO_LOTE)));
		}
	}

}
