package ph.thecoffeejunkie.crm.approvals;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;

import java.util.List;

@RestController
@RequestMapping("/api/approvals")
class ApprovalController {

    private final Approvals approvals;

    ApprovalController(Approvals approvals) {
        this.approvals = approvals;
    }

    @PostMapping
    ApprovalResponse request(@Valid @RequestBody CreateApprovalRequest req) {
        return approvals.request(req);
    }

    @GetMapping
    PageResponse<ApprovalResponse> list(@RequestParam(required = false) ApprovalStatus status,
                                        @RequestParam(defaultValue = "false") boolean mine,
                                        @RequestParam(required = false) String subjectType,
                                        @RequestParam(required = false) List<Long> subjectId, PageQuery page) {
        return approvals.list(status, mine, subjectType, subjectId, page);
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    ApprovalResponse decide(@PathVariable long id, @Valid @RequestBody DecisionRequest req) {
        return approvals.decide(id, req);
    }

    @PostMapping("/{id}/cancel")
    ResponseEntity<Void> cancel(@PathVariable long id) {
        approvals.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
