# OrangeSentinel

Banco digital simulado com um **motor antifraude baseado em Teoria dos Grafos** para detectar **contas laranja**
(mule accounts) — a infraestrutura usada para lavagem de dinheiro e golpes de Pix.

O sistema não olha para contas isoladas: monta a **rede de transferências** em memória (JGraphT) e mede o
comportamento de cada conta dentro dela (funil, dispersão, velocidade de repasse, ciclos e centralidade),
gerando um **score de risco explicável de 0 a 100**.

> Projeto acadêmico. Esta etapa contém apenas o **backend** (API REST). O frontend Angular será servido
> estaticamente pelo próprio Spring Boot (`src/main/resources/static`).

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem / framework | Java 27, Spring Boot 4.1 (Web MVC, Data JPA, Security, Validation, Actuator) |
| Banco | PostgreSQL (Supabase em produção), migrations com Flyway |
| Segurança | Spring Security + JWT (HS256, OAuth2 Resource Server) e BCrypt |
| Grafos | JGraphT 1.5 (multigrafo direcionado, SCC de Kosaraju, Betweenness, PageRank) |
| Documentação | springdoc-openapi (Swagger UI) |
| Testes | JUnit 5, AssertJ, MockMvc, Testcontainers |
| Deploy | Docker (multi-stage) no Render |

## Arquitetura (MVC em camadas)

```
com.api.orangesentinel
├── controller/      Endpoints REST (camada de entrada)
├── service/         Regras de negócio
│   ├── antifraude/  Motor de risco
│   │   ├── grafo/   Grafo JGraphT, métricas, ciclos, velocidade de repasse
│   │   └── score/   Regressão logística + árvore de decisão
│   └── simulacao/   Gerador de dados sintéticos e avaliação (precisão/recall)
├── repository/      Acesso a dados (Spring Data JPA + JDBC em lote)
├── model/           Entidades JPA e enums
├── dto/             Records de request/response
├── config/          Segurança, JWT, OpenAPI, propriedades, seed do admin
├── security/        Emissão de token e leitura do usuário logado
└── exception/       Exceções de domínio e handler global (ProblemDetail / RFC 9457)
```

## Perfis de usuário

| Perfil | Pode |
|---|---|
| `CLIENTE` | Abrir/encerrar contas, cadastrar chaves Pix, depositar, sacar, enviar Pix, ver extrato |
| `ANALISTA` | Tudo em `/api/fraude/**`: ranking de risco, detalhes e motivos, grafo, ciclos, resolver alertas, disparar análise |
| `ADMIN` | O que o analista pode + usuários, parâmetros do modelo e simulador (`/api/admin/**`) |

O cadastro público (`/api/auth/registro`) cria sempre um `CLIENTE` com uma conta corrente. Analistas são criados
pelo admin em `POST /api/admin/usuarios`.

## Como o motor funciona

### 1. Grafo
- **Vértices**: contas. **Arestas direcionadas**: cada Pix concluído (valor + timestamp), dentro de uma janela
  configurável (padrão: 30 dias).
- Um **multigrafo** guarda cada transação; um **grafo agregado** (uma aresta por par, peso = soma) é usado para
  graus, ciclos e centralidade.
- Saques não viram aresta (o dinheiro sai do banco), mas contam como repasse.

### 2. Métricas por conta
| Métrica | O que indica |
|---|---|
| **Fan-in** (in-degree distinto) | Padrão funil: muitas vítimas pagando a mesma conta |
| **Fan-out** (out-degree distinto) | Padrão dispersão: uma conta espalhando valores |
| **Taxa de repasse** `min(recebido, enviado) / max(...)` | A conta não retém saldo |
| **Velocidade de repasse** (FIFO: Δt entre entrada e saída) | Mediana do tempo parado e % do valor repassado em até N min |
| **Ciclos** (SCC + DFS limitada + coerência temporal) | Dinheiro dando a volta A → B → C → A em poucas horas |
| **Betweenness / PageRank** | Intermediários críticos da rede |
| **Idade da conta** | Contas recém-abertas movimentando dinheiro de terceiros |

### 3. Score
**Regressão logística simplificada** com pesos editáveis:
`score = round(100 · σ(b0 + Σ wᵢ·xᵢ))`, com as features normalizadas em [0, 1]. Em seguida, uma **árvore de
decisão** cobre os padrões inequívocos (ciclo de lavagem, funil clássico, conta de passagem) e limita contas com
pouca evidência. Toda análise guarda a **contribuição de cada feature** e **motivos em linguagem natural**.

| Score | Classificação | Efeito |
|---|---|---|
| < 50 | `NORMAL` | — |
| 50–79 | `SUSPEITA` | Aparece no ranking |
| ≥ 80 | `LARANJA` | Conta vai para `SOB_ANALISE` e ganha um alerta |

### 4. Análise híbrida
- **Tempo real (a cada Pix)**, sem montar o grafo: destino bloqueado → `RECUSADA`; destino sinalizado como laranja
  → `RETIDA` (bloqueio cautelar); origem que acabou de receber de várias contas e repassa tudo → aprova e gera alerta.
- **Completa (grafo inteiro)**: agendada (`ANTIFRAUDE_CRON`, padrão a cada 15 min) ou sob demanda via
  `POST /api/fraude/analises`.
- **Decisão do analista** (`PATCH /api/fraude/alertas/{id}`): `CONFIRMADO` bloqueia a conta e **estorna** os Pix
  retidos às vítimas; `FALSO_POSITIVO` reativa a conta e libera os valores.

