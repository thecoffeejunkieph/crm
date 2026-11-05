package ph.thecoffeejunkie.crm.customers;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditChanges;
import ph.thecoffeejunkie.crm.audit.AuditEventResponse;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.identity.SessionAdmin;
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.shared.ContactPrincipal;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.shared.PhoneKey;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Every staff read/write by id goes through {@link #findVisible}, so scope cannot be forgotten per endpoint. */
@Service
public class Customers {

    private static final String ENTITY = "CUSTOMER";
    private static final Set<String> SORTABLE = Set.of("displayName", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by("displayName");

    private final CustomerRepository repo;
    private final CustomerContactRepository contacts;
    private final AuditLog audit;
    private final Users users;
    private final SessionAdmin sessions;
    private final ApplicationEventPublisher events;
    private final LeadSources leadSources;
    private final JdbcTemplate jdbc;

    Customers(CustomerRepository repo, CustomerContactRepository contacts, AuditLog audit, Users users,
              SessionAdmin sessions, ApplicationEventPublisher events, LeadSources leadSources,
              JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.leadSources = leadSources;
        this.events = events;
        this.repo = repo;
        this.contacts = contacts;
        this.audit = audit;
        this.users = users;
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    PageResponse<CustomerSummary> list(Scope s, String q, boolean includeArchived, PageQuery page) {
        Specification<Customer> spec = Specification.allOf(CustomerScopes.visibleTo(s, includeArchived),
                CustomerScopes.matches(q));
        var result = repo.findAll(spec, page.toPageable(SORTABLE, DEFAULT_SORT));
        Map<Long, String> reps = repNames(result.getContent().stream().map(c -> c.assignedRepId).toList());
        return PageResponse.of(result.map(c -> new CustomerSummary(c.id, c.displayName, c.type, c.phone, c.email,
                c.assignedRepId == null ? null : reps.get(c.assignedRepId), c.archivedAt)));
    }

    @Transactional(readOnly = true)
    CustomerResponse get(Scope s, long id) {
        return toResponse(findVisible(id, s, true));
    }

    @Transactional
    CustomerResponse create(Scope s, CustomerRequest req) {
        Customer c = new Customer();
        boolean sales = s.role() == Role.SALES;
        c.assignedRepId = sales ? s.userId() : null;
        c.creditLimit = sales ? null : req.creditLimit();
        leadSources.requireActive(req.sourceId());
        apply(c, req);
        repo.saveAndFlush(c);
        audit.record(AuditAction.CREATED, ENTITY, c.id, "Created customer " + c.displayName, null);
        return toResponse(c);
    }

    @Transactional
    CustomerResponse update(Scope s, long id, CustomerRequest req) {
        Customer c = findVisible(id, s, false);
        if (s.role() == Role.SALES && !sameAmount(c.creditLimit, req.creditLimit())) {
            throw new DomainException(ErrorCode.FORBIDDEN, "Only an admin can change the credit limit.");
        }
        requireVersion(c.version, req.version());
        if (!java.util.Objects.equals(c.sourceId, req.sourceId())) {
            leadSources.requireActive(req.sourceId()); // an archived source may stay, but not be newly chosen
        }
        AuditChanges changes = AuditChanges.diff()
                .field("type", c.type, req.type())
                .field("displayName", c.displayName, req.displayName().trim())
                .field("businessName", c.businessName, req.businessName())
                .field("tin", c.tin, req.tin())
                .field("address", c.address, req.address())
                .field("phone", c.phone, req.phone())
                .field("email", c.email, normaliseOrNull(req.email()))
                .field("creditLimit", c.creditLimit, req.creditLimit())
                .field("paymentTerms", c.paymentTerms, req.paymentTerms())
                .field("taxTreatment", c.taxTreatment, req.taxTreatment())
                .field("sourceId", c.sourceId, req.sourceId())
                .field("notes", c.notes, req.notes())
                .field("socialMedia", c.socialMedia, req.socialMedia());
        apply(c, req);
        c.updatedAt = Instant.now();
        repo.saveAndFlush(c);
        audit.record(AuditAction.UPDATED, ENTITY, id, "Updated customer " + c.displayName, changes);
        return toResponse(c);
    }

    @Transactional
    void archive(Scope s, long id) {
        Customer c = findVisible(id, s, false);
        c.archivedAt = Instant.now();
        c.updatedAt = c.archivedAt;
        repo.saveAndFlush(c);
        for (CustomerContact k : contacts.findByCustomerIdAndArchivedAtIsNullOrderById(id)) {
            k.archivedAt = c.archivedAt;
            k.updatedAt = c.archivedAt;
            contacts.saveAndFlush(k);
            revokeAfterCommit(k.id);
        }
        audit.record(AuditAction.ARCHIVED, ENTITY, id, "Archived customer " + c.displayName, null);
    }

    @Transactional
    CustomerResponse assign(Scope s, long id, Long repId) {
        Customer c = findVisible(id, s, false);
        if (repId != null && !users.isActiveWithRole(repId, Role.SALES)) {
            throw invalid("repId", "Choose an active sales user.");
        }
        AuditChanges changes = AuditChanges.diff().field("assignedRepId", c.assignedRepId, repId);
        if (changes.isEmpty()) {
            return toResponse(c);
        }
        Long from = c.assignedRepId;
        c.assignedRepId = repId;
        c.updatedAt = Instant.now();
        repo.saveAndFlush(c);
        audit.record(AuditAction.ASSIGNMENT_CHANGED, ENTITY, id, "Changed assignment of " + c.displayName, changes);
        events.publishEvent(new CustomerReassigned(id, from, repId));
        return toResponse(c);
    }

    @Transactional
    ContactResponse addContact(Scope s, long customerId, ContactRequest req) {
        Customer c = findVisible(customerId, s, false);
        CustomerContact k = new CustomerContact();
        k.customerId = customerId;
        String email = normalise(req.email());
        requireEmailFree(email, null);
        fill(k, req, email);
        saveContact(k);
        audit.record(AuditAction.CREATED, ENTITY, customerId,
                "Added contact " + k.name + " to " + c.displayName, null);
        return toResponse(k);
    }

    @Transactional
    ContactResponse updateContact(Scope s, long customerId, long contactId, ContactRequest req) {
        Customer c = findVisible(customerId, s, false);
        CustomerContact k = contact(customerId, contactId);
        requireVersion(k.version, req.version());
        String email = normalise(req.email());
        requireEmailFree(email, contactId);
        AuditChanges changes = AuditChanges.diff().field("contact.name", k.name, req.name().trim())
                .field("contact.email", k.email, email).field("contact.phone", k.phone, req.phone())
                .field("contact.portalEnabled", k.portalEnabled, req.portalEnabled());
        // A live portal principal carries the old email, so email change or portal disable ends its sessions.
        boolean portalRevoked = (k.portalEnabled && !req.portalEnabled()) || !k.email.equals(email);
        fill(k, req, email);
        k.updatedAt = Instant.now();
        saveContact(k);
        audit.record(AuditAction.UPDATED, ENTITY, customerId,
                "Updated contact " + k.name + " of " + c.displayName, changes);
        if (portalRevoked) {
            revokeAfterCommit(contactId);
        }
        return toResponse(k);
    }

    @Transactional
    void archiveContact(Scope s, long customerId, long contactId) {
        Customer c = findVisible(customerId, s, false);
        CustomerContact k = contact(customerId, contactId);
        k.archivedAt = Instant.now();
        k.updatedAt = k.archivedAt;
        contacts.saveAndFlush(k);
        audit.record(AuditAction.ARCHIVED, ENTITY, customerId,
                "Archived contact " + k.name + " of " + c.displayName, null);
        revokeAfterCommit(contactId);
    }

    @Transactional(readOnly = true)
    PageResponse<AuditEventResponse> history(Scope s, long id, PageQuery page) {
        findVisible(id, s, true);
        return audit.history(ENTITY, id, page);
    }

    /** Deliberate scope exception (POS lookup, spec section 4): every active customer, minimal fields. */
    @Transactional(readOnly = true)
    List<CustomerLookupItem> lookup(String q) {
        if (q == null || q.trim().length() < 2) {
            throw invalid("q", "Type at least 2 characters.");
        }
        Specification<Customer> spec = Specification.allOf(CustomerScopes.active(), CustomerScopes.matches(q));
        return repo.findAll(spec, PageRequest.of(0, 20, DEFAULT_SORT)).stream()
                .map(c -> new CustomerLookupItem(c.id, c.displayName, c.phone, c.email)).toList();
    }

    /** For portal login (Task 14): an active, portal-enabled contact of a non-archived customer. */
    @Transactional(readOnly = true)
    public Optional<ContactPrincipal> portalContactByEmail(String email) {
        return contacts.findByEmailAndArchivedAtIsNull(normalise(email)).filter(k -> k.portalEnabled)
                .flatMap(k -> repo.findById(k.customerId).filter(c -> c.archivedAt == null)
                        .map(c -> new ContactPrincipal(k.id, c.id, k.email, k.name)));
    }

    /** For portal redeem: the same eligibility as {@link #portalContactByEmail}, by id. */
    @Transactional(readOnly = true)
    public Optional<ContactPrincipal> portalContact(long contactId) {
        return contacts.findById(contactId).filter(k -> k.archivedAt == null && k.portalEnabled)
                .flatMap(k -> repo.findById(k.customerId).filter(c -> c.archivedAt == null)
                        .map(c -> new ContactPrincipal(k.id, c.id, k.email, k.name)));
    }

    @Transactional(readOnly = true)
    public String customerName(long customerId) {
        return repo.findById(customerId).map(c -> c.displayName)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Customer not found"));
    }

    @Transactional(readOnly = true)
    public boolean contactActive(long contactId) {
        return contacts.findById(contactId).filter(k -> k.archivedAt == null && k.portalEnabled)
                .flatMap(k -> repo.findById(k.customerId)).filter(c -> c.archivedAt == null).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<CustomerSalesProfile> salesProfileVisible(Scope s, long id) {
        return repo.findOne(CustomerScopes.idEquals(id).and(CustomerScopes.visibleTo(s, true)))
                .map(Customers::toSalesProfile);
    }

    /** Unscoped: for system and portal paths that have already established access. */
    @Transactional(readOnly = true)
    public CustomerSalesProfile salesProfile(long id) {
        return repo.findById(id).map(Customers::toSalesProfile)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Customer not found"));
    }

    /** Empty = no restriction (ADMIN). Archived customers count, so their orders stay visible. */
    // ponytail: IN-list of a rep's customers; switch to a SQL join if a rep ever has thousands.
    @Transactional(readOnly = true)
    public Optional<Set<Long>> visibleCustomerIds(Scope s) {
        if (s.isAdmin()) {
            return Optional.empty();
        }
        return Optional.of(repo.findAll(CustomerScopes.visibleTo(s, true)).stream().map(c -> c.id)
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Transactional(readOnly = true)
    public Map<Long, String> displayNames(Collection<Long> ids) {
        Map<Long, String> out = new java.util.HashMap<>();
        repo.findAllById(new HashSet<>(ids)).forEach(c -> out.put(c.id, c.displayName));
        return out;
    }

    /**
     * A customer made from a lead, in the caller's transaction: it goes to the lead's rep (null = unassigned) and
     * remembers the lead. Credit-limit rules are those of a plain create. The lead's own source carries over even
     * if it was archived since: it is kept, not newly chosen.
     */
    @Transactional
    public long createForLead(Scope s, CustomerRequest req, Long repId, long leadId, Long leadSourceId) {
        Customer c = new Customer();
        c.assignedRepId = repId;
        c.creditLimit = s.role() == Role.SALES ? null : req.creditLimit();
        c.convertedFromLeadId = leadId;
        if (!java.util.Objects.equals(req.sourceId(), leadSourceId)) {
            leadSources.requireActive(req.sourceId());
        }
        apply(c, req);
        repo.saveAndFlush(c);
        audit.record(AuditAction.CREATED, ENTITY, c.id, "Created customer " + c.displayName + " from a lead", null);
        return c.id;
    }

    /** Links a lead to an existing active customer the caller can see; the first lead linked is the one kept. */
    @Transactional
    public void linkLead(Scope s, long customerId, long leadId) {
        Customer c = findVisible(customerId, s, false);
        if (c.convertedFromLeadId == null) {
            c.convertedFromLeadId = leadId;
            c.updatedAt = Instant.now();
            repo.saveAndFlush(c);
        }
    }

    /** Ids of customers the caller can see whose display or business name contains {@code q} (any case). */
    @Transactional(readOnly = true)
    public List<Long> idsNamed(Scope s, String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String like = "%" + q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_") + "%";
        Specification<Customer> named = (r, cq, cb) -> cb.or(cb.like(cb.lower(r.get("displayName")), like, '\\'),
                cb.like(cb.lower(cb.coalesce(r.<String>get("businessName"), "")), like, '\\'));
        // Scoped and uncapped: a rep's matches are their own book, never cut off by other reps' customers.
        return repo.findAll(CustomerScopes.visibleTo(s, true).and(named)).stream().map(c -> c.id).toList();
    }

    /** Active customers with this email (any case) or phone key; either may be null. */
    @Transactional(readOnly = true)
    public ContactMatches matchContact(Scope s, String email, String phoneKey) {
        List<String> where = new java.util.ArrayList<>();
        List<Object> args = new java.util.ArrayList<>();
        if (email != null && !email.isBlank()) {
            where.add("lower(email) = ?");
            args.add(normalise(email));
        }
        if (phoneKey != null) {
            where.add(PhoneKey.sql("phone") + " = ?");
            args.add(phoneKey);
        }
        if (where.isEmpty()) {
            return new ContactMatches(List.of(), 0);
        }
        List<ContactMatches.Candidate> rows = jdbc.query("select id, display_name, assigned_rep_id from customer "
                        + "where archived_at is null and (" + String.join(" or ", where) + ") order by id limit 200",
                (rs, i) -> new ContactMatches.Candidate(rs.getLong(1), rs.getString(2), rs.getObject(3, Long.class)),
                args.toArray());
        return ContactMatches.split(rows, s);
    }

    /** Archived contacts included: payments keep showing who submitted them. */
    @Transactional(readOnly = true)
    public Map<Long, String> contactNames(Collection<Long> ids) {
        Map<Long, String> out = new java.util.HashMap<>();
        contacts.findAllById(new HashSet<>(ids)).forEach(k -> out.put(k.id, k.name));
        return out;
    }

    /** Active, portal-enabled contacts: the people a quote can be sent to. */
    @Transactional(readOnly = true)
    public List<String> portalContactEmails(long customerId) {
        return contacts.findByCustomerIdAndArchivedAtIsNullOrderById(customerId).stream()
                .filter(k -> k.portalEnabled).map(k -> k.email).toList();
    }

    private static CustomerSalesProfile toSalesProfile(Customer c) {
        return new CustomerSalesProfile(c.id, c.type, c.displayName, c.businessName, c.tin, c.address, c.creditLimit,
                c.paymentTerms, c.taxTreatment, c.assignedRepId, c.archivedAt != null, c.email);
    }

    private Customer findVisible(long id, Scope s, boolean includeArchived) {
        return repo.findOne(CustomerScopes.idEquals(id).and(CustomerScopes.visibleTo(s, includeArchived)))
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Customer not found"));
    }

    private CustomerContact contact(long customerId, long contactId) {
        return contacts.findByIdAndCustomerIdAndArchivedAtIsNull(contactId, customerId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Contact not found"));
    }

    // The partial unique index is the real guard; the pre-check only gives the common case a cheap path.
    private void saveContact(CustomerContact k) {
        try {
            contacts.saveAndFlush(k);
        } catch (DataIntegrityViolationException e) {
            throw duplicateEmail();
        }
    }

    private void requireEmailFree(String email, Long selfId) {
        contacts.findByEmailAndArchivedAtIsNull(email).filter(k -> !k.id.equals(selfId)).ifPresent(k -> {
            throw duplicateEmail();
        });
    }

    private static void requireVersion(long current, Long sent) {
        if (sent == null) {
            throw invalid("version", "must not be null");
        }
        if (current != sent) {
            throw new DomainException(ErrorCode.STALE_VERSION,
                    "This record was changed by someone else. Reload and retry.");
        }
    }

    /** Same body as a failed {@code @Valid}: {@code errors:[{field,message}]}. */
    private static DomainException invalid(String field, String message) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, message,
                Map.of("errors", List.of(Map.of("field", field, "message", message))));
    }

    private static DomainException duplicateEmail() {
        return new DomainException(ErrorCode.DUPLICATE, "A contact with this email already exists.");
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? a == b : a.compareTo(b) == 0;
    }

    /** creditLimit is set by the caller: SALES must not be able to write it. */
    private static void apply(Customer c, CustomerRequest r) {
        c.type = r.type();
        c.displayName = r.displayName().trim();
        c.businessName = r.businessName();
        c.tin = r.tin();
        c.address = r.address();
        c.phone = r.phone();
        c.email = normaliseOrNull(r.email());
        c.paymentTerms = r.paymentTerms();
        c.taxTreatment = r.taxTreatment();
        c.sourceId = r.sourceId();
        c.notes = r.notes();
        c.socialMedia = r.socialMedia();
        if (c.id != null) {
            c.creditLimit = r.creditLimit();
        }
    }

    private static void fill(CustomerContact k, ContactRequest r, String email) {
        k.name = r.name().trim();
        k.email = email;
        k.phone = r.phone();
        k.portalEnabled = r.portalEnabled();
    }

    private static String normaliseOrNull(String email) {
        return email == null || email.isBlank() ? null : normalise(email);
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private Map<Long, String> repNames(Collection<Long> ids) {
        Set<Long> distinct = new HashSet<>(ids);
        distinct.remove(null);
        return distinct.isEmpty() ? Map.of() : users.displayNames(distinct);
    }

    private CustomerResponse toResponse(Customer c) {
        // An archived customer shows the contacts that were archived together with it.
        var rows = c.archivedAt == null ? contacts.findByCustomerIdAndArchivedAtIsNullOrderById(c.id)
                : contacts.findByCustomerIdAndArchivedAtOrderById(c.id, c.archivedAt);
        List<ContactResponse> cs = rows.stream()
                .map(Customers::toResponse).toList();
        String rep = c.assignedRepId == null ? null : repNames(List.of(c.assignedRepId)).get(c.assignedRepId);
        return new CustomerResponse(c.id, c.type, c.displayName, c.businessName, c.tin, c.address, c.phone, c.email,
                c.creditLimit, c.paymentTerms, c.taxTreatment, c.assignedRepId, rep, c.sourceId, leadSources.name(c.sourceId), c.notes, c.socialMedia, c.convertedFromLeadId, cs, c.archivedAt, c.version);
    }

    private static ContactResponse toResponse(CustomerContact k) {
        return new ContactResponse(k.id, k.name, k.email, k.phone, k.portalEnabled, k.version);
    }

    /** After commit: a portal login between an in-transaction revoke and the commit would otherwise survive it. */
    private void revokeAfterCommit(long contactId) {
        String principal = "contact:" + contactId;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sessions.revokeAll(principal);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sessions.revokeAll(principal);
            }
        });
    }
}
