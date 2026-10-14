package ph.thecoffeejunkie.crm.sales;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.approvals.RequiredApproval;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

/** approval.discountPercentMax is seeded at 15. */
@IntegrationTest
class ConfirmIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderService orders;
    @Autowired TransactionTemplate tx;

    SalesFixture f;
    long admin;
    long rep;
    long customer;
    long product;
    RequestPostProcessor asRep;

    @BeforeEach
    void seed() {
        f = new SalesFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        rep = f.user("rep", Role.SALES);
        customer = f.customer("acme", rep);
        product = f.product("A", "500.00", "100.00", "VATABLE");
        asRep = as(rep, Role.SALES);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    /** Another order of the same customer, confirmed, with the given total and due date. */
    private void otherConfirmedOrder(String total, LocalDate dueDate) throws Exception {
        long other = f.createdOrder(as(admin, Role.ADMIN), customer, line(product, "1"));
        jdbc.update("update sales_order set status = 'CONFIRMED', total = ?::numeric, due_date = ? where id = ?",
                total, dueDate, other);
    }

    private void approveAll(String problemBody) {
        List<Map<String, Object>> required = JsonPath.read(problemBody, "$.approvals");
        for (Map<String, Object> r : required) {
            jdbc.update("insert into approval_request (type, subject_type, subject_id, subject_version, requested_value, "
                    + "reason, status, requested_by) values (?, ?, ?, ?, '{}', 'ok', 'APPROVED', ?)",
                    r.get("approvalType"), r.get("subjectType"), ((Number) r.get("subjectId")).longValue(),
                    ((Number) r.get("subjectVersion")).longValue(), rep);
        }
    }

    @Test
    void discountOverMaxNeedsApproval() throws Exception {
        long id = f.createdOrder(asRep, customer, line(product, "1", "500.00", "16"));
        f.transition(asRep, id, "confirm", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("APPROVAL_REQUIRED"))
                .andExpect(jsonPath("$.approvals.length()").value(1))
                .andExpect(jsonPath("$.approvals[0].approvalType").value("DISCOUNT"))
                .andExpect(jsonPath("$.approvals[0].subjectType").value("ORDER"))
                .andExpect(jsonPath("$.approvals[0].requestedValue.maxDiscountPct").value(16));
    }

    @Test
    void belowCostNeedsApprovalWithoutLeakingCost() throws Exception {
        long p = f.product("C", "120.00", "100.00", "VATABLE");
        long id = f.createdOrder(asRep, customer, line(p, "1", "120.00", "20"));
        String body = SalesFixture.body(f.transition(asRep, id, "confirm", null)
                .andExpect(status().isUnprocessableContent()));
        List<String> types = JsonPath.read(body, "$.approvals[*].approvalType");
        assertThat(types).contains("BELOW_COST");
        List<List<String>> skus = JsonPath.read(body, "$.approvals[?(@.approvalType=='BELOW_COST')].requestedValue.skus");
        assertThat(skus.getFirst()).containsExactly(f.tag + "C");
        assertThat(body).doesNotContain("cost").doesNotContain("100.00");
    }

    @Test
    void exposureOverLimit() throws Exception {
        jdbc.update("update customer set credit_limit = 1000 where id = ?", customer);
        otherConfirmedOrder("600.00", null);
        long id = f.createdOrder(asRep, customer, line(product, "1"));
        f.transition(asRep, id, "confirm", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.approvals[0].approvalType").value("CREDIT_LIMIT"))
                .andExpect(jsonPath("$.approvals[0].requestedValue.exposure").value(1100.00))
                .andExpect(jsonPath("$.approvals[0].requestedValue.overdue").value(false));
    }

    @Test
    void cashCustomerSkipsLimitButOverdueBlocks() throws Exception {
        otherConfirmedOrder("9999.00", null);
        long id = f.createdOrder(asRep, customer, line(product, "1"));
        f.transition(asRep, id, "confirm", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        otherConfirmedOrder("100.00", LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(1));
        long second = f.createdOrder(asRep, customer, line(product, "1"));
        f.transition(asRep, second, "confirm", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.approvals[0].approvalType").value("CREDIT_LIMIT"))
                .andExpect(jsonPath("$.approvals[0].requestedValue.overdue").value(true))
                .andExpect(jsonPath("$.approvals[0].requestedValue.creditLimit").isEmpty());
    }

    @Test
    void allFailuresTogether() throws Exception {
        jdbc.update("update customer set credit_limit = 50 where id = ?", customer);
        long id = f.createdOrder(asRep, customer, line(product, "1", "110.00", "20"));
        f.transition(asRep, id, "confirm", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.approvals.length()").value(3));
    }

    @Test
    void approvedThenConfirms() throws Exception {
        long id = f.createdOrder(asRep, customer, line(product, "1", "500.00", "16"));
        approveAll(SalesFixture.body(f.transition(asRep, id, "confirm", null)));
        f.transition(asRep, id, "confirm", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty());
        assertThat(jdbc.queryForObject("select count(*) from audit_event where entity_type = 'ORDER' and entity_id = ? "
                + "and action = 'CONFIRMED'", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void editAfterApprovalNeedsNewApproval() throws Exception {
        long id = f.createdOrder(asRep, customer, line(product, "1", "500.00", "16"));
        approveAll(SalesFixture.body(f.transition(asRep, id, "confirm", null)));
        f.update(asRep, id, "", line(product, "2", "500.00", "16")).andExpect(status().isOk());
        f.transition(asRep, id, "confirm", null).andExpect(status().isUnprocessableContent());
    }

    @Test
    void adminConfirmsWithSelfApprovalAudit() throws Exception {
        long id = f.createdOrder(asRep, customer, line(product, "1", "500.00", "16"));
        f.transition(as(admin, Role.ADMIN), id, "confirm", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(jdbc.queryForObject("select count(*) from audit_event where entity_type = 'ORDER' and entity_id = ? "
                + "and action = 'SELF_APPROVED'", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void expiredQuote422() throws Exception {
        long id = f.createdOrder(asRep, customer, line(product, "1"));
        jdbc.update("update sales_order set status = 'QUOTED', quote_valid_until = ? where id = ?",
                LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(1), id);
        f.transition(asRep, id, "confirm", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("QUOTE_EXPIRED"));
    }

    @Test
    void confirmAsSystemIgnoresAdminPrincipal() throws Exception {
        long id = f.createdOrder(asRep, customer, line(product, "1", "500.00", "16"));
        f.setStatus(id, "QUOTED");
        SalesFixture.login(admin, Role.ADMIN);
        List<RequiredApproval> missing = tx.execute(s -> orders.confirmAsSystem(id));
        assertThat(missing).extracting(RequiredApproval::type).containsExactly(
                ph.thecoffeejunkie.crm.approvals.ApprovalType.DISCOUNT);
        assertThat(jdbc.queryForObject("select status from sales_order where id = ?", String.class, id))
                .isEqualTo("QUOTED");
    }
}
