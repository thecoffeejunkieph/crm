package ph.thecoffeejunkie.crm.sales;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.approvals.Approvals;
import ph.thecoffeejunkie.crm.approvals.CreateApprovalRequest;
import ph.thecoffeejunkie.crm.approvals.RequiredApproval;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.fulfilment.DeliverySummary;
import ph.thecoffeejunkie.crm.fulfilment.Fulfilment;
import ph.thecoffeejunkie.crm.notifications.NotificationType;
import ph.thecoffeejunkie.crm.notifications.Notifications;
import ph.thecoffeejunkie.crm.shared.PdfFile;
import ph.thecoffeejunkie.crm.shared.ContactPrincipal;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** The contact's own company's orders, QUOTED or later (spec §5). Everything else is 404. */
@Service
class PortalOrderService {

    private static final Set<String> SORTABLE = Set.of("number", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "updatedAt");

    private final SalesOrderRepository repo;
    private final OrderService orders;
    private final PaymentService payments;
    private final PaymentRepository paymentRepo;
    private final InvoiceRepository invoices;
    private final InvoiceDocument invoiceDocument;
    private final QuoteDocument quoteDocument;
    private final Approvals approvals;
    private final Notifications notifications;
    private final AuditLog audit;
    private final CurrentActor actor;
    private final Clock clock;
    private final Fulfilment fulfilment;

