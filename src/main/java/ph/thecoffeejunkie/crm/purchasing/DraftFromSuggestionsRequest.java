package ph.thecoffeejunkie.crm.purchasing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import ph.thecoffeejunkie.crm.purchasing.PoRequest.PoLineRequest;

import java.util.List;

/** Lines without a unitCost take the product's cost; a null warehouseId takes the default warehouse. */
record DraftFromSuggestionsRequest(@NotNull Long supplierId, Long warehouseId,
                                   @NotEmpty List<@Valid PoLineRequest> lines) {
}
