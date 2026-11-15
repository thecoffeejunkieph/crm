package ph.thecoffeejunkie.crm.sales;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.approvals.ApprovalDecided;
import ph.thecoffeejunkie.crm.approvals.ApprovalStatus;
import ph.thecoffeejunkie.crm.approvals.ApprovalType;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.asContact;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

@IntegrationTest
class AutoConfirmIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEventPublisher events;
    @Autowired TransactionTemplate tx;

    SalesFixture f;
    long admin;
    long rep;
    long customer;
    long contact;
    long order;

    @BeforeEach
    void seed() throws Exception {
        f = new SalesFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        rep = f.user("rep", Role.SALES);
        customer = f.customer("acme", rep);
        jdbc.update("update customer set credit_limit = 100000 where id = ?", customer);
        contact = f.contact(customer, "cee", true);
        long product = f.product("A", "112.00", "100.00", "VATABLE");
        // 20% off: needs DISCOUNT (over 15) and BELOW_COST (89.60 < 100.00).
        order = f.createdOrder(as(rep, Role.SALES), customer, line(product, "1", "112.00", "20"));
        jdbc.update("update sales_order set status = 'QUOTED', quoted_at = now(), quote_valid_until = ? where id = ?",
                LocalDate.now(ZoneId.of("Asia/Manila")).plusDays(15), order);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private void accept() throws Exception {
        mvc.perform(f.json(post("/api/portal/orders/" + order + "/accept"), "{\"version\":" + f.version(order) + "}")
                .with(asContact(contact, customer))).andExpect(status().isOk());
    }

    private String orderStatus() {
        return jdbc.queryForObject("select status from sales_order where id = ?", String.class, order);
    }

    private int confirmedAudits() {
        return jdbc.queryForObject("select count(*) from audit_event where entity_type = 'ORDER' and entity_id = ? "
                + "and action = 'CONFIRMED' and actor_type = 'SYSTEM'", Integer.class, order);
    }

    private void decide(long approvalId) throws Exception {
        mvc.perform(f.json(post("/api/approvals/" + approvalId + "/decision"), "{\"approve\":true,\"note\":\"ok\"}")
                .with(as(admin, Role.ADMIN))).andExpect(status().isOk());
    }

    @Test
    void approvingLastConfirms() throws Exception {
        accept();
        List<Long> pending = jdbc.queryForList("select id from approval_request where subject_type = 'ORDER' "
                + "and subject_id = ? and status = 'PENDING' order by id", Long.class, order);
        assertThat(pending).hasSize(2);
        decide(pending.get(0));
        assertThat(orderStatus()).isEqualTo("QUOTED");
        decide(pending.get(1));
        assertThat(orderStatus()).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("select unit_cost from order_line where order_id = ?",
                java.math.BigDecimal.class, order)).isEqualByComparingTo("100.00");
        assertThat(confirmedAudits()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notification where recipient_user_id = ? "
                + "and type = 'ORDER_CONFIRMED'", Integer.class, rep)).isEqualTo(1);
    }

    @Test
    void listenerIdempotent() throws Exception {
        accept();
        jdbc.update("update approval_request set status = 'APPROVED' where subject_type = 'ORDER' and subject_id = ?",
                order);
        long version = jdbc.queryForObject("select subject_version from approval_request where subject_id = ? limit 1",
                Long.class, order);
        var event = new ApprovalDecided(1, ApprovalType.DISCOUNT, "ORDER", order, version, ApprovalStatus.APPROVED);
        tx.executeWithoutResult(s -> events.publishEvent(event));
        tx.executeWithoutResult(s -> events.publishEvent(event));
        assertThat(orderStatus()).isEqualTo("CONFIRMED");
        assertThat(confirmedAudits()).isEqualTo(1);
    }

    @Test
    void editedOrderNotAutoConfirmed() throws Exception {
        accept();
        long oldVersion = jdbc.queryForObject("select subject_version from approval_request where subject_id = ? limit 1",
                Long.class, order);
        jdbc.update("update approval_request set status = 'APPROVED' where subject_type = 'ORDER' and subject_id = ?",
                order);
        jdbc.update("update order_line set qty = 2 where order_id = ?", order);
        tx.executeWithoutResult(s -> events.publishEvent(
                new ApprovalDecided(1, ApprovalType.DISCOUNT, "ORDER", order, oldVersion, ApprovalStatus.APPROVED)));
        assertThat(orderStatus()).isEqualTo("QUOTED");
        assertThat(confirmedAudits()).isZero();
    }
}
