package ph.thecoffeejunkie.crm.inventory;

import java.math.BigDecimal;

public record LineStock(long orderLineId, BigDecimal reservedQty, BigDecimal shortQty) {}
