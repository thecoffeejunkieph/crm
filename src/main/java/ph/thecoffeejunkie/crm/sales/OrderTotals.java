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
        return compute(lines, shippingFee, treatment, BigDecimal.ZERO);
    }

    /** {@code discount} is the whole-order peso discount: taken off the goods (never shipping) before VAT is split out. */
    static Result compute(List<Line> lines, BigDecimal shippingFee, TaxTreatment treatment, BigDecimal discount) {
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
        spread(gross, discount);
        gross.merge(effective(TaxClass.VATABLE, treatment), shippingFee, BigDecimal::add);

        BigDecimal vatableGross = money(gross.get(TaxClass.VATABLE));
        BigDecimal vatableSales = vatableGross.divide(VAT_DIVISOR, 2, RoundingMode.HALF_UP);
        BigDecimal exempt = money(gross.get(TaxClass.VAT_EXEMPT));
        BigDecimal zero = money(gross.get(TaxClass.ZERO_RATED));
        return new Result(List.copyOf(lineTotals), vatableSales, vatableGross.subtract(vatableSales), exempt, zero,
                vatableGross.add(exempt).add(zero));
    }

    /** The peso value of a whole-order discount on goods worth {@code goods}. */
    static BigDecimal discountAmount(DiscountType type, BigDecimal value, BigDecimal goods) {
        return money(type == DiscountType.PERCENT ? goods.multiply(value).divide(HUNDRED) : value);
    }

    /** Pro rata across the tax classes; the largest takes the rounding, so it can't go negative and the parts sum. */
    private static void spread(Map<TaxClass, BigDecimal> gross, BigDecimal discount) {
        if (discount.signum() == 0) {
            return;
        }
        BigDecimal goods = gross.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        TaxClass largest = gross.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        BigDecimal left = discount;
        for (TaxClass c : TaxClass.values()) {
            if (c != largest) {
                BigDecimal share = gross.get(c).multiply(discount).divide(goods, 2, RoundingMode.HALF_UP);
                gross.merge(c, share.negate(), BigDecimal::add);
                left = left.subtract(share);
            }
        }
        gross.merge(largest, left.negate(), BigDecimal::add);
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
