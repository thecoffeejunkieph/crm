package ph.thecoffeejunkie.crm.fulfilment;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.catalog.ProductUnit;
import ph.thecoffeejunkie.crm.inventory.Inventory;
import ph.thecoffeejunkie.crm.inventory.InventoryFixture;
import ph.thecoffeejunkie.crm.inventory.OrderStock;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.Role;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.as;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.l;

@IntegrationTest
@RecordApplicationEvents
class DeliveryOrdersIT {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    private static final byte[] PDF = "%PDF-1.7 not a photo".getBytes();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ApplicationContext ctx;
    @Autowired Environment env;
    @Autowired Inventory inventory;
    @Autowired Fulfilment fulfilment;
    @Autowired ApplicationEvents events;

    InventoryFixture f;
    long rep;
    long rep2;
    long wh;
    long otherWh;
    long p;
    long q;
    long whUserId;
    RequestPostProcessor admin;
    RequestPostProcessor whUser;
    RequestPostProcessor otherWhUser;
    RequestPostProcessor sales;
    RequestPostProcessor sales2;

    @BeforeEach
    void seed() {
        f = InventoryFixture.create(jdbc, tx, ctx);
        rep = f.user("rep");
        rep2 = f.user("rep2");
        wh = f.warehouse("W");
        otherWh = f.warehouse("O");
        p = f.product("P", "KG");
        q = f.product("Q", "PC");
        admin = as(f.user("adm"), Role.ADMIN);
        whUserId = f.user("wh");
        jdbc.update("update app_user set role = 'WAREHOUSE' where id = ?", whUserId);
        jdbc.update("insert into user_warehouse (user_id, warehouse_id) values (?, ?)", whUserId, wh);
        whUser = as(whUserId, Role.WAREHOUSE, wh);
        otherWhUser = as(f.user("ow"), Role.WAREHOUSE, otherWh);
        sales = as(rep, Role.SALES);
        sales2 = as(rep2, Role.SALES);
    }

    @AfterEach
    void clean() {
        jdbc.update("delete from delivery_order where warehouse_id in (?, ?)", wh, otherWh);
        jdbc.update("delete from stock_adjustment where warehouse_id in (?, ?)", wh, otherWh);
        // The ledger is append-only for crm_app. Its rows reference our staff users (created_by); removing them as
        // the owner lets the fixture delete those users, so BootstrapAdminIT can still empty app_user afterwards.
        new JdbcTemplate(new DriverManagerDataSource(env.getProperty("spring.datasource.url"),
                env.getProperty("spring.flyway.user"), env.getProperty("spring.flyway.password")))
                .update("delete from stock_movement where warehouse_id in (?, ?)", wh, otherWh);
        jdbc.update("delete from user_warehouse where user_id = ?", whUserId);
        f.cleanUp();
    }

    // --- helpers ---

    private OrderStock reserved(long repId, Instant confirmedAt, InventoryFixture.Line... lines) {
        OrderStock o = f.order(repId, wh, confirmedAt, lines);
        tx.executeWithoutResult(s -> inventory.reserve(o));
        return o;
    }

    private long deliveryFor(OrderStock o, DeliveryMethod method) {
        return fulfilment.create(new DeliveryRequest(o.orderId(), o.orderNumber(), o.repUserId(), o.warehouseId(),
                method, "Cafe Uno", "Makati", o.lines().stream().map(l -> new DeliveryRequest.DeliveryLine(
                        l.orderLineId(), l.productId(), "Beans", ProductUnit.KG, l.qty())).toList()));
    }

    private long delivery(String qty) {
        return deliveryFor(reserved(rep, Instant.now(), l(p, qty)), DeliveryMethod.DELIVERY);
    }

    private List<Long> lineIds(long id) {
        return jdbc.queryForList("select id from delivery_order_line where delivery_order_id = ? order by id",
                Long.class, id);
    }

    private ResultActions act(long id, String action, String body, RequestPostProcessor who) throws Exception {
        return mvc.perform(post("/api/delivery-orders/" + id + "/" + action).contentType("application/json")
                .content(body == null ? "{}" : body).with(csrf(mvc)).with(who));
    }

    private ResultActions pick(long id, RequestPostProcessor who, String... qtys) throws Exception {
        List<Long> ids = lineIds(id);
        StringBuilder b = new StringBuilder("{\"lines\":[");
        for (int i = 0; i < qtys.length; i++) {
            b.append(i == 0 ? "" : ",").append("{\"lineId\":").append(ids.get(i)).append(",\"pickedQty\":")
                    .append(qtys[i]).append('}');
        }
        return act(id, "pick", b.append("]}").toString(), who);
    }

