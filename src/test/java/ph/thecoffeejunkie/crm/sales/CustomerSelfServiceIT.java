package ph.thecoffeejunkie.crm.sales;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.customers.Customers;
import ph.thecoffeejunkie.crm.shared.Role;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.asContact;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

/** O-04: a portal contact keeps their company's contacts, billing recipients and delivery addresses. */
@IntegrationTest
class CustomerSelfServiceIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Customers customers;

    SalesFixture f;
    long rep;
    long customer;
    long product;
    long meId;
    RequestPostProcessor me;
    RequestPostProcessor asRep;

    @BeforeEach
    void seed() {
        f = new SalesFixture(jdbc, mvc);
        rep = f.user("rep", Role.SALES);
        asRep = as(rep, Role.SALES);
        customer = f.customer("acme", rep);
        product = f.product("A", "112.00", null, "VATABLE");
        meId = f.contact(customer, "me", true);
        me = asContact(meId, customer);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private ResultActions send(MockHttpServletRequestBuilder b, String body) throws Exception {
        return mvc.perform(f.json(b, body).with(me));
    }

    private String contactJson(String name, String email, boolean portal, long version, boolean invoices) {
        return "{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"portalEnabled\":" + portal
                + ",\"version\":" + version + ",\"receivesInvoices\":" + invoices + "}";
    }

    @Test
    void contactsAreManagedFromThePortalAndDecideWhoGetsInvoices() throws Exception {
        String accountant = f.contactEmail("books");
        long books = SalesFixture.id(send(post("/api/portal/contacts"), contactJson("Books", accountant, false, 0, true))
                .andExpect(status().isCreated()));
        long ops = f.contact(customer, "ops", true);
        send(put("/api/portal/contacts/" + ops), contactJson("Ops", f.contactEmail("ops"), true, 0, false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.receivesInvoices").value(false));

        mvc.perform(get("/api/portal/contacts").with(me)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)));
        assertThat(customers.billingContactEmails(customer)).containsExactlyInAnyOrder(f.contactEmail("me"), accountant);
        assertThat(customers.portalContactEmails(customer)).containsExactlyInAnyOrder(f.contactEmail("me"), f.contactEmail("ops"));

        send(post("/api/portal/contacts/" + books + "/archive"), "{}").andExpect(status().isNoContent());
        assertThat(customers.billingContactEmails(customer)).containsExactly(f.contactEmail("me"));
    }

    @Test
    void aContactCantLockThemselfOut() throws Exception {
        send(put("/api/portal/contacts/" + meId), contactJson("Me", f.contactEmail("me"), false, 0, true))
                .andExpect(status().isBadRequest());
        send(put("/api/portal/contacts/" + meId), contactJson("Me", f.contactEmail("new"), true, 0, true))
                .andExpect(status().isBadRequest());
        send(post("/api/portal/contacts/" + meId + "/archive"), "{}").andExpect(status().is4xxClientError());
        send(put("/api/portal/contacts/" + meId), contactJson("Me Renamed", f.contactEmail("me"), true, 0, false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Me Renamed"));
    }

    @Test
    void anotherCompanysContactIsNotFound() throws Exception {
        long other = f.customer("other", rep);
        long theirs = f.contact(other, "theirs", true);
        send(put("/api/portal/contacts/" + theirs), contactJson("X", f.contactEmail("theirs"), true, 0, true))
                .andExpect(status().isNotFound());
        send(post("/api/portal/contacts/" + theirs + "/archive"), "{}").andExpect(status().isNotFound());
    }

    @Test
    void oneDefaultAddressAtATimeAndStaffCanSeeThem() throws Exception {
        long office = SalesFixture.id(send(post("/api/portal/addresses"),
                "{\"label\":\"Office\",\"address\":\"1 Ayala Ave, Makati\",\"defaultAddress\":false}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.defaultAddress").value(true))); // first one
        long cafe = SalesFixture.id(send(post("/api/portal/addresses"),
                "{\"label\":\"Cafe\",\"address\":\"2 Katipunan, QC\",\"defaultAddress\":true}")
                .andExpect(status().isCreated()));
        mvc.perform(get("/api/customers/" + customer + "/addresses").with(asRep)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(cafe)).andExpect(jsonPath("$[0].defaultAddress").value(true))
                .andExpect(jsonPath("$[1].id").value(office)).andExpect(jsonPath("$[1].defaultAddress").value(false));

        send(post("/api/portal/addresses/" + cafe + "/archive"), "{}").andExpect(status().isNoContent());
        mvc.perform(get("/api/portal/addresses").with(me)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].defaultAddress").value(false));
    }

    @Test
    void quoteRequestCarriesTheChosenAddressAndReleaseFallsBackToTheDefault() throws Exception {
        long other = f.customer("other", rep);
        long cafe = SalesFixture.id(send(post("/api/portal/addresses"),
                "{\"label\":\"Cafe\",\"address\":\"2 Katipunan, QC\",\"defaultAddress\":true}"));
        String body = "{\"lines\":[{\"productId\":" + product + ",\"qty\":1}],\"addressId\":" + cafe + "}";
        mvc.perform(f.json(post("/api/portal/quote-requests"), body).header("Idempotency-Key", UUID.randomUUID().toString())
                .with(me)).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select ship_to from sales_order where customer_id = ?", String.class, customer))
                .isEqualTo("2 Katipunan, QC");

        // someone else's address id is refused
        String theirs = "{\"lines\":[{\"productId\":" + product + ",\"qty\":1}],\"addressId\":" + cafe + "}";
        mvc.perform(f.json(post("/api/portal/quote-requests"), theirs).header("Idempotency-Key", UUID.randomUUID().toString())
                .with(asContact(f.contact(other, "o", true), other))).andExpect(status().isBadRequest());

        // a staff order with no ship-to delivers to the default address
        f.stock(product, "10");
        jdbc.update("update customer set credit_limit = 10000 where id = ?", customer);
        long order = f.createdOrder(asRep, customer, line(product, "1"));
        f.transition(asRep, order, "confirm", null).andExpect(status().isOk());
        f.transition(asRep, order, "release", null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select ship_to from delivery_order where sales_order_id = ?", String.class, order))
                .isEqualTo("2 Katipunan, QC");
        mvc.perform(get("/api/orders/" + order).with(asRep)).andExpect(jsonPath("$.shipTo").isEmpty());
    }
}
