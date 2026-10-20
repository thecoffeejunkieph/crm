package ph.thecoffeejunkie.crm.inventory;

import java.math.BigDecimal;

public record ReserveLine(long orderLineId, long productId, BigDecimal qty) {}
