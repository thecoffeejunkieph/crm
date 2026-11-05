# Follow-ups (F-141) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dated follow-ups on customers (optionally an order) with Overdue / Today / Upcoming / Done lists, rail sections, a dashboard stat and an 08:00 Manila bell.

**Architecture:** New Spring Modulith module `followups` (owns `follow_up`; reads `sales_order` number/customer through SQL, like `insights`). Customers publish `CustomerReassigned`; follow-ups listen in the same transaction. UI adds a `features/followups` folder reused by the page, customer rail, order rail and sales dashboard.

**Tech Stack:** Spring Boot 4 / Java 21, Postgres 17 + Flyway, Redis lock (`DistributedLock`), Testcontainers `@IntegrationTest`; React 19, TanStack Query 5, Vitest, Playwright.

**Spec:** `docs/superpowers/specs/2026-11-05-follow-ups-design.md` (crm/crm). Repos: crm/crm branch `phase/major_refactor_ui`, crm-dashboard-ui branch `phase/major_refactor` (work on `redesign/follow-ups` in each).

## Global Constraints

- Manila time: `ZoneId.of("Asia/Manila")`; "today" is computed from the injected `Clock` (bean in `identity/SessionConfig`).
- `title` trimmed 1–200 chars; `note` ≤ 2000 chars; `due_date` is a date with no time.
- No delete endpoint. Archive-not-delete: mistakes are marked done.
- Error codes (add to `shared/ErrorCode`): `FOLLOW_UP_NO_ASSIGNEE`, `FOLLOW_UP_ORDER_MISMATCH`, `FOLLOW_UP_NOT_A_REP` — all `UNPROCESSABLE_CONTENT`. Stale version uses existing `STALE_VERSION` (409).
- Audit actions (add to `audit/AuditAction`): `FOLLOW_UP_CREATED`, `FOLLOW_UP_UPDATED`, `FOLLOW_UP_DONE`, `FOLLOW_UP_REOPENED`, `FOLLOW_UP_REASSIGNED`; entity type `"FOLLOW_UP"`.
- Notification type `FOLLOW_UPS_DUE`; body text `"3 due today, 1 overdue"` (omit a zero part; singular/plural unchanged — "1 due today", "1 overdue").
- WAREHOUSE gets 403 on every `/api/follow-ups/**` route via a `SecurityConfig` URL rule (before `@PreAuthorize`).
- Never commit `crm/crm/src/main/resources/application.properties` or `docker-compose.dev.yml` (user's local changes).
- Gradle on this machine: PowerShell, `$env:JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:\Users\THECOF~1\gtmp"`, `.\gradlew.bat`.

## Review Focus

1. A rep supplying `assigneeId` of another rep on create — must be silently forced to themselves (test in Task 2).
2. A rep editing/completing a follow-up after the customer moved away from them — it moved to the new rep, so the old rep gets 404 (test in Task 4).
3. Due today at 23:30 Manila is still "today" although UTC is already the next day — bucket from Manila date, not UTC (test in Task 1).
4. The 08:00 job firing twice (two instances, or a restart at 08:00) — one notification per assignee per day (test in Task 5).
5. Ticking done in two tabs at once — second tick is a no-op 200, not 409 (done/reopen take no version; test in Task 3).

---

### Task 1: Table, entity and the Manila day

**Files:**
- Create: `src/main/resources/db/migration/V11__follow_ups.sql`
- Create: `src/main/java/ph/thecoffeejunkie/crm/followups/{package-info.java, FollowUp.java, FollowUpRepository.java, FollowUpDay.java}`
- Test: `src/test/java/ph/thecoffeejunkie/crm/followups/FollowUpDayTest.java`

**Interfaces:**
- Produces: `FollowUp` JPA entity (package-private fields like `SalesTarget`: `id, customerId, orderId, assigneeId, title, note, dueDate (LocalDate), doneAt (Instant), doneBy, createdBy, createdAt, updatedAt, @Version version`).
- Produces: `FollowUpRepository extends JpaRepository<FollowUp, Long>, JpaSpecificationExecutor<FollowUp>`; `List<FollowUp> findByCustomerIdAndAssigneeIdAndDoneAtIsNull(long customerId, long assigneeId)`.
- Produces: `enum Bucket { OVERDUE, TODAY, UPCOMING, DONE; static Bucket parse(String raw) }` (null → TODAY, unknown → `DomainException(VALIDATION_FAILED)` = 400) inside `FollowUpDay`; `static LocalDate today(Clock clock)` (Manila).

- [ ] **Step 1: Write `FollowUpDayTest`**
  - `today_isManilaDate`: clock fixed at `2026-11-05T15:30:00Z` (23:30 Manila) → `2026-11-05`; at `2026-11-05T16:00:00Z` → `2026-11-06`.
  - `parse`: `null`→TODAY, `"overdue"`→OVERDUE (case-insensitive), `"soon"` throws `DomainException` with code `VALIDATION_FAILED`.
- [ ] **Step 2: Run** `.\gradlew.bat test --tests "*FollowUpDayTest"` → FAIL (classes missing).
- [ ] **Step 3: Write V11** exactly per spec table (FKs to `customer(id)`, `sales_order(id)`, `app_user(id)` — use the user table name V1 uses; partial index `follow_up_open_idx on follow_up(assignee_id, due_date) where done_at is null`, plus indexes on `customer_id`, `order_id`; `title` check `char_length(title) between 1 and 200`). Then the entity, repository and `FollowUpDay`. `package-info.java`: one-line Javadoc "Follow-ups on customers (spec 2026-11-05). Owns follow_up; reads sales_order through SQL."
- [ ] **Step 4: Run** the test → PASS; `.\gradlew.bat test --tests "*ModularityTest"` → PASS.
- [ ] **Step 5: Commit** `feat(followups): table, entity and Manila day`.

### Task 2: Create, list and counts

**Files:**
- Create: `followups/{FollowUpRequest.java, FollowUpResponse.java, FollowUpCounts.java, FollowUpQueries.java, FollowUpService.java, FollowUpController.java}`
- Modify: `shared/ErrorCode.java`, `audit/AuditAction.java`, `identity/SecurityConfig.java` (rule `"/api/follow-ups", "/api/follow-ups/**"` → `hasAnyRole("ADMIN","SALES")`), `shared/IdempotencyFilter.java` (add `"/api/follow-ups"` to `DEFAULT_PATHS`)
- Test: `src/test/java/ph/thecoffeejunkie/crm/followups/{FollowUpsFixture.java, FollowUpsIT.java}`, modify `SecurityMatrixTest.java`, refresh `openapi.json` (`-DupdateSnapshot=true`)

**Interfaces:**
- Consumes: Task 1 types; `Customers.salesProfileVisible(Scope, long)` (→ `assignedRepId`), `Customers.displayNames`, `Users.isActiveWithRole`, `Users.displayNames`, `CurrentActor.scope()`, `AuditLog.record`.
- Produces: `record FollowUpRequest(@NotNull Long customerId, Long orderId, Long assigneeId, @NotBlank @Size(max=200) String title, @Size(max=2000) String note, @NotNull LocalDate dueDate, Long version)` (version used by PUT only).
- Produces: `record FollowUpResponse(long id, long customerId, String customerName, Long orderId, String orderNumber, long assigneeId, String assigneeName, String title, String note, LocalDate dueDate, Instant doneAt, long version)`.
- Produces: `record FollowUpCounts(long overdue, long today, long upcoming)`.
- Produces: `FollowUpQueries` (JdbcTemplate): `Optional<Long> orderCustomer(long orderId)`, `Map<Long,String> orderNumbers(Collection<Long> ids)`.
- Produces endpoints: `GET /api/follow-ups?bucket&customerId&orderId&assigneeId&page&size` → `PageResponse<FollowUpResponse>`; `GET /api/follow-ups/counts?customerId&orderId` → `FollowUpCounts`; `POST /api/follow-ups` → 201 `FollowUpResponse`.

- [ ] **Step 1: Write `FollowUpsIT`** (fixture seeds users/customers/orders via JDBC with a tag, like `InsightsFixture`; dates relative to `FollowUpDay.today(Clock.systemUTC())`):
  - `repCreatesForThemself`: rep POSTs with `assigneeId` = other rep → 201, `$.assigneeId` = rep.
  - `repCannotCreateOnOthersCustomer` → 404.
  - `adminDefaultsToCustomersRep`; `adminCustomerWithoutRep422` (`$.code` = `FOLLOW_UP_NO_ASSIGNEE`); `adminNonRepAssignee422` (`FOLLOW_UP_NOT_A_REP`).
  - `orderOfOtherCustomer422` (`FOLLOW_UP_ORDER_MISMATCH`); `blankTitle400`.
  - `bucketsSplitByDueDate`: yesterday/today/tomorrow rows land in overdue/today/upcoming; `bucket=done` lists only done rows (seed `done_at` via JDBC), newest `done_at` first; `bucket=soon` → 400.
  - `repSeesOnlyOwn`; `adminFiltersByAssignee`; `customerAndOrderFilters`.
  - `countsMatchBuckets`: `{overdue:1,today:1,upcoming:1}` for the rep.
  - `createIsAudited`: one `audit_event` row `FOLLOW_UP_CREATED`, entity `FOLLOW_UP`.
  - `responseNamesOrderAndCustomer`: `$.orderNumber`, `$.customerName`, `$.assigneeName`.
- [ ] **Step 2: Run** `.\gradlew.bat test --tests "*FollowUpsIT"` → FAIL (404 on routes).
- [ ] **Step 3: Implement.** Service checks visibility through `salesProfileVisible` (empty → `NOT_FOUND`). SALES assignee is forced to `scope.userId()`. List uses `Specification`s for bucket/filters with scope (`assigneeId = me` for SALES), sort per spec, then one `displayNames`/`orderNumbers` lookup per page. Counts: one SQL `count(*) filter (where …)` over the open rows in scope.
- [ ] **Step 4: Add `SecurityMatrixTest` rows** `GET /api/follow-ups`, `GET /api/follow-ups/counts`, `POST /api/follow-ups` → `AS`; refresh the OpenAPI snapshot.
- [ ] **Step 5: Run** `FollowUpsIT`, `SecurityMatrixTest`, `OpenApiSnapshotTest`, `ModularityTest` → PASS.
- [ ] **Step 6: Commit** `feat(followups): create, list and counts`.

### Task 3: Edit, done and reopen

**Files:**
- Modify: `followups/FollowUpService.java`, `followups/FollowUpController.java`
- Test: `followups/FollowUpEditIT.java`; `SecurityMatrixTest` rows; OpenAPI snapshot

**Interfaces:**
- Produces endpoints: `PUT /api/follow-ups/{id}` (body `FollowUpRequest` with `version`), `POST /api/follow-ups/{id}/done`, `POST /api/follow-ups/{id}/reopen` → 200 `FollowUpResponse`.

- [ ] **Step 1: Write `FollowUpEditIT`:**
  - `assigneeEditsTitleAndDate`; audit `FOLLOW_UP_UPDATED` with changes.
  - `staleVersion409` (`$.code` = `STALE_VERSION`).
  - `repCannotChangeAssignee`: supplied `assigneeId` ignored for SALES; admin change to a non-rep → 422 `FOLLOW_UP_NOT_A_REP`; admin change to a rep → audit `FOLLOW_UP_REASSIGNED`.
  - `otherRep404` for PUT/done/reopen.
  - `doneThenDoneAgainIsNoop`: second done → 200, `doneAt` unchanged, one `FOLLOW_UP_DONE` audit row.
  - `reopenClearsDone`: `doneAt` null, `done_by` null, audit `FOLLOW_UP_REOPENED`.
  - `editOrderMustMatchCustomer` → 422 `FOLLOW_UP_ORDER_MISMATCH`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4:** matrix rows `PUT /api/follow-ups/{id}`, `POST /api/follow-ups/{id}/done`, `POST /api/follow-ups/{id}/reopen` → `AS`; refresh snapshot; run → PASS.
- [ ] **Step 5: Commit** `feat(followups): edit, done and reopen`.

### Task 4: Follow the customer on reassignment

**Files:**
- Create: `customers/CustomerReassigned.java` (`public record CustomerReassigned(long customerId, Long fromRepId, Long toRepId)`), `followups/ReassignmentListener.java`
- Modify: `customers/Customers.java` `assign(...)` — publish after `saveAndFlush`, only when the rep changed
- Test: `followups/FollowUpReassignIT.java`

**Interfaces:**
- Produces: `CustomerReassigned` (public, in `customers`). Listener: `@EventListener` (same transaction, not after-commit) → `FollowUpService.moveOpen(long customerId, long fromRepId, long toRepId)`.

- [ ] **Step 1: Write `FollowUpReassignIT`** (reassign through `PUT`/`POST` the existing customer-assignment endpoint as admin — find it in `CustomerController`):
  - `openOldRepRowsMove`: open row of old rep → new rep; one `FOLLOW_UP_REASSIGNED` audit row per moved row.
  - `doneRowsStay`; `otherAssigneesStay` (an admin-assigned row to a third rep on the same customer stays).
  - `unassignLeavesRows`: assign to `null` → rows keep the old rep.
  - `oldRepLosesAccess`: old rep `POST /{id}/done` → 404; new rep → 200.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** this IT, `ModularityTest`, and the existing customers assignment IT → PASS.
- [ ] **Step 5: Commit** `feat(followups): move open follow-ups with the customer`.

### Task 5: Morning bell

**Files:**
- Create: `followups/FollowUpReminder.java`
- Modify: `notifications/NotificationType.java` (`FOLLOW_UPS_DUE`)
- Test: `followups/FollowUpReminderIT.java`, `followups/FollowUpReminderTest.java`

**Interfaces:**
- Consumes: `DistributedLock.tryLock(String, Duration)`, `Notifications.toUser(long, NotificationType, String, Long, String, String)`, `Clock`.
- Produces: `@Scheduled(cron = "0 0 8 * * *", zone = "Asia/Manila") void run()` delegating to `int sendFor(LocalDate today)` (returns notifications sent); `static String body(long dueToday, long overdue)`. Lock name `"follow-ups-due:" + today`, TTL `Duration.ofHours(23)`. Notification: entityType `"FOLLOW_UP"`, entityId `null`, title `"Follow-ups due"`, body from `body(...)`.

- [ ] **Step 1: Write tests.** Unit: `body(3,1)` = `"3 due today, 1 overdue"`, `body(2,0)` = `"2 due today"`, `body(0,1)` = `"1 overdue"`. IT: `onePerAssignee` (rep A with 2 today + 1 overdue → one notification with `"2 due today, 1 overdue"`; rep B with only upcoming → none); `secondRunSameDaySendsNothing` (`sendFor` twice → second returns 0); clean the `lock:follow-ups-due:*` key in `@AfterEach`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement** (one grouped SQL: per assignee, `count filter (due_date = :today)`, `count filter (due_date < :today)`, open only, having either > 0). **Step 4: Run** → PASS; refresh OpenAPI snapshot (enum changed) and run the full suite `.\gradlew.bat test` → all green.
- [ ] **Step 5: Commit** `feat(followups): 08:00 Manila reminder`.

### Task 6: UI — api, page, route, nav badge

**Files (crm-dashboard-ui):**
- Run: `npm run gen:api`
- Create: `src/features/followups/{api.ts, FollowUpsPage.tsx, FollowUpRow.tsx, FollowUpsPage.test.tsx}`
- Modify: `src/app/router.tsx` (route `/follow-ups` for ADMIN and SALES), `src/app/shell/nav.ts` (Sell group, first item: `{label: "Follow-ups", to: "/follow-ups", icon: icon(TimeIcon), roles: ["ADMIN","SALES"], badge: "followUps"}`; widen `badge` type to `"approvals" | "followUps"`), `src/app/shell/Sidebar.tsx` (badge = `overdue + today` from `/follow-ups/counts`, ADMIN and SALES), `src/app/shell/NotificationBell.tsx` (also invalidate `["follow-ups"]` on stream messages)

**Interfaces:**
- Produces in `api.ts`: `type FollowUp = Filled<components["schemas"]["FollowUpResponse"]>`; `followUpKeys = {all: ["follow-ups"], list: (p) => [...], counts: (p) => [...]}`; `fetchFollowUps(p: {bucket?: Bucket; customerId?: number; orderId?: number; assigneeId?: number; page?: number})`, `fetchFollowUpCounts(p: {customerId?: number; orderId?: number})`, `createFollowUp(body)`, `updateFollowUp(id, body)`, `completeFollowUp(id)`, `reopenFollowUp(id)`; `type Bucket = "overdue" | "today" | "upcoming" | "done"`. Reuse the `Filled<T>` helper from `features/insights/api.ts` (export it from there if not already exported).
- Produces `FollowUpRow({f, showCustomer}: {f: FollowUp; showCustomer: boolean})`: checkbox labelled `Mark "<title>" done` (or `Reopen "<title>"` when done), title, customer link, order link, due date with `tone="danger"` text when overdue. Toggling calls complete/reopen, then invalidates `followUpKeys.all`; on 409 toast "Someone else changed this follow-up. Reloaded.".

- [ ] **Step 1: Write `FollowUpsPage.test.tsx`:** tabs Overdue/Today/Upcoming/Done (Today selected by default) each fetch their bucket; empty state "Nothing due today." on Today, "Nothing overdue." / "Nothing upcoming." / "Nothing done yet."; ticking a row calls `completeFollowUp(id)`; admin sees a "Rep" filter, rep does not. Sidebar test (extend existing): Follow-ups badge shows `3` for counts `{overdue:1,today:2,upcoming:5}`.
- [ ] **Step 2: Run** `npx vitest run src/features/followups src/app/shell` → FAIL. **Step 3: Implement.** **Step 4:** run → PASS; `npx tsc -b` clean.
- [ ] **Step 5: Commit** `feat(followups): follow-ups page and nav badge`.

### Task 7: UI — add/edit drawer and rail sections

**Files:**
- Create: `src/features/followups/{FollowUpDrawer.tsx, FollowUpsSection.tsx, FollowUpDrawer.test.tsx, FollowUpsSection.test.tsx}`
- Modify: `src/features/customers/CustomerPage.tsx` (rail, after Contacts: `<FollowUpsSection customerId={id} archived={archived}/>`), `src/features/sales/OrderWorkspace.tsx` (rail, after `ShipFromCard`: `<FollowUpsSection customerId={o.customerId} orderId={o.id}/>`)

**Interfaces:**
- Produces `FollowUpDrawer({open, customerId, orderId?, followUp?, onClose})` built on `Drawer` like `ContactDrawer`: fields Title, Due date (`<input type="date">`, default tomorrow in Manila, computed with `Intl.DateTimeFormat("en-CA", {timeZone: "Asia/Manila"})`), Note, Order (select of this customer's orders, optional; preselected when `orderId` given), Assignee (ADMIN only; a select of SALES users whose empty option "Customer's rep" sends no `assigneeId`, so the server default applies). Save → create, or update with `version`; 409 → toast as Task 6.
- Produces `FollowUpsSection({customerId, orderId?, archived?})`: `<Section title="Follow-ups" action={<Button size="sm">Add</Button>}>` listing open follow-ups — three list calls (`overdue`, `today`, `upcoming`) concatenated in that order — each a `FollowUpRow` with `showCustomer={false}`; empty text "No open follow-ups."; Add hidden when `archived`.

- [ ] **Step 1: Write tests:** drawer defaults due date to tomorrow (fixed `vi.setSystemTime` at 2026-11-05T16:30Z → "2026-11-07"), blank title shows "Enter a title", admin sees Assignee and rep does not, submit calls `createFollowUp` with `orderId` when preselected; section lists rows, hides Add when archived, Add opens the drawer.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4:** run `npx vitest run` (whole suite) → PASS, `npx tsc -b` clean.
- [ ] **Step 5: Commit** `feat(followups): drawer and rail sections`.

### Task 8: Dashboard stat, bell link and e2e

**Files:**
- Modify: `src/features/insights/SalesDashboard.tsx` (+ its test), `src/app/shell/NotificationBell.tsx` (item with type `FOLLOW_UPS_DUE` links to `/follow-ups`)
- Create: `e2e/follow-ups.spec.ts`

**Interfaces:**
- Consumes: `fetchFollowUpCounts` (Task 6).

- [ ] **Step 1: Tests.** SalesDashboard: a `Stat` "Follow-ups" with value `"2 overdue · 3 today"` linking to `/follow-ups`; when both are 0 shows `"All caught up"`. Bell: a `FOLLOW_UPS_DUE` item is a link to `/follow-ups`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4:** run vitest → PASS.
- [ ] **Step 5: e2e** `follow-ups.spec.ts`: admin signs in, creates a SALES user (pattern from `insights.spec.ts`), creates a customer assigned to that rep; rep signs in, opens the customer, Follow-ups → Add, title "Call about beans", due date today, Save; `/follow-ups` Today tab shows it; tick done; Done tab shows it. Run with the scratchpad harness (`SPECS=follow-ups`), plus all five existing specs → all pass.
- [ ] **Step 6: Commit** `feat(followups): dashboard stat, bell link and e2e`.
