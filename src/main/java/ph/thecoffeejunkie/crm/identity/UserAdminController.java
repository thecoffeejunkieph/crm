package ph.thecoffeejunkie.crm.identity;

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
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.shared.Role;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class UserAdminController {

    private final UserAdminService service;

    UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<UserResponse> list(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) Role role,
                                    @RequestParam(defaultValue = "false") boolean includeArchived, PageQuery page) {
        return service.list(q, role, includeArchived, page);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse invite(@Valid @RequestBody InviteUserRequest req) {
        return service.invite(req);
    }

    @PutMapping("/{id}")
    UserResponse update(@PathVariable long id, @Valid @RequestBody UpdateUserRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable long id) {
        service.archive(id);
    }

    @PostMapping("/{id}/resend-invite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resendInvite(@PathVariable long id) {
        service.resendInvite(id);
    }
}
