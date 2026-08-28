package ph.thecoffeejunkie.crm.dto.response;

import java.time.LocalDate;
import java.util.List;

public record UserProfileResponse(
        String email,
        String firstName,
        String lastName,
        String cellphoneNumber,
        String address,
        LocalDate birthday,
        List<String> roles,
        String pictureUrl
) {}
