package ph.thecoffeejunkie.crm.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse
        (
                LocalDateTime timestamp,
                int status,
                String error,
                String message,
                String path,
                String requestId,
                List<String> details
        )
{
    public ErrorResponse(int status, String error, String message, String path, String requestId, List<String> details) {
        this(LocalDateTime.now(), status, error, message, path, requestId, details);
    }
}
