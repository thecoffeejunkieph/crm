package ph.thecoffeejunkie.crm.sales;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

@IntegrationTest
class ReviseIT {

    static final String REASON = "\"reason\":\"Customer wants fewer\"";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CacheManager caches;

    SalesFixture f;
    long admin;
    long rep;
    long customer;
    long product;
    long order;
    RequestPostProcessor asRep;
    RequestPostProcessor asAdmin;

    @BeforeEach
    void seed() throws Exception {
        f = new SalesFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        rep = f.user("rep", Role.SALES);
        customer = f.customer("acme", rep);
        jdbc.update("update customer set credit_limit = 100000 where id = ?", customer);
        product = f.product("A", "112.00", null, "VATABLE");
        asRep = as(rep, Role.SALES);
        asAdmin = as(admin, Role.ADMIN);
        order = f.createdOrder(asRep, customer, line(product, "3")); // 336.00
        f.setStatus(order, "CONFIRMED");
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
        InvoicesIT.companySettings(jdbc, caches, "", "", "");
    }

    private int revision() {
        return jdbc.queryForObject("select revision from sales_order where id = ?", Integer.class, order);
    }

    @Test
    void reviseSnapshotsAndIncrements() throws Exception {
        f.update(asRep, order, REASON, line(product, "2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totals.total").value(224.00));
        mvc.perform(get("/api/orders/" + order + "/revisions").with(asRep)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].revision").value(0))
                .andExpect(jsonPath("$[0].reason").value("Customer wants fewer"))
                .andExpect(jsonPath("$[0].snapshot.lines[0].qty").value(3))
                .andExpect(jsonPath("$[0].snapshot.lines[0].sku").value(f.tag + "A"))
                .andExpect(jsonPath("$[0].snapshot.totals.total").value(336.00));
        assertThat(jdbc.queryForObject("select count(*) from audit_event where entity_type = 'ORDER' and entity_id = ? "
                + "and action = 'REVISED'", Integer.class, order)).isEqualTo(1);
    }

    @Test
    void reviseNeedsReason() throws Exception {
        f.update(asRep, order, "", line(product, "2")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        assertThat(revision()).isZero();
    }

    @Test
    void reviseRerunsChecksAndSavesNothingOnFailure() throws Exception {
        f.update(asRep, order, REASON, line(product, "3", "112.00", "20")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.approvals[0].approvalType").value("DISCOUNT"));
        assertThat(revision()).isZero();
        assertThat(jdbc.queryForObject("select discount_pct from order_line where order_id = ?", String.class, order))
                .isEqualTo("0.00");
        assertThat(jdbc.queryForObject("select count(*) from order_revision where order_id = ?", Integer.class, order))
                .isZero();
    }

    @Test
    void approvedRevisionApplies() throws Exception {
        String problem = SalesFixture.body(f.update(asRep, order, REASON, line(product, "3", "112.00", "20")));
        List<Map<String, Object>> required = JsonPath.read(problem, "$.approvals");
        for (Map<String, Object> r : required) {
            jdbc.update("insert into approval_request (type, subject_type, subject_id, subject_version, requested_value, "
                    + "reason, status, requested_by) values (?, ?, ?, ?, '{}', 'ok', 'APPROVED', ?)",
                    r.get("approvalType"), r.get("subjectType"), ((Number) r.get("subjectId")).longValue(),
                    ((Number) r.get("subjectVersion")).longValue(), rep);
        }
        f.update(asRep, order, REASON, line(product, "3", "112.00", "20")).andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1));
    }

    @Test
    void invoicedReviseNeedsVoidThenReissues() throws Exception {
        InvoicesIT.companySettings(jdbc, caches, "TCJ", "123", "Makati");
        String first = SalesFixture.body(mvc.perform(f.json(post("/api/orders/" + order + "/invoice"),
                        "{\"version\":" + f.version(order) + "}")
                .header("Idempotency-Key", UUID.randomUUID().toString()).with(asRep)).andExpect(status().isOk()));
        String oldNumber = JsonPath.read(first, "$.number");
        jdbc.update("update sales_order set settled_amount = 50 where id = ?", order);

        String problem = SalesFixture.body(f.update(asRep, order, REASON, line(product, "2"))
                .andExpect(status().isUnprocessableContent()));
        List<String> types = JsonPath.read(problem, "$.approvals[*].approvalType");
        assertThat(types).contains("INVOICE_VOID");

        f.update(asAdmin, order, REASON, line(product, "2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeInvoice.number").value(org.hamcrest.Matchers.not(oldNumber)))
                .andExpect(jsonPath("$.activeInvoice.total").value(224.00))
                .andExpect(jsonPath("$.balance").value(174.00));
        assertThat(jdbc.queryForObject("select status from invoice where number = ?", String.class, oldNumber))
                .isEqualTo("VOID");
        assertThat(jdbc.queryForObject("select order_revision from invoice where order_id = ? and status = 'ISSUED'",
                Integer.class, order)).isEqualTo(1);
    }

    @Test
    void reviseBelowSettled422() throws Exception {
        jdbc.update("update sales_order set settled_amount = 300 where id = ?", order);
        f.update(asRep, order, REASON, line(product, "1")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("OVERPAYMENT"));
        assertThat(revision()).isZero();
        assertThat(jdbc.queryForObject("select total from sales_order where id = ?", String.class, order))
                .isEqualTo("336.00");
    }

    @Test
    void fulfilledCannotBeRevised() throws Exception {
        f.setStatus(order, "FULFILLED");
        f.update(asRep, order, REASON, line(product, "2")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }
}
