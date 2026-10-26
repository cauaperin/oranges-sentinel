package com.api.orangesentinel.service.simulacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.api.orangesentinel.model.enums.CenarioSimulacao;
import com.api.orangesentinel.model.enums.TipoTransacao;

/**
 * Gera, em memória e de forma reproduzível (mesma seed ⇒ mesmos dados), uma população de correntistas com
 * comportamento normal e redes de contas laranja. Não acessa banco: o {@code SimulacaoService} persiste o
 * resultado e os testes usam o gerador diretamente para medir precisão/recall do motor.
 *
 * <p>
 * Comportamento normal: salário mensal (empregador → cliente), contas pagas a comércios dias depois, Pix entre
 * amigos em horários aleatórios e saques ocasionais.
 * <p>
 * Cenários de fraude (sempre nos últimos dias da janela):
 * <ul>
 * <li><b>Funil</b>: várias vítimas → laranja → repasse em minutos para conta coletora ou saque;</li>
 * <li><b>Dispersão</b>: conta recebe valores altos e espalha em rodadas para várias laranjas, que sacam;</li>
 * <li><b>Cadeia</b>: vítimas → A → B → C → D (layering), cada salto em minutos;</li>
 * <li><b>Ciclo</b>: A → B → C → A repetido em poucas horas.</li>
 * </ul>
 */
public final class GeradorSimulacao {

	public record Parametros(long seed, int clientesNormais, int redesFunil, int redesDispersao, int cadeias,
			int ciclos, int dias) {
	}

	public record ContaSim(int idx, String nome, CenarioSimulacao cenario, Instant criadaEm) {
	}

	/** {@code origem}/{@code destino} são índices em {@link DadosSimulados#contas()} (null = fora do banco). */
	public record TxSim(Integer origem, Integer destino, BigDecimal valor, TipoTransacao tipo, Instant timestamp,
			String descricao) {
	}

	public record DadosSimulados(List<ContaSim> contas, List<TxSim> transacoes, Map<Integer, BigDecimal> saldos) {
	}

	private static final String[] NOMES = { "Ana", "Bruno", "Carla", "Diego", "Eduarda", "Felipe", "Gabriela",
			"Henrique", "Isabela", "João", "Larissa", "Marcos", "Natália", "Otávio", "Paula", "Rafael", "Sabrina",
			"Thiago", "Vanessa", "William", "Yasmin", "Lucas", "Mariana", "Pedro", "Beatriz", "Gustavo", "Camila",
			"Rodrigo", "Juliana", "Fernando" };

	private static final String[] SOBRENOMES = { "Silva", "Santos", "Oliveira", "Souza", "Rodrigues", "Ferreira",
			"Alves", "Pereira", "Lima", "Gomes", "Costa", "Ribeiro", "Martins", "Carvalho", "Almeida", "Lopes",
			"Soares", "Fernandes", "Vieira", "Barbosa" };

	private static final String[] EMPRESAS = { "Tech", "Log", "Agro", "Constru", "Saúde", "Varejo", "Educa",
			"Food", "Moda", "Auto" };

	private static final String[] COMERCIOS = { "Mercado", "Farmácia", "Padaria", "Posto", "Energia", "Internet",
			"Aluguel", "Academia", "Restaurante", "Loja" };

	private final Parametros p;

	private final Random rnd;

	private final Instant agora;

	private final Instant inicio;

	private final List<ContaSim> contas = new ArrayList<>();

	private final List<TxSim> txs = new ArrayList<>();

	private final List<Integer> clientes = new ArrayList<>();

	private final List<Integer> empregadores = new ArrayList<>();

	private final List<Integer> comercios = new ArrayList<>();

	private GeradorSimulacao(Parametros p, Instant agora) {
		this.p = p;
		this.rnd = new Random(p.seed());
		this.agora = agora.truncatedTo(ChronoUnit.SECONDS);
		this.inicio = this.agora.minus(p.dias(), ChronoUnit.DAYS);
	}

