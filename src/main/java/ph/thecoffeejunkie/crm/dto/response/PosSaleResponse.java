package ph.thecoffeejunkie.crm.dto.response;

import java.math.BigDecimal;

public record PosSaleResponse(
        InvoiceResponse invoice,
        BigDecimal change,
        EmailStatus emailStatus
) {
    public enum EmailStatus { NOT_REQUESTED, SENT, FAILED }

    public PosSaleResponse withEmailStatus(EmailStatus status) {
        return new PosSaleResponse(invoice, change, status);
    }
}
