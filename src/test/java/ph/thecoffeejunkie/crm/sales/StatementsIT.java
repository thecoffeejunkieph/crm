package ph.thecoffeejunkie.crm.sales;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.MailIntegrationTest;
import ph.thecoffeejunkie.crm.MailSupport;
import ph.thecoffeejunkie.crm.shared.Role;

import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.asContact;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

@MailIntegrationTest
class StatementsIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JavaMailSender mail;
    @Autowired StringRedisTemplate redis;
    @Autowired OverdueReminders reminders;
    @Autowired @Qualifier("applicationTaskExecutor") ThreadPoolTaskExecutor executor;

    SalesFixture f;
    long rep;
    long customer;
    long product;
    long contact;
    RequestPostProcessor asRep;
    final LocalDate today = LocalDate.now(SalesTime.MANILA);

    @BeforeEach
    void seed() {
        MailSupport.stub(mail);
        f = new SalesFixture(jdbc, mvc);
        rep = f.user("rep", Role.SALES);
        customer = f.customer("acme", rep);
        product = f.product("A", "112.00", null, "VATABLE");
        asRep = as(rep, Role.SALES);
        contact = f.contact(customer, "a", true);
        f.contact(customer, "b", true);
        redis.delete("lock:overdue-reminders:" + today);
    }

    @AfterEach
    void cleanUp() {
        MailSupport.awaitIdle(executor);
        redis.delete("lock:overdue-reminders:" + today);
        f.cleanUp();
    }

    /** A confirmed 336.00 order (3 Ã— 112) due on the given date, partly paid. */
    private long openOrder(long customerId, LocalDate due, String settled) throws Exception {
        long order = f.createdOrder(asRep, customerId, line(product, "3"));
        f.setStatus(order, "CONFIRMED");
        jdbc.update("update sales_order set due_date = ?, settled_amount = ? where id = ?", due, new java.math.BigDecimal(settled), order);
        return order;
    }

    @Test
    void agingReportBucketsByDaysLate() throws Exception {
        openOrder(customer, today.plusDays(5), "0");      // current
        openOrder(customer, today.minusDays(10), "36");   // 1-30, balance 300
        openOrder(customer, today.minusDays(95), "0");    // over 90
        long paid = openOrder(customer, today.minusDays(40), "336"); // settled: not open
        mvc.perform(get("/api/reports/ar-aging").with(asRep)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].customerId").value(customer))
                .andExpect(jsonPath("$[0].aging.current").value(336.00))
                .andExpect(jsonPath("$[0].aging.days1to30").value(300.00))
                .andExpect(jsonPath("$[0].aging.days31to60").value(0))
                .andExpect(jsonPath("$[0].aging.over90").value(336.00))
                .andExpect(jsonPath("$[0].aging.total").value(972.00));
        assertThat(paid).isPositive();
    }

    @Test
    void agingReportIsScopedToTheRepsCustomers() throws Exception {
        long other = f.user("other", Role.SALES);
        openOrder(customer, today.minusDays(3), "0");
        mvc.perform(get("/api/reports/ar-aging").with(as(other, Role.SALES))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void statementPdfForStaffAndPortal() throws Exception {
        openOrder(customer, today.minusDays(3), "0");
        byte[] staff = mvc.perform(get("/api/customers/" + customer + "/statement.pdf").with(asRep))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(staff, 0, 4)).isEqualTo("%PDF");        mvc.perform(get("/api/portal/statement.pdf").with(asContact(contact, customer)))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
        long other = f.user("other", Role.SALES);
        mvc.perform(get("/api/customers/" + customer + "/statement.pdf").with(as(other, Role.SALES)))
                .andExpect(status().isNotFound());
    }

    @Test
    void remindersGoOutOnMilestoneDaysOnceADay() throws Exception {
        long order = openOrder(customer, today.minusDays(7), "0");
        long quiet = f.customer("quiet", rep);
        f.contact(quiet, "q", true);
        openOrder(quiet, today.minusDays(8), "0"); // day 8: not a milestone

        assertThat(reminders.sendFor(today)).isEqualTo(1);
        List<MimeMessage> sent = MailSupport.sent(mail);
        assertThat(sent).extracting(CustomerStatusMailsIT::to)
                .containsExactlyInAnyOrder(f.contactEmail("a"), f.contactEmail("b"));
        String number = jdbc.queryForObject("select number from sales_order where id = ?", String.class, order);
        assertThat(CustomerStatusMailsIT.body(sent.get(0))).contains(number).contains("336.00");
        assertThat(CustomerStatusMailsIT.attachments(sent.get(0))).containsExactly("statement-" + today + ".pdf");

        assertThat(reminders.sendFor(today)).isZero(); // same date again: locked
        assertThat(MailSupport.sent(mail)).hasSize(2);
    }

    @Test
    void milestoneDays() {
        assertThat(List.of(0L, 1L, 2L, 7L, 14L, 15L, 30L, 45L, 60L, 90L).stream().filter(OverdueReminders::milestone))
                .containsExactly(1L, 7L, 14L, 30L, 60L, 90L);
    }
}
