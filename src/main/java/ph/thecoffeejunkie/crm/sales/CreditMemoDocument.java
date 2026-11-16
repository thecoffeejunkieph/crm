package ph.thecoffeejunkie.crm.sales;

import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.catalog.TaxClass;
import ph.thecoffeejunkie.crm.customers.Customers;
import ph.thecoffeejunkie.crm.settings.Settings;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PdfFile;
import ph.thecoffeejunkie.crm.shared.PdfRenderer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ph.thecoffeejunkie.crm.shared.PdfRenderer.vars;

/** Renders a PAID refund's credit memo against the order's invoice; the seller block is that invoice's snapshot. */
@Component
class CreditMemoDocument {

    private final PdfRenderer pdf;
    private final Settings settings;
    private final InvoiceRepository invoices;
    private final Customers customers;

    CreditMemoDocument(PdfRenderer pdf, Settings settings, InvoiceRepository invoices, Customers customers) {
        this.customers = customers;
        this.pdf = pdf;
        this.settings = settings;
        this.invoices = invoices;
    }

    /** 404 unless the refund is PAID: a memo is issued once, at payout. */
    PdfFile render(Refund r, SalesOrder o) {
        if (r.status != RefundStatus.PAID) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Credit memo not found");
        }
        Invoice inv = invoices.findByOrderIdAndStatus(o.id, InvoiceStatus.ISSUED)
                .or(() -> invoices.findByOrderIdOrderByIdDesc(o.id).stream().findFirst())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND,
                        "This order has no invoice, so no credit memo can be issued."));
        Map<String, Object> seller = inv.sellerSnapshot != null ? inv.sellerSnapshot : InvoiceDocument.seller(settings);
        Map<String, Object> c = inv.customerSnapshot;

        List<OrderTotals.Line> taxed = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        if (r.kind == RefundKind.GOODS_RETURN) {
            for (RefundLine l : r.lines) {
                OrderLine ol = o.lines.stream().filter(x -> x.id.equals(l.orderLineId)).findFirst().orElseThrow();
                BigDecimal unit = (ol.netTotal != null ? ol.netTotal : ol.lineTotal).divide(ol.qty, 6, RoundingMode.HALF_UP);
                taxed.add(new OrderTotals.Line(l.qty, unit, BigDecimal.ZERO, ol.taxClass));
                rows.add(vars("description", ol.description, "qty", l.qty.stripTrailingZeros().toPlainString(),
                        "unit", ol.unit, "unitPrice", unit.setScale(2, RoundingMode.HALF_UP), "lineTotal", l.amount));
            }
        } else {
            TaxClass tc = o.lines.stream().anyMatch(l -> l.taxClass == TaxClass.VATABLE) ? TaxClass.VATABLE
                    : o.lines.get(0).taxClass;
            taxed.add(new OrderTotals.Line(BigDecimal.ONE, r.amount, BigDecimal.ZERO, tc));
            rows.add(vars("description", "Adjustment — " + r.reason, "qty", "1", "unit", "",
                    "unitPrice", r.amount, "lineTotal", r.amount));
        }
        OrderTotals.Result t = OrderTotals.compute(taxed, BigDecimal.ZERO, customers.salesProfile(o.customerId).taxTreatment());
        BigDecimal zero = BigDecimal.ZERO;
        byte[] bytes = pdf.render("credit-memo", vars(
                "number", r.creditMemoNo,
                "date", r.paidOn,
                "invoiceNo", inv.number,
                "invoiceDate", inv.issuedAt.atZone(SalesTime.MANILA).toLocalDate(),
                "reason", r.kind == RefundKind.GOODS_RETURN ? r.reason : "",
                "company", vars("name", seller.get("name"), "tin", seller.get("tin"), "address", seller.get("address")),
                "customer", vars("name", c.get("name"), "tin", c.get("tin"), "address", c.get("address")),
                "lines", rows,
                "totals", vars("vatableSales", t.vatableSales(), "vat", t.vat(), "vatExemptSales", t.vatExemptSales(),
                        "zeroRatedSales", t.zeroRatedSales(), "shippingFee", zero, "total", t.total(),
                        "discount", zero, "withholdingPct", zero, "withholding", zero, "netPayable", t.total())));
        return new PdfFile(r.creditMemoNo + ".pdf", bytes);
    }
}
