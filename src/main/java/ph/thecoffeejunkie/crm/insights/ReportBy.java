package ph.thecoffeejunkie.crm.insights;

import java.util.Locale;

/** What a sales report groups by. */
enum ReportBy {
    MONTH, REP, CUSTOMER, PRODUCT;

    static ReportBy parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return MONTH;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ReportRange.bad();
        }
    }
}
