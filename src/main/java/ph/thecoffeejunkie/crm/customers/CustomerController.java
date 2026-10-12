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
import ph.thecoffeejunkie.crm.audit.AuditEventResponse;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
class CustomerController {

    private final Customers customers;
    private final CurrentActor actor;

    CustomerController(Customers customers, CurrentActor actor) {
        this.customers = customers;
        this.actor = actor;
    }

    @GetMapping
    @PreAuthorize("@customerPermissions.canRead()")
    PageResponse<CustomerSummary> list(@RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "false") boolean includeArchived,
                                       PageQuery page) {
        return customers.list(actor.scope(), q, includeArchived, page);
    }

    // Deliberately unscoped (POS lookup); minimal fields only.
    @GetMapping("/lookup")
    @PreAuthorize("@customerPermissions.canRead()")
    List<CustomerLookupItem> lookup(@RequestParam(required = false) String q) {
        return customers.lookup(q);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@customerPermissions.canRead()")
    CustomerResponse get(@PathVariable long id) {
        return customers.get(actor.scope(), id);
    }

    @PostMapping
    @PreAuthorize("@customerPermissions.canCreate()")
    CustomerResponse create(@Valid @RequestBody CustomerRequest req) {
        return customers.create(actor.scope(), req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@customerPermissions.canEdit()")
    CustomerResponse update(@PathVariable long id, @Valid @RequestBody CustomerRequest req) {
        return customers.update(actor.scope(), id, req);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("@customerPermissions.canArchive()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable long id) {
        customers.archive(actor.scope(), id);
    }

    @PutMapping("/{id}/assignment")
    @PreAuthorize("@customerPermissions.canAssign()")
    CustomerResponse assign(@PathVariable long id, @RequestBody AssignmentRequest req) {
        return customers.assign(actor.scope(), id, req.repId());
    }

    @PostMapping("/{id}/contacts")
    @PreAuthorize("@customerPermissions.canEdit()")
    ContactResponse addContact(@PathVariable long id, @Valid @RequestBody ContactRequest req) {
        return customers.addContact(actor.scope(), id, req);
    }

    @PutMapping("/{id}/contacts/{contactId}")
    @PreAuthorize("@customerPermissions.canEdit()")
    ContactResponse updateContact(@PathVariable long id, @PathVariable long contactId,
                                  @Valid @RequestBody ContactRequest req) {
        return customers.updateContact(actor.scope(), id, contactId, req);
    }

    @PostMapping("/{id}/contacts/{contactId}/archive")
    @PreAuthorize("@customerPermissions.canEdit()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archiveContact(@PathVariable long id, @PathVariable long contactId) {
        customers.archiveContact(actor.scope(), id, contactId);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("@customerPermissions.canRead()")
    PageResponse<AuditEventResponse> history(@PathVariable long id, PageQuery page) {
        return customers.history(actor.scope(), id, page);
    }
}
