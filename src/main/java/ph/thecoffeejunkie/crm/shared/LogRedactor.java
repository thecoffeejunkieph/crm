package ph.thecoffeejunkie.crm.shared;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Renders method arguments for logs with secrets and personal data masked by name (spec 2026-11-05 logging).
 * Never throws: a value it cannot render becomes {@code <unrenderable>}.
 */
public final class LogRedactor {

    private static final List<String> SECRET = List.of("password", "passphrase", "secret", "token", "code", "otp",
            "cookie", "authorization", "link", "key", "session", "base32", "plaintext", "credential", "hash");
    private static final int MAX_STRING = 200;
    private static final int MAX_ITEMS = 5;
    private static final int MAX_DEPTH = 3;

    private LogRedactor() {
    }

    public static String render(Object value) {
        return safe(() -> value(null, value, 0, new IdentityHashMap<>()));
    }

    /** {@code names} may be shorter than {@code args} (or null) when parameter names are unavailable. */
    public static String renderArgs(String[] names, Object[] args) {
        return safe(() -> {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < args.length; i++) {
                String name = names != null && i < names.length ? names[i] : "arg" + i;
                parts.add(name + "=" + value(name, args[i], 0, new IdentityHashMap<>()));
            }
            return String.join(", ", parts);
        });
    }

    private interface Render {
        String get();
    }

    private static String safe(Render r) {
        try {
            return r.get();
        } catch (RuntimeException | StackOverflowError e) {
            return "<unrenderable>";
        }
    }

    static boolean secret(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return !n.equals("idempotencykey") && SECRET.stream().anyMatch(n::contains);
    }

    private static String value(String name, Object v, int depth, Map<Object, Boolean> seen) {
        if (v == null) {
            return "null";
        }
        if (secret(name)) {
            return "***";
        }
        if (name != null && name.toLowerCase(Locale.ROOT).equals("tin")) {
            String s = v.toString().replaceAll("\\D", "");
            return "***" + (s.length() >= 3 ? s.substring(s.length() - 3) : "");
        }
        if (v instanceof CharSequence cs) {
            String s = cs.toString();
            if (s.matches("[^@\\s]+@[^@\\s]+")) {
                return s.charAt(0) + "***" + s.substring(s.indexOf('@'));
            }
            return s.length() > MAX_STRING ? s.substring(0, MAX_STRING) + "…" : s;
        }
        if (v instanceof byte[] b) {
            return "<" + b.length + " bytes>";
        }
        if (v instanceof MultipartFile f) {
            return "<" + f.getSize() + " bytes>";
        }
        if (v instanceof InputStream) {
            return "<stream>";
        }
        if (v instanceof Number || v instanceof Boolean || v instanceof Enum<?> || v instanceof Temporal
                || v instanceof Character || v instanceof java.util.UUID) {
            return v.toString();
        }
        if (seen.containsKey(v)) {
            return "<cycle>";
        }
        if (depth >= MAX_DEPTH) {
            return v.getClass().getSimpleName();
        }
        seen.put(v, true);
        try {
            if (v instanceof Collection<?> c) {
                List<String> items = new ArrayList<>();
                int i = 0;
                for (Object o : c) {
                    if (i++ == MAX_ITEMS) {
                        break;
                    }
                    items.add(value(null, o, depth + 1, seen));
                }
                String more = c.size() > MAX_ITEMS ? " (+" + (c.size() - MAX_ITEMS) + " more)" : "";
                return "[" + String.join(", ", items) + more + "]";
            }
            if (v instanceof Map<?, ?> m) {
                List<String> items = new ArrayList<>();
                int i = 0;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (i++ == MAX_ITEMS) {
                        break;
                    }
                    String k = String.valueOf(e.getKey());
                    items.add(k + "=" + value(k, e.getValue(), depth + 1, seen));
                }
                return "{" + String.join(", ", items) + (m.size() > MAX_ITEMS ? " (+" + (m.size() - MAX_ITEMS) + " more)" : "") + "}";
            }
            return fields(v, depth, seen);
        } finally {
            seen.remove(v);
        }
    }

    private static final Set<String> SKIP_GETTERS = Set.of("getClass");

    private static String fields(Object v, int depth, Map<Object, Boolean> seen) {
        List<String> parts = new ArrayList<>();
        Class<?> type = v.getClass();
        // Only our own types are opened up: a framework object (request, session, binding result) can hold
        // session ids and create sessions from its getters.
        if (!type.getName().startsWith("ph.thecoffeejunkie.crm.")) {
            return type.getSimpleName();
        }
        if (type.isRecord()) {
            for (RecordComponent rc : type.getRecordComponents()) {
                parts.add(rc.getName() + "=" + read(rc.getName(), rc.getAccessor(), v, depth, seen));
            }
        } else {
            for (Method m : type.getMethods()) {
                String n = m.getName();
                if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers()) || SKIP_GETTERS.contains(n)
                        || !n.startsWith("get") || n.length() == 3 || m.getDeclaringClass() == Object.class) {
                    continue;
                }
                String field = Character.toLowerCase(n.charAt(3)) + n.substring(4);
                parts.add(field + "=" + read(field, m, v, depth, seen));
            }
            if (parts.isEmpty()) {
                return type.getSimpleName();
            }
        }
        return type.getSimpleName() + "{" + String.join(", ", parts) + "}";
    }

    private static String read(String name, Method accessor, Object target, int depth, Map<Object, Boolean> seen) {
        if (secret(name)) {
            return "***";
        }
        try {
            accessor.setAccessible(true);
            return value(name, accessor.invoke(target), depth + 1, seen);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "?";
        }
    }
}
