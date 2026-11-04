package ph.thecoffeejunkie.crm.insights;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.Map;

@RestController
@PreAuthorize("hasRole('ADMIN')")
class TargetController {

    private final TargetService targets;
    private final Clock clock;

    TargetController(TargetService targets, Clock clock) {
        this.targets = targets;
        this.clock = clock;
    }

    @GetMapping("/api/insights/targets")
    List<TargetRow> list(@RequestParam(required = false) String month) {
        return targets.list(InsightsMonth.parseForPlanning(month, clock));
    }

    @PutMapping("/api/insights/targets/{repId}/{month}")
    TargetRow set(@PathVariable long repId, @PathVariable String month, @Valid @RequestBody TargetRequest req) {
        return targets.set(repId, InsightsMonth.parseForPlanning(month, clock), req);
    }

    @PostMapping("/api/insights/targets/copy")
    Map<String, Integer> copy(@RequestParam String from, @RequestParam String to) {
        return Map.of("copied", targets.copy(InsightsMonth.parseForPlanning(from, clock),
                InsightsMonth.parseForPlanning(to, clock)));
    }
}
