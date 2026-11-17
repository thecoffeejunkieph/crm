package ph.thecoffeejunkie.crm.sales;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

@IntegrationTest
class OrdersIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    SalesFixture f;
    long admin;
    long rep;
    long customer;
    long productA;
    RequestPostProcessor asRep;
    RequestPostProcessor asAdmin;

    @BeforeEach
    void seed() {
        f = new SalesFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        rep = f.user("rep", Role.SALES);
        customer = f.customer("acme", rep);
        productA = f.product("A", "112.00", "50.00", "VATABLE");
        asRep = as(rep, Role.SALES);
        asAdmin = as(admin, Role.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    @Test
    void createNumbersAndAssignsRep() throws Exception {
        f.create(as(admin, Role.ADMIN), customer, line(productA, "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(matchesPattern("SO-\\d{2}-\\d{5}")))
                .andExpect(jsonPath("$.repId").value(rep))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.customerName").value(f.tag + "acme"));
    }

    @Test
    void defaultsFromCatalog() throws Exception {
        f.create(asRep, customer, line(productA, "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].unitPrice").value(112.00))
                .andExpect(jsonPath("$.lines[0].description").value("Product A"))
                .andExpect(jsonPath("$.lines[0].unit").value("PC"))
                .andExpect(jsonPath("$.lines[0].taxClass").value("VATABLE"))
                .andExpect(jsonPath("$.paymentTerms").value("NET_30"));
    }

    @Test
    void nonRegularCustomerGetsNetPrice() throws Exception {
        jdbc.update("update customer set tax_treatment = 'ZERO_RATED' where id = ?", customer);
        f.create(asRep, customer, line(productA, "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].unitPrice").value(100.00))
                .andExpect(jsonPath("$.totals.zeroRatedSales").value(100.00))
                .andExpect(jsonPath("$.totals.vat").value(0));
    }

    @Test
    void totalsCachedAndReturned() throws Exception {
        long id = SalesFixture.id(f.create(asRep, customer, line(productA, "3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.total").value(336.00))
                .andExpect(jsonPath("$.totals.vat").value(36.00))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(336.00))
                .andExpect(jsonPath("$.balance").value(336.00)));
        assertThat(jdbc.queryForObject("select total from sales_order where id = ?", String.class, id))
                .isEqualTo("336.00");
    }

    @Test
    void editingQuotedReturnsToDraft() throws Exception {
        long id = f.createdOrder(asRep, customer, line(productA, "1"));
        jdbc.update("update sales_order set status = 'QUOTED', customer_accepted_at = now() where id = ?", id);
        f.update(asRep, id, "", line(productA, "2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.customerAcceptedAt").doesNotExist());
    }

    @Test
    void staleVersion409() throws Exception {
        long id = f.createdOrder(asRep, customer, line(productA, "1"));
        mvc.perform(f.json(put("/api/orders/" + id), "{\"version\":99,\"lines\":[" + line(productA, "2") + "]}")
                .with(asRep)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void archivedProductAlreadyOnDraftStillSaves() throws Exception {
        long productB = f.product("B", "50.00", null, "VATABLE");
        long id = f.createdOrder(asRep, customer, line(productA, "1"), line(productB, "1"));
        jdbc.update("update product set archived_at = now() where id = ?", productA);
        f.update(asRep, id, "", line(productA, "1"), line(productB, "4")).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[1].qty").value(4));
    }

    @Test
    void newArchivedProductRejected() throws Exception {
        long productB = f.product("B", "50.00", null, "VATABLE");
        jdbc.update("update product set archived_at = now() where id = ?", productB);
        long id = f.createdOrder(asRep, customer, line(productA, "1"));
        f.update(asRep, id, "", line(productA, "1"), line(productB, "1")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[1].productId"));
        f.create(asRep, customer, line(productB, "1")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].productId"));
    }

    @Test
    void belowCostFlagOnlyForAdmin() throws Exception {
        long cheap = f.product("C", "100.00", "90.00", "VATABLE");
        long id = f.createdOrder(asRep, customer, line(cheap, "1", "100.00", "20"));
        mvc.perform(get("/api/orders/" + id).with(asAdmin)).andExpect(jsonPath("$.lines[0].belowCost").value(true));
        mvc.perform(get("/api/orders/" + id).with(asRep)).andExpect(jsonPath("$.lines[0].belowCost").doesNotExist());
    }

    @Test
    void historyShowsLineSummaryChange() throws Exception {
        long id = f.createdOrder(asRep, customer, line(productA, "1"));
        f.update(asRep, id, "\"notes\":\"call first\"", line(productA, "3")).andExpect(status().isOk());
        String body = SalesFixture.body(mvc.perform(get("/api/orders/" + id + "/history").with(asRep))
                .andExpect(status().isOk()));
        List<List<String>> lines = JsonPath.read(body, "$.content[?(@.action=='UPDATED')].changes.lines");
        assertThat(lines).containsExactly(List.of("1 line, total 112.00", "1 line, total 336.00"));
        List<List<String>> notes = JsonPath.read(body, "$.content[?(@.action=='UPDATED')].changes.notes");
        assertThat(notes.getFirst()).containsExactly(null, "call first");
    }

    @Test
    void paymentStatusFilter() throws Exception {
        long paid = f.createdOrder(asRep, customer, line(productA, "1"));
        long partial = f.createdOrder(asRep, customer, line(productA, "1"));
        long overdue = f.createdOrder(asRep, customer, line(productA, "1"));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Manila"));
        jdbc.update("update sales_order set status = 'CONFIRMED', settled_amount = total where id = ?", paid);
        jdbc.update("update sales_order set status = 'CONFIRMED', settled_amount = 40 where id = ?", partial);
        jdbc.update("update sales_order set status = 'CONFIRMED', due_date = ? where id = ?", today.minusDays(1), overdue);
        for (var c : List.of(List.of("PAID", paid), List.of("PARTIAL", partial), List.of("OVERDUE", overdue))) {
            mvc.perform(get("/api/orders").param("customerId", String.valueOf(customer))
                            .param("paymentStatus", (String) c.get(0)).with(asRep))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(c.get(1)))
                    .andExpect(jsonPath("$.content[0].paymentStatus").value(c.get(0)));
        }
    }

    @Test
    void draftHasNoPaymentStatusEvenAtZero() throws Exception {
        long draft = f.createdOrder(asRep, customer, line(productA, "1"));
        jdbc.update("update sales_order set total = 0, settled_amount = 0 where id = ?", draft);
        mvc.perform(get("/api/orders/" + draft).with(asRep)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.paymentStatus").doesNotExist());
        mvc.perform(get("/api/orders").param("customerId", String.valueOf(customer)).with(asRep))
                .andExpect(jsonPath("$.content[0].paymentStatus").doesNotExist());
    }

    @Test
    void sortByRepDesc() throws Exception {
        long zed = f.user("zed", Role.SALES), amy = f.user("amy", Role.SALES);
        long c = f.customer("repsort", null);
        long a = f.createdOrder(asAdmin, c, line(productA, "1"));
        long z = f.createdOrder(asAdmin, c, line(productA, "1"));
        jdbc.update("update sales_order set rep_id = ? where id = ?", amy, a);
        jdbc.update("update sales_order set rep_id = ? where id = ?", zed, z);
        mvc.perform(get("/api/orders").param("customerId", String.valueOf(c)).param("sort", "rep,desc").with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(z))
                .andExpect(jsonPath("$.content[1].id").value(a));
    }

    private List<Long> sortedIds(long cust, String sort) throws Exception {
        String body = SalesFixture.body(mvc.perform(get("/api/orders").param("customerId", String.valueOf(cust))
                .param("sort", sort).with(asAdmin)).andExpect(status().isOk()));
        return ((List<Number>) JsonPath.read(body, "$.content[*].id")).stream().map(Number::longValue).toList();
    }

    @Test
    void nullRepSortsLastBothWays() throws Exception {
        long amy = f.user("amy", Role.SALES);
        long c = f.customer("nullrep", null);
        long withRep = f.createdOrder(asAdmin, c, line(productA, "1"));
        long noRep = f.createdOrder(asAdmin, c, line(productA, "1"));
        jdbc.update("update sales_order set rep_id = ? where id = ?", amy, withRep);
        jdbc.update("update sales_order set rep_id = null where id = ?", noRep);
        assertThat(sortedIds(c, "rep,asc")).containsExactly(withRep, noRep);
        assertThat(sortedIds(c, "rep,desc")).containsExactly(withRep, noRep);
    }

    @Test
    void sortByPaymentStatus() throws Exception {
        long c = f.customer("paysort", null);
        long draft = f.createdOrder(asAdmin, c, line(productA, "1"));
        long[] o = new long[4]; // paid, overdue, partial, unpaid
        for (int i = 0; i < 4; i++) {
            o[i] = f.createdOrder(asAdmin, c, line(productA, "1"));
        }
        jdbc.update("update sales_order set status = 'CONFIRMED', due_date = current_date + 10 where id in (?, ?, ?, ?)",
                o[0], o[1], o[2], o[3]);
        jdbc.update("update sales_order set settled_amount = total where id = ?", o[0]);
        jdbc.update("update sales_order set due_date = current_date - 5 where id = ?", o[1]);
        jdbc.update("update sales_order set settled_amount = 1 where id = ?", o[2]);
        assertThat(sortedIds(c, "payment,asc")).containsExactly(o[3], o[2], o[1], o[0], draft);
        assertThat(sortedIds(c, "payment,desc")).containsExactly(o[0], o[1], o[2], o[3], draft);
    }

    @Test
    void sortByStatusFollowsLifecycle() throws Exception {
        long c = f.customer("statsort", null);
        long[] o = new long[4]; // cancelled, confirmed, draft, quoted
        for (int i = 0; i < 4; i++) {
            o[i] = f.createdOrder(asAdmin, c, line(productA, "1"));
        }
        jdbc.update("update sales_order set status = 'CANCELLED' where id = ?", o[0]);
        jdbc.update("update sales_order set status = 'CONFIRMED' where id = ?", o[1]);
        jdbc.update("update sales_order set status = 'QUOTED' where id = ?", o[3]);
        assertThat(sortedIds(c, "status,asc")).containsExactly(o[2], o[3], o[1], o[0]);
    }

    @Test
    void paymentSortUsesManilaDate() throws Exception {
        // ponytail: the DB clock can't be controlled; due = yesterday in Manila must rank OVERDUE (2) between
        // PARTIAL (1) and PAID (3). It discriminates from current_date whenever the DB date lags Manila's.
        long c = f.customer("manilasort", null);
        long paid = f.createdOrder(asAdmin, c, line(productA, "1"));
        long overdue = f.createdOrder(asAdmin, c, line(productA, "1"));
        long partial = f.createdOrder(asAdmin, c, line(productA, "1"));
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(1);
        jdbc.update("update sales_order set status = 'CONFIRMED', due_date = ? where id in (?, ?, ?)",
                yesterday.plusDays(10), paid, overdue, partial);
        jdbc.update("update sales_order set settled_amount = total where id = ?", paid);
        jdbc.update("update sales_order set due_date = ? where id = ?", yesterday, overdue);
        jdbc.update("update sales_order set settled_amount = 1 where id = ?", partial);
        assertThat(sortedIds(c, "payment,asc")).containsExactly(partial, overdue, paid);
    }

    @Test
    void sortByBalance() throws Exception {
        long c = f.customer("balsort", null);
        long big = f.createdOrder(asAdmin, c, line(productA, "3"));
        long small = f.createdOrder(asAdmin, c, line(productA, "1"));
        long paid = f.createdOrder(asAdmin, c, line(productA, "2"));
        jdbc.update("update sales_order set settled_amount = total where id = ?", paid);
        assertThat(sortedIds(c, "balance,asc")).containsExactly(paid, small, big);
        assertThat(sortedIds(c, "balance,desc")).containsExactly(big, small, paid);
    }

    private List<Long> searchIds(String q) throws Exception {
        String body = SalesFixture.body(mvc.perform(get("/api/orders").param("q", q).with(asAdmin))
                .andExpect(status().isOk()));
        return ((List<Number>) JsonPath.read(body, "$.content[*].id")).stream().map(Number::longValue).toList();
    }

    @Test
    void searchMatchesCustomerName() throws Exception {
        long c = f.customer("Zed Café", null);
        long mine = f.createdOrder(asAdmin, c, line(productA, "1"));
        f.createdOrder(asAdmin, customer, line(productA, "1"));
        assertThat(searchIds((f.tag + "zed café").toLowerCase())).containsExactly(mine);
    }

    @Test
    void searchStillMatchesNumber() throws Exception {
        long id = f.createdOrder(asAdmin, customer, line(productA, "1"));
        String number = jdbc.queryForObject("select number from sales_order where id = ?", String.class, id);
        assertThat(searchIds(number)).containsExactly(id);
    }

    @Test
    void searchEscapesWildcards() throws Exception {
        f.createdOrder(asAdmin, customer, line(productA, "1"));
        assertThat(searchIds("%")).isEmpty();
    }
}
