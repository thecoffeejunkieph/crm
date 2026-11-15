package ph.thecoffeejunkie.crm.identity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.shared.HtmlMail;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.Map;


/** Identity emails, rendered from templates/mail/*.html. Send failures are logged and never undo the user. */
@Slf4j
@Component
class IdentityMailer {

    private final HtmlMail mail;
    private final AppUserRepository users;
    private final TransactionTemplate requiresNew;

    IdentityMailer(HtmlMail mail, AppUserRepository users, PlatformTransactionManager transactionManager) {
        this.mail = mail;
        this.users = users;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** After commit, so a rollback never mails a link to a user that does not exist. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onInviteIssued(InviteIssued event) {
        AppUser user = requiresNew.execute(s -> users.findById(event.userId()).orElse(null));
        if (user == null) {
            return;
        }
        String status = "SENT";
        try {
            sendInvite(user, event.link());
        } catch (RuntimeException e) {
            log.warn("Invite email to user {} failed: {}", user.id, e.toString());
            status = "FAILED";
        }
        String finalStatus = status;
        requiresNew.executeWithoutResult(s -> users.setInviteEmailStatus(event.userId(), finalStatus));
    }

    void sendInvite(AppUser user, String link) {
        var vars = new HashMap<String, Object>();
        vars.put("name", user.firstName);
        vars.put("link", link);
        mail.send(user.email, "You're invited to " + mail.company(), "invite", vars);
    }

    void sendPasswordReset(String email, String link) {
        mail.send(email, "Reset your " + mail.company() + " password", "password-reset", Map.of("link", link));
    }
}
