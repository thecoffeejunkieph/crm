package ph.thecoffeejunkie.crm.sales;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.customers.Customers;
import ph.thecoffeejunkie.crm.shared.Scope;

/** Every staff read/write of an order goes through here, so scope cannot be forgotten. */
@Component
class OrderAccess {

    private final SalesOrderRepository repo;
    private final Customers customers;

    OrderAccess(SalesOrderRepository repo, Customers customers) {
        this.repo = repo;
        this.customers = customers;
    }

    SalesOrder findVisible(long id, Scope s) {
        return repo.findOne(visible(id, s)).orElseThrow(OrderService::notFound);
    }

    /** Scope check, then the row lock every state change and money write takes. */
    SalesOrder findVisibleForUpdate(long id, Scope s) {
        if (!repo.exists(visible(id, s))) {
            throw OrderService.notFound();
        }
        return repo.findByIdForUpdate(id).orElseThrow(OrderService::notFound);
    }

    private Specification<SalesOrder> visible(long id, Scope s) {
        return Specification.allOf((r, q, cb) -> cb.equal(r.get("id"), id),
                OrderScopes.visibleTo(customers.visibleCustomerIds(s)));
    }
}
