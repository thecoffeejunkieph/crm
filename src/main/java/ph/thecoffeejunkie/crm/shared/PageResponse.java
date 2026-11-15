package ph.thecoffeejunkie.crm.shared;

import org.springframework.data.domain.Page;

import java.util.List;

/** pageNumber is 1-based (Spring's Page is 0-based). */
public record PageResponse<T>(int pageNumber, int pageSize, int totalPages, long totalElements, List<T> content) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getNumber() + 1, page.getSize(), page.getTotalPages(),
                page.getTotalElements(), page.getContent());
    }
}
