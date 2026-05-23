package ph.thecoffeejunkie.crm.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ph.thecoffeejunkie.crm.dto.request.CustomerRequest;
import ph.thecoffeejunkie.crm.dto.response.CustomerResponse;
import ph.thecoffeejunkie.crm.dto.response.PageResponse;
import ph.thecoffeejunkie.crm.entity.Customer;
import ph.thecoffeejunkie.crm.repository.CustomerRepository;
import ph.thecoffeejunkie.crm.util.CustomMapper;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerResponse create(CustomerRequest request) {

        Customer customer = new Customer();
        customer.setFirstName(request.getFirstName());
        customer.setLastName(request.getLastName());
        customer.setEmail(request.getEmail());
        customer.setPhoneNumber(request.getPhoneNumber());
        customer.setAddress(request.getAddress());

        return CustomMapper.toCustomerResponse(repository.save(customer));
    }

    public PageResponse<CustomerResponse> findAll(PageRequest pageRequest) {
        log.info("Getting all customers...");

        Page<Customer> customersPage = repository.findAll(pageRequest);

        log.info("Found {} customers", customersPage.getTotalElements());
        return new PageResponse<>
                (
                        customersPage.getPageable().getPageNumber() + 1,
                        customersPage.getPageable().getPageSize(),
                        customersPage.getTotalPages(),
                        customersPage.getTotalElements(),
                        customersPage.getContent().stream()
                                .map(CustomMapper::toCustomerResponse).toList()
                );
    }

    public CustomerResponse findById(Long id) {
        return repository.findById(id)
                .map(CustomMapper::toCustomerResponse)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + id));
    }

    public CustomerResponse update(Long id, CustomerRequest request) {

        Customer customer = repository.findById(id).orElseThrow();

        customer.setFirstName(request.getFirstName());
        customer.setLastName(request.getLastName());
        customer.setPhoneNumber(request.getPhoneNumber());
        customer.setAddress(request.getAddress());

        return CustomMapper.toCustomerResponse(repository.save(customer));
    }

    public void delete(Long id) {

        Customer customer = repository.findById(id).orElseThrow();

        repository.delete(customer);
    }
}
