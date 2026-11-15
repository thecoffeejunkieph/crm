package ph.thecoffeejunkie.crm.customers;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditChanges;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.util.Collection;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** The lead-source list, for customers and the leads module. */
@Service
public class LeadSources {

    private static final String ENTITY = "LEAD_SOURCE";

    private final LeadSourceRepository repo;
    private final AuditLog audit;

    LeadSources(LeadSourceRepository repo, AuditLog audit) {
        this.repo = repo;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    List<LeadSourceResponse> list(boolean includeArchived) {
        return (includeArchived ? repo.findAllByOrderByNameAsc() : repo.findByArchivedAtIsNullOrderByNameAsc())
                .stream().map(LeadSources::toResponse).toList();
    }

    @Transactional
    LeadSourceResponse add(LeadSourceRequest req) {
        requireFree(req.name(), null);
        LeadSource s = new LeadSource();
        s.name = req.name();
        save(s);
        audit.record(AuditAction.LEAD_SOURCE_ADDED, ENTITY, s.id, "Added lead source " + s.name, null);
        return toResponse(s);
    }

    @Transactional
    LeadSourceResponse rename(long id, LeadSourceRequest req) {
        LeadSource s = find(id);
        if (req.version() == null || req.version() != s.version) {
            throw new DomainException(ErrorCode.STALE_VERSION, "This record was changed by someone else. Reload and retry.");
        }
        requireFree(req.name(), id);
        AuditChanges changes = AuditChanges.diff().field("name", s.name, req.name());
        s.name = req.name();
        s.updatedAt = Instant.now();
        save(s);
        audit.record(AuditAction.LEAD_SOURCE_RENAMED, ENTITY, id, "Renamed lead source to " + s.name, changes);
        return toResponse(s);
    }

    @Transactional
    LeadSourceResponse setArchived(long id, boolean archived) {
        LeadSource s = find(id);
        if ((s.archivedAt != null) != archived) {
            s.archivedAt = archived ? Instant.now() : null;
            s.updatedAt = Instant.now();
            save(s);
            audit.record(archived ? AuditAction.LEAD_SOURCE_ARCHIVED : AuditAction.LEAD_SOURCE_RESTORED, ENTITY, id,
                    (archived ? "Archived" : "Restored") + " lead source " + s.name, null);
        }
        return toResponse(s);
    }

    private LeadSource find(long id) {
        return repo.findById(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "That source doesn't exist."));
    }

    private void requireFree(String name, Long self) {
        repo.findByNameIgnoreCase(name).filter(s -> !s.id.equals(self)).ifPresent(s -> {
            throw exists();
        });
    }

    /** The unique index is the last word when two admins add the same name at once. */
    private void save(LeadSource s) {
        try {
            repo.saveAndFlush(s);
        } catch (DataIntegrityViolationException e) {
            throw exists();
        }
    }

    private static DomainException exists() {
        return new DomainException(ErrorCode.LEAD_SOURCE_EXISTS, "That source already exists.");
    }

    private static LeadSourceResponse toResponse(LeadSource s) {
        return new LeadSourceResponse(s.id, s.name, s.archivedAt != null, s.version);
    }

    /** A source a record may newly point at: null is fine, unknown is 404, archived is refused. */
    @Transactional(readOnly = true)
    public void requireActive(Long id) {
        if (id == null) {
            return;
        }
        LeadSource s = repo.findById(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND,
                "That source doesn't exist."));
        if (s.archivedAt != null) {
            throw new DomainException(ErrorCode.LEAD_SOURCE_ARCHIVED, "That source is archived. Choose another.");
        }
    }

    @Transactional(readOnly = true)
    public Map<Long, String> names(Collection<Long> ids) {
        Set<Long> distinct = new HashSet<>(ids);
        distinct.remove(null);
        return distinct.isEmpty() ? Map.of() : repo.findAllById(distinct).stream()
                .collect(Collectors.toMap(s -> s.id, s -> s.name));
    }

    public String name(Long id) {
        return id == null ? null : names(Set.of(id)).get(id);
    }
}
