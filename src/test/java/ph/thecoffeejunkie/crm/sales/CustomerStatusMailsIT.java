package ph.thecoffeejunkie.crm.sales;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.MailIntegrationTest;
import ph.thecoffeejunkie.crm.MailSupport;
import ph.thecoffeejunkie.crm.shared.HtmlMail;
import ph.thecoffeejunkie.crm.shared.Role;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

@MailIntegrationTest
class CustomerStatusMailsIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JavaMailSender mail;
    @Autowired HtmlMail htmlMail;
    @Autowired @Qualifier("applicationTaskExecutor") ThreadPoolTaskExecutor executor;

    SalesFixture f;
    long rep;
    long admin;
    long customer;
    long product;
    long contactA;
    RequestPostProcessor asRep;
    RequestPostProcessor asAdmin;

    @BeforeEach
    void seed() {
        MailSupport.stub(mail);
        f = new SalesFixture(jdbc, mvc);
        rep = f.user("rep", Role.SALES);
        admin = f.user("boss", Role.ADMIN);
        customer = f.customer("acme", rep);
        product = f.product("A", "112.00", null, "VATABLE");
        asRep = as(rep, Role.SALES);
        asAdmin = as(admin, Role.ADMIN);
        contactA = f.contact(customer, "a", true);
        f.contact(customer, "b", true);
        f.contact(customer, "off", false);
        f.stock(product, "10");
    }

    @AfterEach
    void cleanUp() {
        MailSupport.awaitIdle(executor);
        f.cleanUp();
    }

    static String to(MimeMessage m) {
        try {
            return m.getAllRecipients()[0].toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String subject(MimeMessage m) {
        try {
            return m.getSubject();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String body(MimeMessage m) throws Exception {
        Object content = m.getContent();
        if (content instanceof Multipart mp) {
            StringBuilder sb = new StringBuilder();
            text(mp, sb);
            return sb.toString();
        }
        return String.valueOf(content);
    }

    private static void text(Multipart mp, StringBuilder sb) throws Exception {
        for (int i = 0; i < mp.getCount(); i++) {
            var part = mp.getBodyPart(i);
            if (part.getContent() instanceof Multipart inner) {
                text(inner, sb);
            } else if (!Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                sb.append(part.getContent());
            }
        }
    }

    static List<String> attachments(MimeMessage m) throws Exception {
        List<String> names = new ArrayList<>();
        if (m.getContent() instanceof Multipart mp) {
            collect(mp, names);
        }
        return names;
    }

    private static void collect(Multipart mp, List<String> names) throws Exception {
        for (int i = 0; i < mp.getCount(); i++) {
            var part = mp.getBodyPart(i);
            if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                names.add(part.getFileName());
            } else if (part.getContent() instanceof Multipart inner) {
                collect(inner, names);
            }
        }
    }

    private List<MimeMessage> withSubject(String subject) {
        MailSupport.awaitIdle(executor);
        return MailSupport.sent(mail).stream().filter(m -> subject.equals(subject(m))).toList();
    }

    private String number(long orderId) {
        return jdbc.queryForObject("select number from sales_order where id = ?", String.class, orderId);
    }

    private long confirmedOrder() throws Exception {
        long order = f.createdOrder(asRep, customer, line(product, "3")); // total 336.00
        f.setStatus(order, "CONFIRMED"); // no OrderConfirmed event, so no confirm mail
        return order;
    }

    private long pendingPayment(long order, String submittedByType, long submittedById) {
        return jdbc.queryForObject("insert into payment (order_id, status, method, cash_amount, received_on, "
                + "submitted_by_type, submitted_by_id) values (?, 'PENDING', 'GCASH', 100, current_date, ?, ?) "
                + "returning id", Long.class, order, submittedByType, submittedById);
    }

    private void reject(long payment, String reason) throws Exception {
        mvc.perform(f.json(post("/api/payments/" + payment + "/reject"),
                "{\"version\":0,\"reason\":\"" + reason + "\"}").with(asAdmin)).andExpect(status().isOk());
    }

    // --- order confirmed ---

    @Test
    void confirmMailsPortalContacts() throws Exception {
        long order = f.createdOrder(asRep, customer, line(product, "3"));
        f.transition(asRep, order, "confirm", null).andExpect(status().isOk());
        List<MimeMessage> sent = MailSupport.awaitMail(mail, 2);
        assertThat(sent).extracting(CustomerStatusMailsIT::to)
                .containsExactlyInAnyOrder(f.contactEmail("a"), f.contactEmail("b"));
        assertThat(sent).extracting(CustomerStatusMailsIT::subject)
                .containsOnly("Order " + number(order) + " confirmed — " + htmlMail.company());
        assertThat(body(sent.get(0))).contains(number(order)).contains("336.00");
    }

    @Test
    void noPortalContactsNoMail() throws Exception {
        long lonely = f.customer("lonely", rep);
        long order = f.createdOrder(asRep, lonely, line(product, "1"));
        f.transition(asRep, order, "confirm", null).andExpect(status().isOk());
        MailSupport.awaitIdle(executor);
        assertThat(MailSupport.sent(mail)).isEmpty();
    }

    @Test
    void oneFailingAddressDoesNotStopOthers() throws Exception {
        String a = f.contactEmail("a");
        doThrow(new MailSendException("down")).when(mail).send(argThat((MimeMessage m) -> to(m).equals(a)));
        long order = f.createdOrder(asRep, customer, line(product, "3"));
        f.transition(asRep, order, "confirm", null).andExpect(status().isOk());
        MailSupport.awaitMail(mail, 2);
        assertThat(MailSupport.sent(mail)).extracting(CustomerStatusMailsIT::to).contains(f.contactEmail("b"));
    }

    // --- payments ---

    @Test
    void verifyMailsBalance() throws Exception {
        long order = confirmedOrder();
        long payment = pendingPayment(order, "USER", rep);
        mvc.perform(f.json(post("/api/payments/" + payment + "/verify"), "{\"version\":0}").with(asAdmin))
                .andExpect(status().isOk());
        List<MimeMessage> sent = MailSupport.awaitMail(mail, 2);
        assertThat(sent).extracting(CustomerStatusMailsIT::subject)
                .containsOnly("Payment received for " + number(order) + " — " + htmlMail.company());
        assertThat(body(sent.get(0))).contains("100.00").contains("236.00");
    }

    @Test
    void rejectPortalPaymentMailsReason() throws Exception {
        long order = confirmedOrder();
        reject(pendingPayment(order, "CONTACT", contactA), "No such transfer <x>");
        List<MimeMessage> sent = MailSupport.awaitMail(mail, 2);
        assertThat(sent).extracting(CustomerStatusMailsIT::subject)
                .containsOnly("Payment for " + number(order) + " was not accepted — " + htmlMail.company());
        assertThat(body(sent.get(0))).contains("No such transfer &lt;x&gt;").doesNotContain("<x>");
    }

    @Test
    void rejectRepPaymentMailsNobody() throws Exception {
        long order = confirmedOrder();
        reject(pendingPayment(order, "USER", rep), "Typo in amount");
        assertThat(withSubject("Payment for " + number(order) + " was not accepted — " + htmlMail.company())).isEmpty();
        assertThat(MailSupport.sent(mail)).isEmpty();
    }
}
