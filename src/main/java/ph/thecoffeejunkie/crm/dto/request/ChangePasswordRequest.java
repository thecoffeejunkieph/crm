package ph.thecoffeejunkie.crm.dto.request;

public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
) {}
