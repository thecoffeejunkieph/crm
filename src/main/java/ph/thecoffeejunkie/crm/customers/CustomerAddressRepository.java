package ph.thecoffeejunkie.crm.customers;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByCustomerIdAndArchivedAtIsNullOrderByIsDefaultDescIdAsc(long customerId);

    Optional<CustomerAddress> findByIdAndCustomerIdAndArchivedAtIsNull(long id, long customerId);

    Optional<CustomerAddress> findByCustomerIdAndIsDefaultTrueAndArchivedAtIsNull(long customerId);
}