package ph.thecoffeejunkie.crm.sales;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** Read-only order facts for other modules. Unscoped: callers decide who may see what. */
@Service
public class Orders {

    private final SalesOrderRepository repo;

    Orders(SalesOrderRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Optional<OrderBrief> brief(long orderId) {
        return repo.findById(orderId).map(Orders::toBrief);
    }

    @Transactional(readOnly = true)
    public List<OrderBrief> briefs(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : repo.findAllById(new HashSet<>(ids)).stream().map(Orders::toBrief).toList();
    }

    private static OrderBrief toBrief(SalesOrder o) {
        return new OrderBrief(o.id, o.number, o.customerId, o.status, o.total, o.confirmedAt);
    }
}
