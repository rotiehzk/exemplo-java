package exemplo;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Configuração externa; não altera a estrutura da base de dados. */
public final class Db {
    private final Properties config = new Properties();

    public Db(Path file) throws IOException {
        if (Files.exists(file)) try (Reader reader = Files.newBufferedReader(file)) {
            config.load(reader);
        }
    }

    public String setting(String key, String fallback) {
        String env = System.getenv("EXEMPLO_" + key.toUpperCase(Locale.ROOT).replace('.', '_'));
        return env != null ? env : config.getProperty(key, fallback);
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(
            setting("db.url", "jdbc:mysql://localhost:3306/Exemplo?connectionTimeZone=UTC&connectTimeout=5000&socketTimeout=10000"),
            setting("db.user", "root"), setting("db.password", ""));
    }

    public List<Map<String, Object>> query(String sql, Object... args) throws SQLException {
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement(sql)) {
            bind(p, args);
            try (ResultSet r = p.executeQuery()) { return rows(r); }
        }
    }

    public static void bind(PreparedStatement p, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
    }

    public static List<Map<String, Object>> rows(ResultSet r) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        ResultSetMetaData meta = r.getMetaData();
        while (r.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) row.put(meta.getColumnLabel(i), r.getObject(i));
            result.add(row);
        }
        return result;
    }
}
