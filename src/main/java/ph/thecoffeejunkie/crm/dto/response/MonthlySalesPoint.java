package ph.thecoffeejunkie.crm.dto.response;

import java.io.Serializable;

import java.math.BigDecimal;

/** month is an ISO "yyyy-MM" bucket of accepted quotations by quote date. */
public record MonthlySalesPoint(String month, BigDecimal total) implements Serializable {}
