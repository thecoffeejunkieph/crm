package ph.thecoffeejunkie.crm.insights;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.insights.SalesInsights.CustomerAmount;
import ph.thecoffeejunkie.crm.insights.SalesInsights.DayAmount;
import ph.thecoffeejunkie.crm.insights.SalesInsights.NeedsAction;
import ph.thecoffeejunkie.crm.insights.SalesInsights.OrderRef;
import ph.thecoffeejunkie.crm.insights.SalesInsights.ToVerify;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All dashboard SQL (spec 2026-11-05, "Figures"). A null {@code repId} means company-wide; otherwise only orders
 * credited to that rep. Period bounds are Manila midnights.
 */
@Component
class InsightsQueries {

    /** A line's revenue after its share of the whole-order discount (net_total is only set when there is one). */
    static final String NET = "coalesce(l.net_total, l.line_total)";

    static final String MANILA_DATE = "(%s at time zone 'Asia/Manila')::date";
    private static final String PAID = "p.cash_amount + p.withholding_amount";
    /**
     * The order's goods net of VAT, after the whole-order discount, without shipping. The stored sales figures are
     * already net of VAT and discount but include shipping (net of VAT when the order carries VAT, in full otherwise).
     */
    static final String NET_GOODS = "(o.vatable_sales + o.vat_exempt_sales + o.zero_rated_sales"
            + " - case when o.vat > 0 then round(o.shipping_fee / 1.12, 2) else o.shipping_fee end)";
    /** A payment's share of the order's net goods: what counts toward a rep's target and commission. */
    static final String CREDIT = "case when o.total > 0 then (" + PAID + ") * " + NET_GOODS + " / o.total else 0 end";
    private static final String HAS_INVOICE = "exists (select 1 from invoice i where i.order_id = o.id and i.status = 'ISSUED')";

    private final NamedParameterJdbcTemplate jdbc;

    InsightsQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Balance(BigDecimal outstanding, BigDecimal overdue) {
    }

    BigDecimal collected(Long repId, Period m) {
        return verifiedSum(PAID, repId, m, null);
    }

    /** Net sales collected: VAT, shipping and discounts excluded. Targets and commission run on this. */
    BigDecimal netSales(Long repId, YearMonth m) {
        return verifiedSum(CREDIT, repId, Period.of(m), null);
    }

    /** As {@link #netSales(Long, YearMonth)} without one payment: the figure before that payment was verified. */
    BigDecimal netSales(Long repId, YearMonth m, long excludePaymentId) {
        return verifiedSum(CREDIT, repId, Period.of(m), excludePaymentId);
    }

    private BigDecimal verifiedSum(String amount, Long repId, Period m, Long excludePaymentId) {
        MapSqlParameterSource p = m.params().addValue("exclude", excludePaymentId);
        return money(jdbc.queryForObject("select sum(" + amount + ") from money_movement p join sales_order o on o.id = p.order_id "
                + "where p.at >= :start and p.at < :end"
                + (excludePaymentId == null ? "" : " and p.id <> :exclude") + rep(repId, p),
                p, BigDecimal.class));
    }

    BigDecimal invoiced(Long repId, Period m) {
        MapSqlParameterSource p = m.params();
        return money(jdbc.queryForObject("select sum(i.total) from invoice i join sales_order o on o.id = i.order_id "
                + "where i.status = 'ISSUED' and i.issued_at >= :start and i.issued_at < :end" + rep(repId, p),
                p, BigDecimal.class));
    }

    Balance outstanding(Long repId, LocalDate today) {
        MapSqlParameterSource p = new MapSqlParameterSource("today", today);
        return jdbc.queryForObject("select sum(o.total - o.settled_amount) owed, "
                + "sum(case when o.due_date < :today then o.total - o.settled_amount end) late from sales_order o "
                + "where o.status <> 'CANCELLED' and o.total > o.settled_amount and " + HAS_INVOICE + rep(repId, p),
                p, (rs, i) -> new Balance(money(rs.getBigDecimal("owed")), money(rs.getBigDecimal("late"))));
    }

