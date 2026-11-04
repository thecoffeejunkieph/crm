# Dashboards (#4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One dashboard per role at `/insights` — owner sees the company, a sales rep sees only their own sales with
a monthly target bar and commission, the warehouse sees today's work — plus an admin screen to set targets.

**Architecture:** A new `insights` module in `crm` reads across tables with `NamedParameterJdbcTemplate` (the
`SalesReports` pattern), returns one record per role, cached 60s in Redis via the existing `CacheConfig`. A
`sales_target` table holds target and commission % per rep per month. `sales` gains two snapshots at confirm: the
order's `rep_id` refreshed from the customer, and `order_line.unit_cost` from the product. The UI adds
`src/features/insights/` built from the `src/ui` kit, charts via the installed `react-apexcharts`.

**Tech Stack:** Java 21, Spring Boot 4.0.6, Spring Modulith (`ModularityTest`), Postgres 17, Flyway, Redis cache,
Testcontainers (`@IntegrationTest`), MockMvc; React 19 + TypeScript, TanStack Query 5, Vitest, Playwright,
`openapi-typescript` (`npm run gen:api`).

**Spec:** `docs/superpowers/specs/2026-11-05-dashboards-design.md`

**Repos:** backend `crm/crm` (base `phase/major_refactor_ui`), frontend `crm-dashboard-ui` (base
`phase/major_refactor`). Work on a new branch `redesign/dashboards` in each. Backend test command:
`./gradlew test --tests '<pattern>'` (Gradle, not Maven). Frontend: `npx vitest run <path>`, `npm run e2e`.

## Global Constraints

- Amounts are VAT-inclusive pesos; money HALF_UP to 2 places as in `OrderTotals.money`. Money columns `numeric(19,2)`.
- "Month" = `YearMonth` in `Asia/Manila`; "today" = Manila today from the injected `Clock`.
- Collected = Σ `cash_amount + withholding_amount` of `payment.status = 'VERIFIED'` by Manila date of `verified_at`.
- Credit = `sales_order.rep_id`, refreshed from `customer.assigned_rep_id` at confirm, never changed afterwards.
- Commission = `collected × commission_pct / 100` (HALF_UP, 2 dp) iff target exists, `target_amount > 0` and
  `collected ≥ target_amount`; else `0.00`.
- Cost, `unit_cost` and margin appear only in `GET /api/insights/admin`. Never in order, line, POS or portal responses.
- Access: `/api/insights/sales` SALES only; `/admin` and `/targets*` ADMIN only; `/warehouse` WAREHOUSE + ADMIN.
- `month` query param: `YYYY-MM`, default current month; future, > 24 months back, or malformed →
  `400 INSIGHTS_BAD_MONTH`. Target for a non-SALES or inactive user → `422 INSIGHTS_NOT_A_REP`.
- Cache name `insights`, 60s TTL (existing `spring.cache.redis.time-to-live`); target writes evict all entries.
  Cached records implement `java.io.Serializable` (JDK serializer).
- No new Gradle or npm dependencies.
- Don't touch `OrderStateMachine`, `ApprovalChecks`, `StockLedger`.

## Review Focus

1. **Rep reassigned between create and confirm** → credit goes to the rep at confirm, not at create. Pinned in
   Task 1 (`confirmRefreshesRepFromCustomer`).
2. **Same month, other tests' data in the shared container** → company-wide figures must be asserted on a month no
   other test writes, or as deltas. Pinned in Task 4's fixture rule; every admin test follows it.
3. **Cached figure after a target edit** → the rep's bar reflects the new target on the next read, not 60s later.
   Pinned in Task 2 (`savingTargetEvictsCache`).
4. **A rep with no target this month** → `target: null`, commission `0.00`, no division by zero in `progressPct`.
   Pinned in Task 3 (`noTargetMeansNullAndZeroCommission`) and Task 6 (`noTargetShowsNoBar`).
5. **Line added by revising a confirmed order** → gets `unit_cost`, so margin doesn't silently skip it. Pinned in
   Task 1 (`reviseAddsCostToNewLine`).

---

## Backend (`crm/crm`)

### Task 1: Snapshots at confirm and the V10 migration

**Files:**
- Create: `src/main/resources/db/migration/V10__insights.sql`
- Modify: `sales/OrderLine.java` (field `BigDecimal unitCost`), `sales/OrderService.java` (`markConfirmed`, `revise`,
  new private `snapshotCosts`)
- Test: `src/test/java/ph/thecoffeejunkie/crm/sales/ConfirmIT.java`, `ReviseIT.java`, `PosCheckoutIT.java`,
  `AutoConfirmIT.java`