	public static DadosSimulados gerar(Parametros p, Instant agora) {
		return new GeradorSimulacao(p, agora).executar();
	}

	private DadosSimulados executar() {
		criarPopulacao();
		gerarComportamentoNormal();
		for (int i = 0; i < p.redesFunil(); i++) {
			gerarFunil();
		}
		for (int i = 0; i < p.redesDispersao(); i++) {
			gerarDispersao();
		}
		for (int i = 0; i < p.cadeias(); i++) {
			gerarCadeia();
		}
		for (int i = 0; i < p.ciclos(); i++) {
			gerarCiclo();
		}
		txs.removeIf(t -> t.timestamp().isAfter(agora));
		Map<Integer, BigDecimal> saldos = cobrirSaldosNegativos();
		txs.sort(Comparator.comparing(TxSim::timestamp));
		return new DadosSimulados(List.copyOf(contas), List.copyOf(txs), saldos);
	}

	// ---------------------------------------------------------------- população

	private void criarPopulacao() {
		int nEmpregadores = Math.max(2, p.clientesNormais() / 40);
		int nComercios = Math.max(5, p.clientesNormais() / 15);
		for (int i = 0; i < nEmpregadores; i++) {
			empregadores.add(novaConta(EMPRESAS[i % EMPRESAS.length] + " " + sobrenome() + " Ltda",
					CenarioSimulacao.EMPREGADOR, diasAtras(365, 3000)));
		}
		for (int i = 0; i < nComercios; i++) {
			comercios.add(novaConta(COMERCIOS[i % COMERCIOS.length] + " " + sobrenome(), CenarioSimulacao.COMERCIO,
					diasAtras(365, 3000)));
		}
		for (int i = 0; i < p.clientesNormais(); i++) {
			// ~10% de clientes legítimos com conta recente (para medir falso positivo da feature "conta nova")
			Instant criada = rnd.nextDouble() < 0.1 ? diasAtras(p.dias(), p.dias() + 20) : diasAtras(60, 2500);
			clientes.add(novaConta(pessoa(), CenarioSimulacao.CLIENTE_NORMAL, criada));
		}
	}

	private int novaConta(String nome, CenarioSimulacao cenario, Instant criadaEm) {
		int idx = contas.size();
		contas.add(new ContaSim(idx, nome, cenario, criadaEm));
		return idx;
	}

	// ---------------------------------------------------------------- comportamento normal

	private void gerarComportamentoNormal() {
		Map<Integer, List<Integer>> amigos = new HashMap<>();
		for (Integer c : clientes) {
			amigos.put(c, sortearDistintos(clientes, 2 + rnd.nextInt(3), c));
		}
		for (Integer c : clientes) {
			Instant abertura = contas.get(c).criadaEm();
			int empregador = empregadores.get(rnd.nextInt(empregadores.size()));
			BigDecimal salario = valor(2000, 9000);
			Instant pagamento = inicio.plus(rnd.nextInt(30), ChronoUnit.DAYS).plus(9, ChronoUnit.HOURS);
			while (pagamento.isBefore(agora)) {
				if (pagamento.isAfter(abertura)) {
					pix(empregador, c, salario, pagamento, "Salário");
					gerarContas(c, salario, pagamento);
				}
				pagamento = pagamento.plus(30, ChronoUnit.DAYS);
			}
			for (Integer amigo : amigos.get(c)) {
				int vezes = rnd.nextInt(4);
				for (int k = 0; k < vezes; k++) {
					pix(c, amigo, valor(20, 400), momentoApos(maisRecente(abertura, contas.get(amigo).criadaEm())),
							"Racha / transferência");
				}
			}
			if (rnd.nextBoolean()) {
				for (int k = 0, n = 1 + rnd.nextInt(2); k < n; k++) {
					saque(c, valor(100, 800), momentoApos(abertura), "Saque");
				}
			}
		}
		// Comércios fazem a gestão de caixa com saques semanais
		for (Integer m : comercios) {
			for (Instant t = inicio.plus(5, ChronoUnit.DAYS); t.isBefore(agora); t = t.plus(7, ChronoUnit.DAYS)) {
				saque(m, valor(500, 3000), t.plus(rnd.nextInt(10), ChronoUnit.HOURS), "Recolhimento de caixa");
			}
		}
	}

