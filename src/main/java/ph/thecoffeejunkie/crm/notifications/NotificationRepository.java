package ph.thecoffeejunkie.crm.notifications;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientUserId(long recipientUserId, Pageable pageable);

    Page<Notification> findByRecipientUserIdAndReadAtIsNull(long recipientUserId, Pageable pageable);

    long countByRecipientUserIdAndReadAtIsNull(long recipientUserId);

    Optional<Notification> findByIdAndRecipientUserId(long id, long recipientUserId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipientUserId = :userId and n.readAt is null")
    void markAllRead(long userId, Instant now);
}
