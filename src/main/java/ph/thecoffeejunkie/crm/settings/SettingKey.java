package ph.thecoffeejunkie.crm.settings;

import tools.jackson.databind.JsonNode;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public enum SettingKey {
    APPROVAL_DISCOUNT_PERCENT_MAX("approval.discountPercentMax", number(BigDecimal.ZERO, new BigDecimal(100))),
    APPROVAL_WRITE_OFF_QTY_MAX("approval.writeOffQtyMax", number(BigDecimal.ZERO, null)),
    POS_QUICK_ADD_ASSIGNMENT("pos.quickAddAssignment", oneOf("SELF", "UNASSIGNED"));

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

    private static Validator oneOf(String... allowed) {
        Set<String> set = Set.of(allowed);
        return (k, n) -> {
            if (n == null || !n.isString() || !set.contains(n.asString())) {
                throw invalid(k, "must be one of " + String.join(", ", allowed));
            }
            return n.asString();
        };
    }

    private static DomainException invalid(SettingKey k, String why) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, k.key + " " + why, Map.of("field", k.key));
    }
}
