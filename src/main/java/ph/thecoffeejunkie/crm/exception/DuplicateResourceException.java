package ph.thecoffeejunkie.crm.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends CrmException {

    public DuplicateResourceException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
