package ph.thecoffeejunkie.crm.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import ph.thecoffeejunkie.crm.constant.DiscountType;
import ph.thecoffeejunkie.crm.constant.PaymentTerms;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuotationResponse(
        Long id,
        String quotationNumber,
        List<QuotationItemResponse> quotationItems,
        CustomerResponse customer,
        String status,
        BigDecimal totalAmount,
        BigDecimal shippingCharges,
        Integer discount,
        DiscountType discountType,
        LocalDate quoteDate,
        LocalDate expiryDate,
        String notes,
        String termsAndConditions,
        SalesRepResponse salesRep,
        PaymentTerms paymentTerms,
        String paymentTermsLabel
) {}
