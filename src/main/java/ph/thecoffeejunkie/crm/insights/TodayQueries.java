package ph.thecoffeejunkie.crm.insights;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.insights.TodayQueue.Item;
import ph.thecoffeejunkie.crm.insights.TodayQueue.Section;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The Today queue. ADMIN: company-wide. SALES: orders of customers assigned to them (who can open the order, as in
 * "needs action") and their own pending approval requests. Follow-ups are always the caller's own.
 */
@Component
class TodayQueries {

    static final int SHOWN = 5;
    private static final String MANILA = "(%s at time zone 'Asia/Manila')::date";
    /** The prefix PortalOrderService writes on a portal quote request (O-02). */
    private static final String PORTAL_REQUEST = "Requested via portal by %";
    /** Approvals point at orders, releases, invoices or refunds: resolve them to their order. */
    private static final String APPROVAL_ORDER = "case a.subject_type when 'ORDER' then a.subject_id "
            + "when 'ORDER_RELEASE' then a.subject_id "
            + "when 'INVOICE' then (select i.order_id from invoice i where i.id = a.subject_id) "
            + "when 'REFUND' then (select r.order_id from refund r where r.id = a.subject_id) end";

    private final NamedParameterJdbcTemplate jdbc;

    TodayQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    TodayQueue forUser(long userId, boolean admin, LocalDate today) {
        MapSqlParameterSource p = new MapSqlParameterSource("me", userId).addValue("today", today)
                .addValue("limit", SHOWN).addValue("portal", PORTAL_REQUEST);
        String mine = admin ? "" : " and c.assigned_rep_id = :me";
        String orders = " from sales_order o join customer c on c.id = o.customer_id where ";

        Section toVerify = section(p, "from payment y join sales_order o on o.id = y.order_id "
                        + "join customer c on c.id = o.customer_id where y.status = 'PENDING'" + mine,
                "select y.id, o.id order_id, o.number || ' · ' || c.display_name title, initcap(replace(y.method, '_', ' ')) detail, "
                        + "y.cash_amount + y.withholding_amount amount, " + MANILA.formatted("y.created_at") + " since ",
                " order by y.created_at, y.id");
        Section toRelease = section(p, orders.substring(1) + "o.status = 'CONFIRMED'" + mine,
                "select o.id, o.id order_id, o.number || ' · ' || c.display_name title, "
                        + "case when o.settled_amount >= o.total then 'Paid' else 'Not fully paid' end detail, "
                        + "o.total amount, " + MANILA.formatted("o.confirmed_at") + " since ",
                " order by o.confirmed_at nulls last, o.id");
        Section quoteRequests = section(p, orders.substring(1) + "o.status = 'DRAFT' and o.notes like :portal" + mine,
                "select o.id, o.id order_id, o.number || ' · ' || c.display_name title, 'Price and send the quote' detail, "
                        + "null::numeric amount, " + MANILA.formatted("o.created_at") + " since ",
                " order by o.created_at, o.id");
        Section followUps = section(p, "from follow_up f join customer c on c.id = f.customer_id "
                        + "where f.done_at is null and f.due_date <= :today and f.assignee_id = :me",
                "select f.id, null::bigint order_id, f.title title, c.display_name detail, null::numeric amount, f.due_date since ",
                " order by f.due_date, f.id");
        Section approvals = section(p, "from approval_request a where a.status = 'PENDING'"
                        + (admin ? "" : " and a.requested_by = :me"),
                "select a.id, " + APPROVAL_ORDER + " order_id, "
                        + "initcap(replace(a.type, '_', ' ')) || ' approval' title, a.reason detail, null::numeric amount, "
                        + MANILA.formatted("a.requested_at") + " since ",
                " order by a.requested_at, a.id");
        return new TodayQueue(toVerify, toRelease, quoteRequests, followUps, approvals);
    }

    private Section section(MapSqlParameterSource p, String from, String select, String order) {
        long total = jdbc.queryForObject("select count(*) " + from, p, Long.class);
        List<Item> items = total == 0 ? List.of() : jdbc.query(select + from + order + " limit :limit", p,
                (rs, i) -> new Item(rs.getLong("id"), rs.getObject("order_id", Long.class), rs.getString("title"),
                        rs.getString("detail"), rs.getObject("amount", BigDecimal.class),
                        rs.getObject("since", LocalDate.class)));
        return new Section(total, items);
    }
}
