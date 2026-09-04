package ph.thecoffeejunkie.crm.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import ph.thecoffeejunkie.crm.constant.ActivityType;
import ph.thecoffeejunkie.crm.constant.InvoiceStatus;
import ph.thecoffeejunkie.crm.dto.request.CustomerActivityRequest;
import ph.thecoffeejunkie.crm.dto.response.InvoiceItemResponse;
import ph.thecoffeejunkie.crm.dto.response.InvoiceResponse;
import ph.thecoffeejunkie.crm.entity.Invoice;
import ph.thecoffeejunkie.crm.exception.EmailSendException;
import ph.thecoffeejunkie.crm.exception.InvalidRequestException;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.InvoiceRepository;
import ph.thecoffeejunkie.crm.util.CustomMapper;
import ph.thecoffeejunkie.crm.util.EmailStyles;
import ph.thecoffeejunkie.crm.util.FormatUtils;
import ph.thecoffeejunkie.crm.util.LogoAsset;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceEmailService {

    private static final String LOGO_CONTENT_ID = "logo";

    private final InvoiceRepository repository;
    private final InvoicePdfService invoicePdfService;
    private final InvoicePaymentPortalService invoicePaymentPortalService;
    private final JavaMailSender mailSender;
    private final CustomerActivityService customerActivityService;
    private final LogoAsset logoAsset;

    @Value("${app.company.name}")
    private String companyName;

    @Value("${app.company.address}")
    private String companyAddress;

    @Value("${app.company.email}")
    private String companyEmail;

    @Value("${spring.mail.username}")
    private String salesEmail;

    @Value("${app.company.bank.name}")
    private String bankName;

    @Value("${app.company.bank.account-name}")
    private String bankAccountName;

    @Value("${app.company.bank.account-number}")
    private String bankAccountNumber;

    @Value("${app.company.bank.swift-code}")
    private String bankSwiftCode;

    public InvoiceResponse send(Long id) {
        log.info("Sending invoice email for id: {}", id);

        Invoice invoice = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Invoice not found with id: {}", id);
                    return ResourceNotFoundException.of("Invoice", id);
                });

        if (invoice.getCustomer().getEmail() == null || invoice.getCustomer().getEmail().isBlank()) {
            throw new InvalidRequestException("Customer does not have an email address on file");
        }

        InvoicePdfService.InvoicePdf pdf = invoicePdfService.generate(id);

        sendMail(invoice, pdf);
        logSentInvoiceActivity(invoice);

        log.info("Sent invoice email for {} to {}", invoice.getInvoiceNumber(), invoice.getCustomer().getEmail());
        return CustomMapper.toInvoiceResponse(invoice);
    }

    private void logSentInvoiceActivity(Invoice invoice) {
        CustomerActivityRequest request = new CustomerActivityRequest();
        request.setType(ActivityType.SENT_INVOICE);
        request.setNotes("Invoice " + invoice.getInvoiceNumber() + " sent to " + invoice.getCustomer().getEmail());

        customerActivityService.create(invoice.getCustomer().getId(), request);
    }

    private void sendMail(Invoice invoice, InvoicePdfService.InvoicePdf pdf) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            message.setFrom(salesEmail);
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");

            helper.setTo(invoice.getCustomer().getEmail());
            helper.setSubject("Invoice " + invoice.getInvoiceNumber() + " from " + companyName);
            helper.setText(buildHtmlBody(CustomMapper.toInvoiceResponse(invoice)), true);
            helper.addInline(LOGO_CONTENT_ID, new ByteArrayResource(logoAsset.pngBytes()), "image/png");
            helper.addAttachment(pdf.fileName(), new ByteArrayResource(pdf.content()), MediaType.APPLICATION_PDF_VALUE);

            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send invoice email for {}", invoice.getInvoiceNumber(), e);
            throw new EmailSendException("Failed to send invoice email", e);
        }
    }

    private String buildHtmlBody(InvoiceResponse response) {
        StringBuilder items = new StringBuilder();
        for (InvoiceItemResponse item : response.invoiceItems()) {
            items.append("<tr>")
                    .append("<td>")
                    .append(HtmlUtils.htmlEscape(item.product().productName()))
                    .append("</td>")
                    .append("<td style=\"text-align:center;\">")
                    .append(item.quantity())
                    .append("</td>")
                    .append("<td style=\"text-align:right;\">")
                    .append(FormatUtils.formatCurrency(item.price()))
                    .append("</td>")
                    .append("<td style=\"text-align:right;\">")
                    .append(FormatUtils.formatDiscount(item.discount(), item.discountType()))
                    .append("</td>")
                    .append("<td style=\"text-align:right;\">")
                    .append(FormatUtils.formatCurrency(item.total()))
                    .append("</td>")
                    .append("</tr>");
        }

        BigDecimal subtotal = response.invoiceItems().stream()
                .map(InvoiceItemResponse::total)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String customerName = HtmlUtils.htmlEscape(
                response.customer().firstName() + " " + response.customer().lastName());

        String salesRepName = response.salesRep() == null
                ? "-"
                : HtmlUtils.htmlEscape(response.salesRep().firstName() + " " + response.salesRep().lastName());

        String shippingRow = response.shippingCharges() != null
                ? "<p style=\"text-align:right;\">Shipping: " + FormatUtils.formatCurrency(response.shippingCharges()) + "</p>"
                : "";

        String discountRow = response.discount() != null && response.discount() != 0
                ? "<p style=\"text-align:right;\">Discount: " + FormatUtils.formatDiscount(response.discount(), response.discountType()) + "</p>"
                : "";

        String uploadCta = response.status() == InvoiceStatus.UNPAID
                ? """
                  <div style="text-align:center;margin:28px 0;">
                    <a href="%s" class="btn btn-dark">Upload Proof of Payment</a>
                  </div>
                  """.formatted(invoicePaymentPortalService.buildUploadUrl(response.id()))
                : "";

        String notesSection = (response.notes() != null && !response.notes().isBlank())
                ? "<p><strong>Notes</strong><br/>" + HtmlUtils.htmlEscape(response.notes()) + "</p>"
                : "";

        String termsSection = (response.termsAndConditions() != null && !response.termsAndConditions().isBlank())
                ? "<p><strong>Terms and Conditions</strong><br/>" + HtmlUtils.htmlEscape(response.termsAndConditions()) + "</p>"
                : "";

        return """
                <!doctype html>
                <html>
                <head>
                <meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <style>
                  body { margin:0; padding:0; background:#f2f1ee; font-family:'Segoe UI',Arial,sans-serif; }
                  %s
                  .email-wrapper { width:100%%; background:#f2f1ee; padding:32px 16px; }
                  .email-card { max-width:600px; margin:0 auto; background:#ffffff; border-radius:12px; overflow:hidden; box-shadow:0 2px 10px rgba(0,0,0,0.06); border:1px solid #ebe8e3; }
                  .email-header { background:#f4f4f2; padding:28px 24px; text-align:center; border-bottom:1px solid #ebe8e3; }
                  .email-body { padding:28px 24px; color:#2b2b2b; line-height:1.6; }
                  .totals p { margin:4px 0; }
                  .meta p { margin:2px 0; font-size:14px; }
                  .bank-box { background:#faf9f7; border:1px solid #ebe8e3; border-radius:8px; padding:16px; margin:20px 0; font-size:14px; }
                  .footer-brand { font-size:12px; color:#8a8a8a; text-align:center; margin-top:24px; }
                  @media only screen and (max-width:480px) {
                    .email-body { padding:20px 16px; }
                    table.table th, table.table td { padding:8px 4px; font-size:12px; }
                  }
                </style>
                </head>
                <body>
                  <div class="email-wrapper">
                    <div class="email-card">
                      <div class="email-header">
                        <img src="cid:%s" alt="%s" height="42" style="display:inline-block;"/>
                      </div>
                      <div class="email-body">
                        <p>Hi %s,</p>
                        <p>Thank you for your business! Please find attached invoice <strong>%s</strong> (from quotation <strong>%s</strong>). A summary is below.</p>

                        <div class="meta">
                          <p><strong>Invoice Date:</strong> %s</p>
                          <p><strong>Due Date:</strong> %s</p>
                          <p><strong>Payment Terms:</strong> %s</p>
                          <p><strong>Sales Person:</strong> %s</p>
                          <p><strong>Status:</strong> %s</p>
                        </div>

                        <table class="table">
                          <thead class="table-dark">
                            <tr>
                              <th style="text-align:left;">Product</th>
                              <th>Qty</th>
                              <th style="text-align:right;">Unit Price</th>
                              <th style="text-align:right;">Discount</th>
                              <th style="text-align:right;">Total</th>
                            </tr>
                          </thead>
                          <tbody>
                            %s
                          </tbody>
                        </table>

                        <div class="totals">
                          <p style="text-align:right;">Subtotal: %s</p>
                          %s
                          %s
                          <p style="text-align:right;font-size:17px;"><strong>Total Amount Due: %s</strong></p>
                        </div>

                        <div class="bank-box">
                          <strong>Bank Details for Payment</strong><br/>
                          Bank Name: %s<br/>
                          Account Name: %s<br/>
                          Account Number: %s<br/>
                          SWIFT Code: %s
                        </div>

                        %s

                        %s
                        %s

                        <p class="footer-brand">%s | %s | %s</p>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                EmailStyles.BOOTSTRAP_CSS,
                LOGO_CONTENT_ID, HtmlUtils.htmlEscape(companyName),
                customerName, response.invoiceNumber(), response.quotationNumber(),
                FormatUtils.formatDate(response.invoiceDate()), FormatUtils.formatDate(response.dueDate()),
                response.paymentTermsLabel() == null ? "-" : response.paymentTermsLabel(), salesRepName,
                response.statusLabel() == null ? "-" : response.statusLabel(),
                items,
                FormatUtils.formatCurrency(subtotal), shippingRow, discountRow, FormatUtils.formatCurrency(response.totalAmount()),
                bankName, bankAccountName, bankAccountNumber, bankSwiftCode,
                uploadCta,
                notesSection, termsSection,
                HtmlUtils.htmlEscape(companyName), HtmlUtils.htmlEscape(companyAddress), HtmlUtils.htmlEscape(companyEmail));
    }
}
