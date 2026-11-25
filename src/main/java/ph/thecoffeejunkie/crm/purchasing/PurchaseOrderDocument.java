package ph.thecoffeejunkie.crm.purchasing;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.catalog.Catalog;
import ph.thecoffeejunkie.crm.catalog.ProductInfo;
import ph.thecoffeejunkie.crm.settings.SettingKey;
import ph.thecoffeejunkie.crm.settings.Settings;
import ph.thecoffeejunkie.crm.shared.PdfRenderer;
import ph.thecoffeejunkie.crm.warehouses.Warehouses;

import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/** The PO document, rendered from the live PO as a PDF or a full-page PNG (spec "PO document"). */
@Component
class PurchaseOrderDocument {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private final PurchaseOrderService orders;
    private final SupplierRepository suppliers;
    private final Catalog catalog;
    private final Warehouses warehouses;
    private final Settings settings;
    private final PdfRenderer renderer;

    PurchaseOrderDocument(PurchaseOrderService orders, SupplierRepository suppliers, Catalog catalog,
                          Warehouses warehouses, Settings settings, PdfRenderer renderer) {
        this.orders = orders;
        this.suppliers = suppliers;
        this.catalog = catalog;
        this.warehouses = warehouses;
        this.settings = settings;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    byte[] pdf(long poId) {
        return renderer.render("purchase-order", model(poId));
    }

    @Transactional(readOnly = true)
    byte[] png(long poId) {
        return renderer.renderPng("purchase-order", model(poId));
    }

    private Map<String, Object> model(long poId) {
        PurchaseOrder po = orders.documentFor(poId);
        Supplier s = suppliers.findById(po.supplierId).orElseThrow();
        Map<Long, ProductInfo> products = catalog.info(po.lines.stream().map(l -> l.productId).toList());
        return PdfRenderer.vars(
                "number", po.number,
                "status", po.status.name(),
                "draft", po.status == PoStatus.DRAFT ? "yes" : null,
                "date", (po.sentAt != null ? po.sentAt : po.createdAt).atZone(MANILA).toLocalDate(),
                "expectedOn", po.expectedOn,
                "company", PdfRenderer.vars("name", settings.text(SettingKey.COMPANY_NAME),
                        "tin", settings.text(SettingKey.COMPANY_TIN), "address", settings.text(SettingKey.COMPANY_ADDRESS)),
                "supplier", PdfRenderer.vars("name", s.name, "contact", s.contactName, "phone", s.phone,
                        "email", s.email, "address", s.address),
                "warehouse", warehouses.name(po.warehouseId),
                "terms", terms(s, orders.downpayment(po)),
                "notes", po.notes,
                "lines", po.lines.stream().map(l -> {
                    ProductInfo p = products.get(l.productId);
                    return PdfRenderer.vars("description", p.name(), "sku", p.sku(), "unit", p.unit(),
                            "qty", l.qty.stripTrailingZeros().toPlainString(), "unitCost", l.unitCost,
                            "lineTotal", l.qty.multiply(l.unitCost).setScale(2, RoundingMode.HALF_UP));
                }).toList(),
                "total", po.total());
    }

    /** "50% downpayment ₱X (paid ₱Y) · balance on 30-day credit"; empty when the supplier has neither term. */
    static String terms(Supplier s, PurchaseOrderService.Downpayment dp) {
        List<String> parts = new ArrayList<>();
        if (s.downpaymentPercent.signum() > 0) {
            parts.add(String.format(Locale.US, "%s%% downpayment ₱%,.2f (paid ₱%,.2f)",
                    s.downpaymentPercent.stripTrailingZeros().toPlainString(), dp.required(), dp.paid()));
        }
        if (s.paymentTermsDays != null) {
            parts.add("balance on " + s.paymentTermsDays + "-day credit");
        }
        return String.join(" · ", parts);
    }
}
