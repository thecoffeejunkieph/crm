package ph.thecoffeejunkie.crm.catalog;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditChanges;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** The admin-managed product category list. */
@Service
class ProductCategories {

    private static final String ENTITY = "PRODUCT_CATEGORY";
    private static final String ARCHIVED_MSG = "That category is archived. Choose another.";

    private final ProductCategoryRepository repo;
    private final AuditLog audit;

    ProductCategories(ProductCategoryRepository repo, AuditLog audit) {
        this.repo = repo;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    List<ProductCategoryResponse> list(boolean includeArchived) {
        return (includeArchived ? repo.findAllByOrderByNameAsc() : repo.findByArchivedAtIsNullOrderByNameAsc())
                .stream().map(ProductCategories::toResponse).toList();
    }

    @Transactional
    ProductCategoryResponse add(ProductCategoryRequest req) {
        requireFree(req.name(), null);
        ProductCategory c = new ProductCategory();
        c.name = req.name();
        save(c);
        audit.record(AuditAction.CREATED, ENTITY, c.id, "Added product category " + c.name, null);
        return toResponse(c);
    }

    @Transactional
    ProductCategoryResponse rename(long id, ProductCategoryRequest req) {
        ProductCategory c = find(id);
        if (req.version() == null || req.version() != c.version) {
            throw new DomainException(ErrorCode.STALE_VERSION, "This record was changed by someone else. Reload and retry.");
        }
        requireFree(req.name(), id);
        AuditChanges changes = AuditChanges.diff().field("name", c.name, req.name());
        c.name = req.name();
        c.updatedAt = Instant.now();
        save(c);
        audit.record(AuditAction.UPDATED, ENTITY, id, "Renamed product category to " + c.name, changes);
        return toResponse(c);
    }

    @Transactional
    ProductCategoryResponse setArchived(long id, boolean archived) {
        ProductCategory c = find(id);
        if ((c.archivedAt != null) != archived) {
            c.archivedAt = archived ? Instant.now() : null;
            c.updatedAt = Instant.now();
            save(c);
            audit.record(archived ? AuditAction.ARCHIVED : AuditAction.UPDATED, ENTITY, id,
                    (archived ? "Archived" : "Restored") + " product category " + c.name, null);
        }
        return toResponse(c);
    }

    /** A category a product may newly point at: null is fine, unknown is 404, archived is refused. */
    void requireActive(Long id) {
        if (id != null && find(id).archivedAt != null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, ARCHIVED_MSG,
                    Map.of("errors", List.of(Map.of("field", "categoryId", "message", ARCHIVED_MSG))));
        }
    }

    /** A HashMap, so {@code get(null)} is safe for uncategorized products. */
    Map<Long, String> names(Collection<Long> ids) {
        Set<Long> distinct = new HashSet<>(ids);
        distinct.remove(null);
        return repo.findAllById(distinct).stream().collect(Collectors.toMap(c -> c.id, c -> c.name, (a, b) -> a, HashMap::new));
    }

    /** Active category by name, ignoring case; for CSV import. */
    Optional<Long> activeIdByName(String name) {
        return repo.findByNameIgnoreCase(name.trim()).filter(c -> c.archivedAt == null).map(c -> c.id);
    }

    private ProductCategory find(long id) {
        return repo.findById(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "That category doesn't exist."));
    }

    private void requireFree(String name, Long self) {
        repo.findByNameIgnoreCase(name).filter(c -> !c.id.equals(self)).ifPresent(c -> {
            throw exists();
        });
    }

    /** The unique index is the last word when two admins add the same name at once. */
    private void save(ProductCategory c) {
        try {
            repo.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            throw exists();
        }
    }

    private static DomainException exists() {
        return new DomainException(ErrorCode.DUPLICATE, "That category already exists.");
    }

    private static ProductCategoryResponse toResponse(ProductCategory c) {
        return new ProductCategoryResponse(c.id, c.name, c.archivedAt != null, c.version);
    }
}
