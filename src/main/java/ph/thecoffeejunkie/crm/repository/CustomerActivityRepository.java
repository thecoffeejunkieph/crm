package ph.thecoffeejunkie.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ph.thecoffeejunkie.crm.entity.CustomerActivity;

import java.util.List;

@Repository
public interface CustomerActivityRepository extends JpaRepository<CustomerActivity, Long> {

    List<CustomerActivity> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
