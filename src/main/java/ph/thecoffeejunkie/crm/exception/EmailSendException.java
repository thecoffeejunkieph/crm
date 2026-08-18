package ph.thecoffeejunkie.crm.exception;

import org.springframework.http.HttpStatus;

public class EmailSendException extends CrmException {

    public EmailSendException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}
