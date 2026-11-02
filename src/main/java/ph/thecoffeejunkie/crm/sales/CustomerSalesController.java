package ph.thecoffeejunkie.crm.sales;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.customers.Customers;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The Orders and Invoices tabs of the customer page. Customer not visible → 404. */
@RestController
class CustomerSalesController {

    private final Customers customers;
    private final OrderService orders;
    private final InvoiceRepository invoices;
    private final SalesOrderRepository orderRepo;
    private final CurrentActor actor;
    private final Clock clock;

    CustomerSalesController(Customers customers, OrderService orders, InvoiceRepository invoices,
                            SalesOrderRepository orderRepo, CurrentActor actor, Clock clock) {
        this.customers = customers;
        this.orders = orders;
        this.invoices = invoices;
        this.orderRepo = orderRepo;
        this.actor = actor;
        this.clock = clock;
    }

    @GetMapping("/api/customers/{id}/orders")
    @PreAuthorize("@salesPermissions.canRead()")
    PageResponse<OrderSummary> orders(@PathVariable long id, PageQuery page) {
        Scope s = requireVisible(id);
        return orders.list(s, new OrderFilter(null, null, id, null, null, false), page);
    }

    @GetMapping("/api/customers/{id}/invoices")
    @PreAuthorize("@salesPermissions.canRead()")
    @Transactional(readOnly = true)
    PageResponse<CustomerInvoiceRow> invoices(@PathVariable long id, PageQuery page) {
        requireVisible(id);
        var found = invoices.findByCustomer(id, page.toPageable(Set.of("issuedAt", "number"),
                Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, SalesOrder> byId = orderRepo.findAllById(found.getContent().stream().map(i -> i.orderId).toList())
                .stream().collect(Collectors.toMap(o -> o.id, Function.identity()));
        LocalDate today = SalesTime.today(clock);
        return PageResponse.of(found.map(i -> {
            SalesOrder o = byId.get(i.orderId);
            return new CustomerInvoiceRow(i.id, i.number, i.status, i.orderId, o.number, i.issuedAt, i.dueDate, i.total,
                    i.status == InvoiceStatus.ISSUED ? OrderService.paymentStatus(o, today) : null);
        }));
    }

    @GetMapping("/api/customers/{id}/account")
    @PreAuthorize("@salesPermissions.canRead()")
    @Transactional(readOnly = true)
    CustomerAccount account(@PathVariable long id) {
        var profile = customers.salesProfileVisible(actor.scope(), id)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Customer not found"));
        return new CustomerAccount(orderRepo.openBalanceExcluding(id, 0), profile.creditLimit(),
                orderRepo.hasOverdue(id, SalesTime.today(clock)));
    }

    private Scope requireVisible(long customerId) {
        Scope s = actor.scope();
        customers.salesProfileVisible(s, customerId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Customer not found"));
        return s;
    }
}
