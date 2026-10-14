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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

@IntegrationTest
class ReleaseCancelIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CacheManager caches;

    SalesFixture f;
    long admin;
    long rep;
    long customer;
    long order;
    RequestPostProcessor asRep;
    RequestPostProcessor asAdmin;

    @BeforeEach
    void seed() throws Exception {
        f = new SalesFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        rep = f.user("rep", Role.SALES);
        customer = f.customer("acme", rep);
        long product = f.product("A", "112.00", null, "VATABLE");
        asRep = as(rep, Role.SALES);
        asAdmin = as(admin, Role.ADMIN);
        order = f.createdOrder(asRep, customer, line(product, "1"));
        f.setStatus(order, "CONFIRMED");
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
        InvoicesIT.companySettings(jdbc, caches, "", "", "");
    }

    private void creditCustomer() {
        jdbc.update("update customer set credit_limit = 10000 where id = ?", customer);
    }

    private void payment(String status) {
        jdbc.update("insert into payment (order_id, status, method, cash_amount, received_on, submitted_by_type, "
                + "submitted_by_id) values (?, ?, 'CASH', 10, current_date, 'USER', ?)", order, status, rep);
    }

    @Test
    void creditCustomerReleasesUnpaid() throws Exception {
        creditCustomer();
        f.transition(asRep, order, "release", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.releasedAt").isNotEmpty());
    }

    @Test
    void cashCustomerUnpaidNeedsReleaseApproval() throws Exception {
        f.transition(asRep, order, "release", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.approvals[0].approvalType").value("CREDIT_LIMIT"))
                .andExpect(jsonPath("$.approvals[0].subjectType").value("ORDER_RELEASE"))
                .andExpect(jsonPath("$.approvals[0].requestedValue.reason").value("SHIP_BEFORE_PAYMENT"));
    }

    @Test
    void confirmCreditApprovalDoesNotRelease() throws Exception {
        String body = SalesFixture.body(mvc.perform(get("/api/orders/" + order).with(asRep)));
        long fingerprint = ((Number) JsonPath.read(body, "$.approvalVersion")).longValue();
        jdbc.update("insert into approval_request (type, subject_type, subject_id, subject_version, requested_value, "
                + "reason, status, requested_by) values ('CREDIT_LIMIT', 'ORDER', ?, ?, '{}', 'r', 'APPROVED', ?)",
                order, fingerprint, rep);
        f.transition(asRep, order, "release", null).andExpect(status().isUnprocessableContent());
        jdbc.update("insert into approval_request (type, subject_type, subject_id, subject_version, requested_value, "
                + "reason, status, requested_by) values ('CREDIT_LIMIT', 'ORDER_RELEASE', ?, ?, '{}', 'r', 'APPROVED', ?)",
                order, fingerprint, rep);
        f.transition(asRep, order, "release", null).andExpect(status().isOk());
    }

    @Test
    void cashCustomerPaidReleases() throws Exception {
        jdbc.update("update sales_order set settled_amount = total where id = ?", order);
        f.transition(asRep, order, "release", null).andExpect(status().isOk());
    }

    @Test
    void salesCannotFulfil() throws Exception {
        creditCustomer();
        f.transition(asRep, order, "release", null).andExpect(status().isOk());
        f.transition(asRep, order, "fulfil", null).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/" + order).with(asRep))
                .andExpect(jsonPath("$.allowedActions[?(@ == 'FULFIL')]").isEmpty());
    }

    @Test
    void adminFulfilsReleased() throws Exception {
        creditCustomer();
        f.transition(asRep, order, "release", null).andExpect(status().isOk());
        f.transition(asAdmin, order, "fulfil", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULFILLED"))
                .andExpect(jsonPath("$.fulfilledAt").isNotEmpty());
    }

    @Test
    void cancelWithVerifiedPayment422() throws Exception {
        payment("VERIFIED");
        f.transition(asRep, order, "cancel", "Customer changed mind").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("HAS_PAYMENTS"));
    }

    @Test
    void cancelRejectsPendingPayments() throws Exception {
        payment("PENDING");
        f.transition(asRep, order, "cancel", "Customer changed mind").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Customer changed mind"));
        assertThat(jdbc.queryForMap("select status, reject_reason from payment where order_id = ?", order))
                .containsEntry("status", "REJECTED").containsEntry("reject_reason", "Order cancelled");
    }

    @Test
    void cancelInvoicedNeedsVoidApprovalThenVoids() throws Exception {
        InvoicesIT.companySettings(jdbc, caches, "TCJ", "123", "Makati");
        mvc.perform(f.json(post("/api/orders/" + order + "/invoice"), "{\"version\":" + f.version(order) + "}")
                .header("Idempotency-Key", UUID.randomUUID().toString()).with(asRep)).andExpect(status().isOk());
        f.transition(asRep, order, "cancel", "Duplicate order").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.approvals[0].approvalType").value("INVOICE_VOID"))
                .andExpect(jsonPath("$.approvals[0].subjectType").value("INVOICE"));
        f.transition(asAdmin, order, "cancel", "Duplicate order").andExpect(status().isOk())
                .andExpect(jsonPath("$.activeInvoice").doesNotExist());
        assertThat(jdbc.queryForMap("select status, void_reason, replaced_by_id from invoice where order_id = ?", order))
                .containsEntry("status", "VOID").containsEntry("void_reason", "Duplicate order")
                .containsEntry("replaced_by_id", null);
    }

    @Test
    void cancelNeedsReason() throws Exception {
        f.transition(asRep, order, "cancel", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        f.transition(asRep, order, "cancel", "  ").andExpect(status().isBadRequest());
    }

    @Test
    void cancelledAllowsNothing() throws Exception {
        f.transition(asRep, order, "cancel", "No longer needed").andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions").isEmpty());
        f.transition(asRep, order, "release", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }
}
