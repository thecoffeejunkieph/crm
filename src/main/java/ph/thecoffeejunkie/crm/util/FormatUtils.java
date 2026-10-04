package ph.thecoffeejunkie.crm.util;

import ph.thecoffeejunkie.crm.constant.DiscountType;

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

    /**
     * A discount can be a percentage or a fixed peso amount - this renders whichever it is.
     * Rows saved before the discount-type column existed have a null type, which is treated
     * as FIXED since that was the only behavior available at the time.
     */
    public static String formatDiscount(Integer discount, DiscountType discountType) {
        if (discount == null || discount == 0) {
            return "-";
        }
        if (discountType == DiscountType.PERCENT) {
            return discount + "%";
        }
        return formatCurrency(BigDecimal.valueOf(discount));
    }
}
