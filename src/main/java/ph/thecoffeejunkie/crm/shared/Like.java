package ph.thecoffeejunkie.crm.shared;

import java.util.Locale;

/** A case-insensitive "contains" pattern, escaped for {@code cb.like(cb.lower(x), Like.contains(q), '\\')}. */
public final class Like {

    private Like() {
    }

    public static String contains(String q) {
        return "%" + q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }

    public static boolean blank(String q) {
        return q == null || q.isBlank();
    }
}
