package ph.thecoffeejunkie.crm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ph.thecoffeejunkie.crm.entity.Notification;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientEmailOrderByIdDesc(String recipientEmail, Pageable pageable);

    long countByRecipientEmailAndReadAtIsNull(String recipientEmail);

    Optional<Notification> findByIdAndRecipientEmail(Long id, String recipientEmail);

    List<Notification> findByRecipientEmailAndReadAtIsNull(String recipientEmail);
}
