package ph.thecoffeejunkie.crm.purchasing;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.as;

@IntegrationTest
class SendPurchaseOrderIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ApplicationContext ctx;

    PurchasingFixture f;
    RequestPostProcessor admin;
    long wh;
    long product;

    @BeforeEach
    void seed() {
        f = new PurchasingFixture(jdbc, tx, ctx);
        admin = as(f.inv.admin("adm"), Role.ADMIN);
        wh = f.inv.warehouse("A");
        product = f.inv.product("P1", "PC");
    }

    @AfterEach
    void cleanUp() {
        // purchase_order.approval_request_id references the approval, and the fixture deletes the users it points at
        jdbc.update("update purchase_order set approval_request_id = null where supplier_id in "
                + "(select id from supplier where code like ?)", f.inv.tag + "%");
        jdbc.update("delete from approval_request where requested_by in (select id from app_user where email like ?)",
                f.inv.tag + "%");
        f.cleanUp();
    }

    /** A DRAFT PO of 10 x 10.00 = 100.00 for a supplier with the given limit and opening balance. */
    private long draft(String limit, String opening) throws Exception {
        long s = f.supplier("S" + System.nanoTime(), limit == null ? null : new BigDecimal(limit));
        jdbc.update("update supplier set opening_balance = ? where id = ?", new BigDecimal(opening), s);
        String json = "{\"supplierId\":" + s + ",\"warehouseId\":" + wh + ",\"lines\":[{\"productId\":" + product
                + ",\"qty\":10,\"unitCost\":10.00}]}";
        String res = mvc.perform(post("/api/purchase-orders").contentType("application/json").content(json)
                .with(csrf(mvc)).with(admin)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    private ResultActions send(long id) throws Exception {
        return mvc.perform(post("/api/purchase-orders/" + id + "/send").with(csrf(mvc)).with(admin));
    }

    private String poStatus(long id) {
        return jdbc.queryForObject("select status from purchase_order where id = ?", String.class, id);
    }

    private long approvalOf(long po) {
        return jdbc.queryForObject("select approval_request_id from purchase_order where id = ?", Long.class, po);
    }

    private void decide(long approval, boolean approve, String note) throws Exception {
        mvc.perform(post("/api/approvals/" + approval + "/decision").contentType("application/json")
                .content("{\"approve\":" + approve + ",\"note\":\"" + note + "\"}").with(csrf(mvc)).with(admin))
                .andExpect(status().isOk());
    }

    @Test
    void withinLimitGoesSent() throws Exception {
        long id = draft("1000", "0");
        send(id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.sentAt").isNotEmpty());
    }

    @Test
    void exactlyAtLimitIsWithin() throws Exception {
        long id = draft("150", "50"); // 50 + 0 + 100 = 150
        mvc.perform(get("/api/purchase-orders/" + id + "/credit-check").with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.exposure").value(150.00)).andExpect(jsonPath("$.over").value(0))
                .andExpect(jsonPath("$.needsApproval").value(false));
        send(id).andExpect(jsonPath("$.status").value("SENT"));
    }

    @Test
    void noLimitGoesSent() throws Exception {
        long id = draft(null, "999999");
        mvc.perform(get("/api/purchase-orders/" + id + "/credit-check").with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").doesNotExist()).andExpect(jsonPath("$.needsApproval").value(false));
        send(id).andExpect(jsonPath("$.status").value("SENT"));
    }

    @Test
    void overLimitCreatesApprovalAndPending() throws Exception {
        long id = draft("120", "50"); // exposure 150, over 30
        send(id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.approvalRequestId").isNumber());
        long approval = approvalOf(id);
        assertThat(jdbc.queryForObject("select type from approval_request where id = ?", String.class, approval))
                .isEqualTo("PO_OVER_CREDIT");
        assertThat(jdbc.queryForObject("select requested_value->>'total' from approval_request where id = ?",
                String.class, approval)).startsWith("100");
        assertThat(jdbc.queryForObject("select requested_value->>'over' from approval_request where id = ?",
                String.class, approval)).startsWith("30");
    }

    @Test
    void approvedMovesToSent() throws Exception {
        long id = draft("120", "50");
        send(id).andExpect(status().isOk());
        decide(approvalOf(id), true, "ok");
        assertThat(poStatus(id)).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("select sent_at is not null from purchase_order where id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void rejectedReturnsToDraftWithReason() throws Exception {
        long id = draft("120", "50");
        send(id).andExpect(status().isOk());
        decide(approvalOf(id), false, "too much");
        assertThat(poStatus(id)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("select rejection_reason from purchase_order where id = ?", String.class, id))
                .isEqualTo("too much");
        assertThat(jdbc.queryForObject("select approval_request_id from purchase_order where id = ?", Long.class, id)).isNull();
        // a later successful send clears the reason
        jdbc.update("update supplier set credit_limit = 1000 where id = (select supplier_id from purchase_order where id = ?)", id);
        send(id).andExpect(jsonPath("$.status").value("SENT")).andExpect(jsonPath("$.rejectionReason").doesNotExist());
    }

    @Test
    void decisionAfterCancelChangesNothing() throws Exception {
        long id = draft("120", "50");
        send(id).andExpect(status().isOk());
        long approval = approvalOf(id);
        // cancel the PO behind the approval's back, then decide the (still pending) approval
        jdbc.update("update purchase_order set status = 'CANCELLED' where id = ?", id);
        decide(approval, true, "late");
        assertThat(poStatus(id)).isEqualTo("CANCELLED");
    }

    @Test
    void sendWithNoLinesIs400() throws Exception {
        long id = draft("1000", "0");
        jdbc.update("delete from purchase_order_line where po_id = ?", id);
        send(id).andExpect(status().isBadRequest());
        assertThat(poStatus(id)).isEqualTo("DRAFT");
    }

    @Test
    void cancelPendingCancelsApproval() throws Exception {
        long id = draft("120", "50");
        send(id).andExpect(status().isOk());
        long approval = approvalOf(id);
        mvc.perform(post("/api/purchase-orders/" + id + "/cancel").with(csrf(mvc)).with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("select status from approval_request where id = ?", String.class, approval))
                .isEqualTo("CANCELLED");
    }

    @Test
    void sendToArchivedSupplierIs400() throws Exception {
        long id = draft("1000", "0");
        jdbc.update("update supplier set archived_at = now() where id = (select supplier_id from purchase_order where id = ?)", id);
        send(id).andExpect(status().isBadRequest());
        assertThat(poStatus(id)).isEqualTo("DRAFT");
    }

    @Test
    void sendTwiceIsConflict() throws Exception {
        long id = draft("1000", "0");
        send(id).andExpect(status().isOk());
        send(id).andExpect(status().isConflict());
    }

    /** A DRAFT PO of qty x cost for a fresh supplier (limit 2,500,000) on the given downpayment percent. */
    private long[] draftWithTerm(String percent, String qty, String cost) throws Exception {
        long s = f.supplier("D" + System.nanoTime(), new BigDecimal("2500000"));
        jdbc.update("update supplier set downpayment_percent = ? where id = ?", new BigDecimal(percent), s);
        String json = "{\"supplierId\":" + s + ",\"warehouseId\":" + wh + ",\"lines\":[{\"productId\":" + product
                + ",\"qty\":" + qty + ",\"unitCost\":" + cost + "}]}";
        String res = mvc.perform(post("/api/purchase-orders").contentType("application/json").content(json)
                .with(csrf(mvc)).with(admin)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new long[]{((Number) JsonPath.read(res, "$.id")).longValue(), s};
    }

    private String pay(long supplier, long po, String amount) throws Exception {
        return mvc.perform(post("/api/suppliers/" + supplier + "/payments").contentType("application/json")
                .content("{\"amount\":" + amount + ",\"paidOn\":\"2026-11-20\",\"purchaseOrderId\":" + po + "}")
                .with(csrf(mvc)).with(admin)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void sendRefusedUntilDownpaymentPaid() throws Exception {
        long[] d = draftWithTerm("50", "10", "100.00");
        String number = jdbc.queryForObject("select number from purchase_order where id = ?", String.class, d[0]);
        send(d[0]).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.detail")
                .value(number + " needs a downpayment of ₱500.00 before sending; ₱0.00 recorded."));
        pay(d[1], d[0], "500");
        send(d[0]).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.downpaymentRequired").value(500.00))
                .andExpect(jsonPath("$.downpaymentPaid").value(500.00));
        mvc.perform(get("/api/suppliers/" + d[1]).with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.credit.available").value(2499500.00));
    }

    @Test
    void zeroPercentSendsAsBefore() throws Exception {
        long[] d = draftWithTerm("0", "10", "100.00");
        send(d[0]).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.downpaymentRequired").value(0));
    }

    @Test
    void deletingDownpaymentAfterSendKeepsSent() throws Exception {
        long[] d = draftWithTerm("50", "10", "100.00");
        long paymentId = ((Number) JsonPath.read(pay(d[1], d[0], "500"), "$.id")).longValue();
        send(d[0]).andExpect(status().isOk());
        mvc.perform(delete("/api/suppliers/" + d[1] + "/payments/" + paymentId).with(csrf(mvc)).with(admin))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/purchase-orders/" + d[0]).with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT")).andExpect(jsonPath("$.downpaymentPaid").value(0));
    }

    @Test
    void responseShowsRequiredAndPaid() throws Exception {
        long[] d = draftWithTerm("33.33", "1", "100.01");
        mvc.perform(get("/api/purchase-orders/" + d[0]).with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.downpaymentRequired").value(33.33))
                .andExpect(jsonPath("$.downpaymentPaid").value(0));
    }

    @Test
    void downpaymentIsCheckedBeforeCreditApproval() throws Exception {
        long[] d = draftWithTerm("50", "10", "100.00");
        jdbc.update("update supplier set credit_limit = 400 where id = ?", d[1]); // exposure 500 after the downpayment
        send(d[0]).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("needs a downpayment of ₱500.00")));
        assertThat(poStatus(d[0])).isEqualTo("DRAFT");
        pay(d[1], d[0], "500");
        send(d[0]).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }
}
