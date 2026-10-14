package ph.thecoffeejunkie.crm.sales;

import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;
import java.util.Set;

/** Order scope follows the customer (spec §5): reassigning a customer moves its orders. */
final class OrderScopes {

    private OrderScopes() {
    }

    static Specification<SalesOrder> visibleTo(Optional<Set<Long>> customerIds) {
        if (customerIds.isEmpty()) {
            return Specification.unrestricted();
        }
        Set<Long> ids = customerIds.get();
        return (r, q, cb) -> ids.isEmpty() ? cb.disjunction() : r.get("customerId").in(ids);
    }
}
