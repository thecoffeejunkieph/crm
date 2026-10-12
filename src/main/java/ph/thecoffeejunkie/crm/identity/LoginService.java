package ph.thecoffeejunkie.crm.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
class LoginService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditLog audit;
    private final Clock clock;
    /** Checked when there is no real hash, so unknown emails cost the same bcrypt time as wrong passwords. */
    private final String dummyHash;

    LoginService(AppUserRepository users, PasswordEncoder encoder, AuditLog audit, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.clock = clock;
        this.dummyHash = encoder.encode("timing-equaliser-not-a-password");
    }

    /** No rollback on DomainException: failed-login counters and audit rows must survive the 401. */
    @Transactional(noRollbackFor = DomainException.class)
    LoginResponse login(LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        AppUser u = users.findByEmailAndArchivedAtIsNull(email).orElse(null);
        if (u == null) {
            encoder.matches(req.password(), dummyHash);
            audit.recordFor("SYSTEM", null, null, AuditAction.LOGIN_FAILED, "USER", null,
                    "Failed login for " + email);
            throw invalidCredentials();
        }
        if (u.lockedUntil != null && u.lockedUntil.isAfter(now)) {
            throw new DomainException(ErrorCode.ACCOUNT_LOCKED, "Too many failed logins. Try again later.");
        }
        // An invited user without a password yet fails exactly like a wrong password.
        boolean ok = encoder.matches(req.password(), u.passwordHash == null ? dummyHash : u.passwordHash)
                && u.passwordHash != null;
        if (!ok) {
            recordFailure(u, email, now);
            throw invalidCredentials();
        }

        u.failedLogins = 0;
        var principal = Principals.staffOf(u, u.warehouseIds);
        String next;
        String authority;
        if (u.totpEnabledAt != null) {
            next = "TOTP";
            authority = "PRE_2FA";
        } else if (u.role == Role.ADMIN) {
            next = "ENROL_TOTP";
            authority = "ENROL_2FA";
        } else {
            next = "DONE";
            authority = "ROLE_" + u.role;
            audit.recordFor("USER", u.id, u.displayName(), AuditAction.LOGIN_SUCCEEDED, "USER", u.id,
                    "Logged in");
        }
        Principals.establish(request, response, UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority(authority))));
        String token = HeaderOrCookieSessionIdResolver.ANDROID.equals(req.clientType())
                ? request.getSession().getId() : null;
        return new LoginResponse(next, token);
    }

    private void recordFailure(AppUser u, String email, Instant now) {
        u.failedLogins++;
        audit.recordFor("USER", u.id, u.displayName(), AuditAction.LOGIN_FAILED, "USER", u.id,
                "Failed login for " + email);
        if (u.failedLogins >= MAX_FAILURES) {
            u.lockedUntil = now.plus(LOCK_DURATION);
            u.failedLogins = 0;
            audit.recordFor("USER", u.id, u.displayName(), AuditAction.ACCOUNT_LOCKED, "USER", u.id,
                    "Locked for " + LOCK_DURATION.toMinutes() + " minutes after " + MAX_FAILURES + " failed logins");
        }
    }

    private static DomainException invalidCredentials() {
        return new DomainException(ErrorCode.UNAUTHORIZED, "Invalid email or password");
    }
}
