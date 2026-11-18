package exemplo;

import java.math.BigDecimal;
import java.util.*;

/** JSON sem bibliotecas externas. Entrada limitada a objetos planos para esta API. */
public final class Json {
    public static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            map.forEach((k,v) -> parts.add(quote(k.toString()) + ":" + stringify(v)));
            return "{" + String.join(",", parts) + "}";
        }
        if (value instanceof Iterable<?> values) {
            List<String> parts = new ArrayList<>();
            values.forEach(v -> parts.add(stringify(v)));
            return "[" + String.join(",", parts) + "]";
        }
        return quote(value.toString());
    }
    private static String quote(String text) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> { if (c < 32) b.append(String.format("\\u%04x", (int)c)); else b.append(c); }
            }
        }
        return b.append('"').toString();
    }
    public static Map<String, Object> parse(String text) { return new Parser(text).object(); }
    private static final class Parser {
        final String s; int i;
        Parser(String s) { this.s = s; }
        IllegalArgumentException error() { return new IllegalArgumentException("JSON inválido. Usar um objeto com campos simples."); }
        void ws() { while (i < s.length() && " \r\n\t".indexOf(s.charAt(i)) >= 0) i++; }
        boolean take(char c) { ws(); if (i < s.length() && s.charAt(i) == c) { i++; return true; } return false; }
        void expect(char c) { if (!take(c)) throw error(); }
        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>(); expect('{');
            if (!take('}')) {
                do { ws(); String key = string(); expect(':'); Object v = value();
                    if (m.containsKey(key)) throw error(); m.put(key, v);
                } while (take(','));
                expect('}');
            }
            ws(); if (i != s.length()) throw error(); return m;
        }
        Object value() {
            ws(); if (i >= s.length()) throw error();
            if (s.charAt(i) == '"') return string();
            for (String literal : List.of("null", "true", "false")) if (s.startsWith(literal, i)) {
                i += literal.length(); return literal.equals("null") ? null : Boolean.valueOf(literal);
            }
            int start = i;
            while (i < s.length() && "-+0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String num = s.substring(start, i);
            if (!num.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw error();
            try {
                BigDecimal n = new BigDecimal(num);
                if (Math.abs((long)n.scale()) > 100 || n.precision() > 100) throw error();
                return n;
            } catch (NumberFormatException e) { throw error(); }
        }
        String string() {
            expect('"'); StringBuilder b = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c < 32) throw error();
                if (c != '\\') { b.append(c); continue; }
                if (i >= s.length()) throw error();
                char escape = s.charAt(i++);
                switch (escape) {
                    case '"', '\\', '/' -> b.append(escape);
                    case 'b' -> b.append('\b'); case 'f' -> b.append('\f');
                    case 'n' -> b.append('\n'); case 'r' -> b.append('\r'); case 't' -> b.append('\t');
                    case 'u' -> {
                        if (i + 4 > s.length()) throw error();
                        try { b.append((char)Integer.parseInt(s.substring(i, i + 4), 16)); }
                        catch (NumberFormatException e) { throw error(); }
                        i += 4;
                    }
                    default -> throw error();
                }
            }
            throw error();
        }
    }
}
