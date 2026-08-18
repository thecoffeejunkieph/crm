package ph.thecoffeejunkie.crm.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ph.thecoffeejunkie.crm.constant.CustomerType;
import ph.thecoffeejunkie.crm.dto.request.BusinessInformationRequest;
import ph.thecoffeejunkie.crm.dto.request.CustomerRequest;
import ph.thecoffeejunkie.crm.dto.response.CustomerResponse;
import ph.thecoffeejunkie.crm.dto.response.PageResponse;
import ph.thecoffeejunkie.crm.entity.BusinessInformation;
import ph.thecoffeejunkie.crm.entity.Customer;
import ph.thecoffeejunkie.crm.exception.InvalidRequestException;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
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
        customer.setPreferredShippingMethod(request.getPreferredShippingMethod());
        customer.setSource(request.getSource());
        customer.setCustomerType(request.getCustomerType());
        customer.setBusinessInformation(resolveBusinessInformation(request));

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
                .orElseThrow(() -> {
                    log.warn("Customer not found with id: {}", id);
                    return ResourceNotFoundException.of("Customer", id);
                });
    }

    public CustomerResponse update(Long id, CustomerRequest request) {

        Customer customer = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Customer not found with id: {}", id);
                    return ResourceNotFoundException.of("Customer", id);
                });

        customer.setFirstName(request.getFirstName());
        customer.setLastName(request.getLastName());
        customer.setPhoneNumber(request.getPhoneNumber());
        customer.setAddress(request.getAddress());
        customer.setPreferredShippingMethod(request.getPreferredShippingMethod());
        customer.setSource(request.getSource());
        customer.setCustomerType(request.getCustomerType());
        customer.setBusinessInformation(resolveBusinessInformation(request));

        return CustomMapper.toCustomerResponse(repository.save(customer));
    }

    public void delete(Long id) {

        Customer customer = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Customer not found with id: {}", id);
                    return ResourceNotFoundException.of("Customer", id);
                });

        repository.delete(customer);
    }

    private BusinessInformation resolveBusinessInformation(CustomerRequest request) {
        if (request.getCustomerType() != CustomerType.BUSINESS) {
            return null;
        }

        BusinessInformationRequest businessInformationRequest = request.getBusinessInformation();
        if (businessInformationRequest == null) {
            log.warn("Missing business information for business customer: {} {}", request.getFirstName(), request.getLastName());
            throw new InvalidRequestException("Business information is required for business customers");
        }

        return new BusinessInformation(
                businessInformationRequest.getBusinessName(),
                businessInformationRequest.getTin(),
                businessInformationRequest.getBusinessType()
        );
    }
}
