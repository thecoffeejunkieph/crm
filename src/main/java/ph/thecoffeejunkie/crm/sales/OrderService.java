package ph.thecoffeejunkie.crm.sales;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.approvals.ApprovalType;
import ph.thecoffeejunkie.crm.approvals.Approvals;
import ph.thecoffeejunkie.crm.approvals.RequiredApproval;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditChanges;
import ph.thecoffeejunkie.crm.audit.AuditEventResponse;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.catalog.Catalog;
import ph.thecoffeejunkie.crm.catalog.ProductInfo;
import ph.thecoffeejunkie.crm.catalog.TaxClass;
import ph.thecoffeejunkie.crm.customers.CustomerSalesProfile;
import ph.thecoffeejunkie.crm.customers.Customers;
import ph.thecoffeejunkie.crm.customers.TaxTreatment;
import ph.thecoffeejunkie.crm.fulfilment.DeliveryRequest;
import ph.thecoffeejunkie.crm.fulfilment.DeliverySummary;
import ph.thecoffeejunkie.crm.fulfilment.Fulfilment;
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.inventory.Inventory;
import ph.thecoffeejunkie.crm.inventory.LineStock;
import ph.thecoffeejunkie.crm.inventory.TransferResponse;
import ph.thecoffeejunkie.crm.settings.SettingKey;
import ph.thecoffeejunkie.crm.settings.Settings;
import ph.thecoffeejunkie.crm.shared.PdfFile;
import ph.thecoffeejunkie.crm.shared.Like;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.shared.Scope;
import ph.thecoffeejunkie.crm.warehouses.Warehouses;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static ph.thecoffeejunkie.crm.sales.OrderAction.ACCEPT;
import static ph.thecoffeejunkie.crm.sales.OrderAction.COVER_SHORTAGE;
import static ph.thecoffeejunkie.crm.sales.OrderAction.DECLINE;
import static ph.thecoffeejunkie.crm.sales.OrderAction.ISSUE_INVOICE;
import static ph.thecoffeejunkie.crm.sales.OrderAction.REISSUE_INVOICE;
import static ph.thecoffeejunkie.crm.sales.OrderAction.RESEND_QUOTE;

/** Orders: create, edit, transitions. Scoped lookups go through {@link OrderAccess}. */
@Service
class OrderService {

