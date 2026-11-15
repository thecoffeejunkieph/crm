package ph.thecoffeejunkie.crm.sales;

import ph.thecoffeejunkie.crm.shared.PdfRenderer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PdfRendererTest {

    static Map<String, Object> quoteVars() {
        return Map.ofEntries(
                Map.entry("heading", "QUOTATION"),
                Map.entry("number", "SO-26-00042"),
                Map.entry("date", "2026-10-14"),
                Map.entry("validUntil", "2026-10-29"),
                Map.entry("company", Map.of("name", "The Coffee Junkie Inc.", "tin", "123-456-789-000", "address", "Makati")),
                Map.entry("customer", Map.of("name", "Acme & Sons", "tin", "999", "address", "Pasig")),
                Map.entry("paymentTerms", "Net 30"),
                Map.entry("terms", "Prices valid while stocks last."),
                Map.entry("notes", ""),
                Map.entry("lines", List.of(Map.of("description", "Arabica 1kg", "qty", new BigDecimal("3.000"),
                        "unit", "KG", "unitPrice", new BigDecimal("112.00"), "discountPct", BigDecimal.ZERO,
                        "lineTotal", new BigDecimal("336.00")))),
                Map.entry("totals", Map.of("vatableSales", new BigDecimal("300.00"), "vat", new BigDecimal("36.00"),
                        "vatExemptSales", new BigDecimal("0.00"), "zeroRatedSales", new BigDecimal("0.00"),
                        "shippingFee", new BigDecimal("0.00"), "total", new BigDecimal("336.00"),
                        "discount", new BigDecimal("0.00"), "withholdingPct", new BigDecimal("1.00"),
                        "withholding", new BigDecimal("3.00"), "netPayable", new BigDecimal("333.00"))));
    }

    @Test
    void invoiceEndsWithAPageOfBankAccountsAndTerms() throws Exception {
        var vars = new java.util.HashMap<>(quoteVars());
        vars.putAll(Map.of("dueDate", "2026-12-10", "voided", "", "voidReason", "", "footer", "BIR permit", "invoiceTerms", "Pay within 30 days.",
                "bankAccounts", List.of(Map.of("bankName", "BDO", "accountName", "TCJ Inc.", "accountNumber", "0000-1111", "qr", ""))));
        byte[] pdf = new PdfRenderer().render("invoice", vars);
        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(2);
            var strip = new org.apache.pdfbox.text.PDFTextStripper();
            strip.setStartPage(2);
            assertThat(strip.getText(doc)).contains("Payment details", "BDO", "0000-1111", "Terms and conditions", "Pay within 30 days.");
        }
    }

    @Test
    void rendersQuoteWithBreakdown() throws Exception {
        byte[] pdf = new PdfRenderer().render("quote", quoteVars());

        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        String text = PdfText.of(pdf);
        assertThat(text).contains("QUOTATION", "SO-26-00042", "VATable Sales", "PHP 300.00", "PHP 336.00",
                "Acme & Sons");
    }

    @Test
    void rendersDeliveryOrderWithoutPrices() throws Exception {
        byte[] pdf = new PdfRenderer().render("delivery-order", PdfRenderer.vars("number", "DO-26-00007", "status", "DISPATCHED",
                "date", "2026-11-08", "company", Map.of("name", "The Coffee Junkie Inc.", "tin", "123", "address", "Makati"),
                "customer", "Acme & Sons", "shipTo", "Pasig", "orderNumber", "SO-26-00042", "warehouse", "Main",
                "receiverName", "Juan", "pickup", null, "dispatched", null, "delivered", null,
                "lines", List.of(Map.of("sku", "ARB-1", "description", "Arabica 1kg", "unit", "KG", "qty", "3", "picked", "3"))));

        assertThat(PdfText.of(pdf)).contains("DELIVERY ORDER", "DO-26-00007", "Acme & Sons", "Arabica 1kg", "Juan")
                .doesNotContain("PHP");
    }

    @Test
    void embedsDataUriImagesAndPrintsAtTheCssPageSize() throws Exception {
        String dot = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";
        byte[] pdf = new PdfRenderer().render("labels", Map.of("labels", List.of(Map.of("qr", dot, "number", "PR-1", "name", "Beans"))));

        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            var page = doc.getPage(0);
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            assertThat(page.getMediaBox().getWidth()).isCloseTo(175.7f, org.assertj.core.data.Offset.offset(2f)); // 62 mm
            assertThat(page.getResources().getXObjectNames()).isNotEmpty(); // the data: image was not blocked
        }
        assertThat(PdfText.of(pdf)).contains("PR-1", "Beans");
    }
}
