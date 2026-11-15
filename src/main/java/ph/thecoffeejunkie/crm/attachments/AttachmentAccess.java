package ph.thecoffeejunkie.crm.attachments;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.customers.CustomerSalesProfile;
import ph.thecoffeejunkie.crm.customers.Customers;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Who may see and add files (spec 2026-11-05 attachments): access follows the record. Warehouse users see an order's
 * (and its invoices') files when one of its deliveries is in their warehouse, and never add.
 */
@Component
class AttachmentAccess {

    private record OrderRow(long customerId, String status) {
    }

    private record InvoiceRow(long orderId, String status) {
    }

    private final Customers customers;
    private final NamedParameterJdbcTemplate jdbc;

    AttachmentAccess(Customers customers, NamedParameterJdbcTemplate jdbc) {
        this.customers = customers;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    boolean canSee(Scope s, SubjectType type, long id) {
        return switch (type) {
            case CUSTOMER -> s.role() != Role.WAREHOUSE && customer(s, id).isPresent();
            case ORDER -> order(id).map(o -> orderVisible(s, id, o)).orElse(false);
            case INVOICE -> invoice(id).flatMap(i -> order(i.orderId()).map(o -> orderVisible(s, i.orderId(), o)))
                    .orElse(false);
        };
    }

    /** Not visible → NOT_FOUND; visible but read only (warehouse, archived, cancelled, void) → ATTACHMENT_READ_ONLY. */
    @Transactional(readOnly = true)
    void requireWritable(Scope s, SubjectType type, long id) {
        if (!canSee(s, type, id)) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Not found");
        }
        if (!writable(s, type, id)) {
            throw new DomainException(ErrorCode.ATTACHMENT_READ_ONLY, "Files can no longer be added here.");
        }
    }

    /**
     * Never throws, so callers inside a transaction can ask without marking it rollback-only (a caught exception
     * from a transactional proxy still dooms the surrounding transaction).
     */
    @Transactional(readOnly = true)
    boolean writable(Scope s, SubjectType type, long id) {
        return s.role() != Role.WAREHOUSE && switch (type) {
            case CUSTOMER -> customer(s, id).map(c -> !c.archived()).orElse(false);
            case ORDER -> order(id).map(o -> !"CANCELLED".equals(o.status())).orElse(false);
            case INVOICE -> invoice(id).map(i -> !"VOID".equals(i.status())).orElse(false);
        };
    }

    /** The order an invoice belongs to and its number, for audit lines on the order's Activity. */
    record InvoiceRef(long orderId, String number) {
    }

    @Transactional(readOnly = true)
    Optional<InvoiceRef> invoiceRef(long invoiceId) {
        return jdbc.query("select order_id, number from invoice where id = :id", Map.of("id", invoiceId),
                (rs, n) -> new InvoiceRef(rs.getLong(1), rs.getString(2))).stream().findFirst();
    }

    @Transactional(readOnly = true)
    List<Long> invoiceIdsOfOrder(long orderId) {
        return jdbc.queryForList("select id from invoice where order_id = :o order by id", Map.of("o", orderId), Long.class);
    }

    private Optional<CustomerSalesProfile> customer(Scope s, long id) {
        return customers.salesProfileVisible(s, id);
    }

    private boolean orderVisible(Scope s, long orderId, OrderRow o) {
        if (s.role() == Role.WAREHOUSE) {
            return !s.warehouseIds().isEmpty() && Boolean.TRUE.equals(jdbc.queryForObject(
                    "select exists (select 1 from delivery_order where sales_order_id = :o and warehouse_id in (:w))",
                    Map.of("o", orderId, "w", s.warehouseIds()), Boolean.class));
        }
        return customer(s, o.customerId()).isPresent();
    }

    private Optional<OrderRow> order(long id) {
        return jdbc.query("select customer_id, status from sales_order where id = :id", Map.of("id", id),
                (rs, n) -> new OrderRow(rs.getLong(1), rs.getString(2))).stream().findFirst();
    }

    private Optional<InvoiceRow> invoice(long id) {
        return jdbc.query("select order_id, status from invoice where id = :id", Map.of("id", id),
                (rs, n) -> new InvoiceRow(rs.getLong(1), rs.getString(2))).stream().findFirst();
    }
}
