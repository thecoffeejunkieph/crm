package ph.thecoffeejunkie.crm.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import ph.thecoffeejunkie.crm.constant.InvoiceStatus;
import ph.thecoffeejunkie.crm.constant.PaymentTerms;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvoiceResponse(
        Long id,
        String invoiceNumber,
        Long quotationId,
        String quotationNumber,
        List<InvoiceItemResponse> invoiceItems,
        CustomerResponse customer,
        SalesRepResponse salesRep,
        InvoiceStatus status,
        String statusLabel,
        BigDecimal totalAmount,
        BigDecimal shippingCharges,
        LocalDate invoiceDate,
        LocalDate dueDate,
        PaymentTerms paymentTerms,
        String paymentTermsLabel,
        String notes,
        String termsAndConditions,
        String pdfPath,
        String proofOfPaymentPath,
        List<InvoicePaymentResponse> payments,
        LocalDateTime paidAt
) {}
