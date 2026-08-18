package ph.thecoffeejunkie.crm.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends CrmException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }

    public static ResourceNotFoundException of(String resourceName, Object id) {
        return new ResourceNotFoundException(resourceName + " not found with id: " + id);
    }
}
