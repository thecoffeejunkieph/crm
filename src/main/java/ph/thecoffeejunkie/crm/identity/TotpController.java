package ph.thecoffeejunkie.crm.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.util.List;

@RestController
class TotpController {

    private static final String PENDING_SECRET = "pendingTotpSecret";

    private final TotpService service;
    private final CurrentActor actor;

    TotpController(TotpService service, CurrentActor actor) {
        this.service = service;
        this.actor = actor;
    }

    /** Permit-all in SecurityConfig, so the PRE_2FA authority is checked here. */
    @PostMapping("/api/auth/totp")
    LoginResponse login(@RequestBody TotpCodeRequest req, Authentication auth, HttpServletRequest request,
                        HttpServletResponse response) {
        if (!hasAuthority(auth, "PRE_2FA") || !(auth.getPrincipal() instanceof StaffPrincipal p)) {
            throw new DomainException(ErrorCode.UNAUTHORIZED, "Login required.");
        }
        boolean recovery = req.recoveryCode() != null && !req.recoveryCode().isBlank();
        if (recovery) {
            service.verifyRecovery(p.userId(), req.recoveryCode());
        } else {
            service.verifyLogin(p.userId(), req.code());
        }
        return new LoginResponse("DONE", upgrade(p, request, response));
    }

    @PostMapping("/api/me/totp/setup")
    TotpSetupResponse setup(HttpServletRequest request) {
        long userId = actor.scope().userId();
        TotpSetupResponse r = service.setup(userId);
        // Bound to the user: session attributes survive a re-login in the same session.
        request.getSession().setAttribute(PENDING_SECRET, userId + ":" + r.secret());
        return r;
    }

    @PostMapping("/api/me/totp/enable")
    RecoveryCodesResponse enable(@RequestBody TotpCodeRequest req, Authentication auth, HttpServletRequest request,
                                 HttpServletResponse response) {
        var session = request.getSession(false);
        long userId = actor.scope().userId();
        String stored = session == null ? null : (String) session.getAttribute(PENDING_SECRET);
        String prefix = userId + ":";
        if (stored == null || !stored.startsWith(prefix)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Start 2FA setup again.");
        }
        RecoveryCodesResponse r = service.enable(userId, stored.substring(prefix.length()), req.code());
        session.removeAttribute(PENDING_SECRET);
        // An ENROL_2FA session becomes a full one only now that the DB transaction has committed.
        String token = null;
        if (hasAuthority(auth, "ENROL_2FA") && auth.getPrincipal() instanceof StaffPrincipal p) {
            token = upgrade(p, request, response);
        }
        return new RecoveryCodesResponse(r.codes(), token);
    }

    @DeleteMapping("/api/me/totp")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void disable(@RequestBody TotpCodeRequest req) {
        service.disable(actor.scope().userId(), req.code());
    }

    @PostMapping("/api/admin/users/{id}/totp-reset")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset(@PathVariable long id) {
        service.adminReset(id);
    }

    /** Full authorities, rotated session id; returns the new id for ANDROID clients (they hold it as a token). */
    private static String upgrade(StaffPrincipal p, HttpServletRequest request, HttpServletResponse response) {
        Principals.establish(request, response, UsernamePasswordAuthenticationToken.authenticated(
                p, null, List.of(new SimpleGrantedAuthority("ROLE_" + p.role()))));
        return HeaderOrCookieSessionIdResolver.ANDROID.equals(
                request.getHeader(HeaderOrCookieSessionIdResolver.CLIENT_TYPE_HEADER))
                ? request.getSession().getId() : null;
    }

    private static boolean hasAuthority(Authentication auth, String authority) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(authority));
    }
}