    ToVerify toVerify(Long repId) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        return jdbc.queryForObject("select count(*) n, sum(" + PAID + ") amount from payment p "
                + "join sales_order o on o.id = p.order_id where p.status = 'PENDING'" + rep(repId, p),
                p, (rs, i) -> new ToVerify(rs.getLong("n"), money(rs.getBigDecimal("amount"))));
    }

    /** Every day of the month, zeros included. */
    List<DayAmount> collectedByDay(Long repId, YearMonth m) {
        MapSqlParameterSource p = month(m);
        Map<LocalDate, BigDecimal> byDay = new HashMap<>();
        jdbc.query("select " + MANILA_DATE.formatted("p.at") + " d, sum(" + PAID + ") amount from money_movement p "
                + "join sales_order o on o.id = p.order_id where p.at >= :start "
                + "and p.at < :end" + rep(repId, p) + " group by 1", p,
                rs -> {
                    byDay.put(rs.getObject("d", LocalDate.class), rs.getBigDecimal("amount"));
                });
        List<DayAmount> days = new ArrayList<>();
        for (int d = 1; d <= m.lengthOfMonth(); d++) {
            days.add(new DayAmount(m.atDay(d), money(byDay.get(m.atDay(d)))));
        }
        return days;
    }

    List<CustomerAmount> topCustomers(Long repId, Period m, int limit) {
        MapSqlParameterSource p = m.params().addValue("limit", limit);
        return jdbc.query("select c.id, c.display_name, sum(" + PAID + ") amount from money_movement p "
                + "join sales_order o on o.id = p.order_id join customer c on c.id = o.customer_id "
                + "where p.at >= :start and p.at < :end" + rep(repId, p)
                + " group by c.id, c.display_name order by amount desc, c.display_name limit :limit", p,
                (rs, i) -> new CustomerAmount(rs.getLong("id"), rs.getString("display_name"),
                        money(rs.getBigDecimal("amount"))));
    }

    /** Work lists follow the customer's current rep (who can open the order), unlike credit, which stays put. */
    NeedsAction needsAction(long repId, LocalDate today, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("rep", repId).addValue("today", today)
                .addValue("limit", limit);
        String select = "select o.id, o.number, c.display_name, %s amount, %s since from sales_order o "
                + "join customer c on c.id = o.customer_id where c.assigned_rep_id = :rep and ";
        String balance = "o.total - o.settled_amount";
        List<OrderRef> quotes = refs(select.formatted("o.total", MANILA_DATE.formatted("o.updated_at"))
                + "o.status = 'QUOTED' order by o.updated_at, o.id limit :limit", p);
        List<OrderRef> unpaid = refs(select.formatted(balance, MANILA_DATE.formatted("o.confirmed_at"))
                + "o.status in ('CONFIRMED', 'RELEASED') and o.settled_amount < o.total "
                + "order by o.confirmed_at nulls last, o.id limit :limit", p);
        List<OrderRef> overdue = refs(select.formatted(balance, "o.due_date")
                + "o.status <> 'CANCELLED' and o.settled_amount < o.total and o.due_date < :today and " + HAS_INVOICE
                + " order by o.due_date, o.id limit :limit", p);
        return new NeedsAction(quotes, unpaid, overdue);
    }

    /** Lines of billable orders confirmed in the month; revenue = ex-VAT, after the order discount; cost = unit_cost × qty. */
    AdminInsights.Margin margin(Period m) {
        return jdbc.queryForObject("select sum(" + LINE_NET + " - l.unit_cost * l.qty) filter (where l.unit_cost is not null) amt, "
                + "sum(" + LINE_NET + ") filter (where l.unit_cost is not null) rev, "
                + "count(*) filter (where l.unit_cost is null) missing from order_line l " + SOLD_IN_MONTH,
                m.params(), (rs, i) -> {
                    BigDecimal amount = money(rs.getBigDecimal("amt"));
                    BigDecimal rev = rs.getBigDecimal("rev");
                    BigDecimal pct = rev == null || rev.signum() == 0 ? null
                            : amount.multiply(new BigDecimal(100)).divide(rev, 2, RoundingMode.HALF_UP);
                    return new AdminInsights.Margin(amount, pct, rs.getLong("missing"));
                });
    }

    /** Booked sales: what Collected will be paid for, whether or not an invoice was issued yet. */
    private static final String SOLD_IN_MONTH = "join sales_order o on o.id = l.order_id "
            + "and o.status in ('CONFIRMED', 'RELEASED', 'FULFILLED') and o.confirmed_at >= :start and o.confirmed_at < :end";
    /**
     * A line's revenue ex-VAT: its after-discount amount scaled by the order's net goods over its gross goods.
     * ponytail: one ratio per order, so a mixed VATable/exempt order spreads VAT evenly; per-line tax if that matters.
     */
    private static final String LINE_NET = "(" + NET + " * " + NET_GOODS + " / nullif(o.total - o.shipping_fee, 0))";

    /** Collected per day (week, month) or per month (year) of the period, empty buckets included, oldest first. */
    List<DayAmount> collectedTrend(Period p) {
        boolean monthly = p.unit().equals("year");
        String bucket = monthly ? "date_trunc('month', p.at at time zone 'Asia/Manila')::date" : MANILA_DATE.formatted("p.at");
        Map<LocalDate, BigDecimal> by = new HashMap<>();
        jdbc.query("select " + bucket + " d, sum(" + PAID + ") amount from money_movement p "
                + "where p.at >= :start and p.at < :end group by 1", p.params(),
                rs -> {
                    by.put(rs.getObject("d", LocalDate.class), rs.getBigDecimal("amount"));
                });
        List<DayAmount> out = new ArrayList<>();
        for (LocalDate d = p.from(); d.isBefore(p.to()); d = monthly ? d.plusMonths(1) : d.plusDays(1)) {
            out.add(new DayAmount(d, money(by.get(d))));
        }
        return out;
    }

    record RepAmounts(BigDecimal collected, BigDecimal netSales) {
    }

    /** Collected and net sales per credited rep; the null key holds orders with no rep. */
    Map<Long, RepAmounts> collectedByRep(Period m) {
        Map<Long, RepAmounts> out = new HashMap<>();
        jdbc.query("select o.rep_id, sum(" + PAID + ") amount, sum(" + CREDIT + ") net from money_movement p "
                + "join sales_order o on o.id = p.order_id "
                + "where p.at >= :start and p.at < :end group by o.rep_id",
                m.params(), rs -> {
                    long id = rs.getLong("rep_id");
                    out.put(rs.wasNull() ? null : id,
                            new RepAmounts(money(rs.getBigDecimal("amount")), money(rs.getBigDecimal("net"))));
                });
        return out;
    }

    /**
     * By revenue net of goods returned in the month (refund lines paid in the month, ex-VAT like the sales above);
     * margin counts only lines with a cost and is not reduced by returns.
     */
    List<AdminInsights.ProductRow> topProducts(Period m, int limit) {
        return jdbc.query("select pr.id, pr.name, coalesce(s.rev, 0) - coalesce(b.amt, 0) rev, s.margin from product pr "
                + "left join (select l.product_id pid, sum(" + LINE_NET + ") rev, "
                + "sum(" + LINE_NET + " - l.unit_cost * l.qty) filter (where l.unit_cost is not null) margin "
                + "from order_line l " + SOLD_IN_MONTH + " group by l.product_id) s on s.pid = pr.id "
                + "left join (select ol.product_id pid, sum(rl.amount * " + NET_GOODS + " / nullif(o.total - o.shipping_fee, 0)) amt "
                + "from refund_line rl join refund r on r.id = rl.refund_id and r.status = 'PAID' "
                + "and r.paid_at >= :start and r.paid_at < :end join order_line ol on ol.id = rl.order_line_id "
                + "join sales_order o on o.id = r.order_id group by ol.product_id) b on b.pid = pr.id "
                + "where s.pid is not null or b.pid is not null order by rev desc, pr.name limit :limit",
                m.params().addValue("limit", limit),
                (rs, i) -> new AdminInsights.ProductRow(rs.getLong("id"), rs.getString("name"),
                        money(rs.getBigDecimal("rev")), money(rs.getBigDecimal("margin"))));
    }

    AdminInsights.Operations operations() {
        return jdbc.queryForObject("select "
                + "(select count(*) from delivery_order where status = 'PICKING') picking, "
                + "(select count(*) from delivery_order where status = 'DISPATCHED') dispatched, "
                + "(select count(*) from stock_transfer where status = 'DISPATCHED') transit, "
                + "(select count(*) from stock_adjustment where status = 'PENDING_APPROVAL') adjustments",
                new MapSqlParameterSource(), (rs, i) -> new AdminInsights.Operations(rs.getLong("picking"),
                        rs.getLong("dispatched"), rs.getLong("transit"), rs.getLong("adjustments")));
    }

    /** {@code warehouseIds} null = every warehouse; empty = none (all zeros). */
    WarehouseInsights warehouse(Set<Long> warehouseIds, LocalDate today) {
        if (warehouseIds != null && warehouseIds.isEmpty()) {
            return new WarehouseInsights(new WarehouseInsights.Today(0, 0, 0), new WarehouseInsights.Transfers(0, 0),
                    List.of());
        }
        MapSqlParameterSource p = new MapSqlParameterSource("today", today).addValue("ids", warehouseIds);
        String mine = warehouseIds == null ? "true" : "d.warehouse_id in (:ids)";
        String from = warehouseIds == null ? "true" : "t.from_warehouse_id in (:ids)";
        String to = warehouseIds == null ? "true" : "t.to_warehouse_id in (:ids)";
        WarehouseInsights.Today t = jdbc.queryForObject("select "
                + "count(*) filter (where d.status = 'PICKING') picking, "
                + "count(*) filter (where d.status = 'DISPATCHED') dispatched, "
                + "count(*) filter (where d.status = 'DELIVERED' and " + MANILA_DATE.formatted("d.delivered_at")
                + " = :today) delivered from delivery_order d where " + mine, p,
                (rs, i) -> new WarehouseInsights.Today(rs.getLong("picking"), rs.getLong("dispatched"),
                        rs.getLong("delivered")));
        WarehouseInsights.Transfers tr = jdbc.queryForObject("select "
                + "(select count(*) from stock_transfer t where t.status = 'DRAFT' and " + from + ") out, "
                + "(select count(*) from stock_transfer t where t.status = 'DISPATCHED' and " + to + ") inc", p,
                (rs, i) -> new WarehouseInsights.Transfers(rs.getLong("out"), rs.getLong("inc")));
        List<WarehouseInsights.DeliveryRef> queue = jdbc.query("select d.id, d.number, d.customer_name, d.status, "
                + "d.created_at from delivery_order d where d.status in ('PICKING', 'DISPATCHED') and " + mine
                + " order by d.created_at, d.id limit 10", p,
                (rs, i) -> new WarehouseInsights.DeliveryRef(rs.getLong("id"), rs.getString("number"),
                        rs.getString("customer_name"), rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()));
        return new WarehouseInsights(t, tr, queue);
    }

    private List<OrderRef> refs(String sql, MapSqlParameterSource p) {
        return jdbc.query(sql, p, (rs, i) -> new OrderRef(rs.getLong("id"), rs.getString("number"),
                rs.getString("display_name"), money(rs.getBigDecimal("amount")), rs.getObject("since", LocalDate.class)));
    }

    static MapSqlParameterSource month(YearMonth m) {
        return Period.of(m).params();
    }

    private static String rep(Long repId, MapSqlParameterSource p) {
        if (repId == null) {
            return "";
        }
        p.addValue("rep", repId);
        return " and o.rep_id = :rep";
    }

    static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }
}
