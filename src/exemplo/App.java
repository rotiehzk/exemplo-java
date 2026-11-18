package exemplo;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Servidor HTTP do próprio JDK, sem frameworks. */
public final class App {
    private final Auth auth;
    private final ProductDao products;
    private final Path css;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private record Attempts(int count, long expires) {}
    private static final class HttpError extends RuntimeException {
        final int code;
        HttpError(int code, String message) { super(message); this.code = code; }
    }

    public App(Db db, Path css) { auth = new Auth(db); products = new ProductDao(db); this.css = css; }

    public static void main(String[] args) throws Exception {
        Db db = new Db(Path.of("config.properties"));
        if (args.length > 0 && args[0].equals("--criar-admin")) {
            if (args.length != 2) throw new IllegalArgumentException("Usar: --criar-admin email@exemplo.pt");
            Console console = System.console();
            if (console == null) throw new IllegalStateException("Executa este comando num terminal para introduzir a palavra-passe em segurança.");
            char[] password = console.readPassword("Palavra-passe (mínimo 8 caracteres): ");
            char[] confirmation = console.readPassword("Repetir palavra-passe: ");
            try {
                if (password == null || confirmation == null || !Arrays.equals(password, confirmation))
                    throw new IllegalArgumentException("As palavras-passe não coincidem.");
                Auth.createAdmin(db, args[1], password);
                System.out.println("Administrador criado. Estrutura das tabelas preservada.");
            } finally {
                if (password != null) Arrays.fill(password, '\0');
                if (confirmation != null) Arrays.fill(confirmation, '\0');
            }
            return;
        }
        int port = Integer.parseInt(db.setting("server.port", "8080"));
        String host = db.setting("server.host", "127.0.0.1");
        App app = new App(db, Path.of("public", "style.css"));
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/", app::handle);
        var executor = Executors.newFixedThreadPool(8);
        server.setExecutor(executor);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { server.stop(1); executor.shutdownNow(); }));
        server.start();
        System.out.println("Exemplo: http://" + host + ":" + port + "/login");
        System.out.println("Parar: Ctrl+C. A ligação MySQL é aberta quando os dados são necessários.");
    }

    public void handle(HttpExchange x) throws IOException {
        boolean api = x.getRequestURI().getPath().startsWith("/api/");
        x.getResponseHeaders().set("Cache-Control", "no-store");
        x.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        x.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; style-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
        x.getResponseHeaders().set("Referrer-Policy", "same-origin");
        try {
            if (api) api(x); else web(x);
        } catch (HttpError e) {
            error(x, api, e.code, e.getMessage());
        } catch (IllegalArgumentException e) {
            error(x, api, 400, e.getMessage());
        } catch (SQLException e) {
            int code = sqlStatus(e);
            String message = sqlMessage(e);
            System.err.println("Base de dados: SQLState=" + e.getSQLState() + ", código=" + e.getErrorCode());
            error(x, api, code, message);
        } catch (Exception e) {
            System.err.println("Erro interno: " + e.getClass().getSimpleName());
            error(x, api, 500, "Ocorreu um erro interno no servidor.");
        } finally { x.close(); }
    }

    private static int sqlStatus(SQLException e) {
        return e.getSQLState() != null && e.getSQLState().startsWith("23") ? 409 : 503;
    }
    private static String sqlMessage(SQLException e) {
        if (e.getErrorCode() == 1062) return "Já existe um registo com esse nome ou email.";
        if (e.getErrorCode() == 1451) return "O produto está associado a encomendas. Altera o estado para Inativo.";
        if (e.getErrorCode() == 1452) return "A categoria selecionada não existe.";
        if (sqlStatus(e) == 409) return "A operação entra em conflito com dados existentes.";
        return "Não foi possível aceder ao MySQL. Verifica o serviço, a importação da base Exemplo e o config.properties.";
    }
    private void error(HttpExchange x, boolean api, int code, String message) throws IOException {
        if (api) json(x, code, Map.of("erro", message));
        else html(x, code, Views.error(code, message, auth.session(x)));
    }

    private void web(HttpExchange x) throws IOException, SQLException {
        String path = x.getRequestURI().getPath();
        String method = x.getRequestMethod();
        if (path.equals("/style.css")) {
            method(x, "GET"); send(x, 200, "text/css; charset=utf-8", Files.readString(css)); return;
        }
        if (path.equals("/")) { method(x, "GET"); redirect(x, "/dashboard"); return; }
        if (path.equals("/index.html")) { method(x, "GET"); redirect(x, "/login"); return; }
        if (path.equals("/login")) {
            method(x, "GET", "POST");
            Auth.Session s = auth.session(x);
            if (method.equals("GET")) {
                if (s != null && s.user != null) { redirect(x, "/dashboard"); return; }
                if (s == null) s = auth.create(x, null);
                html(x, 200, Views.login(s, "")); return;
            }
            var form = form(x);
            checkCsrf(s, form.get("csrf"));
            Auth.User user = login(x, form.getOrDefault("email", ""), form.getOrDefault("password", ""));
            if (user == null) { html(x, 401, Views.login(s, "Email ou palavra-passe incorretos.")); return; }
            auth.create(x, user); redirect(x, "/dashboard"); return;
        }
        Auth.Session s = auth.session(x);
        if (s == null || s.user == null) { redirect(x, "/login"); return; }
        var q = parameters(x.getRequestURI().getRawQuery());
        switch (path) {
            case "/logout" -> {
                method(x, "POST"); var f = form(x); checkCsrf(s, f.get("csrf"));
                auth.logout(x); redirect(x, "/login");
            }
            case "/dashboard" -> { method(x, "GET"); html(x, 200, Views.dashboard(products.dashboard(), s)); }
            case "/produtos" -> {
                method(x, "GET"); String search = search(q.getOrDefault("q", ""));
                String msg = switch (q.getOrDefault("ok", "")) { case "guardado" -> "Produto guardado."; case "eliminado" -> "Produto eliminado."; default -> ""; };
                html(x, 200, Views.products(products.list(search), search, msg, s));
            }
            case "/produtos/novo" -> {
                method(x, "GET"); admin(s); html(x, 200, Views.productForm(Map.of(), products.categories(), "", s));
            }
            case "/produtos/editar" -> {
                method(x, "GET"); admin(s); html(x, 200, Views.productForm(product(id(q.get("id"))), products.categories(), "", s));
            }
            case "/produtos/guardar" -> {
                method(x, "POST"); admin(s); var f = form(x); checkCsrf(s, f.get("csrf"));
                try {
                    ProductDao.Product p = ProductDao.Product.from(f);
                    if (f.containsKey("id_produto")) {
                        if (!products.update(id(f.get("id_produto")), p)) throw new HttpError(404, "Produto não encontrado.");
                    } else products.create(p);
                    redirect(x, "/produtos?ok=guardado");
                } catch (IllegalArgumentException e) {
                    html(x, 400, Views.productForm(f, products.categories(), e.getMessage(), s));
                } catch (SQLException e) {
                    if (sqlStatus(e) != 409) throw e;
                    html(x, 409, Views.productForm(f, products.categories(), sqlMessage(e), s));
                }
            }
            case "/produtos/eliminar" -> {
                method(x, "GET", "POST"); admin(s);
                if (method.equals("GET")) html(x, 200, Views.delete(product(id(q.get("id"))), s));
                else {
                    var f = form(x); checkCsrf(s, f.get("csrf"));
                    if (!products.delete(id(f.get("id_produto")))) throw new HttpError(404, "Produto não encontrado.");
                    redirect(x, "/produtos?ok=eliminado");
                }
            }
            default -> throw new HttpError(404, "Página não encontrada.");
        }
    }

    private void api(HttpExchange x) throws IOException, SQLException {
        String path = x.getRequestURI().getPath(), method = x.getRequestMethod();
        if (path.equals("/api/login")) {
            method(x, "POST"); sameOrigin(x);
            var f = jsonBody(x);
            Auth.User user = login(x, ProductDao.required(f, "email"), ProductDao.required(f, "password"));
            if (user == null) throw new HttpError(401, "Email ou palavra-passe incorretos.");
            var session = auth.create(x, user);
            json(x, 200, Map.of("utilizador", user.json(), "csrf", session.csrf)); return;
        }
        Auth.Session s = auth.session(x);
        if (s == null || s.user == null) throw new HttpError(401, "Autenticação necessária.");
        if (Set.of("POST", "PUT", "DELETE", "PATCH").contains(method)) {
            sameOrigin(x); checkCsrf(s, x.getRequestHeaders().getFirst("X-CSRF-Token"));
        }
        if (path.equals("/api/logout")) {
            method(x, "POST"); auth.logout(x); json(x, 200, Map.of("mensagem", "Sessão terminada.")); return;
        }
        if (path.equals("/api/me")) {
            method(x, "GET"); json(x, 200, Map.of("utilizador", s.user.json(), "csrf", s.csrf)); return;
        }
        if (path.equals("/api/dashboard")) { method(x, "GET"); json(x, 200, products.dashboard()); return; }
        if (path.equals("/api/categorias")) { method(x, "GET"); json(x, 200, products.categories()); return; }
        if (path.equals("/api/produtos")) {
            method(x, "GET", "POST");
            if (method.equals("GET")) {
                var q = parameters(x.getRequestURI().getRawQuery());
                json(x, 200, products.list(search(q.getOrDefault("q", ""))));
            } else {
                admin(s); int id = products.create(ProductDao.Product.from(jsonBody(x)));
                x.getResponseHeaders().set("Location", "/api/produtos/" + id);
                json(x, 201, products.find(id));
            }
            return;
        }
        if (path.matches("/api/produtos/[0-9]+")) {
            method(x, "GET", "PUT", "DELETE"); int id = id(path.substring(path.lastIndexOf('/') + 1));
            if (method.equals("GET")) json(x, 200, product(id));
            else {
                admin(s);
                if (method.equals("PUT")) {
                    if (!products.update(id, ProductDao.Product.from(jsonBody(x)))) throw new HttpError(404, "Produto não encontrado.");
                    json(x, 200, product(id));
                } else {
                    if (!products.delete(id)) throw new HttpError(404, "Produto não encontrado.");
                    x.sendResponseHeaders(204, -1);
                }
            }
            return;
        }
        throw new HttpError(404, "Endpoint não encontrado.");
    }

    private Auth.User login(HttpExchange x, String email, String password) throws SQLException {
        String ip = x.getRemoteAddress().getAddress().getHostAddress();
        long now = System.currentTimeMillis();
        attempts.values().removeIf(a -> a.expires < now);
        Attempts a = attempts.compute(ip, (key, old) -> old == null || old.expires < now ? new Attempts(1, now + 60_000) : new Attempts(old.count + 1, old.expires));
        if (a.count > 10) { x.getResponseHeaders().set("Retry-After", "60"); throw new HttpError(429, "Demasiadas tentativas. Aguarda um minuto."); }
        Auth.User user = auth.authenticate(email.trim(), password);
        if (user != null) attempts.remove(ip);
        return user;
    }
    private Map<String, Object> product(int id) throws SQLException {
        var p = products.find(id); if (p == null) throw new HttpError(404, "Produto não encontrado."); return p;
    }
    private static void admin(Auth.Session s) { if (!s.user.admin()) throw new HttpError(403, "Apenas o perfil Administrador pode alterar produtos."); }
    private static void checkCsrf(Auth.Session s, String token) { if (!Auth.csrf(s, token)) throw new HttpError(403, "Sessão ou token de segurança inválido. Reabre a página e tenta novamente."); }
    private static String search(String q) { if (q.length() > 100) throw new IllegalArgumentException("Pesquisa: máximo de 100 caracteres."); return q.trim(); }
    private static int id(String value) {
        try { int n = Integer.parseInt(value); if (n <= 0) throw new NumberFormatException(); return n; }
        catch (NumberFormatException e) { throw new IllegalArgumentException("ID inválido."); }
    }
    private static void sameOrigin(HttpExchange x) {
        String origin = x.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin.equals("http://" + x.getRequestHeaders().getFirst("Host"))) throw new HttpError(403, "Origem do pedido não permitida.");
    }
    private static void method(HttpExchange x, String... allowed) {
        if (!Arrays.asList(allowed).contains(x.getRequestMethod())) {
            x.getResponseHeaders().set("Allow", String.join(", ", allowed)); throw new HttpError(405, "Método HTTP não permitido.");
        }
    }
    private static String body(HttpExchange x, String contentType) throws IOException {
        String type = x.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase(contentType)) throw new HttpError(415, "Content-Type esperado: " + contentType);
        byte[] bytes = x.getRequestBody().readNBytes(16_385);
        if (bytes.length > 16_384) throw new HttpError(413, "Pedido demasiado grande.");
        return new String(bytes, StandardCharsets.UTF_8);
    }
    private static Map<String, String> form(HttpExchange x) throws IOException { return parameters(body(x, "application/x-www-form-urlencoded")); }
    private static Map<String, Object> jsonBody(HttpExchange x) throws IOException { return Json.parse(body(x, "application/json")); }
    private static Map<String, String> parameters(String raw) {
        Map<String, String> m = new LinkedHashMap<>();
        if (raw != null && !raw.isEmpty()) for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            if (m.containsKey(key)) throw new IllegalArgumentException("Campo repetido: " + key);
            m.put(key, kv.length == 2 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return m;
    }
    private static void redirect(HttpExchange x, String location) throws IOException { x.getResponseHeaders().set("Location", location); x.sendResponseHeaders(303, -1); }
    private static void html(HttpExchange x, int code, String text) throws IOException { send(x, code, "text/html; charset=utf-8", text); }
    private static void json(HttpExchange x, int code, Object value) throws IOException { send(x, code, "application/json; charset=utf-8", Json.stringify(value)); }
    private static void send(HttpExchange x, int code, String contentType, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", contentType);
        x.sendResponseHeaders(code, bytes.length); x.getResponseBody().write(bytes);
    }
}
