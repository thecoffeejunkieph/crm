package ph.thecoffeejunkie.crm.deals;

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
@RequestMapping("/api/deals")
@PreAuthorize("hasAnyRole('ADMIN', 'SALES')")
class DealController {

    private final DealService deals;
    private final CurrentActor actor;

    DealController(DealService deals, CurrentActor actor) {
        this.deals = deals;
        this.actor = actor;
    }

    @GetMapping
    PageResponse<DealResponse> list(@RequestParam(required = false) DealStage stage,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) Long customerId,
                                    @RequestParam(required = false) Long repId,
                                    @RequestParam(defaultValue = "1") @Min(1) int page,
                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return deals.list(actor.scope(), stage, q, customerId, repId, page, size);
    }

    @GetMapping("/summary")
    DealSummary summary() {
        return deals.summary(actor.scope());
    }

    @GetMapping("/{id}")
    DealResponse get(@PathVariable long id) {
        return deals.get(actor.scope(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DealResponse create(@Valid @RequestBody DealRequest req) {
        return deals.create(actor.scope(), req);
    }

    @PutMapping("/{id}")
    DealResponse update(@PathVariable long id, @Valid @RequestBody DealRequest req) {
        return deals.update(actor.scope(), id, req);
    }

    @PostMapping("/{id}/stage")
    DealResponse stage(@PathVariable long id, @Valid @RequestBody DealStageRequest req) {
        return deals.setStage(actor.scope(), id, req);
    }
}
