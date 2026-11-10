package ph.thecoffeejunkie.crm.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ph.thecoffeejunkie.crm.customers.PaymentTerms;
import ph.thecoffeejunkie.crm.fulfilment.DeliveryMethod;

import java.math.BigDecimal;
import java.util.List;

/**
 * customerId is required on create and ignored on update; null paymentTerms = the customer's. Null
 * shipFromWarehouseId / deliveryMethod / discount / withholdingPct keep the current value (on create: the default
 * warehouse, DELIVERY, no discount, the Settings rate for a business customer and 0 otherwise).
 */
record OrderRequest(Long customerId, PaymentTerms paymentTerms,
                    @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal shippingFee,
                    String notes, String terms,
                    @NotNull @Size(max = 200) @Valid List<LineRequest> lines, Long version, String reason,
                    Long shipFromWarehouseId, DeliveryMethod deliveryMethod, DiscountType discountType,
                    @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal discountValue,
                    @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal withholdingPct) {
}
