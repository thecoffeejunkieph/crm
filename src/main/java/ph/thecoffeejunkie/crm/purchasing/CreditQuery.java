package ph.thecoffeejunkie.crm.purchasing;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Balance, committed and available credit, computed from the ledger tables every time (never stored). */
@Component
@Transactional(readOnly = true)
class CreditQuery {

    private static final BigDecimal NEAR = new BigDecimal("0.9");

    private final NamedParameterJdbcTemplate jdbc;

    CreditQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** One SQL per call. Ids that match no supplier are absent from the result. */
    Map<Long, SupplierCredit> forSuppliers(Collection<Long> ids) {
        Map<Long, SupplierCredit> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        jdbc.query("select s.id, s.credit_limit, s.opening_balance"
                // Received value per receipt line, the same expression as statement(), so balance == statement closing.
                + " + coalesce((select sum(round(rl.qty * pl.unit_cost, 2)) from stock_receipt r"
                + "    join purchase_order o on o.id = r.purchase_order_id join stock_receipt_line rl on rl.receipt_id = r.id"
                + "    join purchase_order_line pl on pl.po_id = o.id and pl.product_id = rl.product_id"
                + "    where o.supplier_id = s.id), 0)"
                + " - coalesce((select sum(p.amount) from supplier_payment p where p.supplier_id = s.id), 0) balance,"
                + " coalesce((select sum(round((l.qty - l.received_qty) * l.unit_cost, 2)) from purchase_order o"
                + "    join purchase_order_line l on l.po_id = o.id where o.supplier_id = s.id and not o.closed_short"
                + "    and o.status in ('PENDING_APPROVAL','SENT','PARTIALLY_RECEIVED')), 0) committed"
                + " from supplier s where s.id in (:ids)", Map.of("ids", ids), rs -> {
                    BigDecimal limit = rs.getBigDecimal("credit_limit");
                    BigDecimal balance = rs.getBigDecimal("balance");
                    BigDecimal committed = rs.getBigDecimal("committed");
                    BigDecimal available = null;
                    String flag = null;
                    if (limit != null) {
                        BigDecimal used = balance.add(committed);
                        available = limit.subtract(used);
                        flag = available.signum() < 0 ? "OVER"
                                : limit.signum() > 0 && used.compareTo(limit.multiply(NEAR)) >= 0 ? "NEAR" : null;
                    }
                    out.put(rs.getLong("id"), new SupplierCredit(limit, balance, committed, available, flag));
                });
        return out;
    }

    /** Opening balance and running balance over receipts (PO-linked only) and payments; null from/to are unbounded. */
    SupplierStatementResponse statement(long supplierId, LocalDate from, LocalDate to) {
        BigDecimal running = jdbc.queryForObject("select opening_balance from supplier where id = :id",
                Map.of("id", supplierId), BigDecimal.class);
        List<SupplierStatementResponse.SupplierStatementEntry> all = new ArrayList<>();
        // Receipt value: each receipt line at its PO line's unit cost (one line per product per PO), rounded per line.
        jdbc.query("select * from ("
                + " select r.received_on d, 0 ord, r.id, 'RECEIPT' kind, r.number ref,"
                + "  sum(round(rl.qty * pl.unit_cost, 2)) amt"
                + "  from stock_receipt r join purchase_order o on o.id = r.purchase_order_id"
                + "  join stock_receipt_line rl on rl.receipt_id = r.id"
                + "  join purchase_order_line pl on pl.po_id = o.id and pl.product_id = rl.product_id"
                + "  where o.supplier_id = :id group by r.id, r.received_on, r.number"
                + " union all"
                + " select p.paid_on, 1, p.id, 'PAYMENT', case when po.id is null then p.reference else 'Downpayment – ' || po.number end, -p.amount"
                + " from supplier_payment p left join purchase_order po on po.id = p.purchase_order_id where p.supplier_id = :id"
                + ") e order by d, ord, id", Map.of("id", supplierId), rs -> {
                    all.add(new SupplierStatementResponse.SupplierStatementEntry(rs.getObject("d", LocalDate.class), rs.getString("kind"),
                            rs.getString("ref"), rs.getBigDecimal("amt"), null));
                });
        List<SupplierStatementResponse.SupplierStatementEntry> shown = new ArrayList<>();
        BigDecimal opening = null;
        for (SupplierStatementResponse.SupplierStatementEntry e : all) {
            if (from != null && e.date().isBefore(from)) {
                running = running.add(e.amount());
                continue;
            }
            if (to != null && e.date().isAfter(to)) {
                break;
            }
            if (opening == null) {
                opening = running;
            }
            running = running.add(e.amount());
            shown.add(new SupplierStatementResponse.SupplierStatementEntry(e.date(), e.kind(), e.reference(), e.amount(), scale(running)));
        }
        return new SupplierStatementResponse(scale(opening == null ? running : opening), shown, scale(running));
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
