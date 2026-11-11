package ph.thecoffeejunkie.crm.shared;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;
import java.util.Set;

/** {@code sort} is {@code field,asc|desc}; only whitelisted fields are accepted. */
public record PageQuery(@Min(1) Integer page, @Min(1) @Max(100) Integer size, String sort) {

    public Pageable toPageable(Set<String> sortableFields, Sort defaultSort) {
        int p = page == null ? 1 : Math.max(1, page); // page 0 would make PageRequest throw a 500
        int s = size == null ? 20 : Math.clamp(size, 1, 100);
        Sort order = defaultSort;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            String field = parts[0].trim();
            String dir = parts.length > 1 ? parts[1].trim() : "asc";
            if (!sortableFields.contains(field) || !(dir.equalsIgnoreCase("asc") || dir.equalsIgnoreCase("desc"))) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED, "Cannot sort by '" + sort + "'",
                        Map.of("field", "sort"));
            }
            order = Sort.by(Sort.Direction.fromString(dir), field);
        }
        return PageRequest.of(p - 1, s, order);
    }
}
