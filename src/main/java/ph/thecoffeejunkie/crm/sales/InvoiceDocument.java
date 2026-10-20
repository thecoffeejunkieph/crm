package ph.thecoffeejunkie.crm.sales;

import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.settings.SettingKey;
import ph.thecoffeejunkie.crm.settings.Settings;

import java.math.BigDecimal;
import java.util.Map;

import static ph.thecoffeejunkie.crm.sales.QuoteDocument.vars;

/** Renders on demand from the frozen invoice, so a reprint is identical (spec §4 Documents). */
@Component
class InvoiceDocument {

    private final PdfRenderer pdf;
    private final Settings settings;

    InvoiceDocument(PdfRenderer pdf, Settings settings) {
        this.pdf = pdf;
        this.settings = settings;
    }

    byte[] render(Invoice i) {
        Map<String, Object> c = i.customerSnapshot;
        Map<String, Object> seller = i.sellerSnapshot != null ? i.sellerSnapshot : seller(settings); // issued before the snapshot existed
        return pdf.render("invoice", vars(
                "number", i.number,
                "date", i.issuedAt.atZone(SalesTime.MANILA).toLocalDate(),
                "dueDate", i.dueDate,
                "voided", i.status == InvoiceStatus.VOID ? "VOID" : null,
                "voidReason", i.voidReason,
                "company", vars("name", seller.get("name"), "tin", seller.get("tin"), "address", seller.get("address")),
                "customer", vars("name", c.get("name"), "tin", c.get("tin"), "address", c.get("address")),
                "paymentTerms", c.get("paymentTerms"),
                "footer", seller.get("footer"),
                "lines", i.linesSnapshot.stream().map(l -> vars("description", l.get("description"),
                        "qty", decimal(l.get("qty")).stripTrailingZeros().toPlainString(), "unit", l.get("unit"),
                        "unitPrice", decimal(l.get("unitPrice")), "lineTotal", decimal(l.get("lineTotal")))).toList(),
                "totals", vars("vatableSales", i.vatableSales, "vat", i.vat, "vatExemptSales", i.vatExemptSales,
                        "zeroRatedSales", i.zeroRatedSales, "shippingFee", i.shippingFee, "total", i.total)));
    }

    /** The seller block and footer, frozen onto the invoice at issue. */
    static Map<String, Object> seller(Settings settings) {
        Map<String, Object> m = new java.util.LinkedHashMap<>(QuoteDocument.company(settings));
        m.put("footer", settings.text(SettingKey.INVOICE_FOOTER_TEXT));
        return m;
    }

    // JSON numbers come back as Integer/Double/BigDecimal.
    private static BigDecimal decimal(Object v) {
        return new BigDecimal(String.valueOf(v));
    }
}
