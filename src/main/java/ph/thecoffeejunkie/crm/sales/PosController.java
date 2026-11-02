package ph.thecoffeejunkie.crm.sales;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.catalog.Catalog;
import ph.thecoffeejunkie.crm.catalog.ProductInfo;
import ph.thecoffeejunkie.crm.inventory.Inventory;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/pos")
class PosController {

    private final PosConfig config;
    private final Catalog catalog;
    private final Inventory inventory;

    PosController(PosConfig config, Catalog catalog, Inventory inventory) {
        this.config = config;
        this.catalog = catalog;
        this.inventory = inventory;
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
