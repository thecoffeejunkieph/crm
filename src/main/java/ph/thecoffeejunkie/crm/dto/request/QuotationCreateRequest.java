package ph.thecoffeejunkie.crm.dto.request;

import ph.thecoffeejunkie.crm.constant.DiscountType;
import ph.thecoffeejunkie.crm.constant.PaymentTerms;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record QuotationCreateRequest(
        List<QuotationItemRequest> quotationItems,
        Long customerId,
        String status,
        BigDecimal totalAmount,
        LocalDate quoteDate,
        LocalDate expiryDate,
        BigDecimal shippingCharges,
        Integer discount,
        DiscountType discountType,
        String notes,
        String termsAndConditions,
        PaymentTerms paymentTerms
) {}
