package ph.thecoffeejunkie.crm.insights;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.insights.ReportQueries.RepFilter;
import ph.thecoffeejunkie.crm.insights.ReportQueries.Sum;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Sales reports (spec 2026-11-07): scope, then rows. SALES sees only orders credited to them, without cost. */
@Service
@Transactional(readOnly = true)
class ReportService {

    static final String UNASSIGNED = "Unassigned";
    static final String SHIPPING = "Shipping";
    private static final BigDecimal HUNDRED = new BigDecimal(100);

    private final ReportQueries queries;
    private final Users users;

    ReportService(ReportQueries queries, Users users) {
        this.queries = queries;
        this.users = users;
    }

    SalesReport report(Scope s, ReportBy by, ReportRange r, Long repId) {
        boolean admin = s.role() == Role.ADMIN;
        if (!admin && (by == ReportBy.REP || repId != null)) {
            throw new AccessDeniedException("Reps see their own figures only.");
        }
        RepFilter f = !admin ? new RepFilter(false, s.userId())
                : repId == null ? RepFilter.everyone()
                : new RepFilter(false, repId == 0 ? null : repId);
        Sum total = queries.total(r, f);
        BigDecimal collected = money(total.amount());
        List<ReportRow> rows = switch (by) {
            case MONTH -> months(r, f);
            case REP -> reps(r, f, collected);
            case CUSTOMER -> queries.byCustomer(r, f).stream()
                    .map(n -> row(n.id(), n.name(), n.sum(), collected)).toList();
            case PRODUCT -> products(r, f, admin);
        };
        return new SalesReport(r.from(), r.to(), by, new SalesReport.Total(collected, total.payments()), rows);
    }

    private List<ReportRow> months(ReportRange r, RepFilter f) {
        Map<String, Sum> found = queries.byMonth(r, f);
        List<ReportRow> rows = new ArrayList<>();
        for (YearMonth m : r.months()) {
            Sum sum = found.getOrDefault(m.toString(), new Sum(BigDecimal.ZERO, 0));
            rows.add(new ReportRow(m.toString(), null, null, money(sum.amount()), sum.payments(), null, null, null, null));
        }
        return rows;
    }

    /** Every active rep (zeros included), plus anyone else with money in range and "Unassigned". */
    private List<ReportRow> reps(ReportRange r, RepFilter f, BigDecimal total) {
        Map<Long, Sum> found = new HashMap<>(queries.byRep(r, f));
        Set<Long> ids = new HashSet<>(users.activeIdsWithRole(Role.SALES));
        found.keySet().stream().filter(Objects::nonNull).forEach(ids::add);
        Map<Long, String> names = users.displayNames(ids);
        List<ReportRow> rows = new ArrayList<>();
        for (Long id : ids) {
            rows.add(row(id, names.getOrDefault(id, "User " + id), found.getOrDefault(id, new Sum(BigDecimal.ZERO, 0)),
                    total));
        }
        if (found.containsKey(null)) {
            rows.add(row(null, UNASSIGNED, found.get(null), total));
        }
        rows.sort(Comparator.comparing(ReportRow::collected).reversed().thenComparing(ReportRow::name));
        return rows;
    }

    /** Products plus one "Shipping" row, highest first; cost fields only for ADMIN. Rows are rounded one by one. */
    private List<ReportRow> products(ReportRange r, RepFilter f, boolean withCost) {
        List<ReportRow> rows = new ArrayList<>();
        for (ReportQueries.ProductSum p : queries.byProduct(r, f)) {
            BigDecimal amount = money(p.collected());
            if (amount.signum() == 0) {
                continue;
            }
            rows.add(new ReportRow(null, p.id(), p.name(), amount, null, null,
                    withCost && p.margin() != null ? money(p.margin()) : null,
                    withCost && p.margin() != null ? pct(p.margin(), p.costed()) : null,
                    withCost ? p.linesWithoutCost() : null));
        }
        BigDecimal shipping = money(queries.shipping(r, f));
        if (shipping.signum() > 0) {
            rows.add(new ReportRow(null, null, SHIPPING, shipping, null, null, null, null, null));
        }
        rows.sort(Comparator.comparing(ReportRow::collected).reversed());
        return rows;
    }

    private static ReportRow row(Long id, String name, Sum sum, BigDecimal total) {
        BigDecimal amount = money(sum.amount());
        return new ReportRow(null, id, name, amount, sum.payments(), pct(amount, total), null, null, null);
    }

    static BigDecimal money(BigDecimal v) {
        return InsightsQueries.money(v);
    }

    /** {@code part / whole × 100} to one place; null when there is nothing to divide by. */
    static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        return whole == null || whole.signum() == 0 ? null
                : part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }
}
