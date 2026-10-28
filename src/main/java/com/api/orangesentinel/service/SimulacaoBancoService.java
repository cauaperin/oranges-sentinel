package com.api.orangesentinel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api.orangesentinel.dto.InternalDtos.ContaSimulada;
import com.api.orangesentinel.dto.InternalDtos.TransacaoSimulada;
import com.api.orangesentinel.repository.SimulacaoBancoRepository;

/**
 * Parte do simulador que mora no banco: cria contas, chaves Pix e transações sintéticas em lote, a pedido do motor.
 */
@Service
public class SimulacaoBancoService {

	/** CPF fictício das contas simuladas: o simulador não usa chave do tipo CPF. */
	private static final String CPF_SIMULADO = "99999999999";

	private final SimulacaoBancoRepository repository;

	public SimulacaoBancoService(SimulacaoBancoRepository repository) {
		this.repository = repository;
	}

	/** @return ids das contas criadas, na mesma ordem da lista recebida */
	@Transactional
	public List<Long> criarContas(List<ContaSimulada> contas) {
		int n = contas.size();
		List<Long> ids = repository.reservarContaIds(n);
		List<Long> numeros = repository.reservarNumeros(n);
		List<Object[]> linhasContas = new ArrayList<>(n);
		List<Object[]> chaves = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			ContaSimulada c = contas.get(i);
			var criada = SimulacaoBancoRepository.ts(c.criadaEm());
			linhasContas.add(new Object[] { ids.get(i), c.usuarioId(), c.titular(), CPF_SIMULADO, numeros.get(i).toString(),
					c.saldo(), criada });
			chaves.add(new Object[] { ids.get(i), c.emailChave(), criada });
		}
		repository.inserirContas(linhasContas);
		repository.inserirChavesEmail(chaves);
		return ids;
	}

	@Transactional
	public void criarTransacoes(List<TransacaoSimulada> transacoes) {
		List<Object[]> linhas = new ArrayList<>(transacoes.size());
		for (TransacaoSimulada t : transacoes) {
			linhas.add(new Object[] { t.origemId(), t.destinoId(), t.valor(), t.tipo().name(), t.descricao(),
					"sim-" + UUID.randomUUID(), SimulacaoBancoRepository.ts(t.timestamp()) });
		}
		repository.inserirTransacoes(linhas);
	}

	@Transactional
	public int limpar() {
		return repository.limpar();
	}

}
