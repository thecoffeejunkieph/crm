package ph.thecoffeejunkie.crm.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class FormatUtils {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    private FormatUtils() {
        /* This utility class should not be instantiated */
    }

    public static String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        return NumberFormat.getCurrencyInstance(Locale.of("en", "PH")).format(amount);
    }

    public static String formatDate(LocalDate date) {
        return date == null ? "-" : date.format(DATE_FORMAT);
    }
}
