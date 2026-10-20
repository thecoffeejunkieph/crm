package ph.thecoffeejunkie.crm.shared;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.IntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Runs as crm_app and rolls back. A failed statement aborts the transaction, so each test fails at most once, last. */
@IntegrationTest
@Transactional
class SalesSchemaIT {

    @Autowired
    JdbcTemplate jdbc;

    long userId;
    long orderId;

    @BeforeEach
    void seedOrder() {
        userId = jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) "
                + "values (?, 'S', 'R', 'SALES') returning id", Long.class, UUID.randomUUID() + "@schema.it");
        long customerId = jdbc.queryForObject(
                "insert into customer (type, display_name) values ('BUSINESS', 'Acme') returning id", Long.class);
        jdbc.update("insert into warehouse (code, name, is_default) select ?, 'W', true "
                + "where not exists (select 1 from warehouse where is_default and archived_at is null)",
                UUID.randomUUID().toString());
        orderId = jdbc.queryForObject("insert into sales_order (number, customer_id, rep_id, status, payment_terms, "
                + "created_by) values (?, ?, ?, 'CONFIRMED', 'NET_30', ?) returning id", Long.class,
                "SO-" + UUID.randomUUID(), customerId, userId, userId);
    }

    private void invoice(String status) {
        jdbc.update("insert into invoice (number, order_id, order_revision, status, issued_at, due_date, "
                + "customer_snapshot, lines_snapshot, vatable_sales, vat, vat_exempt_sales, zero_rated_sales, "
                + "shipping_fee, total, issued_by) values (?, ?, 0, ?, now(), current_date, '{}', '[]', 0, 0, 0, 0, 0, 0, ?)",
                "SI-" + UUID.randomUUID(), orderId, status, userId);
    }

    private void product(String sku) {
        jdbc.update("insert into product (sku, name, unit, price, tax_class) values (?, 'P', 'PC', 1, 'VATABLE')", sku);
    }

    private void line(String qty, String discount) {
        long productId = jdbc.queryForObject("insert into product (sku, name, unit, price, tax_class) "
                + "values (?, 'P', 'PC', 1, 'VATABLE') returning id", Long.class, UUID.randomUUID().toString());
        jdbc.update("insert into order_line (order_id, position, product_id, description, unit, qty, unit_price, "
                + "discount_pct, tax_class, line_total) values (?, 0, ?, 'd', 'PC', ?::numeric, 1, ?::numeric, 'VATABLE', 0)",
                orderId, productId, qty, discount);
    }

    @Test
    void secondIssuedInvoicePerOrderRejected() {
        invoice("ISSUED");
        invoice("VOID");
        assertThrows(DataIntegrityViolationException.class, () -> invoice("ISSUED"));
    }

    @Test
    void skuUniqueIgnoringCaseAndArchived() {
        product("ab-1");
        jdbc.update("update product set archived_at = now() where sku = 'ab-1'");
        product("AB-1");
        assertThrows(DataIntegrityViolationException.class, () -> product("ab-1"));
    }

    @Test
    void negativeQtyRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> line("-1", "0"));
    }

    @Test
    void discountOver100Rejected() {
        assertThrows(DataIntegrityViolationException.class, () -> line("1", "101"));
    }

    @Test
    void paymentNeedsPositiveAmount() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into payment (order_id, status, method, cash_amount, withholding_amount, received_on, "
                        + "submitted_by_type, submitted_by_id) values (?, 'VERIFIED', 'CASH', 0, 0, current_date, 'USER', ?)",
                orderId, userId));
    }

    @Test
    void invoiceVoidApprovalTypeAccepted() {
        jdbc.update("insert into approval_request (type, subject_type, subject_id, subject_version, requested_value, "
                + "reason, status, requested_by) values ('INVOICE_VOID', 'INVOICE', 1, 1, '{}', 'r', 'PENDING', ?)", userId);
        assertThat(jdbc.queryForObject("select count(*) from approval_request where type = 'INVOICE_VOID'",
                Integer.class)).isPositive();
    }

    @Test
    void appCanDrawOrderNumbers() {
        assertThat(jdbc.queryForObject("select nextval('sales_order_number_seq')", Long.class)).isPositive();
        assertThat(jdbc.queryForObject("select next_value from document_counter where name = 'INVOICE'", Long.class))
                .isPositive();
    }

    @Test
    void customerTaxTreatmentDefaultsRegular() {
        assertThat(jdbc.queryForObject("select c.tax_treatment from customer c join sales_order o on o.customer_id = c.id "
                + "where o.id = ?", String.class, orderId)).isEqualTo("REGULAR");
    }
}
