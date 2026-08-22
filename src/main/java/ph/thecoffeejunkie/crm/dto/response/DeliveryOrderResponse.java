package ph.thecoffeejunkie.crm.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import ph.thecoffeejunkie.crm.constant.DeliveryOrderStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeliveryOrderResponse(
        Long id,
        String deliveryOrderNumber,
        InvoiceResponse invoice,
        DeliveryOrderStatus status,
        String deliveryAddress,
        String deliveryInstructions,
        LocalDate targetDeliveryDate,
        List<DeliveryOrderItemResponse> invoiceItems,
        String pdfPath,
        List<String> proofOfPickupPaths,
        LocalDateTime pickedUpAt,
        List<String> proofOfDeliveryPaths,
        LocalDateTime deliveredAt,
        LocalDateTime createdAt
) {}
