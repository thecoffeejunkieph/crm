package ph.thecoffeejunkie.crm.insights;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

@RestController
@PreAuthorize("hasRole('ADMIN')")
class AdSpendController {

    private final AdSpendService spend;
    private final AdPerformanceService performance;
    private final Clock clock;

    AdSpendController(AdSpendService spend, AdPerformanceService performance, Clock clock) {
        this.spend = spend;
        this.performance = performance;
        this.clock = clock;
    }

    @GetMapping("/api/insights/ad-performance")
    AdPerformance performance(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return performance.report(from, to);
    }

    @GetMapping("/api/insights/ad-spend")
    List<AdSpendRow> list(@RequestParam(required = false) String month) {
        return spend.list(InsightsMonth.parseForPlanning(month, clock));
    }

    @PutMapping("/api/insights/ad-spend/{sourceId}/{month}")
    AdSpendRow set(@PathVariable long sourceId, @PathVariable String month, @Valid @RequestBody AdSpendRequest req) {
        return spend.set(sourceId, InsightsMonth.parseForPlanning(month, clock), req);
    }

    @DeleteMapping("/api/insights/ad-spend/{sourceId}/{month}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clear(@PathVariable long sourceId, @PathVariable String month) {
        spend.clear(sourceId, InsightsMonth.parseForPlanning(month, clock));
    }
}
