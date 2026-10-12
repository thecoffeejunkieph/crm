package ph.thecoffeejunkie.crm.portal;

import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.shared.HtmlMail;

import java.util.Map;

/** Portal sign-in email, rendered from templates/mail/portal-login.html. */
@Component
class PortalMailer {

    private final HtmlMail mail;

    PortalMailer(HtmlMail mail) {
        this.mail = mail;
    }

    void sendLogin(String to, String link) {
        mail.send(to, "Sign in to your " + mail.company() + " portal", "portal-login", Map.of("link", link));
    }
}
