package ph.thecoffeejunkie.crm.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.entity.DeliveryOrder;
import ph.thecoffeejunkie.crm.repository.DeliveryOrderRepository;

import java.time.Year;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DeliveryOrderNumberGenerator {

    private final DeliveryOrderRepository repository;

    public String generate() {

        int year = Year.now().getValue();

        Optional<DeliveryOrder> last = repository.findTopByOrderByIdDesc();

        int next = 1;

        if (last.isPresent()) {
            String lastNumber = last.get().getDeliveryOrderNumber();
            String[] parts = lastNumber.split("-");
            next = Integer.parseInt(parts[2]) + 1;
        }

        return String.format("DR-%d-%04d", year, next);
    }
}
