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
import ph.thecoffeejunkie.crm.dto.response.InvoiceItemResponse;
import ph.thecoffeejunkie.crm.dto.response.InvoiceResponse;
import ph.thecoffeejunkie.crm.entity.Invoice;
import ph.thecoffeejunkie.crm.exception.PdfGenerationException;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.InvoiceRepository;
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
public class InvoicePdfService {

    private static final String TEMPLATE_NAME = "invoice";

    private static final TemplateEngine TEMPLATE_ENGINE = buildTemplateEngine();

    private final InvoiceRepository repository;
    private final LogoAsset logoAsset;

    @Value("${app.company.name}")
    private String companyName;

    @Value("${app.company.address}")
    private String companyAddress;

    @Value("${app.company.email}")
    private String companyEmail;

    @Value("${app.company.phone}")
    private String companyPhone;

    @Value("${app.company.bank.name}")
    private String bankName;

    @Value("${app.company.bank.account-name}")
    private String bankAccountName;

    @Value("${app.company.bank.account-number}")
    private String bankAccountNumber;

    @Value("${app.company.bank.swift-code}")
    private String bankSwiftCode;

    public InvoicePdf generate(Long id) {
        log.info("Generating PDF for invoice with id: {}", id);

        Invoice invoice = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Invoice not found with id: {}", id);
                    return ResourceNotFoundException.of("Invoice", id);
                });

        byte[] pdf = render(invoice);

        log.info("Generated PDF for invoice {}", invoice.getInvoiceNumber());
        return new InvoicePdf(invoice.getInvoiceNumber() + ".pdf", pdf);
    }

    public record InvoicePdf(String fileName, byte[] content) {}

    private byte[] render(Invoice invoice) {
        InvoiceResponse response = CustomMapper.toInvoiceResponse(invoice);
        String html = TEMPLATE_ENGINE.process(TEMPLATE_NAME, buildContext(response));

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to render PDF for invoice {}", invoice.getInvoiceNumber(), e);
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

    private Context buildContext(InvoiceResponse response) {
        Context context = new Context();

        context.setVariable("logoDataUri", logoAsset.dataUri());
        context.setVariable("companyName", companyName);
        context.setVariable("companyAddressLines", companyAddress.split("\n"));
        context.setVariable("companyEmail", companyEmail);
        context.setVariable("companyPhone", companyPhone);

        context.setVariable("invoiceNumber", response.invoiceNumber());
        context.setVariable("quotationNumber", valueOrDash(response.quotationNumber()));
        context.setVariable("salesRepName", formatSalesRep(response));
        context.setVariable("invoiceDate", FormatUtils.formatDate(response.invoiceDate()));
        context.setVariable("dueDate", FormatUtils.formatDate(response.dueDate()));
        context.setVariable("paymentTermsLabel", valueOrDash(response.paymentTermsLabel()));
        context.setVariable("statusLabel", valueOrDash(response.statusLabel()));

        context.setVariable("billToLines", buildBillToLines(response.customer()));
        context.setVariable("items", buildItemRows(response.invoiceItems()));

        BigDecimal subtotal = response.invoiceItems().stream()
                .map(InvoiceItemResponse::total)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        context.setVariable("subtotal", FormatUtils.formatCurrency(subtotal));
        context.setVariable("shippingCharges", response.shippingCharges() == null
                ? null : FormatUtils.formatCurrency(response.shippingCharges()));
        context.setVariable("discount", response.discount() == null || response.discount() == 0
                ? null : FormatUtils.formatDiscount(response.discount(), response.discountType()));
        context.setVariable("totalAmount", FormatUtils.formatCurrency(response.totalAmount()));

        context.setVariable("bankName", bankName);
        context.setVariable("bankAccountName", bankAccountName);
        context.setVariable("bankAccountNumber", bankAccountNumber);
        context.setVariable("bankSwiftCode", bankSwiftCode);

        context.setVariable("notesLines", splitLines(response.notes()));
        context.setVariable("termsLines", splitLines(response.termsAndConditions()));

        return context;
    }

    private String formatSalesRep(InvoiceResponse response) {
        if (response.salesRep() == null) {
            return "-";
        }
        return response.salesRep().firstName() + " " + response.salesRep().lastName();
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

    private List<ItemRow> buildItemRows(List<InvoiceItemResponse> items) {
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

    private String valueOrDash(String value) {
        return value == null ? "-" : value;
    }

    private record ItemRow(String product, String quantity, String unitPrice, String discount, String total) {}

}
