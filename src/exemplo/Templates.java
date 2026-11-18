package exemplo;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** HTML externo: valores escapados, condições e listas, sem dependências. */
public final class Templates {
    public static String render(String file, Map<String, ?> data) {
        if (!file.matches("[a-z-]+\\.html")) throw new IllegalArgumentException("Nome de template inválido.");
        try { return expand(Files.readString(Path.of("templates", file)), data); }
        catch (IOException e) { throw new UncheckedIOException("Não foi possível ler templates/" + file, e); }
    }

    static String expand(String source, Map<String, ?> data) {
        StringBuilder out = new StringBuilder();
        int pos = 0;
        while (pos < source.length()) {
            int start = source.indexOf("{{", pos);
            if (start < 0) { out.append(source.substring(pos)); break; }
            out.append(source, pos, start);
            boolean raw = source.startsWith("{{{", start);
            String endToken = raw ? "}}}" : "}}";
            int end = source.indexOf(endToken, start + (raw ? 3 : 2));
            if (end < 0) throw new IllegalArgumentException("Tag de template incompleta.");
            String tag = source.substring(start + (raw ? 3 : 2), end).trim();
            pos = end + endToken.length();
            if (!raw && tag.startsWith("#")) {
                String key = tag.substring(1);
                int[] close = closing(source, pos, key);
                String body = source.substring(pos, close[0]);
                Object value = data.get(key);
                if (value instanceof Iterable<?> items) {
                    for (Object item : items) {
                        if (!(item instanceof Map<?, ?> row)) throw new IllegalArgumentException("A lista deve conter mapas.");
                        Map<String, Object> context = new HashMap<>(data);
                        row.forEach((k, v) -> context.put(k.toString(), v));
                        out.append(expand(body, context));
                    }
                } else if (value != null && !Boolean.FALSE.equals(value) && !(value instanceof String text && text.isEmpty())) {
                    out.append(expand(body, data));
                }
                pos = close[1];
            } else {
                if (!tag.matches("[a-z_]+")) throw new IllegalArgumentException("Tag inválida: " + tag);
                Object value = data.get(tag);
                out.append(raw ? Objects.toString(value, "") : Views.e(value));
            }
        }
        return out.toString();
    }

    private static int[] closing(String source, int from, String name) {
        Deque<String> stack = new ArrayDeque<>(); stack.push(name);
        int pos = from;
        while (true) {
            int start = source.indexOf("{{", pos);
            if (start < 0) throw new IllegalArgumentException("Secção sem fecho: " + name);
            boolean raw = source.startsWith("{{{", start);
            int end = source.indexOf(raw ? "}}}" : "}}", start + (raw ? 3 : 2));
            if (end < 0) throw new IllegalArgumentException("Tag incompleta.");
            String tag = source.substring(start + (raw ? 3 : 2), end).trim();
            pos = end + (raw ? 3 : 2);
            if (!raw && tag.startsWith("#")) stack.push(tag.substring(1));
            if (!raw && tag.startsWith("/")) {
                if (!tag.substring(1).equals(stack.pop())) throw new IllegalArgumentException("Fecho de secção incorreto.");
                if (stack.isEmpty()) return new int[]{start, pos};
            }
        }
    }
}
