package ph.thecoffeejunkie.crm.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.servlet.http.HttpServletRequest;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditChanges;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.files.StorageService;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

@RestController
@RequestMapping("/api/me")
class MeController {

    record UpdateMeRequest(@NotBlank @Size(max = 100) String firstName, @NotBlank @Size(max = 100) String lastName,
                           @Size(max = 50) String phone) {
    }

    record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    private final AppUserRepository users;
    private final CurrentActor actor;
    private final PasswordEncoder encoder;
    private final LoginThrottle throttle;
    private final SessionAdmin sessionAdmin;
    private final AuditLog audit;
    private final TransactionTemplate tx;
    private final StorageService storage;

    MeController(AppUserRepository users, CurrentActor actor, PasswordEncoder encoder, LoginThrottle throttle,
                 SessionAdmin sessionAdmin, AuditLog audit, TransactionTemplate tx, StorageService storage) {
        this.users = users;
        this.actor = actor;
        this.encoder = encoder;
        this.throttle = throttle;
        this.sessionAdmin = sessionAdmin;
        this.audit = audit;
        this.tx = tx;
        this.storage = storage;
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

    /** bcrypt runs outside any transaction (no pooled connection held across it); only the save and audit are in one. */
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request) {
        AppUser u = current();
        if (!encoder.matches(body.currentPassword(), u.passwordHash)) {
            // A hijacked session must not be able to brute-force the current password.
            throttle.recordFailure(u.email);
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Current password is incorrect.",
                    Map.of("field", "currentPassword"));
        }
        PasswordPolicy.check(u.email, body.newPassword());
        u.passwordHash = encoder.encode(body.newPassword());
        tx.executeWithoutResult(s -> {
            users.save(u);
            audit.record(AuditAction.PASSWORD_CHANGED, "USER", u.id, "Password changed", AuditChanges.diff());
        });
        var session = request.getSession(false);
        sessionAdmin.revokeAllExcept("user:" + u.id, session == null ? null : session.getId());
        throttle.clear(u.email);
    }

    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    MeResponse setAvatar(@RequestPart("file") MultipartFile file) {
        AppUser u = current();
        return replaceAvatar(u, storage.store("users/" + u.id + "/avatar", file, StorageService.IMAGE_TYPES));
    }

    @DeleteMapping("/avatar")
    MeResponse removeAvatar() {
        return replaceAvatar(current(), null);
    }

    private MeResponse replaceAvatar(AppUser u, String key) {
        String old = u.avatarKey;
        u.avatarKey = key;
        MeResponse saved = MeResponse.of(users.save(u));
        storage.deleteQuietly(old);
        return saved;
    }

    private AppUser current() {
        long id = actor.scope().userId();
        return users.findById(id).orElseThrow(() -> new DomainException(ErrorCode.UNAUTHORIZED, "Login required"));
    }
}
