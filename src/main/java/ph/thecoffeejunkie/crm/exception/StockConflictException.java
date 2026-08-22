package ph.thecoffeejunkie.crm.exception;

import org.springframework.http.HttpStatus;

public class StockConflictException extends CrmException {

    public StockConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
