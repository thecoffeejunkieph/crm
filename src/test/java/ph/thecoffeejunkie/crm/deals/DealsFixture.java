package ph.thecoffeejunkie.crm.deals;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

/** Seeds deal data with JDBC. Everything carries {@link #tag} and is removed by {@link #cleanUp()}. */
public class DealsFixture {

    public final String tag = "DL" + UUID.randomUUID().toString().substring(0, 6);
    private final JdbcTemplate jdbc;
    private final MockMvc mvc;
    private int seq = 1;

    public DealsFixture(JdbcTemplate jdbc, MockMvc mvc) {
        this.jdbc = jdbc;
        this.mvc = mvc;
        jdbc.update("insert into warehouse (code, name, is_default) select ?, 'Fixture', true "
                + "where not exists (select 1 from warehouse where is_default and archived_at is null)", tag + "WH");
    }

    public long user(String name, Role role) {
        return jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) values (?, ?, 'U', ?) "
                + "returning id", Long.class, tag + name + "@dl.it", tag + name, role.name());
    }

    public static RequestPostProcessor as(long id, Role role) {
        return authentication(new UsernamePasswordAuthenticationToken(new StaffPrincipal(id, "u" + id + "@dl.it",
                "Staff " + id, role, Set.of()), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    public MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    public static long id(ResultActions r) throws Exception {
        return ((Number) JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    public long customer(String name, Long repId) {
        return jdbc.queryForObject("insert into customer (type, display_name, payment_terms, assigned_rep_id) "
                + "values ('INDIVIDUAL', ?, 'NET_30', ?) returning id", Long.class, tag + name, repId);
    }

    public void archive(long customerId) {
        jdbc.update("update customer set archived_at = now() where id = ?", customerId);
    }

    /** A deal straight in the table; title is prefixed with the tag. */
    public long deal(long customerId, Long repId, String title, String stage, BigDecimal value, LocalDate close) {
        return jdbc.queryForObject("insert into deal (customer_id, rep_id, title, stage, value, expected_close, "
                + "lost_reason, created_by) values (?, ?, ?, ?, ?, ?, ?, (select id from app_user order by id limit 1)) "
                + "returning id", Long.class, customerId, repId, tag + title, stage, value, close,
                "LOST".equals(stage) ? "Went elsewhere" : null);
    }

    public long order(long customerId, String status) {
        return jdbc.queryForObject("insert into sales_order (number, customer_id, status, payment_terms, total, "
                + "settled_amount, created_by, ship_from_warehouse_id, confirmed_at) values (?, ?, ?, 'NET_30', 1500, 0, "
                + "(select id from app_user order by id limit 1), "
                + "(select id from warehouse where archived_at is null order by is_default desc, id limit 1), "
                + "case when ? in ('CONFIRMED','RELEASED','FULFILLED') then now() end) returning id",
                Long.class, tag + "-SO" + seq++, customerId, status, status);
    }

    public void link(long dealId, long orderId) {
        jdbc.update("insert into deal_order (deal_id, order_id, linked_at, linked_by) values (?, ?, now(), "
                + "(select id from app_user order by id limit 1))", dealId, orderId);
    }

    public String orderNumber(long orderId) {
        return jdbc.queryForObject("select number from sales_order where id = ?", String.class, orderId);
    }

    public int audits(String action, long id) {
        return jdbc.queryForObject("select count(*) from audit_event where action = ? and entity_type = 'DEAL' "
                + "and entity_id = ?", Integer.class, action, id);
    }

    public void cleanUp() {
        String customers = "select id from customer where display_name like '" + tag + "%'";
        jdbc.update("delete from deal_order where deal_id in (select id from deal where customer_id in (" + customers + "))");
        jdbc.update("delete from deal where customer_id in (" + customers + ")");
        jdbc.update("delete from sales_order where customer_id in (" + customers + ")");
        jdbc.update("delete from customer where display_name like ?", tag + "%");
        jdbc.update("delete from warehouse where code like ?", tag + "%");
        jdbc.update("delete from app_user where email like ?", tag + "%");
    }
}
