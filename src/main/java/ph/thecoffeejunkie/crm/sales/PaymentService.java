package ph.thecoffeejunkie.crm.sales;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.customers.Customers;
import ph.thecoffeejunkie.crm.files.StorageService;
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Spec §4 Payments. Every write takes the order row lock first, so settledAmount and the overpayment check can't
 * race: two payments that together overpay are serialised and the second is refused.
 */
@Service
class PaymentService {

    static final String USER = "USER";
    static final String CONTACT = "CONTACT";

    private final PaymentRepository repo;
    private final SalesOrderRepository orderRepo;
    private final OrderAccess access;
    private final StorageService storage;
    private final AuditLog audit;
    private final Users users;
    private final Customers customers;
    private final Clock clock;

    PaymentService(PaymentRepository repo, SalesOrderRepository orderRepo, OrderAccess access, StorageService storage,
                   AuditLog audit, Users users, Customers customers, Clock clock) {
        this.repo = repo;
        this.orderRepo = orderRepo;
        this.access = access;
        this.storage = storage;
        this.audit = audit;
        this.users = users;
        this.customers = customers;
        this.clock = clock;
    }

    /** Recorded by staff: VERIFIED at once. */
    @Transactional
    PaymentResponse record(Scope s, long orderId, PaymentRequest req, MultipartFile proof) {
        SalesOrder o = access.findVisibleForUpdate(orderId, s);
        OrderStateMachine.require(o.status, OrderAction.RECORD_PAYMENT);
        Payment p = newPayment(o, req);
        settle(o, p.amount());
        p.proofKey = proof == null || proof.isEmpty() ? null
                : storage.store("payments/proof", proof, StorageService.PROOF_TYPES);
        p.status = PaymentVerification.VERIFIED;
        p.submittedByType = USER;
        p.submittedById = s.userId();
        p.verifiedBy = s.userId();
        p.verifiedAt = Instant.now();
        repo.saveAndFlush(p);
        audit.record(AuditAction.PAYMENT_RECORDED, OrderService.ENTITY, o.id,
                "Recorded payment of " + p.amount().toPlainString() + " (" + p.method + ")", null);
        return toResponse(p);
    }

    /** Submitted by a portal contact: PENDING until staff verify it; proof required; settles nothing yet. */
    @Transactional
    Payment submitFromPortal(long contactId, SalesOrder o, PaymentRequest req, MultipartFile proof) {
        OrderStateMachine.require(o.status, OrderAction.RECORD_PAYMENT);
        if (proof == null || proof.isEmpty()) {
            throw OrderService.invalid("proof", "Attach proof of payment.");
        }
        Payment p = newPayment(o, req);
        p.proofKey = storage.store("payments/proof", proof, StorageService.CUSTOMER_PROOF_TYPES);
        p.status = PaymentVerification.PENDING;
        p.submittedByType = CONTACT;
        p.submittedById = contactId;
        repo.saveAndFlush(p);
        audit.record(AuditAction.PAYMENT_SUBMITTED, OrderService.ENTITY, o.id,
                "Customer submitted payment of " + p.amount().toPlainString() + " for verification", null);
        return p;
    }

    @Transactional
    PaymentResponse verify(Scope s, long paymentId, long version) {
        Payment p = find(paymentId);
        SalesOrder o = access.findVisibleForUpdate(p.orderId, s);
        requirePending(p, version);
        OrderStateMachine.require(o.status, OrderAction.RECORD_PAYMENT);
        settle(o, p.amount());
        p.status = PaymentVerification.VERIFIED;
        p.verifiedBy = s.userId();
        p.verifiedAt = Instant.now();
        p.updatedAt = p.verifiedAt;
        repo.saveAndFlush(p);
        audit.record(AuditAction.PAYMENT_VERIFIED, OrderService.ENTITY, o.id,
                "Verified payment of " + p.amount().toPlainString(), null);
        return toResponse(p);
    }

    @Transactional
    PaymentResponse reject(Scope s, long paymentId, RejectRequest req) {
        Payment p = find(paymentId);
        SalesOrder o = access.findVisibleForUpdate(p.orderId, s);
        requirePending(p, req.version());
        markRejected(p, req.reason().trim());
        audit.record(AuditAction.PAYMENT_REJECTED, OrderService.ENTITY, o.id,
                "Rejected payment of " + p.amount().toPlainString() + ": " + p.rejectReason, null);
        return toResponse(p);
    }

    @Transactional
    PaymentResponse attachProof(Scope s, long paymentId, MultipartFile file) {
        Payment p = find(paymentId);
        SalesOrder o = access.findVisibleForUpdate(p.orderId, s);
        if (p.status == PaymentVerification.REJECTED) {
            throw new DomainException(ErrorCode.INVALID_STATE, "Can't attach proof to a rejected payment.");
        }
        if (file == null || file.isEmpty()) {
            throw OrderService.invalid("file", "Choose a file.");
        }
        String old = p.proofKey;
        p.proofKey = storage.store("payments/proof", file, StorageService.PROOF_TYPES);
        p.updatedAt = Instant.now();
        repo.saveAndFlush(p);
        storage.deleteQuietly(old);
        audit.record(AuditAction.PAYMENT_PROOF_ATTACHED, OrderService.ENTITY, o.id,
                "Attached proof to payment of " + p.amount().toPlainString(), null);
        return toResponse(p);
    }

