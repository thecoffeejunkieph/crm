package ph.thecoffeejunkie.crm.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import ph.thecoffeejunkie.crm.dto.response.DeliveryOrderItemResponse;
import ph.thecoffeejunkie.crm.dto.response.DeliveryOrderResponse;
import ph.thecoffeejunkie.crm.entity.DeliveryOrder;
import ph.thecoffeejunkie.crm.exception.PdfGenerationException;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.DeliveryOrderRepository;
import ph.thecoffeejunkie.crm.util.CustomMapper;
import ph.thecoffeejunkie.crm.util.FormatUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryOrderPdfService {

    private static final String LOGO_CLASSPATH = "static/tcj-logo.png";

    private final DeliveryOrderRepository repository;

    @Value("${app.storage.root-dir}")
    private String storageRootDir;

    @Value("${app.storage.public-path}")
    private String storagePublicPath;

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
        String publicPath = write(deliveryOrder.getDeliveryOrderNumber(), pdf);

        deliveryOrder.setPdfPath(publicPath);
        repository.save(deliveryOrder);

        log.info("Generated PDF for delivery order {} at {}", deliveryOrder.getDeliveryOrderNumber(), publicPath);
        return new DeliveryOrderPdf(deliveryOrder.getDeliveryOrderNumber() + ".pdf", pdf);
    }

    public record DeliveryOrderPdf(String fileName, byte[] content) {}

    private String write(String deliveryOrderNumber, byte[] pdf) {
        try {
            Path targetDir = Paths.get(storageRootDir, "delivery-orders");
            Files.createDirectories(targetDir);

            Path targetFile = targetDir.resolve(deliveryOrderNumber + ".pdf");
            Files.write(targetFile, pdf);

            return storagePublicPath + "/delivery-orders/" + deliveryOrderNumber + ".pdf";
        } catch (IOException e) {
            log.error("Failed to write PDF file for delivery order {}", deliveryOrderNumber, e);
            throw new PdfGenerationException("Failed to store generated PDF", e);
        }
    }

    private byte[] render(DeliveryOrder deliveryOrder) {
        DeliveryOrderResponse response = CustomMapper.toDeliveryOrderResponse(deliveryOrder);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(document, out);
            document.open();

            addHeader(document);
            addTitleBlock(document, response);
            addDeliverTo(document, response);
            addItemsTable(document, response.invoiceItems());
            addInstructions(document, response);
            addSignatureBlock(document);

            document.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Failed to render PDF for delivery order {}", deliveryOrder.getDeliveryOrderNumber(), e);
            throw new PdfGenerationException("Failed to generate PDF", e);
        }
    }

    private void addHeader(Document document) throws DocumentException, IOException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1f, 1f});

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Image logo = Image.getInstance(new ClassPathResource(LOGO_CLASSPATH).getURL());
        logo.scaleToFit(120, 60);
        logoCell.addElement(logo);
        header.addCell(logoCell);

        Font companyFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font detailFont = FontFactory.getFont(FontFactory.HELVETICA, 9);

        PdfPCell infoCell = new PdfPCell();
        infoCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        infoCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        infoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Paragraph info = new Paragraph();
        info.setAlignment(Element.ALIGN_RIGHT);
        info.add(new Chunk(companyName + "\n", companyFont));
        info.add(new Chunk(companyAddress + "\n", detailFont));
        info.add(new Chunk(companyEmail + " | " + companyPhone, detailFont));
        infoCell.addElement(info);
        header.addCell(infoCell);

        document.add(header);
        document.add(new Paragraph(" "));
    }

    private void addTitleBlock(Document document, DeliveryOrderResponse response) throws DocumentException {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
        Paragraph title = new Paragraph("DELIVERY ORDER", titleFont);
        document.add(title);

        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9);

        PdfPTable meta = new PdfPTable(3);
        meta.setWidthPercentage(100);
        meta.setSpacingBefore(10);

        addMetaCell(meta, "Delivery Order #", response.deliveryOrderNumber(), labelFont, valueFont);
        addMetaCell(meta, "Invoice #", response.invoice() != null ? response.invoice().invoiceNumber() : "-", labelFont, valueFont);
        addMetaCell(meta, "Status", response.status() != null ? response.status().name() : "-", labelFont, valueFont);

        addMetaCell(meta, "Date", FormatUtils.formatDate(response.createdAt() != null ? response.createdAt().toLocalDate() : null), labelFont, valueFont);
        addMetaCell(meta, "Target Delivery", FormatUtils.formatDate(response.targetDeliveryDate()), labelFont, valueFont);
        meta.addCell(emptyCell());

        document.add(meta);
        document.add(new Paragraph(" "));
    }

    private PdfPCell emptyCell() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        return cell;
    }

    private void addMetaCell(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        cell.addElement(new Paragraph(label, labelFont));
        cell.addElement(new Paragraph(value == null ? "-" : value, valueFont));
        table.addCell(cell);
    }

    private void addDeliverTo(Document document, DeliveryOrderResponse response) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9);

        document.add(new Paragraph("Deliver To", labelFont));

        var customer = response.invoice() != null ? response.invoice().customer() : null;
        StringBuilder deliverTo = new StringBuilder();
        if (customer != null) {
            deliverTo.append(customer.firstName()).append(" ").append(customer.lastName());
            if (customer.phoneNumber() != null) {
                deliverTo.append(" | ").append(customer.phoneNumber());
            }
        }
        deliverTo.append("\n").append(response.deliveryAddress() == null ? "-" : response.deliveryAddress());

        document.add(new Paragraph(deliverTo.toString(), valueFont));
        document.add(new Paragraph(" "));
    }

    private void addItemsTable(Document document, List<DeliveryOrderItemResponse> items) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{4f, 1f});
        table.setSpacingBefore(5);

        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, java.awt.Color.WHITE);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9);

        for (String label : List.of("Product", "Quantity")) {
            PdfPCell cell = new PdfPCell(new Paragraph(label, headerFont));
            cell.setBackgroundColor(new java.awt.Color(60, 40, 30));
            cell.setPadding(6);
            table.addCell(cell);
        }

        for (DeliveryOrderItemResponse item : items) {
            table.addCell(cell(item.productName(), cellFont));
            table.addCell(cell(String.valueOf(item.quantity()), cellFont));
        }

        document.add(table);
        document.add(new Paragraph(" "));
    }

    private PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setPadding(5);
        return cell;
    }

    private void addInstructions(Document document, DeliveryOrderResponse response) throws DocumentException {
        if (response.deliveryInstructions() == null || response.deliveryInstructions().isBlank()) {
            return;
        }

        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9);

        document.add(new Paragraph("Delivery Instructions", labelFont));
        document.add(new Paragraph(response.deliveryInstructions(), valueFont));
        document.add(new Paragraph(" "));
    }

    private void addSignatureBlock(Document document) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9);

        document.add(new Paragraph(" "));
        document.add(new Paragraph("Acknowledgment of Receipt", labelFont));
        document.add(new Paragraph(
                "I acknowledge that I have received all the items listed above in good condition and in complete quantity.",
                valueFont));
        document.add(new Paragraph(" "));
        document.add(new Paragraph(" "));

        document.add(new Paragraph("Received By (Print Name): _________________________________", valueFont));
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Signature: _________________________________", valueFont));
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Date: _________________________________", valueFont));
    }
}
