package ph.thecoffeejunkie.crm.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Stores a notification per recipient and pushes it live once the surrounding transaction commits,
 * so a rolled-back action never notifies anyone. Call it inside the business transaction. The signed-in staff user
 * is never told about their own action (consequences such as stock alerts still reach them); system and portal
 * actions notify everyone addressed.
 */
@Service
public class Notifications {

    private static final Logger log = LoggerFactory.getLogger(Notifications.class);

    /** Consequences of an action rather than news of it: the actor still needs to hear these. */
    private static final Set<NotificationType> TOLD_TO_ACTOR = EnumSet.of(NotificationType.LOW_STOCK,
            NotificationType.OUT_OF_STOCK, NotificationType.ORDER_SHORT, NotificationType.TARGET_MILESTONE,
            NotificationType.RESERVATION_TAKEN, NotificationType.ORDER_RESERVED);

    private final NotificationRepository repo;
    private final NotificationStreams streams;
    private final Users users;
    private final CurrentActor actor;

    Notifications(NotificationRepository repo, NotificationStreams streams, Users users, CurrentActor actor) {
        this.repo = repo;
        this.streams = streams;
        this.users = users;
        this.actor = actor;
    }

    @Transactional
    public void toUser(long userId, NotificationType type, String entityType, Long entityId, String title, String body) {
        send(List.of(userId), type, entityType, entityId, title, body);
    }

    @Transactional
    public void toUsers(Collection<Long> userIds, NotificationType type, String entityType, Long entityId,
                        String title, String body) {
        send(userIds, type, entityType, entityId, title, body);
    }

    @Transactional
    public void toRole(Role role, NotificationType type, String entityType, Long entityId, String title, String body) {
        send(users.activeIdsWithRole(role), type, entityType, entityId, title, body);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(long userId, boolean unreadOnly, PageQuery page) {
        var pageable = page.toPageable(Set.of("id", "createdAt"), Sort.by(Sort.Direction.DESC, "id"));
        var rows = unreadOnly ? repo.findByRecipientUserIdAndReadAtIsNull(userId, pageable)
                : repo.findByRecipientUserId(userId, pageable);
        return PageResponse.of(rows.map(Notification::toResponse));
    }

    @Transactional(readOnly = true)
    public long unreadCount(long userId) {
        return repo.countByRecipientUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(long userId, long id) {
        // Looked up by id AND recipient, so guessing another user's id is a plain 404.
        Notification n = repo.findByIdAndRecipientUserId(id, userId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Notification not found"));
        if (n.readAt == null) {
            n.readAt = Instant.now();
        }
    }

    @Transactional
    public void markAllRead(long userId) {
        repo.markAllRead(userId, Instant.now());
    }

    private void send(Collection<Long> recipients, NotificationType type, String entityType, Long entityId,
                      String title, String body) {
        Long self = TOLD_TO_ACTOR.contains(type) ? null : actor.staff().map(StaffPrincipal::userId).orElse(null);
        List<Notification> rows = recipients.stream().distinct().filter(id -> !id.equals(self)).map(userId -> {
            Notification n = new Notification();
            n.recipientUserId = userId;
            n.type = type;
            n.entityType = entityType;
            n.entityId = entityId;
            n.title = title;
            n.body = body;
            return n;
        }).toList();
        repo.saveAll(rows);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // A failed push must not fail the action it is about; the row is stored either way.
                for (Notification n : rows) {
                    try {
                        streams.publish(n.recipientUserId, n.toResponse());
                    } catch (RuntimeException e) {
                        log.warn("Could not push notification {}: {}", n.id, e.getMessage());
                    }
                }
            }
        });
    }
}