    @Transactional
    PaymentResponse attach2307(Scope s, long paymentId, MultipartFile file) {
        Payment p = find(paymentId);
        SalesOrder o = access.findVisibleForUpdate(p.orderId, s);
        attach2307(p, o, file);
        return toResponse(p);
    }

    /** Any status but REJECTED; replaces an earlier file. Caller holds the order lock. */
    void attach2307(Payment p, SalesOrder o, MultipartFile file) {
        if (p.status == PaymentVerification.REJECTED) {
            throw new DomainException(ErrorCode.INVALID_STATE, "Can't attach a 2307 to a rejected payment.");
        }
        if (file == null || file.isEmpty()) {
            throw OrderService.invalid("file", "Choose a file.");
        }
        String old = p.form2307Key;
        p.form2307Key = storage.store("payments/2307", file, StorageService.PROOF_TYPES);
        p.updatedAt = Instant.now();
        repo.saveAndFlush(p);
        storage.deleteQuietly(old);
        audit.record(AuditAction.FORM_2307_ATTACHED, OrderService.ENTITY, o.id,
                "Attached BIR Form 2307 to payment of " + p.amount().toPlainString(), null);
    }

    @Transactional(readOnly = true)
    List<PaymentResponse> list(Scope s, long orderId) {
        access.findVisible(orderId, s);
        return repo.findByOrderIdOrderByIdDesc(orderId).stream().map(this::toResponse).toList();
    }

    /** On cancel: pending submissions can no longer be verified. Caller holds the order lock. */
    void rejectPendingFor(SalesOrder o, String reason) {
        for (Payment p : repo.findByOrderIdAndStatus(o.id, PaymentVerification.PENDING)) {
            markRejected(p, reason);
            audit.record(AuditAction.PAYMENT_REJECTED, OrderService.ENTITY, o.id,
                    "Rejected payment of " + p.amount().toPlainString() + ": " + reason, null);
        }
    }

    /** Withholding on payments not rejected; a pending one already claims its share of the suggestion. */
    BigDecimal withheld(SalesOrder o) {
        return repo.findByOrderIdOrderByIdDesc(o.id).stream()
                .filter(p -> p.status != PaymentVerification.REJECTED)
                .map(p -> p.withholdingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    boolean hasVerified(SalesOrder o) {
        return repo.existsByOrderIdAndStatus(o.id, PaymentVerification.VERIFIED);
    }

    Payment find(long paymentId) {
        return repo.findById(paymentId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Payment not found"));
    }

    private Payment newPayment(SalesOrder o, PaymentRequest req) {
        Payment p = new Payment();
        p.orderId = o.id;
        p.method = req.method();
        p.cashAmount = OrderTotals.money(req.cashAmount());
        p.withholdingAmount = OrderTotals.money(req.withholdingAmount() == null ? BigDecimal.ZERO
                : req.withholdingAmount());
        if (p.amount().signum() <= 0) {
            throw OrderService.invalid("cashAmount", "Enter an amount.");
        }
        requireNotFuture(req.receivedOn(), clock);
        p.receivedOn = req.receivedOn();
        p.reference = req.reference();
        return p;
    }

    static void requireNotFuture(LocalDate receivedOn, Clock clock) {
        if (receivedOn.isAfter(SalesTime.today(clock))) {
            throw OrderService.invalid("receivedOn", "The date received can't be in the future.");
        }
    }

    private void settle(SalesOrder o, BigDecimal amount) {
        if (o.settledAmount.add(amount).compareTo(o.total) > 0) {
            throw new DomainException(ErrorCode.OVERPAYMENT, "That is more than the balance due ("
                    + o.balance().toPlainString() + ").", Map.of("balance", o.balance()));
        }
        o.settledAmount = o.settledAmount.add(amount);
        o.updatedAt = Instant.now();
        orderRepo.saveAndFlush(o);
    }

    private static void requirePending(Payment p, long version) {
        if (p.version != version) {
            throw new DomainException(ErrorCode.STALE_VERSION, "This payment was changed by someone else. Reload.");
        }
        if (p.status != PaymentVerification.PENDING) {
            throw new DomainException(ErrorCode.INVALID_STATE,
                    "This payment is already " + p.status.name().toLowerCase() + ".");
        }
    }

    private void markRejected(Payment p, String reason) {
        p.status = PaymentVerification.REJECTED;
        p.rejectReason = reason;
        p.updatedAt = Instant.now();
        repo.saveAndFlush(p);
    }

    PaymentResponse toResponse(Payment p) {
        String name = USER.equals(p.submittedByType)
                ? users.displayNames(List.of(p.submittedById)).get(p.submittedById)
                : customers.contactNames(List.of(p.submittedById)).get(p.submittedById);
        return new PaymentResponse(p.id, p.status, p.method, p.cashAmount, p.withholdingAmount, p.receivedOn,
                p.reference, presign(p.proofKey), presign(p.form2307Key), p.submittedByType, name, p.verifiedAt,
                p.rejectReason, p.createdAt, p.version);
    }

    private String presign(String key) {
        return key == null ? null : storage.presignedUrl(key);
    }
}
