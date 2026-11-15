package ph.thecoffeejunkie.crm.notifications;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;

import java.util.Map;

/** The signed-in user's own notifications; every endpoint is scoped to the caller. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
class NotificationController {

    private final Notifications notifications;
    private final NotificationStreams streams;
    private final CurrentActor actor;

    @GetMapping
    PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unread, PageQuery page) {
        return notifications.list(userId(), unread, page);
    }

    @GetMapping("/unread-count")
    Map<String, Long> unreadCount() {
        return Map.of("count", notifications.unreadCount(userId()));
    }

    @PostMapping("/{id}/read")
    ResponseEntity<Void> markRead(@PathVariable long id) {
        notifications.markRead(userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    ResponseEntity<Void> markAllRead() {
        notifications.markAllRead(userId());
        return ResponseEntity.noContent().build();
    }

    // EventSource cannot send custom headers; it authenticates with the session cookie.
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream() {
        long userId = userId();
        return streams.open(userId, notifications.unreadCount(userId));
    }

    private long userId() {
        return actor.scope().userId();
    }
}
