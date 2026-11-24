package ph.thecoffeejunkie.crm.purchasing;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditChanges;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.shared.SortRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
class SupplierService {

    private static final String ENTITY = "SUPPLIER";
    private static final Set<String> SORTABLE = Set.of("code", "name", "creditLimit");
    private static final Sort DEFAULT_SORT = Sort.by("name");

    private final SupplierRepository repo;
    private final AuditLog audit;

    SupplierService(SupplierRepository repo, AuditLog audit) {
        this.repo = repo;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    PageResponse<SupplierResponse> list(String q, boolean includeArchived, PageQuery page) {
        Specification<Supplier> spec = Specification.allOf(
                (r, cq, cb) -> includeArchived ? null : cb.isNull(r.get("archivedAt")), matches(q));
        var sort = SortRequest.<Supplier>of(page, SORTABLE, Map.of(), Map.of(), DEFAULT_SORT);
        return PageResponse.of(repo.findAll(spec.and(sort.orderSpec()), sort.pageable()).map(SupplierService::toResponse));
    }

    @Transactional(readOnly = true)
    SupplierResponse get(long id) {
        return toResponse(find(id));
    }

    @Transactional
    SupplierResponse create(SupplierRequest req) {
        Supplier s = new Supplier();
        apply(s, req);
        save(s);
        audit.record(AuditAction.CREATED, ENTITY, s.id, "Created supplier " + s.code, null);
        return toResponse(s);
    }

    @Transactional
    SupplierResponse update(long id, SupplierRequest req) {
        Supplier s = find(id);
        if (req.version() == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "version is required",
                    Map.of("errors", List.of(Map.of("field", "version", "message", "must not be null"))));
        }
        if (s.version != req.version()) {
            throw new DomainException(ErrorCode.STALE_VERSION,
                    "This record was changed by someone else. Reload and retry.");
        }
        AuditChanges changes = AuditChanges.diff()
                .field("code", s.code, req.code().trim())
                .field("name", s.name, req.name().trim())
                .field("paymentTermsDays", s.paymentTermsDays, req.paymentTermsDays())
                .field("leadTimeDays", s.leadTimeDays, req.leadTimeDays())
                .field("creditLimit", money(s.creditLimit), money(req.creditLimit()))
                .field("openingBalance", money(s.openingBalance), money(req.openingBalance()));
        apply(s, req);
        s.updatedAt = Instant.now();
        save(s);
        audit.record(AuditAction.UPDATED, ENTITY, id, "Updated supplier " + s.code, changes);
        return toResponse(s);
    }

    @Transactional
    void archive(long id) {
        Supplier s = find(id);
        if (s.archivedAt != null) {
            return;
        }
        s.archivedAt = Instant.now();
        s.updatedAt = s.archivedAt;
        repo.saveAndFlush(s);
        audit.record(AuditAction.ARCHIVED, ENTITY, id, "Archived supplier " + s.code, null);
    }

    private Supplier find(long id) {
        return repo.findById(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Supplier not found"));
    }

    // The unique constraint on code is the arbiter.
    private void save(Supplier s) {
        try {
            repo.saveAndFlush(s);
        } catch (DataIntegrityViolationException e) {
            throw new DomainException(ErrorCode.DUPLICATE, "A supplier with this code already exists.");
        }
    }

    private static void apply(Supplier s, SupplierRequest r) {
        s.code = r.code().trim();
        s.name = r.name().trim();
        s.contactName = r.contactName();
        s.phone = r.phone();
        s.email = r.email();
        s.address = r.address();
        s.paymentTermsDays = r.paymentTermsDays();
        s.leadTimeDays = r.leadTimeDays();
        s.creditLimit = r.creditLimit();
        s.openingBalance = r.openingBalance() == null ? BigDecimal.ZERO : r.openingBalance();
        s.notes = r.notes();
    }

    private static String money(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static Specification<Supplier> matches(String q) {
        if (q == null || q.isBlank()) {
            return Specification.unrestricted();
        }
        String like = "%" + q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_") + "%";
        return (r, cq, cb) -> cb.or(cb.like(cb.lower(r.get("code")), like, '\\'),
                cb.like(cb.lower(r.get("name")), like, '\\'));
    }

    private static SupplierResponse toResponse(Supplier s) {
        return new SupplierResponse(s.id, s.code, s.name, s.contactName, s.phone, s.email, s.address,
                s.paymentTermsDays, s.leadTimeDays, s.creditLimit, s.openingBalance, s.notes, s.archivedAt != null,
                null, s.version);
    }
}
