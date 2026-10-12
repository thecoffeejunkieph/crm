package ph.thecoffeejunkie.crm.approvals;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateApprovalRequest(@NotNull ApprovalType type, @NotBlank String subjectType, long subjectId,
                                    long subjectVersion, @NotNull Map<String, Object> requestedValue,
                                    @NotBlank String reason) {
}
