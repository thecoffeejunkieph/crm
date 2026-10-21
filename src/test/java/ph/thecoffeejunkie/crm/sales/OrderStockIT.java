package ph.thecoffeejunkie.crm.sales;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.asContact;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

/** Sales wired to inventory and fulfilment (spec §3, §4): reserve, release into a delivery order, fulfil. */
@IntegrationTest
class OrderStockIT {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    private static final String REASON = "\"reason\":\"Customer asked\"";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment env;

    SalesFixture f;
    long admin;
    long rep;
    long customer;
    long p;
    long wh;
    RequestPostProcessor asRep;
    RequestPostProcessor asAdmin;

    @BeforeEach
    void seed() {
        f = new SalesFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        rep = f.user("rep", Role.SALES);
        customer = f.customer("acme", rep);
        jdbc.update("update customer set credit_limit = 100000 where id = ?", customer);
        p = f.product("A", "112.00", null, "VATABLE");
        wh = f.defaultWarehouse();
        asRep = as(rep, Role.SALES);
        asAdmin = as(admin, Role.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        // The ledger is append-only for crm_app; delivering posts DISPATCH movements, removed here as the owner.
        new JdbcTemplate(new DriverManagerDataSource(env.getProperty("spring.datasource.url"),
                env.getProperty("spring.flyway.user"), env.getProperty("spring.flyway.password")))
                .update("delete from stock_movement where product_id in (select id from product where sku like ?)",
                        f.tag + "%");
        f.cleanUp();
    }

    // --- helpers ---

    private long confirmed(String qty) throws Exception {
        long order = f.createdOrder(asRep, customer, line(p, qty));
        f.transition(asRep, order, "confirm", null).andExpect(status().isOk());
        return order;
    }

    private long released(String qty) throws Exception {
        long order = confirmed(qty);
        f.transition(asRep, order, "release", null).andExpect(status().isOk());
        return order;
    }

    private long lineId(long order) {
        return jdbc.queryForObject("select id from order_line where order_id = ?", Long.class, order);
    }

    private long deliveryOrder(long order) {
        return jdbc.queryForObject("select id from delivery_order where sales_order_id = ? and status <> 'CANCELLED'",
                Long.class, order);
    }

    private ResultActions revise(long order, String extra, String... lines) throws Exception {
        return f.update(asRep, order, extra.isEmpty() ? REASON : REASON + "," + extra, lines);
    }

    private void delivered(long order) throws Exception {
        long d = deliveryOrder(order);
        long doLine = jdbc.queryForObject("select id from delivery_order_line where delivery_order_id = ?", Long.class, d);
        mvc.perform(f.json(post("/api/delivery-orders/" + d + "/pick"),
                "{\"lines\":[{\"lineId\":" + doLine + ",\"pickedQty\":2}]}").with(asAdmin)).andExpect(status().isOk());
        mvc.perform(f.json(post("/api/delivery-orders/" + d + "/dispatch"), "{}").with(asAdmin))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/delivery-orders/" + d + "/deliver")
                .file(new MockMultipartFile("photo", "proof.png", "image/png", PNG)).param("receiverName", "Ana")
                .with(csrf(mvc)).with(asAdmin)).andExpect(status().isOk());
    }

    private int audits(long order, String action) {
        return jdbc.queryForObject("select count(*) from audit_event where entity_type = 'ORDER' and entity_id = ? "
                + "and action = ?", Integer.class, order, action);
    }

    // --- tests ---

    @Test
    void confirmReserves() throws Exception {
        f.stock(p, "10");
        long order = f.createdOrder(asRep, customer, line(p, "4"));
        f.transition(asRep, order, "confirm", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].reservedQty").value(4))
                .andExpect(jsonPath("$.lines[0].shortQty").value(0))
                .andExpect(jsonPath("$.totalShort").value(0))
                .andExpect(jsonPath("$.shipFromWarehouseId").value(wh))
                .andExpect(jsonPath("$.shipFromWarehouseName").isNotEmpty())
                .andExpect(jsonPath("$.deliveryMethod").value("DELIVERY"));
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("4");
    }

