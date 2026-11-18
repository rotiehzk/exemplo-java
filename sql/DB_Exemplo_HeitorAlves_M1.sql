CREATE DATABASE Exemplo;

USE Exemplo;

#--------------------SALINAS E TANQUES---------------------------------------

CREATE TABLE Salinas (
    id_salina INT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL UNIQUE,
    localizacao VARCHAR(255) NOT NULL,
    capacidade_total INT NOT NULL
);

CREATE TABLE Tanques (
    id_tanque INT AUTO_INCREMENT PRIMARY KEY,
    area_util INT NOT NULL,
    capacidade_maxima_producao INT NOT NULL,
    salina_id INT NOT NULL,
    estado_funcionamento VARCHAR(50) NOT NULL,

    FOREIGN KEY (salina_id) REFERENCES Salinas(id_salina)
);

#--------------------TRABALHADORES / FUNCOES / Utilizadores--------------------

CREATE TABLE Funcoes (
    id_funcao INT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE Trabalhadores (
    id_trabalhador INT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    contacto VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    funcao_id INT NOT NULL,
    data_admissao DATE NOT NULL,
    estado_emprego VARCHAR(50) NOT NULL,

    FOREIGN KEY (funcao_id) REFERENCES Funcoes(id_funcao)
);

CREATE TABLE Perfil(
    id_perfil INT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(60) NOT NULL
  
);


CREATE TABLE Utilizador (
    id_utilizador INT AUTO_INCREMENT PRIMARY KEY,
    trabalhador_id INT NULL UNIQUE,
    perfil_id INT NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,

    FOREIGN KEY (trabalhador_id) REFERENCES Trabalhadores(id_trabalhador),
    FOREIGN KEY (perfil_id) REFERENCES Perfil(id_perfil)
);

#--------------------COLHEITAS--------------------

CREATE TABLE Colheitas (
    id_colheita INT AUTO_INCREMENT PRIMARY KEY,
    data_colheita DATE NOT NULL,
    quantidade_produzida INT NOT NULL,
    tanque_id INT NOT NULL,
    trabalhadorAtividadeProduzida_id INT NOT NULL,
    utilizadorRegistroOp_id INT NOT NULL,
    observacoes TEXT,

    FOREIGN KEY (tanque_id) REFERENCES Tanques(id_tanque),
    FOREIGN KEY (trabalhadorAtividadeProduzida_id) REFERENCES Trabalhadores(id_trabalhador),
    FOREIGN KEY (utilizadorRegistroOp_id) REFERENCES Utilizador(id_utilizador)
);

#--------------------CATEGORIAS E PRODUTOS--------------------

CREATE TABLE Categorias (
    id_categoria INT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL UNIQUE,
    descricao TEXT,
    designacao VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE Produtos (
    id_produto INT AUTO_INCREMENT PRIMARY KEY,
    categoria_id INT NOT NULL,
    nome VARCHAR(100) NOT NULL UNIQUE,
    peso_unitario DECIMAL(10, 2) NOT NULL,
    preco_venda DECIMAL(10, 2) NOT NULL,
    stock_disponivel INT NOT NULL,
    estado_produto VARCHAR(50) NOT NULL,

    FOREIGN KEY (categoria_id) REFERENCES Categorias(id_categoria)
);

#--------------------CLIENTES--------------------

CREATE TABLE Clientes (
    id_cliente INT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    contacto VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    morada VARCHAR(255) NOT NULL,
    estado_cliente VARCHAR(50) NOT NULL,
    Numero_Identificacao_Fiscal VARCHAR(20) NOT NULL UNIQUE

);

#----------------ENCOMENDAS E ITENS DE ENCOMENDAS--------------------

CREATE TABLE Encomendas (
    id_encomenda INT AUTO_INCREMENT PRIMARY KEY,
    cliente_id INT NOT NULL,
    utilizador_id INT NOT NULL,
    data_encomenda DATE NOT NULL,
    estado_encomenda VARCHAR(50) NOT NULL,
    observacoes TEXT,
    total DECIMAL(10, 2) NOT NULL,

    FOREIGN KEY (cliente_id) REFERENCES Clientes(id_cliente),
    FOREIGN KEY (utilizador_id) REFERENCES Utilizador(id_utilizador)
);


CREATE TABLE ItensEncomenda (
    id_item_encomenda INT AUTO_INCREMENT PRIMARY KEY,
    encomenda_id INT NOT NULL,
    produto_id INT NOT NULL,
    quantidade INT NOT NULL,
    preco_unitario DECIMAL(10, 2) NOT NULL,

    FOREIGN KEY (encomenda_id) REFERENCES Encomendas(id_encomenda),
    FOREIGN KEY (produto_id) REFERENCES Produtos(id_produto)
);


