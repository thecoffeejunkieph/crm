package ph.thecoffeejunkie.crm.notifications;

import java.time.Instant;

/** entityType + entityId point at the record the notification is about. */
public record NotificationResponse(
        long id,
        NotificationType type,
        String entityType,
        Long entityId,
        String title,
        String body,
        boolean read,
        Instant createdAt
) {}
