package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import ph.thecoffeejunkie.crm.dto.request.CustomerActivityRequest;
import ph.thecoffeejunkie.crm.dto.response.CustomerActivityResponse;
import ph.thecoffeejunkie.crm.entity.CRMUser;
import ph.thecoffeejunkie.crm.entity.Customer;
import ph.thecoffeejunkie.crm.entity.CustomerActivity;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.CRMUserRepository;
import ph.thecoffeejunkie.crm.repository.CustomerActivityRepository;
import ph.thecoffeejunkie.crm.repository.CustomerRepository;
import ph.thecoffeejunkie.crm.util.CustomMapper;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerActivityService {

    private final CustomerActivityRepository repository;
    private final CustomerRepository customerRepository;
    private final CRMUserRepository crmUserRepository;

    public CustomerActivityResponse create(Long customerId, CustomerActivityRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> {
                    log.warn("Customer not found with id: {}", customerId);
                    return ResourceNotFoundException.of("Customer", customerId);
                });

        CustomerActivity activity = new CustomerActivity();
        activity.setCustomer(customer);
        activity.setType(request.getType());
        activity.setNotes(request.getNotes());
        activity.setCreatedBy(resolveCurrentUser());

        CustomerActivity saved = repository.save(activity);
        log.info("Logged {} activity for customer {}", saved.getType(), customerId);

        return CustomMapper.toCustomerActivityResponse(saved);
    }

    public List<CustomerActivityResponse> findByCustomer(Long customerId) {
        if (!customerRepository.existsById(customerId)) {
            log.warn("Customer not found with id: {}", customerId);
            throw ResourceNotFoundException.of("Customer", customerId);
        }

        return repository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(CustomMapper::toCustomerActivityResponse)
                .toList();
    }

    private CRMUser resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        return crmUserRepository.findByEmail(authentication.getName()).orElse(null);
    }
}
