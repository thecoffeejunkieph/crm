package ph.thecoffeejunkie.crm.dto.response;

import java.io.Serializable;

public record DeliveryOrderItemResponse(String productName, Integer quantity) implements Serializable {}
