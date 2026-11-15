# Redesign #2 Sales Lifecycle — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the product catalog, the single Order aggregate (optional quote → confirm → release → fulfil),
BIR-compliant invoices with void/reissue, payments with withholding tax, and the portal order screens.

**Architecture:** Two new Spring modules, `catalog` and `sales`, on the Foundation conventions (package-private
entities, scoped queries, approvals, same-transaction audit, ProblemDetail). Pure classes own the money and state
rules (`OrderTotals`, `OrderFingerprint`, `Balances`, `OrderStateMachine`) so they can be tested without Spring.
React feature folders `catalog` and `sales` reuse the Foundation `src/ui` kit and the approval dialog.

**Tech Stack:** As Foundation (Java 21, Spring Boot 4.0.6, Postgres 17, Flyway, Testcontainers, Modulith-test;
React 19, TanStack Query 5, react-hook-form + zod, Vitest, Playwright). One addition:
`io.github.openhtmltopdf:openhtmltopdf-pdfbox` (latest 1.1.x on Maven Central) for Thymeleaf → PDF.

**Spec:** `docs/superpowers/specs/2026-10-14-sales-lifecycle-design.md` — read it before any task. `§n` refers to it.
Foundation spec and plan (`2026-10-12-foundation*.md`) define every convention used below.

## Global Constraints

- Branch `redesign/sales`, created from `redesign/foundation`, in both `crm` and `crm-dashboard-ui`
  (`C:\Users\The Coffee Junkie\Documents\Codes\crm-dashboard-ui`). Use worktrees `.worktrees/redesign-sales`.
- Modules: `catalog` (depends on `shared`, `audit`); `sales` (depends on `catalog`, `customers`, `approvals`,
  `identity`, `audit`, `settings`, `files`, `notifications`, `shared`). Nothing depends on `sales`. Only facades,
  DTOs, enums and events used across modules are `public`.
- Money `numeric(19,2)` / `BigDecimal`, every rounding `RoundingMode.HALF_UP`, scale 2. Quantity `numeric(12,3)`.
  Prices are VAT-inclusive. VAT divisor is exactly `1.12`.
- Business dates (quote validity, due dates, overdue, receivedOn) use `ZoneId.of("Asia/Manila")` with the existing
  `Clock` bean (`identity/SessionConfig`): `LocalDate.now(clock.withZone(MANILA))`. Constant `SalesTime.MANILA`.
- Order number `SO-<YY>-%05d` from sequence `sales_order_number_seq` (YY = Manila year at creation). Invoice
  number `SI-%05d` from `document_counter` row `INVOICE`.
- Approval subject version for orders is `OrderFingerprint.of(order)` — a **non-negative 52-bit** long, so it
  survives a JavaScript `number` round trip unchanged. Never use the JPA `version` as an approval subject version.
- `cost` (and anything derived from it) never reaches a SALES or portal response, including approval
  `requestedValue`s and problem bodies.
- State-changing order calls carry `version` (JPA) → mismatch is 409 `STALE_VERSION`.
- New `ErrorCode`s, all `HttpStatus.UNPROCESSABLE_CONTENT`: `INVALID_STATE`, `QUOTE_EXPIRED`, `HAS_PAYMENTS`,
  `OVERPAYMENT`.
- Every task that adds or changes an endpoint adds its rows to `SecurityMatrixTest.MATRIX` and regenerates
  `openapi.json` (`./gradlew test --tests '*OpenApiSnapshotTest' -DupdateSnapshot=true`) in the same commit, so the
  full suite stays green after every task.
- Audit entity types: `PRODUCT`, `ORDER` (invoice and payment events are recorded under `ORDER` with the order id, so
  the order History tab shows them).
- Frontend: generated types only (`npm run gen:api` with
  `OPENAPI_PATH="C:/Users/The Coffee Junkie/Documents/Codes/crm/crm/.worktrees/redesign-sales/openapi.json"`); money
  shown as `PHP 1,234.56` in PDFs (the base-14 PDF fonts have no ₱) and with `₱` in the UI.

## Review Focus

- **Contact accepts a quote that changed under them.** Rep edits and re-sends while the contact's page is open; the
  stale Accept must be 409 `STALE_VERSION`, not accept content they never saw → test in Task 13.
- **Revision drops the total below what's already paid.** Must be 422 `OVERPAYMENT` with nothing saved, never a
  negative balance → test in Task 12.
- **Product archived after it was put on a draft.** Editing other lines of that draft must still save; adding a
  newly archived product is 400 → test in Task 6.
- **Customer reassigned to another rep.** The old rep's next order request is 404 and the new rep sees it, with no
  data copy → test in Task 6.
- **Quote sent to a customer with no portal-enabled contact.** Order still becomes QUOTED, `emailedTo` is empty
  and the UI warns, rather than failing or silently pretending it was emailed → tests in Task 7 and Task 17.

---

# Part A — Backend (`crm`)

### Task 1: Schema V2 and sales settings

**Files:**
- Create: `src/main/resources/db/migration/V2__catalog_sales.sql`
- Modify: `settings/SettingKey.java`
- Test: `shared/SalesSchemaIT.java`, `settings/SettingsIT.java` (add cases)

**Interfaces:**
- Produces tables exactly as spec §2, plus on `sales_order`: `settled_amount numeric(19,2) not null default 0`,
  `due_date date null`. File columns are `proof_key text`, `form2307_key text` (MinIO keys from
  `StorageService.store`). Indexes: `sales_order (customer_id)`, `sales_order (status)`, `order_line (order_id)`,
  `invoice (order_id)`, `payment (order_id)`, `order_revision (order_id, revision)` unique.
- `create sequence sales_order_number_seq`; `insert into document_counter values ('INVOICE', 1)`.
- `alter table approval_request` drop/re-add the `type` CHECK with `INVOICE_VOID` added.
- `alter table customer add column tax_treatment text not null default 'REGULAR' check (...)`.
- `SettingKey` adds: `SALES_QUOTE_VALIDITY_DAYS("sales.quoteValidityDays", number(1, 365))`,
  `SALES_WITHHOLDING_SUGGEST_PERCENT("sales.withholdingSuggestPercent", number(0, 100))`,
  `COMPANY_NAME("company.name", text(200))`, `COMPANY_TIN("company.tin", text(32))`,
  `COMPANY_ADDRESS("company.address", text(500))`, `INVOICE_FOOTER_TEXT("invoice.footerText", text(1000))`.
  New validator `text(int maxLength)`: any JSON string (empty allowed), trimmed, longer → `VALIDATION_FAILED`.
- Seed rows: `('sales.quoteValidityDays','15')`, `('sales.withholdingSuggestPercent','1')`, and `'""'` for the four
  text keys.
- Grants come from Foundation's default privileges; verify `crm_app` can use the new sequence.

- [ ] **Step 1: Write failing tests**
  - `SalesSchemaIT` (JdbcTemplate as `crm_app`): `secondIssuedInvoicePerOrderRejected` (two `ISSUED` rows for one
    order → `DataIntegrityViolationException`; one `ISSUED` + one `VOID` is fine); `skuUniqueIgnoringCaseAndArchived`
    (`ab-1` then `AB-1` fails; archiving the first lets it insert); `negativeQtyAndDiscountOver100Rejected`;
    `paymentNeedsPositiveAmount` (cash 0 + withholding 0 fails); `invoiceVoidApprovalTypeAccepted`;
    `appCanDrawOrderNumbers` (`select nextval('sales_order_number_seq')` works as `crm_app`).
  - `SettingsIT`: `textSettingTooLongRejected` (`company.tin` of 33 chars → 400); `quoteValidityZeroRejected` (400);
    `companyNameRoundTrips`.
- [ ] **Step 2:** Run `./gradlew test --tests '*SalesSchemaIT' --tests '*SettingsIT'` — Expected: FAIL.
- [ ] **Step 3:** Write the migration and setting keys.
- [ ] **Step 4:** Run the same command — Expected: PASS. Run `./gradlew test` — Expected: all PASS.
- [ ] **Step 5:** Commit `feat(db): catalog and sales schema, sales settings`.

### Task 2: Approvals — several requirements at once, system requests, decision event

**Files:**
- Create: `approvals/RequiredApproval.java`, `approvals/ApprovalDecided.java`
- Modify: `approvals/Approvals.java`, `approvals/ApprovalType.java`
- Test: `approvals/ApprovalsIT.java` (add cases)

**Interfaces:**
- `public record RequiredApproval(ApprovalType type, String subjectType, long subjectId, long subjectVersion, Map<String, Object> requestedValue)`
- `ApprovalType` gains `INVOICE_VOID`.
- `public void ensureAllApproved(List<RequiredApproval> required)` — empty → return. ADMIN actor → one
  `SELF_APPROVED` audit row per requirement (under its subject) and return. Otherwise compute `missing(required)`;
  non-empty → `DomainException(APPROVAL_REQUIRED, "Approval required", props)` where `props` holds the **first**
  missing requirement's `approvalType, subjectType, subjectId, subjectVersion, requestedValue` (keeps Foundation's
  single-approval UI working) plus `approvals`: list of maps with those five keys for every missing one.
- `public List<RequiredApproval> missing(List<RequiredApproval> required)` — no admin bypass; a requirement is
  satisfied by an `APPROVED` row with the same type, subjectType, subjectId, subjectVersion.
