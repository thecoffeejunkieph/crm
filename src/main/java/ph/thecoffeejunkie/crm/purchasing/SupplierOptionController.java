package ph.thecoffeejunkie.crm.purchasing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** ADMIN and WAREHOUSE (SecurityConfig): the supplier picker on stock in. */
@RestController
class SupplierOptionController {

    private final SupplierService suppliers;

    SupplierOptionController(SupplierService suppliers) {
        this.suppliers = suppliers;
    }

    @GetMapping("/api/supplier-options")
    List<SupplierOption> supplierOptions(@RequestParam(required = false) String q) {
        return suppliers.options(q);
    }
}
