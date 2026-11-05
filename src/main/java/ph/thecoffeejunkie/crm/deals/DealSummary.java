package ph.thecoffeejunkie.crm.deals;

import java.math.BigDecimal;
import java.util.List;

/** Count and summed estimated value per stage; deals without a value count but add nothing. */
public record DealSummary(List<StageTotal> stages, long openCount, BigDecimal openValue) {

    public record StageTotal(DealStage stage, long count, BigDecimal value) {
    }
}
