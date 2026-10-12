package ph.thecoffeejunkie.crm.identity;

import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.util.Map;

final class PasswordPolicy {

    static final int MIN_LENGTH = 12;

    private PasswordPolicy() {
    }

    static void check(String email, String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw invalid("Password must be at least " + MIN_LENGTH + " characters.");
        }
        if (password.equalsIgnoreCase(email)) {
            throw invalid("Password must not be your email address.");
        }
    }

    private static DomainException invalid(String detail) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, detail, Map.of("field", "password"));
    }
}
