package ph.thecoffeejunkie.crm.leads;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LeadRequest(@NotBlank @Size(max = 120) String contactName, @Size(max = 160) String company,
                          @Size(max = 40) String phone, @Email @Size(max = 254) String email,
                          @Size(max = 1000) String socialMedia, String address, @Size(max = 2000) String interest,
                          @Size(max = 4000) String notes, Long sourceId, Long assignedRepId, Long version) {

    /** Trimmed, blanks become null, so "has a way to reach them" and @Email see clean values. */
    public LeadRequest {
        contactName = clean(contactName);
        company = clean(company);
        phone = clean(phone);
        email = clean(email);
        socialMedia = clean(socialMedia);
        address = clean(address);
        interest = clean(interest);
        notes = clean(notes);
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
