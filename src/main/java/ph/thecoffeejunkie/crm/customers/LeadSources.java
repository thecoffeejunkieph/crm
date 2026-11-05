package ph.thecoffeejunkie.crm.customers;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** The lead-source list, for customers and the leads module. */
@Service
public class LeadSources {

    private final LeadSourceRepository repo;

    LeadSources(LeadSourceRepository repo) {
        this.repo = repo;
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