    private ResultActions deliver(long id, byte[] photo, String receiverName) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/delivery-orders/" + id + "/deliver");
        if (photo != null) {
            req.file(new MockMultipartFile("photo", "proof.png", "image/png", photo));
        }
        if (receiverName != null) {
            req.param("receiverName", receiverName);
        }
        return mvc.perform(req.with(csrf(mvc)).with(whUser));
    }

    private String stateOf(long id) {
        return jdbc.queryForObject("select status from delivery_order where id = ?", String.class, id);
    }

    private int dispatchMovements(long id) {
        return jdbc.queryForObject("select count(*) from stock_movement where type = 'DISPATCH' "
                + "and ref_type = 'DELIVERY_ORDER' and ref_id = ?", Integer.class, id);
    }

    private Map<String, Object> reservation(long orderLineId) {
        return jdbc.queryForMap("select reserved_qty, short_qty from stock_reservation where order_line_id = ?",
                orderLineId);
    }

    private int storedProofs(long id) {
        MinioClient client = MinioClient.builder().endpoint(env.getProperty("app.minio.endpoint"))
                .credentials(env.getProperty("app.minio.access-key"), env.getProperty("app.minio.secret-key")).build();
        int n = 0;
        for (var ignored : client.listObjects(ListObjectsArgs.builder().bucket(env.getProperty("app.minio.bucket"))
                .prefix("delivery-orders/" + id + "/proof").recursive(true).build())) {
            n++;
        }
        return n;
    }

    private long dispatched(String qty) throws Exception {
        f.receive(p, wh, "10");
        long id = delivery(qty);
        pick(id, whUser, qty).andExpect(status().isOk());
        act(id, "dispatch", null, whUser).andExpect(status().isOk());
        return id;
    }

    // --- tests ---

    @Test
    void createNotifiesWarehouseUsers() throws Exception {
        f.receive(p, wh, "10");
        long id = delivery("4");
        assertThat(stateOf(id)).isEqualTo("PICKING");
        assertThat(jdbc.queryForObject("select number from delivery_order where id = ?", String.class, id))
                .matches("DO-\\d{4}-\\d{5}");
        assertThat(jdbc.queryForList("select entity_id from notification where recipient_user_id = ? "
                + "and type = 'DELIVERY_CREATED' and entity_type = 'DELIVERY_ORDER'", Long.class, whUserId))
                .containsExactly(id);
        mvc.perform(get("/api/delivery-orders/" + id).with(whUser)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKING"))
                .andExpect(jsonPath("$.number").value(startsWith("DO-")))
                .andExpect(jsonPath("$.customerName").value("Cafe Uno"))
                .andExpect(jsonPath("$.lines[0].qty").value(4.0))
                .andExpect(jsonPath("$.lines[0].unit").value("KG"));
    }

    @Test
    void shortPickPostsAdjustmentAndBlocksDispatch() throws Exception {
        f.receive(p, wh, "10");
        OrderStock older = reserved(rep, Instant.now().minusSeconds(600), l(p, "5"));
        OrderStock younger = reserved(rep2, Instant.now(), l(p, "5"));
        long id = deliveryFor(older, DeliveryMethod.DELIVERY);

        pick(id, whUser, "3").andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].pickedQty").value(3.0));

        assertThat(f.onHand(p, wh)).isEqualByComparingTo("8");
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("8");
        Map<String, Object> a = reservation(older.lines().get(0).orderLineId());
        assertThat((BigDecimal) a.get("reserved_qty")).isEqualByComparingTo("3");
        assertThat((BigDecimal) a.get("short_qty")).isEqualByComparingTo("2");
        // R1: the younger order on the same level keeps its reservation.
        Map<String, Object> b = reservation(younger.lines().get(0).orderLineId());
        assertThat((BigDecimal) b.get("reserved_qty")).isEqualByComparingTo("5");
        assertThat((BigDecimal) b.get("short_qty")).isEqualByComparingTo("0");
        Map<String, Object> adj = jdbc.queryForMap("select delta, source, status, reason from stock_adjustment "
                + "where warehouse_id = ?", wh);
        assertThat((BigDecimal) adj.get("delta")).isEqualByComparingTo("-2");
        assertThat(adj.get("source")).isEqualTo("SHORT_PICK");
        assertThat(adj.get("status")).isEqualTo("POSTED");
        assertThat((String) adj.get("reason")).startsWith("Short pick on DO-");
        assertThat(jdbc.queryForObject("select count(*) from notification where recipient_user_id = ? "
                + "and type = 'SHORT_PICK'", Integer.class, rep)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notification where recipient_user_id = ? "
                + "and type = 'RESERVATION_TAKEN'", Integer.class, rep2)).isZero();

        // Re-picking the full qty before the gap is filled still cannot dispatch.
        pick(id, whUser, "5").andExpect(status().isOk());
        act(id, "dispatch", null, whUser).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ORDER_SHORT"));
        assertThat(stateOf(id)).isEqualTo("PICKING");
        assertThat(f.onHand(p, wh)).isEqualByComparingTo("8");
        assertThat(dispatchMovements(id)).isZero();
    }

    @Test
    void pickMoreThanQty400() throws Exception {
        f.receive(p, wh, "10");
        long id = delivery("4");
        pick(id, whUser, "5").andExpect(status().isBadRequest());
        assertThat(f.onHand(p, wh)).isEqualByComparingTo("10");
    }

    @Test
    void repickAfterReceiptAllowsDispatch() throws Exception {
        f.receive(p, wh, "5");
        long id = delivery("5");
        pick(id, whUser, "3").andExpect(status().isOk());
        assertThat(f.onHand(p, wh)).isEqualByComparingTo("3");
        f.receive(p, wh, "2");
        pick(id, whUser, "5").andExpect(status().isOk());
        act(id, "dispatch", null, whUser).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"));
        assertThat(f.onHand(p, wh)).isEqualByComparingTo("0");
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("0");
    }

    @Test
    void dispatchNeedsFullPick() throws Exception {
        f.receive(p, wh, "10");
        f.receive(q, wh, "10");
        long id = deliveryFor(reserved(rep, Instant.now(), l(p, "2"), l(q, "3")), DeliveryMethod.DELIVERY);
        act(id, "dispatch", null, whUser).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PICK_INCOMPLETE"));
        pick(id, whUser, "2").andExpect(status().isOk());
        act(id, "dispatch", null, whUser).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PICK_INCOMPLETE"));
        assertThat(stateOf(id)).isEqualTo("PICKING");
        assertThat(dispatchMovements(id)).isZero();
    }

    @Test
    void dispatchDeductsAndClearsReservations() throws Exception {
        f.receive(p, wh, "10");
        f.receive(q, wh, "4");
        OrderStock o = reserved(rep, Instant.now(), l(p, "3"), l(q, "4"), l(p, "2"));
        long id = deliveryFor(o, DeliveryMethod.DELIVERY);
        pick(id, whUser, "3", "4", "2").andExpect(status().isOk());
        act(id, "dispatch", null, whUser).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.dispatchedAt").isNotEmpty());
        assertThat(f.onHand(p, wh)).isEqualByComparingTo("5");
        assertThat(f.onHand(q, wh)).isEqualByComparingTo("0");
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("0");
        assertThat(f.reserved(q, wh)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("select count(*) from stock_reservation where sales_order_id = ?",
                Integer.class, o.orderId())).isZero();
        assertThat(dispatchMovements(id)).isEqualTo(3);
    }

    @Test
    void doubleDispatchDeductsOnce() throws Exception {
        f.receive(p, wh, "10");
        long id = delivery("4");
        pick(id, whUser, "4").andExpect(status().isOk());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<MvcResult>> taps = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                RequestPostProcessor token = csrf(mvc);
                taps.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return mvc.perform(post("/api/delivery-orders/" + id + "/dispatch").contentType("application/json")
                            .content("{}").with(token).with(whUser)).andReturn();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            String loserBody = null;
            for (Future<MvcResult> t : taps) {
                MvcResult r = t.get(30, TimeUnit.SECONDS);
                statuses.add(r.getResponse().getStatus());
                if (r.getResponse().getStatus() == 422) {
                    loserBody = r.getResponse().getContentAsString();
                }
            }
            Collections.sort(statuses);
            assertThat(statuses).containsExactly(200, 422);
            assertThat(loserBody).contains("INVALID_STATE");
        } finally {
            pool.shutdownNow();
        }
        assertThat(dispatchMovements(id)).isEqualTo(1);
        assertThat(f.onHand(p, wh)).isEqualByComparingTo("6");
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("0");
    }

    @Test
    void deliverNeedsPhotoAndName() throws Exception {
        long id = dispatched("4");
        deliver(id, null, "Juan").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PROOF_REQUIRED"));
        deliver(id, PNG, " ").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PROOF_REQUIRED"));
        deliver(id, PNG, null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PROOF_REQUIRED"));
        assertThat(stateOf(id)).isEqualTo("DISPATCHED");
        assertThat(storedProofs(id)).isZero();
    }

    @Test
    void deliverPdfProof400StaysDispatched() throws Exception {
        long id = dispatched("4");
        deliver(id, PDF, "Juan").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(stateOf(id)).isEqualTo("DISPATCHED");
        assertThat(storedProofs(id)).isZero();
        assertThat(events.stream(OrderDelivered.class)).isEmpty();
    }

    @Test
    void deliverPublishesOrderDelivered() throws Exception {
        long id = dispatched("4");
        long orderId = jdbc.queryForObject("select sales_order_id from delivery_order where id = ?", Long.class, id);
        deliver(id, PNG, "Juan").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.receiverName").value("Juan"))
                .andExpect(jsonPath("$.proofUrl").value(startsWith("/api/v1/files/delivery-orders/" + id + "/proof/")));
        assertThat(events.stream(OrderDelivered.class)).singleElement()
                .satisfies(e -> assertThat(e.orderId()).isEqualTo(orderId));
        assertThat(storedProofs(id)).isEqualTo(1);
        assertThat(fulfilment.forOrder(orderId)).hasValueSatisfying(s -> {
            assertThat(s.status()).isEqualTo(DeliveryStatus.DELIVERED);
            assertThat(s.receiverName()).isEqualTo("Juan");
            assertThat(s.deliveredAt()).isNotNull();
        });
        // a second tap is refused
        deliver(id, PNG, "Juan").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void pickupDeliversFromPicking() throws Exception {
        f.receive(p, wh, "10");
        long id = deliveryFor(reserved(rep, Instant.now(), l(p, "4")), DeliveryMethod.PICKUP);
        act(id, "dispatch", null, whUser).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        deliver(id, PNG, "Juan").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PICK_INCOMPLETE"));
        pick(id, whUser, "4").andExpect(status().isOk());
        deliver(id, PNG, "Juan").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));
        assertThat(f.onHand(p, wh)).isEqualByComparingTo("6");
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("0");
        assertThat(dispatchMovements(id)).isEqualTo(1);
    }

    @Test
    void cancelForOrderAfterDispatch422() throws Exception {
        f.receive(p, wh, "10");
        OrderStock picking = reserved(rep, Instant.now(), l(p, "2"));
        long a = deliveryFor(picking, DeliveryMethod.DELIVERY);
        fulfilment.cancelForOrder(picking.orderId());
        assertThat(stateOf(a)).isEqualTo("CANCELLED");
        assertThat(fulfilment.forOrder(picking.orderId())).isEmpty();
        fulfilment.cancelForOrder(picking.orderId()); // nothing live: no-op

        long b = dispatched("3");
        long orderId = jdbc.queryForObject("select sales_order_id from delivery_order where id = ?", Long.class, b);
        assertThatThrownBy(() -> fulfilment.cancelForOrder(orderId)).isInstanceOfSatisfying(DomainException.class,
                e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE);
                    assertThat(e.getMessage()).isEqualTo("This order has already left the warehouse");
                });
        assertThat(stateOf(b)).isEqualTo("DISPATCHED");
    }

    @Test
    void warehouseUserOtherWarehouse404() throws Exception {
        f.receive(p, wh, "10");
        long id = delivery("4");
        mvc.perform(get("/api/delivery-orders/" + id).with(otherWhUser)).andExpect(status().isNotFound());
        pick(id, otherWhUser, "4").andExpect(status().isNotFound());
        act(id, "dispatch", null, otherWhUser).andExpect(status().isNotFound());
        mvc.perform(get("/api/delivery-orders").param("warehouseId", String.valueOf(wh)).with(otherWhUser))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/delivery-orders").with(otherWhUser)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")]").isEmpty());
        mvc.perform(get("/api/delivery-orders").param("warehouseId", String.valueOf(wh)).with(whUser))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.id == " + id + ")]").isNotEmpty());
        assertThat(lineIds(id)).hasSize(1);
        assertThat(jdbc.queryForObject("select picked_qty from delivery_order_line where delivery_order_id = ?",
                BigDecimal.class, id)).isNull();
    }

    @Test
    void salesSeesOnlyOwnOrders() throws Exception {
        f.receive(p, wh, "10");
        long mine = delivery("2");
        long theirs = deliveryFor(reserved(rep2, Instant.now(), l(p, "2")), DeliveryMethod.DELIVERY);
        mvc.perform(get("/api/delivery-orders").with(sales)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + mine + ")]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id == " + theirs + ")]").isEmpty());
        mvc.perform(get("/api/delivery-orders/" + mine).with(sales)).andExpect(status().isOk());
        mvc.perform(get("/api/delivery-orders/" + theirs).with(sales)).andExpect(status().isNotFound());
        mvc.perform(get("/api/delivery-orders/" + theirs).with(sales2)).andExpect(status().isOk());
        pick(mine, sales, "2").andExpect(status().isForbidden());
        mvc.perform(get("/api/delivery-orders").param("status", "PICKING").with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + theirs + ")]").isNotEmpty());
    }
}
