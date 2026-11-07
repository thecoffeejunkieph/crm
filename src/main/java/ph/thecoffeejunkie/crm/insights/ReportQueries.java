package ph.thecoffeejunkie.crm.insights;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Report SQL over verified payments (spec 2026-11-07). Every query shares {@link #FILTER}. */
@Component
class ReportQueries {

    private static final String PAID = "p.cash_amount + p.withholding_amount";
    private static final String FILTER = " from payment p join sales_order o on o.id = p.order_id "
            + "where p.status = 'VERIFIED' and p.verified_at >= :start and p.verified_at < :end";

    /** {@code all}: no rep filter; otherwise only orders credited to {@code repId}, or unassigned when it is null. */
    record RepFilter(boolean all, Long repId) {

        static RepFilter everyone() {
            return new RepFilter(true, null);
        }

        String sql() {
            return all ? "" : repId == null ? " and o.rep_id is null" : " and o.rep_id = :rep";
        }
    }

    record Sum(BigDecimal amount, long payments) {
    }

    record Named(Long id, String name, Sum sum) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    ReportQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Sum total(ReportRange r, RepFilter f) {
        return jdbc.queryForObject("select sum(" + PAID + ") amount, count(distinct p.id) n" + FILTER + f.sql(),
                params(r, f), (rs, i) -> new Sum(rs.getBigDecimal("amount"), rs.getLong("n")));
    }

    /** Keyed YYYY-MM; months without payments are absent. */
    Map<String, Sum> byMonth(ReportRange r, RepFilter f) {
        Map<String, Sum> out = new HashMap<>();
        jdbc.query("select to_char(p.verified_at at time zone 'Asia/Manila', 'YYYY-MM') ym, sum(" + PAID + ") amount, "
                + "count(distinct p.id) n" + FILTER + f.sql() + " group by 1", params(r, f),
                rs -> {
                    out.put(rs.getString("ym"), new Sum(rs.getBigDecimal("amount"), rs.getLong("n")));
                });
        return out;
    }

    /** Keyed by credited rep; the null key is unassigned money. */
    Map<Long, Sum> byRep(ReportRange r, RepFilter f) {
        Map<Long, Sum> out = new HashMap<>();
        jdbc.query("select o.rep_id, sum(" + PAID + ") amount, count(distinct p.id) n" + FILTER + f.sql()
                + " group by o.rep_id", params(r, f), rs -> {
                    out.put(rs.getObject("rep_id", Long.class), new Sum(rs.getBigDecimal("amount"), rs.getLong("n")));
                });
        return out;
    }

    /** Highest collected first, then name. */
    List<Named> byCustomer(ReportRange r, RepFilter f) {
        return jdbc.query("select c.id, c.display_name, sum(" + PAID + ") amount, count(distinct p.id) n"
                + FILTER.replace(" where ", " join customer c on c.id = o.customer_id where ") + f.sql()
                + " group by c.id, c.display_name order by amount desc, c.display_name", params(r, f),
                (rs, i) -> new Named(rs.getLong("id"), rs.getString("display_name"),
                        new Sum(rs.getBigDecimal("amount"), rs.getLong("n"))));
    }

    private static MapSqlParameterSource params(ReportRange r, RepFilter f) {
        return new MapSqlParameterSource("start", Timestamp.from(r.start()))
                .addValue("end", Timestamp.from(r.end()))
                .addValue("rep", f.repId());
    }
}
