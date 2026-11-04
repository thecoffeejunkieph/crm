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

/**
 * All dashboard SQL (spec 2026-11-05, "Figures"). A null {@code repId} means company-wide; otherwise only orders
 * credited to that rep. Month bounds are Manila midnights.
 */
@Component
class InsightsQueries {

    static final String MANILA_DATE = "(%s at time zone 'Asia/Manila')::date";
    private static final String PAID = "p.cash_amount + p.withholding_amount";
    private static final String HAS_INVOICE = "exists (select 1 from invoice i where i.order_id = o.id and i.status = 'ISSUED')";

    private final NamedParameterJdbcTemplate jdbc;

    InsightsQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Balance(BigDecimal outstanding, BigDecimal overdue) {
    }

    BigDecimal collected(Long repId, YearMonth m) {
        MapSqlParameterSource p = month(m);
        return money(jdbc.queryForObject("select sum(" + PAID + ") from payment p join sales_order o on o.id = p.order_id "
                + "where p.status = 'VERIFIED' and p.verified_at >= :start and p.verified_at < :end" + rep(repId, p),
                p, BigDecimal.class));
    }

    BigDecimal invoiced(Long repId, YearMonth m) {
        MapSqlParameterSource p = month(m);
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
        jdbc.query("select " + MANILA_DATE.formatted("p.verified_at") + " d, sum(" + PAID + ") amount from payment p "
                + "join sales_order o on o.id = p.order_id where p.status = 'VERIFIED' and p.verified_at >= :start "
                + "and p.verified_at < :end" + rep(repId, p) + " group by 1", p,
                rs -> {
                    byDay.put(rs.getObject("d", LocalDate.class), rs.getBigDecimal("amount"));
                });
        List<DayAmount> days = new ArrayList<>();
        for (int d = 1; d <= m.lengthOfMonth(); d++) {
            days.add(new DayAmount(m.atDay(d), money(byDay.get(m.atDay(d)))));
        }
        return days;
    }

    List<CustomerAmount> topCustomers(Long repId, YearMonth m, int limit) {
        MapSqlParameterSource p = month(m).addValue("limit", limit);
        return jdbc.query("select c.id, c.display_name, sum(" + PAID + ") amount from payment p "
                + "join sales_order o on o.id = p.order_id join customer c on c.id = o.customer_id "
                + "where p.status = 'VERIFIED' and p.verified_at >= :start and p.verified_at < :end" + rep(repId, p)
                + " group by c.id, c.display_name order by amount desc, c.display_name limit :limit", p,
                (rs, i) -> new CustomerAmount(rs.getLong("id"), rs.getString("display_name"),
                        money(rs.getBigDecimal("amount"))));
    }

    NeedsAction needsAction(long repId, LocalDate today, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("rep", repId).addValue("today", today)
                .addValue("limit", limit);
        String select = "select o.id, o.number, c.display_name, %s amount, %s since from sales_order o "
                + "join customer c on c.id = o.customer_id where o.rep_id = :rep and ";
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

    private List<OrderRef> refs(String sql, MapSqlParameterSource p) {
        return jdbc.query(sql, p, (rs, i) -> new OrderRef(rs.getLong("id"), rs.getString("number"),
                rs.getString("display_name"), money(rs.getBigDecimal("amount")), rs.getObject("since", LocalDate.class)));
    }

    static MapSqlParameterSource month(YearMonth m) {
        return new MapSqlParameterSource("start", Timestamp.from(InsightsMonth.start(m)))
                .addValue("end", Timestamp.from(InsightsMonth.end(m)));
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