- Existing `ensureApproved(type, subjectType, id, version, value)` delegates to `ensureAllApproved(List.of(...))`.
- `public ApprovalResponse requestOnBehalf(CreateApprovalRequest req, long requestedBy)`; existing `request(req)`
  becomes `requestOnBehalf(req, actor.scope().userId())`.
- `public record ApprovalDecided(long approvalId, ApprovalType type, String subjectType, long subjectId, long subjectVersion, ApprovalStatus status)`
  published with `ApplicationEventPublisher` inside `decide`, after the save.

- [ ] **Step 1: Write failing tests** in `ApprovalsIT`: `ensureAllListsEveryMissing` (3 required, 1 approved →
  422 with `approvals.length() == 2`, top-level `approvalType` = first missing); `adminSelfApprovesEach` (2 required
  as ADMIN → no throw, 2 `SELF_APPROVED` audit rows); `missingIgnoresAdmin` (as ADMIN, nothing approved →
  `missing` returns both); `requestOnBehalfStoresGivenRequester`; `decidePublishesEvent`
  (`@RecordApplicationEvents`, one `ApprovalDecided` with status `APPROVED`); `invoiceVoidTypeAccepted`.
- [ ] **Step 2:** Run `./gradlew test --tests '*ApprovalsIT'` — Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run — Expected: PASS. Regenerate the OpenAPI snapshot (enum change).
- [ ] **Step 5:** Commit `feat(approvals): multi-approval checks, system requests, decision event`.

### Task 3: Customers — tax treatment and the sales-facing reads

**Files:**
- Create: `customers/TaxTreatment.java`, `customers/CustomerSalesProfile.java`
- Modify: `customers/Customer.java`, `CustomerRequest.java`, `CustomerResponse.java`, `Customers.java`
- Test: `customers/CustomersIT.java` (add cases)

**Interfaces:**
- `public enum TaxTreatment { REGULAR, ZERO_RATED, VAT_EXEMPT }`
- `CustomerRequest` gains trailing `TaxTreatment taxTreatment` (null → `REGULAR`); `CustomerResponse` gains
  `TaxTreatment taxTreatment` after `paymentTerms`. Changes are audited as field `taxTreatment`. SALES may set it.
- `public record CustomerSalesProfile(long id, CustomerType type, String displayName, String businessName, String tin, String address, BigDecimal creditLimit, PaymentTerms paymentTerms, TaxTreatment taxTreatment, Long assignedRepId, boolean archived)`
- On `Customers`:
  - `public Optional<CustomerSalesProfile> salesProfileVisible(Scope s, long id)` — same scope rule as
    `findVisible`, archived included.
  - `public CustomerSalesProfile salesProfile(long id)` — unscoped (system and portal paths); missing → `NOT_FOUND`.
  - `public Optional<Set<Long>> visibleCustomerIds(Scope s)` — ADMIN → `Optional.empty()` (no restriction); SALES
    → ids with `assigned_rep_id = userId` (archived included); WAREHOUSE → `Optional.of(Set.of())`.
    `// ponytail: IN-list of a rep's customers; switch to a SQL join if a rep ever has thousands.`
  - `public Map<Long, String> displayNames(Collection<Long> ids)`
  - `public List<String> portalContactEmails(long customerId)` — active, `portalEnabled` contacts.

- [ ] **Step 1: Write failing tests** in `CustomersIT`: `taxTreatmentDefaultsToRegular`; `taxTreatmentChangeAudited`;
  `visibleIdsForSalesOnlyOwnIncludingArchived`; `visibleIdsForAdminUnrestricted`;
  `portalContactEmailsSkipsDisabledAndArchived`.
- [ ] **Step 2:** Run `./gradlew test --tests 'ph.thecoffeejunkie.crm.customers.*'` — Expected: FAIL.
- [ ] **Step 3:** Implement; fix existing `CustomerRequest` constructor calls in tests.
- [ ] **Step 4:** Run — Expected: PASS. Regenerate the OpenAPI snapshot.
- [ ] **Step 5:** Commit `feat(customers): tax treatment and sales profile reads`.

### Task 4: Catalog module

**Files:**
- Create in `catalog/`: `Catalog.java` (public facade), `ProductUnit.java`, `TaxClass.java`, `ProductInfo.java`
  (public), `Product.java`, `ProductRepository.java`, `ProductController.java`, `CatalogPermissions.java`,
  `ProductRequest.java`, `ProductResponse.java`
- Modify: `identity/SecurityConfig.java`, `SecurityMatrixTest.java`, `openapi.json`
- Test: `catalog/CatalogIT.java`

**Interfaces:**
- `public enum ProductUnit { PC, BOX, PACK, KG, G, L, ML, M }`; `public enum TaxClass { VATABLE, VAT_EXEMPT, ZERO_RATED }`
- `record ProductRequest(@NotBlank @Size(max = 64) String sku, @NotBlank @Size(max = 200) String name, String description, @NotNull ProductUnit unit, @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal price, @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal cost, @NotNull TaxClass taxClass, Long version)`
- `record ProductResponse(long id, String sku, String name, String description, ProductUnit unit, BigDecimal price, @JsonInclude(NON_NULL) BigDecimal cost, TaxClass taxClass, Instant archivedAt, long version)` — `cost` is set only when the actor is ADMIN.
- `public record ProductInfo(long id, String sku, String name, ProductUnit unit, BigDecimal price, BigDecimal cost, TaxClass taxClass, boolean archived)`
- `Catalog`: package-private `list(String q, boolean includeArchived, PageQuery)` (q matches sku or name,
  case-insensitive contains; sortable `sku, name`; default `name`), `get`, `create`, `update` (version check),
  `archive`; `public Map<Long, ProductInfo> info(Collection<Long> ids)` (archived included).
- Duplicate active SKU → 409 `DUPLICATE` (partial index is the arbiter, as in `Customers.saveContact`).
- `@Component("catalogPermissions")`: `canRead()` ADMIN/SALES/WAREHOUSE, `canWrite()` ADMIN.
- Endpoints: `GET /api/products`, `GET /api/products/{id}` (STAFF); `POST /api/products`, `PUT /api/products/{id}`,
  `POST /api/products/{id}/archive` (ADMIN). Audit `CREATED/UPDATED/ARCHIVED` on `PRODUCT` with field diffs.

- [ ] **Step 1: Write failing tests** in `CatalogIT`: `salesListHasNoCostField` (`jsonPath("$.content[0].cost").doesNotExist()`);
  `adminSeesCost`; `salesCannotCreate` (403); `duplicateSkuCaseInsensitive409`; `archivedHiddenUnlessRequested`;
  `priceChangeAudited` (`changes.price == ["10.00","12.00"]`); `staleVersion409`; `infoIncludesArchived`.
- [ ] **Step 2:** Run `./gradlew test --tests 'ph.thecoffeejunkie.crm.catalog.*'` — Expected: FAIL.
- [ ] **Step 3:** Implement; add matrix rows; regenerate the snapshot.
- [ ] **Step 4:** Run the catalog tests, `SecurityMatrixTest`, `ModularityTest` — Expected: PASS.
- [ ] **Step 5:** Commit `feat(catalog): products with admin-only cost`.

### Task 5: Pure sales rules

**Files:**
- Create in `sales/`: `OrderStatus.java`, `PaymentStatus.java`, `OrderAction.java` (public enums), `SalesTime.java`,
  `OrderTotals.java`, `OrderStateMachine.java`, `Balances.java`
- Test: `sales/OrderTotalsTest.java`, `sales/OrderStateMachineTest.java`, `sales/BalancesTest.java`

**Interfaces:**
- `public enum OrderStatus { DRAFT, QUOTED, CONFIRMED, RELEASED, FULFILLED, CANCELLED }`
- `public enum PaymentStatus { UNPAID, PARTIAL, PAID, OVERDUE }`
- `public enum OrderAction { EDIT, SEND_QUOTE, CONFIRM, ACCEPT, DECLINE, REVISE, RELEASE, FULFIL, CANCEL, ISSUE_INVOICE, REISSUE_INVOICE, RECORD_PAYMENT }`
- `final class SalesTime { static final ZoneId MANILA; static LocalDate today(Clock c); }`
- `OrderTotals`:
  - `record Line(BigDecimal qty, BigDecimal unitPrice, BigDecimal discountPct, TaxClass taxClass)`
  - `record Result(List<BigDecimal> lineTotals, BigDecimal vatableSales, BigDecimal vat, BigDecimal vatExemptSales, BigDecimal zeroRatedSales, BigDecimal total)`
  - `static Result compute(List<Line> lines, BigDecimal shippingFee, TaxTreatment treatment)` — algorithm §4
    (line net rounded first; shipping is a VATABLE amount; override: `ZERO_RATED`/`VAT_EXEMPT` treatment turns every
    line and the shipping fee into that class; `vatable_sales` = gross ÷ 1.12 rounded; `vat` = remainder).
  - `static BigDecimal netOfVat(BigDecimal inclusive)` = `inclusive ÷ 1.12` rounded (default price for
    non-`REGULAR` customers, §2).
- `OrderStateMachine`: `static Set<OrderAction> allowed(OrderStatus s)`; `static void require(OrderStatus s, OrderAction a)`
  → `DomainException(INVALID_STATE, "Can't <action> a <status> order")`. Table:

  | Status | Allowed |
  |---|---|
  | DRAFT | EDIT, SEND_QUOTE, CONFIRM, CANCEL |
  | QUOTED | EDIT, SEND_QUOTE, CONFIRM, ACCEPT, DECLINE, CANCEL |
  | CONFIRMED | REVISE, RELEASE, CANCEL, ISSUE_INVOICE, REISSUE_INVOICE, RECORD_PAYMENT |
  | RELEASED | REVISE, FULFIL, CANCEL, ISSUE_INVOICE, REISSUE_INVOICE, RECORD_PAYMENT |
  | FULFILLED | ISSUE_INVOICE, REISSUE_INVOICE, RECORD_PAYMENT |
  | CANCELLED | — |

  (Invoice presence, expiry and payments are checked by the services, not the table.)
