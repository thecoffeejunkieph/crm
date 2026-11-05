package ph.thecoffeejunkie.crm.customers;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
import ph.thecoffeejunkie.crm.shared.Role;

import java.util.List;

@RestController
@RequestMapping("/api/lead-sources")
@PreAuthorize("hasAnyRole('ADMIN', 'SALES')")
class LeadSourceController {

    private final LeadSources sources;
    private final CurrentActor actor;

    LeadSourceController(LeadSources sources, CurrentActor actor) {
        this.sources = sources;
        this.actor = actor;
    }

    @GetMapping
    List<LeadSourceResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return sources.list(includeArchived && actor.scope().role() == Role.ADMIN);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    LeadSourceResponse add(@Valid @RequestBody LeadSourceRequest req) {
        return sources.add(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    LeadSourceResponse rename(@PathVariable long id, @Valid @RequestBody LeadSourceRequest req) {
        return sources.rename(id, req);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    LeadSourceResponse archive(@PathVariable long id) {
        return sources.setArchived(id, true);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    LeadSourceResponse restore(@PathVariable long id) {
        return sources.setArchived(id, false);
    }
}
