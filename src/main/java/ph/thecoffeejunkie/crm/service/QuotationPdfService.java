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
import ph.thecoffeejunkie.crm.dto.response.QuotationItemResponse;
import ph.thecoffeejunkie.crm.dto.response.QuotationResponse;
import ph.thecoffeejunkie.crm.entity.Quotation;
import ph.thecoffeejunkie.crm.exception.PdfGenerationException;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.QuotationRepository;
import ph.thecoffeejunkie.crm.util.CustomMapper;
import ph.thecoffeejunkie.crm.util.FormatUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotationPdfService {

    private static final String LOGO_CLASSPATH = "static/tcj-logo.png";

    private final QuotationRepository repository;

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

    public QuotationPdf generate(Long id) {
        log.info("Generating PDF for quotation with id: {}", id);

        Quotation quotation = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Quotation not found with id: {}", id);
                    return ResourceNotFoundException.of("Quotation", id);
                });

        byte[] pdf = render(quotation);
        String publicPath = write(quotation.getQuotationNumber(), pdf);

        quotation.setPdfPath(publicPath);
        repository.save(quotation);

        log.info("Generated PDF for quotation {} at {}", quotation.getQuotationNumber(), publicPath);
        return new QuotationPdf(quotation.getQuotationNumber() + ".pdf", pdf);
    }

    public record QuotationPdf(String fileName, byte[] content) {}

    private String write(String quotationNumber, byte[] pdf) {
        try {
            Path targetDir = Paths.get(storageRootDir, "quotations");
            Files.createDirectories(targetDir);

            Path targetFile = targetDir.resolve(quotationNumber + ".pdf");
            Files.write(targetFile, pdf);

            return storagePublicPath + "/quotations/" + quotationNumber + ".pdf";
        } catch (IOException e) {
            log.error("Failed to write PDF file for quotation {}", quotationNumber, e);
            throw new PdfGenerationException("Failed to store generated PDF", e);
        }
    }

    private byte[] render(Quotation quotation) {
        QuotationResponse response = CustomMapper.toQuotationResponse(quotation);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(document, out);
            document.open();

            addHeader(document);
            addTitleBlock(document, response);
            addBillTo(document, response);
            addItemsTable(document, response.quotationItems());
            addTotals(document, response);
            addNotesAndTerms(document, response);

            document.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Failed to render PDF for quotation {}", quotation.getQuotationNumber(), e);
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

    private void addTitleBlock(Document document, QuotationResponse response) throws DocumentException {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
        Paragraph title = new Paragraph("QUOTATION", titleFont);
        document.add(title);

        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9);

        PdfPTable meta = new PdfPTable(3);
        meta.setWidthPercentage(100);
        meta.setSpacingBefore(10);

        addMetaCell(meta, "Quotation #", response.quotationNumber(), labelFont, valueFont);
        addMetaCell(meta, "Quote Date", FormatUtils.formatDate(response.quoteDate()), labelFont, valueFont);
        addMetaCell(meta, "Expiry Date", FormatUtils.formatDate(response.expiryDate()), labelFont, valueFont);

        document.add(meta);
        document.add(new Paragraph(" "));
    }

    private void addMetaCell(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        cell.addElement(new Paragraph(label, labelFont));
        cell.addElement(new Paragraph(value, valueFont));
        table.addCell(cell);
    }

    private void addBillTo(Document document, QuotationResponse response) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9);

        document.add(new Paragraph("Bill To", labelFont));

        var customer = response.customer();
        StringBuilder billTo = new StringBuilder();
        billTo.append(customer.firstName()).append(" ").append(customer.lastName());

        if (customer.businessInformation() != null && customer.businessInformation().businessName() != null) {
            billTo.append("\n").append(customer.businessInformation().businessName());
        }
        if (customer.address() != null) {
            billTo.append("\n").append(customer.address());
        }
        billTo.append("\n").append(customer.email());
        if (customer.phoneNumber() != null) {
            billTo.append(" | ").append(customer.phoneNumber());
        }

        document.add(new Paragraph(billTo.toString(), valueFont));
        document.add(new Paragraph(" "));
    }

    private void addItemsTable(Document document, List<QuotationItemResponse> items) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3.5f, 1f, 1.5f, 1f, 1.5f});
        table.setSpacingBefore(5);

        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, java.awt.Color.WHITE);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9);

        for (String label : List.of("Product", "Qty", "Unit Price", "Disc. %", "Total")) {
            PdfPCell cell = new PdfPCell(new Paragraph(label, headerFont));
            cell.setBackgroundColor(new java.awt.Color(60, 40, 30));
            cell.setPadding(6);
            table.addCell(cell);
        }

        for (QuotationItemResponse item : items) {
            table.addCell(cell(item.product().productName(), cellFont));
            table.addCell(cell(String.valueOf(item.quantity()), cellFont));
            table.addCell(cell(FormatUtils.formatCurrency(item.price()), cellFont));
            table.addCell(cell(item.discount() == null ? "0" : item.discount() + "%", cellFont));
            table.addCell(cell(FormatUtils.formatCurrency(item.total()), cellFont));
        }

        document.add(table);
    }

    private PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setPadding(5);
        return cell;
    }

    private void addTotals(Document document, QuotationResponse response) throws DocumentException {
        BigDecimal subtotal = response.quotationItems().stream()
                .map(QuotationItemResponse::total)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(45);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.setSpacingBefore(10);
        totals.setWidths(new float[]{1f, 1f});

        addTotalRow(totals, "Subtotal", FormatUtils.formatCurrency(subtotal), labelFont, labelFont);
        addTotalRow(totals, "Total Amount", FormatUtils.formatCurrency(response.totalAmount()), boldFont, boldFont);

        document.add(totals);
        document.add(new Paragraph(" "));
    }

    private void addTotalRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Paragraph(label, labelFont));
        labelCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Paragraph(value, valueFont));
        valueCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private void addNotesAndTerms(Document document, QuotationResponse response) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9);

        if (response.notes() != null && !response.notes().isBlank()) {
            document.add(new Paragraph("Notes", labelFont));
            document.add(new Paragraph(response.notes(), valueFont));
            document.add(new Paragraph(" "));
        }

        if (response.termsAndConditions() != null && !response.termsAndConditions().isBlank()) {
            document.add(new Paragraph("Terms and Conditions", labelFont));
            document.add(new Paragraph(response.termsAndConditions(), valueFont));
        }
    }

}
