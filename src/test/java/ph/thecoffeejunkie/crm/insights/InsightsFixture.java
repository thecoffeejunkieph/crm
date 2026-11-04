package ph.thecoffeejunkie.crm.insights;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

/**
 * Seeds insights data straight into the tables, so dates are exact. Everything carries {@link #tag} and is removed
 * by {@link #cleanUp()}. Company-wide figures are shared with other tests: assert them on a quiet month or as deltas.
 */
class InsightsFixture {

    final String tag = "IF" + UUID.randomUUID().toString().substring(0, 8);
    private final JdbcTemplate jdbc;
    private final MockMvc mvc;

    InsightsFixture(JdbcTemplate jdbc, MockMvc mvc) {
        this.jdbc = jdbc;
        this.mvc = mvc;
        // Orders need a ship-from warehouse; ours is removed again in cleanUp.
        jdbc.update("insert into warehouse (code, name, is_default) select ?, 'Fixture', true "
                + "where not exists (select 1 from warehouse where is_default and archived_at is null)", tag + "WH");
    }

    long user(String name, Role role) {
        return jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) "
                + "values (?, ?, 'U', ?) returning id", Long.class, tag + name + "@if.it", tag + name, role.name());
    }

    static RequestPostProcessor as(long id, Role role) {
        return as(id, role, Set.of());
    }

    static RequestPostProcessor as(long id, Role role, Set<Long> warehouseIds) {
        return authentication(new UsernamePasswordAuthenticationToken(new StaffPrincipal(id, "u" + id + "@if.it",
                "Staff " + id, role, warehouseIds), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    long customer(String name, Long repId) {
        return jdbc.queryForObject("insert into customer (type, display_name, tin, address, payment_terms, assigned_rep_id) "
                + "values ('BUSINESS', ?, '111-222-333', 'Makati', 'NET_30', ?) returning id", Long.class, tag + name, repId);
    }

    long product(String name, String cost) {
        return jdbc.queryForObject("insert into product (sku, name, unit, price, cost, tax_class) "
                + "values (?, ?, 'PC', 100, ?::numeric, 'VATABLE') returning id", Long.class, tag + name, tag + name, cost);
    }

    long warehouse(String code) {
        return jdbc.queryForObject("insert into warehouse (code, name) values (?, ?) returning id", Long.class,
                tag + code, tag + code);
    }

    private long anyWarehouse() {
        return jdbc.queryForObject("select id from warehouse where archived_at is null order by is_default desc, id limit 1",
                Long.class);
    }

    private long creator() {
        return jdbc.queryForObject("select id from app_user order by id limit 1", Long.class);
    }

    /** A B2B order; {@code due} may be null. */
    long order(long customerId, Long repId, String status, String total, String settled, LocalDate due) {
        return jdbc.queryForObject("insert into sales_order (number, customer_id, rep_id, status, payment_terms, total, "
                + "settled_amount, due_date, created_by, ship_from_warehouse_id, confirmed_at) "
                + "values (?, ?, ?, ?, 'NET_30', ?::numeric, ?::numeric, ?, ?, ?, now()) returning id", Long.class,
                tag + "-" + seq++, customerId, repId, status, total, settled, due, creator(), anyWarehouse());
    }

    void set(long orderId, String column, Object value) {
        jdbc.update("update sales_order set " + column + " = ? where id = ?", value, orderId);
    }

    long line(long orderId, long productId, String qty, String lineTotal, String unitCost) {
        return jdbc.queryForObject("insert into order_line (order_id, position, product_id, description, unit, qty, "
                + "unit_price, tax_class, line_total, unit_cost) values (?, 0, ?, 'L', 'PC', ?::numeric, ?::numeric, "
                + "'VATABLE', ?::numeric, ?::numeric) returning id", Long.class, orderId, productId, qty, lineTotal,
                lineTotal, unitCost);
    }

    long invoice(long orderId, String status, Instant issuedAt, String total) {
        return jdbc.queryForObject("insert into invoice (number, order_id, order_revision, status, issued_at, due_date, "
                + "customer_snapshot, lines_snapshot, vatable_sales, vat, vat_exempt_sales, zero_rated_sales, "
                + "shipping_fee, total, issued_by) values (?, ?, 0, ?, ?, current_date, '{}', '[]', 0, 0, 0, 0, 0, "
                + "?::numeric, ?) returning id", Long.class, tag + "-SI" + seq++, orderId, status,
                Timestamp.from(issuedAt), total, creator());
    }

    long payment(long orderId, String status, String cash, String withholding, Instant verifiedAt) {
        LocalDate received = verifiedAt == null ? LocalDate.now() : verifiedAt.atZone(InsightsMonth.MANILA).toLocalDate();
        return payment(orderId, status, cash, withholding, received, verifiedAt);
    }

    long payment(long orderId, String status, String cash, String withholding, LocalDate receivedOn, Instant verifiedAt) {
        return jdbc.queryForObject("insert into payment (order_id, status, method, cash_amount, withholding_amount, "
                + "received_on, submitted_by_type, submitted_by_id, verified_at) values (?, ?, 'CASH', ?::numeric, "
                + "?::numeric, ?, 'USER', ?, ?) returning id", Long.class, orderId, status, cash, withholding,
                receivedOn, creator(), verifiedAt == null ? null : Timestamp.from(verifiedAt));
    }

    /** An instant {@code hours} after the start of {@code m} in Manila. */
    static Instant at(YearMonth m, long hours) {
        return InsightsMonth.start(m).plus(Duration.ofHours(hours));
    }

    void cleanUp() {
        String users = "select id from app_user where email like '" + tag + "%'";
        String orders = "select o.id from sales_order o join customer c on c.id = o.customer_id where c.display_name like '"
                + tag + "%'";
        jdbc.update("delete from delivery_order where sales_order_id in (" + orders + ") or customer_name like ?", tag + "%");
        jdbc.update("delete from stock_transfer where number like ?", tag + "%");
        jdbc.update("delete from stock_adjustment where number like ?", tag + "%");
        jdbc.update("delete from payment where order_id in (" + orders + ")");
        jdbc.update("update invoice set replaced_by_id = null where order_id in (" + orders + ")");
        jdbc.update("delete from invoice where order_id in (" + orders + ")");
        jdbc.update("delete from order_line where order_id in (" + orders + ")");
        jdbc.update("delete from sales_order where customer_id in (select id from customer where display_name like ?)",
                tag + "%");
        jdbc.update("delete from customer where display_name like ?", tag + "%");
        jdbc.update("delete from product where sku like ?", tag + "%");
        jdbc.update("delete from user_warehouse where warehouse_id in (select id from warehouse where code like ?)",
                tag + "%");
        jdbc.update("delete from warehouse where code like ?", tag + "%");
        jdbc.update("delete from sales_target where rep_id in (" + users + ") or updated_by in (" + users + ")");
        jdbc.update("delete from app_user where email like ?", tag + "%");
    }

    private int seq = 1;
}
