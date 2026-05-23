package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ph.thecoffeejunkie.crm.dto.request.QuotationCreateRequest;
import ph.thecoffeejunkie.crm.dto.request.QuotationItemRequest;
import ph.thecoffeejunkie.crm.dto.response.PageResponse;
import ph.thecoffeejunkie.crm.dto.response.QuotationResponse;
import ph.thecoffeejunkie.crm.entity.Quotation;
import ph.thecoffeejunkie.crm.entity.QuotationItem;
import ph.thecoffeejunkie.crm.repository.CustomerRepository;
import ph.thecoffeejunkie.crm.repository.ProductRepository;
import ph.thecoffeejunkie.crm.repository.QuotationItemRepository;
import ph.thecoffeejunkie.crm.repository.QuotationRepository;
import ph.thecoffeejunkie.crm.util.CustomMapper;
import ph.thecoffeejunkie.crm.util.QuotationNumberGenerator;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QuotationService {

    private final QuotationRepository repository;
    private final QuotationNumberGenerator generator;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final QuotationItemRepository quotationItemRepository;

    public QuotationResponse create(QuotationCreateRequest request) {
        return CustomMapper.toQuotationResponse(repository.save(toQuotation(request)));
    }

//    public List<QuotationResponse> findAll(PageRequest pageRequest) {
//        return repository.findAll(pageRequest)
//                .map(CustomMapper::toQuotationResponse)
//                .toList();
//    }

    public PageResponse<QuotationResponse> findAll(PageRequest pageRequest) {
        Page<Quotation> quotationPage = repository.findAll(pageRequest);

        return new PageResponse<>(
                quotationPage.getPageable().getPageNumber() + 1,
                quotationPage.getPageable().getPageSize(),
                quotationPage.getTotalPages(),
                quotationPage.getTotalElements(),
                quotationPage.getContent().stream()
                        .map(CustomMapper::toQuotationResponse)
                        .toList()
        );
    }

    private Quotation toQuotation(QuotationCreateRequest request) {

        Quotation quotation = new Quotation();
        quotation.setQuotationNumber(generator.generate());
        quotation.setQuotationItems(request.quotationItems().stream().map(this::toQuotationItem)
                .map(quotationItemRepository::save)
                .toList());
        quotation.setCustomer(customerRepository.findById(request.customerId()).orElseThrow());
        quotation.setStatus(request.status());
        quotation.setTotalAmount(request.totalAmount());
        quotation.setQuoteDate(request.quoteDate());
        quotation.setExpiryDate(request.expiryDate());
        quotation.setShippingCharges(request.shippingCharges());
        quotation.setTermsAndConditions(request.termsAndConditions());
        quotation.setNotes(request.notes());

        return quotation;
    }

    private QuotationItem toQuotationItem(QuotationItemRequest request){

       var quotationItem = new QuotationItem();
       quotationItem.setQuantity(request.quantity());
       quotationItem.setPrice(request.price());
       quotationItem.setDiscount(request.discount());
       quotationItem.setTotal(request.total());
       quotationItem.setProduct(productRepository.findById(request.productId()).orElseThrow());

       return quotationItem;
    }
}
