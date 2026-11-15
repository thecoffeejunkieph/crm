package ph.thecoffeejunkie.crm.portal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.customers.Customers;
import ph.thecoffeejunkie.crm.identity.OneTimeTokens;
import ph.thecoffeejunkie.crm.identity.TokenPurpose;
import ph.thecoffeejunkie.crm.shared.ContactPrincipal;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.time.Duration;
import java.util.Locale;

@Service
class PortalAuthService {

    private static final Logger log = LoggerFactory.getLogger(PortalAuthService.class);
    private static final String CONTACT = "CONTACT";

    private final Customers customers;
    private final OneTimeTokens tokens;
    private final AuditLog audit;
    private final PortalMailer mailer;
    private final TaskExecutor executor;
    private final TransactionTemplate tx;
    private final String frontendUrl;

    PortalAuthService(Customers customers, OneTimeTokens tokens, AuditLog audit, PortalMailer mailer,
                      @Qualifier("applicationTaskExecutor") TaskExecutor executor,
                      PlatformTransactionManager transactionManager, @Value("${app.frontend-url}") String frontendUrl) {
        this.customers = customers;
        this.tokens = tokens;
        this.audit = audit;
        this.mailer = mailer;
        this.executor = executor;
        this.tx = new TransactionTemplate(transactionManager);
        this.frontendUrl = frontendUrl;
    }

    /**
     * Silent for unknown, archived and portal-disabled contacts: the caller answers 202 regardless. The mail goes
     * out on another thread after the token commits, so SMTP latency cannot reveal whether the contact exists.
     */
    void requestLink(String rawEmail) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        String link = tx.execute(s -> customers.portalContactByEmail(email)
                .map(c -> frontendUrl + "/portal/signin/"
                        + tokens.issue(TokenPurpose.PORTAL_LOGIN, CONTACT, c.contactId(), Duration.ofMinutes(15), null))
                .orElse(null));
        if (link == null) {
            return;
        }
        executor.execute(() -> {
            try {
                mailer.sendLogin(email, link);
            } catch (RuntimeException e) {
                log.warn("Portal sign-in email failed: {}", e.toString());
            }
        });
    }

    /** Commits (token spent, audit written) before returning; the caller then establishes the session. */
    ContactPrincipal redeem(String token) {
        return tx.execute(s -> {
            ContactPrincipal c = tokens.redeem(TokenPurpose.PORTAL_LOGIN, token)
                    .filter(sub -> CONTACT.equals(sub.type()))
                    .flatMap(sub -> customers.portalContact(sub.id()))
                    .orElseThrow(() -> new DomainException(ErrorCode.TOKEN_EXPIRED,
                            "This link is invalid or has expired."));
            audit.recordFor(CONTACT, c.contactId(), c.displayName(), AuditAction.PORTAL_LOGIN, CONTACT,
                    c.contactId(), "Portal sign-in");
            return c;
        });
    }

    PortalMeResponse me(ContactPrincipal c) {
        return new PortalMeResponse(c.contactId(), c.displayName(), c.email(), c.customerId(),
                customers.customerName(c.customerId()));
    }
}
