package ph.thecoffeejunkie.crm.inventory;

import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.Role;

import java.util.List;
import java.util.Map;

/** Warehouse scoping (spec section 6). A false answer is reported as NOT_FOUND by the callers. */
@Component("inventoryPermissions")
public class InventoryPermissions {

    private final CurrentActor actor;

    InventoryPermissions(CurrentActor actor) {
        this.actor = actor;
    }

    /** ADMIN or SALES anywhere, WAREHOUSE where assigned. */
    public boolean canRead(long warehouseId) {
        return actor.staff().map(s -> s.role() == Role.ADMIN || s.role() == Role.SALES
                || s.warehouseIds().contains(warehouseId)).orElse(false);
    }

    /** ADMIN anywhere, WAREHOUSE where assigned. */
    public boolean canAct(long warehouseId) {
        return actor.staff().map(s -> s.role() == Role.ADMIN
                || s.role() == Role.WAREHOUSE && s.warehouseIds().contains(warehouseId)).orElse(false);
    }

    /** For list endpoints: a named warehouse must be in scope; WAREHOUSE users must name one. */
    void require(Long warehouseId, boolean act) {
        if (warehouseId == null) {
            if (actor.scope().role() == Role.WAREHOUSE) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED, "warehouseId is required.",
                        Map.of("errors", List.of(Map.of("field", "warehouseId", "message", "Required."))));
            }
        } else if (!(act ? canAct(warehouseId) : canRead(warehouseId))) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Warehouse not found");
        }
    }
}
