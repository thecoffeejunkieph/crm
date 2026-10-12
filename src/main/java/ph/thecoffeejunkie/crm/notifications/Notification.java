package ph.thecoffeejunkie.crm.notifications;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "notification")
class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    long recipientUserId;
    @Enumerated(EnumType.STRING)
    NotificationType type;
    String entityType;
    Long entityId;
    String title;
    String body;
    /** Null until the recipient reads it. */
    Instant readAt;
    Instant createdAt = Instant.now();

    NotificationResponse toResponse() {
        return new NotificationResponse(id, type, entityType, entityId, title, body, readAt != null, createdAt);
    }
}
