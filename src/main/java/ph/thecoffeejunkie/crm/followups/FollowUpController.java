package ph.thecoffeejunkie.crm.followups;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/follow-ups")
@PreAuthorize("hasAnyRole('ADMIN', 'SALES')")
class FollowUpController {

    private final FollowUpService followUps;
    private final CurrentActor actor;

    FollowUpController(FollowUpService followUps, CurrentActor actor) {
        this.followUps = followUps;
        this.actor = actor;
    }

    @GetMapping
    PageResponse<FollowUpResponse> list(@RequestParam(required = false) String bucket,
                                        @RequestParam(required = false) Long customerId,
                                        @RequestParam(required = false) Long orderId,
                                        @RequestParam(required = false) Long assigneeId,
                                        @RequestParam(defaultValue = "1") @Min(1) int page,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return followUps.list(actor.scope(), FollowUpDay.Bucket.parse(bucket), customerId, orderId, assigneeId, page, size);
    }

    @GetMapping("/counts")
    FollowUpCounts counts(@RequestParam(required = false) Long customerId, @RequestParam(required = false) Long orderId) {
        return followUps.counts(actor.scope(), customerId, orderId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    FollowUpResponse create(@Valid @RequestBody FollowUpRequest req) {
        return followUps.create(actor.scope(), req);
    }
}
