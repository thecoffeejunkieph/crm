package ph.thecoffeejunkie.crm.warehouses;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditChanges;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class Warehouses {

    private static final String ENTITY = "WAREHOUSE";

    private final WarehouseRepository repo;
    private final AuditLog audit;

    Warehouses(WarehouseRepository repo, AuditLog audit) {
        this.repo = repo;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> listActive() {
        return repo.findByArchivedAtIsNullOrderByCodeAsc().stream().map(Warehouses::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public WarehouseResponse get(long id) {
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public boolean allActive(Set<Long> ids) {
        return repo.countByIdInAndArchivedAtIsNull(ids) == ids.size();
    }

    /** The warehouse new orders ship from. */
    @Transactional(readOnly = true)
    public long defaultId() {
        return repo.findByIsDefaultTrueAndArchivedAtIsNull().map(w -> w.id).orElseThrow(() ->
                new DomainException(ErrorCode.VALIDATION_FAILED, "Choose a default warehouse first."));
    }

    /** Archived ones too: an order keeps showing where it ships from. */
    @Transactional(readOnly = true)
    public String name(long id) {
        return repo.findById(id).map(w -> w.name).orElse(null);
    }

    @Transactional
    public WarehouseResponse create(WarehouseRequest req) {
        String code = req.code().trim();
        requireCodeFree(code, null);
        Warehouse w = new Warehouse();
        w.code = code;
        w.name = req.name().trim();
        w.address = req.address();
        if (req.isDefault()) {
            clearDefault();
        }
        w.isDefault = req.isDefault();
        repo.saveAndFlush(w);
        audit.record(AuditAction.CREATED, ENTITY, w.id, "Created warehouse " + code, null);
        return toResponse(w);
    }

    @Transactional
    public WarehouseResponse update(long id, WarehouseRequest req, long version) {
        Warehouse w = find(id);
        if (w.version != version) {
            throw new DomainException(ErrorCode.STALE_VERSION,
                    "This record was changed by someone else. Reload and retry.");
        }
        String code = req.code().trim();
        String name = req.name().trim();
        requireCodeFree(code, id);
        if (w.isDefault && !req.isDefault()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Choose another default warehouse first.");
        }
        if (req.isDefault() && !w.isDefault) {
            clearDefault();
        }
        AuditChanges changes = AuditChanges.diff().field("code", w.code, code).field("name", w.name, name)
                .field("address", w.address, req.address()).field("isDefault", w.isDefault, req.isDefault());
        w.code = code;
        w.name = name;
        w.address = req.address();
        w.isDefault = req.isDefault();
        w.updatedAt = Instant.now();
        repo.saveAndFlush(w);
        audit.record(AuditAction.UPDATED, ENTITY, id, "Updated warehouse " + code, changes);
        return toResponse(w);
    }

    @Transactional
    public void archive(long id) {
        Warehouse w = find(id);
        if (w.isDefault) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Choose another default warehouse first.");
        }
        w.archivedAt = Instant.now();
        w.updatedAt = w.archivedAt;
        repo.saveAndFlush(w);
        audit.record(AuditAction.ARCHIVED, ENTITY, id, "Archived warehouse " + w.code, null);
    }

    private Warehouse find(long id) {
        return repo.findById(id).filter(w -> w.archivedAt == null)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Warehouse not found"));
    }

    private void requireCodeFree(String code, Long selfId) {
        repo.findActiveByCode(code).filter(w -> !w.id.equals(selfId)).ifPresent(w -> {
            throw new DomainException(ErrorCode.DUPLICATE, "Warehouse code already in use");
        });
    }

    // Flush the clear before the new default is written, or the partial unique index trips.
    private void clearDefault() {
        repo.findByIsDefaultTrueAndArchivedAtIsNull().ifPresent(old -> {
            old.isDefault = false;
            old.updatedAt = Instant.now();
            repo.saveAndFlush(old);
        });
    }

    private static WarehouseResponse toResponse(Warehouse w) {
        return new WarehouseResponse(w.id, w.code, w.name, w.address, w.isDefault, w.version);
    }
}
