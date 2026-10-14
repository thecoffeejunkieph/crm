package ph.thecoffeejunkie.crm.sales;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

@IntegrationTest
class ReportsIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    SalesFixture f;
    long rep;
    long customer;
    long order;
    long product;
    RequestPostProcessor asRep;

    @BeforeEach
    void seed() throws Exception {
        f = new SalesFixture(jdbc, mvc);
        rep = f.user("rep", Role.SALES);
        customer = f.customer("Dela Cruz, Inc.", rep);
        product = f.product("A", "112.00", null, "VATABLE");
        asRep = as(rep, Role.SALES);
        order = confirmedOrder(customer);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private long confirmedOrder(long customerId) throws Exception {
        long id = f.createdOrder(as(rep, Role.ADMIN), customerId, line(product, "10"));
        f.setStatus(id, "CONFIRMED");
        return id;
    }

    private long payment(long orderId, String status, String withholding, String form2307Key) {
        return jdbc.queryForObject("insert into payment (order_id, status, method, cash_amount, withholding_amount, "
                + "received_on, form2307_key, submitted_by_type, submitted_by_id) values (?, ?, 'CHECK', 100, "
                + "?::numeric, current_date, ?, 'USER', ?) returning id", Long.class,
                orderId, status, withholding, form2307Key, rep);
    }

    @Test
    void listsOnlyVerifiedWithholdingWithout2307() throws Exception {
        long wanted = payment(order, "VERIFIED", "1.00", null);
        payment(order, "VERIFIED", "0", null);
        payment(order, "VERIFIED", "1.00", "payments/2307/x.pdf");
        payment(order, "PENDING", "1.00", null);
        mvc.perform(get("/api/reports/missing-2307").param("customerId", String.valueOf(customer)).with(asRep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].paymentId").value(wanted))
                .andExpect(jsonPath("$.content[0].customerName").value(f.tag + "Dela Cruz, Inc."))
                .andExpect(jsonPath("$.content[0].withholdingAmount").value(1.00));
    }

    @Test
    void salesSeesOnlyOwnCustomers() throws Exception {
        long other = f.user("oth", Role.SALES);
        long theirCustomer = f.customer("theirs", other);
        payment(confirmedOrder(theirCustomer), "VERIFIED", "2.00", null);
        long mine = payment(order, "VERIFIED", "1.00", null);
        mvc.perform(get("/api/reports/missing-2307").with(asRep))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].paymentId").value(mine));
    }

    @Test
    void csvQuotesCommasAndIsAudited() throws Exception {
        payment(order, "VERIFIED", "1.00", null);
        int before = jdbc.queryForObject("select count(*) from audit_event where action = 'EXPORTED' and actor_id = ?",
                Integer.class, rep);
        mvc.perform(get("/api/reports/missing-2307.csv").param("customerId", String.valueOf(customer)).with(asRep))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", containsString("missing-2307.csv")))
                .andExpect(content().string(containsString("Payment,Order,Customer,Rep,Received,Withholding")))
                .andExpect(content().string(containsString("\"" + f.tag + "Dela Cruz, Inc.\"")));
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'EXPORTED' and actor_id = ? "
                + "and entity_type = 'REPORT'", Integer.class, rep)).isEqualTo(before + 1);
    }

    @Test
    void customerOrdersAndInvoicesScoped() throws Exception {
        jdbc.update("insert into invoice (number, order_id, order_revision, status, issued_at, due_date, customer_snapshot, "
                + "lines_snapshot, vatable_sales, vat, vat_exempt_sales, zero_rated_sales, shipping_fee, total, issued_by) "
                + "values (?, ?, 0, 'ISSUED', now(), current_date, '{}', '[]', 0, 0, 0, 0, 0, 1120, ?)",
                "SI-" + f.tag, order, rep);
        mvc.perform(get("/api/customers/" + customer + "/orders").with(asRep)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(order));
        mvc.perform(get("/api/customers/" + customer + "/invoices").with(asRep)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].orderNumber").isNotEmpty())
                .andExpect(jsonPath("$.content[0].paymentStatus").value("UNPAID"));
        long other = f.user("oth", Role.SALES);
        mvc.perform(get("/api/customers/" + customer + "/orders").with(as(other, Role.SALES)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/customers/" + customer + "/invoices").with(as(other, Role.SALES)))
                .andExpect(status().isNotFound());
    }
}
