# Exemplo — Java + MySQL + HTML/CSS

Aplicação local sem frameworks. O servidor HTTP pertence ao JDK; o único JAR externo é o driver JDBC oficial MySQL Connector/J 9.6.0, incluído em `lib/`. Não é necessário Maven, Tomcat ou JavaScript.

## O que funciona

- Login/logout com email e palavra-passe, sessões de 30 minutos de inatividade e páginas protegidas.
- Perfil **Administrador**: criar, pesquisar, listar, editar e eliminar produtos. Outros perfis: leitura.
- Dashboard com número de salinas, quantidade produzida, número de encomendas e cinco encomendas recentes.
- API REST JSON para autenticação, dashboard, categorias e CRUD de produtos.
- Validação no servidor, consultas parametrizadas, escape HTML e token CSRF para alterações.
- Eliminação confirmada na interface. Produtos já associados a itens de encomenda não são eliminados.

O backend cobre as páginas do layout simples. Não inclui CRUD das restantes entidades, gestão de encomendas/stock nem relatórios exportáveis. Não cria nem modifica tabelas automaticamente. A criação explícita do administrador insere uma conta e, se necessário, o perfil Administrador.

## Preparar no Windows

1. Instalar **JDK 17 ou superior** e ter `java` e `javac` no PATH.
2. Iniciar MySQL. Importar `sql/DB_Exemplo_HeitorAlves_M1.sql` **apenas se a base Exemplo ainda não existir**. Se já a criaste, usa a existente. O script original contém `CREATE DATABASE`, pelo que não deve ser executado novamente sem necessidade.
3. Copiar `config.example.properties` para `config.properties` e preencher o utilizador e a palavra-passe MySQL. A conta precisa de acesso à base Exemplo. Não confundir estas credenciais com as do login da aplicação.
4. Num terminal aberto nesta pasta, criar a tua conta:

```powershell
.\criar-admin.cmd teu-email@exemplo.pt
```

A palavra-passe é pedida no terminal, sem ser mostrada. Exige pelo menos 8 caracteres. Não existe conta ou palavra-passe predefinida. Um email já existente é recusado, sem substituir a conta.

5. Iniciar:

```powershell
.\iniciar.cmd
```

6. Abrir **http://127.0.0.1:8080/login**. Parar com Ctrl+C. Usa sempre o mesmo endereço durante a sessão.

O servidor fica acessível apenas na própria máquina. A porta pode ser alterada em `config.properties`. O driver incluído é destinado a MySQL 8.0 ou superior. Para versões antigas, instalar uma versão compatível do Connector/J e remover o JAR anterior da pasta `lib/`.

## Categorias e base existente

O teu script cria as tabelas, mas não insere registos. O formulário de produtos precisa de pelo menos uma categoria. Podes criá-la no teu cliente MySQL, por exemplo, se ainda não existir:

```sql
USE Exemplo;
INSERT INTO Categorias (nome, descricao, designacao)
VALUES ('Sal marinho', 'Sal produzido nas salinas', 'SAL-MARINHO');
```

Os estados usados no formulário são `Ativo` e `Inativo`. As categorias são carregadas da base; não dependem de IDs fixos. As unidades do peso e da produção devem ser as definidas no teu enunciado, pois o esquema original não as especifica.

As palavras-passe são guardadas no campo existente `password_hash` como `pbkdf2-sha256$iterações$salt$hash`, com salt aleatório. Contas anteriormente criadas com outro formato de hash não entram automaticamente; adapta a verificação ao formato real ou cria uma nova conta através do comando. Não guardar palavras-passe em texto simples.

## API REST

Todos os endpoints, exceto o login, exigem o cookie de sessão. POST/PUT/DELETE autenticados exigem o cabeçalho `X-CSRF-Token`, devolvido no login e em `/api/me`. Os pedidos com corpo JSON usam `Content-Type: application/json`.

| Método | Rota | Função |
|---|---|---|
| POST | `/api/login` | Email e password; devolve utilizador e token CSRF |
| POST | `/api/logout` | Terminar sessão |
| GET | `/api/me` | Utilizador atual e token CSRF |
| GET | `/api/dashboard` | Indicadores e encomendas recentes |
| GET | `/api/categorias` | Categorias disponíveis |
| GET | `/api/produtos?q=sal` | Produtos, até 200 resultados |
| GET | `/api/produtos/1` | Um produto |
| POST | `/api/produtos` | Criar produto (Administrador) |
| PUT | `/api/produtos/1` | Substituir todos os campos editáveis (Administrador) |
| DELETE | `/api/produtos/1` | Eliminar produto (Administrador) |

