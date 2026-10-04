package ph.thecoffeejunkie.crm.dto.request;

import ph.thecoffeejunkie.crm.constant.DiscountType;
import ph.thecoffeejunkie.crm.constant.PaymentTerms;

import java.math.BigDecimal;
import java.util.List;

public record InvoiceCreateRequest(
        List<InvoiceItemRequest> invoiceItems,
        Long customerId,
        BigDecimal totalAmount,
        BigDecimal shippingCharges,
        Integer discount,
        DiscountType discountType,
        String notes,
        String termsAndConditions,
        PaymentTerms paymentTerms
) {}
