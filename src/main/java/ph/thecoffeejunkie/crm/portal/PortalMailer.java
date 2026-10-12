package ph.thecoffeejunkie.crm.portal;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;

/** Portal sign-in email, rendered from templates/mail/portal-login.html. */
@Component
class PortalMailer {

    private static final Logger log = LoggerFactory.getLogger(PortalMailer.class);

    private final JavaMailSender sender;
    private final TemplateEngine templates = new TemplateEngine();
    private final String from;
    private final String company;
    private final boolean logLinks;

    PortalMailer(JavaMailSender sender, @Value("${spring.mail.username:${app.company.email}}") String from,
                 @Value("${app.company.name}") String company,
                 @Value("${app.mail.log-links:false}") boolean logLinks) {
        this.sender = sender;
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

    void sendLogin(String to, String link) {
        var ctx = new Context();
        ctx.setVariable("company", company);
        ctx.setVariable("link", link);
        if (logLinks) {
            log.info("DEV portal link for {}: {}", to, link);
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("Sign in to your " + company + " portal");
            helper.setText(templates.process("portal-login", ctx), true);
            sender.send(message);
        } catch (MessagingException e) {
            throw new MailPreparationException(e);
        }
    }
}
