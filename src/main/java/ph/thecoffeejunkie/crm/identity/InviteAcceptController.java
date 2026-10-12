package ph.thecoffeejunkie.crm.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public endpoints (no session): invite acceptance and the forgot/reset password pair. */
@RestController
@RequestMapping("/api/auth")
class InviteAcceptController {

    record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    private final PasswordResetService service;

    InviteAcceptController(PasswordResetService service) {
        this.service = service;
    }

    @PostMapping("/invites/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void accept(@Valid @RequestBody AcceptInviteRequest req) {
        service.acceptInvite(req.token(), req.password());
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        service.forgot(req.email());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset(@Valid @RequestBody ResetPasswordRequest req) {
        service.reset(req.token(), req.password());
    }
}
