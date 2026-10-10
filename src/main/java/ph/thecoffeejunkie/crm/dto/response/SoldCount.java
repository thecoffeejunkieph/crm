package ph.thecoffeejunkie.crm.dto.response;

import java.io.Serializable;
import java.math.BigDecimal;

/** Quantity and sales of one product or category, from PAID invoices. */
public record SoldCount(String name, long quantitySold, BigDecimal totalSales) implements Serializable {}
