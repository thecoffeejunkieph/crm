package ph.thecoffeejunkie.crm.exception;

import org.springframework.http.HttpStatus;

public abstract class CrmException extends RuntimeException {

    private final HttpStatus status;

    protected CrmException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    protected CrmException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
