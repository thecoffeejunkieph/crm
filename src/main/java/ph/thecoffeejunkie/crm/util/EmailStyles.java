package ph.thecoffeejunkie.crm.util;

/**
 * Shared CSS for the transactional emails (invoice/quotation). Mirrors the same curated
 * Bootstrap 5 subset used by templates/pdf/fragments/bootstrap.html - table/button/typography
 * rules only, no grid/flexbox, and no CSS custom properties (var(--x)) either: Outlook's
 * rendering engine drops a declaration outright if it can't resolve var(), so every color
 * below is a literal instead of a --bs-* variable.
 */
public final class EmailStyles {

    public static final String BOOTSTRAP_CSS = """
            .small { font-size: .85em; }
            .text-muted { color: #6c757d; }
            .text-end { text-align: right; }
            .fw-bold { font-weight: 700; }
            .mt-0 { margin-top: 0; }
            .mb-0 { margin-bottom: 0; }
            table.table { width: 100%; border-collapse: collapse; margin: 20px 0; }
            table.table th, table.table td { padding: 10px 8px; border: 1px solid #dee2e6; }
            table.table thead.table-dark th { background-color: #212529; color: #ffffff; text-transform: uppercase; letter-spacing: .03em; font-size: 13px; }
            table.table tbody tr:nth-child(even) td { background-color: #f2f2f2; }
            table.table tbody tr:nth-child(odd) td { background-color: #ffffff; }
            .btn { display: inline-block; padding: 13px 30px; border-radius: 6px; text-decoration: none; font-weight: 600; font-size: 14px; margin: 6px; border: 1px solid transparent; }
            .btn-primary { background-color: #0d6efd; border-color: #0d6efd; color: #ffffff; }
            .btn-danger { background-color: #dc3545; border-color: #dc3545; color: #ffffff; }
            .btn-dark { background-color: #212529; border-color: #212529; color: #ffffff; }
            """;

    private EmailStyles() {
        /* This utility class should not be instantiated */
    }
}
