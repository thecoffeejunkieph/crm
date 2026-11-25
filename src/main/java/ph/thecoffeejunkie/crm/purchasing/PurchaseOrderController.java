package ph.thecoffeejunkie.crm.purchasing;

import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;

/** ADMIN and WAREHOUSE reach this (SecurityConfig); writes are ADMIN-only there and in the service; reads are warehouse-scoped. */
@RestController
@RequestMapping("/api/purchase-orders")
class PurchaseOrderController {

    private final PurchaseOrderService orders;
    private final PurchaseOrderDocument document;

    PurchaseOrderController(PurchaseOrderService orders, PurchaseOrderDocument document) {
        this.orders = orders;
        this.document = document;
    }

    @GetMapping
    PageResponse<PoResponse> listPurchaseOrders(@RequestParam(required = false) PoStatus status,
                                  @RequestParam(required = false) Long supplierId, PageQuery page) {
        return orders.list(status, supplierId, page);
    }

    @GetMapping("/{id}")
    PoResponse getPurchaseOrder(@PathVariable long id) {
        return orders.get(id);
    }

    @GetMapping("/{id}.pdf")
    ResponseEntity<byte[]> getPurchaseOrderPdf(@PathVariable long id, @RequestParam(defaultValue = "false") boolean download) {
        String number = orders.get(id).number(); // scope check first: out of scope is 404 before any rendering
        return file(number + ".pdf", MediaType.APPLICATION_PDF, download, document.pdf(id));
    }

    @GetMapping("/{id}.png")
    ResponseEntity<byte[]> getPurchaseOrderPng(@PathVariable long id, @RequestParam(defaultValue = "false") boolean download) {
        String number = orders.get(id).number();
        return file(number + ".png", MediaType.IMAGE_PNG, download, document.png(id));
    }

    private static ResponseEntity<byte[]> file(String filename, MediaType type, boolean download, byte[] bytes) {
        var disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline()).filename(filename);
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.build().toString()).body(bytes);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PoResponse createPurchaseOrder(@Valid @RequestBody PoRequest req) {
        return orders.createDraft(req);
    }

    @PutMapping("/{id}")
    PoResponse updatePurchaseOrder(@PathVariable long id, @Valid @RequestBody PoRequest req) {
        return orders.update(id, req);
    }

    @PostMapping("/{id}/send")
    PoResponse sendPurchaseOrder(@PathVariable long id) {
        return orders.send(id);
    }

    @GetMapping("/{id}/credit-check")
    CreditCheck purchaseOrderCreditCheck(@PathVariable long id) {
        return orders.creditCheck(id);
    }

    @PostMapping("/{id}/receipts")
    PoResponse receivePurchaseOrder(@PathVariable long id, @Valid @RequestBody PoReceiveRequest req) {
        return orders.receive(id, req);
    }

    @PostMapping("/{id}/close")
    PoResponse closePurchaseOrder(@PathVariable long id) {
        return orders.closeShort(id);
    }

    @PostMapping("/{id}/cancel")
    PoResponse cancelPurchaseOrder(@PathVariable long id) {
        return orders.cancel(id);
    }
}
