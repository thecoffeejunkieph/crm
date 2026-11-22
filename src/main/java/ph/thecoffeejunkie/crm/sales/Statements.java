package ph.thecoffeejunkie.crm.sales;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.customers.CustomerSalesProfile;
import ph.thecoffeejunkie.crm.customers.Customers;
import ph.thecoffeejunkie.crm.settings.Settings;
import ph.thecoffeejunkie.crm.shared.PdfFile;
import ph.thecoffeejunkie.crm.shared.PdfRenderer;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ph.thecoffeejunkie.crm.shared.PdfRenderer.vars;

/** Open items, aging and the statement of account (spec 2026-11-22-statements-aging-reminders). */
@Service
class Statements {

    /** An open order: confirmed or later, not fully settled. daysOverdue is 0 when not yet due or no due date. */
    record OpenItem(long orderId, String orderNumber, long customerId, String invoiceNumber, LocalDate invoiceDate,
                    LocalDate dueDate, BigDecimal total, BigDecimal paid, BigDecimal balance, long daysOverdue) {
    }

    private static final String OPEN = "select o.id, o.number, o.customer_id, i.number, i.issued_at, o.due_date, o.total, "
            + "o.settled_amount from sales_order o left join invoice i on i.order_id = o.id and i.status = 'ISSUED' "
            + "where o.status in ('CONFIRMED','RELEASED','FULFILLED') and o.settled_amount < o.total";

    private final NamedParameterJdbcTemplate jdbc;
    private final Customers customers;
    private final PdfRenderer pdf;
    private final Settings settings;
    private final Clock clock;

    Statements(NamedParameterJdbcTemplate jdbc, Customers customers, PdfRenderer pdf, Settings settings, Clock clock) {
        this.jdbc = jdbc;
        this.customers = customers;
        this.pdf = pdf;
        this.settings = settings;
        this.clock = clock;
    }

    /** customerIds empty = every customer. */
    List<OpenItem> openItems(Optional<Set<Long>> customerIds, LocalDate today) {
        if (customerIds.isPresent() && customerIds.get().isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = OPEN;
        if (customerIds.isPresent()) {
            sql += " and o.customer_id in (:ids)";
            p.addValue("ids", customerIds.get());
        }
        return jdbc.query(sql + " order by o.due_date nulls last, o.id", p, (rs, i) -> {
            LocalDate due = rs.getObject(6, LocalDate.class);
            Timestamp issued = rs.getTimestamp(5);
            BigDecimal total = rs.getBigDecimal(7);
            BigDecimal paid = rs.getBigDecimal(8);
            return new OpenItem(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4),
                    issued == null ? null : issued.toInstant().atZone(SalesTime.MANILA).toLocalDate(), due, total, paid,
                    total.subtract(paid), due == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(due, today)));
        });
    }

    static AgingBuckets aging(List<OpenItem> items) {
        BigDecimal[] b = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (OpenItem i : items) {
            long d = i.daysOverdue();
            int k = d == 0 ? 0 : d <= 30 ? 1 : d <= 60 ? 2 : d <= 90 ? 3 : 4;
            b[k] = b[k].add(i.balance());
        }
        return new AgingBuckets(b[0], b[1], b[2], b[3], b[4], b[0].add(b[1]).add(b[2]).add(b[3]).add(b[4]));
    }

    // PdfRenderer reads maps, not records.
    private static Map<String, Object> agingVars(AgingBuckets a) {
        return vars("current", a.current(), "days1to30", a.days1to30(), "days31to60", a.days31to60(),
                "days61to90", a.days61to90(), "over90", a.over90(), "total", a.total());
    }

    /** One row per customer who owes, most overdue first. */
    @Transactional(readOnly = true)
    List<ArAgingRow> agingReport(Scope s) {
        Map<Long, List<OpenItem>> byCustomer = new LinkedHashMap<>();
        for (OpenItem i : openItems(customers.visibleCustomerIds(s), SalesTime.today(clock))) {
            byCustomer.computeIfAbsent(i.customerId(), k -> new ArrayList<>()).add(i);
        }
        Map<Long, String> names = customers.displayNames(byCustomer.keySet());
        return byCustomer.entrySet().stream()
                .map(e -> new ArAgingRow(e.getKey(), names.get(e.getKey()), aging(e.getValue())))
                .sorted(Comparator.comparing((ArAgingRow r) -> r.aging().over90())
                        .thenComparing(r -> r.aging().days61to90())
                        .thenComparing(r -> r.aging().days31to60())
                        .thenComparing(r -> r.aging().days1to30())
                        .thenComparing(r -> r.aging().total()).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    PdfFile pdf(long customerId) {
        return pdf(customerId, SalesTime.today(clock));
    }

    @Transactional(readOnly = true)
    PdfFile pdf(long customerId, LocalDate today) {
        CustomerSalesProfile c = customers.salesProfile(customerId);
        List<OpenItem> items = openItems(Optional.of(Set.of(customerId)), today);
        Map<String, Object> company = QuoteDocument.company(settings);
        String name = c.businessName() != null && !c.businessName().isBlank() ? c.businessName() : c.displayName();
        byte[] bytes = pdf.render("statement", vars(
                "number", today.toString(),
                "date", today,
                "company", company,
                "customer", vars("name", name, "tin", c.tin() == null ? "" : c.tin(),
                        "address", c.address() == null ? "" : c.address()),
                "items", items.stream().map(i -> vars("orderNumber", i.orderNumber(),
                        "invoiceNumber", i.invoiceNumber() == null ? "" : i.invoiceNumber(),
                        "invoiceDate", i.invoiceDate() == null ? "" : i.invoiceDate().toString(),
                        "dueDate", i.dueDate() == null ? "" : i.dueDate().toString(), "total", i.total(),
                        "paid", i.paid(), "balance", i.balance(), "daysOverdue", i.daysOverdue())).toList(),
                "aging", agingVars(aging(items)),
                "bankAccounts", settings.bankAccounts()));
        return new PdfFile("statement-" + today + ".pdf", bytes);
    }
}
