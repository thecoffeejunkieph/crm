package ph.thecoffeejunkie.crm.sales;

import ph.thecoffeejunkie.crm.shared.PdfRenderer;
import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.customers.CustomerSalesProfile;
import ph.thecoffeejunkie.crm.customers.Customers;
import ph.thecoffeejunkie.crm.settings.SettingKey;
import ph.thecoffeejunkie.crm.settings.Settings;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import static ph.thecoffeejunkie.crm.shared.PdfRenderer.vars;

/** The quote PDF renders from the live order (spec §4 Documents). */
@Component
class QuoteDocument {

    private final PdfRenderer pdf;
    private final Settings settings;
    private final Customers customers;
    private final Clock clock;

    QuoteDocument(PdfRenderer pdf, Settings settings, Customers customers, Clock clock) {
        this.pdf = pdf;
        this.settings = settings;
        this.customers = customers;
        this.clock = clock;
    }

    byte[] render(SalesOrder o) {
        CustomerSalesProfile c = customers.salesProfile(o.customerId);
        Instant date = o.quotedAt == null ? clock.instant() : o.quotedAt;
        Map<String, Object> vars = vars(
                "heading", "QUOTATION",
                "number", o.number,
                "date", date.atZone(SalesTime.MANILA).toLocalDate(),
                "validUntil", o.quoteValidUntil,
                "company", company(settings),
                "customer", vars("name", c.businessName() == null || c.businessName().isBlank()
                        ? c.displayName() : c.businessName(), "tin", c.tin(), "address", c.address()),
                "paymentTerms", o.paymentTerms.getLabel(),
                "terms", o.terms,
                "lines", o.lines.stream().map(l -> vars("description", l.description,
                        "qty", l.qty.stripTrailingZeros().toPlainString(), "unit", l.unit, "unitPrice", l.unitPrice,
                        "discountPct", l.discountPct, "lineTotal", l.lineTotal)).toList(),
                "totals", vars("vatableSales", o.vatableSales, "vat", o.vat, "vatExemptSales", o.vatExemptSales,
                        "zeroRatedSales", o.zeroRatedSales, "shippingFee", o.shippingFee, "total", o.total));
        return pdf.render("quote", vars);
    }

    static Map<String, Object> company(Settings settings) {
        return vars("name", settings.text(SettingKey.COMPANY_NAME), "tin", settings.text(SettingKey.COMPANY_TIN),
                "address", settings.text(SettingKey.COMPANY_ADDRESS));
    }
}
