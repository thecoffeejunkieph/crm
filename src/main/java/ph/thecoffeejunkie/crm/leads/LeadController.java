package ph.thecoffeejunkie.crm.leads;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.PageResponse;

@RestController
@Validated
@RequestMapping("/api/leads")
@PreAuthorize("hasAnyRole('ADMIN', 'SALES')")
class LeadController {

    private final LeadService leads;
    private final CurrentActor actor;

    LeadController(LeadService leads, CurrentActor actor) {
        this.leads = leads;
        this.actor = actor;
    }

    @GetMapping
    PageResponse<LeadResponse> list(@RequestParam(required = false) LeadStatus status,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) Long sourceId,
                                    @RequestParam(required = false) Long repId,
                                    @RequestParam(defaultValue = "1") @Min(1) int page,
                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return leads.list(actor.scope(), status, q, sourceId, repId, page, size);
    }

    @GetMapping("/counts")
    LeadCounts counts() {
        return leads.counts(actor.scope());
    }

    @GetMapping("/{id}")
    LeadResponse get(@PathVariable long id) {
        return leads.get(actor.scope(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    LeadResponse create(@Valid @RequestBody LeadRequest req) {
        return leads.create(actor.scope(), req);
    }
}
