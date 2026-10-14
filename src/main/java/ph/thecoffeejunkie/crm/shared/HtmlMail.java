package ph.thecoffeejunkie.crm.shared;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Renders templates/mail/{template}.html and sends it as HTML. Callers decide threading and failure handling. */
@Component
public class HtmlMail {

    private static final Logger log = LoggerFactory.getLogger(HtmlMail.class);

    private final JavaMailSender sender;
    private final TemplateEngine templates = new TemplateEngine();
    private final String from;
    private final String company;
    private final boolean logLinks;

    HtmlMail(JavaMailSender sender, @Value("${spring.mail.username:${app.company.email}}") String from,
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

    public String company() {
        return company;
    }

    public record Attachment(String filename, String contentType, byte[] bytes) {
    }

    /** {@code vars} are template variables; {@code company} is always added. */
    public void send(String to, String subject, String template, Map<String, Object> vars) {
        send(to, subject, template, vars, List.of());
    }

    /** Multipart only when there are attachments. */
    public void send(String to, String subject, String template, Map<String, Object> vars,
                     List<Attachment> attachments) {
        var ctx = new Context();
        ctx.setVariables(vars);
        ctx.setVariable("company", company);
        if (logLinks) {
            log.info("DEV {} mail for {}: {}", template, to, vars.get("link"));
        }
        String html = templates.process(template, ctx);
        try {
            MimeMessage message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, !attachments.isEmpty(), StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            for (Attachment a : attachments) {
                helper.addAttachment(a.filename(), new ByteArrayResource(a.bytes()), a.contentType());
            }
            sender.send(message);
        } catch (MessagingException e) {
            throw new MailPreparationException(e);
        }
    }
}
