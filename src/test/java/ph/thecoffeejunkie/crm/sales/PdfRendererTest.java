package ph.thecoffeejunkie.crm.sales;

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
                        "shippingFee", new BigDecimal("0.00"), "total", new BigDecimal("336.00"))));
    }

    @Test
    void rendersQuoteWithBreakdown() throws Exception {
        byte[] pdf = new PdfRenderer().render("quote", quoteVars());

        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        String text = PdfText.of(pdf);
        assertThat(text).contains("QUOTATION", "SO-26-00042", "VATable Sales", "PHP 300.00", "PHP 336.00",
                "Acme & Sons");
    }
}
