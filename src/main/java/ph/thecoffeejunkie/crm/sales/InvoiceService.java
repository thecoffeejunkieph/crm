package ph.thecoffeejunkie.crm.sales;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.approvals.ApprovalType;
import ph.thecoffeejunkie.crm.approvals.Approvals;
import ph.thecoffeejunkie.crm.approvals.RequiredApproval;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.catalog.Catalog;
import ph.thecoffeejunkie.crm.catalog.ProductInfo;
import ph.thecoffeejunkie.crm.customers.CustomerSalesProfile;
import ph.thecoffeejunkie.crm.customers.Customers;
import ph.thecoffeejunkie.crm.settings.SettingKey;
import ph.thecoffeejunkie.crm.settings.Settings;
import ph.thecoffeejunkie.crm.shared.PdfFile;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DocumentCounters;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** BIR invoices (spec §4): gapless serials from a locked counter row, frozen snapshots, void and reissue. */
@Service
class InvoiceService {

    static final String SUBJECT = "INVOICE";

    private final InvoiceRepository repo;
    private final SalesOrderRepository orderRepo;
    private final OrderAccess access;
    private final Customers customers;
    private final Catalog catalog;
    private final Settings settings;
    private final Approvals approvals;
    private final AuditLog audit;
    private final CurrentActor actor;
    private final DocumentCounters counters;
    private final InvoiceDocument document;
    private final Clock clock;

    InvoiceService(InvoiceRepository repo, SalesOrderRepository orderRepo, OrderAccess access, Customers customers,
                   Catalog catalog, Settings settings, Approvals approvals, AuditLog audit, CurrentActor actor,
                   DocumentCounters counters, InvoiceDocument document, Clock clock) {
        this.repo = repo;
        this.orderRepo = orderRepo;
        this.access = access;
        this.customers = customers;
        this.catalog = catalog;
        this.settings = settings;
        this.approvals = approvals;
        this.audit = audit;
        this.actor = actor;
        this.counters = counters;
        this.document = document;
        this.clock = clock;
    }

    @Transactional
    InvoiceResponse issue(Scope s, long orderId, TransitionRequest req) {
        SalesOrder o = access.findVisibleForUpdate(orderId, s);
        OrderService.requireVersion(o, req.version());
        OrderStateMachine.require(o.status, OrderAction.ISSUE_INVOICE);
        if (active(o).isPresent()) {
            throw new DomainException(ErrorCode.INVALID_STATE, "This order already has an active invoice.");
        }
        return issueFor(o).toResponse();
    }

    @Transactional
    InvoiceResponse reissue(Scope s, long orderId, ReissueRequest req) {
        SalesOrder o = access.findVisibleForUpdate(orderId, s);
        OrderService.requireVersion(o, req.version());
        OrderStateMachine.require(o.status, OrderAction.REISSUE_INVOICE);
        Invoice current = active(o).orElseThrow(() ->
                new DomainException(ErrorCode.INVALID_STATE, "This order has no active invoice to reissue."));
        approvals.ensureAllApproved(List.of(voidApproval(current, OrderFingerprint.of(o), req.reason())));
        return voidAndReplace(o, req.reason()).toResponse();
    }

    @Transactional(readOnly = true)
    List<InvoiceResponse> list(Scope s, long orderId) {
        access.findVisible(orderId, s);
        return repo.findByOrderIdOrderByIdDesc(orderId).stream().map(Invoice::toResponse).toList();
    }

    @Transactional(readOnly = true)
    PdfFile pdf(Scope s, long invoiceId) {
        Invoice i = repo.findById(invoiceId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Invoice not found"));
        try {
            access.findVisible(i.orderId, s);
        } catch (DomainException e) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Invoice not found");
        }
        return new PdfFile(i.number + ".pdf", document.render(i));
    }

    /** Unscoped, for the after-commit mail of an invoice the caller just issued. */
    @Transactional(readOnly = true)
    byte[] pdfBytes(long invoiceId) {
        return document.render(repo.findById(invoiceId).orElseThrow());
    }

    java.util.Optional<Invoice> active(SalesOrder o) {
        return repo.findByOrderIdAndStatus(o.id, InvoiceStatus.ISSUED);
    }