    PortalOrderService(SalesOrderRepository repo, OrderService orders, PaymentService payments,
                       PaymentRepository paymentRepo, InvoiceRepository invoices, InvoiceDocument invoiceDocument,
                       QuoteDocument quoteDocument, Approvals approvals, Notifications notifications, AuditLog audit,
                       CurrentActor actor, Clock clock, Fulfilment fulfilment) {
        this.fulfilment = fulfilment;
        this.repo = repo;
        this.orders = orders;
        this.payments = payments;
        this.paymentRepo = paymentRepo;
        this.invoices = invoices;
        this.invoiceDocument = invoiceDocument;
        this.quoteDocument = quoteDocument;
        this.approvals = approvals;
        this.notifications = notifications;
        this.audit = audit;
        this.actor = actor;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    PageResponse<PortalOrderSummary> list(PageQuery page) {
        ContactPrincipal c = contact();
        LocalDate today = SalesTime.today(clock);
        Specification<SalesOrder> spec = Specification.allOf(
                (r, q, cb) -> cb.equal(r.get("customerId"), c.customerId()),
                (r, q, cb) -> cb.and(cb.notEqual(r.get("status"), OrderStatus.DRAFT),
                        cb.or(cb.notEqual(r.get("status"), OrderStatus.CANCELLED), cb.isNotNull(r.get("quotedAt")))));
        return PageResponse.of(repo.findAll(spec, page.toPageable(SORTABLE, DEFAULT_SORT)).map(o ->
                new PortalOrderSummary(o.id, o.number, o.status, OrderService.paymentStatus(o, today), o.total,
                        o.balance(), o.quoteValidUntil, o.quotedAt)));
    }

    @Transactional(readOnly = true)
    PortalOrderResponse get(long id) {
        return toResponse(visible(contact(), id));
    }

    /**
     * The contact accepts the version they saw (stale → 409). Then confirm as SYSTEM; if approvals are missing the
     * order stays QUOTED with customerAcceptedAt set, and one request per missing approval is filed for the rep.
     */
    @Transactional
    PortalOrderResponse accept(long id, TransitionRequest req) {
        ContactPrincipal c = contact();
        SalesOrder o = visibleForUpdate(c, id);
        OrderService.requireVersion(o, req.version());
        OrderStateMachine.require(o.status, OrderAction.ACCEPT);
        if (OrderService.quoteExpired(o, SalesTime.today(clock))) {
            throw new DomainException(ErrorCode.QUOTE_EXPIRED, "This quote has expired. Ask us for a new one.");
        }
        if (o.customerAcceptedAt != null) {
            throw new DomainException(ErrorCode.INVALID_STATE, "You already accepted this quote.");
        }
        o.customerAcceptedAt = Instant.now();
        o.updatedAt = o.customerAcceptedAt;
        repo.saveAndFlush(o);
        audit.record(AuditAction.QUOTE_ACCEPTED, OrderService.ENTITY, id, "Customer accepted quote " + o.number, null);
        List<RequiredApproval> missing = orders.confirmAsSystem(id);
        long rep = rep(o);
        for (RequiredApproval r : missing) {
            approvals.requestOnBehalf(new CreateApprovalRequest(r.type(), r.subjectType(), r.subjectId(),
                    r.subjectVersion(), r.requestedValue(), "Customer accepted via portal"), rep);
        }
        notifications.toUser(rep, NotificationType.QUOTE_ACCEPTED, OrderService.ENTITY, id,
                "Quote " + o.number + " accepted", missing.isEmpty() ? "The order is confirmed."
                        : "The customer accepted; awaiting approval.");
        return toResponse(o);
    }

    @Transactional
    void decline(long id, DeclineRequest req) {
        ContactPrincipal c = contact();
        SalesOrder o = visibleForUpdate(c, id);
        OrderService.requireVersion(o, req.version());
        OrderStateMachine.require(o.status, OrderAction.DECLINE);
        o.status = OrderStatus.DRAFT;
        o.customerAcceptedAt = null;
        o.updatedAt = Instant.now();
        repo.saveAndFlush(o);
        String reason = req.reason() == null || req.reason().isBlank() ? "no reason given" : req.reason().trim();
        audit.record(AuditAction.QUOTE_DECLINED, OrderService.ENTITY, id,
                "Customer declined quote " + o.number + ": " + reason, null);
        notifications.toUser(rep(o), NotificationType.QUOTE_DECLINED, OrderService.ENTITY, id,
                "Quote " + o.number + " declined", reason);
    }

    @Transactional
    PortalPaymentResponse submitPayment(long id, PaymentRequest req, MultipartFile proof) {
        ContactPrincipal c = contact();
        SalesOrder o = visibleForUpdate(c, id);
        Payment p = payments.submitFromPortal(c.contactId(), o, req, proof);
        notifications.toUser(rep(o), NotificationType.PAYMENT_SUBMITTED, OrderService.ENTITY, id,
                "Payment submitted for " + o.number, "Verify " + p.amount().toPlainString() + " (" + p.method + ")");
        return toResponse(p, c);
    }

    /** Only on a payment this contact submitted. */
    @Transactional
    PortalPaymentResponse attach2307(long paymentId, MultipartFile file) {
        ContactPrincipal c = contact();
        Payment p = paymentRepo.findById(paymentId)
                .filter(x -> PaymentService.CONTACT.equals(x.submittedByType) && x.submittedById == c.contactId())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Payment not found"));
        SalesOrder o = visibleForUpdate(c, p.orderId);
        payments.attach2307(p, o, file);
        return toResponse(p, c);
    }

    @Transactional(readOnly = true)
    List<PortalPaymentResponse> payments(long id) {
        ContactPrincipal c = contact();
        SalesOrder o = visible(c, id);
        return paymentRepo.findByOrderIdOrderByIdDesc(o.id).stream().map(p -> toResponse(p, c)).toList();
    }

    @Transactional(readOnly = true)
    PdfFile quotePdf(long id) {
        SalesOrder o = visible(contact(), id);
        return new PdfFile(o.number + ".pdf", quoteDocument.render(o));
    }

    @Transactional(readOnly = true)
    PdfFile invoicePdf(long invoiceId) {
        ContactPrincipal c = contact();
        Invoice i = invoices.findById(invoiceId).filter(x -> repo.findById(x.orderId)
                        .filter(o -> isVisible(c, o)).isPresent())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Invoice not found"));
        return new PdfFile(i.number + ".pdf", invoiceDocument.render(i));
    }

    private ContactPrincipal contact() {
        return actor.contact().orElseThrow(() -> new DomainException(ErrorCode.UNAUTHORIZED, "Login required"));
    }

    /** QUOTED or later: a draft cancelled before it was ever sent stays internal. */
    private static boolean isVisible(ContactPrincipal c, SalesOrder o) {
        return o.customerId == c.customerId() && o.status != OrderStatus.DRAFT
                && (o.status != OrderStatus.CANCELLED || o.quotedAt != null);
    }

    private SalesOrder visible(ContactPrincipal c, long id) {
        return repo.findById(id).filter(o -> isVisible(c, o)).orElseThrow(OrderService::notFound);
    }

    private SalesOrder visibleForUpdate(ContactPrincipal c, long id) {
        return repo.findByIdForUpdate(id).filter(o -> isVisible(c, o)).orElseThrow(OrderService::notFound);
    }

    private static long rep(SalesOrder o) {
        return o.repId != null ? o.repId : o.createdBy;
    }

    private PortalOrderResponse toResponse(SalesOrder o) {
        LocalDate today = SalesTime.today(clock);
        boolean expired = OrderService.quoteExpired(o, today);
        InvoiceSummary invoice = invoices.findByOrderIdAndStatus(o.id, InvoiceStatus.ISSUED)
                .map(Invoice::toSummary).orElse(null);
        DeliverySummary d = fulfilment.forOrder(o.id).orElse(null);
        return new PortalOrderResponse(o.id, o.number, o.status, OrderService.paymentStatus(o, today),
                o.quoteValidUntil, expired, o.status == OrderStatus.QUOTED && !expired && o.customerAcceptedAt == null,
                o.paymentTerms, o.shippingFee, o.terms,
                o.lines.stream().map(l -> new PortalLine(l.description, l.unit, l.qty, l.unitPrice, l.discountPct,
                        l.lineTotal)).toList(),
                TotalsResponse.of(o.totals()), o.settledAmount, o.balance(), invoice, o.customerAcceptedAt, o.version,
                deliveryStatus(d), d == null ? null : d.deliveredAt());
    }

    /** Where the delivery is; never the proof photo. */
    private static PortalDeliveryStatus deliveryStatus(DeliverySummary d) {
        if (d == null) {
            return null;
        }
        return switch (d.status()) {
            case PICKING -> PortalDeliveryStatus.PREPARING;
            case DISPATCHED -> PortalDeliveryStatus.OUT_FOR_DELIVERY;
            case DELIVERED -> PortalDeliveryStatus.DELIVERED;
            case CANCELLED -> null;
        };
    }

    private static PortalPaymentResponse toResponse(Payment p, ContactPrincipal c) {
        boolean mine = PaymentService.CONTACT.equals(p.submittedByType) && Objects.equals(p.submittedById, c.contactId());
        return new PortalPaymentResponse(p.id, p.status, p.method, p.cashAmount, p.withholdingAmount, p.receivedOn,
                p.reference, p.rejectReason, p.form2307Key != null, mine);
    }
}
