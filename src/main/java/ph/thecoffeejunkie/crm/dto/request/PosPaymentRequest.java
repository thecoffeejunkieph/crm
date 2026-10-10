package ph.thecoffeejunkie.crm.dto.request;

import ph.thecoffeejunkie.crm.constant.PaymentMethod;

import java.math.BigDecimal;

// amountTendered is cash only: what the customer handed over, so change can be given.
public record PosPaymentRequest(
        PaymentMethod method,
        BigDecimal amount,
        BigDecimal amountTendered
) {}