    /** The approval a void needs: subject is the invoice, version the order fingerprint after the change. */
    static RequiredApproval voidApproval(Invoice i, long fingerprint, String reason) {
        return new RequiredApproval(ApprovalType.INVOICE_VOID, SUBJECT, i.id, fingerprint,
                Map.of("invoice", i.number, "reason", reason == null ? "" : reason));
    }

    /** Caller holds the order lock and has checked approval. Clears the order's due date. */
    Invoice voidActive(SalesOrder o, String reason) {
        Invoice i = active(o).orElseThrow(() ->
                new DomainException(ErrorCode.INVALID_STATE, "This order has no active invoice."));
        i.status = InvoiceStatus.VOID;
        i.voidReason = reason;
        i.voidedBy = actor.scope().userId();
        i.voidedAt = Instant.now();
        repo.saveAndFlush(i); // before any replacement insert: one ISSUED per order is a unique index
        o.dueDate = null;
        o.updatedAt = i.voidedAt;
        orderRepo.saveAndFlush(o);
        audit.record(AuditAction.INVOICE_VOIDED, OrderService.ENTITY, o.id,
                "Voided invoice " + i.number + ": " + reason, null);
        return i;
    }

    /** Caller holds the order lock and has checked approval. */
    Invoice voidAndReplace(SalesOrder o, String reason) {
        Invoice old = voidActive(o, reason);
        Invoice replacement = issueFor(o);
        old.replacedById = replacement.id;
        repo.saveAndFlush(old);
        return replacement;
    }

    /**
     * Takes the next serial from the INVOICE counter row: the UPDATE locks it until commit, and a rollback rolls
     * the counter back with the invoice, so numbers are gapless.
     */
    private Invoice issueFor(SalesOrder o) {
        requireCompanySettings();
        long serial = counters.next("INVOICE");
        CustomerSalesProfile c = customers.salesProfile(o.customerId);
        Map<Long, ProductInfo> products = catalog.info(o.lines.stream().map(l -> l.productId).toList());
        LocalDate today = SalesTime.today(clock);

        Invoice i = new Invoice();
        i.number = "SI-%05d".formatted(serial);
        i.orderId = o.id;
        i.orderRevision = o.revision;
        i.issuedAt = Instant.now();
        i.dueDate = today.plusDays(o.paymentTerms.getDays());
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("name", c.businessName() == null || c.businessName().isBlank() ? c.displayName() : c.businessName());
        customer.put("tin", c.tin());
        customer.put("address", c.address());
        customer.put("paymentTerms", o.paymentTerms.getLabel());
        i.customerSnapshot = customer;
        i.sellerSnapshot = InvoiceDocument.seller(settings);
        List<Map<String, Object>> lines = new ArrayList<>();
        for (OrderLine l : o.lines) {
            Map<String, Object> m = new LinkedHashMap<>();
            ProductInfo p = products.get(l.productId);
            m.put("productId", l.productId);
            m.put("sku", p == null ? null : p.sku());
            m.put("description", l.description);
            m.put("unit", l.unit.name());
            m.put("qty", l.qty);
            m.put("unitPrice", l.unitPrice);
            m.put("discountPct", l.discountPct);
            m.put("taxClass", l.taxClass.name());
            m.put("lineTotal", l.lineTotal);
            lines.add(m);
        }
        i.linesSnapshot = lines;
        i.vatableSales = o.vatableSales;
        i.vat = o.vat;
        i.vatExemptSales = o.vatExemptSales;
        i.zeroRatedSales = o.zeroRatedSales;
        i.shippingFee = o.shippingFee;
        i.total = o.total;
        i.issuedBy = actor.scope().userId();
        repo.saveAndFlush(i);

        o.dueDate = i.dueDate;
        o.updatedAt = i.issuedAt;
        orderRepo.saveAndFlush(o);
        audit.record(AuditAction.INVOICE_ISSUED, OrderService.ENTITY, o.id,
                "Issued invoice " + i.number + ", due " + i.dueDate, null);
        return i;
    }

    private void requireCompanySettings() {
        List<Map<String, String>> missing = new ArrayList<>();
        for (SettingKey k : List.of(SettingKey.COMPANY_NAME, SettingKey.COMPANY_TIN, SettingKey.COMPANY_ADDRESS)) {
            if (settings.text(k).isBlank()) {
                missing.add(Map.of("field", k.key(), "message", "Fill in " + k.key() + " in Settings first."));
            }
        }
        if (!missing.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Company details are required on invoices.",
                    Map.of("errors", missing));
        }
    }
}
