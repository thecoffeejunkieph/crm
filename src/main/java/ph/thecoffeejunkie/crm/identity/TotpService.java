package ph.thecoffeejunkie.crm.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.EncryptionService;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.Role;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Not @Transactional: each write is its own short TransactionTemplate so a failed code's counter and audit rows
 * commit even though the caller then gets an error (same approach as LoginService).
 */
@Service
class TotpService {

    private static final String ENTITY = "USER";
    private static final int RECOVERY_CODES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final RecoveryCodeRepository recovery;
    private final EncryptionService encryption;
    private final AuditLog audit;
    private final LoginThrottle throttle;
    private final SessionAdmin sessions;
    private final Clock clock;
    private final TransactionTemplate tx;
    /** PBKDF2 is deliberately slow, so the key is derived once. */
    private final SecretKey key;

    TotpService(AppUserRepository users, RecoveryCodeRepository recovery, EncryptionService encryption,
                AuditLog audit, LoginThrottle throttle, SessionAdmin sessions, Clock clock,
                PlatformTransactionManager transactionManager,
                @Value("${app.security.encryption-key}") String encryptionKey) throws Exception {
        this.users = users;
        this.recovery = recovery;
        this.encryption = encryption;
        this.audit = audit;
        this.throttle = throttle;
        this.sessions = sessions;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.key = encryption.getKeyFromPassword(encryptionKey);
    }

    /** Nothing is persisted: the caller keeps the secret in the session until enable. */
    TotpSetupResponse setup(long userId) {
        AppUser u = user(userId);
        if (u.totpEnabledAt != null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Two-factor authentication is already on.");
        }
        byte[] secret = new byte[20];
        RANDOM.nextBytes(secret);
        String base32 = Base32.encode(secret);
        String uri = "otpauth://totp/TCJ%20CRM:" + u.email + "?secret=" + base32
                + "&issuer=TCJ%20CRM&algorithm=SHA1&digits=6&period=30";
        return new TotpSetupResponse(uri, base32, QrCodes.svg(uri));
    }

