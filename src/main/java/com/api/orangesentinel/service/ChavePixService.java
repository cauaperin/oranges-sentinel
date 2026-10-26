package com.api.orangesentinel.service;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api.orangesentinel.exception.ConflitoException;
import com.api.orangesentinel.exception.RecursoNaoEncontradoException;
import com.api.orangesentinel.exception.RegraNegocioException;
import com.api.orangesentinel.model.ChavePix;
import com.api.orangesentinel.model.Conta;
import com.api.orangesentinel.model.enums.StatusConta;
import com.api.orangesentinel.model.enums.TipoChave;
import com.api.orangesentinel.repository.ChavePixRepository;

@Service
public class ChavePixService {

	private static final int MAX_CHAVES_POR_CONTA = 5;

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private static final Pattern TELEFONE = Pattern.compile("^\\+55\\d{10,11}$");

	private final ChavePixRepository repository;

	private final ContaService contaService;

	public ChavePixService(ChavePixRepository repository, ContaService contaService) {
		this.repository = repository;
		this.contaService = contaService;
	}

	@Transactional(readOnly = true)
	public List<ChavePix> listar(Long contaId, Long usuarioId) {
		contaService.buscarDoUsuario(contaId, usuarioId);
		return repository.findByContaIdOrderByIdAsc(contaId);
	}

	@Transactional
	public ChavePix criar(Long contaId, Long usuarioId, TipoChave tipo, String valorInformado) {
		Conta conta = contaService.buscarDoUsuario(contaId, usuarioId);
		if (conta.getStatus() != StatusConta.ATIVA) {
			throw new RegraNegocioException("Só é possível cadastrar chaves em contas ativas");
		}
		if (repository.countByContaId(contaId) >= MAX_CHAVES_POR_CONTA) {
			throw new RegraNegocioException("Limite de " + MAX_CHAVES_POR_CONTA + " chaves por conta atingido");
		}
		String valor = normalizar(tipo, valorInformado, conta);
		if (repository.existsByValor(valor)) {
			throw new ConflitoException("Chave Pix já cadastrada");
		}
		return repository.save(new ChavePix(conta, tipo, valor));
	}

	@Transactional
	public void remover(Long contaId, Long chaveId, Long usuarioId) {
		contaService.buscarDoUsuario(contaId, usuarioId);
		ChavePix chave = repository.findById(chaveId)
			.filter(c -> c.getConta().getId().equals(contaId))
			.orElseThrow(() -> new RecursoNaoEncontradoException("Chave Pix", chaveId));
		repository.delete(chave);
	}

	/**
	 * Resolve o id da conta de destino a partir de uma chave Pix (usado na transferência). Retorna só o id para que
	 * a conta seja carregada depois já com lock.
	 */
	@Transactional(readOnly = true)
	public Long resolverContaId(String chave) {
		String valor = chave.trim();
		return repository.findContaIdByValor(valor)
			.or(() -> repository.findContaIdByValor(valor.toLowerCase()))
			.orElseThrow(() -> new RecursoNaoEncontradoException("Chave Pix", chave));
	}

	private static String normalizar(TipoChave tipo, String valor, Conta conta) {
		return switch (tipo) {
			case CPF -> conta.getTitular().getCpf();
			case ALEATORIA -> UUID.randomUUID().toString();
			case EMAIL -> {
				String email = obrigatorio(valor).toLowerCase();
				if (!EMAIL.matcher(email).matches()) {
					throw new RegraNegocioException("E-mail inválido para chave Pix");
				}
				yield email;
			}
			case TELEFONE -> {
				String tel = obrigatorio(valor).replaceAll("[\\s()-]", "");
				if (!TELEFONE.matcher(tel).matches()) {
					throw new RegraNegocioException("Telefone deve estar no formato +55DDDNUMERO");
				}
				yield tel;
			}
		};
	}

	private static String obrigatorio(String valor) {
		if (valor == null || valor.isBlank()) {
			throw new RegraNegocioException("Valor da chave é obrigatório para este tipo");
		}
		return valor.trim();
	}

}
