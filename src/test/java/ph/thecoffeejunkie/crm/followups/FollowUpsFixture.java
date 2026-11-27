package ph.thecoffeejunkie.crm.followups;

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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

/** Seeds follow-up data with JDBC. Everything carries {@link #tag} and is removed by {@link #cleanUp()}. */
class FollowUpsFixture {

    final String tag = "FU" + UUID.randomUUID().toString().substring(0, 8);
    final LocalDate today = FollowUpDay.today(Clock.systemUTC());
    private final JdbcTemplate jdbc;
    private final MockMvc mvc;
    private static final AtomicInteger DAYS = new AtomicInteger();
    private final List<LocalDate> usedDays = new ArrayList<>();
    private int seq = 1;

    FollowUpsFixture(JdbcTemplate jdbc, MockMvc mvc) {
        this.jdbc = jdbc;
        this.mvc = mvc;
        jdbc.update("insert into warehouse (code, name, is_default) select ?, 'Fixture', true "
                + "where not exists (select 1 from warehouse where is_default and archived_at is null)", tag + "WH");
    }

    long user(String name, Role role) {
        return jdbc.queryForObject("with u as (insert into app_user (email, first_name, last_name) "
                + "values (?, ?, 'U') returning id) insert into user_role select id, ? from u returning user_id", Long.class, tag + name + "@fu.it", tag + name, role.name());
    }

    static RequestPostProcessor as(long id, Role role) {
        return authentication(new UsernamePasswordAuthenticationToken(new StaffPrincipal(id, "u" + id + "@fu.it",
                "Staff " + id, Set.of(role), Set.of()), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    long customer(String name, Long repId) {
        return jdbc.queryForObject("insert into customer (type, display_name, tin, address, payment_terms, assigned_rep_id) "
                + "values ('BUSINESS', ?, '111-222-333', 'Makati', 'NET_30', ?) returning id", Long.class, tag + name, repId);
    }

    long order(long customerId) {
        return jdbc.queryForObject("insert into sales_order (number, customer_id, status, payment_terms, total, "
                + "settled_amount, created_by, ship_from_warehouse_id) values (?, ?, 'DRAFT', 'NET_30', 0, 0, "
                + "(select id from app_user order by id limit 1), "
                + "(select id from warehouse where archived_at is null order by is_default desc, id limit 1)) returning id",
                Long.class, tag + "-SO" + seq++, customerId);
    }

    String orderNumber(long orderId) {
        return jdbc.queryForObject("select number from sales_order where id = ?", String.class, orderId);
    }

    /** A follow-up written straight to the table; {@code doneAt} may be null. */
    long followUp(long customerId, Long orderId, long assigneeId, String title, LocalDate due, Instant doneAt) {
        return jdbc.queryForObject("insert into follow_up (customer_id, order_id, assignee_id, title, due_date, done_at, "
                + "done_by, created_by) values (?, ?, ?, ?, ?, ?, ?, ?) returning id", Long.class, customerId, orderId,
                assigneeId, title, due, doneAt == null ? null : Timestamp.from(doneAt), doneAt == null ? null : assigneeId,
                assigneeId);
    }

    /**
     * A base day owning the next 200 days that no other test uses, so the engine's once-a-day marker never collides:
     * deliver relative to it, then run the engine on {@code base.plusDays(n)}.
     */
    LocalDate newDay() {
        LocalDate d = LocalDate.of(2000, 1, 1).plusDays(200L * DAYS.incrementAndGet());
        usedDays.add(d);
        return d;
    }

    /** A noon Manila instant on that date. */
    static Instant at(LocalDate d) {
        return d.atTime(12, 0).atZone(FollowUpDay.MANILA).toInstant();
    }

    /** An order in the given status; {@code confirmedAt} may be null. */
    long salesOrder(long customerId, Long repId, String status, Instant confirmedAt) {
        return jdbc.queryForObject("insert into sales_order (number, customer_id, rep_id, status, payment_terms, total, "
                + "settled_amount, created_by, confirmed_at, ship_from_warehouse_id) values (?, ?, ?, ?, 'NET_30', 0, 0, "
                + "(select id from app_user order by id limit 1), ?, "
                + "(select id from warehouse where archived_at is null order by is_default desc, id limit 1)) returning id",
                Long.class, tag + "-SO" + seq++, customerId, repId, status,
                confirmedAt == null ? null : Timestamp.from(confirmedAt));
    }

    /** A confirmed order that was delivered on {@code date} (Manila). */
    long delivered(long customerId, Long repId, LocalDate date) {
        long id = salesOrder(customerId, repId, "FULFILLED", at(date).minusSeconds(3600));
        jdbc.update("insert into delivery_order (number, sales_order_id, order_number, rep_user_id, warehouse_id, method, "
                + "status, delivered_at, customer_name) select ?, id, number, created_by, ship_from_warehouse_id, "
                + "'DELIVERY', 'DELIVERED', ?, 'x' from sales_order where id = ?",
                tag + "-DO" + seq++, Timestamp.from(at(date)), id);
        return id;
    }

    void cleanUp() {
        usedDays.forEach(d -> jdbc.update("delete from auto_follow_up_run where day >= ? and day < ?", d, d.plusDays(200)));
        jdbc.update("delete from delivery_order where sales_order_id in (select id from sales_order where customer_id in "
                + "(select id from customer where display_name like ?))", tag + "%");
        String users = "select id from app_user where email like '" + tag + "%'";
        String customers = "select id from customer where display_name like '" + tag + "%'";
        jdbc.update("delete from follow_up where customer_id in (" + customers + ")");
        jdbc.update("delete from notification where recipient_user_id in (" + users + ")");
        jdbc.update("delete from sales_order where customer_id in (" + customers + ")");
        jdbc.update("delete from customer where display_name like ?", tag + "%");
        jdbc.update("delete from warehouse where code like ?", tag + "%");
        jdbc.update("delete from app_user where email like ?", tag + "%");
    }
}
