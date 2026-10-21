package ph.thecoffeejunkie.crm.fulfilment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.catalog.ProductUnit;
import ph.thecoffeejunkie.crm.inventory.Inventory;
import ph.thecoffeejunkie.crm.inventory.InventoryFixture;
import ph.thecoffeejunkie.crm.inventory.OrderStock;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.as;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.l;

@IntegrationTest
class ScanIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ApplicationContext ctx;
    @Autowired Inventory inventory;
    @Autowired Fulfilment fulfilment;

    InventoryFixture f;
    long rep;
    long wh;
    long otherWh;
    long p;
    long deliveryId;
    long transferId;
    RequestPostProcessor admin;
    RequestPostProcessor whUser;
    RequestPostProcessor otherWhUser;
    RequestPostProcessor sales;
    RequestPostProcessor sales2;

    @BeforeEach
    void seed() {
        f = InventoryFixture.create(jdbc, tx, ctx);
        rep = f.user("rep");
        wh = f.warehouse("W");
        otherWh = f.warehouse("O");
        p = f.product("P", "KG");
        admin = as(f.user("adm"), Role.ADMIN);
        whUser = as(f.user("wh"), Role.WAREHOUSE, wh);
        otherWhUser = as(f.user("ow"), Role.WAREHOUSE, otherWh);
        sales = as(rep, Role.SALES);
        sales2 = as(f.user("rep2"), Role.SALES);
        f.receive(p, wh, "10");
        OrderStock o = f.order(rep, wh, Instant.now(), l(p, "2"));
        tx.executeWithoutResult(s -> inventory.reserve(o));
        deliveryId = fulfilment.create(new DeliveryRequest(o.orderId(), o.orderNumber(), o.repUserId(), wh,
                DeliveryMethod.DELIVERY, "Cafe Uno", "Makati", o.lines().stream().map(x ->
                new DeliveryRequest.DeliveryLine(x.orderLineId(), x.productId(), "Beans", ProductUnit.KG, x.qty()))
                .toList()));
        transferId = jdbc.queryForObject("insert into stock_transfer (number, status, from_warehouse_id, "
                + "to_warehouse_id) values (?, 'DRAFT', ?, ?) returning id", Long.class, f.tag + "-TR", wh, otherWh);
    }

    @AfterEach
    void clean() {
        jdbc.update("delete from delivery_order where warehouse_id = ?", wh);
        jdbc.update("delete from stock_transfer where id = ?", transferId);
        f.cleanUp();
    }

    private void resolves(String code, String type, long id) throws Exception {
        mvc.perform(get("/api/scan/" + code).with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value(type)).andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void resolvesEachType() throws Exception {
        resolves("crm:DO:" + deliveryId, "DELIVERY_ORDER", deliveryId);
        resolves("crm:TR:" + transferId, "TRANSFER", transferId);
        resolves("crm:PR:" + p, "PRODUCT", p);
        resolves("crm:WH:" + wh, "WAREHOUSE", wh);
        mvc.perform(get("/api/scan/crm:PR:" + p).with(sales)).andExpect(status().isOk());
    }

    @Test
    void garbage404() throws Exception {
        for (String code : new String[] {"hello", "crm:XX:1", "crm:DO:abc", "crm:DO:", "crm:DO:99999999",
                "crm:PR:99999999", "crm:WH:99999999", "crm:TR:99999999", "crm:DO:99999999999999999999"}) {
            mvc.perform(get("/api/scan/" + code).with(admin)).andExpect(status().isNotFound());
        }
    }

    @Test
    void outOfScopeDo404() throws Exception {
        mvc.perform(get("/api/scan/crm:DO:" + deliveryId).with(whUser)).andExpect(status().isOk());
        mvc.perform(get("/api/scan/crm:DO:" + deliveryId).with(sales)).andExpect(status().isOk());
        mvc.perform(get("/api/scan/crm:DO:" + deliveryId).with(otherWhUser)).andExpect(status().isNotFound());
        mvc.perform(get("/api/scan/crm:DO:" + deliveryId).with(sales2)).andExpect(status().isNotFound());
    }
}
