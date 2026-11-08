package ph.thecoffeejunkie.crm.fulfilment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.inventory.Inventory;
import ph.thecoffeejunkie.crm.inventory.TransferResponse;
import ph.thecoffeejunkie.crm.settings.SettingKey;
import ph.thecoffeejunkie.crm.settings.Settings;
import ph.thecoffeejunkie.crm.shared.PdfFile;
import ph.thecoffeejunkie.crm.shared.PdfRenderer;
import ph.thecoffeejunkie.crm.warehouses.Warehouses;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static ph.thecoffeejunkie.crm.shared.PdfRenderer.vars;

/** Delivery order and transfer PDFs. The services' own reads apply warehouse and rep scope (out of scope is 404). */
@RestController
class DocumentController {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private final DeliveryService deliveries;
    private final Inventory inventory;
    private final Warehouses warehouses;
    private final Settings settings;
    private final PdfRenderer pdf;

    DocumentController(DeliveryService deliveries, Inventory inventory, Warehouses warehouses, Settings settings,
                       PdfRenderer pdf) {
        this.deliveries = deliveries;
        this.inventory = inventory;
        this.warehouses = warehouses;
        this.settings = settings;
        this.pdf = pdf;
    }

    @GetMapping("/api/delivery-orders/{id}.pdf")
    ResponseEntity<byte[]> delivery(@PathVariable long id) {
        var d = deliveries.get(id);
        return new PdfFile(d.number() + ".pdf", pdf.render("delivery-order", vars(
                "number", d.number(), "status", d.status(), "date", day(d.createdAt()),
                "pickup", d.method() == DeliveryMethod.PICKUP ? "yes" : null,
                "company", company(), "customer", d.customerName(), "shipTo", d.shipTo(),
                "orderNumber", d.orderNumber(), "warehouse", warehouses.name(d.warehouseId()),
                "dispatched", day(d.dispatchedAt()), "delivered", day(d.deliveredAt()), "receiverName", d.receiverName(),
                "lines", d.lines().stream().map(l -> vars("sku", l.sku(), "description", l.description(), "unit", l.unit(),
                        "qty", qty(l.qty()), "picked", qty(l.pickedQty()))).toList()))).inline();
    }

    @GetMapping("/api/transfers/{id}.pdf")
    ResponseEntity<byte[]> transfer(@PathVariable long id) {
        TransferResponse t = inventory.transfer(id);
        return new PdfFile(t.number() + ".pdf", pdf.render("transfer", vars(
                "number", t.number(), "status", t.status(), "date", day(t.createdAt()),
                "company", company(), "from", warehouses.name(t.fromWarehouseId()), "to", warehouses.name(t.toWarehouseId()),
                "createdBy", t.createdByName(), "dispatched", day(t.dispatchedAt()), "received", day(t.receivedAt()),
                "notes", t.notes(),
                "lines", t.lines().stream().map(l -> vars("sku", l.sku(), "name", l.name(), "unit", l.unit(),
                        "qty", qty(l.qty()), "received", qty(l.receivedQty()))).toList()))).inline();
    }

    private Map<String, Object> company() {
        return vars("name", settings.text(SettingKey.COMPANY_NAME), "tin", settings.text(SettingKey.COMPANY_TIN),
                "address", settings.text(SettingKey.COMPANY_ADDRESS));
    }

    private static Object day(Instant at) {
        return at == null ? null : at.atZone(MANILA).toLocalDate();
    }

    private static String qty(BigDecimal q) {
        return q == null ? null : q.stripTrailingZeros().toPlainString();
    }
}
