package ph.thecoffeejunkie.crm.settings;

import tools.jackson.databind.JsonNode;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public enum SettingKey {
    APPROVAL_DISCOUNT_PERCENT_MAX("approval.discountPercentMax", number(BigDecimal.ZERO, new BigDecimal(100))),
    APPROVAL_WRITE_OFF_VALUE_MAX("approval.writeOffValueMax", number(BigDecimal.ZERO, null)),
    SALES_QUOTE_VALIDITY_DAYS("sales.quoteValidityDays", number(BigDecimal.ONE, new BigDecimal(365))),
    SALES_WITHHOLDING_SUGGEST_PERCENT("sales.withholdingSuggestPercent", number(BigDecimal.ZERO, new BigDecimal(100))),
    COMPANY_NAME("company.name", text(200)),
    COMPANY_TIN("company.tin", text(32)),
    COMPANY_ADDRESS("company.address", text(500)),
    INVOICE_FOOTER_TEXT("invoice.footerText", text(1000)),
    INVOICE_BANK_ACCOUNTS("invoice.bankAccounts", bankAccounts(10, 400_000)),
    INVOICE_TERMS("invoice.terms", text(5000)),
    FOLLOW_UPS_AUTO_RESTOCK_ENABLED("followUps.auto.RESTOCK.enabled", bool(), true),
    FOLLOW_UPS_AUTO_WIN_BACK_ENABLED("followUps.auto.WIN_BACK.enabled", bool(), true),
    FOLLOW_UPS_AUTO_QUOTE_CHASE_ENABLED("followUps.auto.QUOTE_CHASE.enabled", bool(), true),
    FOLLOW_UPS_AUTO_QUOTE_EXPIRING_ENABLED("followUps.auto.QUOTE_EXPIRING.enabled", bool(), true),
    FOLLOW_UPS_AUTO_QUOTE_DECLINED_ENABLED("followUps.auto.QUOTE_DECLINED.enabled", bool(), true),
    FOLLOW_UPS_AUTO_FIRST_ORDER_ENABLED("followUps.auto.FIRST_ORDER.enabled", bool(), true),
    FOLLOW_UPS_AUTO_PAYMENT_OVERDUE_ENABLED("followUps.auto.PAYMENT_OVERDUE.enabled", bool(), true),
    FOLLOW_UPS_AUTO_DEAL_STALLED_ENABLED("followUps.auto.DEAL_STALLED.enabled", bool(), true),
    FOLLOW_UPS_RESTOCK_FALLBACK_DAYS("followUps.restock.fallbackDays", whole(1, 365), 15),
    FOLLOW_UPS_RESTOCK_RHYTHM_PERCENT("followUps.restock.rhythmPercent", whole(10, 100), 80),
    FOLLOW_UPS_RESTOCK_MIN_DAYS("followUps.restock.minDays", whole(1, 60), 3),
    FOLLOW_UPS_WIN_BACK_DAYS("followUps.winBack.days", whole(7, 365), 60),
    FOLLOW_UPS_QUOTE_CHASE_DAYS("followUps.quoteChase.days", whole(1, 60), 3),
    FOLLOW_UPS_QUOTE_EXPIRING_DAYS_BEFORE("followUps.quoteExpiring.daysBefore", whole(0, 30), 2),
    FOLLOW_UPS_FIRST_ORDER_DAYS("followUps.firstOrder.days", whole(0, 60), 3),
    FOLLOW_UPS_DEAL_STALLED_DAYS("followUps.dealStalled.days", whole(1, 180), 14),
    /** Must be an active ADMIN, but that is checked when the scan uses it, not at save. */
    FOLLOW_UPS_FALLBACK_ADMIN_ID("followUps.fallbackAdminId", positiveIdOrNull(), null);

    private final String key;
    private final Validator validator;
    private final boolean hasDefault;
    private final Object defaultValue;

    /** Seeded by a migration: a missing row is a bug. */
    SettingKey(String key, Validator validator) {
        this.key = key;
        this.validator = validator;
        this.hasDefault = false;
        this.defaultValue = null;
    }

    /** Needs no row: reads fall back to the default (null is a valid default). */
    SettingKey(String key, Validator validator, Object defaultValue) {
        this.key = key;
        this.validator = validator;
        this.hasDefault = true;
        this.defaultValue = defaultValue;
    }

    boolean hasDefault() {
        return hasDefault;
    }

    Object defaultValue() {
        return defaultValue;
    }

    public String key() {
        return key;
    }

    public static Optional<SettingKey> of(String key) {
        return Stream.of(values()).filter(k -> k.key.equals(key)).findFirst();
    }

    /** Returns the normalized value to store (BigDecimal, String, Boolean, Long or null); throws VALIDATION_FAILED otherwise. */
    Object validate(JsonNode node) {
        return validator.apply(this, node);
    }

    private interface Validator {
        Object apply(SettingKey key, JsonNode node);
    }

    private static Validator number(BigDecimal min, BigDecimal max) {
        return (k, n) -> {
            if (n == null || !n.isNumber()) {
                throw invalid(k, "must be a number");
            }
            BigDecimal v = n.decimalValue();
            if (v.compareTo(min) < 0 || (max != null && v.compareTo(max) > 0)) {
                throw invalid(k, "must be between " + min + " and " + (max == null ? "infinity" : max));
            }
            return v;
        };
    }

    private static Validator whole(int min, int max) {
        Validator range = number(new BigDecimal(min), new BigDecimal(max));
        return (k, n) -> {
            BigDecimal v = (BigDecimal) range.apply(k, n);
            if (v.stripTrailingZeros().scale() > 0) {
                throw invalid(k, "must be a whole number");
            }
            return v;
        };
    }

    private static Validator bool() {
        return (k, n) -> {
            if (n == null || !n.isBoolean()) {
                throw invalid(k, "must be true or false");
            }
            return n.booleanValue();
        };
    }

    /** A user id, or null for "none". */
    private static Validator positiveIdOrNull() {
        return (k, n) -> {
            if (n == null || n.isNull()) {
                return null;
            }
            if (!n.isIntegralNumber() || n.longValue() < 1) {
                throw invalid(k, "must be a positive whole number or null");
            }
            return n.longValue();
        };
    }

    private static Validator text(int maxLength) {
        return (k, n) -> {
            if (n == null || !n.isString()) {
                throw invalid(k, "must be text");
            }
            String v = n.asString().trim();
            if (v.length() > maxLength) {
                throw invalid(k, "must be at most " + maxLength + " characters");
            }
            return v;
        };
    }

    /**
     * QR images travel as data URIs: PdfRenderer blocks the network, and a frozen invoice must keep its QR forever.
     * ponytail: images live in the setting and each invoice snapshot (a few KB each once the browser shrinks them);
     * move to object storage if invoice rows ever get heavy.
     */
    private static Validator bankAccounts(int maxAccounts, int maxQrChars) {
        Pattern image = Pattern.compile("data:image/(png|jpeg);base64,[A-Za-z0-9+/]+={0,2}");
        return (k, n) -> {
            if (n == null || !n.isArray() || n.size() > maxAccounts) {
                throw invalid(k, "must be a list of at most " + maxAccounts + " accounts");
            }
            List<Map<String, String>> out = new ArrayList<>();
            for (JsonNode a : n) {
                if (!a.isObject()) {
                    throw invalid(k, "must be a list of accounts");
                }
                Map<String, String> account = new LinkedHashMap<>();
                account.put("bankName", field(k, a, "bankName", 200));
                account.put("accountName", field(k, a, "accountName", 150));
                account.put("accountNumber", field(k, a, "accountNumber", 60));
                account.put("qr", field(k, a, "qr", maxQrChars));
                if (account.get("bankName").isEmpty() || account.get("accountNumber").isEmpty()) {
                    throw invalid(k, "each account needs a bank and an account number");
                }
                if (!account.get("qr").isEmpty() && !image.matcher(account.get("qr")).matches()) {
                    throw invalid(k, "QR code must be a PNG or JPEG image");
                }
                out.add(account);
            }
            return out;
        };
    }

    private static String field(SettingKey k, JsonNode account, String name, int maxLength) {
        JsonNode v = account.get(name);
        if (v == null || v.isNull()) {
            return "";
        }
        if (!v.isString() || v.asString().trim().length() > maxLength) {
            throw invalid(k, name + " must be text of at most " + maxLength + " characters");
        }
        return v.asString().trim();
    }

    private static DomainException invalid(SettingKey k, String why) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, k.key + " " + why, Map.of("field", k.key));
    }
}
