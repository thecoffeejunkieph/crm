package ph.thecoffeejunkie.crm.dto.response;

import java.math.BigDecimal;

public record TopSalesRepResponse(
        String email,
        String firstName,
        String lastName,
        BigDecimal totalSales,
        long dealsWon
) {}
