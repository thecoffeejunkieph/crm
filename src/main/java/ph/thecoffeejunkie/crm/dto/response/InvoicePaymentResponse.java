package ph.thecoffeejunkie.crm.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import ph.thecoffeejunkie.crm.constant.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvoicePaymentResponse(
        Long id,
        BigDecimal amount,
        PaymentMethod method,
        String methodLabel,
        String proofOfPaymentPath,
        LocalDateTime recordedAt
) {}
