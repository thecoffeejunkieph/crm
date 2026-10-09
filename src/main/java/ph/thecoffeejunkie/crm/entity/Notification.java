package ph.thecoffeejunkie.crm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ph.thecoffeejunkie.crm.constant.NotificationType;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Notification extends BaseEntity {

    @Column(nullable = false)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    private Long entityId;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String body;

    /** Null until the recipient reads it. */
    private LocalDateTime readAt;
}
