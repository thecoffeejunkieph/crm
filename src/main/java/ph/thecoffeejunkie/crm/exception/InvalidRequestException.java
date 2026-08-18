package ph.thecoffeejunkie.crm.exception;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends CrmException {

    public InvalidRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
