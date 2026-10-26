package com.api.orangesentinel.service.antifraude.grafo;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.jgrapht.graph.builder.GraphTypeBuilder;

/**
 * Grafo de transações em memória (JGraphT).
 * <ul>
 * <li><b>multigrafo</b>: uma aresta por transação (preserva valor e timestamp de cada Pix);</li>
 * <li><b>agregado</b>: no máximo uma aresta por par (origem, destino), com peso = soma dos valores. Usado para
 * graus distintos, ciclos e centralidade.</li>
 * </ul>
 */
public final class GrafoTransacoes {

	private final Graph<Long, ArestaTransacao> multigrafo;

	private final Graph<Long, DefaultWeightedEdge> agregado;

	private GrafoTransacoes(Graph<Long, ArestaTransacao> multigrafo, Graph<Long, DefaultWeightedEdge> agregado) {
		this.multigrafo = multigrafo;
		this.agregado = agregado;
	}

	public static GrafoTransacoes construir(Collection<ArestaTransacao> arestas) {
		Graph<Long, ArestaTransacao> multi = GraphTypeBuilder.<Long, ArestaTransacao>directed()
			.allowingMultipleEdges(true)
			.allowingSelfLoops(false)
			.weighted(false)
			.buildGraph();
		Graph<Long, DefaultWeightedEdge> agregado = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

		for (ArestaTransacao a : arestas) {
			if (a.origem() == null || a.destino() == null || a.origem().equals(a.destino())) {
				continue;
			}
			multi.addVertex(a.origem());
			multi.addVertex(a.destino());
			multi.addEdge(a.origem(), a.destino(), a);

			agregado.addVertex(a.origem());
			agregado.addVertex(a.destino());
			DefaultWeightedEdge e = agregado.getEdge(a.origem(), a.destino());
			if (e == null) {
				e = agregado.addEdge(a.origem(), a.destino());
				agregado.setEdgeWeight(e, a.valor().doubleValue());
			}
			else {
				agregado.setEdgeWeight(e, agregado.getEdgeWeight(e) + a.valor().doubleValue());
			}
		}
		return new GrafoTransacoes(multi, agregado);
	}

	public Graph<Long, ArestaTransacao> multigrafo() {
		return multigrafo;
	}

	public Graph<Long, DefaultWeightedEdge> agregado() {
		return agregado;
	}

	public Set<Long> contas() {
		return multigrafo.vertexSet();
	}

	public int numeroArestas() {
		return multigrafo.edgeSet().size();
	}

	/** Transações de {@code origem} para {@code destino}, em ordem cronológica. */
	public List<ArestaTransacao> transacoesEntre(Long origem, Long destino) {
		return multigrafo.getAllEdges(origem, destino)
			.stream()
			.sorted(Comparator.comparing(ArestaTransacao::timestamp))
			.toList();
	}

	/**
	 * Subgrafo "ego" ao redor de uma conta: vizinhos (ignorando direção) até {@code profundidade} saltos, limitado a
	 * {@code maxNos} vértices.
	 */
	public SubgrafoEgo ego(Long centro, int profundidade, int maxNos) {
		Set<Long> visitados = new LinkedHashSet<>();
		if (!multigrafo.containsVertex(centro)) {
			visitados.add(centro);
			return new SubgrafoEgo(visitados, List.of());
		}
		Deque<long[]> fila = new ArrayDeque<>();
		fila.add(new long[] { centro, 0 });
		visitados.add(centro);
		while (!fila.isEmpty() && visitados.size() < maxNos) {
			long[] atual = fila.poll();
			if (atual[1] >= profundidade) {
				continue;
			}
			for (Long vizinho : Graphs.neighborSetOf(agregado, atual[0])) {
				if (visitados.size() >= maxNos) {
					break;
				}
				if (visitados.add(vizinho)) {
					fila.add(new long[] { vizinho, atual[1] + 1 });
				}
			}
		}
		Set<ArestaTransacao> arestas = new HashSet<>();
		for (Long v : visitados) {
			for (ArestaTransacao a : multigrafo.outgoingEdgesOf(v)) {
				if (visitados.contains(a.destino())) {
					arestas.add(a);
				}
			}
		}
		return new SubgrafoEgo(visitados,
				arestas.stream().sorted(Comparator.comparing(ArestaTransacao::timestamp)).toList());
	}

	public record SubgrafoEgo(Set<Long> contas, List<ArestaTransacao> arestas) {
	}

}
