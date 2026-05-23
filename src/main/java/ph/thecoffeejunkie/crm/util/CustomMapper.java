package ph.thecoffeejunkie.crm.util;

import ph.thecoffeejunkie.crm.dto.request.QuotationItemRequest;
import ph.thecoffeejunkie.crm.dto.response.CustomerResponse;
import ph.thecoffeejunkie.crm.dto.response.ProductResponse;
import ph.thecoffeejunkie.crm.dto.response.QuotationItemResponse;
import ph.thecoffeejunkie.crm.dto.response.QuotationResponse;
import ph.thecoffeejunkie.crm.entity.Customer;
import ph.thecoffeejunkie.crm.entity.Product;
import ph.thecoffeejunkie.crm.entity.Quotation;
import ph.thecoffeejunkie.crm.entity.QuotationItem;

public final class CustomMapper {

    private CustomMapper() {
        /* This utility class should not be instantiated */
    }

    public static QuotationResponse toQuotationResponse(Quotation quotation) {
        return new QuotationResponse(
                quotation.getQuotationNumber(),
                quotation.getQuotationItems().stream().map(CustomMapper::toQuotationItemResponse).toList(),
                toCustomerResponse(quotation.getCustomer()),
                quotation.getStatus(),
                quotation.getTotalAmount(),
                quotation.getQuoteDate(),
                quotation.getExpiryDate(),
                quotation.getNotes(),
                quotation.getTermsAndConditions()
                );
    }

    public static ProductResponse toProductResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getProductName(),
                product.getDescription(),
                product.getUnit(),
                product.getPrice()
        );
    }

    public static CustomerResponse toCustomerResponse(Customer customer){
        return new CustomerResponse(
                customer.getId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getAddress(),
                customer.getPhoneNumber()
        );
    }

    public static QuotationItemResponse toQuotationItemResponse(QuotationItem quotationItem) {
        return new QuotationItemResponse(
                quotationItem.getQuantity(),
                quotationItem.getPrice(),
                quotationItem.getDiscount(),
                quotationItem.getTotal(),
                toProductResponse(quotationItem.getProduct())
                );
    }
}
