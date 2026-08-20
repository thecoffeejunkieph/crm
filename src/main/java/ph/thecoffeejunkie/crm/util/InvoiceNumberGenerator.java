package ph.thecoffeejunkie.crm.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.entity.Invoice;
import ph.thecoffeejunkie.crm.repository.InvoiceRepository;

import java.time.Year;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class InvoiceNumberGenerator {

    private final InvoiceRepository repository;

    public String generate() {

        int year = Year.now().getValue();

        Optional<Invoice> last = repository.findTopByOrderByIdDesc();

        int next = 1;

        if (last.isPresent()) {
            String lastNumber = last.get().getInvoiceNumber();
            String[] parts = lastNumber.split("-");
            next = Integer.parseInt(parts[2]) + 1;
        }

        return String.format("INV-%d-%04d", year, next);
    }
}
