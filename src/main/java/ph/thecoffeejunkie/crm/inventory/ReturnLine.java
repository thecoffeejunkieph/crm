package ph.thecoffeejunkie.crm.inventory;

import java.math.BigDecimal;

/** Goods coming back on a refund; {@code damaged} ones are written off again at once. */
public record ReturnLine(long productId, long warehouseId, BigDecimal qty, boolean damaged) {}
