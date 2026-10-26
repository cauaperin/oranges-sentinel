package com.api.orangesentinel.service.antifraude.grafo;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector;
import org.jgrapht.graph.DefaultWeightedEdge;

/**
 * Detecta ciclos de lavagem (A → B → C → A).
 * <ol>
 * <li>Restringe a busca às <b>componentes fortemente conexas</b> (Kosaraju, JGraphT): só existe ciclo dentro de
 * uma SCC.</li>
 * <li>Enumera ciclos simples de comprimento {@code 3..maxComprimento} por DFS limitada, começando sempre pelo menor
 * id do ciclo (evita duplicatas).</li>
 * <li>Mantém apenas ciclos <b>temporalmente coerentes</b>: existe uma sequência de transferências seguindo o ciclo,
 * com timestamps crescentes, que se completa dentro de {@code janela}. Isso separa lavagem (dinheiro dando a volta
 * em horas) de amigos que se pagam ao longo do mês.</li>
 * </ol>
 */
public final class CycleDetector {

	private static final int COMPRIMENTO_MINIMO = 3;

	private static final int MAX_CICLOS = 5_000;

	private static final long MAX_PASSOS_DFS = 3_000_000;

	private CycleDetector() {
	}

	public static List<Ciclo> detectar(GrafoTransacoes grafo, int maxComprimento, Duration janela) {
		Graph<Long, DefaultWeightedEdge> g = grafo.agregado();
		List<Ciclo> ciclos = new ArrayList<>();
		long[] passos = { 0 };
		List<Set<Long>> sccs = new KosarajuStrongConnectivityInspector<>(g).stronglyConnectedSets();
		for (Set<Long> scc : sccs) {
			if (scc.size() < COMPRIMENTO_MINIMO) {
				continue;
			}
			for (Long inicio : scc.stream().sorted().toList()) {
				List<Long> caminho = new ArrayList<>();
				caminho.add(inicio);
				Set<Long> noCaminho = new HashSet<>(caminho);
				dfs(grafo, scc, inicio, inicio, caminho, noCaminho, maxComprimento, janela, ciclos, passos);
				if (ciclos.size() >= MAX_CICLOS || passos[0] >= MAX_PASSOS_DFS) {
					return ciclos;
				}
			}
		}
		return ciclos;
	}

	private static void dfs(GrafoTransacoes grafo, Set<Long> scc, Long inicio, Long atual, List<Long> caminho,
			Set<Long> noCaminho, int maxComprimento, Duration janela, List<Ciclo> ciclos, long[] passos) {
		Graph<Long, DefaultWeightedEdge> g = grafo.agregado();
		for (DefaultWeightedEdge e : g.outgoingEdgesOf(atual)) {
			if (++passos[0] >= MAX_PASSOS_DFS || ciclos.size() >= MAX_CICLOS) {
				return;
			}
			Long prox = g.getEdgeTarget(e);
			if (prox.equals(inicio)) {
				if (caminho.size() >= COMPRIMENTO_MINIMO) {
					validarTemporal(grafo, caminho, janela).ifPresent(ciclos::add);
				}
				continue;
			}
			// Só visita vértices maiores que o início: cada ciclo é encontrado uma única vez
			if (prox < inicio || !scc.contains(prox) || noCaminho.contains(prox) || caminho.size() >= maxComprimento) {
				continue;
			}
			caminho.add(prox);
			noCaminho.add(prox);
			dfs(grafo, scc, inicio, prox, caminho, noCaminho, maxComprimento, janela, ciclos, passos);
			caminho.removeLast();
			noCaminho.remove(prox);
		}
	}

	/**
	 * Testa todas as rotações do ciclo (a lavagem pode ter começado em qualquer conta) procurando uma sequência de
	 * transferências cronológica que feche o ciclo dentro da janela.
	 */
	static Optional<Ciclo> validarTemporal(GrafoTransacoes grafo, List<Long> ciclo, Duration janela) {
		int n = ciclo.size();
		for (int rot = 0; rot < n; rot++) {
			List<Long> ordem = new ArrayList<>(n);
			for (int k = 0; k < n; k++) {
				ordem.add(ciclo.get((rot + k) % n));
			}
			for (ArestaTransacao primeira : grafo.transacoesEntre(ordem.get(0), ordem.get(1))) {
				Instant limite = primeira.timestamp().plus(janela);
				Instant cursor = primeira.timestamp();
				BigDecimal minimo = primeira.valor();
				boolean fechou = true;
				for (int k = 1; k < n && fechou; k++) {
					Long de = ordem.get(k);
					Long para = ordem.get((k + 1) % n);
					ArestaTransacao proxima = null;
					for (ArestaTransacao a : grafo.transacoesEntre(de, para)) {
						if (!a.timestamp().isBefore(cursor) && !a.timestamp().isAfter(limite)) {
							proxima = a;
							break;
						}
					}
					if (proxima == null) {
						fechou = false;
					}
					else {
						cursor = proxima.timestamp();
						minimo = minimo.min(proxima.valor());
					}
				}
				if (fechou) {
					return Optional.of(new Ciclo(List.copyOf(ordem), minimo, primeira.timestamp(), cursor));
				}
			}
		}
		return Optional.empty();
	}

}