    static final String ENTITY = "ORDER";
    private static final Set<String> SORTABLE = Set.of("number", "updatedAt", "total");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "updatedAt");
    private static final Set<OrderStatus> BILLABLE =
            EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.RELEASED, OrderStatus.FULFILLED);

    private final SalesOrderRepository repo;
    private final Customers customers;
    private final Catalog catalog;
    private final Users users;
    private final Settings settings;
    private final AuditLog audit;
    private final Clock clock;
    private final QuoteDocument quotes;
    private final SalesMailer mailer;
    private final ApprovalChecks checks;
    private final Approvals approvals;
    private final InvoiceRepository invoices;
    private final OrderAccess access;
    private final InvoiceService invoiceService;
    private final PaymentService payments;
    private final OrderRevisionRepository revisions;
    private final Inventory inventory;
    private final Fulfilment fulfilment;
    private final Warehouses warehouses;
    private final ApplicationEventPublisher events;

    OrderService(SalesOrderRepository repo, Customers customers, Catalog catalog, Users users, Settings settings,
                 AuditLog audit, Clock clock, QuoteDocument quotes, SalesMailer mailer, ApprovalChecks checks,
                 Approvals approvals, InvoiceRepository invoices, OrderAccess access, InvoiceService invoiceService,
                 PaymentService payments, OrderRevisionRepository revisions, Inventory inventory,
                 Fulfilment fulfilment, Warehouses warehouses, ApplicationEventPublisher events) {
        this.events = events;
        this.inventory = inventory;
        this.fulfilment = fulfilment;
        this.warehouses = warehouses;
        this.revisions = revisions;
        this.invoices = invoices;
        this.access = access;
        this.invoiceService = invoiceService;
        this.payments = payments;
        this.repo = repo;
        this.quotes = quotes;
        this.mailer = mailer;
        this.checks = checks;
        this.approvals = approvals;
        this.customers = customers;
        this.catalog = catalog;
        this.users = users;
        this.settings = settings;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    PageResponse<OrderSummary> list(Scope s, OrderFilter f, PageQuery page) {
        LocalDate today = SalesTime.today(clock);
        Specification<SalesOrder> spec = Specification.allOf(
                OrderScopes.visibleTo(customers.visibleCustomerIds(s)),
                (r, q, cb) -> f.status() == null || f.status().isEmpty() ? null : r.get("status").in(f.status()),
                (r, q, cb) -> f.customerId() == null ? null : cb.equal(r.get("customerId"), f.customerId()),
                (r, q, cb) -> f.repId() == null ? null : cb.equal(r.get("repId"), f.repId()),
                (r, q, cb) -> f.q() == null || f.q().isBlank() ? null
                        : cb.like(cb.lower(r.get("number")), Like.contains(f.q()), '\\'),
                (r, q, cb) -> !f.awaitingConfirmation() ? null : cb.and(
                        cb.equal(r.get("status"), OrderStatus.QUOTED), cb.isNotNull(r.get("customerAcceptedAt"))),
                (r, q, cb) -> f.channel() == null ? null : cb.equal(r.get("channel"), f.channel()),
                paymentStatus(f.paymentStatus(), today));
        var found = repo.findAll(spec, page.toPageable(SORTABLE, DEFAULT_SORT));
        Map<Long, String> customerNames = customers.displayNames(
                found.getContent().stream().map(o -> o.customerId).toList());
        Map<Long, String> repNames = users.displayNames(
                found.getContent().stream().map(o -> o.repId).filter(Objects::nonNull).toList());
        return PageResponse.of(found.map(o -> new OrderSummary(o.id, o.number, o.customerId,
                customerNames.get(o.customerId), o.status, paymentStatus(o, today), o.total, o.balance(),
                o.repId == null ? null : repNames.get(o.repId), o.updatedAt, o.channel)));
    }

    @Transactional(readOnly = true)
    OrderResponse get(Scope s, long id) {
        return toResponse(access.findVisible(id, s), s);
    }

    @Transactional
    OrderResponse create(Scope s, OrderRequest req) {
        return create(s, req, SalesChannel.B2B);
    }

    @Transactional
    OrderResponse create(Scope s, OrderRequest req, SalesChannel channel) {
        if (req.customerId() == null) {
            throw invalid("customerId", "must not be null");
        }
        CustomerSalesProfile c = customers.salesProfileVisible(s, req.customerId())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Customer not found"));
        if (c.archived()) {
            throw invalid("customerId", "This customer is archived.");
        }
        SalesOrder o = new SalesOrder();
        o.channel = channel;
        o.customerId = c.id();
        o.repId = c.assignedRepId();
        o.createdBy = s.userId();
        o.number = "SO-%02d-%05d".formatted(SalesTime.today(clock).getYear() % 100, repo.nextNumber());
        o.shipFromWarehouseId = warehouses.defaultId();
        applyHeader(o, req, c);
        applyShipping(o, req, AuditChanges.diff());
        applyLines(o, req.lines(), c, Set.of());
        repo.saveAndFlush(o);
        audit.record(AuditAction.CREATED, ENTITY, o.id, "Created order " + o.number, null);
        return toResponse(o, s);
    }

    @Transactional
    OrderResponse update(Scope s, long id, OrderRequest req) {
        fulfilment.lockForOrder(id); // delivery order before sales order, as deliver does; scope is checked next
        SalesOrder o = access.findVisibleForUpdate(id, s);
        requireVersion(o, req.version());
        if (o.status == OrderStatus.CONFIRMED || o.status == OrderStatus.RELEASED) {
            return revise(s, o, req);
        }
        OrderStateMachine.require(o.status, OrderAction.EDIT);
        CustomerSalesProfile c = customers.salesProfile(o.customerId);
        AuditChanges changes = AuditChanges.diff();
        String before = linesSummary(o);
        Set<Long> alreadyOn = new HashSet<>(o.lines.stream().map(l -> l.productId).toList());
        headerChanges(changes, o, req, c);
        applyHeader(o, req, c);
        applyShipping(o, req, changes);
        applyLines(o, req.lines(), c, alreadyOn);
        changes.field("lines", before, linesSummary(o));
        if (o.status == OrderStatus.QUOTED) {
            changes.field("status", o.status, OrderStatus.DRAFT);
            o.status = OrderStatus.DRAFT;
        }
        o.customerAcceptedAt = null;
        o.updatedAt = Instant.now();
        repo.saveAndFlush(o);
        audit.record(AuditAction.UPDATED, ENTITY, id, "Updated order " + o.number, changes);
        return toResponse(o, s);
    }

    /**
     * PUT on a CONFIRMED/RELEASED order (spec §3 Revise): snapshot the current state, apply, re-run the confirm
     * checks plus INVOICE_VOID when invoiced, adjust the reservations, then void and reissue. A RELEASED order's
     * PICKING delivery order is cancelled and the order goes back to CONFIRMED; after dispatch it is INVALID_STATE.
     * Ship-from and delivery method change only while CONFIRMED. One transaction: any refusal saves nothing.
     * Locks: delivery order (update), order row, stock levels (adjust), then the invoice counter (reissue).
     */
    private OrderResponse revise(Scope s, SalesOrder o, OrderRequest req) {
        if (req.reason() == null || req.reason().isBlank()) {
            throw invalid("reason", "Say why the confirmed order is being revised.");
        }
        String reason = req.reason().trim();
        OrderStateMachine.require(o.status, OrderAction.REVISE);
        if (req.lines() == null || req.lines().isEmpty()) {
            throw invalid("lines", "A confirmed order needs at least one line.");
        }
        boolean released = o.status == OrderStatus.RELEASED;
        boolean shipping = shippingChanges(o, req);
        if (released && shipping) {
            throw new DomainException(ErrorCode.INVALID_STATE,
                    "The ship-from warehouse and delivery method can't change after release.");
        }
        if (released) {
            fulfilment.cancelForOrder(o.id);
        }
        CustomerSalesProfile c = customers.salesProfile(o.customerId);
        Map<String, Object> snapshot = snapshot(o);
        String before = linesSummary(o);
        AuditChanges changes = AuditChanges.diff();
        AuditChanges shipChanges = AuditChanges.diff();
        headerChanges(changes, o, req, c);
        applyHeader(o, req, c);
        applyShipping(o, req, shipChanges);
        applyLines(o, req.lines(), c, new HashSet<>(o.lines.stream().map(l -> l.productId).toList()));
        snapshotCosts(o);
        changes.field("lines", before, linesSummary(o));
        if (o.total.compareTo(o.settledAmount) < 0) {
            throw new DomainException(ErrorCode.OVERPAYMENT, "The revised total is less than what was already paid ("
                    + o.settledAmount.toPlainString() + ").");
        }
        List<RequiredApproval> required = new ArrayList<>(requiredForConfirm(o));
        var invoice = invoiceService.active(o);
        invoice.ifPresent(i -> required.add(InvoiceService.voidApproval(i, OrderFingerprint.of(o), reason)));
        approvals.ensureAllApproved(required);

        OrderRevision r = new OrderRevision();
        r.orderId = o.id;
        r.revision = o.revision;
        r.snapshot = snapshot;
        r.reason = reason;
        r.createdBy = s.userId();
        revisions.save(r);
        changes.field("revision", o.revision, o.revision + 1);
        o.revision += 1;
        if (released) {
            changes.field("status", o.status, OrderStatus.CONFIRMED);
            o.status = OrderStatus.CONFIRMED;
            o.releasedAt = null;
        }
        save(o, Instant.now());
        inventory.adjust(OrderStockMapper.of(o));
        if (shipping) {
            audit.record(AuditAction.SHIP_FROM_CHANGED, ENTITY, o.id, "Changed how order " + o.number + " ships",
                    shipChanges);
        }
        if (invoice.isPresent()) {
            invoiceService.voidAndReplace(o, reason);
        }
        audit.record(AuditAction.REVISED, ENTITY, o.id, "Revised order " + o.number + ": " + reason, changes);
        return toResponse(o, s);
    }

    @Transactional(readOnly = true)
    List<RevisionResponse> revisions(Scope s, long id) {
        access.findVisible(id, s);
        List<OrderRevision> rows = revisions.findByOrderIdOrderByRevisionDesc(id);
        Map<Long, String> names = users.displayNames(rows.stream().map(r -> r.createdBy).toList());
        return rows.stream().map(r -> new RevisionResponse(r.revision, r.snapshot, r.reason, names.get(r.createdBy),
                r.createdAt)).toList();
    }

    private Map<String, Object> snapshot(SalesOrder o) {
        Map<Long, ProductInfo> products = catalog.info(o.lines.stream().map(l -> l.productId).toList());
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
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("paymentTerms", o.paymentTerms.name());
        out.put("shippingFee", o.shippingFee);
        out.put("notes", o.notes);
        out.put("terms", o.terms);
        out.put("lines", lines);
        out.put("totals", Map.of("vatableSales", o.vatableSales, "vat", o.vat, "vatExemptSales", o.vatExemptSales,
                "zeroRatedSales", o.zeroRatedSales, "total", o.total));
        return out;
    }

    /** DRAFT|QUOTED → QUOTED. Mails the PDF to the portal contacts after commit; none → still quoted, nobody mailed. */
    @Transactional
    SendQuoteResponse sendQuote(Scope s, long id, TransitionRequest req) {
        SalesOrder o = access.findVisibleForUpdate(id, s);
        requireVersion(o, req.version());
        OrderStateMachine.require(o.status, OrderAction.SEND_QUOTE);
        if (o.lines.isEmpty()) {
            throw invalid("lines", "Add at least one line before sending a quote.");
        }
        OrderStatus before = o.status;
        o.status = OrderStatus.QUOTED;
        o.quotedAt = Instant.now();
        o.quoteValidUntil = SalesTime.today(clock)
                .plusDays(settings.decimal(SettingKey.SALES_QUOTE_VALIDITY_DAYS).longValue());
        o.customerAcceptedAt = null;
        o.updatedAt = o.quotedAt;
        repo.saveAndFlush(o);
        audit.record(AuditAction.QUOTE_SENT, ENTITY, id, "Sent quote " + o.number + ", valid until " + o.quoteValidUntil,
                AuditChanges.diff().field("status", before, o.status));
        List<String> to = customers.portalContactEmails(o.customerId);
        if (!to.isEmpty()) {
            mailer.quoteAfterCommit(to, o.number, o.quoteValidUntil, quotes.render(o));
        }
        return new SendQuoteResponse(toResponse(o, s), to);
    }

    /** Mails a live quote again. Unlike sendQuote it changes nothing: validity and any acceptance stay as they are. */
    @Transactional
    ResendResponse resendQuote(Scope s, long id) {
        SalesOrder o = access.findVisible(id, s);
        OrderStateMachine.require(o.status, OrderAction.RESEND_QUOTE);
        if (o.quoteValidUntil == null || quoteExpired(o, SalesTime.today(clock))) {
            throw new DomainException(ErrorCode.INVALID_STATE, "This quote has expired. Send a new quote instead.");
        }
        List<String> to = customers.portalContactEmails(o.customerId);
        if (!to.isEmpty()) {
            audit.record(AuditAction.QUOTE_SENT, ENTITY, id, "Resent quote " + o.number, null);
            mailer.quoteAfterCommit(to, o.number, o.quoteValidUntil, quotes.render(o));
        }
        return new ResendResponse(to);
    }

    /** DRAFT|QUOTED → CONFIRMED by staff. Every failing approval check is reported in one 422. */
    @Transactional
    OrderResponse confirm(Scope s, long id, TransitionRequest req) {
        SalesOrder o = access.findVisibleForUpdate(id, s);
        requireVersion(o, req.version());
        OrderStateMachine.require(o.status, OrderAction.CONFIRM);
        requireConfirmable(o);
        approvals.ensureAllApproved(requiredForConfirm(o));
        markConfirmed(o);
        inventory.reserve(OrderStockMapper.of(o));
        audit.record(AuditAction.CONFIRMED, ENTITY, id, "Confirmed order " + o.number, null);
        return toResponse(o, s);
    }

    /**
     * Confirm on behalf of the system (portal accept, approval decided). No admin bypass: whoever is the security
     * principal, only APPROVED requests count. Confirms only when nothing is missing; returns what is.
     */
    @Transactional
    List<RequiredApproval> confirmAsSystem(long id) {
        SalesOrder o = repo.findByIdForUpdate(id).orElseThrow(OrderService::notFound);
        OrderStateMachine.require(o.status, OrderAction.CONFIRM);
        requireConfirmable(o);
        List<RequiredApproval> missing = approvals.missing(requiredForConfirm(o));
        if (missing.isEmpty()) {
            markConfirmed(o);
            inventory.reserve(OrderStockMapper.of(o));
            audit.recordFor("SYSTEM", null, "System", AuditAction.CONFIRMED, ENTITY, id, "Auto-confirmed after approval");
        }
        return missing;
    }

    /**
     * CONFIRMED → RELEASED: the payment/credit gate, reservations reconciled, then no short line (ORDER_SHORT lists them), then a PICKING
     * delivery order. FULFILLED follows from its delivery ({@link DeliveredListener}). Creating the delivery order
     * takes the DELIVERY_ORDER counter, and nothing after it locks a stock level.
     */
    @Transactional
    OrderResponse release(Scope s, long id, TransitionRequest req) {
        SalesOrder o = transition(s, id, req, OrderAction.RELEASE);
        CustomerSalesProfile c = customers.salesProfile(o.customerId);
        approvals.ensureAllApproved(checks.forRelease(o, c));
        // Reconciles: an order confirmed before reservations existed is reserved now (a no-op diff otherwise).
        inventory.adjust(OrderStockMapper.of(o));
        requireNotShort(o);
        o.status = OrderStatus.RELEASED;
        o.releasedAt = Instant.now();
        save(o, o.releasedAt);
        fulfilment.create(new DeliveryRequest(o.id, o.number, OrderStockMapper.rep(o), o.shipFromWarehouseId,
                o.deliveryMethod, c.displayName(), c.address(), o.lines.stream().map(l ->
                        new DeliveryRequest.DeliveryLine(l.id, l.productId, l.description, l.unit, l.qty)).toList()));
        audit.record(AuditAction.RELEASED, ENTITY, id, "Released order " + o.number, null);
        return toResponse(o, s);
    }

    /**
     * POS: a fully paid CONFIRMED order is handed over at the counter. CONFIRMED → RELEASED → FULFILLED in one go,
     * stock dispatched against the invoice. Unlike {@link #release} there is no delivery order.
     */
    @Transactional
    void fulfilAtCounter(Scope s, long id, String invoiceNumber, long invoiceId) {
        SalesOrder o = access.findVisibleForUpdate(id, s);
        OrderStateMachine.require(o.status, OrderAction.RELEASE);
        if (o.channel != SalesChannel.POS || o.balance().signum() != 0) {
            throw new DomainException(ErrorCode.INVALID_STATE, "Only a fully paid POS order is handed over at the counter.");
        }
        approvals.ensureAllApproved(checks.forRelease(o, customers.salesProfile(o.customerId)));
        requireNotShort(o);
        Instant now = Instant.now();
        o.status = OrderStatus.RELEASED;
        o.releasedAt = now;
        save(o, now);
        audit.record(AuditAction.RELEASED, ENTITY, id, "Released order " + o.number + " at the counter", null);
        inventory.consume(id, "INVOICE", invoiceNumber, invoiceId);
        o.status = OrderStatus.FULFILLED;
        o.fulfilledAt = now;
        save(o, now);
        audit.record(AuditAction.FULFILLED, ENTITY, id, "Order " + o.number + " handed over at the counter",
                AuditChanges.diff().field("status", OrderStatus.RELEASED, OrderStatus.FULFILLED));
    }

    private void requireNotShort(SalesOrder o) {
        if (inventory.totalShort(o.id).signum() == 0) {
            return;
        }
        Map<Long, LineStock> stock = inventory.stockFor(o.id);
        Map<Long, ProductInfo> products = catalog.info(o.lines.stream().map(l -> l.productId).toList());
        List<Map<String, Object>> details = new ArrayList<>();
        for (OrderLine l : o.lines) {
            LineStock ls = stock.get(l.id);
            if (ls != null && ls.shortQty().signum() > 0) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("orderLineId", l.id);
                d.put("sku", products.get(l.productId).sku());
                d.put("shortQty", ls.shortQty());
                details.add(d);
            }
        }
        throw new DomainException(ErrorCode.ORDER_SHORT,
                "Some lines are short of stock. Cover the shortage or revise the order first.", Map.of("details", details));
    }

    /** CONFIRMED and short: drafts linked transfers from the warehouses that have the stock. */
    @Transactional
    List<TransferResponse> coverShortage(Scope s, long id) {
        SalesOrder o = access.findVisibleForUpdate(id, s);
        OrderStateMachine.require(o.status, COVER_SHORTAGE);
        if (inventory.totalShort(o.id).signum() == 0) {
            throw new DomainException(ErrorCode.INVALID_STATE, "This order isn't short of stock.");
        }
        List<TransferResponse> drafted = inventory.coverShortage(o.id, s.userId());
        if (!drafted.isEmpty()) {
            audit.record(AuditAction.SHORTAGE_COVERED, ENTITY, id, "Drafted "
                    + String.join(", ", drafted.stream().map(TransferResponse::number).toList())
                    + " to cover the shortage on " + o.number, null);
        }
        return drafted;
    }

    /**
     * Refunds happen outside the system, so verified payments block it. A PICKING delivery order is cancelled (after
     * dispatch: INVALID_STATE) and the reservations freed, before an active invoice is voided (not reissued).
     */
    @Transactional
    OrderResponse cancel(Scope s, long id, TransitionRequest req) {
        if (req.reason() == null || req.reason().isBlank()) {
            throw invalid("reason", "Say why the order is cancelled.");
        }
        String reason = req.reason().trim();
        fulfilment.lockForOrder(id); // delivery order before sales order, as deliver does; scope is checked next
        SalesOrder o = transition(s, id, req, OrderAction.CANCEL);
        if (payments.hasVerified(o)) {
            throw new DomainException(ErrorCode.HAS_PAYMENTS,
                    "This order has verified payments. Refund them outside the system first.");
        }
        fulfilment.cancelForOrder(o.id);
        inventory.release(o.id);
        var invoice = invoiceService.active(o);
        if (invoice.isPresent()) {
            approvals.ensureAllApproved(List.of(InvoiceService.voidApproval(invoice.get(), OrderFingerprint.of(o),
                    reason)));
            invoiceService.voidActive(o, reason);
        }
        payments.rejectPendingFor(o, "Order cancelled");
        o.status = OrderStatus.CANCELLED;
        o.cancelledAt = Instant.now();
        o.cancelReason = reason;
        save(o, o.cancelledAt);
        audit.record(AuditAction.CANCELLED, ENTITY, id, "Cancelled order " + o.number + ": " + reason, null);
        return toResponse(o, s);
    }

    private SalesOrder transition(Scope s, long id, TransitionRequest req, OrderAction action) {
        SalesOrder o = access.findVisibleForUpdate(id, s);
        requireVersion(o, req.version());
        OrderStateMachine.require(o.status, action);
        return o;
    }

    private void save(SalesOrder o, Instant at) {
        o.updatedAt = at;
        repo.saveAndFlush(o);
    }

    /** A POS sale is paid in full before it leaves the counter, so credit (overdue, over limit) never blocks it. */
    List<RequiredApproval> requiredForConfirm(SalesOrder o) {
        List<RequiredApproval> required = checks.forConfirm(o, customers.salesProfile(o.customerId),
                catalog.info(o.lines.stream().map(l -> l.productId).toList()), SalesTime.today(clock));
        return o.channel != SalesChannel.POS ? required
                : required.stream().filter(r -> r.type() != ApprovalType.CREDIT_LIMIT).toList();
    }

    private void requireConfirmable(SalesOrder o) {
        if (o.lines.isEmpty()) {
            throw invalid("lines", "Add at least one line before confirming.");
        }
        if (quoteExpired(o, SalesTime.today(clock))) {
            throw new DomainException(ErrorCode.QUOTE_EXPIRED, "This quote expired on " + o.quoteValidUntil
                    + ". Send it again first.");
        }
    }

    private void markConfirmed(SalesOrder o) {
        // Credit is the customer's rep at confirm; never changed afterwards (spec 2026-11-05).
        o.repId = customers.salesProfile(o.customerId).assignedRepId();
        snapshotCosts(o);
        o.status = OrderStatus.CONFIRMED;
        o.confirmedAt = Instant.now();
        o.updatedAt = o.confirmedAt;
        repo.saveAndFlush(o);
        // Every confirm path (staff, portal acceptance, approvals decided) passes here; a revision does not.
        events.publishEvent(new OrderConfirmed(o.id, o.customerId));
    }

    /** Fills unit cost on lines that have none yet: at confirm, and for lines a revision adds. */
    private void snapshotCosts(SalesOrder o) {
        List<Long> missing = o.lines.stream().filter(l -> l.unitCost == null).map(l -> l.productId).toList();
        if (missing.isEmpty()) {
            return;
        }
        Map<Long, ProductInfo> info = catalog.info(missing);
        for (OrderLine l : o.lines) {
            ProductInfo p = info.get(l.productId);
            if (l.unitCost == null && p != null) {
                l.unitCost = p.cost();
            }
        }
    }

    @Transactional(readOnly = true)
    PdfFile quotePdf(Scope s, long id) {
        SalesOrder o = access.findVisible(id, s);
        return new PdfFile(o.number + ".pdf", quotes.render(o));
    }

    @Transactional(readOnly = true)
    PageResponse<AuditEventResponse> history(Scope s, long id, PageQuery page) {
        access.findVisible(id, s);
        return audit.history(ENTITY, id, page);
    }

    OrderResponse toResponse(SalesOrder o, Scope s) {
        boolean admin = s.isAdmin();
        LocalDate today = SalesTime.today(clock);
        CustomerSalesProfile c = customers.salesProfile(o.customerId);
        Map<Long, ProductInfo> products = catalog.info(o.lines.stream().map(l -> l.productId).toList());
        Map<Long, LineStock> stock = inventory.stockFor(o.id);
        List<LineResponse> lines = o.lines.stream().map(l -> {
            ProductInfo p = products.get(l.productId);
            LineStock ls = stock.get(l.id);
            return new LineResponse(l.id, l.position, l.productId, p == null ? null : p.sku(), l.description, l.unit,
                    l.qty, l.unitPrice, l.discountPct, l.taxClass, l.lineTotal, admin ? belowCost(l, p) : null,
                    ls == null ? null : ls.reservedQty(), ls == null ? null : ls.shortQty());
        }).toList();
        BigDecimal totalShort = stock.values().stream().map(LineStock::shortQty)
                .reduce(new BigDecimal("0.000"), BigDecimal::add);
        String repName = o.repId == null ? null : users.displayNames(List.of(o.repId)).get(o.repId);
        InvoiceSummary invoice = invoices.findByOrderIdAndStatus(o.id, InvoiceStatus.ISSUED)
                .map(Invoice::toSummary).orElse(null);
        BigDecimal withholding = Balances.suggestedWithholding(o.totals(),
                settings.decimal(SettingKey.SALES_WITHHOLDING_SUGGEST_PERCENT), c.type(), payments.withheld(o), o.balance());
        return new OrderResponse(o.id, o.number, o.customerId, c.displayName(), c.type(), o.repId, repName, o.status,
                paymentStatus(o, today), o.revision, o.paymentTerms, o.quoteValidUntil, quoteExpired(o, today),
                o.shippingFee, o.notes, o.terms, lines, TotalsResponse.of(o.totals()), o.settledAmount, o.balance(),
                withholding, invoice, o.quotedAt, o.customerAcceptedAt, o.confirmedAt, o.releasedAt, o.fulfilledAt,
                o.cancelledAt, o.cancelReason, allowedActions(o, invoice != null, totalShort.signum() > 0, today),
                OrderFingerprint.of(o), o.updatedAt, o.version, o.shipFromWarehouseId,
                warehouses.name(o.shipFromWarehouseId), o.deliveryMethod, totalShort,
                fulfilment.forOrder(o.id).orElse(null), o.channel);
    }

    static Set<OrderAction> allowedActions(SalesOrder o, boolean hasInvoice, boolean isShort, LocalDate today) {
        Set<OrderAction> a = OrderStateMachine.allowed(o.status);
        a.removeAll(EnumSet.of(ACCEPT, DECLINE));
        a.remove(hasInvoice ? ISSUE_INVOICE : REISSUE_INVOICE);
        if (!isShort) {
            a.remove(COVER_SHORTAGE);
        }
        if (o.quoteValidUntil == null || quoteExpired(o, today)) {
            a.remove(RESEND_QUOTE);
        }
        return a;
    }

    static boolean quoteExpired(SalesOrder o, LocalDate today) {
        return o.status == OrderStatus.QUOTED && o.quoteValidUntil != null && o.quoteValidUntil.isBefore(today);
    }

    static PaymentStatus paymentStatus(SalesOrder o, LocalDate today) {
        return Balances.status(o.total, o.settledAmount, o.dueDate, today);
    }

    static void requireVersion(SalesOrder o, Long sent) {
        if (sent == null) {
            throw invalid("version", "must not be null");
        }
        if (o.version != sent) {
            throw new DomainException(ErrorCode.STALE_VERSION,
                    "This order was changed by someone else. Reload and retry.");
        }
    }

    static String linesSummary(SalesOrder o) {
        int n = o.lines.size();
        return n + (n == 1 ? " line" : " lines") + ", total " + o.total.toPlainString();
    }

    /** Same body as a failed {@code @Valid}: {@code errors:[{field,message}]}. */
    static DomainException invalid(String field, String message) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, message,
                Map.of("errors", List.of(Map.of("field", field, "message", message))));
    }

    static DomainException notFound() {
        return new DomainException(ErrorCode.NOT_FOUND, "Order not found");
    }

    private static void headerChanges(AuditChanges changes, SalesOrder o, OrderRequest req, CustomerSalesProfile c) {
        changes.field("paymentTerms", o.paymentTerms, req.paymentTerms() == null ? c.paymentTerms() : req.paymentTerms())
                .field("shippingFee", o.shippingFee, shipping(req))
                .field("notes", o.notes, req.notes())
                .field("terms", o.terms, req.terms());
    }

    private static void applyHeader(SalesOrder o, OrderRequest req, CustomerSalesProfile c) {
        o.paymentTerms = req.paymentTerms() == null ? c.paymentTerms() : req.paymentTerms();
        o.shippingFee = shipping(req);
        o.notes = req.notes();
        o.terms = req.terms();
    }

    private static boolean shippingChanges(SalesOrder o, OrderRequest req) {
        return req.shipFromWarehouseId() != null && !req.shipFromWarehouseId().equals(o.shipFromWarehouseId)
                || req.deliveryMethod() != null && req.deliveryMethod() != o.deliveryMethod;
    }

    /** Null keeps the current value. A new ship-from must be an active warehouse. */
    private void applyShipping(SalesOrder o, OrderRequest req, AuditChanges changes) {
        Long wh = req.shipFromWarehouseId();
        if (wh != null && !wh.equals(o.shipFromWarehouseId)) {
            if (!warehouses.allActive(Set.of(wh))) {
                throw invalid("shipFromWarehouseId", "Choose an active warehouse.");
            }
            changes.field("shipFromWarehouseId", o.shipFromWarehouseId, wh);
            o.shipFromWarehouseId = wh;
        }
        if (req.deliveryMethod() != null && req.deliveryMethod() != o.deliveryMethod) {
            changes.field("deliveryMethod", o.deliveryMethod, req.deliveryMethod());
            o.deliveryMethod = req.deliveryMethod();
        }
    }

    private static BigDecimal shipping(OrderRequest req) {
        return OrderTotals.money(req.shippingFee() == null ? BigDecimal.ZERO : req.shippingFee());
    }

    /**
     * Sets the order's lines to reqs, in order. A request naming one of this order's line ids updates that line in
     * place; a line not named is deleted; no id (or an id no order has) adds a line. Another order's line id is 400.
     * Snapshots description, unit and tax class, and recomputes the cached totals.
     */
    void applyLines(SalesOrder o, List<LineRequest> reqs, CustomerSalesProfile c, Set<Long> alreadyOn) {
        Map<Long, ProductInfo> products = catalog.info(reqs.stream().map(LineRequest::productId).toList());
        Map<Long, OrderLine> current = new HashMap<>();
        o.lines.forEach(l -> current.put(l.id, l));
        List<OrderLine> lines = new ArrayList<>();
        for (int i = 0; i < reqs.size(); i++) {
            LineRequest r = reqs.get(i);
            ProductInfo p = products.get(r.productId());
            String field = "lines[" + i + "].productId";
            if (p == null) {
                throw invalid(field, "Unknown product.");
            }
            if (p.archived() && !alreadyOn.contains(p.id())) {
                throw invalid(field, "This product is archived.");
            }
            OrderLine l = r.id() == null ? null : current.remove(r.id());
            if (l == null && r.id() != null && repo.lineExists(r.id())) {
                throw invalid("lines[" + i + "].id", "This line isn't on this order, or is listed twice.");
            }
            if (l == null) {
                l = new OrderLine();
                l.order = o;
            }
            l.position = i;
            l.productId = p.id();
            l.description = r.description() == null || r.description().isBlank() ? p.name() : r.description().trim();
            l.unit = p.unit();
            l.taxClass = p.taxClass();
            l.qty = r.qty();
            l.unitPrice = r.unitPrice() != null ? r.unitPrice() : defaultPrice(p, c.taxTreatment());
            l.discountPct = r.discountPct() == null ? BigDecimal.ZERO : r.discountPct();
            lines.add(l);
        }
        OrderTotals.Result t = OrderTotals.compute(lines.stream().map(OrderLine::toTotalsLine).toList(),
                o.shippingFee, c.taxTreatment());
        for (int i = 0; i < lines.size(); i++) {
            lines.get(i).lineTotal = t.lineTotals().get(i);
        }
        o.lines.clear();
        o.lines.addAll(lines);
        o.vatableSales = t.vatableSales();
        o.vat = t.vat();
        o.vatExemptSales = t.vatExemptSales();
        o.zeroRatedSales = t.zeroRatedSales();
        o.total = t.total();
    }

    private static BigDecimal defaultPrice(ProductInfo p, TaxTreatment t) {
        return t != TaxTreatment.REGULAR && p.taxClass() == TaxClass.VATABLE ? OrderTotals.netOfVat(p.price()) : p.price();
    }

    private static Boolean belowCost(OrderLine l, ProductInfo p) {
        return p != null && p.cost() != null && l.netUnitPrice().compareTo(p.cost()) < 0;
    }

    private static Specification<SalesOrder> paymentStatus(PaymentStatus ps, LocalDate today) {
        if (ps == null) {
            return Specification.unrestricted();
        }
        return (r, q, cb) -> {
            Expression<BigDecimal> settled = r.get("settledAmount");
            Expression<BigDecimal> balance = cb.diff(r.<BigDecimal>get("total"), settled);
            Expression<LocalDate> due = r.get("dueDate");
            Predicate open = cb.gt(balance, BigDecimal.ZERO);
            Predicate notOverdue = cb.or(cb.isNull(due), cb.greaterThanOrEqualTo(due, today));
            Predicate p = switch (ps) {
                case PAID -> cb.le(balance, BigDecimal.ZERO);
                case OVERDUE -> cb.and(open, cb.lessThan(due, today));
                case UNPAID -> cb.and(open, notOverdue, cb.equal(settled, BigDecimal.ZERO));
                case PARTIAL -> cb.and(open, notOverdue, cb.gt(settled, BigDecimal.ZERO));
            };
            return cb.and(r.get("status").in(BILLABLE), p);
        };
    }

}
