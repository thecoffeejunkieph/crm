package ph.thecoffeejunkie.crm.exception;

import org.springframework.http.HttpStatus;

public class PdfGenerationException extends CrmException {

    public PdfGenerationException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}
