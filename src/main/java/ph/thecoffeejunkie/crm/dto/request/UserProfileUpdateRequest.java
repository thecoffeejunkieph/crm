package ph.thecoffeejunkie.crm.dto.request;

import java.time.LocalDate;

public record UserProfileUpdateRequest(
        String firstName,
        String lastName,
        String cellphoneNumber,
        String address,
        LocalDate birthday
) {}
