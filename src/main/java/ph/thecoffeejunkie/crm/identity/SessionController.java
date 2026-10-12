package ph.thecoffeejunkie.crm.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditChanges;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.util.List;

@RestController
class SessionController {

    private final SessionAdmin admin;
    private final AppUserRepository users;
    private final CurrentActor actor;
    private final AuditLog audit;

    SessionController(SessionAdmin admin, AppUserRepository users, CurrentActor actor, AuditLog audit) {
        this.admin = admin;
        this.users = users;
        this.actor = actor;
        this.audit = audit;
    }

    @GetMapping("/api/me/sessions")
    List<SessionResponse> mine(HttpServletRequest request) {
        return admin.list(principal(actor.scope().userId()), currentId(request));
    }

    @DeleteMapping("/api/me/sessions/{hash}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void revokeMine(@PathVariable String hash) {
        revokeOne(actor.scope().userId(), hash);
    }

    @DeleteMapping("/api/me/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void revokeMyOthers(HttpServletRequest request) {
        revokeMany(actor.scope().userId(), currentId(request));
    }

    @GetMapping("/api/admin/users/{id}/sessions")
    @PreAuthorize("hasRole('ADMIN')")
    List<SessionResponse> ofUser(@PathVariable long id, HttpServletRequest request) {
        requireUser(id);
        return admin.list(principal(id), currentId(request));
    }

    @DeleteMapping("/api/admin/users/{id}/sessions/{hash}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void revokeOfUser(@PathVariable long id, @PathVariable String hash) {
        requireUser(id);
        revokeOne(id, hash);
    }

    @DeleteMapping("/api/admin/users/{id}/sessions")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void revokeAllOfUser(@PathVariable long id) {
        requireUser(id);
        revokeMany(id, null);
    }

    // The audit row is written first: if it fails nothing was revoked.
    private void revokeOne(long userId, String hash) {
        if (admin.list(principal(userId), null).stream().noneMatch(s -> s.id().equals(hash))) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Session not found");
        }
        audit.record(AuditAction.SESSION_REVOKED, "USER", userId, "Session revoked", AuditChanges.diff());
        admin.revokeByHash(principal(userId), hash);
    }

    private void revokeMany(long userId, String keepId) {
        if (admin.list(principal(userId), keepId).stream().anyMatch(s -> !s.current())) {
            audit.record(AuditAction.SESSION_REVOKED, "USER", userId, "Sessions revoked", AuditChanges.diff());
        }
        admin.revokeAllExcept(principal(userId), keepId);
    }

    private void requireUser(long id) {
        if (!users.existsById(id)) {
            throw new DomainException(ErrorCode.NOT_FOUND, "User not found");
        }
    }

    private static String currentId(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        return s == null ? null : s.getId();
    }

    private static String principal(long userId) {
        return "user:" + userId;
    }
}
