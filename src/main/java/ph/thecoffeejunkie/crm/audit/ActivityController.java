package ph.thecoffeejunkie.crm.audit;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/activity")
class ActivityController {

    private final AuditLog log;

    ActivityController(AuditLog log) {
        this.log = log;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    PageResponse<AuditEventResponse> activity(
            String actorType, Long actorId, String entityType, AuditAction action,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to, PageQuery page) {
        return log.search(actorType, actorId, entityType, action, from, to, page);
    }
}
