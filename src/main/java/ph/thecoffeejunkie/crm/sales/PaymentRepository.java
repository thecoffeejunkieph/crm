package ph.thecoffeejunkie.crm.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderIdOrderByIdDesc(long orderId);

    List<Payment> findByOrderIdAndStatus(long orderId, PaymentVerification status);

    boolean existsByOrderIdAndStatus(long orderId, PaymentVerification status);
}
