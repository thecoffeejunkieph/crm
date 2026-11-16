package ph.thecoffeejunkie.crm.sales;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

@IntegrationTest
class RefundsIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.core.env.Environment env;

    SalesFixture f;
    long rep;
    long admin;
    long customer;
    long product;
    long order; // 10 x 100.00 = 1000.00, fully collected
    RequestPostProcessor asRep;
    RequestPostProcessor asAdmin;

    @BeforeEach
    void seed() throws Exception {
        f = new SalesFixture(jdbc, mvc);
        rep = f.user("rep", Role.SALES);
        admin = f.user("boss", Role.ADMIN);
        asRep = as(rep, Role.SALES);
        asAdmin = as(admin, Role.ADMIN);
        customer = f.customer("acme", rep);
        product = f.product("A", "100.00", null, "VATABLE");
        order = f.createdOrder(asRep, customer, line(product, "10"));
        collect(order);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from stock_adjustment where product_id = ?", product);
        // The ledger is append-only for crm_app: clear it as the owner so the fixture can delete its products.
        new JdbcTemplate(new DriverManagerDataSource(env.getProperty("spring.datasource.url"),
                env.getProperty("spring.flyway.user"), env.getProperty("spring.flyway.password")))
                .update("delete from stock_movement where product_id = ?", product);
        f.cleanUp();
    }

    /** Confirms the order and settles it in full, as a verified payment would. */
    private void collect(long orderId) {
        f.setStatus(orderId, "CONFIRMED");
        jdbc.update("update sales_order set settled_amount = total where id = ?", orderId);
    }

    private ResultActions request(RequestPostProcessor who, long orderId, String json) throws Exception {
        return mvc.perform(f.json(post("/api/orders/" + orderId + "/refunds"), json)
                .header("Idempotency-Key", UUID.randomUUID().toString()).with(who));
    }

    private static String money(String amount) {
        return "{\"kind\":\"MONEY_ONLY\",\"amount\":" + amount + ",\"reason\":\"Customer changed mind\"}";
    }

    private static String goods(long orderLine, long warehouse, String qty) {
        return "{\"kind\":\"GOODS_RETURN\",\"reason\":\"Wrong item\",\"lines\":[{\"orderLineId\":" + orderLine
                + ",\"warehouseId\":" + warehouse + ",\"qty\":" + qty + ",\"disposition\":\"RESTOCK\"}]}";
    }

    private static String goods(long orderLine, long warehouse, String qty, String disposition) {
        return goods(orderLine, warehouse, qty).replace("RESTOCK", disposition);
    }

    private ResultActions payout(RequestPostProcessor who, long refundId) throws Exception {
        long version = jdbc.queryForObject("select version from refund where id = ?", Long.class, refundId);
        String json = "{\"method\":\"BANK_TRANSFER\",\"reference\":\"TRX-1\",\"paidOn\":\""
                + java.time.LocalDate.now() + "\",\"version\":" + version + "}";
        return mvc.perform(multipart("/api/refunds/" + refundId + "/payout")
                .file(new MockMultipartFile("payout", "", "application/json", json.getBytes()))
                .header("Idempotency-Key", UUID.randomUUID().toString()).with(csrf(mvc)).with(who));
    }

    private long approved(String json) throws Exception {
        return SalesFixture.id(request(asAdmin, order, json).andExpect(status().isCreated()));
    }

    private BigDecimal onHand(long warehouse) {
        return jdbc.queryForObject("select coalesce((select on_hand from stock_level where product_id = ? "
                + "and warehouse_id = ?), 0)", BigDecimal.class, product, warehouse);
    }

    private long orderLine(long orderId) {
        return jdbc.queryForObject("select id from order_line where order_id = ?", Long.class, orderId);
    }

    /** Dispatches the whole line from the default warehouse, as a delivery order would. */
    private void dispatched(long orderId, String qty) {
        dispatched(orderId, qty, f.defaultWarehouse(), "DISPATCHED");
    }

    /** One live delivery order per order, so a second shipment is recorded as a dispatched-then-cancelled one. */
    private void dispatched(long orderId, String qty, long wh, String doStatus) {
        long d = jdbc.queryForObject("insert into delivery_order (number, sales_order_id, warehouse_id, method, status, "
                + "dispatched_at, customer_name, order_number, rep_user_id) values (?, ?, ?, 'DELIVERY', ?, "
                + "now(), 'c', 'n', ?) returning id", Long.class, f.tag + "DO" + wh, orderId, wh,
                doStatus, rep);
        jdbc.update("insert into delivery_order_line (delivery_order_id, order_line_id, product_id, qty, picked_qty, "
                + "description, unit) values (?, ?, ?, ?::numeric, ?::numeric, 'd', 'PC')", d, orderLine(orderId),
                product, qty, qty);
        f.setStatus(orderId, "RELEASED");
    }

    private String refundStatus(long id) {
        return jdbc.queryForObject("select status from refund where id = ?", String.class, id);
    }

    private void decide(long refundId, boolean approve) throws Exception {
        long approval = jdbc.queryForObject("select approval_request_id from refund where id = ?", Long.class, refundId);
        mvc.perform(post("/api/approvals/" + approval + "/decision").contentType("application/json")
                .content("{\"approve\":" + approve + "}").with(csrf(mvc)).with(asAdmin)).andExpect(status().isOk());
    }

    @Test
    void adminMoneyOnlyRefundIsApprovedImmediately() throws Exception {
        long id = SalesFixture.id(request(asAdmin, order, money("100.00")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED")).andExpect(jsonPath("$.amount").value(100.00)));
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'SELF_APPROVED' "
                + "and entity_type = 'REFUND' and entity_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'REFUND_REQUESTED' "
                + "and entity_type = 'ORDER' and entity_id = ?", Integer.class, order)).isEqualTo(1);
    }

    @Test
    void repRefundNeedsApproval() throws Exception {
        long id = SalesFixture.id(request(asRep, order, money("100.00")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED")));
        assertThat(jdbc.queryForObject("select count(*) from approval_request where type = 'REFUND' "
                + "and subject_type = 'REFUND' and subject_id = ? and status = 'PENDING'", Integer.class, id)).isEqualTo(1);
        decide(id, true);
        assertThat(refundStatus(id)).isEqualTo("APPROVED");

        long other = SalesFixture.id(request(asRep, order, money("50.00")).andExpect(status().isCreated()));
        decide(other, false);
        assertThat(refundStatus(other)).isEqualTo("REJECTED");
    }

    @Test
    void cannotRefundMoreThanCollected() throws Exception {
        request(asAdmin, order, money("1000.01")).andExpect(status().isUnprocessableContent());
        long unpaid = f.createdOrder(asRep, customer, line(product, "1"));
        f.setStatus(unpaid, "CONFIRMED");
        request(asAdmin, unpaid, money("1.00")).andExpect(status().isUnprocessableContent());
    }

    @Test
    void concurrentRequestsCannotExceedRefundable() throws Exception {
        request(asRep, order, money("700.00")).andExpect(status().isCreated());
        request(asRep, order, money("400.00")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.refundable").value(300.00));
        request(asRep, order, money("300.00")).andExpect(status().isCreated());
    }

    @Test
    void rejectedRefundFreesAmount() throws Exception {
        long id = SalesFixture.id(request(asRep, order, money("700.00")).andExpect(status().isCreated()));
        decide(id, false);
        request(asRep, order, money("900.00")).andExpect(status().isCreated());
    }

    @Test
    void cannotReturnMoreThanShipped() throws Exception {
        dispatched(order, "4");
        long line = orderLine(order);
        long wh = f.defaultWarehouse();
        request(asAdmin, order, goods(line, wh, "5")).andExpect(status().isUnprocessableContent());
        request(asAdmin, order, goods(line, f.warehouse("X"), "1")).andExpect(status().isUnprocessableContent());
        request(asAdmin, order, goods(line, wh, "3")).andExpect(status().isCreated());
        request(asAdmin, order, goods(line, wh, "2")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.returnable").value(1));
        mvc.perform(get("/api/orders/" + order + "/refunds/returnable").with(asRep)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shipped").value(4)).andExpect(jsonPath("$[0].returned").value(3));
    }

    @Test
    void goodsLineAmountUsesNetUnitPrice() throws Exception {
        long discounted = SalesFixture.id(mvc.perform(f.json(post("/api/orders"), "{\"customerId\":" + customer
                + ",\"discountType\":\"AMOUNT\",\"discountValue\":100,\"lines\":[" + line(product, "10") + "]}")
                .with(asAdmin)).andExpect(status().isOk()));
        collect(discounted);
        dispatched(discounted, "10");
        BigDecimal net = jdbc.queryForObject("select net_total from order_line where order_id = ?", BigDecimal.class,
                discounted);
        assertThat(net).isNotNull().isLessThan(new BigDecimal("1000.00"));
        BigDecimal expected = net.multiply(new BigDecimal(2)).divide(new BigDecimal(10), 2, java.math.RoundingMode.HALF_UP);
        request(asAdmin, discounted, goods(orderLine(discounted), f.defaultWarehouse(), "2"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(expected.doubleValue()))
                .andExpect(jsonPath("$.lines[0].amount").value(expected.doubleValue()))
                .andExpect(jsonPath("$.lines[0].description").isNotEmpty());
    }

    @Test
    void cancelledOrderAllowsMoneyOnlyOnly() throws Exception {
        dispatched(order, "4");
        f.setStatus(order, "CANCELLED");
        request(asAdmin, order, goods(orderLine(order), f.defaultWarehouse(), "1"))
                .andExpect(status().isUnprocessableContent());
        request(asAdmin, order, money("100.00")).andExpect(status().isCreated());
    }

    @Test
    void cancelFreesRefund() throws Exception {
        long id = SalesFixture.id(request(asRep, order, money("700.00")).andExpect(status().isCreated()));
        long approval = jdbc.queryForObject("select approval_request_id from refund where id = ?", Long.class, id);
        long version = jdbc.queryForObject("select version from refund where id = ?", Long.class, id);
        mvc.perform(f.json(post("/api/refunds/" + id + "/cancel"), "{\"version\":" + version + "}").with(asRep))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("select status from approval_request where id = ?", String.class, approval))
                .isEqualTo("CANCELLED");
        request(asRep, order, money("1000.00")).andExpect(status().isCreated());
        // Not cancellable twice.
        mvc.perform(f.json(post("/api/refunds/" + id + "/cancel"), "{\"version\":" + (version + 1) + "}").with(asRep))
                .andExpect(status().isUnprocessableContent());
        assertThat(List.of(refundStatus(id))).containsExactly("CANCELLED");
    }

    @Test
    void adminCancelOfRepRefundCancelsApproval() throws Exception {
        long id = SalesFixture.id(request(asRep, order, money("700.00")).andExpect(status().isCreated()));
        long approval = jdbc.queryForObject("select approval_request_id from refund where id = ?", Long.class, id);
        long version = jdbc.queryForObject("select version from refund where id = ?", Long.class, id);
        mvc.perform(f.json(post("/api/refunds/" + id + "/cancel"), "{\"version\":" + version + "}").with(asAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("select status from approval_request where id = ?", String.class, approval))
                .isEqualTo("CANCELLED");
    }

    @Test
    void payoutMarksPaidAndIssuesCreditMemo() throws Exception {
        long a = approved(money("100.00"));
        long b = approved(money("50.00"));
        payout(asAdmin, a).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.creditMemoNo").value(org.hamcrest.Matchers.matchesPattern("CM-\\d{5}")));
        payout(asAdmin, b).andExpect(status().isOk());
        String cmA = jdbc.queryForObject("select credit_memo_no from refund where id = ?", String.class, a);
        String cmB = jdbc.queryForObject("select credit_memo_no from refund where id = ?", String.class, b);
        assertThat(Integer.parseInt(cmB.substring(3))).isEqualTo(Integer.parseInt(cmA.substring(3)) + 1);
        assertThat(jdbc.queryForObject("select refunded_amount from sales_order where id = ?", BigDecimal.class, order))
                .isEqualByComparingTo("150.00");
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'REFUND_PAID' "
                + "and entity_type = 'ORDER' and entity_id = ?", Integer.class, order)).isEqualTo(2);
    }

    @Test
    void paidOrderStaysPaid() throws Exception {
        payout(asAdmin, approved(money("300.00"))).andExpect(status().isOk());
        mvc.perform(get("/api/orders/" + order).with(asRep)).andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID")).andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void goodsPayoutPostsReturns() throws Exception {
        long wh = f.defaultWarehouse();
        f.stock(product, "10");
        dispatched(order, "10");
        long line = orderLine(order);
        payout(asAdmin, approved(goods(line, wh, "3"))).andExpect(status().isOk());
        assertThat(onHand(wh)).isEqualByComparingTo("13");
        payout(asAdmin, approved(goods(line, wh, "2", "DAMAGED"))).andExpect(status().isOk());
        assertThat(onHand(wh)).isEqualByComparingTo("13");
        assertThat(jdbc.queryForList("select delta from stock_adjustment where product_id = ? "
                + "and source = 'RETURN_DAMAGED'", BigDecimal.class, product)).hasSize(1);
    }

    @Test
    void returnGoesToChosenWarehouse() throws Exception {
        long w1 = f.defaultWarehouse();
        long w2 = f.warehouse("2");
        f.stock(product, "10");
        dispatched(order, "5", w1, "DISPATCHED");
        dispatched(order, "5", w2, "CANCELLED");
        long line = orderLine(order);
        request(asAdmin, order, goods(line, w2, "6")).andExpect(status().isUnprocessableContent());
        payout(asAdmin, approved(goods(line, w2, "4"))).andExpect(status().isOk());
        assertThat(onHand(w2)).isEqualByComparingTo("4");
        assertThat(onHand(w1)).isEqualByComparingTo("10");
    }

    @Test
    void payoutRechecksGuards() throws Exception {
        long a = approved(money("700.00"));
        long b = approved(money("300.00"));
        jdbc.update("update sales_order set settled_amount = 800 where id = ?", order);
        payout(asAdmin, a).andExpect(status().isOk());
        payout(asAdmin, b).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.refundable").value(100.00));
        assertThat(refundStatus(b)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select refunded_amount from sales_order where id = ?", BigDecimal.class, order))
                .isEqualByComparingTo("700.00");
    }

    @Test
    void payoutNotifiesRepAndAdmins() throws Exception {
        long admin2 = f.user("boss2", Role.ADMIN);
        payout(asAdmin, approved(money("100.00"))).andExpect(status().isOk());
        assertThat(jdbc.queryForList("select recipient_user_id from notification where type = 'REFUND_PAID' "
                + "and entity_id = ?", Long.class, order)).contains(rep, admin2).doesNotContain(admin);
    }

    @Test
    void payoutRequiresApproved() throws Exception {
        long id = SalesFixture.id(request(asRep, order, money("100.00")).andExpect(status().isCreated()));
        payout(asAdmin, id).andExpect(status().isUnprocessableContent());
        assertThat(refundStatus(id)).isEqualTo("REQUESTED");
        payout(asAdmin, approved(money("100.00"))).andExpect(status().isOk());
    }
}
