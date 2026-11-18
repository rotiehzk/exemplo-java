package exemplo;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Consultas parametrizadas sobre as tabelas existentes. */
public final class ProductDao {
    private final Db db;
    private static final String SELECT = "SELECT p.*, c.nome AS categoria FROM Produtos p JOIN Categorias c ON c.id_categoria=p.categoria_id ";
    public ProductDao(Db db) { this.db = db; }

    public record Product(String nome, int categoria, BigDecimal peso, BigDecimal preco, int stock, String estado) {
        public static Product from(Map<String, ?> m) {
            String nome = required(m, "nome");
            String estado = required(m, "estado_produto");
            if (nome.codePointCount(0, nome.length()) > 100) throw new IllegalArgumentException("Nome: máximo de 100 caracteres.");
            if (!Set.of("Ativo", "Inativo").contains(estado)) throw new IllegalArgumentException("Estado: escolher Ativo ou Inativo.");
            return new Product(nome, positiveInt(m, "categoria_id"), decimal(m, "peso_unitario", true),
                decimal(m, "preco_venda", false), nonnegativeInt(m, "stock_disponivel"), estado);
        }
        Object[] values() { return new Object[]{categoria, nome, peso, preco, stock, estado}; }
    }

    static String required(Map<String, ?> m, String key) {
        Object v = m.get(key);
        if (v == null || v.toString().trim().isEmpty()) throw new IllegalArgumentException("Campo obrigatório: " + key);
        return v.toString().trim();
    }
    static int nonnegativeInt(Map<String, ?> m, String key) {
        try {
            int n = Integer.parseInt(required(m, key));
            if (n < 0) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) { throw new IllegalArgumentException("Valor inteiro inválido: " + key); }
    }
    static int positiveInt(Map<String, ?> m, String key) {
        int n = nonnegativeInt(m, key);
        if (n == 0) throw new IllegalArgumentException("Valor deve ser superior a zero: " + key);
        return n;
    }
    static BigDecimal decimal(Map<String, ?> m, String key, boolean positive) {
        try {
            BigDecimal n = new BigDecimal(required(m, key));
            if (n.scale() > 2 || n.signum() < 0 || (positive && n.signum() == 0) || n.compareTo(new BigDecimal("99999999.99")) > 0)
                throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) { throw new IllegalArgumentException("Decimal inválido (máximo 2 casas): " + key); }
    }

    public List<Map<String, Object>> list(String search) throws SQLException {
        // LOCATE trata o texto como texto literal, incluindo % e _.
        return db.query(SELECT + "WHERE LOCATE(?,p.nome)>0 ORDER BY p.id_produto DESC LIMIT 200", search);
    }
    public Map<String, Object> find(int id) throws SQLException {
        var rows = db.query(SELECT + "WHERE p.id_produto=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public List<Map<String, Object>> categories() throws SQLException {
        return db.query("SELECT id_categoria,nome FROM Categorias ORDER BY nome");
    }
    public int create(Product product) throws SQLException {
        try (Connection c = db.connect(); PreparedStatement p = c.prepareStatement(
            "INSERT INTO Produtos(categoria_id,nome,peso_unitario,preco_venda,stock_disponivel,estado_produto) VALUES (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            Db.bind(p, product.values()); p.executeUpdate();
            try (ResultSet r = p.getGeneratedKeys()) { r.next(); return r.getInt(1); }
        }
    }
    public boolean update(int id, Product product) throws SQLException {
        try (Connection c = db.connect(); PreparedStatement p = c.prepareStatement(
            "UPDATE Produtos SET categoria_id=?,nome=?,peso_unitario=?,preco_venda=?,stock_disponivel=?,estado_produto=? WHERE id_produto=?")) {
            Object[] values = Arrays.copyOf(product.values(), 7); values[6] = id;
            Db.bind(p, values);
            int count = p.executeUpdate();
            // Alguns drivers devolvem zero quando os valores são iguais.
            return count > 0 || find(id) != null;
        }
    }
    public boolean delete(int id) throws SQLException {
        try (Connection c = db.connect(); PreparedStatement p = c.prepareStatement("DELETE FROM Produtos WHERE id_produto=?")) {
            p.setInt(1, id); return p.executeUpdate() > 0;
        }
    }
    public Map<String, Object> dashboard() throws SQLException {
        var result = new LinkedHashMap<String, Object>(db.query("SELECT " +
            "(SELECT COUNT(*) FROM Salinas) AS salinas," +
            "(SELECT COALESCE(SUM(quantidade_produzida),0) FROM Colheitas) AS producao," +
            "(SELECT COUNT(*) FROM Encomendas) AS encomendas").get(0));
        result.put("recentes", db.query("SELECT e.id_encomenda,c.nome AS cliente,e.data_encomenda,e.estado_encomenda,e.total " +
            "FROM Encomendas e JOIN Clientes c ON c.id_cliente=e.cliente_id ORDER BY e.data_encomenda DESC,e.id_encomenda DESC LIMIT 5"));
        return result;
    }
}
