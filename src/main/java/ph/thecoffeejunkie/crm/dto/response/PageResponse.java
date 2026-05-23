package ph.thecoffeejunkie.crm.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageResponse<T>
        (
                int pageNumber,
                int pageSize,
                int totalPages,
                long totalElements,
                List<T> content
        )
{}
