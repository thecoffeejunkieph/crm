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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

/** Seeds follow-up data with JDBC. Everything carries {@link #tag} and is removed by {@link #cleanUp()}. */
class FollowUpsFixture {

    final String tag = "FU" + UUID.randomUUID().toString().substring(0, 8);
    final LocalDate today = FollowUpDay.today(Clock.systemUTC());
    private final JdbcTemplate jdbc;
    private final MockMvc mvc;
    private int seq = 1;

    FollowUpsFixture(JdbcTemplate jdbc, MockMvc mvc) {
        this.jdbc = jdbc;
        this.mvc = mvc;
        jdbc.update("insert into warehouse (code, name, is_default) select ?, 'Fixture', true "
                + "where not exists (select 1 from warehouse where is_default and archived_at is null)", tag + "WH");
    }

    long user(String name, Role role) {
        return jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) "
                + "values (?, ?, 'U', ?) returning id", Long.class, tag + name + "@fu.it", tag + name, role.name());
    }

    static RequestPostProcessor as(long id, Role role) {
        return authentication(new UsernamePasswordAuthenticationToken(new StaffPrincipal(id, "u" + id + "@fu.it",
                "Staff " + id, role, Set.of()), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
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

    void cleanUp() {
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
