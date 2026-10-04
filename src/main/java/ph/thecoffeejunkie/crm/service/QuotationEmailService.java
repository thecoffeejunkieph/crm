package ph.thecoffeejunkie.crm.service;

import io.jsonwebtoken.JwtException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import ph.thecoffeejunkie.crm.dto.response.QuotationItemResponse;
import ph.thecoffeejunkie.crm.dto.response.QuotationResponse;
import ph.thecoffeejunkie.crm.entity.Quotation;
import ph.thecoffeejunkie.crm.exception.EmailSendException;
import ph.thecoffeejunkie.crm.exception.InvalidRequestException;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.QuotationRepository;
import ph.thecoffeejunkie.crm.util.CustomMapper;
import ph.thecoffeejunkie.crm.util.EmailStyles;
import ph.thecoffeejunkie.crm.util.FormatUtils;
import ph.thecoffeejunkie.crm.util.LogoAsset;
import ph.thecoffeejunkie.crm.util.QuotationResponseTokenService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotationEmailService {

    private static final String LOGO_CONTENT_ID = "logo";
    private static final List<String> RESOLVED_STATUSES = List.of("ACCEPTED", "REJECTED");

    private final QuotationRepository repository;
    private final QuotationPdfService quotationPdfService;
    private final QuotationResponseTokenService tokenService;
    private final QuotationAcceptanceService quotationAcceptanceService;
    private final JavaMailSender mailSender;
    private final LogoAsset logoAsset;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.company.name}")
    private String companyName;

    @Value("${app.company.address}")
    private String companyAddress;

    @Value("${app.company.email}")
    private String companyEmail;

    @Value("${spring.mail.username}")
    private String salesEmail;

    public QuotationResponse send(Long id) {
        log.info("Sending quotation email for id: {}", id);

        Quotation quotation = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Quotation not found with id: {}", id);
                    return ResourceNotFoundException.of("Quotation", id);
                });

        if (quotation.getCustomer().getEmail() == null || quotation.getCustomer().getEmail().isBlank()) {
            throw new InvalidRequestException("Customer does not have an email address on file");
        }

        QuotationPdfService.QuotationPdf pdf = quotationPdfService.generate(id);
        String token = tokenService.generate(id);

        sendMail(quotation, pdf, token);

        quotation.setStatus("SENT");
        QuotationResponse response = CustomMapper.toQuotationResponse(repository.save(quotation));

        log.info("Sent quotation email for {} to {}", quotation.getQuotationNumber(), quotation.getCustomer().getEmail());
        return response;
    }

    private void sendMail(Quotation quotation, QuotationPdfService.QuotationPdf pdf, String token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            message.setFrom(salesEmail);
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");

            String acceptUrl = buildResponseUrl(quotation.getId(), token, "accept");
            String rejectUrl = buildResponseUrl(quotation.getId(), token, "reject");

            helper.setTo(quotation.getCustomer().getEmail());
            helper.setSubject("Quotation " + quotation.getQuotationNumber() + " from " + companyName);
            helper.setText(buildHtmlBody(CustomMapper.toQuotationResponse(quotation), acceptUrl, rejectUrl), true);
            helper.addInline(LOGO_CONTENT_ID, new ByteArrayResource(logoAsset.pngBytes()), "image/png");
            helper.addAttachment(pdf.fileName(), new ByteArrayResource(pdf.content()), MediaType.APPLICATION_PDF_VALUE);

            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send quotation email for {}", quotation.getQuotationNumber(), e);
            throw new EmailSendException("Failed to send quotation email", e);
        }
    }

    public RespondResult respond(Long id, String token, String decision) {
        String normalizedDecision = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);

        if (!normalizedDecision.equals("ACCEPT") && !normalizedDecision.equals("REJECT")) {
            return message(HttpStatus.BAD_REQUEST, "Invalid Request", "This link is not valid.");
        }

        Long tokenQuotationId;
        try {
            tokenQuotationId = tokenService.resolveQuotationId(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Rejected quotation response with invalid/expired token for id {}: {}", id, e.getMessage());
            return message(HttpStatus.GONE, "Link Expired",
                    "This link is invalid or has expired. Please contact us for a new quotation link.");
        }

        if (!tokenQuotationId.equals(id)) {
            log.warn("Quotation response token/path id mismatch: token={}, path={}", tokenQuotationId, id);
            return message(HttpStatus.BAD_REQUEST, "Invalid Request", "This link is not valid.");
        }

        Quotation quotation = repository.findById(id).orElse(null);
        if (quotation == null) {
            return message(HttpStatus.NOT_FOUND, "Not Found", "This quotation no longer exists.");
        }

        if (RESOLVED_STATUSES.contains(quotation.getStatus())) {
            return message(HttpStatus.OK, "Already Responded",
                    "This quotation has already been " + quotation.getStatus().toLowerCase(Locale.ROOT) + ".");
        }

        if (quotation.getExpiryDate() != null && quotation.getExpiryDate().isBefore(LocalDate.now())) {
            return message(HttpStatus.GONE, "Quotation Expired",
                    "This quotation has expired. Please contact us for a new one.");
        }

        if (normalizedDecision.equals("ACCEPT")) {
            quotationAcceptanceService.accept(quotation);
            log.info("Quotation {} marked as ACCEPTED via customer response link", quotation.getQuotationNumber());
            return message(HttpStatus.OK, "Quotation Accepted",
                    "Thank you! Your quotation " + quotation.getQuotationNumber()
                            + " has been accepted. An invoice has been emailed to you.");
        }

        quotation.setStatus("REJECTED");
        repository.save(quotation);
        log.info("Quotation {} marked as REJECTED via customer response link", quotation.getQuotationNumber());
        return message(HttpStatus.OK, "Quotation Declined",
                "You have declined quotation " + quotation.getQuotationNumber() + ". Thank you for letting us know.");
    }

    private String buildResponseUrl(Long quotationId, String token, String decision) {
        return baseUrl + "/api/v1/quotations/" + quotationId + "/respond?token=" + token + "&decision=" + decision;
    }

    private String buildHtmlBody(QuotationResponse response, String acceptUrl, String rejectUrl) {
        StringBuilder items = new StringBuilder();
        for (QuotationItemResponse item : response.quotationItems()) {
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

        BigDecimal subtotal = response.quotationItems().stream()
                .map(QuotationItemResponse::total)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String customerName = HtmlUtils.htmlEscape(
                response.customer().firstName() + " " + response.customer().lastName());

        String notesSection = (response.notes() != null && !response.notes().isBlank())
                ? "<p><strong>Notes</strong><br/>" + HtmlUtils.htmlEscape(response.notes()) + "</p>"
                : "";

        String termsSection = (response.termsAndConditions() != null && !response.termsAndConditions().isBlank())
                ? "<p><strong>Terms and Conditions</strong><br/>" + HtmlUtils.htmlEscape(response.termsAndConditions()) + "</p>"
                : "";

        String shippingRow = response.shippingCharges() != null
                ? "<p style=\"text-align:right;\">Shipping: " + FormatUtils.formatCurrency(response.shippingCharges()) + "</p>"
                : "";

        String discountRow = response.discount() != null && response.discount() != 0
                ? "<p style=\"text-align:right;\">Discount: " + FormatUtils.formatDiscount(response.discount(), response.discountType()) + "</p>"
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
                  .cta { text-align:center; margin:32px 0; }
                  .footer-brand { font-size:12px; color:#8a8a8a; text-align:center; margin-top:24px; }
                  @media only screen and (max-width:480px) {
                    .email-body { padding:20px 16px; }
                    .btn { display:block; width:100%%; box-sizing:border-box; margin:8px 0; }
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
                        <p>Please find attached your quotation <strong>%s</strong>, valid until <strong>%s</strong>. A summary is below.</p>

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
                          <p style="text-align:right;font-size:17px;"><strong>Total Amount: %s</strong></p>
                        </div>

                        %s
                        %s

                        <div class="cta">
                          <a href="%s" class="btn btn-primary">Accept Quotation</a>
                          <a href="%s" class="btn btn-danger">Reject Quotation</a>
                        </div>

                        <p style="font-size:12px;color:#8a8a8a;">If the buttons don't work, copy and paste these links into your browser:<br/>
                        Accept: %s<br/>Reject: %s</p>

                        <p class="footer-brand">%s | %s | %s</p>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                EmailStyles.BOOTSTRAP_CSS,
                LOGO_CONTENT_ID, HtmlUtils.htmlEscape(companyName),
                customerName, response.quotationNumber(), FormatUtils.formatDate(response.expiryDate()),
                items,
                FormatUtils.formatCurrency(subtotal), shippingRow, discountRow, FormatUtils.formatCurrency(response.totalAmount()),
                notesSection, termsSection,
                acceptUrl, rejectUrl,
                acceptUrl, rejectUrl,
                HtmlUtils.htmlEscape(companyName), HtmlUtils.htmlEscape(companyAddress), HtmlUtils.htmlEscape(companyEmail));
    }

    private RespondResult message(HttpStatus status, String heading, String body) {
        String html = """
                <html>
                <body style="font-family:Arial,sans-serif;color:#2b2b2b;background:#f5f2ef;padding:48px 24px;text-align:center;">
                  <div style="max-width:480px;margin:0 auto;background:#ffffff;border-radius:8px;padding:32px;border:1px solid #e5e0da;">
                    <h2 style="margin-top:0;">%s</h2>
                    <p>%s</p>
                  </div>
                </body>
                </html>
                """.formatted(HtmlUtils.htmlEscape(heading), HtmlUtils.htmlEscape(body));

        return new RespondResult(status, html);
    }

    public record RespondResult(HttpStatus status, String html) {}
}
