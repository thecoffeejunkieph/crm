package ph.thecoffeejunkie.crm.shared;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    STALE_VERSION(HttpStatus.CONFLICT),
    APPROVAL_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED),
    TOTP_REQUIRED(HttpStatus.UNAUTHORIZED),
    TOTP_INVALID(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.GONE),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    DUPLICATE(HttpStatus.CONFLICT),
    LAST_ADMIN(HttpStatus.CONFLICT),
    POS_NOT_CONFIGURED(HttpStatus.CONFLICT),
    INVALID_STATE(HttpStatus.UNPROCESSABLE_CONTENT),
    QUOTE_EXPIRED(HttpStatus.UNPROCESSABLE_CONTENT),
    HAS_PAYMENTS(HttpStatus.UNPROCESSABLE_CONTENT),
    OVERPAYMENT(HttpStatus.UNPROCESSABLE_CONTENT),
    INSUFFICIENT_STOCK(HttpStatus.UNPROCESSABLE_CONTENT),
    ORDER_SHORT(HttpStatus.UNPROCESSABLE_CONTENT),
    PICK_INCOMPLETE(HttpStatus.UNPROCESSABLE_CONTENT),
    PROOF_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
