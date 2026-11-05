package ph.thecoffeejunkie.crm.deals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.deals.DealsFixture.as;

@IntegrationTest
class DealLinkIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    DealsFixture f;
    long rep;
    long other;
    long acme;
    long bolt;
    long deal;
    RequestPostProcessor asRep;

    @BeforeEach
    void seed() {
        f = new DealsFixture(jdbc, mvc);
        rep = f.user("rep", Role.SALES);
        other = f.user("oth", Role.SALES);
        acme = f.customer("Acme", rep);
        bolt = f.customer("Bolt", other);
        deal = f.deal(acme, rep, "Beans", "PROPOSAL", null, null);
        asRep = as(rep, Role.SALES);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private ResultActions link(long dealId, long orderId) throws Exception {
        return mvc.perform(f.json(post("/api/deals/" + dealId + "/orders"), "{\"orderId\":" + orderId + "}").with(asRep));
    }

    private ResultActions unlink(long dealId, long orderId) throws Exception {
        return mvc.perform(f.json(post("/api/deals/" + dealId + "/orders/" + orderId + "/unlink"), "{}").with(asRep));
    }

    private int links(long orderId) {
        return jdbc.queryForObject("select count(*) from deal_order where order_id = ?", Integer.class, orderId);
    }

    @Test
    void linksDraftAndQuotedOrdersOfSameCustomer() throws Exception {
        long draft = f.order(acme, "DRAFT");
        long quoted = f.order(acme, "QUOTED");
        link(deal, draft).andExpect(status().isOk());
        link(deal, quoted).andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[*].id", contains((int) draft, (int) quoted)))
                .andExpect(jsonPath("$.orders[1].number").value(f.orderNumber(quoted)))
                .andExpect(jsonPath("$.orders[1].status").value("QUOTED"))
                .andExpect(jsonPath("$.orders[1].total").value(1500));
        assertThat(f.audits("DEAL_ORDER_LINKED", deal)).isEqualTo(2);
    }

    @Test
    void relinkIsNoop() throws Exception {
        long o = f.order(acme, "DRAFT");
        link(deal, o).andExpect(status().isOk());
        link(deal, o).andExpect(status().isOk());
        assertThat(links(o)).isEqualTo(1);
        assertThat(f.audits("DEAL_ORDER_LINKED", deal)).isEqualTo(1);
    }

    @Test
    void rejectsOtherCustomersOrderWithoutNamingIt() throws Exception {
        long theirs = f.order(bolt, "QUOTED");
        link(deal, theirs).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DEAL_ORDER_MISMATCH"))
                .andExpect(content().string(not(containsString(f.orderNumber(theirs)))));
        assertThat(links(theirs)).isZero();
    }

    @Test
    void rejectsOrderOnAnotherDealConfirmedOrUnknown() throws Exception {
        long elsewhere = f.order(acme, "QUOTED");
        long second = f.deal(acme, rep, "Other", "DISCOVERY", null, null);
        f.link(second, elsewhere);
        link(deal, elsewhere).andExpect(jsonPath("$.code").value("DEAL_ORDER_MISMATCH"));
        link(deal, f.order(acme, "CONFIRMED")).andExpect(jsonPath("$.code").value("DEAL_ORDER_MISMATCH"));
        link(deal, 999_999_999L).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DEAL_ORDER_MISMATCH"));
    }

    @Test
    void unlinkRules() throws Exception {
        long o = f.order(acme, "QUOTED");
        f.link(deal, o);
        unlink(deal, o).andExpect(status().isOk()).andExpect(jsonPath("$.orders").isEmpty());
        assertThat(f.audits("DEAL_ORDER_UNLINKED", deal)).isEqualTo(1);
        unlink(deal, o).andExpect(status().isOk());
        assertThat(f.audits("DEAL_ORDER_UNLINKED", deal)).isEqualTo(1);
        long lost = f.deal(acme, rep, "Lost", "LOST", null, null);
        long o2 = f.order(acme, "DRAFT");
        f.link(lost, o2);
        unlink(lost, o2).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEAL_CLOSED"));
        link(lost, f.order(acme, "DRAFT")).andExpect(jsonPath("$.code").value("DEAL_CLOSED"));
    }

    @Test
    void byOrderLookupIsScoped() throws Exception {
        long o = f.order(acme, "QUOTED");
        f.link(deal, o);
        mvc.perform(get("/api/deals/by-order/" + o).with(asRep)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deal)).andExpect(jsonPath("$.title").value(f.tag + "Beans"));
        mvc.perform(get("/api/deals/by-order/" + f.order(acme, "DRAFT")).with(asRep)).andExpect(status().isNoContent());
        mvc.perform(get("/api/deals/by-order/" + o).with(as(other, Role.SALES))).andExpect(status().isNoContent());
    }
}
