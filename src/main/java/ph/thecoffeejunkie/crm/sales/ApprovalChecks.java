package ph.thecoffeejunkie.crm.sales;

import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.approvals.ApprovalType;
import ph.thecoffeejunkie.crm.approvals.RequiredApproval;
import ph.thecoffeejunkie.crm.catalog.ProductInfo;
import ph.thecoffeejunkie.crm.customers.CustomerSalesProfile;
import ph.thecoffeejunkie.crm.settings.SettingKey;
import ph.thecoffeejunkie.crm.settings.Settings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The approvals an order needs (spec §3). Subject version is the content fingerprint. requestedValues never carry
 * cost: they reach SALES in problem bodies and approval lists.
 */
@Component
class ApprovalChecks {

    static final String ORDER = "ORDER";

    private final SalesOrderRepository orders;
    private final Settings settings;

    ApprovalChecks(SalesOrderRepository orders, Settings settings) {
        this.orders = orders;
        this.settings = settings;
    }

    /**
     * Cash customers (no credit limit) ship before payment only with a CREDIT_LIMIT approval on its own subject,
     * so a confirm-time credit approval can't double as one.
     */
    List<RequiredApproval> forRelease(SalesOrder o, CustomerSalesProfile c) {
        if (c.creditLimit() != null || o.balance().signum() <= 0) {
            return List.of();
        }
        return List.of(new RequiredApproval(ApprovalType.CREDIT_LIMIT, "ORDER_RELEASE", o.id, OrderFingerprint.of(o),
                Map.of("reason", "SHIP_BEFORE_PAYMENT", "balance", o.balance())));
    }

    List<RequiredApproval> forConfirm(SalesOrder o, CustomerSalesProfile c, Map<Long, ProductInfo> products,
                                      LocalDate today) {
        long version = OrderFingerprint.of(o);
        List<RequiredApproval> out = new ArrayList<>();

        BigDecimal limit = settings.decimal(SettingKey.APPROVAL_DISCOUNT_PERCENT_MAX);
        o.lines.stream().map(OrderLine::effectiveDiscountPct).filter(d -> d.compareTo(limit) > 0).max(BigDecimal::compareTo)
                .ifPresent(max -> out.add(new RequiredApproval(ApprovalType.DISCOUNT, ORDER, o.id, version,
                        Map.of("maxDiscountPct", max, "limit", limit))));

        List<String> skus = o.lines.stream().filter(l -> {
            ProductInfo p = products.get(l.productId);
            return p != null && p.cost() != null && l.netUnitPrice().compareTo(p.cost()) < 0;
        }).map(l -> products.get(l.productId).sku()).filter(Objects::nonNull).distinct().toList();
        if (!skus.isEmpty()) {
            out.add(new RequiredApproval(ApprovalType.BELOW_COST, ORDER, o.id, version, Map.of("skus", skus)));
        }

        BigDecimal exposure = orders.openBalanceExcluding(o.customerId, o.id).add(o.total);
        boolean overLimit = c.creditLimit() != null && exposure.compareTo(c.creditLimit()) > 0;
        boolean overdue = orders.hasOverdue(o.customerId, today);
        if (overLimit || overdue) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("exposure", OrderTotals.money(exposure));
            value.put("creditLimit", c.creditLimit());
            value.put("overdue", overdue);
            out.add(new RequiredApproval(ApprovalType.CREDIT_LIMIT, ORDER, o.id, version, value));
        }
        return out;
    }
}
