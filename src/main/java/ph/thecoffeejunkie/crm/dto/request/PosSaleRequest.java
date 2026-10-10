package ph.thecoffeejunkie.crm.dto.request;

import ph.thecoffeejunkie.crm.constant.DiscountType;

import java.util.List;

// Totals are never sent: the server prices the sale from the products.
public record PosSaleRequest(
        Long customerId,
        List<PosSaleItemRequest> items,
        Integer discount,
        DiscountType discountType,
        List<PosPaymentRequest> payments,
        String notes,
        Boolean emailReceipt
) {}