- `Balances`:
  - `static PaymentStatus status(BigDecimal total, BigDecimal settled, LocalDate dueDate, LocalDate today)` —
    balance > 0 and `dueDate != null && today.isAfter(dueDate)` → OVERDUE; balance ≤ 0 → PAID; settled = 0 →
    UNPAID; else PARTIAL.
  - `static BigDecimal suggestedWithholding(OrderTotals.Result t, BigDecimal percent, CustomerType type)` —
    `BUSINESS` → `(vatableSales + vatExemptSales + zeroRatedSales) × percent / 100` rounded; otherwise `0.00`.
- Also `ErrorCode` gains the four codes from Global Constraints (here, since the state machine throws one).

- [ ] **Step 1: Write failing tests**
  - `OrderTotalsTest`: `singleVatableLine` (3 × 112.00 → vatable 300.00, vat 36.00, total 336.00);
    `remainderVat` (1 × 100.00 → 89.29 / 10.71 / 100.00); `discountRoundsPerLine` (2.5 × 99.99 at 10% → line
    224.98); `mixedClassesWithShipping` (VATABLE 112, VAT_EXEMPT 50, ZERO_RATED 30, shipping 56 → vatable 150.00,
    vat 18.00, exempt 50.00, zero 30.00, total 248.00); `zeroRatedCustomerOverridesAll` (same input, treatment
    ZERO_RATED → zero 248.00, vatable 0.00, vat 0.00); `breakdownAlwaysSums` (loop over 1..500 cents × qty
    1..7: vatable + vat + exempt + zero == total); `netOfVat` (112.00 → 100.00; 99.99 → 89.28).
  - `OrderStateMachineTest`: parameterised over every status × action; allowed iff in the table; the rejected
    message names action and status.
  - `BalancesTest`: `overdueBeatsPartial`; `dueTodayIsNotOverdue`; `zeroTotalIsPaid`; `withholdingOnePercentBusiness`
    (vatable 300.00 + exempt 50.00 at 1 → 3.50); `withholdingZeroForIndividual`.
- [ ] **Step 2:** Run `./gradlew test --tests 'ph.thecoffeejunkie.crm.sales.*Test'` — Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run — Expected: PASS.
- [ ] **Step 5:** Commit `feat(sales): totals, state machine and balance rules`.

### Task 6: Orders — create, edit, read, list, scope, history

**Files:**
- Create in `sales/`: `SalesOrder.java`, `OrderLine.java`, `SalesOrderRepository.java`, `OrderScopes.java`,
  `OrderFingerprint.java`, `OrderService.java`, `OrderController.java`, `SalesPermissions.java`, records
  `OrderRequest`, `LineRequest`, `OrderResponse`, `LineResponse`, `TotalsResponse`, `InvoiceSummary`, `OrderSummary`
- Modify: `identity/SecurityConfig.java`, `SecurityMatrixTest.java`, `openapi.json`
- Test: `sales/OrderFingerprintTest.java`, `sales/OrdersIT.java`, `sales/OrderScopingIT.java`

**Interfaces:**
- Consumes: `Customers.salesProfileVisible/visibleCustomerIds/displayNames`, `Catalog.info`, `Users.displayNames`,
  `OrderTotals`, `Balances`, `OrderStateMachine`, `Settings.decimal(SALES_WITHHOLDING_SUGGEST_PERCENT)`.
- `SalesOrder` (`@Entity @Table(name = "sales_order")`) has `@OneToMany(mappedBy = "order", cascade = ALL, orphanRemoval = true) @OrderBy("position") List<OrderLine> lines`. Lines are replaced wholesale on every save.
- `SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder>`; adds
  `@Lock(PESSIMISTIC_WRITE) @Query("select o from SalesOrder o where o.id = :id") Optional<SalesOrder> findByIdForUpdate(long id)`
  and `@Query(value = "select nextval('sales_order_number_seq')", nativeQuery = true) long nextNumber()`.
- `OrderFingerprint`: `static long of(SalesOrder o)` — SHA-256 over `customerId|paymentTerms|shippingFee|` then per
  line `productId|qty|unitPrice|discountPct|` (BigDecimals as `stripTrailingZeros().toPlainString()`); take the first
  8 bytes as a long, then `& 0x000F_FFFF_FFFF_FFFFL`.
- `OrderScopes.visibleTo(Optional<Set<Long>> customerIds)` → empty → no restriction; a set → `customer_id in (...)`
  (empty set → `disjunction()`). `OrderService.findVisible(long id, Scope s)` → absent → `NOT_FOUND "Order not found"`.
- `record LineRequest(@NotNull Long productId, @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal qty, @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal unitPrice, @DecimalMin("0") @DecimalMax("100") BigDecimal discountPct, @Size(max = 500) String description)`
  — null `unitPrice` → catalog price (or `OrderTotals.netOfVat(price)` for a `VATABLE` product on a non-`REGULAR`
  customer); null `discountPct` → 0; null/blank `description` → product name. `unit` and `taxClass` snapshot from
  the catalog on every save.
- `record OrderRequest(Long customerId, PaymentTerms paymentTerms, @PositiveOrZero BigDecimal shippingFee, String notes, String terms, @NotNull @Size(max = 200) @Valid List<LineRequest> lines, Long version, String reason)`
  — `customerId` required on create, ignored on update; `paymentTerms` null → customer's.
- `record TotalsResponse(BigDecimal vatableSales, BigDecimal vat, BigDecimal vatExemptSales, BigDecimal zeroRatedSales, BigDecimal total)`
- `record LineResponse(long id, int position, long productId, String sku, String description, ProductUnit unit, BigDecimal qty, BigDecimal unitPrice, BigDecimal discountPct, TaxClass taxClass, BigDecimal lineTotal, @JsonInclude(NON_NULL) Boolean belowCost)` — `belowCost` only for ADMIN.
- `record InvoiceSummary(long id, String number, Instant issuedAt, LocalDate dueDate, BigDecimal total)`
- `record OrderResponse(long id, String number, long customerId, String customerName, CustomerType customerType, Long repId, String repName, OrderStatus status, PaymentStatus paymentStatus, int revision, PaymentTerms paymentTerms, LocalDate quoteValidUntil, boolean quoteExpired, BigDecimal shippingFee, String notes, String terms, List<LineResponse> lines, TotalsResponse totals, BigDecimal settled, BigDecimal balance, BigDecimal suggestedWithholding, InvoiceSummary activeInvoice, Instant quotedAt, Instant customerAcceptedAt, Instant confirmedAt, Instant releasedAt, Instant fulfilledAt, Instant cancelledAt, String cancelReason, Set<OrderAction> allowedActions, long approvalVersion, Instant updatedAt, long version)`
  — `approvalVersion` = fingerprint; `activeInvoice` null until Task 9; `allowedActions` = state table minus
  actions the services would refuse right now (ACCEPT/DECLINE never shown to staff; ISSUE_INVOICE only without an
  active invoice; REISSUE_INVOICE only with one; FULFIL only for ADMIN).
- `record OrderSummary(long id, String number, long customerId, String customerName, OrderStatus status, PaymentStatus paymentStatus, BigDecimal total, BigDecimal balance, String repName, Instant updatedAt)`
- `OrderService` package-private methods: `PageResponse<OrderSummary> list(Scope, OrderFilter, PageQuery)` with
  `record OrderFilter(List<OrderStatus> status, PaymentStatus paymentStatus, Long customerId, Long repId, String q, boolean awaitingConfirmation)`
  (`q` = number contains; `awaitingConfirmation` = QUOTED with `customer_accepted_at` set; `paymentStatus` as SQL
  over `total`, `settled_amount`, `due_date`, Manila today; sortable `number, updatedAt, total`, default
  `updatedAt,desc`); `OrderResponse get(Scope, long id)`; `OrderResponse create(Scope, OrderRequest)`;
  `OrderResponse update(Scope, long id, OrderRequest)` (EDIT; a QUOTED order returns to DRAFT and clears
  `customerAcceptedAt`; CONFIRMED/RELEASED is delegated to `revise` in Task 12 — until then `INVALID_STATE`);
  `PageResponse<AuditEventResponse> history(Scope, long id, PageQuery)`; `OrderResponse toResponse(SalesOrder, Scope)`
  (shared by later tasks).
- Create rules: customer visible and not archived (archived → 400 field `customerId`); `repId` = customer's
  `assignedRepId`; `createdBy` = actor. Adding a product that is archived and not already on this order → 400 field
  `lines[i].productId`.
- `@Component("salesPermissions")`: `canRead()`, `canWrite()` ADMIN/SALES; `canFulfil()` ADMIN.
- Endpoints: `GET /api/orders`, `POST /api/orders`, `GET /api/orders/{id}`, `PUT /api/orders/{id}`,
  `GET /api/orders/{id}/history`. SecurityConfig: `/api/orders/**`, `/api/payments/**`, `/api/invoices/**`,
  `/api/reports/**` → `hasAnyRole("ADMIN","SALES")` (add all four now).
