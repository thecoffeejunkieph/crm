package ph.thecoffeejunkie.crm.insights;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.time.Clock;

/** Role gates are also URL rules in SecurityConfig, so a wrong role gets 403 before any parameter check. */
@RestController
class InsightsController {

    private final InsightsService insights;
    private final CurrentActor actor;
    private final Clock clock;

    InsightsController(InsightsService insights, CurrentActor actor, Clock clock) {
        this.insights = insights;
        this.actor = actor;
        this.clock = clock;
    }

    /** Always the signed-in rep: there is deliberately no repId parameter. */
    @GetMapping("/api/insights/sales")
    @PreAuthorize("hasRole('SALES')")
    SalesInsights sales(@RequestParam(required = false) String month) {
        return insights.sales(actor.scope().userId(), InsightsMonth.parse(month, clock));
    }

    @GetMapping("/api/insights/admin")
    @PreAuthorize("hasRole('ADMIN')")
    AdminInsights admin(@RequestParam(required = false) String month) {
        return insights.admin(InsightsMonth.parse(month, clock));
    }

    /** Scoped to the caller's warehouses; ADMIN sees all. Ignores month: it is today's work. */
    @GetMapping("/api/insights/warehouse")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE')")
    WarehouseInsights warehouse() {
        Scope s = actor.scope();
        return insights.warehouse(s.role() == Role.ADMIN ? null : s.warehouseIds(), InsightsMonth.today(clock));
    }
}
