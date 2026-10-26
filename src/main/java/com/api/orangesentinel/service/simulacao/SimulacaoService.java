package com.api.orangesentinel.service.simulacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api.orangesentinel.dto.SimulacaoDtos.AvaliacaoResponse;
import com.api.orangesentinel.dto.SimulacaoDtos.SimulacaoRequest;
import com.api.orangesentinel.dto.SimulacaoDtos.SimulacaoResponse;
import com.api.orangesentinel.exception.ConflitoException;
import com.api.orangesentinel.repository.SimulacaoRepository;
import com.api.orangesentinel.repository.SimulacaoRepository.Sequencia;
import com.api.orangesentinel.repository.UsuarioRepository;
import com.api.orangesentinel.service.simulacao.GeradorSimulacao.ContaSim;
import com.api.orangesentinel.service.simulacao.GeradorSimulacao.DadosSimulados;
import com.api.orangesentinel.service.simulacao.GeradorSimulacao.TxSim;

/**
 * Persiste os dados do {@link GeradorSimulacao} e avalia o motor contra o rótulo verdadeiro.
 */
@Service
public class SimulacaoService {

	private static final Logger log = LoggerFactory.getLogger(SimulacaoService.class);

	/** Senha de todos os usuários simulados (permite logar como um deles para explorar o banco). */
	public static final String SENHA_SIMULADOS = "simulado123";

	private final SimulacaoRepository repository;

	private final UsuarioRepository usuarioRepository;

	private final PasswordEncoder passwordEncoder;

	public SimulacaoService(SimulacaoRepository repository, UsuarioRepository usuarioRepository,
			PasswordEncoder passwordEncoder) {
		this.repository = repository;
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public SimulacaoResponse gerar(SimulacaoRequest req) {
		if (usuarioRepository.existsBySimuladoTrue()) {
			throw new ConflitoException(
					"Já existem dados simulados. Remova-os com DELETE /api/admin/simulacao antes de gerar novamente.");
		}
		DadosSimulados dados = GeradorSimulacao.gerar(new GeradorSimulacao.Parametros(req.seed(),
				req.clientesNormais(), req.redesFunil(), req.redesDispersao(), req.cadeias(), req.ciclos(), req.dias()),
				Instant.now());
		int n = dados.contas().size();
		List<Long> usuarioIds = repository.reservarValores(Sequencia.USUARIO, n);
		List<Long> contaIds = repository.reservarValores(Sequencia.CONTA, n);
		List<Long> numeros = repository.reservarValores(Sequencia.NUMERO_CONTA, n);
		String senhaHash = passwordEncoder.encode(SENHA_SIMULADOS);
		Random rndCpf = new Random(req.seed() ^ 0x5EEDL);
		Set<String> cpfs = new HashSet<>();

		List<Object[]> usuarios = new ArrayList<>(n);
		List<Object[]> contas = new ArrayList<>(n);
		List<Object[]> chaves = new ArrayList<>(n);
		List<Object[]> rotulos = new ArrayList<>(n);
		for (ContaSim c : dados.contas()) {
			int i = c.idx();
			String email = "sim%d.%d@orangesentinel.sim".formatted(contaIds.get(i), req.seed());
			var criada = SimulacaoRepository.ts(c.criadaEm());
			usuarios.add(new Object[] { usuarioIds.get(i), c.nome(), cpfUnico(rndCpf, cpfs), email, senhaHash, criada });
			contas.add(new Object[] { contaIds.get(i), usuarioIds.get(i), numeros.get(i).toString(),
					dados.saldos().getOrDefault(i, BigDecimal.ZERO), criada });
			chaves.add(new Object[] { contaIds.get(i), email, criada });
			rotulos.add(new Object[] { contaIds.get(i), c.cenario().isLaranja(), c.cenario().name() });
		}
		List<Object[]> transacoes = new ArrayList<>(dados.transacoes().size());
		for (TxSim t : dados.transacoes()) {
			transacoes.add(new Object[] { t.origem() == null ? null : contaIds.get(t.origem()),
					t.destino() == null ? null : contaIds.get(t.destino()), t.valor(), t.tipo().name(), t.descricao(),
					"sim-" + UUID.randomUUID(), SimulacaoRepository.ts(t.timestamp()) });
		}

		repository.inserirUsuarios(usuarios);
		repository.inserirContas(contas);
		repository.inserirChavesEmail(chaves);
		repository.inserirRotulos(rotulos);
		repository.inserirTransacoes(transacoes);

		int laranjas = (int) dados.contas().stream().filter(c -> c.cenario().isLaranja()).count();
		log.info("Simulação seed={} gerada: {} contas ({} laranjas), {} transações", req.seed(), n, laranjas,
				transacoes.size());
		return new SimulacaoResponse(req.seed(), n, n, transacoes.size(), laranjas, SENHA_SIMULADOS);
	}

	@Transactional
	public int limpar() {
		return repository.limpar();
	}

	@Transactional(readOnly = true)
	public AvaliacaoResponse avaliar() {
		return AvaliacaoCalculator.avaliar(repository.amostras());
	}

	/**
	 * CPF fictício começando com 9, único dentro da simulação. Colisão com um CPF já cadastrado é improvável
	 * (1 em 10¹⁰) e, se ocorrer, a restrição UNIQUE desfaz a geração inteira.
	 */
	private static String cpfUnico(Random rnd, Set<String> usados) {
		String cpf;
		do {
			cpf = "9" + String.format("%010d", rnd.nextLong(10_000_000_000L));
		}
		while (!usados.add(cpf));
		return cpf;
	}

}
