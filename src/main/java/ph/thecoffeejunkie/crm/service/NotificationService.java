package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import ph.thecoffeejunkie.crm.constant.NotificationType;
import ph.thecoffeejunkie.crm.constant.Role;
import ph.thecoffeejunkie.crm.dto.response.NotificationResponse;
import ph.thecoffeejunkie.crm.dto.response.PageResponse;
import ph.thecoffeejunkie.crm.entity.CRMUser;
import ph.thecoffeejunkie.crm.entity.Customer;
import ph.thecoffeejunkie.crm.entity.Notification;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.CRMUserRepository;
import ph.thecoffeejunkie.crm.repository.NotificationRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Stores a notification per recipient and pushes it live. Called from the middle of business
 * flows (a customer accepting a quote, an invoice being paid), so it never throws: a failed
 * notification must not fail the action it is about.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final CRMUserRepository userRepository;
    private final NotificationStreams streams;

    /** The record's owner (e.g. its sales rep), or every admin when it has none or it is inactive. */
    public void toOwner(CRMUser ownerOrNull, NotificationType type, Long entityId, String title, String body) {
        try {
            List<String> emails = isActive(ownerOrNull) ? List.of(ownerOrNull.getEmail()) : emailsWithRole(Role.ADMIN);
            send(emails, type, entityId, title, body);
        } catch (RuntimeException e) {
            log.warn("Could not send {} notification for {}: {}", type, entityId, e.getMessage());
        }
    }

    public void toRole(Role role, NotificationType type, Long entityId, String title, String body) {
        try {
            send(emailsWithRole(role), type, entityId, title, body);
        } catch (RuntimeException e) {
            log.warn("Could not send {} notification for {}: {}", type, entityId, e.getMessage());
        }
    }

    public static String customerName(Customer customer) {
        return customer == null ? "A customer" : customer.getFirstName() + " " + customer.getLastName();
    }

    public PageResponse<NotificationResponse> list(String email, PageRequest pageRequest) {
        Page<Notification> page = repository.findByRecipientEmailOrderByIdDesc(email, pageRequest);
        return new PageResponse<>(page.getNumber() + 1, page.getSize(), page.getTotalPages(),
                page.getTotalElements(), page.getContent().stream().map(NotificationService::toResponse).toList());
    }

    public long unreadCount(String email) {
        return repository.countByRecipientEmailAndReadAtIsNull(email);
    }

    public void markRead(String email, Long id) {
        // Looked up by id AND recipient, so guessing another user's id is a plain 404.
        Notification notification = repository.findByIdAndRecipientEmail(id, email)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", id));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            repository.save(notification);
        }
    }

    public void markAllRead(String email) {
        List<Notification> unread = repository.findByRecipientEmailAndReadAtIsNull(email);
        LocalDateTime now = LocalDateTime.now();
        unread.forEach(n -> n.setReadAt(now));
        repository.saveAll(unread);
    }

    private void send(Collection<String> emails, NotificationType type, Long entityId, String title, String body) {
        Set<String> recipients = new LinkedHashSet<>(emails);
        // Nobody needs telling about something they just did themselves.
        Authentication actor = SecurityContextHolder.getContext().getAuthentication();
        if (actor != null) {
            recipients.remove(actor.getName());
        }
        if (recipients.isEmpty()) {
            return;
        }

        List<Notification> rows = recipients.stream().map(email -> {
            Notification n = new Notification();
            n.setRecipientEmail(email);
            n.setType(type);
            n.setEntityId(entityId);
            n.setTitle(title);
            n.setBody(body);
            return n;
        }).toList();

        repository.saveAll(rows).forEach(n -> streams.publish(n.getRecipientEmail(), toResponse(n)));
    }

    // A deactivated rep's records fall back to the admins so the event isn't lost. Looked up by
    // email because the owner is often an uninitialized lazy proxy (only its id is safe to read).
    private boolean isActive(CRMUser owner) {
        return owner != null && userRepository.findByEmail(owner.getEmail())
                .map(u -> Boolean.TRUE.equals(u.getActive()))
                .orElse(false);
    }

    private List<String> emailsWithRole(Role role) {
        return userRepository.findByActiveTrueAndRolesContaining(role.name()).stream()
                .map(CRMUser::getEmail)
                .toList();
    }

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getEntityId(), n.getTitle(), n.getBody(),
                n.getReadAt() != null, n.getCreatedAt());
    }
}
