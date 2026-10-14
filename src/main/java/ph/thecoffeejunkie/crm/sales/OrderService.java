package ph.thecoffeejunkie.crm.sales;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.settings.SettingKey;
import ph.thecoffeejunkie.crm.settings.Settings;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static ph.thecoffeejunkie.crm.sales.OrderAction.ACCEPT;
import static ph.thecoffeejunkie.crm.sales.OrderAction.DECLINE;
import static ph.thecoffeejunkie.crm.sales.OrderAction.FULFIL;
import static ph.thecoffeejunkie.crm.sales.OrderAction.ISSUE_INVOICE;
import static ph.thecoffeejunkie.crm.sales.OrderAction.REISSUE_INVOICE;

/** Every staff read/write of an order goes through {@link #findVisible}, so scope cannot be forgotten. */
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

    OrderService(SalesOrderRepository repo, Customers customers, Catalog catalog, Users users, Settings settings,
                 AuditLog audit, Clock clock, QuoteDocument quotes, SalesMailer mailer, ApprovalChecks checks,
                 Approvals approvals) {
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
                        : cb.like(cb.lower(r.get("number")), like(f.q()), '\\'),
                (r, q, cb) -> !f.awaitingConfirmation() ? null : cb.and(
                        cb.equal(r.get("status"), OrderStatus.QUOTED), cb.isNotNull(r.get("customerAcceptedAt"))),
                paymentStatus(f.paymentStatus(), today));
        var found = repo.findAll(spec, page.toPageable(SORTABLE, DEFAULT_SORT));
        Map<Long, String> customerNames = customers.displayNames(
                found.getContent().stream().map(o -> o.customerId).toList());
        Map<Long, String> repNames = users.displayNames(
                found.getContent().stream().map(o -> o.repId).filter(Objects::nonNull).toList());
        return PageResponse.of(found.map(o -> new OrderSummary(o.id, o.number, o.customerId,
                customerNames.get(o.customerId), o.status, paymentStatus(o, today), o.total, o.balance(),
                o.repId == null ? null : repNames.get(o.repId), o.updatedAt)));
    }

    @Transactional(readOnly = true)
    OrderResponse get(Scope s, long id) {
        return toResponse(findVisible(id, s), s);
    }

    @Transactional
    OrderResponse create(Scope s, OrderRequest req) {
        if (req.customerId() == null) {
            throw invalid("customerId", "must not be null");
        }
        CustomerSalesProfile c = customers.salesProfileVisible(s, req.customerId())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Customer not found"));
        if (c.archived()) {
            throw invalid("customerId", "This customer is archived.");
        }
        SalesOrder o = new SalesOrder();
        o.customerId = c.id();
        o.repId = c.assignedRepId();
        o.createdBy = s.userId();
        o.number = "SO-%02d-%05d".formatted(SalesTime.today(clock).getYear() % 100, repo.nextNumber());
        applyHeader(o, req, c);
        applyLines(o, req.lines(), c, Set.of());
        repo.saveAndFlush(o);
        audit.record(AuditAction.CREATED, ENTITY, o.id, "Created order " + o.number, null);
        return toResponse(o, s);
    }

    @Transactional
    OrderResponse update(Scope s, long id, OrderRequest req) {
        SalesOrder o = findVisible(id, s);
        requireVersion(o, req.version());
        OrderStateMachine.require(o.status, OrderAction.EDIT);
        CustomerSalesProfile c = customers.salesProfile(o.customerId);
        AuditChanges changes = AuditChanges.diff();
        String before = linesSummary(o);
        Set<Long> alreadyOn = new HashSet<>(o.lines.stream().map(l -> l.productId).toList());
        headerChanges(changes, o, req, c);
        applyHeader(o, req, c);
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

    /** DRAFT|QUOTED → QUOTED. Mails the PDF to the portal contacts after commit; none → still quoted, nobody mailed. */
    @Transactional
    SendQuoteResponse sendQuote(Scope s, long id, TransitionRequest req) {
        SalesOrder o = findVisibleForUpdate(id, s);
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

    /** DRAFT|QUOTED → CONFIRMED by staff. Every failing approval check is reported in one 422. */
    @Transactional
    OrderResponse confirm(Scope s, long id, TransitionRequest req) {
        SalesOrder o = findVisibleForUpdate(id, s);
        requireVersion(o, req.version());
        OrderStateMachine.require(o.status, OrderAction.CONFIRM);
        requireConfirmable(o);
        approvals.ensureAllApproved(requiredForConfirm(o));
        markConfirmed(o);
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
            audit.recordFor("SYSTEM", null, "System", AuditAction.CONFIRMED, ENTITY, id, "Auto-confirmed after approval");
        }
        return missing;
    }

    List<RequiredApproval> requiredForConfirm(SalesOrder o) {
        return checks.forConfirm(o, customers.salesProfile(o.customerId),
                catalog.info(o.lines.stream().map(l -> l.productId).toList()), SalesTime.today(clock));
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
        o.status = OrderStatus.CONFIRMED;
        o.confirmedAt = Instant.now();
        o.updatedAt = o.confirmedAt;
        repo.saveAndFlush(o);
    }

    @Transactional(readOnly = true)
    PdfFile quotePdf(Scope s, long id) {
        SalesOrder o = findVisible(id, s);
        return new PdfFile(o.number + ".pdf", quotes.render(o));
    }

    @Transactional(readOnly = true)
    PageResponse<AuditEventResponse> history(Scope s, long id, PageQuery page) {
        findVisible(id, s);
        return audit.history(ENTITY, id, page);
    }

    SalesOrder findVisible(long id, Scope s) {
        return repo.findOne(Specification.allOf((r, q, cb) -> cb.equal(r.get("id"), id),
                        OrderScopes.visibleTo(customers.visibleCustomerIds(s))))
                .orElseThrow(OrderService::notFound);
    }

    /** Scope check, then the row lock every state change and money write takes. */
    SalesOrder findVisibleForUpdate(long id, Scope s) {
        if (!repo.exists(Specification.allOf((r, q, cb) -> cb.equal(r.get("id"), id),
                OrderScopes.visibleTo(customers.visibleCustomerIds(s))))) {
            throw notFound();
        }
        return repo.findByIdForUpdate(id).orElseThrow(OrderService::notFound);
    }

    OrderResponse toResponse(SalesOrder o, Scope s) {
        boolean admin = s.isAdmin();
        LocalDate today = SalesTime.today(clock);
        CustomerSalesProfile c = customers.salesProfile(o.customerId);
        Map<Long, ProductInfo> products = catalog.info(o.lines.stream().map(l -> l.productId).toList());
        List<LineResponse> lines = o.lines.stream().map(l -> {
            ProductInfo p = products.get(l.productId);
            return new LineResponse(l.id, l.position, l.productId, p == null ? null : p.sku(), l.description, l.unit,
                    l.qty, l.unitPrice, l.discountPct, l.taxClass, l.lineTotal, admin ? belowCost(l, p) : null);
        }).toList();
        String repName = o.repId == null ? null : users.displayNames(List.of(o.repId)).get(o.repId);
        InvoiceSummary invoice = null;
        BigDecimal withholding = Balances.suggestedWithholding(o.totals(),
                settings.decimal(SettingKey.SALES_WITHHOLDING_SUGGEST_PERCENT), c.type());
        return new OrderResponse(o.id, o.number, o.customerId, c.displayName(), c.type(), o.repId, repName, o.status,
                paymentStatus(o, today), o.revision, o.paymentTerms, o.quoteValidUntil, quoteExpired(o, today),
                o.shippingFee, o.notes, o.terms, lines, TotalsResponse.of(o.totals()), o.settledAmount, o.balance(),
                withholding, invoice, o.quotedAt, o.customerAcceptedAt, o.confirmedAt, o.releasedAt, o.fulfilledAt,
                o.cancelledAt, o.cancelReason, allowedActions(o, admin, invoice != null), OrderFingerprint.of(o),
                o.updatedAt, o.version);
    }

    static Set<OrderAction> allowedActions(SalesOrder o, boolean admin, boolean hasInvoice) {
        Set<OrderAction> a = OrderStateMachine.allowed(o.status);
        a.removeAll(EnumSet.of(ACCEPT, DECLINE));
        a.remove(hasInvoice ? ISSUE_INVOICE : REISSUE_INVOICE);
        if (!admin) {
            a.remove(FULFIL);
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

    private static BigDecimal shipping(OrderRequest req) {
        return OrderTotals.money(req.shippingFee() == null ? BigDecimal.ZERO : req.shippingFee());
    }

    /** Replaces every line, snapshotting description, unit and tax class, and recomputes the cached totals. */
    void applyLines(SalesOrder o, List<LineRequest> reqs, CustomerSalesProfile c, Set<Long> alreadyOn) {
        Map<Long, ProductInfo> products = catalog.info(reqs.stream().map(LineRequest::productId).toList());
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
            OrderLine l = new OrderLine();
            l.order = o;
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

    private static String like(String q) {
        return "%" + q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }
}
