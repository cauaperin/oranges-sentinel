# OrangeSentinel: serviço banco

Banco digital simulado com um **motor antifraude baseado em Teoria dos Grafos** para detectar **contas laranja**
(mule accounts), a infraestrutura usada para lavagem de dinheiro e golpes de Pix.

Este repositório é o serviço **banco**: contas, chaves Pix, depósito, saque, Pix e extrato. A autenticação e o motor
antifraude são serviços próprios.

| Serviço | Responsabilidade | Repositório |
|---|---|---|
| auth | Usuários, login, emissão do JWT (RS256 + JWKS) | `oranges-sentinel-auth` |
| **banco** (este) | Contas, chaves Pix, transações, extrato | `oranges-sentinel` |
| motor | Grafo, score de risco, alertas, parâmetros, simulador | `oranges-sentinel-motor` |
| frontend | Interface Vue.js | `oranges-sentinel-frontend` |

> Projeto acadêmico (A3, Sistemas Distribuídos). O algoritmo do motor está documentado no README do repositório
> `oranges-sentinel-motor`.

## Arquitetura

```
frontend ─┬─▶ auth   (login, registro) ──── POST /internal/contas/inicial ───▶ banco
          ├─▶ banco  (contas, Pix, extrato) ──── POST /internal/avaliacoes/pix ──▶ motor
          └─▶ motor  (analista, admin) ──── /internal/** ───▶ banco, auth
```

- **JWT:** o auth assina com RS256; o banco só valida, buscando a chave pública em `AUTH_JWKS_URI`. Nenhum serviço
  além do auth consegue forjar um token.
- **Chamadas entre serviços:** `/internal/**` exige o header `X-Internal-Key` (`INTERNAL_KEY`, igual nos três serviços).
  Um token de usuário não abre essas rotas.
- **Dados:** cada serviço tem o seu schema. A conta guarda o id do usuário (do auth) e uma **cópia** do nome e do CPF
  do titular, tirada na abertura a partir das claims do JWT.
- **Pix:** antes de efetivar, o banco envia ao motor o status das duas contas, o último risco calculado e os
  recebimentos dos últimos 60 minutos da origem. O motor responde `APROVAR`, `APROVAR_COM_ALERTA`, `RETER` ou
  `RECUSAR`. Se o motor não responder em 3 s (ou `MOTOR_URL` estiver vazio), o banco **aprova o Pix sem análise**
  (*fail-open*) e a análise em lote revisa depois.
- **Decisão do analista:** quem move o dinheiro é o banco. O motor chama `POST /internal/contas/{id}/decisao-fraude`:
  `CONFIRMADO` bloqueia a conta e estorna os Pix retidos; `FALSO_POSITIVO` reativa a conta e libera os valores.

```
com.api.orangesentinel
├── controller/   Endpoints REST do cliente e a API interna (Internal*Controller)
├── service/      Regras de negócio (contas, Pix, decisão de fraude, simulador em lote)
├── client/       MotorClient: chamada ao motor, com fail-open
├── repository/   Spring Data JPA + JDBC em lote
├── model/        Entidades JPA e enums
├── dto/          Records de request/response
├── config/       Segurança (JWKS), OpenAPI
├── security/     Filtro da chave interna e leitura do usuário logado
└── exception/    Exceções de domínio e handler global (ProblemDetail / RFC 9457)
```

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem / framework | Java 27, Spring Boot 4.1 (Web MVC, Data JPA, Security, Validation, Actuator) |
| Banco | PostgreSQL (Supabase em produção), migrations com Flyway |
| Segurança | Spring Security, OAuth2 Resource Server (JWT RS256 via JWKS) |
| Documentação | springdoc-openapi (Swagger UI) |
| Testes | JUnit 5, AssertJ, MockMvc, Testcontainers |
| Deploy | Docker (multi-stage) no Render |

## Endpoints

| Método | Rota | Quem |
|---|---|---|
| GET/POST | `/api/contas` · GET/DELETE `/api/contas/{id}` | cliente |
| GET/POST/DELETE | `/api/contas/{id}/chaves-pix` | cliente |
| POST | `/api/contas/{id}/deposito`, `/api/contas/{id}/saque` | cliente |
| GET | `/api/contas/{id}/extrato?de&ate&page&size` | cliente |
| POST | `/api/pix` (header opcional `Idempotency-Key`) | cliente |
| POST | `/internal/contas/inicial` | interno (auth, no cadastro) |
| POST | `/internal/contas/consulta` · GET `/internal/contas/ranking` | interno (motor) |
| PUT | `/internal/contas/riscos` · POST `/internal/contas/{id}/decisao-fraude` | interno (motor) |
| GET | `/internal/transacoes/arestas`, `/internal/transacoes/saques` | interno (motor) |
| POST/DELETE | `/internal/simulacao/contas`, `/internal/simulacao/transacoes`, `/internal/simulacao` | interno (motor) |

Cadastro e login ficam no auth; ranking de risco, alertas e simulador ficam no motor.

## Executando localmente

Pré-requisitos: JDK 27 e Docker. Suba os três serviços (cada um no seu repositório) com a mesma `INTERNAL_KEY`
(em dev o padrão é `dev-internal-key`):

| Serviço | Porta | Postgres do compose |
|---|---|---|
| banco | 8080 | 5432 |
| auth | 8081 | 5433 |
| motor | 8082 | 5434 |

```bash
docker compose up -d
./mvnw spring-boot:run   # perfil dev, Swagger em :8080/swagger-ui.html
```

O perfil `dev` libera CORS para `http://localhost:5173` (frontend Vite), espera o auth em `:8081` (JWKS) e o motor em
`:8082`.

### Testes

```bash
./mvnw test
```

Os testes de integração (`integracao/`) sobem um PostgreSQL via Testcontainers e são **pulados automaticamente se o
Docker não estiver rodando**. O motor é falso e os tokens são assinados por uma chave de teste, no formato do auth.
`MotorRestClientTest` confirma o fail-open.

## Deploy (Render + Supabase)

1. No Supabase, copie a connection string do **Session pooler** e converta para JDBC:
   `jdbc:postgresql://<host>:5432/postgres?sslmode=require`.
2. No Render, crie um **Web Service** a partir do `Dockerfile`, na região do Supabase (ex.: Virginia).
3. Configure as variáveis de `.env.example` (no mínimo `DATABASE_URL`, `DB_USER`, `DB_PASSWORD`, `AUTH_JWKS_URI`,
   `INTERNAL_KEY` e `MOTOR_URL`). O Render define `PORT` automaticamente.
4. O Flyway cria o schema na primeira inicialização. Use `/actuator/health` como health check.

> **Banco que já tinha a versão monolítica:** a migration `V3__separa_servicos.sql` remove `usuario` e as tabelas do
> antifraude. Como os ids de usuário agora são gerados pelo auth, as contas antigas apontariam para usuários que não
> existem mais. Recomece com o schema `public` limpo (`DROP SCHEMA public CASCADE; CREATE SCHEMA public;`).

## Limitações conhecidas

- A avaliação do Pix pelo motor acontece dentro da transação do Pix, com as duas contas travadas: um motor lento
  atrasa o Pix em até o timeout (3 s).
- O nome e o CPF do titular são uma cópia feita na abertura da conta: mudanças posteriores no perfil (auth) não
  atualizam contas já abertas.
- `DELETE /internal/simulacao` remove também transações entre contas reais e simuladas, sem ajustar o saldo das
  contas reais.
- A collection do Postman em `postman/` ainda reflete a API monolítica (uma `baseUrl` só) e precisa ser atualizada.
