package ph.thecoffeejunkie.crm.inventory;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;

import java.util.List;

/** Role gates are URL rules in SecurityConfig; warehouse scope is checked in the services (out of scope is 404). */
@RestController
class StockController {

    private final StockViews views;
    private final ReceiptService receipts;

    StockController(StockViews views, ReceiptService receipts) {
        this.views = views;
        this.receipts = receipts;
    }

    @GetMapping("/api/stock")
    PageResponse<StockLevelResponse> levels(@RequestParam(required = false) Long warehouseId,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(defaultValue = "false") boolean lowOnly, PageQuery page) {
        return views.levels(warehouseId, q, lowOnly, page);
    }

    @GetMapping("/api/stock/{productId}/movements")
    PageResponse<MovementResponse> movements(@PathVariable long productId,
                                             @RequestParam(required = false) Long warehouseId, PageQuery page) {
        return views.movements(productId, warehouseId, page);
    }

    @GetMapping("/api/products/{id}/availability")
    List<AvailabilityResponse> availability(@PathVariable long id) {
        return views.availability(id);
    }

    @PostMapping("/api/stock/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    ReceiptResponse receive(@Valid @RequestBody ReceiptRequest req) {
        return receipts.receive(req);
    }

    @GetMapping("/api/stock/receipts")
    PageResponse<ReceiptResponse> receipts(@RequestParam(required = false) Long warehouseId, PageQuery page) {
        return receipts.list(warehouseId, page);
    }

    @GetMapping("/api/stock/receipts/{id}")
    ReceiptResponse receipt(@PathVariable long id) {
        return receipts.get(id);
    }
}
