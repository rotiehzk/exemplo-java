package exemplo;

import java.math.*;
import java.util.*;

/** Testes de validação e segurança que não precisam de uma base de dados. */
public final class SelfTest {
    static int passed;
    static void check(boolean valid, String label) {
        if (!valid) throw new AssertionError(label);
        passed++;
    }
    static void rejects(Runnable action, String label) {
        try { action.run(); } catch (IllegalArgumentException e) { passed++; return; }
        throw new AssertionError(label);
    }
    public static void main(String[] args) {
        String hash = Auth.hash("palavra-passe-segura".toCharArray());
        check(Auth.verify("palavra-passe-segura".toCharArray(), hash), "Palavra-passe correta");
        check(!Auth.verify("outra".toCharArray(), hash), "Palavra-passe incorreta");
        check(!hash.equals(Auth.hash("palavra-passe-segura".toCharArray())), "Salt aleatório");
        check(!Auth.verify("teste".toCharArray(), "texto-sem-hash"), "Rejeitar hash desconhecido");
        var s = new Auth.Session(null);
        check(Auth.csrf(s, s.csrf) && !Auth.csrf(s, "outro") && !Auth.csrf(null, null), "Proteção CSRF");
        check(new Auth.User(1, "a@b.pt", "Administrador").admin(), "Perfil administrador");
        check(!new Auth.User(2, "a@b.pt", "Operador").admin(), "Perfil de leitura");

        String text = "Açúcar \"sal\" \\ e\nlinha";
        var parsed = Json.parse(Json.stringify(Map.of("nome", text, "preco", new BigDecimal("2.50"))));
        check(parsed.get("nome").equals(text), "JSON com caracteres especiais");
        check(parsed.get("preco").equals(new BigDecimal("2.50")), "JSON preserva decimal");
        check(Json.parse("{\"campo\":null}").containsKey("campo"), "JSON null");
        for (String bad : List.of("{\"x\":1,}", "{\"x\":1,\"x\":2}", "{\"x\":01}", "{\"x\":1}x", "{\"x\":1e99999}", "{\"x\":{}}"))
            rejects(() -> Json.parse(bad), "JSON inválido " + bad);
        check(Views.e("<script>\"'&").equals("&lt;script&gt;&quot;&#39;&amp;"), "Escape HTML");
        Map<String, Object> p = new HashMap<>(Map.of("nome", "Sal", "categoria_id", "1", "peso_unitario", "0.50",
            "preco_venda", "2.50", "stock_disponivel", "10", "estado_produto", "Ativo"));
        check(ProductDao.Product.from(p).preco().equals(new BigDecimal("2.50")), "Produto válido");
        for (String[] invalid : new String[][]{{"nome", " "}, {"nome", "a".repeat(101)}, {"categoria_id", "0"},
            {"peso_unitario", "0"}, {"preco_venda", "-1"}, {"preco_venda", "2.555"},
            {"preco_venda", "100000000"}, {"stock_disponivel", "1.5"}, {"stock_disponivel", "-1"}, {"estado_produto", "<script>"}}) {
            var copy = new HashMap<>(p); copy.put(invalid[0], invalid[1]);
            rejects(() -> ProductDao.Product.from(copy), "Produto inválido: " + Arrays.toString(invalid));
        }
        check(Templates.expand("{{nome}}", Map.of("nome", "<x>{{segredo}}", "segredo", "NÃO"))
            .equals("&lt;x&gt;{{segredo}}"), "Dados escapados não são reinterpretados como templates");
        check(Templates.expand("{{#rows}}{{nome}}{{#admin}}!{{/admin}}{{/rows}}",
            Map.of("admin", true, "rows", List.of(Map.of("nome", "A"), Map.of("nome", "B"))))
            .equals("A!B!"), "Listas, condições aninhadas e contexto herdado");
        check(Templates.expand("{{#admin}}privado{{/admin}}", Map.of("admin", false)).isEmpty(), "Condição falsa");
        check(Templates.expand("{{#rows}}linha{{/rows}}", Map.of("rows", List.of())).isEmpty(), "Lista vazia");
        rejects(() -> Templates.expand("{{#admin}}x{{/outro}}", Map.of("admin", true)), "Fecho incorreto de template");
        check(Views.login(s, "<erro>").contains("&lt;erro&gt;"), "Login HTML externo com erro escapado");
        var readonly = new Auth.Session(new Auth.User(2, "a@b.pt", "Operador"));
        String list = Views.products(List.of(), "", "", readonly);
        check(list.contains("colspan='6'") && !list.contains("Novo produto") && !list.contains("{{"), "Tabela vazia sem ações de administrador");
        var admin = new Auth.Session(new Auth.User(1, "a@b.pt", "Administrador"));
        String form = Views.productForm(Map.of(), List.of(), "", admin);
        check(form.contains(" disabled") && !form.contains("{{"), "Formulário sem categorias");
        check(Views.dashboard(Map.of("salinas", 0, "producao", 0, "encomendas", 0, "recentes", List.of()), admin)
            .contains("Ainda não existem encomendas."), "Dashboard vazio em template externo");
        System.out.println(passed + " verificações passaram.");
    }
}
