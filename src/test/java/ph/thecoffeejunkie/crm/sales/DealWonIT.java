package ph.thecoffeejunkie.crm.sales;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
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

/** Deals (spec 2026-11-06) are won when a linked order is confirmed, by any confirm path. */
@IntegrationTest
class DealWonIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    SalesFixture f;
    long admin;
    long rep;
    long customer;
    long contact;
    long product;
    RequestPostProcessor asRep;

    @BeforeEach
    void seed() {
        f = new SalesFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        rep = f.user("rep", Role.SALES);
        customer = f.customer("acme", rep);
        jdbc.update("update customer set credit_limit = 100000 where id = ?", customer);
        contact = f.contact(customer, "cee", true);
        product = f.product("A", "112.00", "100.00", "VATABLE");
        f.stock(product, "50");
        asRep = as(rep, Role.SALES);
    }

    @AfterEach
    void cleanUp() {
        String customers = "select id from customer where display_name like '" + f.tag + "%'";
        jdbc.update("delete from deal_order where deal_id in (select id from deal where customer_id in (" + customers + "))");
        jdbc.update("delete from deal where customer_id in (" + customers + ")");
        f.cleanUp();
    }

    private long deal(String stage) {
        return jdbc.queryForObject("insert into deal (customer_id, rep_id, title, stage, lost_reason, created_by) "
                + "values (?, ?, 'Beans', ?, ?, ?) returning id", Long.class, customer, rep, stage,
                "LOST".equals(stage) ? "Cheaper elsewhere" : null, rep);
    }

    private void link(long dealId, long orderId) {
        jdbc.update("insert into deal_order (deal_id, order_id, linked_by) values (?, ?, ?)", dealId, orderId, rep);
    }

    private String stage(long dealId) {
        return jdbc.queryForObject("select stage from deal where id = ?", String.class, dealId);
    }

    private Long wonOrder(long dealId) {
        return jdbc.queryForObject("select won_order_id from deal where id = ?", Long.class, dealId);
    }

    private int wonAudits(long dealId) {
        return jdbc.queryForObject("select count(*) from audit_event where entity_type = 'DEAL' and entity_id = ? "
                + "and action = 'DEAL_WON'", Integer.class, dealId);
    }

    private long quoted(String... lines) throws Exception {
        long id = f.createdOrder(asRep, customer, lines);
        jdbc.update("update sales_order set status = 'QUOTED', quoted_at = now(), quote_valid_until = ? where id = ?",
                LocalDate.now(ZoneId.of("Asia/Manila")).plusDays(15), id);
        return id;
    }

    private void accept(long order) throws Exception {
        mvc.perform(f.json(post("/api/portal/orders/" + order + "/accept"), "{\"version\":" + f.version(order) + "}")
                .with(asContact(contact, customer))).andExpect(status().isOk());
    }

    @Test
    void staffConfirmWinsDeal() throws Exception {
        long d = deal("NEGOTIATION");
        long order = f.createdOrder(asRep, customer, line(product, "1"));
        link(d, order);
        f.transition(asRep, order, "confirm", null).andExpect(status().isOk());
        assertThat(stage(d)).isEqualTo("WON");
        assertThat(wonOrder(d)).isEqualTo(order);
        assertThat(wonAudits(d)).isEqualTo(1);
    }

    @Test
    void portalAcceptWinsDeal() throws Exception {
        long d = deal("PROPOSAL");
        long order = quoted(line(product, "1"));
        link(d, order);
        accept(order);
        assertThat(stage(d)).isEqualTo("WON");
    }

    @Test
    void approvalDecidedWinsDeal() throws Exception {
        long d = deal("PROPOSAL");
        long order = quoted(line(product, "1", "112.00", "20")); // needs DISCOUNT and BELOW_COST
        link(d, order);
        accept(order);
        assertThat(stage(d)).isEqualTo("PROPOSAL");
        List<Long> pending = jdbc.queryForList("select id from approval_request where subject_type = 'ORDER' "
                + "and subject_id = ? and status = 'PENDING' order by id", Long.class, order);
        for (long a : pending) {
            mvc.perform(f.json(post("/api/approvals/" + a + "/decision"), "{\"approve\":true,\"note\":\"ok\"}")
                    .with(as(admin, Role.ADMIN))).andExpect(status().isOk());
        }
        assertThat(stage(d)).isEqualTo("WON");
        assertThat(wonOrder(d)).isEqualTo(order);
    }

    @Test
    void secondConfirmLeavesItWonByTheFirst() throws Exception {
        long d = deal("NEGOTIATION");
        long first = f.createdOrder(asRep, customer, line(product, "1"));
        long second = f.createdOrder(asRep, customer, line(product, "2"));
        link(d, first);
        link(d, second);
        f.transition(asRep, first, "confirm", null).andExpect(status().isOk());
        f.transition(asRep, second, "confirm", null).andExpect(status().isOk());
        assertThat(wonOrder(d)).isEqualTo(first);
        assertThat(wonAudits(d)).isEqualTo(1);
    }

    @Test
    void lostDealIsNotWon() throws Exception {
        long d = deal("LOST");
        long order = f.createdOrder(asRep, customer, line(product, "1"));
        link(d, order);
        f.transition(asRep, order, "confirm", null).andExpect(status().isOk());
        assertThat(stage(d)).isEqualTo("LOST");
        assertThat(wonAudits(d)).isZero();
    }

    @Test
    void reviseDoesNotWin() throws Exception {
        long order = f.createdOrder(asRep, customer, line(product, "3"));
        f.transition(asRep, order, "confirm", null).andExpect(status().isOk());
        long d = deal("NEGOTIATION");
        link(d, order); // linked after confirming, so only a new confirm event could win it
        f.update(asRep, order, ReviseIT.REASON, line(product, "2")).andExpect(status().isOk());
        assertThat(stage(d)).isEqualTo("NEGOTIATION");
    }

    @Test
    void unlinkedConfirmChangesNothing() throws Exception {
        long d = deal("DISCOVERY");
        long order = f.createdOrder(asRep, customer, line(product, "1"));
        f.transition(asRep, order, "confirm", null).andExpect(status().isOk());
        assertThat(stage(d)).isEqualTo("DISCOVERY");
    }
}
