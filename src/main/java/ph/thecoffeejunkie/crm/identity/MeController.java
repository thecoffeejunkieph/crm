package ph.thecoffeejunkie.crm.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

@RestController
@RequestMapping("/api/me")
class MeController {

    record UpdateMeRequest(@NotBlank @Size(max = 100) String firstName, @NotBlank @Size(max = 100) String lastName,
                           @Size(max = 50) String phone) {
    }

    private final AppUserRepository users;
    private final CurrentActor actor;

    MeController(AppUserRepository users, CurrentActor actor) {
        this.users = users;
        this.actor = actor;
    }

    @GetMapping
    MeResponse me() {
        return MeResponse.of(current());
    }

    @PutMapping
    MeResponse update(@Valid @RequestBody UpdateMeRequest body) {
        AppUser u = current();
        u.firstName = body.firstName().trim();
        u.lastName = body.lastName().trim();
        u.phone = body.phone();
        return MeResponse.of(users.save(u));
    }

    private AppUser current() {
        long id = actor.scope().userId();
        return users.findById(id).orElseThrow(() -> new DomainException(ErrorCode.UNAUTHORIZED, "Login required"));
    }
}
