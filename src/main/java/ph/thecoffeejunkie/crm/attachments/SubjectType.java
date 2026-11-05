package ph.thecoffeejunkie.crm.attachments;

import java.util.Locale;

/** What a file is attached to; also the audit entity type of that record. */
public enum SubjectType {
    CUSTOMER, ORDER, INVOICE;

    String keySegment() {
        return name().toLowerCase(Locale.ROOT);
    }
}
