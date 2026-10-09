package ph.thecoffeejunkie.crm.dto.response;

import ph.thecoffeejunkie.crm.constant.NotificationType;

import java.time.LocalDateTime;

/** entityId is the quotation, invoice or delivery order id, depending on type. */
public record NotificationResponse(
        Long id,
        NotificationType type,
        Long entityId,
        String title,
        String body,
        boolean read,
        LocalDateTime createdAt
) {}
