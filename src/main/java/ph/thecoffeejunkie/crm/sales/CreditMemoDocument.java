package ph.thecoffeejunkie.crm.sales;

import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.customers.TaxTreatment;
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
import java.util.Optional;

import static ph.thecoffeejunkie.crm.shared.PdfRenderer.vars;

/**
 * Renders a PAID refund's credit memo against the invoice and tax class frozen on the refund at payout, so a later
 * void and reissue of the invoice never changes a memo already issued. The seller block is that invoice's snapshot.
 */
@Component
class CreditMemoDocument {

    private final PdfRenderer pdf;
    private final Settings settings;
    private final InvoiceRepository invoices;

    CreditMemoDocument(PdfRenderer pdf, Settings settings, InvoiceRepository invoices) {
        this.pdf = pdf;
        this.settings = settings;
        this.invoices = invoices;
    }

    /** 404 unless the refund is PAID: a memo is issued once, at payout. */
    PdfFile render(Refund r, SalesOrder o) {
        if (r.status != RefundStatus.PAID) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Credit memo not found");
        }
        Invoice inv = Optional.ofNullable(r.invoiceId).flatMap(invoices::findById)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND,
                        "This refund has no invoice, so no credit memo can be issued."));
        Map<String, Object> seller = inv.sellerSnapshot != null ? inv.sellerSnapshot : InvoiceDocument.seller(settings);
        Map<String, Object> c = inv.customerSnapshot;

        List<OrderTotals.Line> taxed = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        if (r.kind == RefundKind.GOODS_RETURN) {
            for (RefundLine l : r.lines) {
                OrderLine ol = o.lines.stream().filter(x -> x.id.equals(l.orderLineId)).findFirst().orElseThrow();
                taxed.add(new OrderTotals.Line(BigDecimal.ONE, l.amount, BigDecimal.ZERO, ol.taxClass));
                BigDecimal unit = l.amount.divide(l.qty, 2, RoundingMode.HALF_UP);
                rows.add(vars("description", ol.description, "qty", l.qty.stripTrailingZeros().toPlainString(),
                        "unit", ol.unit, "unitPrice", unit, "lineTotal", l.amount));
            }
        } else {
            taxed.add(new OrderTotals.Line(BigDecimal.ONE, r.amount, BigDecimal.ZERO, r.memoTaxClass));
            rows.add(vars("description", "Adjustment — " + r.reason, "qty", "1", "unit", "",
                    "unitPrice", r.amount, "lineTotal", r.amount));
        }
        OrderTotals.Result t = OrderTotals.compute(taxed, BigDecimal.ZERO, treatment(inv));
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

    /** Not stored on the invoice, so read back from its frozen split: the memo follows the invoice, not the live customer. */
    private static TaxTreatment treatment(Invoice inv) {
        return inv.vatableSales.signum() > 0 ? TaxTreatment.REGULAR
                : inv.zeroRatedSales.signum() > 0 ? TaxTreatment.ZERO_RATED : TaxTreatment.VAT_EXEMPT;
    }
}
