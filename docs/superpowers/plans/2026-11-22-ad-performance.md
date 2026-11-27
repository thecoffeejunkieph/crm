# Ad Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Admins record monthly ad spend per lead source and see a cohort report of customers acquired per month/source against that spend.

**Architecture:** A new `ad_spend` table owned by the `insights` module (same shape and pattern as `sales_target`), a spend CRUD API, and one read-only native SQL report endpoint. A new admin page in `crm-dashboard-ui` `features/insights` with a spend grid and the cohort table.

**Tech Stack:** Spring Boot, Flyway, Postgres, `NamedParameterJdbcTemplate`, JPA, Testcontainers ITs; React, Vite, TanStack Query, Vitest.

**Spec:** `docs/superpowers/specs/2026-11-22-ad-performance-design.md`

## Global Constraints

- All endpoints `ADMIN` only (`@PreAuthorize("hasRole('ADMIN')")` on the controller, like `TargetController`).
- Months are `YYYY-MM` strings in the API; computed in `Asia/Manila` (`InsightsMonth.MANILA`, `InsightsQueries.MANILA_DATE`).
- Cohort month = month of `lead.created_at` via `customer.converted_from_lead_id`, else month of `customer.created_at`.
- Value = Σ `money_movement.cash_amount + withholding_amount` over the customer's orders (same as `SalesOrderRepository.lifetimeValue`).
- Source on a row = the customer's **current** `source_id`; customers/leads with null source are excluded everywhere.
- `insights` reads other modules' tables through SQL only. Do not inject `customers`/`leads` beans (ModularityTest). Note: ModularityTest already fails on baseline; compare against `git stash`, don't treat red as proof.
- Gradle in Claude's shell: `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/Users/Public/gtmp` and `--no-daemon`; if `build/` is locked, add `-I` init script setting `layout.buildDirectory = file('C:/Users/Public/gbuild')`.
- Do not commit `src/main/resources/application.properties` (user's unrelated uncommitted change).

## Review Focus

1. Lead created 2026-10-31T16:30Z (= Nov 1 Manila) → counts in **November**, not October. (Task 2 test `cohortMonthUsesManilaTime`.)
2. Customer whose lead had source A but customer now has source B → counts under **B**. (Task 2 test `usesCustomersCurrentSource`.)
3. Spend entered for a month with no leads and no customers → row still appears with `newCustomers = 0`, `cohortValue = 0`, `roas = 0`. (Task 2 test `spendOnlyRowAppears`.)
4. Range crossing a year (`2026-11`..`2027-01`) → months returned in chronological order. (Task 2 test `monthsOrderedAcrossYear`.)
5. Shared test DB holds other tests' customers with sources → every IT assertion filters by the test's own tagged source ids, never asserts global counts.

---

### Task 1: Spend storage and API

**Files:**
- Create: `src/main/resources/db/migration/V27__ad_spend.sql`
- Create: `src/main/java/ph/thecoffeejunkie/crm/insights/AdSpend.java` (JPA entity, mirror `SalesTarget`)
- Create: `src/main/java/ph/thecoffeejunkie/crm/insights/AdSpendRepository.java`
- Create: `src/main/java/ph/thecoffeejunkie/crm/insights/AdSpendRow.java`, `AdSpendRequest.java`
- Create: `src/main/java/ph/thecoffeejunkie/crm/insights/AdSpendService.java`, `AdSpendController.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/audit/AuditAction.java` (add `AD_SPEND_SET`)
- Modify: `src/test/java/ph/thecoffeejunkie/crm/insights/InsightsFixture.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/insights/AdSpendIT.java`

**Interfaces:**
- Produces (fixture, used by Task 2):
  - `long source(String name)` → inserts `lead_source` with name `tag + name`
  - `long lead(String name, long sourceId, Instant createdAt)` → inserts `lead` (contact_name `tag + name`, email set, `created_by` = `creator()`)
  - `long customer(String name, Long sourceId, Long leadId, Instant createdAt)` → inserts customer with `source_id`, `converted_from_lead_id`, `created_at`; existing `customer(name, repId)` unchanged
  - `cleanUp()` additionally: delete `ad_spend` for tagged sources, `update customer set converted_from_lead_id = null` for tagged customers, delete tagged leads, then (after customers) delete tagged `lead_source`.
- Produces (API):
  - `record AdSpendRow(long sourceId, String sourceName, boolean archived, BigDecimal amount /* null if none */)`
  - `record AdSpendRequest(@NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal amount)`
  - `GET /api/insights/ad-spend?month=` → `List<AdSpendRow>`: non-archived sources + archived ones with spend that month, sorted by name case-insensitive. Month via `InsightsMonth.parseForPlanning`.
  - `PUT /api/insights/ad-spend/{sourceId}/{month}` → `AdSpendRow` (upsert, last write wins, no version check)
  - `DELETE /api/insights/ad-spend/{sourceId}/{month}` → 204, no-op if absent
  - Unknown `sourceId` on PUT → `DomainException(ErrorCode.NOT_FOUND, "Lead source not found.")`
  - Source names/existence read through `NamedParameterJdbcTemplate` on `lead_source`, not `LeadSources`.

- [ ] **Step 1: Write the migration** — table exactly as in the spec's Data section.

- [ ] **Step 2: Add fixture helpers** listed above.

- [ ] **Step 3: Write failing `AdSpendIT`** (extends `IntegrationTest`, uses `InsightsFixture`, `as(adminId, Role.ADMIN)`):
  - `putThenGetShowsAmount`: PUT `{"amount": 15000}` for source S, month `2026-11` → 200, `amount == 15000.00`; GET month `2026-11` → row for S has `amount` 15000.00.
  - `putOverwrites`: PUT 15000 then 9000 → GET shows 9000.00; one row in `ad_spend` for (S, 2026-11-01).
  - `deleteClears`: PUT then DELETE → 204; GET row for S has `amount` null. DELETE again → 204.
  - `archivedSourceShownOnlyWithSpend`: archived source A with spend in 2026-11 appears in GET 2026-11 with `archived: true`; absent in GET 2026-10.
  - `negativeAmountRejected`: `{"amount": -1}` → 400.
  - `unknownSource404`: PUT to sourceId `999999999` → 404.
  - `salesForbidden`: SALES user → 403 on GET, PUT, DELETE.

- [ ] **Step 4: Run, expect FAIL**
  Run: `gradlew test --no-daemon --tests '*AdSpendIT'`
  Expected: FAIL (404 on endpoints / missing table).

- [ ] **Step 5: Implement** entity, repository (`findBySourceIdAndMonth`, `findByMonth`, `deleteBySourceIdAndMonth`), service, controller. Service audits every PUT/DELETE with `AuditAction.AD_SPEND_SET`, entity type `"AD_SPEND"`, message `"Set <source> ad spend for MMM yyyy to <amount>"` / `"Cleared <source> ad spend for MMM yyyy"`.

- [ ] **Step 6: Run, expect PASS**
  Run: `gradlew test --no-daemon --tests '*AdSpendIT'`

- [ ] **Step 7: Commit**
  `git add src/main/resources/db/migration/V27__ad_spend.sql src/main/java/ph/thecoffeejunkie/crm/insights/AdSpend*.java src/main/java/ph/thecoffeejunkie/crm/audit/AuditAction.java src/test/java/ph/thecoffeejunkie/crm/insights/InsightsFixture.java src/test/java/ph/thecoffeejunkie/crm/insights/AdSpendIT.java`
  `git commit -m "feat(insights): monthly ad spend per lead source"`

---

### Task 2: Ad performance report

**Files:**
- Create: `src/main/java/ph/thecoffeejunkie/crm/insights/AdPerformance.java` (records)
- Create: `src/main/java/ph/thecoffeejunkie/crm/insights/AdPerformanceService.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/insights/InsightsQueries.java` (add query method)
- Modify: `src/main/java/ph/thecoffeejunkie/crm/insights/AdSpendController.java` (add GET)
- Modify: `openapi.json` (snapshot)
- Test: `src/test/java/ph/thecoffeejunkie/crm/insights/AdPerformanceIT.java`

**Interfaces:**
- Consumes: Task 1 fixture helpers; `InsightsFixture.payment(orderId, "VERIFIED", cash, wht, verifiedAt)`, `refund(orderId, amount, paidAt)`, `order(...)`.
- Produces:
  - `record AdPerformance(List<Month> months)`
  - `record AdPerformance.Month(String month /* YYYY-MM */, Row total, List<Row> rows)`
  - `record AdPerformance.Row(Long sourceId, String sourceName, BigDecimal spend, long leads, long newCustomers, BigDecimal cohortValue, BigDecimal costPerCustomer, BigDecimal valuePerCustomer, BigDecimal roas)` — total row has `sourceId`/`sourceName` null.
  - `List<InsightsQueries.AdCohortRow> adCohorts(LocalDate fromMonth, LocalDate toMonth)` with `record AdCohortRow(LocalDate month, long sourceId, String sourceName, BigDecimal spend, long leads, long newCustomers, BigDecimal cohortValue)`
  - `GET /api/insights/ad-performance?from=YYYY-MM&to=YYYY-MM` → `AdPerformance`

- [ ] **Step 1: Write failing `AdPerformanceIT`**. Every assertion picks rows by the test's own source ids.
  - `leadMonthWins`: source F; lead created `at(2026-10, 24)`; customer created `at(2026-12, 24)` with that lead; verified payment 5000 → `2026-10` row F: `leads 1, newCustomers 1, cohortValue 5000.00`; no F row in `2026-12`.
  - `directCustomerUsesCreatedMonth`: customer without lead created in 2026-11 → counts in `2026-11`.
  - `refundsReduceValue`: payment 5000, paid refund 1000 → `cohortValue 4000.00`.
  - `ratios`: spend 10000, 2 customers, values 6000 + 9000 → `costPerCustomer 5000.00`, `valuePerCustomer 7500.00`, `roas 1.50`.
  - `nullsWhenMissing`: no spend → `spend`, `costPerCustomer`, `roas` null; spend 0 → `roas` null, `costPerCustomer 0.00`; leads but zero customers → `costPerCustomer`, `valuePerCustomer` null.
  - `cohortMonthUsesManilaTime`: lead at `2026-10-31T16:30:00Z` → November.
  - `usesCustomersCurrentSource`: lead source A, customer source B → counted under B, A row has `leads 1, newCustomers 0`.
  - `spendOnlyRowAppears`: spend 3000, nothing else → row present, `newCustomers 0`, `cohortValue 0.00`, `roas 0.00`.
  - `monthsOrderedAcrossYear`: `from=2026-11&to=2027-01` → months `["2026-11","2026-12","2027-01"]` (months with no rows still listed, `rows` empty, total zeros/nulls).
  - `totalRecomputesRatios`: two sources in a month → `total.spend` = sum, `total.roas` = Σvalue/Σspend; `total.spend` null when no source row has spend. Assert via the test's sources only by using a month range no other IT uses (e.g. 2030-xx), so the total is deterministic.
  - `badRange400`: `from > to` → 400; range of 25 months → 400.
  - `salesForbidden`: SALES → 403.

- [ ] **Step 2: Run, expect FAIL**
  Run: `gradlew test --no-daemon --tests '*AdPerformanceIT'`

- [ ] **Step 3: Implement `InsightsQueries.adCohorts`**. The query shape (not obvious from the tests):

```sql
with cust as (
  select c.id, c.source_id,
         date_trunc('month', coalesce(l.created_at, c.created_at) at time zone 'Asia/Manila')::date as month
  from customer c left join lead l on l.id = c.converted_from_lead_id
  where c.source_id is not null),
cv as (
  select cu.month, cu.source_id, count(*) as new_customers, coalesce(sum(v.value), 0) as cohort_value
  from cust cu
  left join lateral (select coalesce(sum(m.cash_amount + m.withholding_amount), 0) as value
                     from money_movement m join sales_order o on o.id = m.order_id
                     where o.customer_id = cu.id) v on true
  where cu.month between :from and :to
  group by cu.month, cu.source_id),
ld as (
  select date_trunc('month', created_at at time zone 'Asia/Manila')::date as month, source_id, count(*) as leads
  from lead where source_id is not null
  group by 1, 2 having date_trunc('month', created_at at time zone 'Asia/Manila')::date between :from and :to),
k as (
  select month, source_id from ad_spend where month between :from and :to
  union select month, source_id from cv
  union select month, source_id from ld)
select k.month, k.source_id, s.name, a.amount as spend,
       coalesce(ld.leads, 0) as leads, coalesce(cv.new_customers, 0) as new_customers,
       coalesce(cv.cohort_value, 0) as cohort_value
from k join lead_source s on s.id = k.source_id
left join ad_spend a on a.source_id = k.source_id and a.month = k.month
left join cv on cv.source_id = k.source_id and cv.month = k.month
left join ld on ld.source_id = k.source_id and ld.month = k.month
order by k.month, lower(s.name)
```

- [ ] **Step 4: Implement `AdPerformanceService.report(YearMonth from, YearMonth to)`** — group rows by month, emit every month in range, build total per spec ("sums the source rows and recomputes the ratios"). Ratios: `divide(x, 2, RoundingMode.HALF_UP)`. Range parsing in the controller: default `to` = current Manila month, `from` = `to.minusMonths(5)`; `from > to` or more than 24 months inclusive → `DomainException(ErrorCode.INSIGHTS_BAD_MONTH, ...)`.

- [ ] **Step 5: Run, expect PASS**
  Run: `gradlew test --no-daemon --tests '*AdPerformanceIT' --tests '*AdSpendIT'`

- [ ] **Step 6: Update OpenAPI snapshot**
  Run: `gradlew test --no-daemon --tests '*OpenApiSnapshotTest' -DupdateSnapshot=true`
  Expected: PASS; `openapi.json` gains the ad-spend and ad-performance paths.

- [ ] **Step 7: Commit**
  `git add openapi.json src/main/java/ph/thecoffeejunkie/crm/insights/ src/test/java/ph/thecoffeejunkie/crm/insights/AdPerformanceIT.java`
  `git commit -m "feat(insights): ad performance cohort report"`

---

### Task 3: Ad performance page (`crm-dashboard-ui`)

**Files:**
- Modify: `src/api/schema.d.ts` (generated)
- Modify: `src/features/insights/api.ts`
- Create: `src/features/insights/AdPerformancePage.tsx`
- Modify: `src/app/router.tsx` (route `/admin/ad-performance`, next to `/admin/sales-targets`)
- Modify: `src/app/shell/nav.ts` (entry "Ad performance" after "Sales targets", `roles: ["ADMIN"]`, reuse an icon already imported there)
- Test: `src/features/insights/AdPerformancePage.test.tsx`

**Interfaces:**
- Consumes: Task 2's OpenAPI schemas `AdSpendRow`, `AdSpendRequest`, `AdPerformance`.
- Produces in `api.ts`:
  - types `AdSpendRow = Nullable<S["AdSpendRow"], "amount">`, `AdPerformance` (Row nullable: `sourceId | sourceName | spend | costPerCustomer | valuePerCustomer | roas`)
  - `insightsKeys.adSpend(month)`, `insightsKeys.adPerformance(from, to)`
  - `fetchAdSpend(month: string): Promise<AdSpendRow[]>`
  - `saveAdSpend(sourceId: number, month: string, amount: number): Promise<AdSpendRow>`
  - `clearAdSpend(sourceId: number, month: string): Promise<void>`
  - `fetchAdPerformance(from: string, to: string): Promise<AdPerformance>`

- [ ] **Step 1: Regenerate types**
  Run (in `crm-dashboard-ui`): `npm run gen:api`
  Expected: `schema.d.ts` contains `AdPerformance`.

- [ ] **Step 2: Add the `api.ts` functions and types above.**

- [ ] **Step 3: Write failing `AdPerformancePage.test.tsx`** (mock `./api` like `SalesTargetsPage.test.tsx`, `currentMonth: () => "2026-11"`):
  - `rendersCohortTable`: `fetchAdPerformance` resolves one month `2026-11` with total and a Facebook row `{spend: 10000, leads: 12, newCustomers: 2, cohortValue: 15000, costPerCustomer: 5000, valuePerCustomer: 7500, roas: 1.5}` → text "Facebook", "₱10,000", "1.5×" visible; `fetchAdPerformance` called with `("2026-06", "2026-11")`.
  - `nullsShowDash`: row with `spend` null, `roas` null → its cells show "—".
  - `savesSpendOnBlur`: change Facebook input to `15000`, blur → `saveAdSpend(<id>, "2026-11", 15000)`.
  - `clearingDeletes`: empty a filled input, blur → `clearAdSpend(<id>, "2026-11")`; `saveAdSpend` not called.

- [ ] **Step 4: Run, expect FAIL**
  Run: `npx vitest run src/features/insights/AdPerformancePage.test.tsx`

- [ ] **Step 5: Implement `AdPerformancePage`** — spend grid (reuse `MonthPicker`), cohort table grouped by month (header row = total, source rows beneath). Format money with the ₱ formatter `SalesTargetsPage`/`parts.tsx` already use; ROAS as `${roas}×`; null → "—". On save/clear, invalidate `insightsKeys.adPerformance`. Wrap the table in a `relative overflow-x-auto` container (sr-only overflow bug on mobile).

- [ ] **Step 6: Wire route and nav.**

- [ ] **Step 7: Run tests and typecheck, expect PASS**
  Run: `npx vitest run src/features/insights` then `npx tsc -b`
  Expected: all pass, no type errors.

- [ ] **Step 8: Verify in the browser preview** — log in as admin on the local dev server, open `/admin/ad-performance`, enter a spend, confirm the table updates. Screenshot.

- [ ] **Step 9: Commit**
  `git add src/api/schema.d.ts src/features/insights/api.ts src/features/insights/AdPerformancePage.tsx src/features/insights/AdPerformancePage.test.tsx src/app/router.tsx src/app/shell/nav.ts`
  `git commit -m "feat(insights): ad performance page"`

---

After all tasks: run `graphify update .` in both repos.
