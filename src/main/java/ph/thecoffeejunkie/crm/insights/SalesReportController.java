package ph.thecoffeejunkie.crm.insights;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.shared.CurrentActor;

import java.time.Clock;

/** {@code /api/reports/**} is also an ADMIN/SALES URL rule in SecurityConfig. */
@RestController
class SalesReportController {

    private final ReportService reports;
    private final CurrentActor actor;
    private final Clock clock;

    SalesReportController(ReportService reports, CurrentActor actor, Clock clock) {
        this.reports = reports;
        this.actor = actor;
        this.clock = clock;
    }

    /** {@code repId} is ADMIN only; 0 means unassigned. */
    @GetMapping("/api/reports/sales")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES')")
    SalesReport sales(@RequestParam(required = false) String by, @RequestParam(required = false) String from,
                      @RequestParam(required = false) String to, @RequestParam(required = false) Long repId) {
        return reports.report(actor.scope(), ReportBy.parse(by), ReportRange.parse(from, to, clock), repId);
    }
}
