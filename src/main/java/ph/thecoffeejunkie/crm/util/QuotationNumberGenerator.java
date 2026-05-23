package ph.thecoffeejunkie.crm.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.entity.Quotation;
import ph.thecoffeejunkie.crm.repository.QuotationRepository;

import java.time.Year;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class QuotationNumberGenerator {

    private final QuotationRepository repository;

    public String generate() {

        int year = Year.now().getValue();

        Optional<Quotation> last = repository.findTopByOrderByIdDesc();

        int next = 1;

        if (last.isPresent()) {
            String lastNumber = last.get().getQuotationNumber();
            String[] parts = lastNumber.split("-");
            next = Integer.parseInt(parts[2]) + 1;
        }

        return String.format("QTN-%d-%04d", year, next);
    }
}
