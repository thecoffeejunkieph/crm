package ph.thecoffeejunkie.crm.shared;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;
import java.util.Set;

/** {@code sort} is {@code field,asc|desc}; only whitelisted fields are accepted. */
public record PageQuery(@Min(1) Integer page, @Min(1) @Max(100) Integer size, String sort) {

    /** Own-field sorting only; see {@link SortRequest} for linked-name and computed keys. */
    public Pageable toPageable(Set<String> sortableFields, Sort defaultSort) {
        return SortRequest.of(this, sortableFields, Map.of(), defaultSort).pageable();
    }
}
