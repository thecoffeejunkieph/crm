package ph.thecoffeejunkie.crm.purchasing;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN only (SecurityConfig: /api/purchasing/**). */
@RestController
@RequestMapping("/api/purchasing/suggestions")
class SuggestionController {

    private final SuggestionQuery query;
    private final PurchaseOrderService orders;

    SuggestionController(SuggestionQuery query, PurchaseOrderService orders) {
        this.query = query;
        this.orders = orders;
    }

    @GetMapping
    SuggestionResponse reorderSuggestions() {
        return query.compute();
    }

    /** createDraft validates the supplier, warehouse and products (unknown or archived are rejected). */
    @PostMapping("/draft")
    @ResponseStatus(HttpStatus.CREATED)
    PoResponse draftFromSuggestions(@Valid @RequestBody DraftFromSuggestionsRequest req) {
        return orders.createDraft(new PoRequest(req.supplierId(), req.warehouseId(), null, null, req.lines()));
    }
}
