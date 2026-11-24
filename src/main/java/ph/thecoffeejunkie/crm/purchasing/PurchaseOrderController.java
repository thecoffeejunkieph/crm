package ph.thecoffeejunkie.crm.purchasing;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    PurchaseOrderController(PurchaseOrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    PageResponse<PoResponse> list(@RequestParam(required = false) PoStatus status,
                                  @RequestParam(required = false) Long supplierId, PageQuery page) {
        return orders.list(status, supplierId, page);
    }

    @GetMapping("/{id}")
    PoResponse get(@PathVariable long id) {
        return orders.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PoResponse create(@Valid @RequestBody PoRequest req) {
        return orders.createDraft(req);
    }

    @PutMapping("/{id}")
    PoResponse update(@PathVariable long id, @Valid @RequestBody PoRequest req) {
        return orders.update(id, req);
    }

    @PostMapping("/{id}/cancel")
    PoResponse cancel(@PathVariable long id) {
        return orders.cancel(id);
    }
}
