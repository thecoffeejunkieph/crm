package ph.thecoffeejunkie.crm.customers;

import org.springframework.data.jpa.domain.Specification;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.util.Locale;

/** The only place that decides which customers a staff member can see. */
final class CustomerScopes {

    private CustomerScopes() {
    }

    static Specification<Customer> visibleTo(Scope s) {
        return visibleTo(s, false);
    }

    static Specification<Customer> visibleTo(Scope s, boolean includeArchived) {
        Specification<Customer> notArchived = (r, q, cb) -> includeArchived ? null : cb.isNull(r.get("archivedAt"));
        return switch (s.role()) {
            case ADMIN -> notArchived;
            case SALES -> notArchived.and((r, q, cb) -> cb.equal(r.get("assignedRepId"), s.userId()));
            case WAREHOUSE -> (r, q, cb) -> cb.disjunction();
        };
    }

    static Specification<Customer> idEquals(long id) {
        return (r, q, cb) -> cb.equal(r.get("id"), id);
    }

    static Specification<Customer> active() {
        return (r, q, cb) -> cb.isNull(r.get("archivedAt"));
    }

    /** Case-insensitive contains over name, business name, email and phone. */
    static Specification<Customer> matches(String q) {
        if (q == null || q.isBlank()) {
            return Specification.unrestricted();
        }
        String like = "%" + q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_") + "%";
        return (r, cq, cb) -> cb.or(
                cb.like(cb.lower(r.get("displayName")), like, '\\'),
                cb.like(cb.lower(cb.coalesce(r.<String>get("businessName"), "")), like, '\\'),
                cb.like(cb.lower(cb.coalesce(r.<String>get("email"), "")), like, '\\'),
                cb.like(cb.lower(cb.coalesce(r.<String>get("phone"), "")), like, '\\'),
                cb.like(cb.lower(cb.coalesce(r.<String>get("socialMedia"), "")), like, '\\'));
    }
}
