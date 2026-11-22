package ph.thecoffeejunkie.crm.customers;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A customer's delivery addresses; at most one default (spec 2026-11-22-customer-self-service). */
@Service
public class CustomerAddresses {

    private static final String ENTITY = "CUSTOMER";

    private final CustomerAddressRepository repo;
    private final AuditLog audit;

    CustomerAddresses(CustomerAddressRepository repo, AuditLog audit) {
        this.repo = repo;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    List<AddressResponse> list(long customerId) {
        return repo.findByCustomerIdAndArchivedAtIsNullOrderByIsDefaultDescIdAsc(customerId).stream()
                .map(CustomerAddresses::toResponse).toList();
    }

    @Transactional
    AddressResponse add(long customerId, AddressRequest req) {
        CustomerAddress a = new CustomerAddress();
        a.customerId = customerId;
        boolean first = repo.findByCustomerIdAndArchivedAtIsNullOrderByIsDefaultDescIdAsc(customerId).isEmpty();
        fill(a, req, first || req.defaultAddress());
        audit.record(AuditAction.UPDATED, ENTITY, customerId, "Added delivery address " + a.label, null);
        return toResponse(a);
    }

    @Transactional
    AddressResponse update(long customerId, long id, AddressRequest req) {
        CustomerAddress a = find(customerId, id);
        if (req.version() == null || req.version() != a.version) {
            throw new DomainException(ErrorCode.STALE_VERSION, "This record was changed by someone else. Reload and retry.");
        }
        fill(a, req, a.isDefault || req.defaultAddress()); // unset a default by making another one default
        audit.record(AuditAction.UPDATED, ENTITY, customerId, "Updated delivery address " + a.label, null);
        return toResponse(a);
    }

    @Transactional
    void archive(long customerId, long id) {
        CustomerAddress a = find(customerId, id);
        a.archivedAt = Instant.now();
        a.updatedAt = a.archivedAt;
        a.isDefault = false;
        repo.saveAndFlush(a);
        audit.record(AuditAction.UPDATED, ENTITY, customerId, "Removed delivery address " + a.label, null);
    }

    /** The default delivery address's text, if the customer has one. */
    @Transactional(readOnly = true)
    public Optional<String> defaultAddress(long customerId) {
        return repo.findByCustomerIdAndIsDefaultTrueAndArchivedAtIsNull(customerId).map(a -> a.address);
    }

    /** An active address of this customer, or 400 on addressId. */
    @Transactional(readOnly = true)
    public String address(long customerId, long addressId) {
        return repo.findByIdAndCustomerIdAndArchivedAtIsNull(addressId, customerId).map(a -> a.address)
                .orElseThrow(() -> new DomainException(ErrorCode.VALIDATION_FAILED, "Choose one of your addresses.",
                        Map.of("errors", List.of(Map.of("field", "addressId", "message", "Choose one of your addresses.")))));
    }

    private void fill(CustomerAddress a, AddressRequest req, boolean makeDefault) {
        if (makeDefault && !a.isDefault) {
            repo.findByCustomerIdAndIsDefaultTrueAndArchivedAtIsNull(a.customerId).ifPresent(d -> {
                d.isDefault = false;
                d.updatedAt = Instant.now();
                repo.saveAndFlush(d); // before ours, so the one-default index never sees two
            });
        }
        a.label = req.label().trim();
        a.address = req.address().trim();
        a.isDefault = makeDefault;
        a.updatedAt = Instant.now();
        repo.saveAndFlush(a);
    }

    private CustomerAddress find(long customerId, long id) {
        return repo.findByIdAndCustomerIdAndArchivedAtIsNull(id, customerId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Address not found"));
    }

    private static AddressResponse toResponse(CustomerAddress a) {
        return new AddressResponse(a.id, a.label, a.address, a.isDefault, a.version);
    }
}
