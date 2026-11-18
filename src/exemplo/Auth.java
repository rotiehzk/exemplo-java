package exemplo;

import com.sun.net.httpserver.HttpExchange;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Auth {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 600_000;
    private static final long TTL = Duration.ofMinutes(30).toMillis();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Db db;
    // Usado para não revelar pela duração se o email existe.
    private final String dummyHash = hash("conta-inexistente".toCharArray());

    public record User(int id, String email, String perfil) {
        public boolean admin() { return "Administrador".equalsIgnoreCase(perfil); }
        public Map<String, Object> json() { return Map.of("id_utilizador", id, "email", email, "perfil", perfil); }
    }

    public static final class Session {
        final String id = token();
        final String csrf = token();
        final User user;
        volatile long expires = System.currentTimeMillis() + TTL;
        Session(User user) { this.user = user; }
    }

    public Auth(Db db) { this.db = db; }

    public User authenticate(String email, String password) throws SQLException {
        if (email.length() > 100 || password.length() > 1024) return null;
        var rows = db.query("SELECT u.id_utilizador, u.email, u.password_hash, p.nome AS perfil " +
            "FROM Utilizador u JOIN Perfil p ON p.id_perfil=u.perfil_id WHERE u.email=?", email);
        String stored = rows.isEmpty() ? dummyHash : String.valueOf(rows.get(0).get("password_hash"));
        boolean valid = verify(password.toCharArray(), stored);
        if (!valid || rows.isEmpty()) return null;
        var r = rows.get(0);
        return new User(((Number) r.get("id_utilizador")).intValue(), r.get("email").toString(), r.get("perfil").toString());
    }

    public Session session(HttpExchange x) {
        String cookie = x.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) return null;
        for (String part : cookie.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals("EXEMPLO_SESSION")) {
                Session s = sessions.get(kv[1]);
                if (s == null) return null;
                if (s.expires < System.currentTimeMillis()) { sessions.remove(s.id); return null; }
                s.expires = System.currentTimeMillis() + TTL;
                cookie(x, s.id, 1800);
                return s;
            }
        }
        return null;
    }

    public Session create(HttpExchange x, User user) {
        Session old = session(x);
        if (old != null) sessions.remove(old.id);
        sessions.values().removeIf(s -> s.expires < System.currentTimeMillis());
        Session s = new Session(user);
        sessions.put(s.id, s);
        cookie(x, s.id, 1800);
        return s;
    }

    public void logout(HttpExchange x) {
        Session s = session(x);
        if (s != null) sessions.remove(s.id);
        cookie(x, "", 0);
    }

    private static void cookie(HttpExchange x, String id, int maxAge) {
        x.getResponseHeaders().set("Set-Cookie", "EXEMPLO_SESSION=" + id + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" + maxAge);
    }

    public static boolean csrf(Session s, String token) {
        return s != null && token != null && MessageDigest.isEqual(
            s.csrf.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    private static String token() {
        byte[] b = new byte[32]; RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String hash(char[] password) {
        byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
        byte[] key = derive(password, salt, ITERATIONS);
        return "pbkdf2-sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(key);
    }

    public static boolean verify(char[] password, String stored) {
        try {
            String[] parts = stored.split("\\$");
            if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")) return false;
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 100_000 || iterations > 2_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] key = Base64.getDecoder().decode(parts[3]);
            if (salt.length != 16 || key.length != 32) return false;
            return MessageDigest.isEqual(key, derive(password, salt, iterations));
        } catch (IllegalArgumentException e) { return false; }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
        finally { spec.clearPassword(); }
    }

    /** Criação explícita de uma conta, apenas através da linha de comandos. */
    public static void createAdmin(Db db, String email, char[] password) throws SQLException {
        if (email.length() > 100 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalArgumentException("Email inválido.");
        if (password.length < 8 || password.length > 1024)
            throw new IllegalArgumentException("A palavra-passe deve ter entre 8 e 1024 caracteres.");
        String passwordHash = hash(password);
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try {
                int profile;
                try (PreparedStatement p = c.prepareStatement("SELECT id_perfil FROM Perfil WHERE nome=? ORDER BY id_perfil LIMIT 1")) {
                    p.setString(1, "Administrador");
                    try (ResultSet r = p.executeQuery()) { profile = r.next() ? r.getInt(1) : 0; }
                }
                if (profile == 0) try (PreparedStatement p = c.prepareStatement("INSERT INTO Perfil(nome) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                    p.setString(1, "Administrador"); p.executeUpdate();
                    try (ResultSet r = p.getGeneratedKeys()) { r.next(); profile = r.getInt(1); }
                }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO Utilizador(trabalhador_id,perfil_id,email,password_hash) VALUES (NULL,?,?,?)")) {
                    Db.bind(p, profile, email, passwordHash); p.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }
}
