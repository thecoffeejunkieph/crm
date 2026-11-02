package ph.thecoffeejunkie.crm.sales;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.catalog.Catalog;
import ph.thecoffeejunkie.crm.catalog.ProductInfo;
import ph.thecoffeejunkie.crm.inventory.Inventory;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/pos")
class PosController {

    private final PosConfig config;
    private final Catalog catalog;
    private final Inventory inventory;
    private final PosService pos;
    private final CurrentActor actor;

    PosController(PosConfig config, Catalog catalog, Inventory inventory, PosService pos, CurrentActor actor) {
        this.config = config;
        this.catalog = catalog;
        this.inventory = inventory;
        this.pos = pos;
        this.actor = actor;
    }

    /** Requires an Idempotency-Key: a double-tapped checkout must not sell twice. */
    @PostMapping("/sales")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@salesPermissions.canWrite()")
    PosSaleResponse checkout(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody PosSaleRequest req) {
        return pos.checkout(actor.scope(), req);
    }

    @GetMapping("/products")
    @PreAuthorize("@salesPermissions.canRead()")
    PosProductPage products(@RequestParam(required = false) String q, PageQuery page) {
        long warehouse = config.warehouseId();
        BigDecimal cap = config.discountCapPct();
        var found = catalog.searchActive(q, page);
        var available = inventory.available(warehouse, found.content().stream().map(ProductInfo::id).toList());
        var rows = found.content().stream().map(p -> new PosProductResponse(p.id(), p.sku(), p.name(), p.unit(),
                p.price(), available.get(p.id()), PosConfig.minUnitPrice(p.price(), p.cost(), cap))).toList();
        return new PosProductPage(new PageResponse<>(found.pageNumber(),
                found.pageSize(), found.totalPages(), found.totalElements(), rows), cap);
    }
}
