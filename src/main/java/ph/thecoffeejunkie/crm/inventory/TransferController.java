package ph.thecoffeejunkie.crm.inventory;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;

/** Role gates are URL rules in SecurityConfig; warehouse scope is checked in the service (out of scope is 404). */
@RestController
class TransferController {

    private final TransferService transfers;

    TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    @PostMapping("/api/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    TransferResponse create(@Valid @RequestBody TransferRequest req) {
        return transfers.create(req);
    }

    @PutMapping("/api/transfers/{id}")
    TransferResponse update(@PathVariable long id, @Valid @RequestBody TransferRequest req) {
        return transfers.update(id, req);
    }

    @PostMapping("/api/transfers/{id}/dispatch")
    TransferResponse dispatch(@PathVariable long id) {
        return transfers.dispatch(id);
    }

    @PostMapping("/api/transfers/{id}/receive")
    TransferResponse receive(@PathVariable long id, @Valid @RequestBody ReceiveRequest req) {
        return transfers.receive(id, req);
    }

    @PostMapping("/api/transfers/{id}/cancel")
    TransferResponse cancel(@PathVariable long id) {
        return transfers.cancel(id);
    }

    @GetMapping("/api/transfers")
    PageResponse<TransferResponse> list(@RequestParam(required = false) TransferStatus status,
                                        @RequestParam(required = false) Long warehouseId,
                                        @RequestParam(required = false) String q, PageQuery page) {
        return transfers.list(status, warehouseId, q, page);
    }

    @GetMapping("/api/transfers/{id}")
    TransferResponse get(@PathVariable long id) {
        return transfers.get(id);
    }
}
