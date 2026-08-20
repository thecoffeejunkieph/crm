package ph.thecoffeejunkie.crm.service;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;
import ph.thecoffeejunkie.crm.entity.Invoice;
import ph.thecoffeejunkie.crm.exception.CrmException;
import ph.thecoffeejunkie.crm.repository.InvoiceRepository;
import ph.thecoffeejunkie.crm.util.InvoicePaymentTokenService;

/**
 * Owns the customer-facing "upload proof of payment" flow reached via the token-secured link in
 * the invoice email - mirrors how {@link QuotationEmailService} serves the accept/reject page
 * for quotations, since there's no separate customer portal in front of this backend.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoicePaymentPortalService {

    private final InvoiceRepository repository;
    private final InvoicePaymentTokenService tokenService;
    private final InvoiceService invoiceService;

    @Value("${app.base-url}")
    private String baseUrl;

    public String buildUploadUrl(Long invoiceId) {
        String token = tokenService.generate(invoiceId);
        return baseUrl + "/api/v1/invoices/" + invoiceId + "/proof-of-payment?token=" + token;
    }

    public PortalResult renderUploadForm(Long id, String token) {
        ResolvedInvoice resolved = resolveInvoice(id, token);
        if (resolved.error() != null) {
            return resolved.error();
        }

        Invoice invoice = resolved.invoice();
        return switch (invoice.getStatus()) {
            case PAID -> message(HttpStatus.OK, "Already Paid",
                    "Invoice " + invoice.getInvoiceNumber() + " has already been paid. Thank you!");
            case FOR_PAYMENT_VERIFICATION -> message(HttpStatus.OK, "Already Received",
                    "We've already received your proof of payment for invoice " + invoice.getInvoiceNumber()
                            + " and it's being verified. We'll be in touch shortly.");
            case UNPAID -> uploadForm(id, token, invoice);
        };
    }

    public PortalResult handleUpload(Long id, String token, MultipartFile file) {
        ResolvedInvoice resolved = resolveInvoice(id, token);
        if (resolved.error() != null) {
            return resolved.error();
        }

        try {
            invoiceService.uploadProofOfPayment(id, file);
        } catch (CrmException e) {
            log.warn("Rejected proof of payment upload for invoice {}: {}", id, e.getMessage());
            return message(e.getStatus(), "Upload Failed", e.getMessage());
        }

        return message(HttpStatus.OK, "Proof of Payment Received",
                "Thank you! We've received your proof of payment and it's now being verified.");
    }

    private ResolvedInvoice resolveInvoice(Long id, String token) {
        Long tokenInvoiceId;
        try {
            tokenInvoiceId = tokenService.resolveInvoiceId(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Rejected invoice payment link with invalid/expired token for id {}: {}", id, e.getMessage());
            return ResolvedInvoice.error(message(HttpStatus.GONE, "Link Expired",
                    "This link is invalid or has expired. Please contact us for a new link."));
        }

        if (!tokenInvoiceId.equals(id)) {
            log.warn("Invoice payment token/path id mismatch: token={}, path={}", tokenInvoiceId, id);
            return ResolvedInvoice.error(message(HttpStatus.BAD_REQUEST, "Invalid Request", "This link is not valid."));
        }

        Invoice invoice = repository.findById(id).orElse(null);
        if (invoice == null) {
            return ResolvedInvoice.error(message(HttpStatus.NOT_FOUND, "Not Found", "This invoice no longer exists."));
        }

        return ResolvedInvoice.of(invoice);
    }

    private PortalResult uploadForm(Long id, String token, Invoice invoice) {
        String html = """
                <html>
                <body style="font-family:Arial,sans-serif;color:#2b2b2b;background:#f5f2ef;padding:48px 24px;text-align:center;">
                  <div style="max-width:480px;margin:0 auto;background:#ffffff;border-radius:8px;padding:32px;border:1px solid #e5e0da;">
                    <h2 style="margin-top:0;">Upload Proof of Payment</h2>
                    <p>Please attach a photo or PDF of your payment receipt for invoice <strong>%s</strong>.</p>
                    <form method="post" action="/api/v1/invoices/%d/proof-of-payment?token=%s" enctype="multipart/form-data">
                      <input type="file" name="file" accept="image/jpeg,image/png,image/webp,application/pdf" required
                             style="display:block;margin:16px auto;"/>
                      <button type="submit" style="background:#3c281e;color:#ffffff;border:none;border-radius:6px;padding:12px 28px;font-weight:600;font-size:14px;cursor:pointer;">
                        Submit
                      </button>
                    </form>
                  </div>
                </body>
                </html>
                """.formatted(HtmlUtils.htmlEscape(invoice.getInvoiceNumber()), id, token);

        return new PortalResult(HttpStatus.OK, html);
    }

    private PortalResult message(HttpStatus status, String heading, String body) {
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

        return new PortalResult(status, html);
    }

    private record ResolvedInvoice(Invoice invoice, PortalResult error) {
        static ResolvedInvoice of(Invoice invoice) {
            return new ResolvedInvoice(invoice, null);
        }

        static ResolvedInvoice error(PortalResult error) {
            return new ResolvedInvoice(null, error);
        }
    }

    public record PortalResult(HttpStatus status, String html) {}
}
