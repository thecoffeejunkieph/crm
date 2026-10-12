package ph.thecoffeejunkie.crm;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Helpers for tests using {@link MailIntegrationTest}. The mock is reset after each test, so call {@link #stub}. */
public final class MailSupport {

    private static final Pattern HREF = Pattern.compile("href=\"([^\"]+)\"");

    private MailSupport() {
    }

    public static void stub(JavaMailSender sender) {
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    }

    public static List<MimeMessage> sent(JavaMailSender sender) {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender, atLeast(0)).send(captor.capture());
        return captor.getAllValues();
    }

    /** The single call-to-action link in the message body. */
    public static String link(MimeMessage m) throws Exception {
        Matcher matcher = HREF.matcher(String.valueOf(m.getContent()));
        if (!matcher.find()) {
            throw new IllegalStateException("No link in " + m.getContent());
        }
        return matcher.group(1);
    }

    /** The raw one-time token: the last path segment of the link. */
    public static String token(MimeMessage m) throws Exception {
        String link = link(m);
        return link.substring(link.lastIndexOf('/') + 1);
    }
}
