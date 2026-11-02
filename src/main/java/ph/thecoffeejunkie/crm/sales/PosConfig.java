package ph.thecoffeejunkie.crm.sales;

import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.settings.SettingKey;
import ph.thecoffeejunkie.crm.settings.Settings;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.warehouses.Warehouses;

import java.math.BigDecimal;
import java.util.Set;

@Component
class PosConfig {

    private final Settings settings;
    private final Warehouses warehouses;

    PosConfig(Settings settings, Warehouses warehouses) {
        this.settings = settings;
        this.warehouses = warehouses;
    }

    /** The warehouse the till sells from; 0 means not configured. */
    long warehouseId() {
        long id = settings.decimal(SettingKey.POS_WAREHOUSE_ID).longValue();
        if (id == 0 || !warehouses.allActive(Set.of(id))) {
            throw new DomainException(ErrorCode.POS_NOT_CONFIGURED, "Set the POS warehouse in Settings.");
        }
        return id;
    }

    BigDecimal discountCapPct() {
        return settings.decimal(SettingKey.APPROVAL_DISCOUNT_PERCENT_MAX);
    }

    /** Cashiers may not go below cost, nor discount past the cap: the higher of the two floors. */
    static BigDecimal minUnitPrice(BigDecimal price, BigDecimal cost, BigDecimal capPct) {
        BigDecimal capFloor = OrderTotals.money(price.multiply(BigDecimal.valueOf(100).subtract(capPct))
                .divide(BigDecimal.valueOf(100)));
        return cost == null ? capFloor : capFloor.max(OrderTotals.money(cost));
    }
}
