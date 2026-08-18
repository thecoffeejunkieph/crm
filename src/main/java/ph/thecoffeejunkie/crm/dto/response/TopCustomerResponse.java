package ph.thecoffeejunkie.crm.dto.response;

import java.math.BigDecimal;

public record TopCustomerResponse(
        Long customerId,
        String firstName,
        String lastName,
        BigDecimal totalSpent,
        long quotationCount
) {}
