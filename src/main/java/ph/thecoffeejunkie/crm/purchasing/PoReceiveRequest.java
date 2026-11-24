package ph.thecoffeejunkie.crm.purchasing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Receives stock against PO lines; each qty must be at most what remains on its line. */
record PoReceiveRequest(@NotNull LocalDate receivedOn, String reference, @NotEmpty List<@Valid PoReceiveLine> lines) {

    record PoReceiveLine(@NotNull Long lineId, @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal qty) {
    }
}