	private void gerarContas(int cliente, BigDecimal salario, Instant dataSalario) {
		int n = 3 + rnd.nextInt(5);
		double fracaoGasta = 0.5 + rnd.nextDouble() * 0.35;
		BigDecimal porConta = salario.multiply(BigDecimal.valueOf(fracaoGasta / n)).setScale(2, RoundingMode.HALF_UP);
		for (int k = 0; k < n; k++) {
			Instant quando = dataSalario.plus(3, ChronoUnit.HOURS).plusSeconds(rnd.nextLong(20L * 86_400));
			pix(cliente, comercios.get(rnd.nextInt(comercios.size())), porConta, quando, "Pagamento");
		}
	}

	// ---------------------------------------------------------------- cenários de fraude

	/** Vítimas → laranja(s) → repasse em minutos para conta coletora ou saque. */
	private void gerarFunil() {
		Instant ataque = inicioAtaque();
		int coletora = novaConta(pessoa(), CenarioSimulacao.FUNIL, ataque.minus(dias(2, 12)));
		int nLaranjas = 1 + rnd.nextInt(2);
		List<Integer> laranjas = new ArrayList<>();
		for (int i = 0; i < nLaranjas; i++) {
			laranjas.add(novaConta(pessoa(), CenarioSimulacao.FUNIL, ataque.minus(dias(1, 10))));
		}
		for (Integer vitima : sortearDistintos(clientes, 8 + rnd.nextInt(13), -1)) {
			int laranja = laranjas.get(rnd.nextInt(laranjas.size()));
			Instant t = ataque.plusSeconds(rnd.nextLong(2L * 86_400));
			BigDecimal golpe = valor(300, 3000);
			pix(vitima, laranja, golpe, t, "Pagamento");
			Instant repasse = t.plus(minutos(3, 40));
			BigDecimal valorRepasse = percentual(golpe, 90, 99);
			if (rnd.nextDouble() < 0.6) {
				pix(laranja, coletora, valorRepasse, repasse, null);
				saque(coletora, percentual(valorRepasse, 95, 99), repasse.plus(minutos(10, 50)), null);
			}
			else {
				saque(laranja, valorRepasse, repasse, null);
			}
		}
	}

	/** Conta recebe valores altos e dispersa em rodadas para várias laranjas, que sacam em seguida. */
	private void gerarDispersao() {
		Instant ataque = inicioAtaque();
		int origem = novaConta(pessoa(), CenarioSimulacao.DISPERSAO, ataque.minus(dias(2, 12)));
		List<Integer> laranjas = new ArrayList<>();
		for (int i = 0, n = 5 + rnd.nextInt(6); i < n; i++) {
			laranjas.add(novaConta(pessoa(), CenarioSimulacao.DISPERSAO, ataque.minus(dias(1, 10))));
		}
		int rodadas = 2 + rnd.nextInt(2);
		Instant t = ataque;
		for (int r = 0; r < rodadas; r++) {
			BigDecimal recebido = BigDecimal.ZERO;
			for (Integer vitima : sortearDistintos(clientes, 1 + rnd.nextInt(2), -1)) {
				BigDecimal golpe = valor(8000, 25000);
				pix(vitima, origem, golpe, t.plus(minutos(0, 30)), "Pagamento");
				recebido = recebido.add(golpe);
			}
			Instant espalha = t.plus(minutos(35, 55));
			BigDecimal parte = recebido.multiply(new BigDecimal("0.97"))
				.divide(BigDecimal.valueOf(laranjas.size()), 2, RoundingMode.DOWN);
			for (Integer laranja : laranjas) {
				Instant chegada = espalha.plus(minutos(0, 5));
				pix(origem, laranja, parte, chegada, null);
				saque(laranja, percentual(parte, 95, 99), chegada.plus(minutos(5, 30)), null);
			}
			t = t.plus(Duration.ofHours(6 + rnd.nextInt(30)));
		}
	}