    @Test
    void draftLinesHaveNoStockYet() throws Exception {
        long order = f.createdOrder(asRep, customer, line(p, "4"));
        mvc.perform(get("/api/orders/" + order).with(asRep)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].reservedQty").doesNotExist())
                .andExpect(jsonPath("$.lines[0].shortQty").doesNotExist())
                .andExpect(jsonPath("$.deliveryOrder").doesNotExist());
    }

    @Test
    void confirmShortStillConfirms() throws Exception {
        f.stock(p, "1");
        long order = f.createdOrder(asRep, customer, line(p, "3"));
        f.transition(asRep, order, "confirm", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.lines[0].reservedQty").value(1))
                .andExpect(jsonPath("$.lines[0].shortQty").value(2))
                .andExpect(jsonPath("$.totalShort").value(2))
                .andExpect(jsonPath("$.allowedActions", hasItem("COVER_SHORTAGE")));
    }

    @Test
    void releaseBlockedWhileShort() throws Exception {
        long order = confirmed("2");
        long line = lineId(order);
        f.transition(asRep, order, "release", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ORDER_SHORT"))
                .andExpect(jsonPath("$.details[0].orderLineId").value(line))
                .andExpect(jsonPath("$.details[0].sku").value(f.tag + "A"))
                .andExpect(jsonPath("$.details[0].shortQty").value(2));
        assertThat(jdbc.queryForObject("select status from sales_order where id = ?", String.class, order))
                .isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("select count(*) from delivery_order where sales_order_id = ?", Integer.class,
                order)).isZero();
    }

    @Test
    void releaseCreatesDeliveryOrder() throws Exception {
        f.stock(p, "5");
        long order = confirmed("2");
        f.transition(asRep, order, "release", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.deliveryOrder.status").value("PICKING"))
                .andExpect(jsonPath("$.deliveryOrder.number", startsWith("DO-")));
        long d = deliveryOrder(order);
        assertThat(jdbc.queryForMap("select warehouse_id, method, customer_name, ship_to from delivery_order "
                + "where id = ?", d)).containsEntry("warehouse_id", wh).containsEntry("method", "DELIVERY")
                .containsEntry("customer_name", f.tag + "acme").containsEntry("ship_to", "Makati");
        assertThat(jdbc.queryForObject("select order_line_id from delivery_order_line where delivery_order_id = ?",
                Long.class, d)).isEqualTo(lineId(order));
    }

    @Test
    void coverShortageDraftsLinkedTransfer() throws Exception {
        long other = f.warehouse("O");
        f.stock(p, other, "5");
        long order = confirmed("3");
        mvc.perform(f.json(post("/api/orders/" + order + "/cover-shortage"), "{}").with(asRep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("DRAFT"))
                .andExpect(jsonPath("$[0].fromWarehouseId").value(other))
                .andExpect(jsonPath("$[0].toWarehouseId").value(wh))
                .andExpect(jsonPath("$[0].salesOrderId").value(order))
                .andExpect(jsonPath("$[0].lines[0].qty").value(3));
        assertThat(audits(order, "SHORTAGE_COVERED")).isEqualTo(1);
    }

    @Test
    void coverShortageHiddenWhenNotShort() throws Exception {
        f.stock(p, "5");
        long order = confirmed("2");
        mvc.perform(get("/api/orders/" + order).with(asRep))
                .andExpect(jsonPath("$.allowedActions", not(hasItem("COVER_SHORTAGE"))));
        mvc.perform(f.json(post("/api/orders/" + order + "/cover-shortage"), "{}").with(asRep))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void reviseReleasedPickingCancelsDoAndReturnsToConfirmed() throws Exception {
        f.stock(p, "5");
        long order = released("2");
        long d = deliveryOrder(order);
        revise(order, "", line(lineId(order), p, "3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.releasedAt").doesNotExist())
                .andExpect(jsonPath("$.deliveryOrder").doesNotExist())
                .andExpect(jsonPath("$.lines[0].reservedQty").value(3));
        assertThat(jdbc.queryForObject("select status from delivery_order where id = ?", String.class, d))
                .isEqualTo("CANCELLED");
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("3");
    }

    @Test
    void reviseAfterDispatch422() throws Exception {
        f.stock(p, "5");
        long order = released("2");
        jdbc.update("update delivery_order set status = 'DISPATCHED' where sales_order_id = ?", order);
        revise(order, "", line(lineId(order), p, "3")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        assertThat(jdbc.queryForObject("select status from sales_order where id = ?", String.class, order))
                .isEqualTo("RELEASED");
    }

    @Test
    void cancelReleasesReservations() throws Exception {
        f.stock(p, "5");
        long order = released("2");
        long d = deliveryOrder(order);
        f.transition(asRep, order, "cancel", "No longer needed").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("select count(*) from stock_reservation where sales_order_id = ?",
                Integer.class, order)).isZero();
        assertThat(jdbc.queryForObject("select status from delivery_order where id = ?", String.class, d))
                .isEqualTo("CANCELLED");
    }

    @Test
    void cancelAfterDispatch422() throws Exception {
        f.stock(p, "5");
        long order = released("2");
        jdbc.update("update delivery_order set status = 'DISPATCHED' where sales_order_id = ?", order);
        f.transition(asRep, order, "cancel", "No longer needed").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("2");
    }

    @Test
    void shipFromChangeMovesReservations() throws Exception {
        long other = f.warehouse("O");
        f.stock(p, "5");
        f.stock(p, other, "5");
        long order = confirmed("2");
        revise(order, "\"shipFromWarehouseId\":" + other + ",\"deliveryMethod\":\"PICKUP\"",
                line(lineId(order), p, "2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.shipFromWarehouseId").value(other))
                .andExpect(jsonPath("$.deliveryMethod").value("PICKUP"))
                .andExpect(jsonPath("$.lines[0].reservedQty").value(2));
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("0");
        assertThat(f.reserved(p, other)).isEqualByComparingTo("2");
        assertThat(audits(order, "SHIP_FROM_CHANGED")).isEqualTo(1);
    }

    @Test
    void archivedShipFrom400() throws Exception {
        long other = f.warehouse("O");
        jdbc.update("update warehouse set archived_at = now() where id = ?", other);
        long order = f.createdOrder(asRep, customer, line(p, "1"));
        f.update(asRep, order, "\"shipFromWarehouseId\":" + other, line(lineId(order), p, "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("shipFromWarehouseId"));
    }

    @Test
    void shipFromChangeAfterRelease422() throws Exception {
        long other = f.warehouse("O");
        f.stock(p, "5");
        long order = released("2");
        revise(order, "\"shipFromWarehouseId\":" + other, line(lineId(order), p, "2"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        assertThat(jdbc.queryForObject("select status from delivery_order where sales_order_id = ?", String.class,
                order)).isEqualTo("PICKING");
    }

    @Test
    void deliveredFulfils() throws Exception {
        f.stock(p, "5");
        long order = released("2");
        delivered(order);
        mvc.perform(get("/api/orders/" + order).with(asRep)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULFILLED"))
                .andExpect(jsonPath("$.fulfilledAt").isNotEmpty())
                .andExpect(jsonPath("$.deliveryOrder.status").value("DELIVERED"));
        assertThat(audits(order, "FULFILLED")).isEqualTo(1);
        assertThat(f.reserved(p, wh)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void portalSeesDeliveryStatusNotProof() throws Exception {
        long contact = f.contact(customer, "Ana", true);
        f.stock(p, "5");
        long order = released("2");
        mvc.perform(get("/api/portal/orders/" + order).with(asContact(contact, customer))).andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryStatus").value("PREPARING"))
                .andExpect(jsonPath("$.deliveredAt").doesNotExist());
        delivered(order);
        String body = SalesFixture.body(mvc.perform(get("/api/portal/orders/" + order)
                .with(asContact(contact, customer))).andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredAt").isNotEmpty()));
        assertThat(body).doesNotContainIgnoringCase("proof");
    }
}
