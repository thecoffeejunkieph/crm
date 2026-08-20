package ph.thecoffeejunkie.crm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.dto.request.CustomerActivityRequest;
import ph.thecoffeejunkie.crm.dto.response.CustomerActivityResponse;
import ph.thecoffeejunkie.crm.service.CustomerActivityService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/activities")
@RequiredArgsConstructor
public class CustomerActivityController {

    private final CustomerActivityService customerActivityService;

    @GetMapping
    public List<CustomerActivityResponse> getAll(@PathVariable Long customerId) {
        return customerActivityService.findByCustomer(customerId);
    }

    @PostMapping
    public CustomerActivityResponse save(@PathVariable Long customerId,
                                          @RequestBody @Valid CustomerActivityRequest request) {
        return customerActivityService.create(customerId, request);
    }
}
