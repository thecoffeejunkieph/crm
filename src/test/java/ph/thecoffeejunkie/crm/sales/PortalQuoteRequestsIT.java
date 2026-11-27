package ph.thecoffeejunkie.crm.sales;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.asContact;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

@IntegrationTest
class PortalQuoteRequestsIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    SalesFixture f;
    long rep;
    long customer;
    long product;
    RequestPostProcessor me;

    @BeforeEach
    void seed() {
        f = new SalesFixture(jdbc, mvc);
        rep = f.user("rep", Role.SALES);
        customer = f.customer("acme", rep);
        product = f.product("A", "112.00", "100.00", "VATABLE");
        me = asContact(f.contact(customer, "cee", true), customer);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private ResultActions request(String body) throws Exception {
        return mvc.perform(f.json(post("/api/portal/quote-requests"), body)
                .header("Idempotency-Key", UUID.randomUUID().toString()).with(me));
    }

    @Test
    void createsDraftAtCatalogPriceAndTellsTheRep() throws Exception {
        String number = SalesFixture.body(request("{\"lines\":[{\"productId\":" + product + ",\"qty\":3}],\"notes\":\"by Friday\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.number").exists()));
        Map<String, Object> o = jdbc.queryForMap("select id, status, rep_id, created_by, notes, total from sales_order"
                + " where customer_id = ?", customer);
        assertThat(number).contains((String) jdbc.queryForObject("select number from sales_order where id = ?",
                String.class, o.get("id")));
        assertThat(o.get("status")).isEqualTo("DRAFT");
        assertThat(((Number) o.get("rep_id")).longValue()).isEqualTo(rep);
        assertThat(((Number) o.get("created_by")).longValue()).isEqualTo(rep);
        assertThat((String) o.get("notes")).startsWith("Requested via portal by").endsWith("by Friday");
        assertThat(o.get("total").toString()).isEqualTo("336.00");
        assertThat(jdbc.queryForObject("select count(*) from notification where recipient_user_id = ? and type = 'QUOTE_REQUESTED'",
                Integer.class, rep)).isEqualTo(1);
        // still a draft: the customer doesn't see it until staff send the quote
        mvc.perform(get("/api/portal/orders").with(me)).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void rejectsArchivedProductAndEmptyRequest() throws Exception {
        jdbc.update("update product set archived_at = now() where id = ?", product);
        request("{\"lines\":[{\"productId\":" + product + ",\"qty\":1}]}").andExpect(status().isBadRequest());
        request("{\"lines\":[]}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from sales_order where customer_id = ?", Integer.class, customer))
                .isZero();
    }

    @Test
    void productListHasNoPriceOrCost() throws Exception {
        String body = SalesFixture.body(mvc.perform(get("/api/portal/products").with(me)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(product)));
        assertThat(body).doesNotContainIgnoringCase("price").doesNotContainIgnoringCase("cost");
    }

    @Test
    void reorderSkipsArchivedProductsAndOtherCompanies() throws Exception {
        long archived = f.product("B", "50.00", "40.00", "VATABLE");
        RequestPostProcessor asRep = as(rep, Role.SALES);
        long id = f.createdOrder(asRep, customer, line(product, "2"), line(archived, "1"));
        jdbc.update("update sales_order set status = 'QUOTED', quoted_at = now(), quote_valid_until = ? where id = ?",
                LocalDate.now(ZoneId.of("Asia/Manila")).plusDays(15), id);
        jdbc.update("update product set archived_at = now() where id = ?", archived);
        mvc.perform(get("/api/portal/orders/" + id + "/reorder").with(me)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(product))
                .andExpect(jsonPath("$[0].qty").value(2));
        long other = f.customer("other", rep);
        long theirs = f.createdOrder(asRep, other, line(product, "1"));
        jdbc.update("update sales_order set status = 'QUOTED', quoted_at = now() where id = ?", theirs);
        mvc.perform(get("/api/portal/orders/" + theirs + "/reorder").with(me)).andExpect(status().isNotFound());
    }
}
