package ph.thecoffeejunkie.crm.sales;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.audit.AuditEventResponse;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final OrderService orders;
    private final CurrentActor actor;

    OrderController(OrderService orders, CurrentActor actor) {
        this.orders = orders;
        this.actor = actor;
    }

    @GetMapping
    @PreAuthorize("@salesPermissions.canRead()")
    PageResponse<OrderSummary> list(@RequestParam(required = false) List<OrderStatus> status,
                                    @RequestParam(required = false) PaymentStatus paymentStatus,
                                    @RequestParam(required = false) Long customerId,
                                    @RequestParam(required = false) Long repId,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "false") boolean awaitingConfirmation,
                                    PageQuery page) {
        return orders.list(actor.scope(), new OrderFilter(status, paymentStatus, customerId, repId, q,
                awaitingConfirmation), page);
    }

    @PostMapping
    @PreAuthorize("@salesPermissions.canWrite()")
    OrderResponse create(@Valid @RequestBody OrderRequest req) {
        return orders.create(actor.scope(), req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@salesPermissions.canRead()")
    OrderResponse get(@PathVariable long id) {
        return orders.get(actor.scope(), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@salesPermissions.canWrite()")
    OrderResponse update(@PathVariable long id, @Valid @RequestBody OrderRequest req) {
        return orders.update(actor.scope(), id, req);
    }

    @PostMapping("/{id}/send-quote")
    @PreAuthorize("@salesPermissions.canWrite()")
    SendQuoteResponse sendQuote(@PathVariable long id, @Valid @RequestBody TransitionRequest req) {
        return orders.sendQuote(actor.scope(), id, req);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("@salesPermissions.canWrite()")
    OrderResponse confirm(@PathVariable long id, @Valid @RequestBody TransitionRequest req) {
        return orders.confirm(actor.scope(), id, req);
    }

    @GetMapping("/{id}/quote.pdf")
    @PreAuthorize("@salesPermissions.canRead()")
    ResponseEntity<byte[]> quotePdf(@PathVariable long id) {
        return orders.quotePdf(actor.scope(), id).inline();
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("@salesPermissions.canRead()")
    PageResponse<AuditEventResponse> history(@PathVariable long id, PageQuery page) {
        return orders.history(actor.scope(), id, page);
    }
}
