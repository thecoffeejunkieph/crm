package ph.thecoffeejunkie.crm.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthenticationRequest {

    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private String cellphoneNumber;
    private String address;
    private String birthday;
}
