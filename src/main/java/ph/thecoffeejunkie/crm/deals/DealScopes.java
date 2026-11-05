package ph.thecoffeejunkie.crm.deals;

import org.springframework.data.jpa.domain.Specification;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.util.Collection;
import java.util.Locale;

/** The only place that decides which deals a staff member can see. */
final class DealScopes {

    private DealScopes() {
    }

    static Specification<Deal> visibleTo(Scope s) {
        return switch (s.role()) {
            case ADMIN -> Specification.unrestricted();
            case SALES -> (r, q, cb) -> cb.equal(r.get("repId"), s.userId());
            case WAREHOUSE -> (r, q, cb) -> cb.disjunction();
        };
    }

    static Specification<Deal> eq(String field, Object value) {
        return (r, q, cb) -> value == null ? null : cb.equal(r.get(field), value);
    }

    static Specification<Deal> stageIn(Collection<DealStage> stages) {
        return (r, q, cb) -> r.get("stage").in(stages);
    }

    /** Case-insensitive contains over the title, or a customer whose name matches (ids from the customers module). */
    static Specification<Deal> matches(String q, Collection<Long> customerIds) {
        if (q == null || q.isBlank()) {
            return Specification.unrestricted();
        }
        String like = "%" + q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_") + "%";
        return (r, cq, cb) -> customerIds.isEmpty() ? cb.like(cb.lower(r.get("title")), like, '\\')
                : cb.or(cb.like(cb.lower(r.get("title")), like, '\\'), r.get("customerId").in(customerIds));
    }
}
