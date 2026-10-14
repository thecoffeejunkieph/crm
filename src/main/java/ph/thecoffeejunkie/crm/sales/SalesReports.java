package ph.thecoffeejunkie.crm.sales;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.customers.Customers;
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Verified payments with withholding but no BIR Form 2307 yet (spec §4), scoped like orders. */
@Service
class SalesReports {

    private final NamedParameterJdbcTemplate jdbc;
    private final Customers customers;
    private final Users users;
    private final AuditLog audit;

    SalesReports(NamedParameterJdbcTemplate jdbc, Customers customers, Users users, AuditLog audit) {
        this.jdbc = jdbc;
        this.customers = customers;
        this.users = users;
        this.audit = audit;
    }

    private record Raw(long paymentId, long orderId, String orderNumber, long customerId, Long repId,
                       LocalDate receivedOn, BigDecimal withholdingAmount) {
    }

    @Transactional(readOnly = true)
    PageResponse<Missing2307Row> missing2307(Scope s, Long customerId, Long repId, LocalDate from, LocalDate to,
                                            PageQuery page) {
        Pageable p = page.toPageable(Set.of("receivedOn"), Sort.by("receivedOn"));
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(s, customerId, repId, from, to, params);
        if (where == null) {
            return PageResponse.of(new PageImpl<>(List.of(), p, 0));
        }
        long total = jdbc.queryForObject("select count(*) " + where, params, Long.class);
        params.addValue("limit", p.getPageSize()).addValue("offset", p.getOffset());
        List<Raw> raw = jdbc.query(SELECT + where + ORDER + " limit :limit offset :offset", params, SalesReports::raw);
        return PageResponse.of(new PageImpl<>(rows(raw), p, total));
    }

    @Transactional
    String missing2307Csv(Scope s, Long customerId, Long repId, LocalDate from, LocalDate to) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(s, customerId, repId, from, to, params);
        List<Missing2307Row> rows = where == null ? List.of()
                : rows(jdbc.query(SELECT + where + ORDER, params, SalesReports::raw));
        StringBuilder csv = new StringBuilder("Payment,Order,Customer,Rep,Received,Withholding\r\n");
        for (Missing2307Row r : rows) {
            csv.append(String.join(",", cell(r.paymentId()), cell(r.orderNumber()), cell(r.customerName()),
                    cell(r.repName()), cell(r.receivedOn()), cell(r.withholdingAmount().toPlainString()))).append("\r\n");
        }
        audit.record(AuditAction.EXPORTED, "REPORT", null, "Exported missing-2307 report (" + rows.size() + " rows)",
                null);
        return csv.toString();
    }

    private static final String SELECT = "select p.id, p.order_id, o.number, o.customer_id, o.rep_id, p.received_on, "
            + "p.withholding_amount ";
    private static final String ORDER = " order by p.received_on, p.id";

    /** null = the scope sees nothing. */
    private String where(Scope s, Long customerId, Long repId, LocalDate from, LocalDate to,
                         MapSqlParameterSource params) {
        StringBuilder w = new StringBuilder("from payment p join sales_order o on o.id = p.order_id "
                + "where p.status = 'VERIFIED' and p.withholding_amount > 0 and p.form2307_key is null");
        Optional<Set<Long>> visible = customers.visibleCustomerIds(s);
        if (visible.isPresent()) {
            if (visible.get().isEmpty()) {
                return null;
            }
            w.append(" and o.customer_id in (:visible)");
            params.addValue("visible", visible.get());
        }
        if (customerId != null) {
            w.append(" and o.customer_id = :customerId");
            params.addValue("customerId", customerId);
        }
        if (repId != null) {
            w.append(" and o.rep_id = :repId");
            params.addValue("repId", repId);
        }
        if (from != null) {
            w.append(" and p.received_on >= :from");
            params.addValue("from", from);
        }
        if (to != null) {
            w.append(" and p.received_on <= :to");
            params.addValue("to", to);
        }
        return w.toString();
    }

    private static Raw raw(ResultSet rs, int i) throws SQLException {
        return new Raw(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4), (Long) rs.getObject(5),
                rs.getObject(6, LocalDate.class), rs.getBigDecimal(7));
    }

    private List<Missing2307Row> rows(List<Raw> raw) {
        Map<Long, String> customerNames = customers.displayNames(raw.stream().map(Raw::customerId).toList());
        Map<Long, String> repNames = users.displayNames(raw.stream().map(Raw::repId).filter(Objects::nonNull).toList());
        return raw.stream().map(r -> new Missing2307Row(r.paymentId(), r.orderId(), r.orderNumber(),
                customerNames.get(r.customerId()), r.repId() == null ? null : repNames.get(r.repId()), r.receivedOn(),
                r.withholdingAmount())).toList();
    }

    /** RFC 4180: quote when the value has a comma, quote or line break; double inner quotes. */
    static String cell(Object v) {
        String s = v == null ? "" : v.toString();
        return s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")
                ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