	/** Vítimas → A → B → C (→ D → E) com saltos de minutos (layering) e saque no último elo. */
	private void gerarCadeia() {
		Instant ataque = inicioAtaque();
		int tamanho = 3 + rnd.nextInt(3);
		List<Integer> elos = new ArrayList<>();
		for (int i = 0; i < tamanho; i++) {
			elos.add(novaConta(pessoa(), CenarioSimulacao.CADEIA, ataque.minus(dias(1, 12))));
		}
		int rodadas = 2 + rnd.nextInt(2);
		Instant t = ataque;
		for (int r = 0; r < rodadas; r++) {
			BigDecimal valor = BigDecimal.ZERO;
			Instant ultima = t;
			for (Integer vitima : sortearDistintos(clientes, 2 + rnd.nextInt(4), -1)) {
				BigDecimal golpe = valor(500, 4000);
				ultima = t.plus(minutos(0, 50));
				pix(vitima, elos.getFirst(), golpe, ultima, "Pagamento");
				valor = valor.add(golpe);
			}
			Instant cursor = ultima;
			for (int i = 0; i + 1 < elos.size(); i++) {
				cursor = cursor.plus(minutos(5, 30));
				valor = percentual(valor, 96, 99);
				pix(elos.get(i), elos.get(i + 1), valor, cursor, null);
			}
			saque(elos.getLast(), percentual(valor, 96, 99), cursor.plus(minutos(5, 30)), null);
			t = t.plus(Duration.ofHours(8 + rnd.nextInt(30)));
		}
	}

	/** A → B → C (→ D) → A, repetido algumas vezes em poucas horas. */
	private void gerarCiclo() {
		Instant ataque = inicioAtaque();
		int tamanho = 3 + rnd.nextInt(3);
		List<Integer> membros = new ArrayList<>();
		for (int i = 0; i < tamanho; i++) {
			membros.add(novaConta(pessoa(), CenarioSimulacao.CICLO, ataque.minus(dias(3, 20))));
		}
		BigDecimal valor = valor(5000, 20000);
		Instant cursor = ataque;
		int voltas = 2 + rnd.nextInt(3);
		for (int v = 0; v < voltas; v++) {
			for (int i = 0; i < tamanho; i++) {
				cursor = cursor.plus(minutos(10, 50));
				valor = percentual(valor, 97, 99);
				pix(membros.get(i), membros.get((i + 1) % tamanho), valor, cursor, null);
			}
			cursor = cursor.plus(minutos(30, 180));
		}
		saque(membros.getFirst(), percentual(valor, 90, 99), cursor.plus(minutos(10, 40)), null);
	}

	// ---------------------------------------------------------------- saldos

