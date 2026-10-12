package ph.thecoffeejunkie.crm.identity;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

@Service
class LoginService {

    /** What the caller turns into a session, after the write transaction has committed. */
    record Outcome(String next, Authentication authentication) {
    }

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditLog audit;
    private final LoginThrottle throttle;
    private final Clock clock;
    private final TransactionTemplate tx;
    /** Checked when there is no real hash, so every path costs the same bcrypt time. */
    private final String dummyHash;

    LoginService(AppUserRepository users, PasswordEncoder encoder, AuditLog audit, LoginThrottle throttle,
                 Clock clock, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.throttle = throttle;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.dummyHash = encoder.encode("timing-equaliser-not-a-password");
    }

    /**
     * Lockout is decided by LoginThrottle (Redis, per email, same rules for unknown emails); the app_user
     * columns only mirror it.
     * Deliberately not @Transactional: bcrypt (~250 ms at cost 12) runs with no transaction and no pooled
     * connection held, so a login flood cannot drain Hikari. The lookup is its own short repository read; the
     * mirror UPDATE and audit rows go in one short write transaction afterwards.
     */
    Outcome login(LoginRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (throttle.isLocked(email)) {
            encoder.matches(req.password(), dummyHash);
            throw new DomainException(ErrorCode.ACCOUNT_LOCKED, "Too many failed logins. Try again later.");
        }
        AppUser u = users.findByEmailAndArchivedAtIsNull(email).orElse(null);
        String hash = u == null ? null : u.passwordHash;
        // Unknown emails and invited users without a password fail exactly like a wrong password.
        boolean ok = encoder.matches(req.password(), hash == null ? dummyHash : hash) && hash != null;
        if (!ok) {
            LoginThrottle.Result result = throttle.recordFailure(email);
            tx.executeWithoutResult(s -> recordFailure(u, email, result));
            // Even the attempt that locks answers UNAUTHORIZED; the next one gets ACCOUNT_LOCKED.
            throw new DomainException(ErrorCode.UNAUTHORIZED, "Invalid email or password");
        }

        throttle.clear(email);
        return tx.execute(s -> succeed(u));
    }

    private Outcome succeed(AppUser u) {
        if (u.failedLogins != 0 || u.lockedUntil != null) {
            users.resetFailedLogins(u.id);
        }
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
        return new Outcome(next, UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority(authority))));
    }

    private void recordFailure(AppUser u, String email, LoginThrottle.Result result) {
        boolean locked = result == LoginThrottle.Result.LOCKED;
        String summary = "Failed login for " + email;
        if (u == null) {
            audit.recordFor("SYSTEM", null, null, AuditAction.LOGIN_FAILED, "USER", null, summary);
            if (locked) {
                audit.recordFor("SYSTEM", null, null, AuditAction.ACCOUNT_LOCKED, "USER", null,
                        "Unknown email " + email + " locked after " + LoginThrottle.MAX_FAILURES + " failed logins");
            }
            return;
        }
        audit.recordFor("USER", u.id, u.displayName(), AuditAction.LOGIN_FAILED, "USER", u.id, summary);
        if (locked) {
            users.lockLogin(u.id, clock.instant().plus(LoginThrottle.WINDOW));
            audit.recordFor("USER", u.id, u.displayName(), AuditAction.ACCOUNT_LOCKED, "USER", u.id,
                    "Locked " + email + " for " + LoginThrottle.WINDOW.toMinutes() + " minutes after "
                            + LoginThrottle.MAX_FAILURES + " failed logins");
        } else if (result == LoginThrottle.Result.COUNTED) {
            // ALREADY_LOCKED attempts (raced past isLocked) are audited but not mirrored: the lock is already set.
            users.incrementFailedLogins(u.id);
        }
    }
}
