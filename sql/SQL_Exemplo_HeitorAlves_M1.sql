USE Exemplo;

-- 1. Salinas e respetivos tanques (inclui salinas sem tanques).
SELECT s.id_salina, s.nome AS salina, s.localizacao, s.capacidade_total,
       t.id_tanque, t.area_util, t.capacidade_maxima_producao,
       t.estado_funcionamento
FROM Salinas s
LEFT JOIN Tanques t ON t.salina_id = s.id_salina
ORDER BY s.id_salina, t.id_tanque;

-- 2. Trabalhadores, funcoes e salinas onde realizaram colheitas.
-- ATENCAO: o modelo nao guarda a colocacao/associacao dos trabalhadores
-- a salinas. Esta consulta mostra apenas salinas com atividade registada.
-- Um trabalhador sem colheitas aparece com salina NULL.
SELECT DISTINCT tr.id_trabalhador, tr.nome AS trabalhador,
       f.nome AS funcao, s.id_salina, s.nome AS salina_com_colheitas
FROM Trabalhadores tr
JOIN Funcoes f ON f.id_funcao = tr.funcao_id
LEFT JOIN Colheitas c ON c.trabalhadorAtividadeProduzida_id = tr.id_trabalhador
LEFT JOIN Tanques t ON t.id_tanque = c.tanque_id
LEFT JOIN Salinas s ON s.id_salina = t.salina_id
ORDER BY tr.id_trabalhador, s.id_salina;

-- 3. Utilizadores, perfis e trabalhador associado, quando existir.
SELECT u.id_utilizador, u.email AS email_acesso, p.nome AS perfil,
       tr.id_trabalhador, tr.nome AS trabalhador
FROM Utilizador u
JOIN Perfil p ON p.id_perfil = u.perfil_id
LEFT JOIN Trabalhadores tr ON tr.id_trabalhador = u.trabalhador_id
ORDER BY u.id_utilizador;

-- 4. Colheitas com tanque, salina, responsavel e utilizador que registou.
SELECT c.id_colheita, c.data_colheita, c.quantidade_produzida,
       t.id_tanque, s.id_salina, s.nome AS salina,
       tr.id_trabalhador, tr.nome AS trabalhador_responsavel,
       u.id_utilizador, u.email AS utilizador_registo, c.observacoes
FROM Colheitas c
JOIN Tanques t ON t.id_tanque = c.tanque_id
JOIN Salinas s ON s.id_salina = t.salina_id
JOIN Trabalhadores tr ON tr.id_trabalhador = c.trabalhadorAtividadeProduzida_id
JOIN Utilizador u ON u.id_utilizador = c.utilizadorRegistroOp_id
ORDER BY c.data_colheita, c.id_colheita;

-- 5. Numero de colheitas e quantidade produzida por salina.
SELECT s.id_salina, s.nome AS salina,
       COUNT(c.id_colheita) AS numero_colheitas,
       COALESCE(SUM(c.quantidade_produzida), 0) AS quantidade_total
FROM Salinas s
LEFT JOIN Tanques t ON t.salina_id = s.id_salina
LEFT JOIN Colheitas c ON c.tanque_id = t.id_tanque
GROUP BY s.id_salina, s.nome
ORDER BY s.id_salina;

-- 6. Numero de colheitas e quantidade produzida por trabalhador.
SELECT tr.id_trabalhador, tr.nome AS trabalhador,
       COUNT(c.id_colheita) AS numero_colheitas,
       COALESCE(SUM(c.quantidade_produzida), 0) AS quantidade_total
FROM Trabalhadores tr
LEFT JOIN Colheitas c ON c.trabalhadorAtividadeProduzida_id = tr.id_trabalhador
GROUP BY tr.id_trabalhador, tr.nome
ORDER BY tr.id_trabalhador;

-- 7. Produtos e respetivas categorias.
SELECT p.id_produto, p.nome AS produto, p.peso_unitario, p.preco_venda,
       p.stock_disponivel, p.estado_produto,
       cat.id_categoria, cat.nome AS categoria, cat.designacao