    RecoveryCodesResponse enable(long userId, String pendingBase32, String code) {
        if (pendingBase32 == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Start setup first.");
        }
        OptionalLong step = Totp.verify(Base32.decode(pendingBase32), code, clock.instant(), -1);
        if (step.isEmpty()) {
            throw new DomainException(ErrorCode.TOTP_INVALID, "That code is not valid.");
        }
        return tx.execute(s -> {
            if (users.enableTotp(userId, crypt(pendingBase32, true), clock.instant(), step.getAsLong()) == 0) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED, "Two-factor authentication is already on.");
            }
            recovery.deleteAllFor(userId);
            List<String> codes = new ArrayList<>();
            for (int i = 0; i < RECOVERY_CODES; i++) {
                String c = newRecoveryCode();
                codes.add(c);
                RecoveryCode row = new RecoveryCode();
                row.userId = userId;
                row.codeHash = hash(c);
                recovery.save(row);
            }
            audit.record(AuditAction.TOTP_ENROLLED, ENTITY, userId, "Two-factor authentication enabled", null);
            return new RecoveryCodesResponse(codes, null);
        });
    }

    void verifyLogin(long userId, String code) {
        verify(userId, u -> checkTotp(u, code), u -> audit.recordFor(ENTITY, u.id, u.displayName(),
                AuditAction.LOGIN_SUCCEEDED, ENTITY, u.id, "Logged in with two-factor code"));
    }

    void verifyRecovery(long userId, String recoveryCode) {
        String hash = hash(recoveryCode == null ? "" : recoveryCode.trim().toLowerCase(Locale.ROOT));
        verify(userId, u -> recovery.markUsed(u.id, hash, clock.instant()) == 1, u -> {
            audit.recordFor(ENTITY, u.id, u.displayName(), AuditAction.RECOVERY_CODE_USED, ENTITY, u.id,
                    "Signed in with a recovery code");
            audit.recordFor(ENTITY, u.id, u.displayName(), AuditAction.LOGIN_SUCCEEDED, ENTITY, u.id,
                    "Logged in with a recovery code");
        });
    }

    void disable(long userId, String code) {
        AppUser u = user(userId);
        if (u.role == Role.ADMIN) {
            throw new DomainException(ErrorCode.FORBIDDEN, "Admins cannot turn off two-factor authentication.");
        }
        if (u.totpEnabledAt == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Two-factor authentication is not on.");
        }
        verify(userId, p -> checkTotp(p, code), p -> {
            users.clearTotp(p.id);
            recovery.deleteAllFor(p.id);
            audit.record(AuditAction.TOTP_DISABLED, ENTITY, p.id, "Two-factor authentication disabled", null);
        });
    }

    void adminReset(long targetUserId) {
        tx.executeWithoutResult(s -> {
            AppUser u = users.findById(targetUserId).filter(t -> t.archivedAt == null)
                    .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "User not found"));
            users.clearTotp(u.id);
            recovery.deleteAllFor(u.id);
            audit.record(AuditAction.TOTP_RESET, ENTITY, u.id, "Two-factor authentication reset for " + u.email,
                    null);
            // After commit: a login between an in-transaction revoke and the commit would survive it.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sessions.revokeAll("user:" + u.id);
                }
            });
        });
    }

    /**
     * Shared by every second-factor check. A wrong code counts toward the same lockout as a wrong password
     * (LoginThrottle decides, the row columns mirror it). check and onSuccess run in one transaction.
     */
    private void verify(long userId, Predicate<AppUser> check, Consumer<AppUser> onSuccess) {
        AppUser u = user(userId);
        if (throttle.isLocked(u.email)) {
            throw new DomainException(ErrorCode.ACCOUNT_LOCKED, "Too many failed logins. Try again later.");
        }
        boolean ok = Boolean.TRUE.equals(tx.execute(s -> {
            if (u.totpEnabledAt == null || !check.test(u)) {
                return false;
            }
            if (u.failedLogins != 0 || u.lockedUntil != null) {
                users.resetFailedLogins(u.id);
            }
            onSuccess.accept(u);
            return true;
        }));
        if (!ok) {
            LoginThrottle.Result result = throttle.recordFailure(u.email);
            tx.executeWithoutResult(s -> recordFailure(u, result));
            throw new DomainException(ErrorCode.TOTP_INVALID, "That code is not valid.");
        }
        throttle.clear(u.email);
    }

    /** Atomic against replay: the conditional UPDATE lets only one of two parallel requests claim the step. */
    private boolean checkTotp(AppUser u, String code) {
        byte[] secret = Base32.decode(crypt(u.totpSecretEnc, false));
        OptionalLong step = Totp.verify(secret, code, clock.instant(), u.totpLastStep == null ? -1 : u.totpLastStep);
        return step.isPresent() && users.advanceTotpStep(u.id, step.getAsLong()) == 1;
    }

    private void recordFailure(AppUser u, LoginThrottle.Result result) {
        audit.recordFor(ENTITY, u.id, u.displayName(), AuditAction.LOGIN_FAILED, ENTITY, u.id,
                "Failed two-factor code for " + u.email);
        if (result == LoginThrottle.Result.LOCKED) {
            users.lockLogin(u.id, clock.instant().plus(LoginThrottle.WINDOW));
            audit.recordFor(ENTITY, u.id, u.displayName(), AuditAction.ACCOUNT_LOCKED, ENTITY, u.id,
                    "Locked " + u.email + " for " + LoginThrottle.WINDOW.toMinutes() + " minutes after "
                            + LoginThrottle.MAX_FAILURES + " failed logins");
        } else if (result == LoginThrottle.Result.COUNTED) {
            users.incrementFailedLogins(u.id);
        }
    }

    private AppUser user(long id) {
        return users.findById(id).filter(u -> u.archivedAt == null)
                .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHORIZED, "Login required"));
    }

    private String crypt(String text, boolean encrypt) {
        try {
            return encrypt ? encryption.encrypt(text, key) : encryption.decrypt(text, key);
        } catch (Exception e) {
            throw new IllegalStateException("Could not " + (encrypt ? "encrypt" : "decrypt") + " the TOTP secret", e);
        }
    }

    /** xxxxx-xxxxx, lower-case base32 (50 bits). */
    private static String newRecoveryCode() {
        byte[] bytes = new byte[7];
        RANDOM.nextBytes(bytes);
        String s = Base32.encode(bytes).toLowerCase(Locale.ROOT);
        return s.substring(0, 5) + "-" + s.substring(5, 10);
    }

    private static String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
