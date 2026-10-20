package ph.thecoffeejunkie.crm.sales;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.shared.ContactPrincipal;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

/** Seeds tagged users, customers and products for the sales ITs, and removes everything they created. */
final class SalesFixture {

    final String tag = "SF" + UUID.randomUUID().toString().substring(0, 8);
    private final JdbcTemplate jdbc;
    private final MockMvc mvc;

    SalesFixture(JdbcTemplate jdbc, MockMvc mvc) {
        this.jdbc = jdbc;
        this.mvc = mvc;
        // New orders ship from the default warehouse, so one must exist; ours is removed again in cleanUp.
        jdbc.update("insert into warehouse (code, name, is_default) select ?, 'Fixture', true "
                + "where not exists (select 1 from warehouse where is_default and archived_at is null)", tag + "WH");
    }

    long user(String name, Role role) {
        return jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) "
                + "values (?, ?, 'U', ?) returning id", Long.class, tag + name + "@sf.it", name, role.name());
    }

    /** BUSINESS, NET_30, no credit limit, REGULAR. */
    long customer(String name, Long repId) {
        return jdbc.queryForObject("insert into customer (type, display_name, tin, address, payment_terms, assigned_rep_id) "
                + "values ('BUSINESS', ?, '111-222-333', 'Makati', 'NET_30', ?) returning id", Long.class, tag + name, repId);
    }

    long contact(long customerId, String name, boolean portalEnabled) {
        return jdbc.queryForObject("insert into customer_contact (customer_id, name, email, portal_enabled) "
                + "values (?, ?, ?, ?) returning id", Long.class, customerId, name,
                (tag + name + "@contact.ph").toLowerCase(), portalEnabled);
    }

    String contactEmail(String name) {
        return (tag + name + "@contact.ph").toLowerCase();
    }

    long product(String sku, String price, String cost, String taxClass) {
        return jdbc.queryForObject("insert into product (sku, name, unit, price, cost, tax_class) "
                + "values (?, ?, 'PC', ?::numeric, ?::numeric, ?) returning id", Long.class,
                tag + sku, "Product " + sku, price, cost, taxClass);
    }

    static RequestPostProcessor as(long id, Role role) {
        return authentication(token(id, role));
    }

    static UsernamePasswordAuthenticationToken token(long id, Role role) {
        return new UsernamePasswordAuthenticationToken(new StaffPrincipal(id, "u" + id + "@sf.it", "Staff " + id, role,
                Set.of()), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    static RequestPostProcessor asContact(long contactId, long customerId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new ContactPrincipal(contactId, customerId, "c" + contactId + "@contact.ph", "Contact " + contactId),
                null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }

    static void login(long id, Role role) {
        SecurityContextHolder.getContext().setAuthentication(token(id, role));
    }

    static String line(long productId, String qty) {
        return "{\"productId\":" + productId + ",\"qty\":" + qty + "}";
    }

    static String line(long productId, String qty, String unitPrice, String discountPct) {
        return "{\"productId\":" + productId + ",\"qty\":" + qty + ",\"unitPrice\":" + unitPrice
                + ",\"discountPct\":" + discountPct + "}";
    }

    MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    ResultActions create(RequestPostProcessor who, long customerId, String... lines) throws Exception {
        return mvc.perform(json(post("/api/orders"), "{\"customerId\":" + customerId + ",\"lines\":["
                + String.join(",", lines) + "]}").with(who));
    }

    long createdOrder(RequestPostProcessor who, long customerId, String... lines) throws Exception {
        return id(create(who, customerId, lines).andExpect(status().isOk()));
    }

    ResultActions update(RequestPostProcessor who, long orderId, String extra, String... lines) throws Exception {
        return mvc.perform(json(put("/api/orders/" + orderId), "{\"version\":" + version(orderId)
                + (extra.isEmpty() ? "" : "," + extra) + ",\"lines\":[" + String.join(",", lines) + "]}").with(who));
    }

    /** POST to an order transition endpoint with the current version. */
    ResultActions transition(RequestPostProcessor who, long orderId, String action, String reason) throws Exception {
        return mvc.perform(json(post("/api/orders/" + orderId + "/" + action), "{\"version\":" + version(orderId)
                + (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}").with(who));
    }

    long version(long orderId) {
        return jdbc.queryForObject("select version from sales_order where id = ?", Long.class, orderId);
    }

    /** Sets the order's status directly, for tests that start mid-lifecycle. */
    void setStatus(long orderId, String status) {
        jdbc.update("update sales_order set status = ? where id = ?", status, orderId);
    }

    static long id(ResultActions r) throws Exception {
        return ((Number) JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    static String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    void cleanUp() {
        SecurityContextHolder.clearContext();
        String orders = "select o.id from sales_order o join customer c on c.id = o.customer_id where c.display_name like '"
                + tag + "%'";
        jdbc.update("delete from payment where order_id in (" + orders + ")");
        jdbc.update("update invoice set replaced_by_id = null where order_id in (" + orders + ")");
        jdbc.update("delete from invoice where order_id in (" + orders + ")");
        jdbc.update("delete from order_revision where order_id in (" + orders + ")");
        jdbc.update("delete from order_line where order_id in (" + orders + ")");
        jdbc.update("delete from approval_request where requested_by in (select id from app_user where email like ?)",
                tag + "%");
        jdbc.update("delete from notification where recipient_user_id in (select id from app_user where email like ?)",
                tag + "%");
        jdbc.update("delete from sales_order where customer_id in (select id from customer where display_name like ?)",
                tag + "%");
        jdbc.update("delete from customer_contact where customer_id in (select id from customer where display_name like ?)",
                tag + "%");
        jdbc.update("delete from customer where display_name like ?", tag + "%");
        jdbc.update("delete from product where sku like ?", tag + "%");
        jdbc.update("delete from warehouse where code = ?", tag + "WH");
        jdbc.update("delete from app_user where email like ?", tag + "%");
    }
}
