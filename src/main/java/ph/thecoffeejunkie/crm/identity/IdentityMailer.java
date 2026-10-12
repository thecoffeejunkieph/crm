package ph.thecoffeejunkie.crm.identity;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;

/** Identity emails, rendered from templates/mail/*.html. Send failures are logged and never undo the user. */
@Slf4j
@Component
class IdentityMailer {

    private final JavaMailSender sender;
    private final AppUserRepository users;
    private final TransactionTemplate requiresNew;
    private final TemplateEngine templates = new TemplateEngine();
    private final String from;
    private final String company;
    private final boolean logLinks;

    IdentityMailer(JavaMailSender sender, AppUserRepository users, PlatformTransactionManager transactionManager,
                   @Value("${spring.mail.username:${app.company.email}}") String from,
                   @Value("${app.company.name}") String company,
                   @Value("${app.mail.log-links:false}") boolean logLinks) {
        this.sender = sender;
        this.users = users;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.from = from;
        this.company = company;
        this.logLinks = logLinks;
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/mail/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        templates.setTemplateResolver(resolver);
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
        send(user.email, "You're invited to " + company, "invite", user.firstName, link, "invite");
    }

    void sendPasswordReset(String email, String link) {
        send(email, "Reset your " + company + " password", "password-reset", null, link, "password reset");
    }

    private void send(String to, String subject, String template, String name, String link, String what) {
        var ctx = new Context();
        ctx.setVariable("name", name);
        ctx.setVariable("company", company);
        ctx.setVariable("link", link);
        if (logLinks) {
            log.info("DEV {} link for {}: {}", what, to, link);
        }
        String html = templates.process(template, ctx);
        try {
            MimeMessage message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
        } catch (jakarta.mail.MessagingException e) {
            throw new org.springframework.mail.MailPreparationException(e);
        }
    }
}
