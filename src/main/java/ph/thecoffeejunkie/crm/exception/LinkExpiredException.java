package ph.thecoffeejunkie.crm.exception;

import org.springframework.http.HttpStatus;

/** A customer-facing emailed link whose token is invalid or past its validity. */
public class LinkExpiredException extends CrmException {

    public LinkExpiredException() {
        super("This link is invalid or has expired. Please contact us for a new link.", HttpStatus.GONE);
    }
}
