package ph.thecoffeejunkie.crm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(@NotBlank @Size(max = 100) String name) {}
