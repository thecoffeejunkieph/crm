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
import ph.thecoffeejunkie.crm.dto.response.DeliveryOrderItemResponse;
import ph.thecoffeejunkie.crm.dto.response.DeliveryOrderResponse;
import ph.thecoffeejunkie.crm.entity.DeliveryOrder;
import ph.thecoffeejunkie.crm.exception.PdfGenerationException;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.DeliveryOrderRepository;
import ph.thecoffeejunkie.crm.util.CustomMapper;
import ph.thecoffeejunkie.crm.util.FormatUtils;
import ph.thecoffeejunkie.crm.util.LogoAsset;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryOrderPdfService {

    private static final String TEMPLATE_NAME = "delivery-order";

    private static final TemplateEngine TEMPLATE_ENGINE = buildTemplateEngine();

    private final DeliveryOrderRepository repository;
    private final LogoAsset logoAsset;

    @Value("${app.company.name}")
    private String companyName;

    @Value("${app.company.address}")
    private String companyAddress;

    @Value("${app.company.email}")
    private String companyEmail;

    @Value("${app.company.phone}")
    private String companyPhone;

    public DeliveryOrderPdf generate(Long id) {
        log.info("Generating PDF for delivery order with id: {}", id);

        DeliveryOrder deliveryOrder = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Delivery order not found with id: {}", id);
                    return ResourceNotFoundException.of("Delivery order", id);
                });

        byte[] pdf = render(deliveryOrder);

        log.info("Generated PDF for delivery order {}", deliveryOrder.getDeliveryOrderNumber());
        return new DeliveryOrderPdf(deliveryOrder.getDeliveryOrderNumber() + ".pdf", pdf);
    }

    public record DeliveryOrderPdf(String fileName, byte[] content) {}

    private byte[] render(DeliveryOrder deliveryOrder) {
        DeliveryOrderResponse response = CustomMapper.toDeliveryOrderResponse(deliveryOrder);
        String html = TEMPLATE_ENGINE.process(TEMPLATE_NAME, buildContext(response));

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to render PDF for delivery order {}", deliveryOrder.getDeliveryOrderNumber(), e);
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

    private Context buildContext(DeliveryOrderResponse response) {
        Context context = new Context();

        context.setVariable("logoDataUri", logoAsset.dataUri());
        context.setVariable("companyName", companyName);
        context.setVariable("companyAddressLines", companyAddress.split("\n"));
        context.setVariable("companyEmail", companyEmail);
        context.setVariable("companyPhone", companyPhone);

        context.setVariable("deliveryOrderNumber", response.deliveryOrderNumber());
        context.setVariable("invoiceNumber", response.invoice() != null ? response.invoice().invoiceNumber() : "-");
        context.setVariable("status", response.status() != null ? response.status().name() : "-");
        context.setVariable("date", FormatUtils.formatDate(
                response.createdAt() != null ? response.createdAt().toLocalDate() : null));
        context.setVariable("targetDeliveryDate", FormatUtils.formatDate(response.targetDeliveryDate()));

        context.setVariable("deliverToLines", buildDeliverToLines(response));
        context.setVariable("items", buildItemRows(response.invoiceItems()));
        context.setVariable("deliveryInstructions", response.deliveryInstructions() == null
                || response.deliveryInstructions().isBlank() ? null : response.deliveryInstructions());

        return context;
    }

    private List<String> buildDeliverToLines(DeliveryOrderResponse response) {
        CustomerResponse customer = response.invoice() != null ? response.invoice().customer() : null;

        StringBuilder nameLine = new StringBuilder();
        if (customer != null) {
            nameLine.append(customer.firstName()).append(" ").append(customer.lastName());
            if (customer.phoneNumber() != null) {
                nameLine.append(" | ").append(customer.phoneNumber());
            }
        }

        return List.of(
                nameLine.toString(),
                response.deliveryAddress() == null ? "-" : response.deliveryAddress()
        );
    }

    private List<ItemRow> buildItemRows(List<DeliveryOrderItemResponse> items) {
        return items.stream()
                .map(item -> new ItemRow(item.productName(), String.valueOf(item.quantity())))
                .toList();
    }

    private record ItemRow(String product, String quantity) {}

}
