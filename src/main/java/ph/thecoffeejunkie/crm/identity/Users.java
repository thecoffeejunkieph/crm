package ph.thecoffeejunkie.crm.identity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.shared.Role;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Read-only view of staff users for other modules (customers, approvals, notifications). */
@Service
public class Users {

    private final AppUserRepository repo;

    Users(AppUserRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Optional<UserSummary> find(long id) {
        return repo.findById(id).map(u -> new UserSummary(u.id, u.email, u.displayName(), u.role, u.archivedAt == null));
    }

    @Transactional(readOnly = true)
    public boolean isActiveWithRole(long id, Role role) {
        return repo.existsByIdAndRoleAndArchivedAtIsNull(id, role);
    }

    @Transactional(readOnly = true)
    public List<Long> activeIdsWithRole(Role role) {
        return repo.activeIdsWithRole(role);
    }

    /** Active WAREHOUSE users assigned to the warehouse. */
    @Transactional(readOnly = true)
    public List<Long> idsForWarehouse(long warehouseId) {
        return repo.activeIdsForWarehouse(Role.WAREHOUSE, warehouseId);
    }

    /** Includes archived users: history still needs their names. Unknown ids are absent. */
    @Transactional(readOnly = true)
    public Map<Long, String> displayNames(Collection<Long> ids) {
        return repo.findAllById(ids).stream().collect(Collectors.toMap(u -> u.id, AppUser::displayName));
    }
}
