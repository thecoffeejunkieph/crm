package ph.thecoffeejunkie.crm.identity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.time.Duration;
import java.util.Locale;

/**
 * Token-based password setting: invite acceptance and reset. Like LoginService, bcrypt runs outside any
 * transaction; the token redeem, password write and audit row share one short transaction, so a failure
 * (e.g. the policy rejecting the password) leaves the token usable.
 */
@Slf4j
@Service
class PasswordResetService {

    private final AppUserRepository users;
    private final OneTimeTokens tokens;
    private final PasswordEncoder encoder;
    private final AuditLog audit;
    private final LoginThrottle throttle;
    private final SessionAdmin sessions;
    private final IdentityMailer mailer;
    private final TransactionTemplate tx;
    private final String frontendUrl;

    PasswordResetService(AppUserRepository users, OneTimeTokens tokens, PasswordEncoder encoder, AuditLog audit,
                         LoginThrottle throttle, SessionAdmin sessions, IdentityMailer mailer,
                         PlatformTransactionManager transactionManager,
                         @Value("${app.frontend-url}") String frontendUrl) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.audit = audit;
        this.throttle = throttle;
        this.sessions = sessions;
        this.mailer = mailer;
        this.tx = new TransactionTemplate(transactionManager);
        this.frontendUrl = frontendUrl;
    }

    void acceptInvite(String token, String password) {
        setPassword(TokenPurpose.INVITE, token, password, AuditAction.INVITE_ACCEPTED, "Accepted invite");
    }

    /** Silent for unknown, archived and still-pending users: the caller answers 202 regardless. */
    void forgot(String rawEmail) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        String link = tx.execute(s -> users.findByEmailAndArchivedAtIsNull(email)
                .filter(u -> u.passwordHash != null)
                .map(u -> frontendUrl + "/reset-password/"
                        + tokens.issue(TokenPurpose.PASSWORD_RESET, "USER", u.id, Duration.ofHours(1), null))
                .orElse(null));
        if (link == null) {
            return;
        }
        try {
            mailer.sendPasswordReset(email, link);
        } catch (RuntimeException e) {
            log.warn("Password reset email failed: {}", e.toString());
        }
    }

    void reset(String token, String password) {
        AppUser u = setPassword(TokenPurpose.PASSWORD_RESET, token, password, AuditAction.PASSWORD_RESET,
                "Password reset");
        throttle.clear(u.email);
        sessions.revokeAll("user:" + u.id);
    }

    private AppUser setPassword(TokenPurpose purpose, String token, String password, AuditAction action,
                                String summary) {
        PasswordPolicy.check("", password); // length only: cheap rejection before the bcrypt cost
        String hash = encoder.encode(password);
        return tx.execute(s -> {
            OneTimeTokens.Subject subject = tokens.redeem(purpose, token).orElseThrow(PasswordResetService::gone);
            AppUser u = users.findById(subject.id()).filter(a -> a.archivedAt == null)
                    .orElseThrow(PasswordResetService::gone);
            PasswordPolicy.check(u.email, password);
            u.passwordHash = hash;
            u.failedLogins = 0;
            u.lockedUntil = null;
            users.save(u);
            audit.recordFor("USER", u.id, u.displayName(), action, "USER", u.id, summary);
            return u;
        });
    }

    private static DomainException gone() {
        return new DomainException(ErrorCode.TOKEN_EXPIRED, "This link is invalid or has expired.");
    }
}
