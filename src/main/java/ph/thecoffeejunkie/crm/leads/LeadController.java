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
import org.springframework.web.bind.annotation.PutMapping;
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
    private final LeadDuplicates duplicates;
    private final CurrentActor actor;

    LeadController(LeadService leads, LeadDuplicates duplicates, CurrentActor actor) {
        this.duplicates = duplicates;
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

    @GetMapping("/duplicates")
    LeadDuplicates.LeadDuplicatesResponse duplicates(@RequestParam(required = false) String phone,
                                       @RequestParam(required = false) String email,
                                       @RequestParam(required = false) Long excludeId) {
        return duplicates.find(actor.scope(), phone, email, excludeId);
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

    @PutMapping("/{id}")
    LeadResponse update(@PathVariable long id, @Valid @RequestBody LeadRequest req) {
        return leads.update(actor.scope(), id, req);
    }

    @PostMapping("/{id}/status")
    LeadResponse status(@PathVariable long id, @Valid @RequestBody LeadStatusRequest req) {
        return leads.setStatus(actor.scope(), id, req);
    }

    @PostMapping("/{id}/convert")
    LeadResponse convert(@PathVariable long id, @Valid @RequestBody LeadConvertRequest req) {
        return leads.convert(actor.scope(), id, req);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    LeadResponse assign(@PathVariable long id, @Valid @RequestBody LeadAssignRequest req) {
        return leads.assign(actor.scope(), id, req);
    }
}