## Executando localmente

Pré-requisitos: JDK 27 e Docker.

```bash
docker compose up -d
```

```bash
./mvnw spring-boot:run
```

O perfil `dev` (padrão) usa o PostgreSQL do `compose.yaml`, cria o admin `admin@orangesentinel.dev` / `admin123`
e libera CORS para `http://localhost:4200`. A análise agendada fica desligada em dev.

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

### Testes

```bash
./mvnw test
```

Os testes unitários do motor rodam sem dependências. Os testes de integração (`integracao/`) sobem um PostgreSQL
via Testcontainers e são **pulados automaticamente se o Docker não estiver rodando**. O teste
`MotorSobreSimulacaoTest` executa o motor inteiro sobre dados simulados e exige precisão e recall ≥ 90%.

## Roteiro de demonstração

1. Faça login como admin (`POST /api/auth/login`) e use o token no botão **Authorize** do Swagger.
2. Gere dados: `POST /api/admin/simulacao` com `{"seed": 42}` (150 clientes normais, empresas e redes de
   funil, dispersão, cadeia e ciclo).
3. Rode o motor: `POST /api/fraude/analises`.
4. Veja a qualidade: `GET /api/admin/simulacao/avaliacao` (matriz de confusão, precisão, recall, F1 por cenário).
5. Explore: `GET /api/fraude/contas?classificacao=LARANJA`, `GET /api/fraude/contas/{id}` (motivos),
   `GET /api/fraude/contas/{id}/grafo` (nodes/edges prontos para Cytoscape.js ou D3), `GET /api/fraude/ciclos`.
6. Tempo real: cadastre um cliente, deposite e faça um Pix para a chave de uma laranja
   (`sim{contaId}.{seed}@orangesentinel.sim`) — o Pix volta `RETIDA`.
7. Resolva o alerta em `PATCH /api/fraude/alertas/{id}` com `{"decisao": "CONFIRMADO"}` e veja o estorno no extrato.

Todos os usuários simulados têm a senha `simulado123`. Para recomeçar: `DELETE /api/admin/simulacao`.

## Collection para Postman / Bruno

[`postman/OrangeSentinel.postman_collection.json`](postman/OrangeSentinel.postman_collection.json) tem 48
requisições em 10 pastas, cobrindo o roteiro acima, os erros esperados (400/401/403/422) e o fluxo de fraude
em tempo real.

- **Postman:** *Import* → selecione o arquivo. Rode pasta por pasta ou a collection inteira no *Runner*.
- **Bruno:** *Import Collection* → *Postman Collection* → selecione o arquivo.
- Os scripts de teste guardam tokens e ids (`tokenCliente`, `tokenAnalista`, `contaId`, `laranjaContaId`,
  `alertaId`...) nas variáveis da collection. Por isso, **rode as pastas em ordem** na primeira vez.
- CPFs e e-mails são gerados aleatoriamente a cada execução, então a collection pode ser rodada várias vezes.
- Para apontar para outro ambiente (ex.: Render), altere a variável `baseUrl`.

Para rodar tudo pela linha de comando:

```bash
npx newman run postman/OrangeSentinel.postman_collection.json
```

## Principais endpoints

| Método | Rota | Perfil |
|---|---|---|
| POST | `/api/auth/registro`, `/api/auth/login` | público |
| GET/PUT | `/api/usuarios/me` | autenticado |
| GET/POST | `/api/contas` · GET/DELETE `/api/contas/{id}` | cliente |
| GET/POST/DELETE | `/api/contas/{id}/chaves-pix` | cliente |
| POST | `/api/contas/{id}/deposito`, `/api/contas/{id}/saque` | cliente |
| GET | `/api/contas/{id}/extrato?de&ate&page&size` | cliente |
| POST | `/api/pix` (header opcional `Idempotency-Key`) | cliente |
| GET | `/api/fraude/contas`, `/api/fraude/contas/{id}`, `/api/fraude/contas/{id}/grafo` | analista |
| GET/POST | `/api/fraude/analises` · GET `/api/fraude/ciclos` | analista |
| GET/PATCH | `/api/fraude/alertas`, `/api/fraude/alertas/{id}` | analista |
| GET/PUT | `/api/admin/parametros-risco` | admin |
| GET/POST/PATCH | `/api/admin/usuarios` | admin |
| POST/DELETE | `/api/admin/simulacao` · GET `/api/admin/simulacao/avaliacao` | admin |

## Deploy (Render + Supabase)

1. No Supabase, copie a connection string do **Session pooler** e converta para JDBC:
   `jdbc:postgresql://<host>:5432/postgres?sslmode=require`.
2. No Render, crie um **Web Service** a partir do repositório usando o `Dockerfile`.
3. Configure as variáveis de `.env.example` (no mínimo `DATABASE_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`,
   `ADMIN_PASSWORD`). O Render define `PORT` automaticamente.
4. O Flyway cria o schema na primeira inicialização. Use `/actuator/health` como health check.

## Limitações conhecidas

- O simulador gera padrões de fraude bem separados do comportamento normal, por isso as métricas de avaliação
  saem altas; dados reais são mais ruidosos. Os pesos são definidos manualmente (não treinados).
- Os ciclos da última análise ficam em memória (somem ao reiniciar a aplicação; os scores ficam no banco).
- O grafo é reconstruído em memória a cada análise: adequado para milhares de contas, não para a escala de um banco
  real.
- `DELETE /api/admin/simulacao` remove também transações entre contas reais e simuladas, sem ajustar o saldo das
  contas reais.
