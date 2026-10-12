package ph.thecoffeejunkie.crm.customers;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface CustomerContactRepository extends JpaRepository<CustomerContact, Long> {

    List<CustomerContact> findByCustomerIdAndArchivedAtIsNullOrderById(long customerId);

    Optional<CustomerContact> findByIdAndCustomerIdAndArchivedAtIsNull(long id, long customerId);

    Optional<CustomerContact> findByEmailAndArchivedAtIsNull(String email);
}