- Audit on `ORDER`: `CREATED`, `UPDATED` with diffs of header fields and `lines` as a one-line summary
  (`"3 lines, total 336.00"` old → new).

- [ ] **Step 1: Write failing tests**
  - `OrderFingerprintTest`: `sameContentSameValue` (qty `2` vs `2.000` equal); `anyLineChangeDiffers`;
    `alwaysJsSafe` (1,000 random orders → `0 <= v <= 2^52 - 1`).
  - `OrdersIT`: `createNumbersAndAssignsRep` (number matches `SO-\d{2}-\d{5}`, repId = customer's rep);
    `defaultsFromCatalog` (null price → catalog 112.00; description = product name); `nonRegularCustomerGetsNetPrice`
    (ZERO_RATED customer, VATABLE 112.00 → line price 100.00); `totalsCachedAndReturned` (3 × 112 → 336.00, vat
    36.00); `editingQuotedReturnsToDraft`; `staleVersion409`; `archivedProductAlreadyOnDraftStillSaves` and
    `newArchivedProductRejected` (Review Focus); `belowCostFlagOnlyForAdmin`; `historyShowsLineSummaryChange`;
    `paymentStatusFilter` (seed `settled_amount`/`due_date` by SQL: one PAID, one PARTIAL, one OVERDUE → each filter
    returns exactly its order).
  - `OrderScopingIT`: `salesListsOnlyOwnCustomersOrders`; `otherRepsOrderIs404` (GET, PUT, history);
    `reassignedCustomerMovesOrders` (Review Focus: old rep 404, new rep 200); `warehouseGets403`.
- [ ] **Step 2:** Run `./gradlew test --tests 'ph.thecoffeejunkie.crm.sales.*'` — Expected: new tests FAIL.
- [ ] **Step 3:** Implement; matrix rows; regenerate the snapshot.
- [ ] **Step 4:** Run sales tests, `SecurityMatrixTest`, `ModularityTest` — Expected: PASS.
- [ ] **Step 5:** Commit `feat(sales): orders with scoped list, edit and history`.

### Task 7: PDF rendering, quote PDF and Send quote

**Files:**
- Modify: `build.gradle` (add openhtmltopdf-pdfbox), `shared/HtmlMail.java`
- Create: `sales/PdfRenderer.java`, `sales/QuoteDocument.java`, `sales/SendQuoteResponse.java`,
  `sales/TransitionRequest.java`, `resources/templates/pdf/quote.html`, `resources/templates/mail/quote-sent.html`
- Modify: `sales/OrderService.java`, `sales/OrderController.java`, matrix, snapshot
- Test: `sales/PdfRendererTest.java`, `sales/SendQuoteIT.java`

**Interfaces:**
- `HtmlMail`: `public record Attachment(String filename, String contentType, byte[] bytes)` and
  `public void send(String to, String subject, String template, Map<String, Object> vars, List<Attachment> attachments)`
  (multipart only when attachments are present); the 4-arg `send` delegates with `List.of()`.
- `PdfRenderer` (`@Component`): `byte[] render(String template, Map<String, Object> vars)` — own Thymeleaf
  `TemplateEngine`, prefix `templates/pdf/`, `TemplateMode.HTML`; templates are written as well-formed XHTML
  (`<br/>`, closed tags) so openhtmltopdf can parse Thymeleaf's output; `PdfRendererBuilder.useFastMode()`.
  Money formatted in the template with `#numbers.formatDecimal(x, 1, 'COMMA', 2, 'POINT')`, prefixed `PHP`.
- `QuoteDocument` (`@Component`): `byte[] render(SalesOrder o)` — vars: company name/TIN/address from settings,
  customer from `Customers.salesProfile`, lines, totals, `quoteValidUntil`, `terms`, `notes`. Heading `QUOTATION`.
- `record TransitionRequest(@NotNull Long version, String reason)` — used by every transition endpoint from here on.
- `record SendQuoteResponse(OrderResponse order, List<String> emailedTo)`
- `OrderService.sendQuote(Scope, long id, TransitionRequest)`: `SEND_QUOTE`; at least one line (else 400 field
  `lines`); `quotedAt = now`, `quoteValidUntil = today + sales.quoteValidityDays`, status `QUOTED`; audit
  `QUOTE_SENT`; **after commit** render the PDF and send `quote-sent` to each `portalContactEmails` address with
  attachment `<number>.pdf` and `link = ${app.frontend-url}/portal/signin`.
- Endpoints: `POST /api/orders/{id}/send-quote` → `SendQuoteResponse`; `GET /api/orders/{id}/quote.pdf` →
  `application/pdf`, `Content-Disposition: inline; filename="<number>.pdf"`.
- `AuditAction` gains `QUOTE_SENT`.

- [ ] **Step 1: Write failing tests**
  - `PdfRendererTest` (no Spring): render `quote.html` with fixed vars → bytes start with `%PDF`; PDFBox
    `PDFTextStripper` text contains `QUOTATION`, the order number, `VATable Sales`, `PHP 300.00`.
  - `SendQuoteIT` (uses `MailSupport`): `sendsPdfToPortalContacts` (2 portal contacts + 1 disabled → 2 mails, each
    with `SO-…pdf` attachment; `emailedTo` has 2); `noContactsStillQuoted` (Review Focus: `emailedTo` empty, status
    QUOTED); `validityFromSetting` (setting 7 → `quoteValidUntil` = Manila today + 7); `emptyOrderRejected` (400);
    `rollbackSendsNoMail` (stale version → 409 and zero mails); `quotePdfScoped` (other rep → 404).
- [ ] **Step 2:** Run `./gradlew test --tests '*PdfRendererTest' --tests '*SendQuoteIT'` — Expected: FAIL.
- [ ] **Step 3:** Implement; matrix rows; snapshot.
- [ ] **Step 4:** Run — Expected: PASS.
- [ ] **Step 5:** Commit `feat(sales): quote PDF and send quote`.

### Task 8: Confirm and the approval checks

**Files:**
- Create: `sales/ApprovalChecks.java`
- Modify: `sales/SalesOrderRepository.java`, `sales/OrderService.java`, `sales/OrderController.java`, matrix, snapshot
- Test: `sales/ConfirmIT.java`

**Interfaces:**
- `SalesOrderRepository` native queries:
  - `BigDecimal openBalanceExcluding(long customerId, long orderId)` = `coalesce(sum(total - settled_amount), 0)`
    over the customer's other orders in `CONFIRMED, RELEASED, FULFILLED`.
  - `boolean hasOverdue(long customerId, LocalDate today)` = exists such an order with `due_date < today` and
    `settled_amount < total`.
- `ApprovalChecks` (`@Component`): `List<RequiredApproval> forConfirm(SalesOrder o, CustomerSalesProfile c, Map<Long, ProductInfo> products, LocalDate today)`
  — subject `("ORDER", o.id, OrderFingerprint.of(o))`; rules §3 table. `requestedValue`s (no cost, Global
  Constraints): `DISCOUNT → {"maxDiscountPct": <highest line pct>, "limit": <setting>}`;
  `BELOW_COST → {"skus": [..]}`; `CREDIT_LIMIT → {"exposure": .., "creditLimit": .. or null, "overdue": bool}`.
  One `CREDIT_LIMIT` entry even when both the limit and overdue rules fire.
- `OrderService.confirm(Scope, long id, TransitionRequest)`: `CONFIRM`; ≥ 1 line; QUOTED and expired →
  `QUOTE_EXPIRED`; `approvals.ensureAllApproved(checks.forConfirm(...))`; status `CONFIRMED`, `confirmedAt`; audit
  `CONFIRMED`.
- `OrderService.confirmAsSystem(long id)` (used by Task 13): same checks via `approvals.missing(...)` (no admin
  bypass — the decision's admin is still the security principal), returns the missing list; confirms only when
  empty; audit through `AuditLog.recordFor("SYSTEM", null, "System", CONFIRMED, "ORDER", id, "Auto-confirmed after approval")`.
- Endpoint `POST /api/orders/{id}/confirm`. `AuditAction` gains `CONFIRMED`.

- [ ] **Step 1: Write failing tests** in `ConfirmIT` (setting `approval.discountPercentMax` = 15):
  `discountOverMaxNeedsApproval` (16% → 422, `approvals[0].approvalType == "DISCOUNT"`);
  `belowCostNeedsApprovalWithoutLeakingCost` (cost 100, price 120 at 20% → `BELOW_COST`; response body has no
  `100`/`cost`); `exposureOverLimit` (limit 1000, other confirmed order balance 600, this 500 → `CREDIT_LIMIT`,
  `exposure == 1100.00`); `cashCustomerSkipsLimitButOverdueBlocks` (no limit, overdue other order → `CREDIT_LIMIT`);
  `allFailuresTogether` (three rules → `approvals.length() == 3`); `approvedThenConfirms` (insert APPROVED rows
  for the returned subject versions → 200 CONFIRMED); `editAfterApprovalNeedsNewApproval` (fingerprint changed →
  422 again); `adminConfirmsWithSelfApprovalAudit`; `expiredQuote422` (`quote_valid_until` = yesterday →
  `QUOTE_EXPIRED`); `confirmAsSystemIgnoresAdminPrincipal`.
- [ ] **Step 2:** Run `./gradlew test --tests '*ConfirmIT'` — Expected: FAIL.
- [ ] **Step 3:** Implement; matrix row; snapshot.
- [ ] **Step 4:** Run — Expected: PASS.
- [ ] **Step 5:** Commit `feat(sales): confirm with discount, below-cost and credit checks`.

### Task 9: Invoices — issue, gapless numbers, void and reissue, PDF

**Files:**
- Create: `sales/Invoice.java`, `sales/InvoiceStatus.java` (public), `sales/InvoiceRepository.java`,
  `sales/InvoiceService.java`, `sales/InvoiceController.java`, `sales/InvoiceDocument.java`,
  `sales/InvoiceResponse.java`, `sales/ReissueRequest.java`, `resources/templates/pdf/invoice.html`
- Modify: `sales/OrderService.java` (`activeInvoice` in responses), matrix, snapshot
- Test: `sales/InvoicesIT.java`, `sales/InvoiceNumberingIT.java`

**Interfaces:**
- `Invoice` maps `customer_snapshot` and `lines_snapshot` as `@JdbcTypeCode(SqlTypes.JSON)` (`Map<String, Object>` /
  `List<Map<String, Object>>`), as `ApprovalRequest.requestedValue` does.
- `InvoiceService`:
  - `InvoiceResponse issue(Scope, long orderId, TransitionRequest)` — `ISSUE_INVOICE`; no active invoice (else
    `INVALID_STATE`); company name/TIN/address non-blank (else 400 `VALIDATION_FAILED` with
    `errors[].field` = each missing setting key); `findByIdForUpdate`; number via
    `jdbc.queryForObject("update document_counter set next_value = next_value + 1 where name = 'INVOICE' returning next_value - 1", Long.class)`
    formatted `SI-%05d`; freeze snapshots and totals; `dueDate = today + paymentTerms.getDays()`; set
    `order.dueDate`; audit `INVOICE_ISSUED`.
  - `InvoiceResponse reissue(Scope, long orderId, ReissueRequest)` — `REISSUE_INVOICE`; requires
    `RequiredApproval(INVOICE_VOID, "INVOICE", active.id, fingerprint, {"invoice": number, "reason": reason})`;
    then `voidAndReplace`.
  - package-private `Invoice voidActive(SalesOrder o, String reason)` and
    `Invoice voidAndReplace(SalesOrder o, String reason)` (void old, issue new, set `replacedById`; used by Tasks 11
    and 12; the caller has already checked approval). Audit `INVOICE_VOIDED`.
  - `List<InvoiceResponse> list(Scope, long orderId)` newest first; `byte[] pdf(Scope, long invoiceId)` (scope via
    the order).
- `record InvoiceResponse(long id, String number, InvoiceStatus status, int orderRevision, Instant issuedAt, LocalDate dueDate, BigDecimal total, String voidReason, Instant voidedAt, Long replacedById)`
- `record ReissueRequest(@NotBlank String reason, @NotNull Long version)`
- `InvoiceDocument.render(Invoice i)` — fields §4 Documents; heading `SALES INVOICE`; void → large rotated
  `VOID` overlay and the void reason; footer = `invoice.footerText`.
- Endpoints: `POST /api/orders/{id}/invoice`, `POST /api/orders/{id}/invoice/reissue` (both require header
  `Idempotency-Key` — `@RequestHeader("Idempotency-Key") String key`, missing → 400; add both paths to
  `IdempotencyFilter.DEFAULT_PATHS`), `GET /api/orders/{id}/invoices`, `GET /api/invoices/{id}.pdf`.
- `AuditAction` gains `INVOICE_ISSUED`, `INVOICE_VOIDED`.

- [ ] **Step 1: Write failing tests**
  - `InvoicesIT`: `issueFreezesCustomerAndLines` (change customer TIN and product name after → `GET` and PDF text
    still show the old); `dueDateFromTerms` (NET_30 → today + 30); `secondIssueIs422`; `draftCannotBeInvoiced`;
    `missingCompanySettings400NamesKeys`; `salesReissueNeedsInvoiceVoidApproval` (422 `approvals[0].subjectType ==
    "INVOICE"`); `reissueVoidsAndKeepsOldNumber` (as ADMIN: old `VOID` with reason, new number = old + 1,
    `replacedById` set); `voidPdfShowsVoid`; `missingIdempotencyKey400`; `invoicePdfOfOtherRepIs404`.
  - `InvoiceNumberingIT`: `concurrentIssuesAreGaplessAndUnique` (12 confirmed orders, 12 threads issuing at once,
    plus one transaction that issues then `setRollbackOnly()` → resulting numbers are exactly consecutive with no
    duplicates and no gap).
- [ ] **Step 2:** Run `./gradlew test --tests '*InvoicesIT' --tests '*InvoiceNumberingIT'` — Expected: FAIL.
- [ ] **Step 3:** Implement; matrix rows; snapshot.
- [ ] **Step 4:** Run — Expected: PASS.
- [ ] **Step 5:** Commit `feat(sales): BIR invoices with gapless numbers and void/reissue`.

### Task 10: Payments — record, verify, reject, 2307

**Files:**
- Create: `sales/Payment.java`, `sales/PaymentMethod.java`, `sales/PaymentVerification.java` (public enums),
  `sales/PaymentRepository.java`, `sales/PaymentService.java`, `sales/PaymentController.java`,
  `sales/PaymentRequest.java`, `sales/PaymentResponse.java`, `sales/RejectRequest.java`
- Modify: `IdempotencyFilter.DEFAULT_PATHS`, matrix, snapshot
- Test: `sales/PaymentsIT.java`

**Interfaces:**
- `public enum PaymentMethod { CASH, BANK_TRANSFER, CHECK, GCASH, CARD, OTHER }`;
  `public enum PaymentVerification { PENDING, VERIFIED, REJECTED }`
- `record PaymentRequest(@NotNull PaymentMethod method, @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal cashAmount, @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal withholdingAmount, @NotNull @PastOrPresent LocalDate receivedOn, @Size(max = 100) String reference)`
  — cash + withholding must be > 0 (400 field `cashAmount`).
- `record PaymentResponse(long id, PaymentVerification status, PaymentMethod method, BigDecimal cashAmount, BigDecimal withholdingAmount, LocalDate receivedOn, String reference, String proofUrl, String form2307Url, String submittedByType, String submittedByName, Instant verifiedAt, String rejectReason, Instant createdAt, long version)`
  — URLs from `StorageService.presignedUrl`, null when no key.
- `record RejectRequest(@NotNull Long version, @NotBlank String reason)`
- `PaymentService` (all mutations take `orderRepo.findByIdForUpdate` first):
  - `PaymentResponse record(Scope, long orderId, PaymentRequest, MultipartFile proof)` — `RECORD_PAYMENT`;
    `VERIFIED` immediately; `settled + amount > total` → `OVERPAYMENT`; add to `order.settledAmount`; proof
    optional, stored under `payments/proof` with `StorageService.PROOF_TYPES`. Audit `PAYMENT_RECORDED`.
  - `PaymentResponse verify(Scope, long paymentId, long version)` — PENDING only (else `INVALID_STATE`); overpayment
    check; add to settled. Audit `PAYMENT_VERIFIED`.
  - `PaymentResponse reject(Scope, long paymentId, RejectRequest)` — PENDING only. Audit `PAYMENT_REJECTED`.
  - `PaymentResponse attach2307(Scope, long paymentId, MultipartFile file)` — any status but REJECTED; stored under
    `payments/2307`, `PROOF_TYPES`; replaces an earlier file. Audit `FORM_2307_ATTACHED`.
  - `List<PaymentResponse> list(Scope, long orderId)` newest first.
  - package-private `void rejectPendingFor(SalesOrder o, String reason)` (used by cancel, Task 11).
- Endpoints: `GET /api/orders/{id}/payments`; `POST /api/orders/{id}/payments` (multipart: part `payment` JSON +
  optional part `proof`; header `Idempotency-Key` required); `POST /api/payments/{id}/verify` (`TransitionRequest`);
  `POST /api/payments/{id}/reject`; `PUT /api/payments/{id}/form-2307` (multipart part `file`). Payment scope =
  its order's scope.
- `AuditAction` gains `PAYMENT_RECORDED`, `PAYMENT_SUBMITTED`, `PAYMENT_VERIFIED`, `PAYMENT_REJECTED`,
  `FORM_2307_ATTACHED`.

- [ ] **Step 1: Write failing tests** in `PaymentsIT` (MinIO via the existing `StorageServiceIT` container setup):
  `recordReducesBalance` (total 336.00, cash 300 → balance 36.00, `PARTIAL`); `withholdingSettles` (cash 332.64 +
  withholding 3.36 → `PAID`); `overpaymentRejected` (balance 36, pay 40 → 422 `OVERPAYMENT`);
  `racingPaymentsOneOverpays` (two threads each 200 on 336 → exactly one 200 and one 422; `settled_amount` 200.00);
  `pendingCountsOnlyAfterVerify` (seed a PENDING row by SQL); `rejectNeedsReason` (400); `verifyTwice422`;
  `draftOrderCannotTakePayment` (`INVALID_STATE`); `attach2307ShowsUrl`; `badProofType400` (`text/plain`);
  `suggestedWithholdingOnOrder` (BUSINESS, vatable 300.00 → `3.00`; INDIVIDUAL → `0.00`).
- [ ] **Step 2:** Run `./gradlew test --tests '*PaymentsIT'` — Expected: FAIL.
- [ ] **Step 3:** Implement; matrix rows; snapshot.
- [ ] **Step 4:** Run — Expected: PASS.
- [ ] **Step 5:** Commit `feat(sales): payments with withholding, verification and 2307`.

### Task 11: Release, fulfil, cancel

**Files:**
- Modify: `sales/OrderService.java`, `sales/ApprovalChecks.java`, `sales/OrderController.java`, matrix, snapshot
- Test: `sales/ReleaseCancelIT.java`

**Interfaces:**
- `ApprovalChecks.forRelease(SalesOrder o, CustomerSalesProfile c)` → empty when `creditLimit != null` or balance
  is 0; else `RequiredApproval(CREDIT_LIMIT, "ORDER_RELEASE", o.id, fingerprint, {"reason": "SHIP_BEFORE_PAYMENT", "balance": ..})`.
- `OrderService.release(Scope, long id, TransitionRequest)` — `RELEASE`; `ensureAllApproved(forRelease)`;
  `RELEASED`, `releasedAt`; audit `RELEASED`.
- `OrderService.fulfil(Scope, long id, TransitionRequest)` — `FULFIL`; ADMIN (`@salesPermissions.canFulfil()` and
  URL rule `POST /api/orders/*/fulfil` → ADMIN); audit `FULFILLED`.
- `OrderService.cancel(Scope, long id, TransitionRequest)` — `CANCEL`; blank `reason` → 400; any VERIFIED payment
  → `HAS_PAYMENTS`; with an active invoice: `ensureAllApproved(INVOICE_VOID on "INVOICE", invoice id, fingerprint)`
  then `invoices.voidActive(o, reason)`; `payments.rejectPendingFor(o, "Order cancelled")`; `CANCELLED`,
  `cancelledAt`, `cancelReason`; audit `CANCELLED`.
- Endpoints `POST /api/orders/{id}/release`, `/fulfil`, `/cancel`. `AuditAction` gains `RELEASED`, `FULFILLED`,
  `CANCELLED`.

- [ ] **Step 1: Write failing tests** in `ReleaseCancelIT`: `creditCustomerReleasesUnpaid`;
  `cashCustomerUnpaidNeedsReleaseApproval` (422, `subjectType == "ORDER_RELEASE"`); `confirmCreditApprovalDoesNotRelease`
  (APPROVED `CREDIT_LIMIT` on `ORDER` doesn't satisfy release); `cashCustomerPaidReleases`; `salesCannotFulfil` (403);
  `adminFulfilsReleased`; `cancelWithVerifiedPayment422`; `cancelRejectsPendingPayments`;
  `cancelInvoicedNeedsVoidApprovalThenVoids` (invoice `VOID`, no replacement); `cancelNeedsReason`;
  `cancelledAllowsNothing` (`allowedActions` empty).
- [ ] **Step 2:** Run `./gradlew test --tests '*ReleaseCancelIT'` — Expected: FAIL.
- [ ] **Step 3:** Implement; matrix rows (fulfil = ADMIN only); snapshot.
- [ ] **Step 4:** Run — Expected: PASS.
- [ ] **Step 5:** Commit `feat(sales): release gate, fulfil and cancel`.

### Task 12: Revise confirmed orders

**Files:**
- Create: `sales/OrderRevision.java`, `sales/OrderRevisionRepository.java`, `sales/RevisionResponse.java`
- Modify: `sales/OrderService.java`, `sales/OrderController.java`, matrix, snapshot
- Test: `sales/ReviseIT.java`

**Interfaces:**
- `record RevisionResponse(int revision, Map<String, Object> snapshot, String reason, String createdByName, Instant createdAt)`
  — `snapshot` holds `paymentTerms, shippingFee, notes, terms, lines[] {productId, sku, description, unit, qty, unitPrice, discountPct, taxClass, lineTotal}, totals`.
- `OrderService.revise(Scope, SalesOrder o, OrderRequest req)` called from `update` for CONFIRMED/RELEASED:
  1. blank `reason` → 400 field `reason`; version check;
  2. build the snapshot map of the current state (in memory);
  3. apply the request, recompute totals; new total < `settledAmount` → `OVERPAYMENT`;
  4. required = `checks.forConfirm(...)` + (active invoice ? `INVOICE_VOID` on `"INVOICE"`, invoice id, new
     fingerprint : nothing) → `approvals.ensureAllApproved(required)` (one 422 lists everything);
  5. save `order_revision(revision = old revision)`, `revision += 1`; active invoice → `invoices.voidAndReplace(o, reason)`;
  6. audit `REVISED` with the reason and the line summary diff.
  Any exception leaves the order untouched (single transaction).
- Endpoint `GET /api/orders/{id}/revisions` (newest first). `AuditAction` gains `REVISED`.

- [ ] **Step 1: Write failing tests** in `ReviseIT`: `reviseSnapshotsAndIncrements` (revision 0 → 1; revisions
  endpoint has the old qty); `reviseNeedsReason`; `reviseRerunsChecksAndSavesNothingOnFailure` (raise discount to
  20% → 422; `revision` still 0, lines unchanged); `approvedRevisionApplies` (approve the 422's subject version,
  resubmit identical request → 200); `invoicedReviseNeedsVoidThenReissues` (SALES gets `INVOICE_VOID` in
  `approvals`; as ADMIN → old invoice VOID, new number, balance keeps payments); `reviseBelowSettled422`
  (Review Focus: settled 300, new total 200 → `OVERPAYMENT`, nothing saved); `fulfilledCannotBeRevised`
  (`INVALID_STATE`).
- [ ] **Step 2:** Run `./gradlew test --tests '*ReviseIT'` — Expected: FAIL.
- [ ] **Step 3:** Implement; matrix row; snapshot.
- [ ] **Step 4:** Run — Expected: PASS.
- [ ] **Step 5:** Commit `feat(sales): revise confirmed orders with snapshots and reissue`.

### Task 13: Portal orders, accept/decline, portal payments, auto-confirm

**Files:**
- Create in `sales/`: `PortalOrderController.java`, `PortalOrderService.java`, `AutoConfirmListener.java`, records
  `PortalOrderSummary`, `PortalOrderResponse`, `PortalLine`, `PortalPaymentResponse`, `DeclineRequest`
- Modify: `notifications/NotificationType.java`, `PaymentService.java`, `IdempotencyFilter.DEFAULT_PATHS`, matrix,
  snapshot
- Test: `sales/PortalOrdersIT.java`, `sales/AutoConfirmIT.java`

**Interfaces:**
- Visibility: `customer_id = contact.customerId()` and `status <> 'DRAFT'`; otherwise 404. The actor is
  `CurrentActor.contact()` (absent → 401).
- `record PortalOrderSummary(long id, String number, OrderStatus status, PaymentStatus paymentStatus, BigDecimal total, BigDecimal balance, LocalDate quoteValidUntil, Instant quotedAt)`
- `record PortalLine(String description, ProductUnit unit, BigDecimal qty, BigDecimal unitPrice, BigDecimal discountPct, BigDecimal lineTotal)`
- `record PortalOrderResponse(long id, String number, OrderStatus status, PaymentStatus paymentStatus, LocalDate quoteValidUntil, boolean quoteExpired, boolean canAccept, PaymentTerms paymentTerms, BigDecimal shippingFee, String terms, List<PortalLine> lines, TotalsResponse totals, BigDecimal settled, BigDecimal balance, InvoiceSummary activeInvoice, Instant customerAcceptedAt, long version)`
  — no notes, rep, cost, approvals. `canAccept` = QUOTED, not expired, `customerAcceptedAt == null`.
- `record PortalPaymentResponse(long id, PaymentVerification status, PaymentMethod method, BigDecimal cashAmount, BigDecimal withholdingAmount, LocalDate receivedOn, String reference, String rejectReason, boolean hasForm2307, boolean mine)`
- `record DeclineRequest(@NotNull Long version, @Size(max = 500) String reason)`
- `PortalOrderService`:
  - `accept(ContactPrincipal, long id, TransitionRequest)` — `ACCEPT`; version check (Review Focus); expired →
    `QUOTE_EXPIRED`; set `customerAcceptedAt`, flush; `missing = orders.confirmAsSystem(id)`; when non-empty, for
    each `approvals.requestOnBehalf(new CreateApprovalRequest(type, subjectType, subjectId, subjectVersion, value, "Customer accepted via portal"), order.repId ?? order.createdBy)`
    and notify the rep `QUOTE_ACCEPTED` ("…awaiting approval"); when empty, notify `QUOTE_ACCEPTED` ("…confirmed").
    Audit `QUOTE_ACCEPTED` (actor CONTACT).
  - `decline(ContactPrincipal, long id, DeclineRequest)` — `DECLINE`; status → DRAFT, clear `customerAcceptedAt`;
    audit `QUOTE_DECLINED` with reason; notify rep `QUOTE_DECLINED`.
  - `submitPayment(ContactPrincipal, long id, PaymentRequest, MultipartFile proof)` → delegates to
    `PaymentService.submitFromPortal(...)` (new: proof required, `CUSTOMER_PROOF_TYPES`, status PENDING,
    `submittedByType = "CONTACT"`; no settled change; overpayment check against settled + other PENDING is **not**
    applied — verification does it); notify rep `PAYMENT_SUBMITTED`; audit `PAYMENT_SUBMITTED`.
  - `attach2307(ContactPrincipal, long paymentId, MultipartFile)` — only payments with `submittedByType = CONTACT`
    and `submittedById = contactId`, else 404.
  - `list`, `get`, `payments`, `quotePdf`, `invoicePdf` (active or void invoices of a visible order).
- `AutoConfirmListener`: `@TransactionalEventListener(phase = AFTER_COMMIT) @Transactional(propagation = REQUIRES_NEW)`
  on `ApprovalDecided` with `subjectType == "ORDER"` and status `APPROVED` → load the order; act only if QUOTED with
  `customerAcceptedAt` set and `subjectVersion == OrderFingerprint.of(order)`; `confirmAsSystem`; on success notify
  the rep `ORDER_CONFIRMED`. Idempotent by those guards.
- `NotificationType` gains `QUOTE_ACCEPTED`, `QUOTE_DECLINED`, `PAYMENT_SUBMITTED`, `ORDER_CONFIRMED`.
  `AuditAction` gains `QUOTE_ACCEPTED`, `QUOTE_DECLINED`.
- Endpoints: `GET /api/portal/orders`, `GET /api/portal/orders/{id}`, `POST /api/portal/orders/{id}/accept`,
  `POST /api/portal/orders/{id}/decline`, `GET|POST /api/portal/orders/{id}/payments` (POST multipart, header
  `Idempotency-Key` required), `PUT /api/portal/payments/{id}/form-2307`, `GET /api/portal/orders/{id}/quote.pdf`,
  `GET /api/portal/invoices/{id}.pdf`. (Portal URL rule `/api/portal/**` → CUSTOMER already exists.)

- [ ] **Step 1: Write failing tests**
  - `PortalOrdersIT`: `seesOnlyOwnCompanyNonDraft`; `otherCompanyOrderIs404`; `jsonHasNoCostNotesOrRep`;
    `acceptConfirmsWhenChecksPass`; `acceptWithFailingChecksCreatesApprovalsForRep` (2 PENDING requests,
    `requested_by` = rep, rep has a `QUOTE_ACCEPTED` notification); `staleAcceptIs409` (Review Focus);
    `expiredAccept422`; `declineReturnsToDraftAndHides` (then GET → 404); `portalPaymentPendingAndProofRequired`
    (no proof → 400; with proof → PENDING, rep notified); `contact2307OnlyOnOwnPayment` (staff-recorded payment →
    404); `voidInvoicePdfDownloadable`.
  - `AutoConfirmIT`: `approvingLastConfirms` (two approvals; approve one as ADMIN via `/api/approvals/{id}/decision`
    → still QUOTED; approve second → CONFIRMED, audit actor `SYSTEM`, rep has `ORDER_CONFIRMED`);
    `listenerIdempotent` (publish the same event twice → one CONFIRMED audit row); `editedOrderNotAutoConfirmed`
    (approval for an old fingerprint → stays QUOTED/DRAFT).
- [ ] **Step 2:** Run `./gradlew test --tests '*PortalOrdersIT' --tests '*AutoConfirmIT'` — Expected: FAIL.
- [ ] **Step 3:** Implement; matrix rows (CUSTOMER); snapshot.
- [ ] **Step 4:** Run — Expected: PASS.
- [ ] **Step 5:** Commit `feat(sales): portal quotes, payments and auto-confirm`.

### Task 14: Missing-2307 report, customer order/invoice lists, final sweep

**Files:**
- Create: `sales/SalesReports.java`, `sales/ReportController.java`, `sales/Missing2307Row.java`,
  `sales/CustomerSalesController.java`, `sales/CustomerInvoiceRow.java`
- Modify: matrix, snapshot
- Test: `sales/ReportsIT.java`

**Interfaces:**
- `record Missing2307Row(long paymentId, long orderId, String orderNumber, String customerName, String repName, LocalDate receivedOn, BigDecimal withholdingAmount)`
- `SalesReports.missing2307(Scope, Long customerId, Long repId, LocalDate from, LocalDate to, PageQuery)` — VERIFIED,
  `withholding_amount > 0`, `form2307_key is null`, order visible in scope; sorted `receivedOn` asc.
  `String missing2307Csv(...)` — same filter, all rows, header
  `Payment,Order,Customer,Rep,Received,Withholding`, RFC 4180 quoting; audit `EXPORTED` (`AuditAction` gains it) on
  entity `REPORT`.
- `record CustomerInvoiceRow(long id, String number, InvoiceStatus status, long orderId, String orderNumber, Instant issuedAt, LocalDate dueDate, BigDecimal total, PaymentStatus paymentStatus)`
- Endpoints: `GET /api/reports/missing-2307` → `PageResponse<Missing2307Row>`; `GET /api/reports/missing-2307.csv`
  → `text/csv; charset=UTF-8`, attachment `missing-2307.csv`; `GET /api/customers/{id}/orders` →
  `PageResponse<OrderSummary>`; `GET /api/customers/{id}/invoices` → `PageResponse<CustomerInvoiceRow>` (customer
  not visible → 404, via `Customers.salesProfileVisible`).

- [ ] **Step 1: Write failing tests** in `ReportsIT`: `listsOnlyVerifiedWithholdingWithout2307`;
  `salesSeesOnlyOwnCustomers`; `csvQuotesCommasAndIsAudited` (customer name `Dela Cruz, Inc.` → quoted; one
  `EXPORTED` audit row); `customerOrdersAndInvoicesScoped` (other rep → 404).
- [ ] **Step 2:** Run `./gradlew test --tests '*ReportsIT'` — Expected: FAIL.
- [ ] **Step 3:** Implement; matrix rows; snapshot.
- [ ] **Step 4:** Run `./gradlew test` (full suite) — Expected: all PASS, including `ModularityTest`,
  `SecurityMatrixTest`, `OpenApiSnapshotTest`.
- [ ] **Step 5:** Commit `feat(sales): missing-2307 report and customer sales lists`.

---

# Part B — Frontend (`crm-dashboard-ui`)

Every task: regenerate types first (`npm run gen:api` with the Global Constraints path), and finish with
`npx tsc -b && npm run lint && npm test` all passing.

### Task 15: Types, products screen, settings and customer fields

**Files:**
- Modify: `src/api/schema.d.ts` (generated), `src/app/shell/nav.ts`, `src/app/shell/nav.test.ts`,
  `src/app/router.tsx`, `src/features/admin/SettingsPage.tsx`, `src/features/admin/settingsSchema.ts`,
  `src/features/customers/customerSchema.ts`, `src/features/customers/CustomerDrawer.tsx`
- Create: `src/features/catalog/api.ts`, `ProductsPage.tsx`, `ProductDrawer.tsx`, `productSchema.ts`,
  `productSchema.test.ts`, `ProductsPage.test.tsx`

**Interfaces:**
- Nav: Sell gets `Orders` (`/orders`, ADMIN/SALES) and `Products` (`/products`, all staff); Insights gets
  `Missing 2307` (`/reports/missing-2307`, ADMIN/SALES). Routes for `/products` added here; `/orders*` and the
  report page in Tasks 16 and 19.
- `productSchema` (zod): sku 1–64, name 1–200, unit enum, price ≥ 0 with ≤ 2 decimals, cost optional ≥ 0,
  taxClass enum.
- `ProductsPage`: `DataTable` (sku, name, unit, price, tax class, and cost column only when the user is ADMIN);
  archived filter; ADMIN-only New/Edit (`ProductDrawer`) and Archive (`ConfirmDialog`).
- Settings page: section "Company & invoices" with the four text keys and the two `sales.*` numbers.
- Customer drawer: `taxTreatment` select (Regular / Zero-rated / VAT-exempt).

- [ ] **Step 1: Write failing tests** — `productSchema.test.ts` (rejects `price: 1.234`, negative cost, empty sku);
  `ProductsPage.test.tsx` (SALES: no Cost column, no New button; ADMIN: both); `nav.test.ts` (SALES labels
  `["Inbox", "Customers", "Orders", "Products", "Missing 2307"]`; WAREHOUSE `["Inbox", "Products"]`).
- [ ] **Step 2:** `npm test` — Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `npx tsc -b && npm run lint && npm test` — Expected: PASS.
- [ ] **Step 5:** Commit `feat(ui): products, sales settings, customer tax treatment`.

### Task 16: Orders list and new order

**Files:**
- Create: `src/features/sales/api.ts`, `OrdersPage.tsx`, `OrdersPage.test.tsx`, `NewOrderDialog.tsx`,
  `statusBadges.ts`
- Modify: `src/app/router.tsx`

**Interfaces:**
- `api.ts` wraps every staff endpoint from Tasks 6–14 (one function each, typed from `schema.d.ts`). Mutations that
  the backend requires it for send `Idempotency-Key: crypto.randomUUID()`, generated once per dialog open (a retry
  of the same submit reuses it).
- `statusBadges.ts`: `orderStatusColor(s: OrderStatus)` and `paymentStatusColor(s: PaymentStatus)` →
  `StatusBadge` colours (DRAFT gray, QUOTED blue, CONFIRMED indigo, RELEASED amber, FULFILLED green, CANCELLED red;
  UNPAID gray, PARTIAL amber, PAID green, OVERDUE red).
- `OrdersPage` `/orders`: `DataTable` with view tabs → query params: *Quotes* `status=DRAFT&status=QUOTED`,
  *To confirm* `awaitingConfirmation=true`, *To release* `status=CONFIRMED`, *Unpaid* `paymentStatus=UNPAID`,
  *Overdue* `paymentStatus=OVERDUE`, *All*. Columns per spec §6. Row → `/orders/:id`.
- `NewOrderDialog`: customer `Combobox` over `GET /api/customers?q=` → `POST /api/orders {customerId, lines: []}` →
  navigate to the workspace.

- [ ] **Step 1: Write failing tests** — `OrdersPage.test.tsx`: each view tab sends its params (mock `api`);
  `NewOrderDialog` posts and navigates.
- [ ] **Step 2–4:** `npm test` FAIL → implement → `npx tsc -b && npm run lint && npm test` PASS.
- [ ] **Step 5:** Commit `feat(ui): orders list and new order`.

### Task 17: Order workspace — header, lines, totals, approvals

**Files:**
- Create: `src/features/sales/OrderWorkspace.tsx`, `orderActions.ts`, `orderActions.test.ts`, `LinesTab.tsx`,
  `LinesTab.test.tsx`, `lineSchema.ts`, `lineSchema.test.ts`, `TotalsRail.tsx`, `ApprovalBanner.tsx`,
  `ApprovalBanner.test.tsx`, `ReasonDialog.tsx`
- Modify: `src/features/approvals/useApprovalRequired.ts` (+ test), `src/app/router.tsx`

**Interfaces:**
- `orderActions.ts`: `primaryAction(o: Order): ActionKey | undefined` — first of
  `SEND_QUOTE → CONFIRM → RELEASE → FULFIL` present in `o.allowedActions` (for QUOTED, CONFIRM before SEND_QUOTE);
  `menuActions(o: Order): ActionKey[]` — `ISSUE_INVOICE, REVISE, REISSUE_INVOICE, CANCEL` filtered by
  `allowedActions`. The server's `allowedActions` is the only source of truth; the UI never re-derives state rules.
- `useApprovalRequired` returns additionally `required: ApprovalDraft[]` built from the problem's `approvals[]`
  (falls back to the single top-level fields); `handle(e)` keeps its contract.
- `ApprovalBanner({required, onRequested})`: one row per draft with **Request approval** (opens the existing
  `RequestApprovalDialog`) and the status of matching requests from `GET /api/approvals?mine=true` (matched on type,
  subjectType, subjectId, subjectVersion).
- `LinesTab`: editable when `allowedActions` has `EDIT`, or in *Edit (revise)* mode when it has `REVISE` (save asks
  a reason through `ReasonDialog`). Product search = `Combobox` over `GET /api/products?q=`; `QuantityInput`,
  `MoneyInput`, discount %; `belowCost` shows a warning icon (ADMIN only, since only ADMIN gets the field).
  Saves the whole line list with `version` via `PUT /api/orders/{id}`.
- `lineSchema`: qty > 0 with ≤ 3 decimals, unitPrice ≥ 0 with ≤ 2, discount 0–100.
- `TotalsRail`: VATable sales, VAT, VAT-exempt, zero-rated, shipping, total, paid (`settled`), balance.
- Send quote shows a warning toast when `emailedTo` is empty: "Quote marked as sent, but this customer has no
  portal contacts — nothing was emailed." (Review Focus.)
- `OrderWorkspace` `/orders/:id`: header (number, customer link, `StageTracker` over the five statuses, both pills,
  primary button, "⋯" `Dropdown`), `ApprovalBanner`, `Tabs` (Lines | Invoice | Payments | Revisions | History —
  the last four filled in Task 18), `TotalsRail`.

- [ ] **Step 1: Write failing tests** — `orderActions.test.ts` (QUOTED with `[EDIT, SEND_QUOTE, CONFIRM, CANCEL]` →
  primary CONFIRM, menu `[CANCEL]`; CANCELLED → none); `useApprovalRequired.test.ts` (problem with 3 `approvals`
  → `required.length === 3`; old single shape → 1); `ApprovalBanner.test.tsx`; `LinesTab.test.tsx` (read-only
  without EDIT; revise asks a reason); `lineSchema.test.ts`.
- [ ] **Step 2–4:** FAIL → implement → `npx tsc -b && npm run lint && npm test` PASS.
- [ ] **Step 5:** Commit `feat(ui): order workspace with lines, totals and approvals`.

### Task 18: Workspace tabs — invoice, payments, revisions, history

**Files:**
- Create: `src/features/sales/InvoiceTab.tsx`, `PaymentsTab.tsx`, `PaymentDrawer.tsx`, `paymentSchema.ts`,
  `paymentSchema.test.ts`, `PaymentsTab.test.tsx`, `RevisionsTab.tsx`, `revisionDiff.ts`, `revisionDiff.test.ts`
- Modify: `OrderWorkspace.tsx`

**Interfaces:**
- `InvoiceTab`: `GET /api/orders/{id}/invoices`; active invoice card (number, issued, due, total, *View PDF* →
  `/api/invoices/{id}.pdf` in a new tab); VOID rows greyed with reason; *Issue invoice* / *Reissue* (reason via
  `ReasonDialog`) when allowed.
- `PaymentDrawer`: method, cash (`MoneyInput`), withholding prefilled with `order.suggestedWithholding` (editable,
  clearable), receivedOn (`DateInput`, default today Manila), reference, optional proof file → multipart
  `payment` (JSON blob) + `proof`.
- `paymentSchema`: cash ≥ 0, withholding ≥ 0, sum > 0, sum ≤ `balance` (message "More than the balance due").
- `PaymentsTab`: list with status badges, proof/2307 links, *Verify* / *Reject* (reason) on PENDING, *Upload 2307*.
- `revisionDiff.ts`: `diffLines(before: SnapshotLine[], after: SnapshotLine[]): LineDiff[]` keyed by `productId`
  (added / removed / changed with the changed fields).
- `RevisionsTab`: list of revisions; selecting one shows `diffLines(snapshot.lines, nextSnapshotOrCurrent.lines)`.
- History tab: reuse the customers History list component pattern against `GET /api/orders/{id}/history`.

- [ ] **Step 1: Write failing tests** — `paymentSchema.test.ts` (over balance rejected; 0 + 0 rejected);
  `PaymentsTab.test.tsx` (Verify only on PENDING); `revisionDiff.test.ts` (qty change, added, removed).
- [ ] **Step 2–4:** FAIL → implement → PASS.
- [ ] **Step 5:** Commit `feat(ui): invoice, payments, revisions and history tabs`.

### Task 19: Customer sales tabs and the Missing 2307 report

**Files:**
- Modify: `src/features/customers/CustomerPage.tsx` (+ test)
- Create: `src/features/sales/Missing2307Page.tsx`, `Missing2307Page.test.tsx`; route `/reports/missing-2307`

**Interfaces:**
- Customer page: *Orders* tab (`GET /api/customers/{id}/orders`, rows → workspace, *New order* prefilled with this
  customer) and *Invoices* tab (`GET /api/customers/{id}/invoices`, PDF link, payment status).
- `Missing2307Page`: filters customer, rep (ADMIN only), date range (`DateRangeInput`); `DataTable`; *Export CSV*
  downloads `/api/reports/missing-2307.csv` with the same params.

- [ ] **Step 1: Write failing tests** — Customer page shows both tabs; report sends filters and the CSV link carries
  them.
- [ ] **Step 2–4:** FAIL → implement → PASS.
- [ ] **Step 5:** Commit `feat(ui): customer orders/invoices tabs and missing-2307 report`.

### Task 20: Portal order screens

**Files:**
- Create: `src/features/portal/ordersApi.ts`, `PortalOrdersPage.tsx`, `PortalOrderPage.tsx`,
  `PortalPaymentForm.tsx`, `portalOrders.test.tsx`
- Modify: `src/app/router.tsx` (`/portal` → orders list, `/portal/orders/:id`), `PortalLayout.tsx` nav

**Interfaces:**
- `PortalOrdersPage`: list (number, status, balance, valid-until for quotes).
- `PortalOrderPage`: lines and totals; **Accept** (sends `version`; 409 → toast "This quote was updated — please
  review it again." and refetch) and **Decline** (optional reason) when `canAccept`; after accept, a pending note
  when the order is still QUOTED ("Accepted — waiting for confirmation"); quote/invoice PDF links;
  `PortalPaymentForm` (amount, withholding, date, reference, required proof file) and the payments list with
  status, reject reason, and *Upload 2307* on `mine` rows.

- [ ] **Step 1: Write failing tests** — Accept hidden when `canAccept` false; stale 409 shows the toast and refetches;
  payment form requires a file.
- [ ] **Step 2–4:** FAIL → implement → PASS.
- [ ] **Step 5:** Commit `feat(ui): portal orders, quote acceptance and payment proof`.

### Task 21: Sales end-to-end smoke

**Files:**
- Create: `e2e/sales.spec.ts` (reuse Foundation's Playwright helpers)

**Interfaces:**
- One scenario against the local stack (backend on the `redesign/sales` worktree, `docker-compose.dev.yml`
  services, Mailpit): ADMIN fills company settings → creates a product → creates an order for a NET_30 credit
  customer with one portal contact → Send quote → portal contact signs in by the Mailpit link → Accept → staff sees
  CONFIRMED → Issue invoice → record full payment → payment pill PAID → invoice PDF responds `application/pdf`.

- [ ] **Step 1:** Write the spec.
- [ ] **Step 2:** Start the stack per Foundation Task 25's preconditions, then `npm run e2e -- sales` — Expected:
  1 passed.
- [ ] **Step 3:** Commit `test(ui): sales lifecycle e2e smoke`.

---

## Explicitly deferred (not in this plan)

- Stock reservation, delivery orders, warehouse views of released orders — #3 (`RELEASED` is the hand-off).
- POS sales and the senior citizen/PWD discount — #4.
- Refunds, credit/debit memos, deposit/progress invoices, price lists, multi-currency, overdue reminder emails,
  portal quote requests — spec "Out of scope".
- BIR permit (PTU/CAS) — owner action before go-live; only the footer text is configurable here.
