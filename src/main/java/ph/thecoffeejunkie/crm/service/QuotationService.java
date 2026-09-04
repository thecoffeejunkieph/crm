package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import ph.thecoffeejunkie.crm.dto.request.QuotationCreateRequest;
import ph.thecoffeejunkie.crm.dto.request.QuotationItemRequest;
import ph.thecoffeejunkie.crm.dto.response.PageResponse;
import ph.thecoffeejunkie.crm.dto.response.QuotationResponse;
import ph.thecoffeejunkie.crm.entity.CRMUser;
import ph.thecoffeejunkie.crm.entity.Quotation;
import ph.thecoffeejunkie.crm.entity.QuotationItem;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.CRMUserRepository;
import ph.thecoffeejunkie.crm.repository.CustomerRepository;
import ph.thecoffeejunkie.crm.repository.ProductRepository;
import ph.thecoffeejunkie.crm.repository.QuotationItemRepository;
import ph.thecoffeejunkie.crm.repository.QuotationRepository;
import ph.thecoffeejunkie.crm.util.CustomMapper;
import ph.thecoffeejunkie.crm.util.QuotationNumberGenerator;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotationService {

    private final QuotationRepository repository;
    private final QuotationNumberGenerator generator;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final QuotationItemRepository quotationItemRepository;
    private final CRMUserRepository crmUserRepository;
    private final InventoryService inventoryService;

    public QuotationResponse create(QuotationCreateRequest request) {
        log.info("Creating quotation...");

        for (QuotationItemRequest item : request.quotationItems()) {
            inventoryService.assertSufficientStock(item.productId(), item.quantity());
        }

        QuotationResponse response = CustomMapper.toQuotationResponse(repository.save(toQuotation(request)));

        log.info("Created quotation with number: {}", response.quotationNumber());
        return response;
    }

    public QuotationResponse findById(Long id) {
        log.info("Getting quotation with id: {}", id);

        return repository.findById(id)
                .map(CustomMapper::toQuotationResponse)
                .orElseThrow(() -> {
                    log.warn("Quotation not found with id: {}", id);
                    return ResourceNotFoundException.of("Quotation", id);
                });
    }

//    public List<QuotationResponse> findAll(PageRequest pageRequest) {
//        return repository.findAll(pageRequest)
//                .map(CustomMapper::toQuotationResponse)
//                .toList();
//    }

    public PageResponse<QuotationResponse> findAll(PageRequest pageRequest) {
        log.info("Getting all quotations...");

        Page<Quotation> quotationPage = repository.findAll(pageRequest);

        log.info("Found {} quotations", quotationPage.getTotalElements());
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

    public void delete(Long id) {
        log.info("Deleting quotation with id: {}", id);

        Quotation quotation = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Quotation not found with id: {}", id);
                    return ResourceNotFoundException.of("Quotation", id);
                });

        repository.delete(quotation);
        log.info("Deleted quotation with id: {}", id);
    }

    private Quotation toQuotation(QuotationCreateRequest request) {

        Quotation quotation = new Quotation();
        quotation.setQuotationNumber(generator.generate());
        quotation.setQuotationItems(request.quotationItems().stream().map(this::toQuotationItem)
                .map(quotationItemRepository::save)
                .toList());
        quotation.setCustomer(customerRepository.findById(request.customerId())
                .orElseThrow(() -> {
                    log.warn("Customer not found with id: {}", request.customerId());
                    return ResourceNotFoundException.of("Customer", request.customerId());
                }));
        quotation.setStatus(request.status());
        quotation.setTotalAmount(request.totalAmount());
        quotation.setQuoteDate(request.quoteDate());
        quotation.setExpiryDate(request.expiryDate());
        quotation.setShippingCharges(request.shippingCharges());
        quotation.setDiscount(request.discount());
        quotation.setDiscountType(request.discountType());
        quotation.setTermsAndConditions(request.termsAndConditions());
        quotation.setNotes(request.notes());
        quotation.setSalesRep(resolveCurrentSalesRep());
        quotation.setPaymentTerms(request.paymentTerms());

        return quotation;
    }

    private CRMUser resolveCurrentSalesRep() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        return crmUserRepository.findByEmail(authentication.getName()).orElse(null);
    }

    private QuotationItem toQuotationItem(QuotationItemRequest request){

       var quotationItem = new QuotationItem();
       quotationItem.setQuantity(request.quantity());
       quotationItem.setPrice(request.price());
       quotationItem.setDiscount(request.discount());
       quotationItem.setDiscountType(request.discountType());
       quotationItem.setTotal(request.total());
       quotationItem.setProduct(productRepository.findById(request.productId())
               .orElseThrow(() -> {
                   log.warn("Product not found with id: {}", request.productId());
                   return ResourceNotFoundException.of("Product", request.productId());
               }));

       return quotationItem;
    }
}
