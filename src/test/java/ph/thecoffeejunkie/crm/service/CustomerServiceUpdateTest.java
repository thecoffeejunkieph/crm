package ph.thecoffeejunkie.crm.service;

import org.junit.jupiter.api.Test;
import ph.thecoffeejunkie.crm.constant.CustomerType;
import ph.thecoffeejunkie.crm.dto.request.CustomerRequest;
import ph.thecoffeejunkie.crm.dto.response.CustomerResponse;
import ph.thecoffeejunkie.crm.entity.Customer;
import ph.thecoffeejunkie.crm.repository.CRMUserRepository;
import ph.thecoffeejunkie.crm.repository.CustomerRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerServiceUpdateTest {

    private final CustomerRepository repository = mock(CustomerRepository.class);
    private final CustomerService service = new CustomerService(repository, mock(CRMUserRepository.class));

    @Test
    void updatePersistsEmailAndSocialMedia() {
        Customer customer = new Customer();
        customer.setId(7L);
        customer.setEmail("old@example.com");
        when(repository.findById(7L)).thenReturn(Optional.of(customer));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomerRequest request = new CustomerRequest();
        request.setFirstName("Ana");
        request.setLastName("Cruz");
        request.setEmail("new@example.com");
        request.setSocialMedia("facebook.com/ana.c");
        request.setCustomerType(CustomerType.INDIVIDUAL);

        CustomerResponse response = service.update(7L, request);

        assertEquals("new@example.com", response.email());
        assertEquals("facebook.com/ana.c", response.socialMedia());
    }
}
