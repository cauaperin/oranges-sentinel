package com.api.orangesentinel.service.antifraude.score;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.api.orangesentinel.model.enums.Classificacao;
import com.api.orangesentinel.service.antifraude.grafo.MetricasConta;

/**
 * Regressão logística simplificada com pesos definidos pelo especialista:
 *
 * <pre>
 *   z     = b0 + Σ wᵢ · xᵢ          (xᵢ = features normalizadas em [0, 1])
 *   score = round(100 · σ(z))       (σ = função sigmoide)
 * </pre>
 *
 * Em seguida aplica {@link RegrasDecisao} (pequena árvore de decisão) para padrões inequívocos ou evidência
 * insuficiente.
 */
@Component
public class LogisticRiskScorer implements RiskScorer {

	/** Número de contrapartes distintas a partir do qual fan-in/fan-out saturam em 1. */
	static final int SATURACAO_GRAU = 20;

	/** Idade (dias) abaixo da qual a conta é considerada nova. */
	static final int DIAS_CONTA_NOVA = 30;

	@Override
	public ScoreResultado avaliar(MetricasConta m, ConfiguracaoRisco c) {
		Map<String, Double> x = features(m);
		Map<String, Double> contribuicoes = new LinkedHashMap<>();
		contribuicoes.put("fanIn", c.pesoFanIn() * x.get("fanIn"));
		contribuicoes.put("fanOut", c.pesoFanOut() * x.get("fanOut"));
		contribuicoes.put("taxaRepasse", c.pesoTaxaRepasse() * x.get("taxaRepasse"));
		contribuicoes.put("repasseRapido", c.pesoRepasseRapido() * x.get("repasseRapido"));
		contribuicoes.put("ciclo", c.pesoCiclo() * x.get("ciclo"));
		contribuicoes.put("centralidade", c.pesoCentralidade() * x.get("centralidade"));
		contribuicoes.put("contaNova", c.pesoContaNova() * x.get("contaNova"));

		double z = c.intercepto() + contribuicoes.values().stream().mapToDouble(Double::doubleValue).sum();
		int scoreBase = (int) Math.round(100.0 * sigmoide(z));

		List<String> motivos = motivos(m, c, contribuicoes);
		int score = RegrasDecisao.aplicar(m, c, scoreBase, motivos);
		return new ScoreResultado(score, classificar(score, c), contribuicoes, motivos);
	}

	static Map<String, Double> features(MetricasConta m) {
		Map<String, Double> x = new LinkedHashMap<>();
		x.put("fanIn", grauNormalizado(m.inDegree()));
		x.put("fanOut", grauNormalizado(m.outDegree()));
		x.put("taxaRepasse", m.taxaRepasse());
		x.put("repasseRapido", m.fracRepasseRapido());
		x.put("ciclo", m.emCiclo() ? 1.0 : 0.0);
		x.put("centralidade", m.centralidadeRelativa());
		x.put("contaNova",
				m.idadeContaDias() < DIAS_CONTA_NOVA ? 1.0 - (double) m.idadeContaDias() / DIAS_CONTA_NOVA : 0.0);
		return x;
	}

	static double grauNormalizado(int grau) {
		return Math.min(1.0, Math.log1p(grau) / Math.log1p(SATURACAO_GRAU));
	}

	static double sigmoide(double z) {
		return 1.0 / (1.0 + Math.exp(-z));
	}

	public static Classificacao classificar(int score, ConfiguracaoRisco c) {
		if (score >= c.limiarLaranja()) {
			return Classificacao.LARANJA;
		}
		return score >= c.limiarSuspeita() ? Classificacao.SUSPEITA : Classificacao.NORMAL;
	}

	/** Explicações em linguagem natural, ordenadas pela contribuição da feature correspondente. */
	private static List<String> motivos(MetricasConta m, ConfiguracaoRisco c, Map<String, Double> contrib) {
		record Motivo(double peso, String texto) {
		}
		List<Motivo> lista = new ArrayList<>();
		if (m.inDegree() >= 5) {
			lista.add(new Motivo(contrib.get("fanIn"),
					"Recebeu de %d contas distintas (padrão funil / fan-in)".formatted(m.inDegree())));
		}
		if (m.outDegree() >= 5) {
			lista.add(new Motivo(contrib.get("fanOut"),
					"Enviou para %d contas distintas (padrão dispersão / fan-out)".formatted(m.outDegree())));
		}
		if (m.taxaRepasse() >= 0.8 && m.qtdRecebidas() > 0) {
			lista.add(new Motivo(contrib.get("taxaRepasse"), String.format(Locale.ROOT,
					"Não retém saldo: taxa de repasse de %.0f%%", m.taxaRepasse() * 100)));
		}
		if (m.fracRepasseRapido() >= 0.5) {
			lista.add(new Motivo(contrib.get("repasseRapido"), String.format(Locale.ROOT,
					"%.0f%% do valor enviado saiu em até %d min após entrar (mediana de %s)",
					m.fracRepasseRapido() * 100, c.repasseRapidoMinutos(), formatarDuracao(m.medianaRepasseSeg()))));
		}
		if (m.emCiclo()) {
			lista.add(new Motivo(contrib.get("ciclo"),
					"Participa de ciclo fechado de transferências com %d contas".formatted(m.menorCiclo())));
		}
		if (m.centralidadeRelativa() >= 0.5) {
			lista.add(new Motivo(contrib.get("centralidade"), String.format(Locale.ROOT,
					"Intermediário crítico na rede (betweenness em %.0f%% do máximo)", m.centralidadeRelativa() * 100)));
		}
		if (m.idadeContaDias() < DIAS_CONTA_NOVA / 2 && m.qtdRecebidas() > 0) {
			lista.add(new Motivo(contrib.get("contaNova"),
					"Conta recente (%d dias) já movimentando valores de terceiros".formatted(m.idadeContaDias())));
		}
		return new ArrayList<>(lista.stream()
			.sorted((a, b) -> Double.compare(b.peso(), a.peso()))
			.map(Motivo::texto)
			.toList());
	}

	static String formatarDuracao(Double segundos) {
		if (segundos == null) {
			return "n/d";
		}
		long s = Math.round(segundos);
		if (s < 60) {
			return s + "s";
		}
		if (s < 3600) {
			return (s / 60) + " min";
		}
		if (s < 86_400) {
			return String.format(Locale.ROOT, "%.1f h", s / 3600.0);
		}
		return String.format(Locale.ROOT, "%.1f dias", s / 86_400.0);
	}

}