Códigos: 200 sucesso; 201 criado; 204 eliminado; 400 validação; 401 sem autenticação; 403 sem permissão/token; 404 inexistente; 405 método incorreto; 409 conflito; 413 corpo demasiado grande; 415 formato de corpo incorreto; 429 demasiados logins; 503 falha MySQL.

Exemplo PowerShell, após criares a conta (a palavra-passe é pedida sem ficar escrita no comando):

```powershell
$cred = Get-Credential -Message 'Conta Exemplo'
$body = @{ email = $cred.UserName; password = $cred.GetNetworkCredential().Password } | ConvertTo-Json
$login = Invoke-RestMethod http://127.0.0.1:8080/api/login -Method Post -ContentType 'application/json' -Body $body -SessionVariable sessao
$body = $null
$headers = @{ 'X-CSRF-Token' = $login.csrf }
Invoke-RestMethod http://127.0.0.1:8080/api/produtos -WebSession $sessao
Invoke-RestMethod http://127.0.0.1:8080/api/categorias -WebSession $sessao
```

Corpo para criar/editar (substituir `categoria_id` por um ID real):

```json
{
  "nome": "Sal tradicional",
  "categoria_id": 1,
  "peso_unitario": 0.50,
  "preco_venda": 2.50,
  "stock_disponivel": 100,
  "estado_produto": "Ativo"
}
```

## Organização

- `App.java`: arranque, rotas web/API, respostas HTTP e validação dos pedidos.
- `Db.java`: configuração e acesso JDBC.
- `Auth.java`: sessões, palavras-passe, perfis e criação de administrador.
- `ProductDao.java`: consultas e validação de produtos.
- `Views.java`: prepara os dados e escolhe os ficheiros HTML.
- `Templates.java`: lê os HTML e preenche valores, condições e listas, sem biblioteca externa.
- `templates/`: ficheiros HTML separados: `login.html`, `dashboard.html`, `produtos.html`, `produto-form.html`, `eliminar-produto.html` e `erro.html`. `layout.html` define a estrutura da página e `header.html` contém o menu.
- `Json.java`: JSON de saída e leitura de objetos planos de entrada.
- `public/style.css`: estilos do layout simples.
- `sql/`: scripts SQL adaptados ao nome de base Exemplo; estrutura das tabelas preservada.
- `test/exemplo/SelfTest.java`: testes sem MySQL; executar `testar.cmd`.

O servidor usa HTTP local e sessões em memória: reiniciar termina as sessões. Os cookies são HttpOnly/SameSite=Strict; HTTPS não está configurado. Esta versão foi preparada para execução local e estudo. As regras específicas do júri determinam se podes levar código previamente preparado.

## Editar o HTML

Altera os ficheiros da pasta `templates/` para mudar as páginas. O Java lê-os em cada pedido, por isso basta guardar e atualizar o navegador; não precisas de recompilar para alterar apenas o HTML. Mantém a pasta `templates/` junto de `src/`, `public/` e dos comandos de arranque.

`{{nome}}` é substituído por um valor escapado. `{{#produtos}}...{{/produtos}}` repete linhas; `{{#admin}}...{{/admin}}` mostra conteúdo condicional. Só `layout.html` usa `{{{header}}}` e `{{{body}}}` para incluir outros HTML já renderizados. Não usar chavetas triplas para valores introduzidos pelo utilizador.

As páginas usam `layout.html` para partilhar a estrutura e o CSS. Abre-as através de `http://127.0.0.1:8080/`, com o servidor iniciado, para ver os dados e formulários a funcionar. Os ficheiros são templates, pelo que o duplo clique num HTML mostra apenas o conteúdo sem preencher os dados MySQL.

Referências: [JDK HttpServer](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html) e [MySQL Connector/J](https://dev.mysql.com/doc/connector-j/en/).

JAR incluído: `mysql-connector-j-9.6.0.jar`, SHA-256 `66DF1D453789DC8CB759A7DC17F58646893BF28483F262328650F170472A6EAD`. As licenças do driver encontram-se dentro do próprio JAR. Código e driver estão separados para permitir inspeção e substituição.

## Nome e base de dados

Este pacote usa o nome genérico Exemplo. Os SQL criam/usam a base Exemplo. Para ligar a outra base existente, altera o nome da base em `db.url` no `config.properties`; não precisas de alterar os nomes das tabelas. Os ficheiros originais fornecidos não foram modificados.
