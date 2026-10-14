package ph.thecoffeejunkie.crm.sales;

import ph.thecoffeejunkie.crm.catalog.TaxClass;
import ph.thecoffeejunkie.crm.customers.TaxTreatment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Spec §4. Prices are VAT-inclusive; VAT is split out once, at order level, so the breakdown always sums. */
final class OrderTotals {

    static final BigDecimal VAT_DIVISOR = new BigDecimal("1.12");
    private static final BigDecimal HUNDRED = new BigDecimal(100);

    record Line(BigDecimal qty, BigDecimal unitPrice, BigDecimal discountPct, TaxClass taxClass) {
    }

    record Result(List<BigDecimal> lineTotals, BigDecimal vatableSales, BigDecimal vat, BigDecimal vatExemptSales,
                  BigDecimal zeroRatedSales, BigDecimal total) {
    }

    private OrderTotals() {
    }

    static Result compute(List<Line> lines, BigDecimal shippingFee, TaxTreatment treatment) {
        Map<TaxClass, BigDecimal> gross = new EnumMap<>(TaxClass.class);
        for (TaxClass c : TaxClass.values()) {
            gross.put(c, BigDecimal.ZERO);
        }
        List<BigDecimal> lineTotals = new ArrayList<>();
        for (Line l : lines) {
            BigDecimal net = money(l.qty().multiply(l.unitPrice())
                    .multiply(HUNDRED.subtract(l.discountPct())).divide(HUNDRED));
            lineTotals.add(net);
            gross.merge(effective(l.taxClass(), treatment), net, BigDecimal::add);
        }
        gross.merge(effective(TaxClass.VATABLE, treatment), shippingFee, BigDecimal::add);

        BigDecimal vatableGross = money(gross.get(TaxClass.VATABLE));
        BigDecimal vatableSales = vatableGross.divide(VAT_DIVISOR, 2, RoundingMode.HALF_UP);
        BigDecimal exempt = money(gross.get(TaxClass.VAT_EXEMPT));
        BigDecimal zero = money(gross.get(TaxClass.ZERO_RATED));
        return new Result(List.copyOf(lineTotals), vatableSales, vatableGross.subtract(vatableSales), exempt, zero,
                vatableGross.add(exempt).add(zero));
    }

    /** Default price for a VATABLE product on a customer who pays no VAT. */
    static BigDecimal netOfVat(BigDecimal inclusive) {
        return inclusive.divide(VAT_DIVISOR, 2, RoundingMode.HALF_UP);
    }

    static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static TaxClass effective(TaxClass c, TaxTreatment t) {
        return switch (t) {
            case REGULAR -> c;
            case ZERO_RATED -> TaxClass.ZERO_RATED;
            case VAT_EXEMPT -> TaxClass.VAT_EXEMPT;
        };
    }
}
