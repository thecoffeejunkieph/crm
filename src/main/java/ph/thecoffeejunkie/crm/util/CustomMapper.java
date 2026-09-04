package ph.thecoffeejunkie.crm.util;

import ph.thecoffeejunkie.crm.dto.response.BusinessInformationResponse;
import ph.thecoffeejunkie.crm.dto.response.CustomerActivityResponse;
import ph.thecoffeejunkie.crm.dto.response.CustomerResponse;
import ph.thecoffeejunkie.crm.dto.response.DeliveryOrderItemResponse;
import ph.thecoffeejunkie.crm.dto.response.DeliveryOrderResponse;
import ph.thecoffeejunkie.crm.dto.response.InventoryItemResponse;
import ph.thecoffeejunkie.crm.dto.response.InvoiceItemResponse;
import ph.thecoffeejunkie.crm.dto.response.InvoicePaymentResponse;
import ph.thecoffeejunkie.crm.dto.response.InvoiceResponse;
import ph.thecoffeejunkie.crm.dto.response.ProductResponse;
import ph.thecoffeejunkie.crm.dto.response.QuotationItemResponse;
import ph.thecoffeejunkie.crm.dto.response.QuotationResponse;
import ph.thecoffeejunkie.crm.dto.response.SalesRepResponse;
import ph.thecoffeejunkie.crm.dto.response.StockMovementResponse;
import ph.thecoffeejunkie.crm.dto.response.WarehouseResponse;
import ph.thecoffeejunkie.crm.entity.BusinessInformation;
import ph.thecoffeejunkie.crm.entity.CRMUser;
import ph.thecoffeejunkie.crm.entity.Customer;
import ph.thecoffeejunkie.crm.entity.CustomerActivity;
import ph.thecoffeejunkie.crm.entity.DeliveryOrder;
import ph.thecoffeejunkie.crm.entity.Invoice;
import ph.thecoffeejunkie.crm.entity.InvoiceItem;
import ph.thecoffeejunkie.crm.entity.InvoicePayment;
import ph.thecoffeejunkie.crm.entity.InventoryItem;
import ph.thecoffeejunkie.crm.entity.Product;
import ph.thecoffeejunkie.crm.entity.Quotation;
import ph.thecoffeejunkie.crm.entity.QuotationItem;
import ph.thecoffeejunkie.crm.entity.StockMovement;
import ph.thecoffeejunkie.crm.entity.Warehouse;
import ph.thecoffeejunkie.crm.constant.InvoiceStatus;
import ph.thecoffeejunkie.crm.constant.PaymentTerms;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class CustomMapper {

    private CustomMapper() {
        /* This utility class should not be instantiated */
    }

    public static QuotationResponse toQuotationResponse(Quotation quotation) {
        return new QuotationResponse(
                quotation.getId(),
                quotation.getQuotationNumber(),
                quotation.getQuotationItems().stream().map(CustomMapper::toQuotationItemResponse).toList(),
                toCustomerResponse(quotation.getCustomer()),
                quotation.getStatus(),
                quotation.getTotalAmount(),
                quotation.getShippingCharges(),
                quotation.getDiscount(),
                quotation.getDiscountType(),
                quotation.getQuoteDate(),
                quotation.getExpiryDate(),
                quotation.getNotes(),
                quotation.getTermsAndConditions(),
                toSalesRepResponse(quotation.getSalesRep()),
                quotation.getPaymentTerms(),
                quotation.getPaymentTerms() != null ? quotation.getPaymentTerms().getLabel() : null
                );
    }

    public static InvoiceResponse toInvoiceResponse(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getQuotation() != null ? invoice.getQuotation().getId() : null,
                invoice.getQuotation() != null ? invoice.getQuotation().getQuotationNumber() : null,
                invoice.getInvoiceItems().stream().map(CustomMapper::toInvoiceItemResponse).toList(),
                toCustomerResponse(invoice.getCustomer()),
                toSalesRepResponse(invoice.getSalesRep()),
                invoice.getStatus(),
                resolveInvoiceStatusLabel(invoice),
                invoice.getTotalAmount(),
                invoice.getShippingCharges(),
                invoice.getDiscount(),
                invoice.getDiscountType(),
                invoice.getInvoiceDate(),
                invoice.getDueDate(),
                invoice.getPaymentTerms(),
                invoice.getPaymentTerms() != null ? invoice.getPaymentTerms().getLabel() : null,
                invoice.getNotes(),
                invoice.getTermsAndConditions(),
                invoice.getProofOfPaymentPath(),
                invoice.getPayments().stream().map(CustomMapper::toInvoicePaymentResponse).toList(),
                invoice.getPaidAt()
                );
    }

    /**
     * The "Awaiting Payment" stage renders as fixed text for Due-on-Receipt invoices, but as a
     * live day-countdown (or overdue count) for Net-terms invoices, recomputed from today's date
     * rather than stored, so it never goes stale.
     */
    public static String resolveInvoiceStatusLabel(Invoice invoice) {
        InvoiceStatus status = invoice.getStatus();
        if (status == InvoiceStatus.PAID) {
            return "Paid";
        }
        if (status == InvoiceStatus.FOR_PAYMENT_VERIFICATION) {
            return "For Payment Verification";
        }

        if (invoice.getPaymentTerms() == null || invoice.getPaymentTerms() == PaymentTerms.DUE_ON_RECEIPT) {
            return "Awaiting Payment";
        }

        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), invoice.getDueDate());
        return daysLeft >= 0
                ? "Due on " + daysLeft + " days"
                : "Overdue by " + Math.abs(daysLeft) + " days";
    }

    public static InvoiceItemResponse toInvoiceItemResponse(InvoiceItem invoiceItem) {
        return new InvoiceItemResponse(
                invoiceItem.getQuantity(),
                invoiceItem.getPrice(),
                invoiceItem.getDiscount(),
                invoiceItem.getDiscountType(),
                invoiceItem.getTotal(),
                toProductResponse(invoiceItem.getProduct())
                );
    }

    public static InvoicePaymentResponse toInvoicePaymentResponse(InvoicePayment payment) {
        return new InvoicePaymentResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getMethod() != null ? payment.getMethod().getLabel() : null,
                payment.getProofOfPaymentPath(),
                payment.getRecordedAt()
                );
    }

    public static SalesRepResponse toSalesRepResponse(CRMUser salesRep) {
        if (salesRep == null) {
            return null;
        }
        return new SalesRepResponse(salesRep.getEmail(), salesRep.getFirstName(), salesRep.getLastName());
    }

    public static ProductResponse toProductResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getProductName(),
                product.getDescription(),
                product.getUnit(),
                product.getPrice(),
                product.getPicturePath()
        );
    }

    public static CustomerResponse toCustomerResponse(Customer customer){
        return new CustomerResponse(
                customer.getId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getAddress(),
                customer.getPhoneNumber(),
                customer.getPreferredShippingMethod(),
                customer.getSource(),
                customer.getCustomerType(),
                toBusinessInformationResponse(customer.getBusinessInformation()),
                toSalesRepResponse(customer.getAssignedRep())
        );
    }

    public static CustomerActivityResponse toCustomerActivityResponse(CustomerActivity activity) {
        return new CustomerActivityResponse(
                activity.getId(),
                activity.getType(),
                activity.getNotes(),
                activity.getCreatedAt(),
                toSalesRepResponse(activity.getCreatedBy())
        );
    }

    public static BusinessInformationResponse toBusinessInformationResponse(BusinessInformation businessInformation) {
        if (businessInformation == null) {
            return null;
        }
        return new BusinessInformationResponse(
                businessInformation.getBusinessName(),
                businessInformation.getTin(),
                businessInformation.getBusinessType()
        );
    }

    public static QuotationItemResponse toQuotationItemResponse(QuotationItem quotationItem) {
        return new QuotationItemResponse(
                quotationItem.getQuantity(),
                quotationItem.getPrice(),
                quotationItem.getDiscount(),
                quotationItem.getDiscountType(),
                quotationItem.getTotal(),
                toProductResponse(quotationItem.getProduct())
                );
    }

    public static WarehouseResponse toWarehouseResponse(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getName(),
                warehouse.getCode(),
                warehouse.getAddress(),
                warehouse.isActive(),
                warehouse.isDefaultWarehouse()
        );
    }

    public static InventoryItemResponse toInventoryItemResponse(InventoryItem inventoryItem) {
        return new InventoryItemResponse(
                inventoryItem.getId(),
                inventoryItem.getProduct().getId(),
                inventoryItem.getProduct().getProductName(),
                inventoryItem.getWarehouse().getId(),
                inventoryItem.getWarehouse().getName(),
                inventoryItem.getQuantityOnHand(),
                inventoryItem.getQuantityReserved(),
                inventoryItem.getQuantityAvailable()
        );
    }

    public static StockMovementResponse toStockMovementResponse(StockMovement movement) {
        return new StockMovementResponse(
                movement.getId(),
                movement.getProduct().getId(),
                movement.getProduct().getProductName(),
                movement.getWarehouse().getId(),
                movement.getWarehouse().getName(),
                movement.getType(),
                movement.getQuantity(),
                movement.getQuantityOnHandAfter(),
                movement.getQuantityReservedAfter(),
                movement.getReferenceType(),
                movement.getReferenceId(),
                movement.getNotes(),
                toSalesRepResponse(movement.getPerformedBy()),
                movement.getCreatedAt()
        );
    }

    public static DeliveryOrderResponse toDeliveryOrderResponse(DeliveryOrder deliveryOrder) {
        return new DeliveryOrderResponse(
                deliveryOrder.getId(),
                deliveryOrder.getDeliveryOrderNumber(),
                toInvoiceResponse(deliveryOrder.getInvoice()),
                deliveryOrder.getStatus(),
                deliveryOrder.getDeliveryAddress(),
                deliveryOrder.getDeliveryInstructions(),
                deliveryOrder.getTargetDeliveryDate(),
                toDeliveryOrderItemResponses(deliveryOrder.getInvoice()),
                deliveryOrder.getProofOfPickupPaths(),
                deliveryOrder.getPickedUpAt(),
                deliveryOrder.getProofOfDeliveryPaths(),
                deliveryOrder.getDeliveredAt(),
                deliveryOrder.getCreatedAt()
        );
    }

    private static List<DeliveryOrderItemResponse> toDeliveryOrderItemResponses(Invoice invoice) {
        return invoice.getInvoiceItems().stream()
                .map(item -> new DeliveryOrderItemResponse(item.getProduct().getProductName(), item.getQuantity()))
                .toList();
    }
}
