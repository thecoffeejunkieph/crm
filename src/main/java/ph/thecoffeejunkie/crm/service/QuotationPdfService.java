package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;
import ph.thecoffeejunkie.crm.dto.response.CustomerResponse;
import ph.thecoffeejunkie.crm.dto.response.QuotationItemResponse;
import ph.thecoffeejunkie.crm.dto.response.QuotationResponse;
import ph.thecoffeejunkie.crm.entity.Quotation;
import ph.thecoffeejunkie.crm.exception.PdfGenerationException;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.QuotationRepository;
import ph.thecoffeejunkie.crm.util.CustomMapper;
import ph.thecoffeejunkie.crm.util.FormatUtils;
import ph.thecoffeejunkie.crm.util.LogoAsset;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotationPdfService {

    private static final String TEMPLATE_NAME = "quotation";

    private static final TemplateEngine TEMPLATE_ENGINE = buildTemplateEngine();

    private final QuotationRepository repository;
    private final LogoAsset logoAsset;

    @Value("${app.company.name}")
    private String companyName;

    @Value("${app.company.address}")
    private String companyAddress;

    @Value("${app.company.email}")
    private String companyEmail;

    @Value("${app.company.phone}")
    private String companyPhone;

    public QuotationPdf generate(Long id) {
        log.info("Generating PDF for quotation with id: {}", id);

        Quotation quotation = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Quotation not found with id: {}", id);
                    return ResourceNotFoundException.of("Quotation", id);
                });

        byte[] pdf = render(quotation);

        log.info("Generated PDF for quotation {}", quotation.getQuotationNumber());
        return new QuotationPdf(quotation.getQuotationNumber() + ".pdf", pdf);
    }

    public record QuotationPdf(String fileName, byte[] content) {}

    private byte[] render(Quotation quotation) {
        QuotationResponse response = CustomMapper.toQuotationResponse(quotation);
        String html = TEMPLATE_ENGINE.process(TEMPLATE_NAME, buildContext(response));

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to render PDF for quotation {}", quotation.getQuotationNumber(), e);
            throw new PdfGenerationException("Failed to generate PDF", e);
        }
    }

    private static TemplateEngine buildTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/pdf/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private Context buildContext(QuotationResponse response) {
        Context context = new Context();

        context.setVariable("logoDataUri", logoAsset.dataUri());
        context.setVariable("companyName", companyName);
        context.setVariable("companyAddressLines", companyAddress.split("\n"));
        context.setVariable("companyEmail", companyEmail);
        context.setVariable("companyPhone", companyPhone);

        context.setVariable("quotationNumber", response.quotationNumber());
        context.setVariable("quoteDate", FormatUtils.formatDate(response.quoteDate()));
        context.setVariable("expiryDate", FormatUtils.formatDate(response.expiryDate()));

        context.setVariable("billToLines", buildBillToLines(response.customer()));
        context.setVariable("items", buildItemRows(response.quotationItems()));

        BigDecimal subtotal = response.quotationItems().stream()
                .map(QuotationItemResponse::total)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        context.setVariable("subtotal", FormatUtils.formatCurrency(subtotal));
        context.setVariable("shippingCharges", response.shippingCharges() == null
                ? null : FormatUtils.formatCurrency(response.shippingCharges()));
        context.setVariable("discount", response.discount() == null || response.discount() == 0
                ? null : FormatUtils.formatDiscount(response.discount(), response.discountType()));
        context.setVariable("totalAmount", FormatUtils.formatCurrency(response.totalAmount()));

        context.setVariable("notesLines", splitLines(response.notes()));
        context.setVariable("termsLines", splitLines(response.termsAndConditions()));

        return context;
    }

    private List<String> buildBillToLines(CustomerResponse customer) {
        List<String> lines = new ArrayList<>();
        lines.add(customer.firstName() + " " + customer.lastName());

        if (customer.businessInformation() != null && customer.businessInformation().businessName() != null) {
            lines.add(customer.businessInformation().businessName());
        }
        if (customer.address() != null) {
            lines.add(customer.address());
        }
        lines.add(customer.phoneNumber() != null
                ? customer.email() + " | " + customer.phoneNumber()
                : customer.email());

        return lines;
    }

    private List<ItemRow> buildItemRows(List<QuotationItemResponse> items) {
        return items.stream()
                .map(item -> new ItemRow(
                        item.product().productName(),
                        String.valueOf(item.quantity()),
                        FormatUtils.formatCurrency(item.price()),
                        FormatUtils.formatDiscount(item.discount(), item.discountType()),
                        FormatUtils.formatCurrency(item.total())
                ))
                .toList();
    }

    private List<String> splitLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return List.of(text.split("\n"));
    }

    private record ItemRow(String product, String quantity, String unitPrice, String discount, String total) {}

}
