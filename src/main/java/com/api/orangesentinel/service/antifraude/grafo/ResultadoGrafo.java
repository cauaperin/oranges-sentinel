package com.api.orangesentinel.service.antifraude.grafo;

import java.util.List;
import java.util.Map;

public record ResultadoGrafo(Map<Long, MetricasConta> metricas, List<Ciclo> ciclos, int nVertices, int nArestas) {
}
