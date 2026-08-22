package ph.thecoffeejunkie.crm.dto.response;

import java.math.BigDecimal;

public record SalesStat(BigDecimal value, Double deltaPercent) {}
