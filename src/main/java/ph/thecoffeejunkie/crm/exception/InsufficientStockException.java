package ph.thecoffeejunkie.crm.exception;

import org.springframework.http.HttpStatus;

public class InsufficientStockException extends CrmException {

    public InsufficientStockException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
