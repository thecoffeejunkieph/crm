package ph.thecoffeejunkie.crm.fulfilment;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.catalog.Catalog;
import ph.thecoffeejunkie.crm.catalog.ProductInfo;
import ph.thecoffeejunkie.crm.inventory.Inventory;
import ph.thecoffeejunkie.crm.inventory.InventoryPermissions;
import ph.thecoffeejunkie.crm.inventory.TransferResponse;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PdfRenderer;
import ph.thecoffeejunkie.crm.warehouses.WarehouseResponse;
import ph.thecoffeejunkie.crm.warehouses.Warehouses;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Finds the thing a code points at under the target's own read rules, and prints 62 x 40 mm QR labels. */
@Service
class LabelService {

    private static final int MAX_LABELS = 100;

    private final DeliveryService deliveries;
    private final Inventory inventory;
    private final InventoryPermissions perms;
    private final Catalog catalog;
    private final Warehouses warehouses;
    private final PdfRenderer pdf;

    LabelService(DeliveryService deliveries, Inventory inventory, InventoryPermissions perms, Catalog catalog,
                 Warehouses warehouses, PdfRenderer pdf) {
        this.deliveries = deliveries;
        this.inventory = inventory;
        this.perms = perms;
        this.catalog = catalog;
        this.warehouses = warehouses;
        this.pdf = pdf;
    }

    /** What a label prints: the number or SKU in large text, the name or customer below. */
    record Label(String number, String name) {
    }

    /** 404 when it does not exist or the caller may not read it. */
    @Transactional(readOnly = true)
    Label find(LabelKind kind, long id) {
        return switch (kind) {
            case DO -> {
                var d = deliveries.get(id);
                yield new Label(d.number(), d.customerName());
            }
            case TR -> {
                TransferResponse t = inventory.transfer(id);
                yield new Label(t.number(), warehouses.name(t.fromWarehouseId()) + " > "
                        + warehouses.name(t.toWarehouseId()));
            }
            case PR -> {
                ProductInfo p = catalog.info(List.of(id)).get(id);
                if (p == null) {
                    throw new DomainException(ErrorCode.NOT_FOUND, "Product not found");
                }
                yield new Label(p.sku(), p.name());
            }
            case WH -> {
                if (!perms.canRead(id)) {
                    throw new DomainException(ErrorCode.NOT_FOUND, "Warehouse not found");
                }
                WarehouseResponse w = warehouses.get(id);
                yield new Label(w.code(), w.name());
            }
        };
    }

    @Transactional(readOnly = true)
    byte[] render(LabelKind kind, List<Long> ids) {
        if (ids.isEmpty() || ids.size() > MAX_LABELS) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Print 1 to " + MAX_LABELS + " labels at a time.",
                    Map.of("errors", List.of(Map.of("field", "ids", "message", "Give 1 to " + MAX_LABELS + " ids."))));
        }
        List<Map<String, String>> rows = ids.stream().map(id -> {
            Label l = find(kind, id);
            return Map.of("qr", qr("crm:" + kind.name() + ":" + id), "number", l.number(),
                    "name", l.name() == null ? "" : l.name());
        }).toList();
        return pdf.render("labels", Map.of("labels", rows));
    }

    /** 300 px PNG as a data URI; zxing core has no image writer, so the pixels are set directly. */
    private static String qr(String text) {
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 300, 300,
                    Map.of(EncodeHintType.MARGIN, 1));
            var img = new BufferedImage(m.getWidth(), m.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
            for (int y = 0; y < m.getHeight(); y++) {
                for (int x = 0; x < m.getWidth(); x++) {
                    img.setRGB(x, y, m.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            var out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (WriterException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
