package ph.thecoffeejunkie.crm.leads;

import org.springframework.data.jpa.domain.Specification;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.util.Locale;

/** The only place that decides which leads a staff member can see. */
final class LeadScopes {

    private LeadScopes() {
    }

    static Specification<Lead> visibleTo(Scope s) {
        return switch (s.role()) {
            case ADMIN -> Specification.unrestricted();
            case SALES -> (r, q, cb) -> cb.equal(r.get("assignedRepId"), s.userId());
            case WAREHOUSE -> (r, q, cb) -> cb.disjunction();
        };
    }

    static Specification<Lead> eq(String field, Object value) {
        return (r, q, cb) -> value == null ? null : cb.equal(r.get(field), value);
    }

    /** Case-insensitive contains over name, company, email, phone and social media. */
    static Specification<Lead> matches(String q) {
        if (q == null || q.isBlank()) {
            return Specification.unrestricted();
        }
        String like = "%" + q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_") + "%";
        return (r, cq, cb) -> cb.or(
                cb.like(cb.lower(r.get("contactName")), like, '\\'),
                cb.like(cb.lower(cb.coalesce(r.<String>get("company"), "")), like, '\\'),
                cb.like(cb.lower(cb.coalesce(r.<String>get("email"), "")), like, '\\'),
                cb.like(cb.lower(cb.coalesce(r.<String>get("phone"), "")), like, '\\'),
                cb.like(cb.lower(cb.coalesce(r.<String>get("socialMedia"), "")), like, '\\'));
    }
}
