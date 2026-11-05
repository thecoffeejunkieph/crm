package ph.thecoffeejunkie.crm.attachments;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.Scope;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/** Seeds users, customers, orders, invoices and deliveries with JDBC; all tagged and removed by {@link #cleanUp()}. */
class AttachmentsFixture {

    final String tag = "AT" + UUID.randomUUID().toString().substring(0, 8);
    private final JdbcTemplate jdbc;
    private int seq = 1;

    AttachmentsFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        jdbc.update("insert into warehouse (code, name, is_default) select ?, 'Fixture', true "
                + "where not exists (select 1 from warehouse where is_default and archived_at is null)", tag + "WH");
    }

    long user(String name, Role role) {
        return jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) values (?, ?, 'U', ?) "
                + "returning id", Long.class, tag + name + "@at.it", tag + name, role.name());
    }

    static Scope scope(long id, Role role, Long... warehouses) {
        return new Scope(id, role, Set.of(warehouses));
    }

    static RequestPostProcessor as(long id, Role role, Long... warehouses) {
        return authentication(new UsernamePasswordAuthenticationToken(new StaffPrincipal(id, "u" + id + "@at.it",
                "Staff " + id, role, Set.of(warehouses)), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    long customer(String name, Long repId) {
        return jdbc.queryForObject("insert into customer (type, display_name, tin, address, payment_terms, assigned_rep_id) "
                + "values ('BUSINESS', ?, '111-222-333', 'Makati', 'NET_30', ?) returning id", Long.class, tag + name, repId);
    }

    void archiveCustomer(long id) {
        jdbc.update("update customer set archived_at = now() where id = ?", id);
    }

    long warehouse(String code) {
        return jdbc.queryForObject("insert into warehouse (code, name) values (?, ?) returning id", Long.class,
                tag + code, tag + code);
    }

    long order(long customerId, String status) {
        return jdbc.queryForObject("insert into sales_order (number, customer_id, status, payment_terms, total, "
                + "settled_amount, created_by, ship_from_warehouse_id) values (?, ?, ?, 'NET_30', 0, 0, "
                + "(select id from app_user order by id limit 1), "
                + "(select id from warehouse where archived_at is null order by is_default desc, id limit 1)) returning id",
                Long.class, tag + "-SO" + seq++, customerId, status);
    }

    long invoice(long orderId, String status) {
        return jdbc.queryForObject("insert into invoice (number, order_id, order_revision, status, issued_at, due_date, "
                + "customer_snapshot, lines_snapshot, vatable_sales, vat, vat_exempt_sales, zero_rated_sales, "
                + "shipping_fee, total, issued_by) values (?, ?, 0, ?, now(), current_date, '{}', '[]', 0, 0, 0, 0, 0, "
                + "0, (select id from app_user order by id limit 1)) returning id", Long.class, tag + "-SI" + seq++,
                orderId, status);
    }

    long delivery(long orderId, long warehouseId) {
        return jdbc.queryForObject("insert into delivery_order (number, sales_order_id, warehouse_id, method, status, "
                + "customer_name, order_number, rep_user_id) values (?, ?, ?, 'DELIVERY', 'PICKING', ?, 'SO-X', "
                + "(select id from app_user order by id limit 1)) returning id", Long.class, tag + "-DO" + seq++,
                orderId, warehouseId, tag + "cust");
    }

    void cleanUp() {
        String customers = "select id from customer where display_name like '" + tag + "%'";
        String orders = "select id from sales_order where customer_id in (" + customers + ")";
        jdbc.update("delete from attachment where uploaded_by in (select id from app_user where email like ?)", tag + "%");
        jdbc.update("delete from delivery_order where sales_order_id in (" + orders + ")");
        jdbc.update("delete from invoice where order_id in (" + orders + ")");
        jdbc.update("delete from sales_order where customer_id in (" + customers + ")");
        jdbc.update("delete from customer where display_name like ?", tag + "%");
        jdbc.update("delete from warehouse where code like ?", tag + "%");
        jdbc.update("delete from app_user where email like ?", tag + "%");
    }
}
