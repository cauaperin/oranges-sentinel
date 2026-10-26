-- Pesos iniciais da regressão logística simplificada (editáveis via /api/admin/parametros-risco)
INSERT INTO parametros_risco (id, intercepto, peso_fan_in, peso_fan_out, peso_taxa_repasse, peso_repasse_rapido,
                              peso_ciclo, peso_centralidade, peso_conta_nova, limiar_suspeita, limiar_laranja,
                              janela_dias, repasse_rapido_minutos, ciclo_max_comprimento, ciclo_janela_horas,
                              min_transacoes)
VALUES (1, -6.5, 3.0, 2.0, 2.0, 3.5, 2.5, 1.5, 1.5, 50, 80, 30, 60, 6, 72, 3);