FROM Produtos p
JOIN Categorias cat ON cat.id_categoria = p.categoria_id
ORDER BY cat.nome, p.nome;

-- 8. Encomendas, cliente e utilizador que registou.
SELECT e.id_encomenda, e.data_encomenda, e.estado_encomenda,
       cl.id_cliente, cl.nome AS cliente,
       u.id_utilizador, u.email AS utilizador_registo,
       e.total AS total_guardado, e.observacoes
FROM Encomendas e
JOIN Clientes cl ON cl.id_cliente = e.cliente_id
JOIN Utilizador u ON u.id_utilizador = e.utilizador_id
ORDER BY e.data_encomenda, e.id_encomenda;

-- 9. Produtos de uma encomenda. Substituir 1 pelo ID pretendido.
SET @encomenda_id = 1;
SELECT i.encomenda_id, i.id_item_encomenda, p.id_produto,
       p.nome AS produto, i.quantidade, i.preco_unitario
FROM ItensEncomenda i
JOIN Produtos p ON p.id_produto = i.produto_id
WHERE i.encomenda_id = @encomenda_id
ORDER BY i.id_item_encomenda;

-- 10. Subtotal de cada linha de produto, em todas as encomendas.
-- Usa o preco historico do item, nao o preco atual do produto.
SELECT i.encomenda_id, i.id_item_encomenda, p.nome AS produto,
       i.quantidade, i.preco_unitario,
       i.quantidade * i.preco_unitario AS subtotal
FROM ItensEncomenda i
JOIN Produtos p ON p.id_produto = i.produto_id
ORDER BY i.encomenda_id, i.id_item_encomenda;

-- 11. Total calculado de cada encomenda e total guardado para comparacao.
-- A consulta nao altera o total guardado nem o stock.
SELECT e.id_encomenda, e.total AS total_guardado,
       COALESCE(SUM(i.quantidade * i.preco_unitario), 0) AS total_calculado
FROM Encomendas e
LEFT JOIN ItensEncomenda i ON i.encomenda_id = e.id_encomenda
GROUP BY e.id_encomenda, e.total
ORDER BY e.id_encomenda;

-- 12. Filtros opcionais: substituir NULL pelos valores pretendidos.
-- Exemplos: cliente = 1, datas = '2026-01-01' e '2026-12-31',
-- estado = um estado que exista nos teus dados.
-- NULL desativa o respetivo filtro; os filtros preenchidos combinam-se.
SET @cliente_id = NULL;
SET @data_inicio = NULL;
SET @data_fim = NULL;
SET @estado = NULL;

SELECT e.id_encomenda, cl.nome AS cliente, e.data_encomenda,
       e.estado_encomenda, e.total AS total_guardado
FROM Encomendas e
JOIN Clientes cl ON cl.id_cliente = e.cliente_id
WHERE (@cliente_id IS NULL OR e.cliente_id = @cliente_id)
  AND (@data_inicio IS NULL OR e.data_encomenda >= @data_inicio)
  AND (@data_fim IS NULL OR e.data_encomenda <= @data_fim)
  AND (@estado IS NULL OR e.estado_encomenda = @estado)
ORDER BY e.data_encomenda, e.id_encomenda;

-- 13a. Producao por dia.
SELECT data_colheita AS dia, COUNT(*) AS numero_colheitas,
       SUM(quantidade_produzida) AS quantidade_total
FROM Colheitas
GROUP BY data_colheita
ORDER BY dia;

-- 13b. Producao por mes (separa os meses de anos diferentes).
SELECT YEAR(data_colheita) AS ano, MONTH(data_colheita) AS mes,
       COUNT(*) AS numero_colheitas,
       SUM(quantidade_produzida) AS quantidade_total
FROM Colheitas
GROUP BY YEAR(data_colheita), MONTH(data_colheita)
ORDER BY ano, mes;

-- 13c. Producao por ano.
SELECT YEAR(data_colheita) AS ano, COUNT(*) AS numero_colheitas,
       SUM(quantidade_produzida) AS quantidade_total
FROM Colheitas
GROUP BY YEAR(data_colheita)
ORDER BY ano;