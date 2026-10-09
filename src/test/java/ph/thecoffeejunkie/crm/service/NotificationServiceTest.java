package ph.thecoffeejunkie.crm.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import ph.thecoffeejunkie.crm.constant.NotificationType;
import ph.thecoffeejunkie.crm.constant.Role;
import ph.thecoffeejunkie.crm.dto.response.NotificationResponse;
import ph.thecoffeejunkie.crm.entity.CRMUser;
import ph.thecoffeejunkie.crm.entity.Customer;
import ph.thecoffeejunkie.crm.entity.Notification;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.CRMUserRepository;
import ph.thecoffeejunkie.crm.repository.NotificationRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final CRMUserRepository userRepository = mock(CRMUserRepository.class);
    private final NotificationStreams streams = mock(NotificationStreams.class);
    private final NotificationService service = new NotificationService(repository, userRepository, streams);

    @BeforeEach
    void assignIdsOnSave() {
        AtomicLong ids = new AtomicLong();
        when(repository.saveAll(anyList())).thenAnswer(inv -> {
            List<Notification> rows = new ArrayList<>(inv.getArgument(0));
            rows.forEach(n -> n.setId(ids.incrementAndGet()));
            return rows;
        });
    }

    @AfterEach
    void clearUser() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings("unchecked")
    void toOwnerSavesOneRowAndPublishes() {
        service.toOwner(user("rep@x.ph"), NotificationType.INVOICE_PAID, 9L, "Invoice INV-1 paid", "body");

        ArgumentCaptor<List<Notification>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertEquals(1, saved.getValue().size());
        Notification row = saved.getValue().getFirst();
        assertEquals("rep@x.ph", row.getRecipientEmail());
        assertEquals(NotificationType.INVOICE_PAID, row.getType());
        assertEquals(9L, row.getEntityId());
        assertEquals("Invoice INV-1 paid", row.getTitle());
        assertEquals("body", row.getBody());
        assertNull(row.getReadAt());

        ArgumentCaptor<NotificationResponse> published = ArgumentCaptor.forClass(NotificationResponse.class);
        verify(streams).publish(eq("rep@x.ph"), published.capture());
        assertEquals(1L, published.getValue().id());
        assertFalse(published.getValue().read());
    }

    @Test
    void toOwnerFallsBackToAdminsWhenOwnerNull() {
        when(userRepository.findByActiveTrueAndRolesContaining("ADMIN"))
                .thenReturn(List.of(user("a1@x.ph"), user("a2@x.ph")));

        service.toOwner(null, NotificationType.QUOTATION_ACCEPTED, 1L, "t", "b");

        verify(streams).publish(eq("a1@x.ph"), any());
        verify(streams).publish(eq("a2@x.ph"), any());
    }

    @Test
    void toRoleNotifiesActiveUsersInRole() {
        when(userRepository.findByActiveTrueAndRolesContaining("WAREHOUSE"))
                .thenReturn(List.of(user("w1@x.ph"), user("w2@x.ph")));

        service.toRole(Role.WAREHOUSE, NotificationType.DELIVERY_ORDER_CREATED, 3L, "t", "b");

        verify(streams, times(2)).publish(anyString(), any());
        verify(streams).publish(eq("w1@x.ph"), any());
        verify(streams).publish(eq("w2@x.ph"), any());
    }

    @Test
    void skipsTheActingUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("rep@x.ph", null, List.of()));

        service.toOwner(user("rep@x.ph"), NotificationType.INVOICE_PAID, 9L, "t", "b");

        verify(repository, never()).saveAll(anyList());
        verifyNoInteractions(streams);
    }

    @Test
    void neverThrows() {
        when(repository.saveAll(anyList())).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> service.toOwner(user("rep@x.ph"), NotificationType.INVOICE_PAID, 9L, "t", "b"));

        verifyNoInteractions(streams);
    }

    @Test
    void markReadOfOtherUsersNotificationIs404() {
        when(repository.findByIdAndRecipientEmail(5L, "me@x.ph")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.markRead("me@x.ph", 5L));

        verify(repository, never()).save(any());
    }

    @Test
    void markAllReadStampsOnlyUnread() {
        Notification first = new Notification();
        Notification second = new Notification();
        when(repository.findByRecipientEmailAndReadAtIsNull("me@x.ph")).thenReturn(List.of(first, second));

        service.markAllRead("me@x.ph");

        assertNotNull(first.getReadAt());
        assertNotNull(second.getReadAt());
        verify(repository).saveAll(List.of(first, second));
    }

    @Test
    void customerNameHandlesNull() {
        Customer customer = new Customer();
        customer.setFirstName("Ana");
        customer.setLastName("Cruz");

        assertEquals("A customer", NotificationService.customerName(null));
        assertEquals("Ana Cruz", NotificationService.customerName(customer));
    }

    private static CRMUser user(String email) {
        return CRMUser.builder().email(email).build();
    }
}