	/**
	 * Garante que nenhuma conta fique com saldo negativo em momento algum: se faltar dinheiro, insere um depósito
	 * (dinheiro externo) logo antes da primeira movimentação da conta.
	 */
	private Map<Integer, BigDecimal> cobrirSaldosNegativos() {
		txs.sort(Comparator.comparing(TxSim::timestamp));
		Map<Integer, BigDecimal> saldo = new HashMap<>();
		Map<Integer, BigDecimal> minimo = new HashMap<>();
		Map<Integer, Instant> primeira = new HashMap<>();
		for (TxSim t : txs) {
			if (t.origem() != null) {
				BigDecimal s = saldo.getOrDefault(t.origem(), BigDecimal.ZERO).subtract(t.valor());
				saldo.put(t.origem(), s);
				minimo.merge(t.origem(), s, BigDecimal::min);
				primeira.putIfAbsent(t.origem(), t.timestamp());
			}
			if (t.destino() != null) {
				saldo.merge(t.destino(), t.valor(), BigDecimal::add);
				primeira.putIfAbsent(t.destino(), t.timestamp());
			}
		}
		for (Map.Entry<Integer, BigDecimal> e : minimo.entrySet()) {
			if (e.getValue().signum() < 0) {
				int conta = e.getKey();
				BigDecimal aporte = e.getValue().negate().add(valor(100, 2000));
				Instant quando = maisRecente(contas.get(conta).criadaEm().plusSeconds(60),
						primeira.get(conta).minus(1, ChronoUnit.HOURS));
				txs.add(new TxSim(null, conta, aporte, TipoTransacao.DEPOSITO, quando, "Depósito"));
				saldo.merge(conta, aporte, BigDecimal::add);
			}
		}
		return saldo;
	}

	// ---------------------------------------------------------------- utilitários

	private void pix(int origem, int destino, BigDecimal valor, Instant quando, String descricao) {
		txs.add(new TxSim(origem, destino, valor, TipoTransacao.PIX, quando, descricao));
	}

	private void saque(int conta, BigDecimal valor, Instant quando, String descricao) {
		txs.add(new TxSim(conta, null, valor, TipoTransacao.SAQUE, quando, descricao));
	}

	/** Início de um ataque: dentro dos últimos 10 dias (ou da janela, se menor), deixando 3 dias de folga. */
	private Instant inicioAtaque() {
		int ultimosDias = Math.min(10, p.dias());
		return agora.minus(ultimosDias, ChronoUnit.DAYS)
			.plusSeconds(rnd.nextLong(Math.max(1, (ultimosDias - 3) * 86_400L)));
	}

	private Instant momentoApos(Instant minimo) {
		Instant base = maisRecente(minimo, inicio);
		long janela = Math.max(1, agora.getEpochSecond() - base.getEpochSecond());
		return base.plusSeconds(rnd.nextLong(janela));
	}

	private Instant diasAtras(int min, int max) {
		return agora.minus(dias(min, max));
	}

	private Duration dias(int min, int max) {
		return Duration.ofDays(min + rnd.nextInt(max - min + 1)).plusSeconds(rnd.nextInt(86_400));
	}

	private Duration minutos(int min, int max) {
		return Duration.ofSeconds(min * 60L + rnd.nextInt((max - min) * 60 + 1));
	}

	private BigDecimal valor(int min, int max) {
		return BigDecimal.valueOf(min + rnd.nextDouble() * (max - min)).setScale(2, RoundingMode.HALF_UP);
	}

	private BigDecimal percentual(BigDecimal base, int minPct, int maxPct) {
		double pct = (minPct + rnd.nextDouble() * (maxPct - minPct)) / 100.0;
		return base.multiply(BigDecimal.valueOf(pct)).setScale(2, RoundingMode.DOWN);
	}

	private List<Integer> sortearDistintos(List<Integer> universo, int quantidade, int exceto) {
		Set<Integer> escolhidos = new HashSet<>();
		int alvo = Math.min(quantidade, universo.size() - (universo.contains(exceto) ? 1 : 0));
		while (escolhidos.size() < alvo) {
			int c = universo.get(rnd.nextInt(universo.size()));
			if (c != exceto) {
				escolhidos.add(c);
			}
		}
		return escolhidos.stream().sorted().toList();
	}

	private String pessoa() {
		return NOMES[rnd.nextInt(NOMES.length)] + " " + sobrenome() + " " + sobrenome();
	}

	private String sobrenome() {
		return SOBRENOMES[rnd.nextInt(SOBRENOMES.length)];
	}

	private static Instant maisRecente(Instant a, Instant b) {
		return a.isAfter(b) ? a : b;
	}

}
