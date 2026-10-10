package ph.thecoffeejunkie.crm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.dto.request.PosSaleRequest;
import ph.thecoffeejunkie.crm.dto.response.CustomerResponse;
import ph.thecoffeejunkie.crm.dto.response.InvoiceResponse;
import ph.thecoffeejunkie.crm.dto.response.PageResponse;
import ph.thecoffeejunkie.crm.dto.response.PosProductResponse;
import ph.thecoffeejunkie.crm.dto.response.PosSaleResponse;
import ph.thecoffeejunkie.crm.dto.response.PosSaleResponse.EmailStatus;
import ph.thecoffeejunkie.crm.service.InvoiceEmailService;
import ph.thecoffeejunkie.crm.service.PosSaleService;

@Slf4j
@RestController
@RequestMapping("/api/v1/pos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN','SALES')")
public class PosController {

    private final PosSaleService posSaleService;
    private final InvoiceEmailService invoiceEmailService;

    @GetMapping("/products")
    public PageResponse<PosProductResponse> products(@RequestParam(required = false) String q,
                                                     @RequestParam(required = false) Long categoryId,
                                                     @RequestParam(defaultValue = "1") int pageNumber,
                                                     @RequestParam(defaultValue = "24") int pageSize) {
        return posSaleService.searchProducts(q, categoryId,
                PageRequest.of(pageNumber - 1, pageSize, Sort.by("productName")));
    }

    // Not transactional: the sale commits inside checkout, so a failed email can never undo it.
    @PostMapping("/sales")
    public PosSaleResponse checkout(@RequestBody PosSaleRequest request) {
        PosSaleResponse sale = posSaleService.checkout(request);

        CustomerResponse customer = sale.invoice().customer();
        boolean hasEmail = customer != null && customer.email() != null && !customer.email().isBlank();
        if (!Boolean.TRUE.equals(request.emailReceipt()) || !hasEmail) {
            return sale;
        }
        try {
            invoiceEmailService.send(sale.invoice().id());
            return sale.withEmailStatus(EmailStatus.SENT);
        } catch (Exception e) {
            log.error("Receipt email failed for POS sale {}", sale.invoice().invoiceNumber(), e);
            return sale.withEmailStatus(EmailStatus.FAILED);
        }
    }

    @PostMapping("/sales/{id}/email")
    public InvoiceResponse emailReceipt(@PathVariable Long id) {
        posSaleService.requirePosSale(id);
        return invoiceEmailService.send(id);
    }
}
