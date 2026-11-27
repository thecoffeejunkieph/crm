package ph.thecoffeejunkie.crm.sales;

import com.jayway.jsonpath.JsonPath;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

/** O-05: GET /api/insights/today. */
@IntegrationTest
class TodayQueueIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    SalesFixture f;
    long rep;
    long customer;
    long order;
    long draft;
    long payment;
    long approval;
    RequestPostProcessor asRep;

    @BeforeEach
    void seed() throws Exception {
        f = new SalesFixture(jdbc, mvc);
        rep = f.user("rep", Role.SALES);
        asRep = as(rep, Role.SALES);
        customer = f.customer("acme", rep);
        long product = f.product("A", "112.00", null, "VATABLE");

        order = f.createdOrder(asRep, customer, line(product, "3")); // 336.00
        f.setStatus(order, "CONFIRMED");
        payment = jdbc.queryForObject("insert into payment (order_id, status, method, cash_amount, received_on, "
                + "submitted_by_type, submitted_by_id) values (?, 'PENDING', 'BANK_TRANSFER', 100, current_date, 'USER', ?) "
                + "returning id", Long.class, order, rep);
        draft = f.createdOrder(asRep, customer, line(product, "1"));
        jdbc.update("update sales_order set notes = 'Requested via portal by Ana.' where id = ?", draft);
        f.createdOrder(asRep, customer, line(product, "1")); // a rep's own draft: not a quote request
        jdbc.update("insert into follow_up (customer_id, assignee_id, title, due_date, created_by) values (?, ?, ?, ?, ?)",
                customer, rep, "Call about restock", LocalDate.now().minusDays(1), rep);
        jdbc.update("insert into follow_up (customer_id, assignee_id, title, due_date, created_by) values (?, ?, ?, ?, ?)",
                customer, rep, "Next week", LocalDate.now().plusDays(7), rep);
        approval = jdbc.queryForObject("insert into approval_request (type, subject_type, subject_id, subject_version, "
                + "requested_value, reason, status, requested_by) values ('DISCOUNT', 'ORDER', ?, 0, '{}'::jsonb, "
                + "'Loyal customer', 'PENDING', ?) returning id", Long.class, order, rep);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from follow_up where customer_id = ?", customer);
        f.cleanUp();
    }

    @Test
    void aRepSeesTheirCustomersWork() throws Exception {
        mvc.perform(get("/api/insights/today").with(asRep)).andExpect(status().isOk())
                .andExpect(jsonPath("$.toVerify.total").value(1))
                .andExpect(jsonPath("$.toVerify.items[0].id").value(payment))
                .andExpect(jsonPath("$.toVerify.items[0].orderId").value(order))
                .andExpect(jsonPath("$.toVerify.items[0].detail").value("Bank Transfer"))
                .andExpect(jsonPath("$.toVerify.items[0].amount").value(100.00))
                .andExpect(jsonPath("$.toRelease.total").value(1))
                .andExpect(jsonPath("$.toRelease.items[0].detail").value("Not fully paid"))
                .andExpect(jsonPath("$.quoteRequests.total").value(1))
                .andExpect(jsonPath("$.quoteRequests.items[0].orderId").value(draft))
                .andExpect(jsonPath("$.followUps.total").value(1))
                .andExpect(jsonPath("$.followUps.items[0].title").value("Call about restock"))
                .andExpect(jsonPath("$.followUps.items[0].orderId").isEmpty())
                .andExpect(jsonPath("$.approvals.total").value(1))
                .andExpect(jsonPath("$.approvals.items[0].title").value("Discount approval"))
                .andExpect(jsonPath("$.approvals.items[0].orderId").value(order));
    }

    @Test
    void anotherRepSeesNoneOfIt() throws Exception {
        long other = f.user("other", Role.SALES);
        mvc.perform(get("/api/insights/today").with(as(other, Role.SALES))).andExpect(status().isOk())
                .andExpect(jsonPath("$.toVerify.total").value(0))
                .andExpect(jsonPath("$.toRelease.total").value(0))
                .andExpect(jsonPath("$.quoteRequests.total").value(0))
                .andExpect(jsonPath("$.followUps.total").value(0))
                .andExpect(jsonPath("$.approvals.total").value(0));
    }

    @Test
    void anAdminSeesTheCompanysApprovalsAndPayments() throws Exception {
        long admin = f.user("boss", Role.ADMIN);
        String body = mvc.perform(get("/api/insights/today").with(as(admin, Role.ADMIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.followUps.total").value(0)) // follow-ups are the caller's own
                .andReturn().getResponse().getContentAsString();
        // company-wide: other tests' rows may be there too, and only the oldest few are listed
        assertThat(JsonPath.<Integer>read(body, "$.approvals.total")).isPositive();
        assertThat(JsonPath.<Integer>read(body, "$.toVerify.total")).isPositive();
        List<Integer> shown = JsonPath.read(body, "$.approvals.items[*].id");
        assertThat(shown.size()).isBetween(1, 5);
    }

    @Test
    void warehouseCantSeeIt() throws Exception {
        long wh = f.user("wh", Role.WAREHOUSE);
        mvc.perform(get("/api/insights/today").with(as(wh, Role.WAREHOUSE))).andExpect(status().isForbidden());
    }
}
