package ph.thecoffeejunkie.crm.exception;

import org.springframework.http.HttpStatus;

public class FileStorageException extends CrmException {

    public FileStorageException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}
