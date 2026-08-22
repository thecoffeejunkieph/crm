package ph.thecoffeejunkie.crm.dto.request;

import java.time.LocalDate;

public record DeliveryOrderUpdateRequest(
        String deliveryInstructions,
        String deliveryAddress,
        LocalDate targetDeliveryDate
) {}
