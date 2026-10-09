package ph.thecoffeejunkie.crm.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ph.thecoffeejunkie.crm.dto.response.NotificationResponse;
import ph.thecoffeejunkie.crm.dto.response.PageResponse;
import ph.thecoffeejunkie.crm.service.NotificationService;
import ph.thecoffeejunkie.crm.service.NotificationStreams;

import java.util.Map;

/** The signed-in user's own notifications; every endpoint is scoped to the caller. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationStreams streams;

    @GetMapping
    public PageResponse<NotificationResponse> getAll(Authentication auth,
                                                     @RequestParam(defaultValue = "1") int pageNumber,
                                                     @RequestParam(defaultValue = "20") int pageSize) {
        return notificationService.list(auth.getName(), PageRequest.of(pageNumber - 1, pageSize));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication auth) {
        return Map.of("count", notificationService.unreadCount(auth.getName()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(Authentication auth, @PathVariable Long id) {
        notificationService.markRead(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(Authentication auth) {
        notificationService.markAllRead(auth.getName());
        return ResponseEntity.noContent().build();
    }

    // EventSource can't send an Authorization header; it authenticates with the jwt cookie
    // (new EventSource(url, { withCredentials: true })).
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication auth) {
        return streams.open(auth.getName(), notificationService.unreadCount(auth.getName()));
    }
}
