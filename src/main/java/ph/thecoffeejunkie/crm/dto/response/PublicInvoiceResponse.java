package ph.thecoffeejunkie.crm.dto.response;

import ph.thecoffeejunkie.crm.constant.DiscountType;
import ph.thecoffeejunkie.crm.constant.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What the customer's upload-proof-of-payment page sees, including where to pay. */
public record PublicInvoiceResponse(
        String invoiceNumber,
        String customerName,
        InvoiceStatus status,
        String statusLabel,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String paymentTermsLabel,
        List<PublicLineItem> items,
        BigDecimal shippingCharges,
        Integer discount,
        DiscountType discountType,
        BigDecimal totalAmount,
        BigDecimal balanceDue,
        String notes,
        String termsAndConditions,
        String bankName,
        String bankAccountName,
        String bankAccountNumber,
        String bankSwiftCode
) {}
