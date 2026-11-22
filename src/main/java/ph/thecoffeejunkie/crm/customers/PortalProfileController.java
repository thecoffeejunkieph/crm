package ph.thecoffeejunkie.crm.customers;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.shared.ContactPrincipal;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.util.List;

/** A portal contact keeps their company's contacts and delivery addresses (URL rule /api/portal/** → CUSTOMER). */
@RestController
@RequestMapping("/api/portal")
class PortalProfileController {

    private final Customers customers;
    private final CustomerAddresses addresses;
    private final CurrentActor actor;

    PortalProfileController(Customers customers, CustomerAddresses addresses, CurrentActor actor) {
        this.customers = customers;
        this.addresses = addresses;
        this.actor = actor;
    }

    @GetMapping("/contacts")
    List<ContactResponse> contacts() {
        return customers.portalContacts(me());
    }

    @PostMapping("/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    ContactResponse addContact(@Valid @RequestBody ContactRequest req) {
        return customers.portalAddContact(me(), req);
    }

    @PutMapping("/contacts/{id}")
    ContactResponse updateContact(@PathVariable long id, @Valid @RequestBody ContactRequest req) {
        return customers.portalUpdateContact(me(), id, req);
    }

    @PostMapping("/contacts/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archiveContact(@PathVariable long id) {
        customers.portalArchiveContact(me(), id);
    }

    @GetMapping("/addresses")
    List<AddressResponse> addresses() {
        return addresses.list(me().customerId());
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    AddressResponse addAddress(@Valid @RequestBody AddressRequest req) {
        return addresses.add(me().customerId(), req);
    }

    @PutMapping("/addresses/{id}")
    AddressResponse updateAddress(@PathVariable long id, @Valid @RequestBody AddressRequest req) {
        return addresses.update(me().customerId(), id, req);
    }

    @PostMapping("/addresses/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archiveAddress(@PathVariable long id) {
        addresses.archive(me().customerId(), id);
    }

    private ContactPrincipal me() {
        return actor.contact().orElseThrow(() -> new DomainException(ErrorCode.UNAUTHORIZED, "Login required"));
    }
}