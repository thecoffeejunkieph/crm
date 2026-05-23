package ph.thecoffeejunkie.crm.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuotationResponse(
        String quotationNumber,
        List<QuotationItemResponse> quotationItems,
        CustomerResponse customer,
        String status,
        BigDecimal totalAmount,
        LocalDateTime quoteDate,
        LocalDateTime expiryDate,
        String notes,
        String termsAndConditions
) {}
