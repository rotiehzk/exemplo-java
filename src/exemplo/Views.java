package exemplo;

import java.math.*;
import java.util.*;

/** Prepara os dados; o HTML está nos ficheiros da pasta templates/. */
public final class Views {
    public static String e(Object value) {
        if (value == null) return "";
        return value.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }
    private static String money(Object value) {
        return String.format(Locale.forLanguageTag("pt-PT"), "%.2f €", new BigDecimal(value.toString()));
    }
    private static String page(String title, String file, Map<String, ?> data, Auth.Session s, String current) {
        String header = "";
        if (s != null && s.user != null) header = Templates.render("header.html", Map.of(
            "csrf", s.csrf, "email", s.user.email(), "dashboard_atual", current.equals("/dashboard"), "produtos_atual", current.equals("/produtos")));
        return Templates.render("layout.html", Map.of("title", title, "header", header, "body", Templates.render(file, data)));
    }
    public static String login(Auth.Session s, String error) {
        return page("Entrar", "login.html", Map.of("csrf", s.csrf, "error", error), null, "");
    }
    @SuppressWarnings("unchecked")
    public static String dashboard(Map<String, Object> data, Auth.Session s) {
        Map<String, Object> context = new HashMap<>(data);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var r : (List<Map<String, Object>>)data.get("recentes")) {
            var row = new HashMap<>(r); row.put("total_formatado", money(r.get("total"))); rows.add(row);
        }
        context.put("recentes", rows); context.put("sem_encomendas", rows.isEmpty());
        return page("Dashboard", "dashboard.html", context, s, "/dashboard");
    }
    public static String products(List<Map<String, Object>> rows, String search, String notice, Auth.Session s) {
        List<Map<String, Object>> formatted = new ArrayList<>();
        for (var r : rows) {
            var row = new HashMap<>(r); row.put("preco_formatado", money(r.get("preco_venda"))); formatted.add(row);
        }
        return page("Produtos", "produtos.html", Map.of("produtos", formatted, "search", search, "message", notice,
            "admin", s.user.admin(), "sem_produtos", rows.isEmpty(), "colunas", s.user.admin() ? 7 : 6), s, "/produtos");
    }
    public static String productForm(Map<String, ?> values, List<Map<String, Object>> categories, String error, Auth.Session s) {
        boolean edit = values.get("id_produto") != null;
        String title = edit ? "Editar produto" : "Novo produto";
        Map<String, Object> context = new HashMap<>(values);
        List<Map<String, Object>> options = new ArrayList<>();
        for (var c : categories) {
            var option = new HashMap<>(c);
            option.put("selected", Objects.toString(c.get("id_categoria"), "").equals(Objects.toString(values.get("categoria_id"), "")));
            options.add(option);
        }
        context.put("title", title); context.put("csrf", s.csrf); context.put("error", error); context.put("editar", edit);
        context.put("categorias", options); context.put("sem_categorias", categories.isEmpty());
        context.put("inativo", "Inativo".equals(values.get("estado_produto")));
        context.put("ativo", !"Inativo".equals(values.get("estado_produto")));
        return page(title, "produto-form.html", context, s, "/produtos");
    }
    public static String delete(Map<String, Object> product, Auth.Session s) {
        var context = new HashMap<>(product); context.put("csrf", s.csrf);
        return page("Eliminar produto", "eliminar-produto.html", context, s, "/produtos");
    }
    public static String error(int code, String message, Auth.Session s) {
        return page("Erro " + code, "erro.html", Map.of("code", code, "message", message,
            "voltar", s == null || s.user == null ? "/login" : "/produtos"), s, "");
    }
}
