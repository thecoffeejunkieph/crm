package ph.thecoffeejunkie.crm.purchasing;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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

import java.time.LocalDate;
import java.util.List;

/** ADMIN only (also gated by SecurityConfig on /api/suppliers/**). */
@RestController
@RequestMapping("/api/suppliers")
@PreAuthorize("hasRole('ADMIN')")
class SupplierController {

    private final SupplierService suppliers;

    SupplierController(SupplierService suppliers) {
        this.suppliers = suppliers;
    }

    @GetMapping
    PageResponse<SupplierResponse> listSuppliers(@RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "false") boolean includeArchived,
                                        PageQuery page) {
        return suppliers.list(q, includeArchived, page);
    }

    @GetMapping("/{id}")
    SupplierResponse getSupplier(@PathVariable long id) {
        return suppliers.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SupplierResponse createSupplier(@Valid @RequestBody SupplierRequest req) {
        return suppliers.create(req);
    }

    @PutMapping("/{id}")
    SupplierResponse updateSupplier(@PathVariable long id, @Valid @RequestBody SupplierRequest req) {
        return suppliers.update(id, req);
    }

    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    SupplierPaymentResponse recordSupplierPayment(@PathVariable long id, @Valid @RequestBody SupplierPaymentRequest req) {
        return suppliers.addPayment(id, req);
    }

    @GetMapping("/{id}/payments")
    List<SupplierPaymentResponse> supplierPayments(@PathVariable long id) {
        return suppliers.payments(id);
    }

    @DeleteMapping("/{id}/payments/{paymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteSupplierPayment(@PathVariable long id, @PathVariable long paymentId) {
        suppliers.deletePayment(id, paymentId);
    }

    @GetMapping("/{id}/statement")
    SupplierStatementResponse supplierStatement(@PathVariable long id,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return suppliers.statement(id, from, to);
    }

    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archiveSupplier(@PathVariable long id) {
        suppliers.archive(id);
    }
}