**Interfaces:**
- Produces: column `order_line.unit_cost numeric(19,2) null`; table `sales_target` exactly as the spec's
  "Data model" block (with `id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY` to match the schema's style);
  indexes on `sales_order (rep_id)` and `payment (status, verified_at)`; `OrderLine.unitCost`.

- [ ] **Step 1: Write failing tests.**
  - `ConfirmIT.confirmRefreshesRepFromCustomer` — order created for customer with rep A; `update customer set
    assigned_rep_id = B`; confirm as ADMIN → `select rep_id from sales_order` = B. Then `update customer set
    assigned_rep_id = A` → `rep_id` still B.
  - `ConfirmIT.confirmSnapshotsLineCost` — product cost `"60.00"`; confirm → `order_line.unit_cost = 60.00`; then
    `update product set cost = 99` → still `60.00`.
  - `ConfirmIT.unitCostNotInOrderResponse` — `GET /api/orders/{id}` body does not contain `"unitCost"`.
  - `ReviseIT.reviseAddsCostToNewLine` — confirmed order, revise adding a second product with cost `"10.00"` →
    new line `unit_cost = 10.00`, original line unchanged.
  - `AutoConfirmIT` — in the existing auto-confirm test, assert `unit_cost` is set on its lines.
  - `PosCheckoutIT` — in the existing happy-path test, assert `rep_id` equals the customer's rep and `unit_cost` set.
- [ ] **Step 2: Run** `./gradlew test --tests '*ConfirmIT' --tests '*ReviseIT' --tests '*PosCheckoutIT'` —
  Expected: FAIL (`column "unit_cost" does not exist`).
- [ ] **Step 3: Implement.** `V10__insights.sql` per the spec plus the backfill
  `UPDATE order_line l SET unit_cost = p.cost FROM product p, sales_order o WHERE l.product_id = p.id AND
  l.order_id = o.id AND o.status IN ('CONFIRMED','RELEASED','FULFILLED');`. `@Column(name = "unit_cost")` on
  `OrderLine.unitCost`. `markConfirmed(o)`: `o.repId = customers.salesProfile(o.customerId).assignedRepId()` then
  `snapshotCosts(o)` before the save. `snapshotCosts` uses `catalog.info(productIds)` (already used by the
  below-cost check) and fills only null `unitCost`. In `revise`, call `snapshotCosts(o)` after `applyLines`.
  Do not add `unitCost` to `LineResponse`.
- [ ] **Step 4: Run** the same command — Expected: PASS. Then `./gradlew test` — Expected: all green.
- [ ] **Step 5: Commit** `feat(sales): rep and line cost snapshot at confirm; V10 insights schema`

### Task 2: Insights module, month parsing and sales targets

**Files:**
- Create in `src/main/java/ph/thecoffeejunkie/crm/insights/`: `package-info.java` (module doc, like
  `fulfilment/package-info.java`), `InsightsMonth.java`, `InsightsPermissions.java`, `SalesTarget.java` (entity),
  `SalesTargetRepository.java`, `TargetRow.java`, `TargetRequest.java`, `TargetService.java`,
  `TargetController.java`, `Commission.java`
- Modify: `shared/ErrorCode.java`, `audit/AuditAction.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/insights/InsightsMonthTest.java`, `CommissionTest.java`,
  `TargetsIT.java`; `SecurityMatrixTest.java`; `OpenApiSnapshotTest` snapshot

**Interfaces:**
- Consumes: `Users.isActiveWithRole(long, Role)`, `Users.activeIdsWithRole(Role)`, `Users.displayNames(...)`,
  `AuditLog.record(...)`, `CurrentActor.scope()`.
- Produces:
  - `ErrorCode.INSIGHTS_BAD_MONTH(BAD_REQUEST)`, `ErrorCode.INSIGHTS_NOT_A_REP(UNPROCESSABLE_CONTENT)`;
    `AuditAction.SALES_TARGET_SET`.
  - `final class InsightsMonth { static YearMonth parse(String raw, Clock clock) }` — null/blank → current Manila
    month; throws `DomainException(INSIGHTS_BAD_MONTH, …)` on malformed, future, or before current minus 24 months.
    Also `static Instant start(YearMonth)`, `static Instant end(YearMonth)` (Manila month bounds, end exclusive).
  - `final class Commission { static BigDecimal of(BigDecimal collected, BigDecimal target, BigDecimal pct) }`
    (target/pct null → `0.00`); `static Integer progressPct(BigDecimal collected, BigDecimal target)` (null when
    target null or zero; floor of collected/target×100, not capped).
  - `@Component("insightsPermissions") InsightsPermissions` with `isAdmin()`, `isSales()`, `isAdminOrWarehouse()`
    (same shape as `SalesPermissions`).
  - `record TargetRow(long repId, String repName, BigDecimal targetAmount, BigDecimal commissionPct, Long version)
    implements Serializable` (nulls when no row).
  - `record TargetRequest(@NotNull @PositiveOrZero BigDecimal targetAmount, @NotNull @DecimalMin("0")
    @DecimalMax("100") BigDecimal commissionPct, Long version)`.
  - `TargetService`: `List<TargetRow> list(YearMonth)`, `TargetRow set(long repId, YearMonth, TargetRequest)`,
    `int copy(YearMonth from, YearMonth to)`; `Optional<SalesTarget> find(long repId, YearMonth)` for Tasks 3–4.
    `set` and `copy` are `@CacheEvict(cacheNames = "insights", allEntries = true)`.
  - Routes (all `@PreAuthorize("@insightsPermissions.isAdmin()")`): `GET /api/insights/targets?month=`,
    `PUT /api/insights/targets/{repId}/{month}` (path `month` also parsed by `InsightsMonth.parse`),
    `POST /api/insights/targets/copy?from=&to=` → `{"copied": n}`.

- [ ] **Step 1: Write failing unit tests.**
  - `InsightsMonthTest` (fixed `Clock` at `2026-11-05T16:30:00Z`, i.e. 2026-11-06 Manila):
    `blankIsCurrentManilaMonth` → `2026-11`; `parsesIso` `"2026-03"`; `futureRejected` `"2026-12"`;
    `twentyFiveMonthsBackRejected` `"2024-10"`; `twentyFourBackAllowed` `"2024-11"`; `malformedRejected` `"2026-3"`,
    `"abc"`; `startIsManilaMidnight` — `start(2026-11)` = `2026-10-31T16:00:00Z`.
  - `CommissionTest`: `belowTargetIsZero` (`9999.99`, `10000`, `5`) → `0.00`; `exactlyAtTargetPays`
    (`10000`, `10000`, `5`) → `500.00`; `roundsHalfUp` (`10000.10`, `10000`, `2.5`) → `250.00`
    (250.0025 → 250.00) and (`10000.30`, `10000`, `2.5`) → `250.01`; `noTargetIsZero`; `zeroTargetIsZero`;
    `progressNullWithoutTarget`; `progressFloors` (`3333`, `10000`) → `33`; `progressNotCapped` (`15000`,
    `10000`) → `150`.
- [ ] **Step 2: Run** `./gradlew test --tests '*InsightsMonthTest' --tests '*CommissionTest'` — Expected: FAIL
  (classes don't exist).
- [ ] **Step 3: Implement** `InsightsMonth`, `Commission`, the error codes.
- [ ] **Step 4: Run** the same — Expected: PASS.
- [ ] **Step 5: Write failing `TargetsIT`** (users via `SalesFixture.user`):
  - `listShowsEverySalesRepWithNulls` — two SALES users, no rows → both present, `targetAmount` null.
  - `putCreatesThenUpdatesWithVersion` — PUT `{targetAmount:"50000.00", commissionPct:"3.00", version:null}` → 200
    with version; PUT again with that version → 200; PUT with the old version → 409 `STALE_VERSION`.
  - `putForNonRep422` — WAREHOUSE user id → 422 `INSIGHTS_NOT_A_REP`.
  - `putRejectsPctOver100` → 400.
  - `copyFillsOnlyEmptyRows` — rep A has rows in Oct and Nov, rep B only in Oct → copy Oct→Nov returns 1; A's Nov
    unchanged.
  - `putWritesAudit` — `audit_event` row with action `SALES_TARGET_SET`.
  - `badMonth400` — `GET …/targets?month=2026-13` → 400 `INSIGHTS_BAD_MONTH`.
  - `savingTargetEvictsCache` — put a value into cache `insights` via the `CacheManager` bean, PUT a target, assert
    the cache entry is gone. (Task 3 adds the end-to-end variant.)
- [ ] **Step 6: Run** `./gradlew test --tests '*TargetsIT'` — Expected: FAIL (404).
- [ ] **Step 7: Implement** the entity (`@Version long version`, `updatedBy` from `actor.scope().userId()`),
  repository (`findByRepIdAndMonth`, `findByMonth`), service and controller. Audit summary:
  `"Set {repName}'s target for {Mon yyyy} to {amount} at {pct}%"`.
- [ ] **Step 8: Run** `TargetsIT` — Expected: PASS. Add the three routes to `SecurityMatrixTest.MATRIX` as `A`;
  run `./gradlew test --tests '*SecurityMatrixTest' --tests '*ModularityTest' --tests '*OpenApiSnapshotTest'`,
  regenerate the snapshot as that test's failure message says, re-run — Expected: PASS.
- [ ] **Step 9: Commit** `feat(insights): sales targets with commission rate per rep per month`

### Task 3: Sales rep dashboard

**Files:**
- Create in `insights/`: `InsightsQueries.java` (all SQL), `SalesInsights.java` (record + nested records),
  `InsightsService.java`, `InsightsController.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/insights/InsightsFixture.java`, `SalesInsightsIT.java`;
  `SecurityMatrixTest.java`; OpenAPI snapshot

**Interfaces:**
- Consumes: `InsightsMonth`, `Commission`, `TargetService.find`.
- Produces:
  - `record SalesInsights(YearMonth month, Target target, BigDecimal collected, Integer progressPct,
    BigDecimal remaining, boolean targetHit, BigDecimal commission, BigDecimal invoiced, BigDecimal outstanding,
    BigDecimal overdue, ToVerify toVerify, List<DayAmount> collectedByDay, NeedsAction needsAction,
    List<CustomerAmount> topCustomers) implements Serializable`, nested records `Target(amount, commissionPct)`,
    `ToVerify(long count, BigDecimal amount)`, `DayAmount(LocalDate date, BigDecimal amount)`,
    `NeedsAction(List<OrderRef> openQuotes, List<OrderRef> unpaidConfirmed, List<OrderRef> overdueInvoices)`,
    `OrderRef(long orderId, String number, String customerName, BigDecimal amount, LocalDate since)`,
    `CustomerAmount(long customerId, String name, BigDecimal collected)` — all `Serializable`. `remaining` =
    `max(target − collected, 0)` or null without target. JSON month serialises as `"2026-11"`.
  - `InsightsQueries` methods take `Long repId` (null = company-wide) so Task 4 reuses them:
    `BigDecimal collected(Long repId, YearMonth m)`, `BigDecimal invoiced(Long repId, YearMonth m)`,
    `Balance outstanding(Long repId, LocalDate today)` (`record Balance(BigDecimal outstanding, BigDecimal overdue)`),
    `ToVerify toVerify(Long repId)`, `List<DayAmount> collectedByDay(Long repId, YearMonth m)` (every day, zeros
    filled in Java), `List<CustomerAmount> topCustomers(Long repId, YearMonth m, int limit)`,
    `NeedsAction needsAction(long repId, LocalDate today, int limit)`.
  - `InsightsService.sales(long userId, YearMonth m)` —
    `@Cacheable(cacheNames = "insights", key = "'sales:' + #userId + ':' + #m")`.
  - `GET /api/insights/sales?month=` → `SalesInsights`, `@PreAuthorize("@insightsPermissions.isSales()")`, userId
    from `actor.scope().userId()`.

SQL definitions are the spec's "Figures" table. `needsAction`: `openQuotes` = status `QUOTED` oldest `updated_at`
first; `unpaidConfirmed` = status in (`CONFIRMED`,`RELEASED`) with `settled_amount < total`, oldest `confirmed_at`
first; `overdueInvoices` = orders with an `ISSUED` invoice, positive balance and `due_date < today`, oldest due first.
`amount` is the order total for quotes and the balance for the other two; `since` is the date sorted on.

- [ ] **Step 1: Write `InsightsFixture`** (test helper, `JdbcTemplate`, reuses `SalesFixture` for users, customers,
  products): `long order(long customerId, Long repId, String status, String total, String settled, LocalDate due)`,
  `long line(long orderId, long productId, String qty, String lineTotal, String unitCost)`,
  `long invoice(long orderId, String status, Instant issuedAt, String total)`,
  `long payment(long orderId, String status, String cash, String withholding, Instant verifiedAt)`. Read
  `V2__catalog_sales.sql` for the NOT NULL columns and fill them with fixed values. Fixture rule (Review Focus 2):
  company-wide assertions use `YearMonth.now(MANILA).minusMonths(20)` ("quiet month") or before/after deltas.
  `@BeforeEach` clears the `insights` cache via `CacheManager`.
- [ ] **Step 2: Write failing `SalesInsightsIT`** (rep A, rep B, month M = current month; data for rep A unless said):
  - `collectedCountsVerifiedOnlyByVerifiedDate` — VERIFIED 1000+20 withholding verified in M, PENDING 500, REJECTED
    300, VERIFIED 700 verified in M−1 → `collected = 1020.00`, `toVerify = {1, 500.00}`.
  - `lateVerifiedCountsInVerifyMonth` — VERIFIED 400 with `received_on` in M−1, `verified_at` on the 1st of M at
    00:30 Manila → counted in M, not M−1.
  - `onlyOwnOrders` — rep B's verified 999 → absent from rep A's `collected` and `topCustomers`.
  - `reassignedAfterConfirmStaysWithRep` — order `rep_id = A`, customer then reassigned to B → still in A's figures.
  - `invoicedExcludesVoid` — ISSUED 2000 and VOID 1500 in M → `invoiced = 2000.00`.
  - `outstandingAndOverdue` — order total 3000 settled 1000 due yesterday with ISSUED invoice → outstanding and
    overdue `2000.00`; same with due tomorrow → outstanding only.
  - `targetHitPaysCommission` — target 1000 at 5% for A in M, collected 1020 → `targetHit = true`,
    `commission = 51.00`, `progressPct = 102`, `remaining = 0.00`.
  - `belowTargetNoCommission` — target 5000 → `commission = 0.00`, `remaining = 3980.00`, `targetHit = false`.
  - `noTargetMeansNullAndZeroCommission` — `target = null`, `progressPct = null`, `commission = 0.00`.
  - `collectedByDayHasEveryDay` — length = days in M, the verify day's amount set, others `0.00`.
  - `needsActionListsOldestFive` — six QUOTED orders → 5, oldest first.
  - `noCostFieldsInResponse` — body contains none of `cost`, `margin`, `unitCost`.
  - `targetChangeVisibleImmediately` — GET (caches), PUT a target as ADMIN, GET → new target shown.
  - `adminAndWarehouseForbidden` — ADMIN and WAREHOUSE → 403.
- [ ] **Step 3: Run** `./gradlew test --tests '*SalesInsightsIT'` — Expected: FAIL (404).
- [ ] **Step 4: Implement** queries, service, controller. Manila date in SQL:
  `(p.verified_at AT TIME ZONE 'Asia/Manila')::date`; month filter by `verified_at >= :start AND verified_at < :end`
  using `InsightsMonth.start/end`.
- [ ] **Step 5: Run** — Expected: PASS. Add `GET /api/insights/sales` to `SecurityMatrixTest` with a new set
  `S = EnumSet.of(Who.SALES)`; refresh OpenAPI snapshot; run matrix + modularity + snapshot — Expected: PASS.
- [ ] **Step 6: Commit** `feat(insights): sales rep dashboard with target progress and commission`

### Task 4: Owner dashboard

**Files:**
- Create in `insights/`: `AdminInsights.java`
- Modify: `InsightsQueries.java`, `InsightsService.java`, `InsightsController.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/insights/AdminInsightsIT.java`; `SecurityMatrixTest.java`; snapshot

**Interfaces:**
- Consumes: Task 3's `InsightsQueries` methods with `repId = null`; `TargetService.find`/`list`; `Commission`.
- Produces:
  - `record AdminInsights(YearMonth month, BigDecimal collected, BigDecimal collectedPrevMonth, BigDecimal invoiced,
    Margin margin, BigDecimal outstanding, BigDecimal overdue, ToVerify toVerify, List<MonthAmount>
    collectedByMonth, List<RepRow> reps, List<ChannelAmount> byChannel, List<ProductRow> topProducts,
    List<CustomerAmount> topCustomers, Operations operations) implements Serializable`, with
    `Margin(BigDecimal amount, BigDecimal pct, long linesWithoutCost)`, `MonthAmount(YearMonth month, BigDecimal
    amount)`, `RepRow(Long repId, String name, BigDecimal target, BigDecimal commissionPct, BigDecimal collected,
    Integer progressPct, boolean targetHit, BigDecimal commission)`, `ChannelAmount(String channel, BigDecimal
    collected)`, `ProductRow(long productId, String name, BigDecimal revenue, BigDecimal margin)`,
    `Operations(long deliveriesPicking, long deliveriesDispatched, long transfersInTransit,
    long adjustmentsPendingApproval)`.
  - New queries: `Margin margin(YearMonth)`, `List<MonthAmount> collectedByMonth(YearMonth end, int months)`,
    `Map<Long, BigDecimal> collectedByRep(YearMonth)` (null key = unassigned), `List<ChannelAmount>
    collectedByChannel(YearMonth)` (both `B2B` and `POS` always present), `List<ProductRow> topProducts(YearMonth,
    int)`, `Operations operations()`.
  - `InsightsService.admin(YearMonth m)` — `@Cacheable(cacheNames = "insights", key = "'admin:' + #m")`.
  - `GET /api/insights/admin?month=` → `AdminInsights`, `@PreAuthorize("@insightsPermissions.isAdmin()")`.

Margin per the spec: lines of orders whose `ISSUED` invoice was issued in the month; line revenue = `line_total`
(net of discount, VAT-inclusive — `OrderTotals.compute` derives vatable sales from it), cost = `unit_cost × qty`,
the same basis the below-cost check compares (`netUnitPrice` vs `cost`). `pct` =
amount / Σ line_total of included lines × 100, 2 dp, null when that sum is zero. `reps` = every active SALES user
(even with zero) plus `repId = null, name = "Unassigned"` only when its collected > 0, sorted by collected desc.

- [ ] **Step 1: Write failing `AdminInsightsIT`** (quiet month Q unless said):
  - `companyCollectedAndPrevMonth` — verified 1000 in Q (rep A), 500 in Q (no rep), 300 in Q−1 →
    `collected = 1500.00`, `collectedPrevMonth = 300.00`.
  - `repsTableIncludesUnassignedAndZeroReps` — rep B with nothing still listed with `collected 0.00`;
    "Unassigned" row = 500.00; rep A with target 1000 at 2% → `commission 20.00`, `targetHit true`.
  - `marginSkipsLinesWithoutCost` — invoice issued in Q; lines (`line_total` 1000, cost 600, qty 1) and
    (`line_total` 500, cost null) → `margin.amount = 400.00`, `pct = 40.00`, `linesWithoutCost = 1`.
  - `marginIgnoresVoidedInvoice` — order whose only invoice in Q is VOID contributes nothing.
  - `collectedByMonthHasTwelveEndingAtMonth` — 12 entries, last = Q, oldest = Q−11.
  - `byChannelAlwaysBoth` — POS verified 200 in Q → `[{B2B,1500.00},{POS,200.00}]` (order by channel name).
  - `topProductsByRevenue` — two products, higher revenue first, margin per product.
  - `operationsCountsAreDeltas` — read before, insert one PICKING delivery order, one DISPATCHED transfer, one
    PENDING_APPROVAL adjustment, read after → each count +1 (current month, delta rule).
  - `salesAndWarehouseForbidden` → 403.
- [ ] **Step 2: Run** `./gradlew test --tests '*AdminInsightsIT'` — Expected: FAIL (404).
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** — Expected: PASS. Add route to matrix as `A`; refresh snapshot; run matrix + modularity +
  snapshot — Expected: PASS.
- [ ] **Step 5: Commit** `feat(insights): owner dashboard with margin, reps and operations`

### Task 5: Warehouse dashboard

**Files:**
- Create in `insights/`: `WarehouseInsights.java`
- Modify: `InsightsQueries.java`, `InsightsService.java`, `InsightsController.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/insights/WarehouseInsightsIT.java`; `SecurityMatrixTest.java`; snapshot

**Interfaces:**
- Consumes: `Scope.warehouseIds()`, `Scope.role()`.
- Produces: `record WarehouseInsights(Today today, Transfers transfers, List<DeliveryRef> queue) implements
  Serializable`, `Today(long toPick, long dispatched, long deliveredToday)`, `Transfers(long toDispatch,
  long incoming)`, `DeliveryRef(long id, String number, String customerName, String status, Instant since)`;
  `InsightsService.warehouse(Set<Long> warehouseIds /* null = all */, LocalDate today)` —
  `@Cacheable(cacheNames = "insights", key = "'wh:' + (#warehouseIds == null ? 'all' : new java.util.TreeSet(#warehouseIds)) + ':' + #today")`;
  `GET /api/insights/warehouse` → `@PreAuthorize("@insightsPermissions.isAdminOrWarehouse()")`; ADMIN passes null.

Definitions: `toPick` = `delivery_order.status = 'PICKING'`; `dispatched` = `'DISPATCHED'`; `deliveredToday` =
`'DELIVERED'` with Manila date of `delivered_at` = today; `toDispatch` = `stock_transfer.status = 'DRAFT'` and
`from_warehouse_id` in set; `incoming` = `'DISPATCHED'` and `to_warehouse_id` in set; `queue` = 10 oldest
PICKING/DISPATCHED by `created_at`, `since` = `created_at`. A WAREHOUSE user with an empty set gets all zeros.

- [ ] **Step 1: Write failing `WarehouseInsightsIT`** (two warehouses W1, W2; user U assigned to W1; counts as deltas):
  - `countsOnlyAssignedWarehouses` — PICKING in W1 and W2 → U's `toPick` +1; ADMIN's +2.
  - `deliveredTodayUsesManilaDate` — DELIVERED at today 00:30 Manila counted; yesterday 23:30 Manila not.
  - `transfersFromAndTo` — DRAFT W1→W2 counts as U's `toDispatch`; DISPATCHED W2→W1 counts as `incoming`.
  - `queueOldestTenNoMoney` — 11 open delivery orders → 10, oldest first; body has no `total`/`amount` fields.
  - `salesForbidden` → 403.
- [ ] **Step 2: Run** `./gradlew test --tests '*WarehouseInsightsIT'` — Expected: FAIL (404).
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** — Expected: PASS. Matrix row `AW`; refresh snapshot; run matrix + modularity + snapshot, then
  the whole suite `./gradlew test` — Expected: all green.
- [ ] **Step 5: Commit** `feat(insights): warehouse dashboard`

---

## Frontend (`crm-dashboard-ui`)

### Task 6: API module and target card

**Files:**
- Modify: `src/api/schema.d.ts` (regenerated)
- Create: `src/features/insights/api.ts`, `src/features/insights/TargetCard.tsx`, `TargetCard.test.tsx`,
  `src/features/insights/MonthPicker.tsx`
- Test: `src/features/insights/TargetCard.test.tsx`

**Interfaces:**
- Consumes: Tasks 2–5 OpenAPI schemas `SalesInsights`, `AdminInsights`, `WarehouseInsights`, `TargetRow`,
  `TargetRequest`.
- Produces (`api.ts`, same style as `features/pos/api.ts`): types `SalesInsights`, `AdminInsights`,
  `WarehouseInsights`, `TargetRow`, `TargetRequest`; `fetchSalesInsights(month?: string)`,
  `fetchAdminInsights(month?: string)`, `fetchWarehouseInsights()`, `fetchTargets(month: string)`,
  `saveTarget(repId: number, month: string, body: TargetRequest)`, `copyTargets(from: string, to: string)`;
  query keys `["insights", role, month]` and `["insights","targets", month]`.
  `TargetCard({data}: {data: Pick<SalesInsights, "target"|"collected"|"progressPct"|"remaining"|"targetHit"|"commission">})`.
  `MonthPicker({value, onChange}: {value: string; onChange: (m: string) => void})` — native `<input type="month">`
  with `max` = current month and `min` = current minus 24 months.

- [ ] **Step 1:** `npm run gen:api` (backend `openapi.json` from Task 5) — Expected: `schema.d.ts` has the four
  schemas; `npx tsc -b` clean.
- [ ] **Step 2: Write failing `TargetCard.test.tsx`:**
  - `noTargetShowsNoBar` — `target: null` → text "No target set", no `role="progressbar"`.
  - `belowTargetShowsRemaining` — collected 3980 vs 5000 → progressbar `aria-valuenow=79`, "₱1,020.00 to go",
    commission "₱0.00".
  - `exactlyHitShowsBadge` — collected 5000 vs 5000 at 5% → "Target hit", commission "₱250.00".
  - `overTargetCapsBarAt100` — progressPct 150 → bar width 100%, label "150%".
- [ ] **Step 3: Run** `npx vitest run src/features/insights/TargetCard.test.tsx` — Expected: FAIL (module missing).
- [ ] **Step 4: Implement** with the kit's money formatter (whatever `MoneyInput`/order pages use for ₱ display).
- [ ] **Step 5: Run** — Expected: PASS.
- [ ] **Step 6: Commit** `feat(insights): api module, target card, month picker`

### Task 7: Insights page for sales and warehouse, routing and nav

**Files:**
- Create: `src/features/insights/InsightsPage.tsx`, `SalesDashboard.tsx`, `WarehouseDashboard.tsx`,
  `InsightsPage.test.tsx`
- Modify: `src/app/router.tsx`, `src/app/router.test.tsx`, `src/app/shell/nav.ts`, `src/app/shell/nav.test.ts`,
  `src/app/shell/MorePage.test.tsx`

**Interfaces:**
- Consumes: Task 6 api, `TargetCard`, `MonthPicker`; `useAuth()` role from `AuthProvider`.
- Produces: `InsightsPage` switching on role: SALES → `SalesDashboard`, WAREHOUSE → `WarehouseDashboard`, ADMIN →
  `WarehouseDashboard` for now (the endpoint allows ADMIN; Task 8 swaps in `AdminDashboard`).
  Route `{path: "/insights", element: <InsightsPage/>}` in the all-staff block; `/` → `<Navigate to="/insights"
  replace/>`. Nav: replace the Insights group with `Dashboard` (`/insights`, roles ALL, chart-like icon from
  `../../icons` — pick an existing one) followed by the existing `Missing 2307` item.

- [ ] **Step 1: Write failing tests.**
  - `router.test.tsx`: `rootRedirectsToInsights` (replace the old `/inbox` expectation).
  - `nav.test.ts`: every role's nav contains `Dashboard → /insights`.
  - `InsightsPage.test.tsx` (mock `api.ts`):
    `salesShowsTargetCardAndKpis` — SALES role, fixture `SalesInsights` → "Collected", "Invoiced", "Outstanding",
    "To verify" values, TargetCard present, needs-action order links to `/orders/{id}`;
    `salesMonthPickerRefetches` — change month → `fetchSalesInsights` called with `"2026-10"`;
    `emptyMonthMessage` — all zeros and empty lists → "No sales recorded";
    `warehouseShowsTodayAndQueue` — WAREHOUSE fixture → three counters, queue links to `/delivery-orders/{id}`,
    no ₱ anywhere in the page text;
    `loadingShowsSkeleton`.
- [ ] **Step 2: Run** `npx vitest run src/features/insights src/app` — Expected: FAIL.
- [ ] **Step 3: Implement.** Add `src/features/insights/AmountBars.tsx` —
  `AmountBars({points}: {points: {label: string; amount: number}[]})`, a `react-apexcharts` bar chart coloured from
  the redesign tokens (read `--color-brand-500` with `getComputedStyle` so dark mode follows), default-exported and
  loaded with `React.lazy` so ApexCharts stays out of the main bundle. Tests mock it. Collected-by-day uses it.
- [ ] **Step 4: Run** the same — Expected: PASS. `npx tsc -b` and `npm run lint` clean.
- [ ] **Step 5: Commit** `feat(insights): dashboard page for sales and warehouse; / lands on /insights`

### Task 8: Owner dashboard page

**Files:**
- Create: `src/features/insights/AdminDashboard.tsx`, `AdminDashboard.test.tsx`
- Modify: `src/features/insights/InsightsPage.tsx` (ADMIN branch → `AdminDashboard`)

**Interfaces:**
- Consumes: `fetchAdminInsights`, `MonthPicker`, Task 7's lazy `AmountBars`. Operations come from
  `AdminInsights.operations`.

- [ ] **Step 1: Write failing `AdminDashboard.test.tsx`** (mock api):
  - `kpisIncludingMargin` — Collected, Invoiced, Margin "₱400.00 · 40.00%", Outstanding / Overdue, To verify.
  - `linesWithoutCostWarning` — `linesWithoutCost: 3` → "3 lines without cost".
  - `repsTableRowsAndUnassigned` — rep rows with target, %, commission, "Target hit" badge; "Unassigned" row has no
    target cell value.
  - `vsLastMonthDelta` — collected 1500, prev 1000 → "+50%".
  - `operationsLink` — counts link to `/delivery-orders`, `/transfers`, `/inbox`.
  - `adminRoleRendersAdminDashboard` in `InsightsPage.test.tsx`.
- [ ] **Step 2: Run** `npx vitest run src/features/insights` — Expected: FAIL.
- [ ] **Step 3: Implement.** Reps table with the kit `DataTable`; on phones (< 768px) a stacked list (follow the
  list pattern from the redesign's list screens). 12-month bars via the same lazy chart as Task 7.
- [ ] **Step 4: Run** — Expected: PASS; `npx tsc -b`, `npm run lint` clean.
- [ ] **Step 5: Commit** `feat(insights): owner dashboard`

### Task 9: Sales targets admin page

**Files:**
- Create: `src/features/insights/SalesTargetsPage.tsx`, `SalesTargetsPage.test.tsx`
- Modify: `src/app/router.tsx` (ADMIN block: `/admin/sales-targets`), `src/app/shell/nav.ts` (Admin group:
  "Sales targets", ADMIN), `src/app/shell/MorePage.test.tsx`

**Interfaces:**
- Consumes: `fetchTargets`, `saveTarget`, `copyTargets`, `MonthPicker`, kit `MoneyInput`, `toastError`.

- [ ] **Step 1: Write failing `SalesTargetsPage.test.tsx`:**
  - `listsRepsWithEmptyRows` — two reps, one null → empty inputs.
  - `saveRowSendsVersion` — edit target "50000", pct "3", Save → `saveTarget(id, "2026-11", {targetAmount:"50000.00",
    commissionPct:"3.00", version: 4})`, then refetch.
  - `staleVersionReloadsRow` — `saveTarget` rejects with 409 → message "Someone else changed this target", row
    refetched.
  - `copyFromLastMonth` — click → `copyTargets("2026-10","2026-11")`, toast "Copied 2 targets".
  - `pctAbove100Blocked` — Save disabled with "Max 100%".
  - nav/More: "Sales targets" visible for ADMIN only.
- [ ] **Step 2: Run** `npx vitest run src/features/insights/SalesTargetsPage.test.tsx src/app` — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** — Expected: PASS; tsc, lint clean.
- [ ] **Step 5: Commit** `feat(insights): sales targets admin page`

### Task 10: End-to-end smoke

**Files:**
- Create: `e2e/insights.spec.ts` (helpers from `e2e/helpers`, same setup as `pos.spec.ts`)

- [ ] **Step 1: Write `insights.spec.ts`:** `targetBarMovesWhenPaymentVerified` — admin opens
  `/admin/sales-targets`, sets the seeded SALES user's target for this month to the next payment's amount at 5%;
  admin records a payment on an invoiced order whose customer is assigned to that rep; sign in as the rep → `/`
  lands on `/insights`, "Target hit" visible, commission = 5% of collected. Second test `warehouseLandsOnInsights` —
  warehouse user → `/` shows the warehouse dashboard.
- [ ] **Step 2: Run** `npm run e2e -- insights` against the dev stack (backend on `redesign/dashboards`) —
  Expected: PASS. Then the full `npm run e2e` and `npm test` — Expected: all green.
- [ ] **Step 3: Commit** `test(e2e): insights smoke`
