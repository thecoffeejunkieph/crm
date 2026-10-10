package ph.thecoffeejunkie.crm.dto.response;

import ph.thecoffeejunkie.crm.constant.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the customer's accept-quotation page sees. Deliberately leaves out the customer's email,
 * since the page asks the customer to type it as proof they're the recipient.
 */
public record PublicQuotationResponse(
        String quotationNumber,
        String customerName,
        String status,
        boolean expired,
        LocalDate quoteDate,
        LocalDate expiryDate,
        String paymentTermsLabel,
        List<PublicLineItem> items,
        BigDecimal shippingCharges,
        Integer discount,
        DiscountType discountType,
        BigDecimal totalAmount,
        String notes,
        String termsAndConditions
) {}
