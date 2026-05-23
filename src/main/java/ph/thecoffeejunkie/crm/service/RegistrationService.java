package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ph.thecoffeejunkie.crm.constant.Role;
import ph.thecoffeejunkie.crm.dto.request.AuthenticationRequest;
import ph.thecoffeejunkie.crm.entity.CRMUser;
import ph.thecoffeejunkie.crm.repository.CRMUserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {


    private final PasswordEncoder passwordEncoder;
    private final CRMUserRepository crmUserRepository;

    public void register(AuthenticationRequest request) throws Exception {

        if (crmUserRepository.findByEmail(request.getEmail()).isPresent()) {
            log.error("Email already exists: {}", request.getEmail());
            throw new IllegalArgumentException("Email already exists");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        CRMUser user = CRMUser.builder()
                .email(request.getEmail())
                .password(encodedPassword)
                .active(true)
                .roles(Role.SALES.toString())
                .address(request.getAddress())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .cellphoneNumber(request.getCellphoneNumber())
                .birthday(null)
                .build();

        crmUserRepository.save(user);
    }
}
