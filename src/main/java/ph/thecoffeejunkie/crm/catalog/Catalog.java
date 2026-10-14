package ph.thecoffeejunkie.crm.catalog;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditChanges;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.shared.Role;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Public facade: other modules read products through {@link #info}; admins manage them via the controller. */
@Service
public class Catalog {

    private static final String ENTITY = "PRODUCT";
    private static final Set<String> SORTABLE = Set.of("sku", "name");
    private static final Sort DEFAULT_SORT = Sort.by("name");

    private final ProductRepository repo;
    private final AuditLog audit;
    private final CurrentActor actor;

    Catalog(ProductRepository repo, AuditLog audit, CurrentActor actor) {
        this.repo = repo;
        this.audit = audit;
        this.actor = actor;
    }

    @Transactional(readOnly = true)
    PageResponse<ProductResponse> list(String q, boolean includeArchived, PageQuery page) {
        Specification<Product> spec = Specification.allOf(
                (r, cq, cb) -> includeArchived ? null : cb.isNull(r.get("archivedAt")), matches(q));
        boolean admin = isAdmin();
        return PageResponse.of(repo.findAll(spec, page.toPageable(SORTABLE, DEFAULT_SORT))
                .map(p -> toResponse(p, admin)));
    }

    @Transactional(readOnly = true)
    ProductResponse get(long id) {
        return toResponse(find(id), isAdmin());
    }

    @Transactional
    ProductResponse create(ProductRequest req) {
        Product p = new Product();
        apply(p, req);
        save(p);
        audit.record(AuditAction.CREATED, ENTITY, p.id, "Created product " + p.sku, null);
        return toResponse(p, true);
    }

    @Transactional
    ProductResponse update(long id, ProductRequest req) {
        Product p = find(id);
        if (req.version() == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "version is required",
                    Map.of("errors", List.of(Map.of("field", "version", "message", "must not be null"))));
        }
        if (p.version != req.version()) {
            throw new DomainException(ErrorCode.STALE_VERSION,
                    "This record was changed by someone else. Reload and retry.");
        }
        AuditChanges changes = AuditChanges.diff()
                .field("sku", p.sku, req.sku().trim())
                .field("name", p.name, req.name().trim())
                .field("description", p.description, req.description())
                .field("unit", p.unit, req.unit())
                .field("price", money(p.price), money(req.price()))
                .field("cost", money(p.cost), money(req.cost()))
                .field("taxClass", p.taxClass, req.taxClass());
        apply(p, req);
        p.updatedAt = Instant.now();
        save(p);
        audit.record(AuditAction.UPDATED, ENTITY, id, "Updated product " + p.sku, changes);
        return toResponse(p, true);
    }

    @Transactional
    void archive(long id) {
        Product p = find(id);
        if (p.archivedAt != null) {
            return;
        }
        p.archivedAt = Instant.now();
        p.updatedAt = p.archivedAt;
        repo.saveAndFlush(p);
        audit.record(AuditAction.ARCHIVED, ENTITY, id, "Archived product " + p.sku, null);
    }

    /** Archived products included: orders keep referring to them. */
    @Transactional(readOnly = true)
    public Map<Long, ProductInfo> info(Collection<Long> ids) {
        Map<Long, ProductInfo> out = new HashMap<>();
        repo.findAllById(new HashSet<>(ids)).forEach(p -> out.put(p.id,
                new ProductInfo(p.id, p.sku, p.name, p.unit, p.price, p.cost, p.taxClass, p.archivedAt != null)));
        return out;
    }

    private Product find(long id) {
        return repo.findById(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Product not found"));
    }

    // The partial unique index on lower(sku) is the arbiter.
    private void save(Product p) {
        try {
            repo.saveAndFlush(p);
        } catch (DataIntegrityViolationException e) {
            throw new DomainException(ErrorCode.DUPLICATE, "A product with this SKU already exists.");
        }
    }

    private boolean isAdmin() {
        return actor.staff().filter(s -> s.role() == Role.ADMIN).isPresent();
    }

    private static void apply(Product p, ProductRequest r) {
        p.sku = r.sku().trim();
        p.name = r.name().trim();
        p.description = r.description();
        p.unit = r.unit();
        p.price = r.price();
        p.cost = r.cost();
        p.taxClass = r.taxClass();
    }

    private static String money(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static Specification<Product> matches(String q) {
        if (q == null || q.isBlank()) {
            return Specification.unrestricted();
        }
        String like = "%" + q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_") + "%";
        return (r, cq, cb) -> cb.or(cb.like(cb.lower(r.get("sku")), like, '\\'),
                cb.like(cb.lower(r.get("name")), like, '\\'));
    }

    private static ProductResponse toResponse(Product p, boolean withCost) {
        return new ProductResponse(p.id, p.sku, p.name, p.description, p.unit, p.price, withCost ? p.cost : null,
                p.taxClass, p.archivedAt, p.version);
    }
}
