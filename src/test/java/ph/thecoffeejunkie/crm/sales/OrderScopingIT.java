package ph.thecoffeejunkie.crm.sales;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

@IntegrationTest
class OrderScopingIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    SalesFixture f;
    long a;
    long b;
    long theirCustomer;
    long theirOrder;
    long myOrder;

    @BeforeEach
    void seed() throws Exception {
        f = new SalesFixture(jdbc, mvc);
        long admin = f.user("adm", Role.ADMIN);
        a = f.user("a", Role.SALES);
        b = f.user("b", Role.SALES);
        long product = f.product("P", "10.00", null, "VATABLE");
        long myCustomer = f.customer("mine", a);
        theirCustomer = f.customer("theirs", b);
        myOrder = f.createdOrder(as(admin, Role.ADMIN), myCustomer, line(product, "1"));
        theirOrder = f.createdOrder(as(admin, Role.ADMIN), theirCustomer, line(product, "1"));
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    @Test
    void salesListsOnlyOwnCustomersOrders() throws Exception {
        mvc.perform(get("/api/orders").with(as(a, Role.SALES))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(myOrder));
    }

    @Test
    void otherRepsOrderIs404() throws Exception {
        var me = as(a, Role.SALES);
        mvc.perform(get("/api/orders/" + theirOrder).with(me)).andExpect(status().isNotFound());
        f.update(me, theirOrder, "").andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/" + theirOrder + "/history").with(me)).andExpect(status().isNotFound());
        f.create(me, theirCustomer).andExpect(status().isNotFound());
    }

    @Test
    void reassignedCustomerMovesOrders() throws Exception {
        jdbc.update("update customer set assigned_rep_id = ? where id = ?", a, theirCustomer);
        mvc.perform(get("/api/orders/" + theirOrder).with(as(a, Role.SALES))).andExpect(status().isOk());
        mvc.perform(get("/api/orders/" + theirOrder).with(as(b, Role.SALES))).andExpect(status().isNotFound());
    }

    @Test
    void warehouseGets403() throws Exception {
        long w = f.user("w", Role.WAREHOUSE);
        mvc.perform(get("/api/orders").with(as(w, Role.WAREHOUSE))).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/" + myOrder).with(as(w, Role.WAREHOUSE))).andExpect(status().isForbidden());
    }
}
