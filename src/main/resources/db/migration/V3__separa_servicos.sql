-- =====================================================================
-- Separação em serviços (auth, banco, motor)
--
-- Usuários passam a ser do serviço auth e as tabelas do antifraude (análises, alertas, parâmetros e rótulos do
-- simulador) passam a ser do serviço motor. Aqui a conta deixa de referenciar a tabela usuario e guarda só o id do
-- usuário, mais uma cópia do nome e do CPF do titular tirada na abertura.
--
-- ATENÇÃO: os ids de usuário agora são gerados pelo auth. Em um banco com dados da versão monolítica, as contas
-- existentes apontariam para usuários que não existem mais; recomece com o schema limpo (ver README).
-- =====================================================================

ALTER TABLE conta ADD COLUMN titular_nome VARCHAR(120);
ALTER TABLE conta ADD COLUMN titular_cpf  VARCHAR(11);
UPDATE conta c SET titular_nome = u.nome, titular_cpf = u.cpf FROM usuario u WHERE u.id = c.usuario_id;
ALTER TABLE conta ALTER COLUMN titular_nome SET NOT NULL;
ALTER TABLE conta ALTER COLUMN titular_cpf SET NOT NULL;

-- Contas geradas pelo simulador (o motor pede para criá-las e removê-las)
ALTER TABLE conta ADD COLUMN simulada BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE conta SET simulada = TRUE WHERE usuario_id IN (SELECT id FROM usuario WHERE simulado);

ALTER TABLE conta DROP CONSTRAINT conta_usuario_id_fkey;

DROP TABLE rotulo_simulacao;
DROP TABLE alerta_fraude;
DROP TABLE analise_risco;
DROP TABLE execucao_analise;
DROP TABLE parametros_risco;
DROP TABLE usuario;
