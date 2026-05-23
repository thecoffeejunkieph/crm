package ph.thecoffeejunkie.crm.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record QuotationCreateRequest(
        List<QuotationItemRequest> quotationItems,
        Long customerId,
        String status,
        BigDecimal totalAmount,
        LocalDateTime quoteDate,
        LocalDateTime expiryDate,
        BigDecimal shippingCharges,
        String notes,
        String termsAndConditions
) {}
