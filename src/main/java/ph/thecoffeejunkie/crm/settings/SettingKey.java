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
    INVOICE_TERMS("invoice.terms", text(5000));

    private final String key;
    private final Validator validator;

    SettingKey(String key, Validator validator) {
        this.key = key;
        this.validator = validator;
    }

    public String key() {
        return key;
    }

    public static Optional<SettingKey> of(String key) {
        return Stream.of(values()).filter(k -> k.key.equals(key)).findFirst();
    }

    /** Returns the normalized value to store (BigDecimal or String); throws VALIDATION_FAILED otherwise. */
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
