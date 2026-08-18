package ph.thecoffeejunkie.crm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import ph.thecoffeejunkie.crm.dto.request.CustomerRequest;
import ph.thecoffeejunkie.crm.dto.response.CustomerResponse;
import ph.thecoffeejunkie.crm.dto.response.PageResponse;
import ph.thecoffeejunkie.crm.service.CustomerService;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public PageResponse<CustomerResponse> getAll(@RequestParam (defaultValue = "0") int pageNumber,
                                                 @RequestParam (defaultValue = "10") int pageSize) {
        return customerService.findAll(PageRequest.of(pageNumber - 1, pageSize));
    }

    @PostMapping
    public CustomerResponse save(@RequestBody @Valid CustomerRequest request) {
        return customerService.create(request);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable Long id, @RequestBody @Valid CustomerRequest request) {
        return customerService.update(id, request);
    }

    @GetMapping("/{id}")
    public CustomerResponse findById(@PathVariable Long id) {
        return customerService.findById(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        customerService.delete(id);
    }
}
