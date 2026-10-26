-- =====================================================================
-- OrangeSentinel - schema inicial
-- =====================================================================

CREATE TABLE usuario (
    id          BIGSERIAL PRIMARY KEY,
    nome        VARCHAR(120) NOT NULL,
    cpf         VARCHAR(11)  NOT NULL UNIQUE,
    email       VARCHAR(160) NOT NULL UNIQUE,
    senha_hash  VARCHAR(100) NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    simulado    BOOLEAN      NOT NULL DEFAULT FALSE,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE SEQUENCE conta_numero_seq START WITH 10000001;

CREATE TABLE conta (
    id                  BIGSERIAL PRIMARY KEY,
    usuario_id          BIGINT         NOT NULL REFERENCES usuario (id),
    agencia             VARCHAR(4)     NOT NULL DEFAULT '0001',
    numero              VARCHAR(10)    NOT NULL UNIQUE,
    tipo                VARCHAR(20)    NOT NULL,
    saldo               NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (saldo >= 0),
    status              VARCHAR(20)    NOT NULL,
    score_risco         INTEGER,
    classificacao_risco VARCHAR(20),
    analisado_em        TIMESTAMPTZ,
    criada_em           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version             BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX idx_conta_usuario ON conta (usuario_id);
CREATE INDEX idx_conta_score ON conta (score_risco DESC NULLS LAST);

CREATE TABLE chave_pix (
    id        BIGSERIAL PRIMARY KEY,
    conta_id  BIGINT       NOT NULL REFERENCES conta (id),
    tipo      VARCHAR(20)  NOT NULL,
    valor     VARCHAR(140) NOT NULL UNIQUE,
    criada_em TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_chave_pix_conta ON chave_pix (conta_id);

CREATE TABLE transacao (
    id               BIGSERIAL PRIMARY KEY,
    conta_origem_id  BIGINT REFERENCES conta (id),
    conta_destino_id BIGINT REFERENCES conta (id),
    valor            NUMERIC(19, 2) NOT NULL CHECK (valor > 0),
    tipo             VARCHAR(20)    NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    descricao        VARCHAR(140),
    motivo           VARCHAR(500),
    idempotency_key  VARCHAR(80)    NOT NULL UNIQUE,
    criada_em        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (conta_origem_id IS NOT NULL OR conta_destino_id IS NOT NULL)
);
CREATE INDEX idx_transacao_origem ON transacao (conta_origem_id, criada_em);
CREATE INDEX idx_transacao_destino ON transacao (conta_destino_id, criada_em);
CREATE INDEX idx_transacao_tipo_status ON transacao (tipo, status, criada_em);

CREATE TABLE execucao_analise (
    id            BIGSERIAL PRIMARY KEY,
    inicio        TIMESTAMPTZ NOT NULL,
    fim           TIMESTAMPTZ,
    janela_inicio TIMESTAMPTZ NOT NULL,
    n_contas      INTEGER     NOT NULL DEFAULT 0,
    n_arestas     INTEGER     NOT NULL DEFAULT 0,
    n_ciclos      INTEGER     NOT NULL DEFAULT 0,
    n_suspeitas   INTEGER     NOT NULL DEFAULT 0,
    n_laranjas    INTEGER     NOT NULL DEFAULT 0,
    duracao_ms    BIGINT,
    disparada_por VARCHAR(80) NOT NULL
);

CREATE TABLE analise_risco (
    id                    BIGSERIAL PRIMARY KEY,
    execucao_id           BIGINT           NOT NULL REFERENCES execucao_analise (id),
    conta_id              BIGINT           NOT NULL REFERENCES conta (id),
    score                 INTEGER          NOT NULL,
    classificacao         VARCHAR(20)      NOT NULL,
    in_degree             INTEGER          NOT NULL,
    out_degree            INTEGER          NOT NULL,
    qtd_recebidas         INTEGER          NOT NULL,
    qtd_enviadas          INTEGER          NOT NULL,
    total_recebido        NUMERIC(19, 2)   NOT NULL,
    total_enviado         NUMERIC(19, 2)   NOT NULL,
    taxa_repasse          DOUBLE PRECISION NOT NULL,
    mediana_repasse_seg   DOUBLE PRECISION,
    frac_repasse_rapido   DOUBLE PRECISION NOT NULL,
    em_ciclo              BOOLEAN          NOT NULL,
    betweenness           DOUBLE PRECISION NOT NULL,
    centralidade_relativa DOUBLE PRECISION NOT NULL,
    page_rank             DOUBLE PRECISION NOT NULL,
    idade_conta_dias      INTEGER          NOT NULL,
    contribuicoes         TEXT,
    motivos               TEXT,
    criada_em             TIMESTAMPTZ      NOT NULL DEFAULT now()
);
CREATE INDEX idx_analise_conta ON analise_risco (conta_id, criada_em DESC);
CREATE INDEX idx_analise_execucao ON analise_risco (execucao_id);

CREATE TABLE alerta_fraude (
    id            BIGSERIAL PRIMARY KEY,
    conta_id      BIGINT      NOT NULL REFERENCES conta (id),
    analise_id    BIGINT REFERENCES analise_risco (id),
    origem        VARCHAR(20) NOT NULL,
    score         INTEGER,
    motivos       TEXT,
    status        VARCHAR(20) NOT NULL,
    analista_id   BIGINT REFERENCES usuario (id),
    observacao    VARCHAR(500),
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolvido_em  TIMESTAMPTZ
);
-- No máximo um alerta aberto por conta
CREATE UNIQUE INDEX uq_alerta_aberto_conta ON alerta_fraude (conta_id) WHERE status = 'ABERTO';
CREATE INDEX idx_alerta_status ON alerta_fraude (status, criado_em DESC);

CREATE TABLE parametros_risco (
    id                     SMALLINT PRIMARY KEY CHECK (id = 1),
    intercepto             DOUBLE PRECISION NOT NULL,
    peso_fan_in            DOUBLE PRECISION NOT NULL,
    peso_fan_out           DOUBLE PRECISION NOT NULL,
    peso_taxa_repasse      DOUBLE PRECISION NOT NULL,
    peso_repasse_rapido    DOUBLE PRECISION NOT NULL,
    peso_ciclo             DOUBLE PRECISION NOT NULL,
    peso_centralidade      DOUBLE PRECISION NOT NULL,
    peso_conta_nova        DOUBLE PRECISION NOT NULL,
    limiar_suspeita        INTEGER          NOT NULL,
    limiar_laranja         INTEGER          NOT NULL,
    janela_dias            INTEGER          NOT NULL,
    repasse_rapido_minutos INTEGER          NOT NULL,
    ciclo_max_comprimento  INTEGER          NOT NULL,
    ciclo_janela_horas     INTEGER          NOT NULL,
    min_transacoes         INTEGER          NOT NULL,
    atualizado_em          TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE TABLE rotulo_simulacao (
    conta_id   BIGINT PRIMARY KEY REFERENCES conta (id),
    eh_laranja BOOLEAN     NOT NULL,
    cenario    VARCHAR(30) NOT NULL
);
