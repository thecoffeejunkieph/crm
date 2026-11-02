package ph.thecoffeejunkie.crm.sales;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.catalog.Catalog;
import ph.thecoffeejunkie.crm.catalog.ProductInfo;
import ph.thecoffeejunkie.crm.customers.Customers;
import ph.thecoffeejunkie.crm.customers.PaymentTerms;
import ph.thecoffeejunkie.crm.customers.TaxTreatment;
import ph.thecoffeejunkie.crm.fulfilment.DeliveryMethod;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ph.thecoffeejunkie.crm.sales.OrderService.invalid;

/**
 * Counter sale (spec PosService.checkout): order, confirm, invoice, payments and dispatch in one transaction, each
 * through its own service. Any exception rolls back all of it, the invoice counter included.
 */
@Service
class PosService {

    private final PosConfig config;
    private final Catalog catalog;
    private final OrderService orders;
    private final InvoiceService invoices;
    private final PaymentService payments;
    private final Customers customers;
    private final SalesMailer mailer;
    private final AuditLog audit;
    private final Clock clock;

    PosService(PosConfig config, Catalog catalog, OrderService orders, InvoiceService invoices,
               PaymentService payments, Customers customers, SalesMailer mailer, AuditLog audit,
               Clock clock) {
        this.config = config;
        this.catalog = catalog;
        this.orders = orders;
        this.invoices = invoices;
        this.payments = payments;
        this.customers = customers;
        this.mailer = mailer;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    PosSaleResponse checkout(Scope s, PosSaleRequest req) {
        long wh = config.warehouseId();
        validate(req);
        checkPrices(req.lines());
        // Unknown or hidden customers fall through to orders.create, which reports them as it always has.
        customers.salesProfileVisible(s, req.customerId()).filter(c -> c.taxTreatment() != TaxTreatment.REGULAR)
                .ifPresent(c -> {
                    throw new DomainException(ErrorCode.POS_TAX_TREATMENT_NOT_SUPPORTED,
                            "This customer is VAT-exempt or zero-rated. Sell as a regular order.");
                });

        OrderResponse created = orders.create(s, new OrderRequest(req.customerId(), PaymentTerms.DUE_ON_RECEIPT,
                BigDecimal.ZERO, null, null, req.lines().stream().map(l -> new LineRequest(null, l.productId(), l.qty(),
                        l.unitPrice(), l.discountPct(), null)).toList(), null, null, wh, DeliveryMethod.PICKUP),
                SalesChannel.POS);
        BigDecimal total = created.totals().total();
        BigDecimal paid = OrderTotals.money(req.payments().stream().map(PosSaleRequest.Tender::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        if (paid.compareTo(total) != 0) {
            throw new DomainException(ErrorCode.POS_PAYMENT_MISMATCH, "The payments don't add up to the total.",
                    Map.of("total", total, "paid", paid));
        }

        long id = created.id();
        OrderResponse confirmed;
        try {
            confirmed = orders.confirm(s, id, new TransitionRequest(created.version(), null));
        } catch (DomainException e) {
            if (e.code() != ErrorCode.APPROVAL_REQUIRED) {
                throw e;
            }
            throw new DomainException(ErrorCode.POS_APPROVAL_REQUIRED,
                    "This sale needs an approval. Sell it as a regular order instead.", e.properties());
        }
        requireInStock(confirmed);

        InvoiceResponse invoice = invoices.issue(s, id, new TransitionRequest(confirmed.version(), null));
        LocalDate today = SalesTime.today(clock);
        for (PosSaleRequest.Tender t : req.payments()) {
            payments.record(s, id, new PaymentRequest(t.method(), t.amount(), BigDecimal.ZERO, today, t.reference()),
                    null);
        }
        orders.fulfilAtCounter(s, id, invoice.number(), invoice.id());
        audit.record(AuditAction.POS_SALE, OrderService.ENTITY, id,
                "POS sale " + invoice.number() + " " + total.toPlainString(), null);

        String email = customers.salesProfile(req.customerId()).email();
        if (req.emailInvoice() && email != null && !email.isBlank()) {
            long invoiceId = invoice.id();
            mailer.invoiceAfterCommit(List.of(email), invoice.number(), () -> invoices.pdfBytes(invoiceId));
        }
        BigDecimal change = req.payments().stream().filter(t -> t.tendered() != null)
                .map(t -> t.tendered().subtract(t.amount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PosSaleResponse(id, created.number(), invoice.id(), invoice.number(), total,
                OrderTotals.money(change));
    }

    /** One line per product; tendered only on CASH and never less than the amount. */
    private static void validate(PosSaleRequest req) {
        if (req.lines().stream().map(PosSaleRequest.Line::productId).distinct().count() != req.lines().size()) {
            throw invalid("lines", "Each product goes on one line.");
        }
        for (int i = 0; i < req.payments().size(); i++) {
            PosSaleRequest.Tender t = req.payments().get(i);
            if (t.tendered() == null) {
                continue;
            }
            if (t.method() != PaymentMethod.CASH) {
                throw invalid("payments[" + i + "].tendered", "Only cash is tendered.");
            }
            if (t.tendered().compareTo(t.amount()) < 0) {
                throw invalid("payments[" + i + "].tendered", "Tendered can't be less than the amount.");
            }
        }
    }

    /**
     * No line past the discount cap, below the cap floor (net rounded, as invoiced) or below cost (net unrounded, as
     * ApprovalChecks' BELOW_COST sees it), so the till refuses exactly what confirm would send for approval.
     */
    private void checkPrices(List<PosSaleRequest.Line> lines) {
        Map<Long, ProductInfo> products = catalog.info(lines.stream().map(PosSaleRequest.Line::productId).toList());
        if (products.size() != new HashSet<>(lines.stream().map(PosSaleRequest.Line::productId).toList()).size()
                || products.values().stream().anyMatch(ProductInfo::archived)) {
            throw invalid("lines", "A product is unknown or archived.");
        }
        BigDecimal cap = config.discountCapPct();
        List<Map<String, Object>> details = new ArrayList<>();
        for (PosSaleRequest.Line l : lines) {
            ProductInfo p = products.get(l.productId());
            BigDecimal min = PosConfig.minUnitPrice(p.price(), p.cost(), cap);
            BigDecimal capFloor = PosConfig.minUnitPrice(p.price(), null, cap);
            BigDecimal discount = l.discountPct() == null ? BigDecimal.ZERO : l.discountPct();
            BigDecimal net = l.unitPrice().multiply(BigDecimal.valueOf(100).subtract(discount))
                    .divide(BigDecimal.valueOf(100));
            if (discount.compareTo(cap) > 0 || OrderTotals.money(net).compareTo(capFloor) < 0
                    || (p.cost() != null && net.compareTo(p.cost()) < 0)) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("productId", p.id());
                d.put("minUnitPrice", min);
                details.add(d);
            }
        }
        if (!details.isEmpty()) {
            throw new DomainException(ErrorCode.POS_PRICE_NOT_ALLOWED, "Some prices are below what the till allows.",
                    Map.of("details", details));
        }
    }

    /** Confirm reserved what it could (the response carries it per line); any short line refuses the whole sale. */
    private static void requireInStock(OrderResponse o) {
        List<Map<String, Object>> details = new ArrayList<>();
        for (LineResponse l : o.lines()) {
            if (l.shortQty() != null && l.shortQty().signum() > 0) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("productId", l.productId());
                d.put("requested", l.qty());
                d.put("available", l.reservedQty());
                details.add(d);
            }
        }
        if (!details.isEmpty()) {
            throw new DomainException(ErrorCode.POS_OUT_OF_STOCK, "Not enough stock for this sale.",
                    Map.of("details", details));
        }
    }
}
