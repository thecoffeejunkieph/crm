# Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Role-targeted, persistent notifications for payments, stock, targets and order lifecycle; a Notifications page reached from the bell replaces Inbox; approvals are decided on the record they are about.

**Architecture:** New triggers call the existing `Notifications` service directly at each trigger site (stored in the business transaction, pushed after commit). Target milestones go through a `PaymentVerified` event so `sales` does not depend on `insights`. Frontend adds `/notifications`, a reusable `PendingApprovals` panel, and removes Inbox.

**Tech Stack:** Spring Boot / JPA / Flyway / JUnit ITs (`crm/crm`); React + Vite + TanStack Query + Vitest/RTL (`crm-dashboard-ui`).

**Spec:** `docs/superpowers/specs/2026-11-12-notifications-design.md`

## Global Constraints

- Backend repo: `C:\Users\The Coffee Junkie\Documents\Codes\crm\crm`. Frontend repo: `C:\Users\The Coffee Junkie\Documents\Codes\crm-dashboard-ui`.
- Gradle from Claude's shell: `$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:/Users/Public/gtmp'; .\gradlew test --no-daemon --tests '<pattern>'`. If `build/` is locked by a running dev server, add `-I <init script>` setting `allprojects { layout.buildDirectory = file('C:/Users/Public/gbuild') }`; never stop the user's server.
- Backend git branch is `phase/major_refactor_ui` with unrelated uncommitted changes (`.gitignore`, `application.properties`): stage only the files each task names.
- No DB migration: `notification.type` is `text` with no check constraint.
- Every new notification is sent with `Notifications`; never write `notification` rows directly.
- Low/out rule: product-wide `available = sum(on_hand) - sum(reserved)`; LOW when `available <= reorder_level`, OUT when `available <= 0`; fire only on a crossing within one `reserve`; both at once → OUT only; null `reorder_level` → never LOW.
- Target thresholds: 50, 80, 100 of `Commission.progressPct`; highest `t` with `before < t <= after`; month = `verified_at` in `Asia/Manila`.
- The current staff user never receives a notification about their own action; system/portal actors drop nobody.
- After code changes in each repo run `graphify update .` in that repo (last task).

## Notification copy (exact)

| Type | entityType / entityId | Title | Body |
|---|---|---|---|
| `PAYMENT_SUBMITTED` (admin) | `ORDER` / order id | `Payment to verify for {orderNumber}` | `{amount} ({method})` |
| `PAYMENT_VERIFIED` | `ORDER` | `Payment verified for {orderNumber}` | `{amount} ({method})` |
| `PAYMENT_REJECTED` | `ORDER` | `Payment rejected for {orderNumber}` | `{reason}` |
| `TARGET_MILESTONE` | `SALES_TARGET` / null | 50/80: `You're at {t}% of your {Month} target`; 100: `Target hit for {Month}` | `{netSales} of {target}` |
| `LOW_STOCK` | `PRODUCT` / product id | `{sku} is low on stock` | `{available} {unit} available, reorder level {level}.` |
| `OUT_OF_STOCK` | `PRODUCT` / product id | `{sku} is out of stock` | `Order {orderNumber} took the last of it.` |
| `ORDER_SHORT` | `ORDER` | `Order {orderNumber} is short` | `{shortQty total} short after reservation.` |
| `QUOTE_DECLINED` (admin) | `ORDER` | `Quote {orderNumber} declined` | `{reason}` |
| `ORDER_RELEASED` | `ORDER` | `Order {orderNumber} released to the warehouse` | null |
| `ORDER_DELIVERED` | `ORDER` | `Order {orderNumber} delivered` | null |
| `RELEASED_ORDER_CHANGED` | admins: `ORDER` / order id; warehouse: `DELIVERY_ORDER` / DO id | `Delivery order {doNumber} cancelled` | `Order {orderNumber} was changed or cancelled; stop picking.` |

`{Month}` = full English month name (`November`). Amounts use `toPlainString()`.

## Review Focus

- Order with no rep (`repId` null): rep-bound payment/release/delivered notifications go to `createdBy` (via `OrderStockMapper.rep`), target milestones are skipped — test in Task 2 and Task 3.
- An admin who is also a member of the ship-from warehouse gets one LOW_STOCK, not two — test in Task 4.
- A payment verified at 2026-12-01T00:30+08:00 (still Nov 30 in UTC) counts toward December — test in Task 3.
- Notifications sent with no authenticated staff user (scheduler, portal contact) are not dropped — test in Task 1.
- A notification with no link on `/notifications` only marks read and does not navigate — test in Task 8.

---

## Backend (`crm/crm`)

### Task 1: Notifications core — types, self-skip, multi-user send, unread filter

**Files:**
- Modify: `src/main/java/ph/thecoffeejunkie/crm/notifications/NotificationType.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/notifications/Notifications.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/notifications/NotificationRepository.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/notifications/NotificationController.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/notifications/NotificationsIT.java`

**Interfaces:**
- Produces: enum values `PAYMENT_VERIFIED, PAYMENT_REJECTED, LOW_STOCK, OUT_OF_STOCK, ORDER_SHORT, ORDER_RELEASED, ORDER_DELIVERED, RELEASED_ORDER_CHANGED, TARGET_MILESTONE`.
- Produces: `public void toUsers(Collection<Long> userIds, NotificationType type, String entityType, Long entityId, String title, String body)` (`@Transactional`, distinct recipients, same self-skip).
- Produces: `PageResponse<NotificationResponse> list(long userId, boolean unreadOnly, PageQuery page)`; `GET /api/notifications?unread=true`.
- Behaviour: `send()` removes `actor.staff().map(StaffPrincipal::userId)` from recipients (inject `CurrentActor`).

- [ ] **Step 1: Write failing tests in `NotificationsIT`**

```java
@Test void actorIsNotNotifiedOfOwnAction() {
    login(sales, Role.SALES);   // SecurityContext with StaffPrincipal(sales…)
    tx.executeWithoutResult(s -> notifications.toUsers(List.of(sales, otherSales),
            NotificationType.ORDER_RELEASED, "ORDER", 1L, "NT:Released", null));
    assertThat(count(sales)).isZero();
    assertThat(count(otherSales)).isEqualTo(1);
}
@Test void noStaffActorDropsNobody() {   // SecurityContext cleared
    tx.executeWithoutResult(s -> notifications.toUser(sales, NotificationType.ORDER_RELEASED, "ORDER", 1L, "NT:Sys", null));
    assertThat(count(sales)).isEqualTo(1);
}
@Test void toUsersDeduplicates() {
    tx.executeWithoutResult(s -> notifications.toUsers(List.of(sales, sales), NotificationType.LOW_STOCK, "PRODUCT", 1L, "NT:Low", null));
    assertThat(count(sales)).isEqualTo(1);
}
@Test void unreadFilterListsOnlyUnread() throws Exception {
    // two notifications for sales, mark one read, GET /api/notifications?unread=true as sales
    // → jsonPath("$.content.length()").value(1), jsonPath("$.content[0].read").value(false)
}
```

Use `SecurityContextHolder` with `SalesFixture.token(id, role)`-style token (already used in this class); clear it in `@AfterEach`.

- [ ] **Step 2: Run** `.\gradlew test --no-daemon --tests '*NotificationsIT'` — Expected: FAIL (no `toUsers`, no `unread` param).
- [ ] **Step 3: Implement** the Interfaces above; repository adds `Page<Notification> findByRecipientUserIdAndReadAtIsNull(long, Pageable)`; controller `@RequestParam(defaultValue = "false") boolean unread`.
- [ ] **Step 4: Run** `.\gradlew test --no-daemon --tests '*NotificationsIT' --tests '*ApprovalsIT' --tests '*FollowUpReminderIT' --tests '*ApprovalPushAfterCommitIT'` — Expected: PASS. If an existing IT asserted a self-notification, change that assertion to expect none (it is the new rule) and note it in the commit message.
- [ ] **Step 5: Commit** `feat(notifications): new types, toUsers, self-skip, unread filter`

### Task 2: Payment notifications and `PaymentVerified` event

**Files:**
- Create: `src/main/java/ph/thecoffeejunkie/crm/sales/PaymentVerified.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/sales/PaymentService.java` (`record`, `verify`, `reject`)
- Modify: `src/main/java/ph/thecoffeejunkie/crm/sales/PortalOrderService.java` (`submitPayment`)
- Test: `src/test/java/ph/thecoffeejunkie/crm/sales/PaymentsIT.java`, `src/test/java/ph/thecoffeejunkie/crm/sales/PortalOrdersIT.java`

**Interfaces:**
- Consumes: Task 1 types; `Notifications.toRole/toUser`.
- Produces: `public record PaymentVerified(long orderId, long paymentId, Long repId, Instant verifiedAt)` published via `ApplicationEventPublisher` inside the transaction from `verify` and from `record`'s admin branch. `repId` is `o.repId` (nullable — no fallback; targets credit `rep_id`).
- Rep recipient for notifications = `OrderStockMapper.rep(o)`. Not sent from `rejectPendingFor`.

- [ ] **Step 1: Write failing tests**
  - `PaymentsIT.repRecordedPaymentNotifiesAdmins` — rep records → admin user has a `PAYMENT_SUBMITTED` row with `entity_type='ORDER'`, `entity_id=orderId`, title `Payment to verify for {number}`; rep has none.
  - `PaymentsIT.verifyNotifiesRepAndPublishesEvent` — admin verifies → rep has `PAYMENT_VERIFIED`; capture events with `@RecordApplicationEvents` and assert one `PaymentVerified` with that payment id.
  - `PaymentsIT.adminRecordedPaymentNotifiesRep` — admin records on rep's order → rep has `PAYMENT_VERIFIED`.
  - `PaymentsIT.rejectNotifiesRepWithReason` — body equals the reason.
  - `PaymentsIT.unassignedOrderNotifiesCreator` — order with `rep_id` null created by user X → X gets `PAYMENT_VERIFIED`; the event has `repId == null`.
  - `PortalOrdersIT.portalPaymentNotifiesAdminsAndRep` — both have `PAYMENT_SUBMITTED`.
- [ ] **Step 2: Run** `--tests '*PaymentsIT' --tests '*PortalOrdersIT'` — Expected: FAIL.
- [ ] **Step 3: Implement** per Interfaces and the copy table. Inject `Notifications` and `ApplicationEventPublisher` into `PaymentService`.
- [ ] **Step 4: Run** same — Expected: PASS.
- [ ] **Step 5: Commit** `feat(sales): payment notifications and PaymentVerified event`

### Task 3: Target milestones

**Files:**
- Create: `src/main/java/ph/thecoffeejunkie/crm/insights/TargetMilestones.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/insights/InsightsQueries.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/insights/TargetMilestonesIT.java`

**Interfaces:**
- Consumes: `PaymentVerified` (Task 2); `TargetService.find(long repId, YearMonth m)`; `Commission.progressPct`.
- Produces: `InsightsQueries.netSales(Long repId, YearMonth m, long excludePaymentId)` — same as `netSales` with `and p.id <> :exclude`.
- `@EventListener void on(PaymentVerified e)` (synchronous, same transaction). Skip when `repId == null` or no target / target ≤ 0. `after = netSales(rep, m)`, `before = netSales(rep, m, paymentId)`; never use the `@Cacheable` `InsightsService`.

```java
static Integer crossed(Integer before, Integer after) {   // highest of 100, 80, 50 with before < t <= after, else null
```

- [ ] **Step 1: Write failing tests** (seed a target of 1000.00 for the rep's month, verify payments whose net-of-VAT credit gives the stated progress; read the rep's `TARGET_MILESTONE` titles)
  - `jumpFrom40To85SendsOnlyEighty` → exactly `["You're at 80% of your November target"]`
  - `from85To90SendsNothing`
  - `reachingHundredSendsTargetHit` → `"Target hit for November"`
  - `noTargetSendsNothing`
  - `unassignedOrderSendsNothing`
  - `manilaMonthBoundary` — payment verified at `2026-11-30T16:30:00Z` counts toward December's target
  - unit test `crossed(40, 85) == 80`, `crossed(85, 90) == null`, `crossed(null, 50) == 50`
- [ ] **Step 2: Run** `--tests '*TargetMilestonesIT'` — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `--tests '*TargetMilestonesIT' --tests '*SalesInsightsIT' --tests '*TargetsIT'` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(insights): notify reps at 50/80/100% of monthly target`

### Task 4: Low / out of stock and order short on reservation

**Files:**
- Modify: `src/main/java/ph/thecoffeejunkie/crm/inventory/Inventory.java` (`reserve`, constructor)
- Modify: `src/main/java/ph/thecoffeejunkie/crm/catalog/Catalog.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/inventory/ReservationsIT.java`, `InventoryFixture.java`

**Interfaces:**
- Consumes: `Notifications.toUsers` (Task 1); `Users.activeIdsWithRole(Role.ADMIN)`, `Users.idsForWarehouse(long)`.
- Produces: `public Map<Long, BigDecimal> Catalog.reorderLevels(Collection<Long> productIds)` (missing/null level → absent key).
- In `reserve(OrderStock o)`: `before`/`after` = product-wide available per distinct product id (sum over all of the product's `stock_level` rows, via `levels.findByProductIdIn`; make sure `after` reads post-reservation values — flush if levels are updated outside the persistence context). Crossings → one `toUsers(admins ∪ idsForWarehouse(o.warehouseId()), …)` per product. Then if `totalShort(o.orderId()) > 0` → `toUsers(admins, ORDER_SHORT, "ORDER", o.orderId(), …)`.
- Fixture additions: `long admin(String name)`, `long warehouseUser(String name, long warehouseId)` (inserts the membership row `idsForWarehouse` reads), `void reorderLevel(long productId, String level)`, `List<String> notificationTypes(long userId)`.

- [ ] **Step 1: Write failing tests in `ReservationsIT`**
  - `crossingIntoLowNotifiesAdminsAndShipFromWarehouse` — on hand 10, level 5, reserve 6 → admin and warehouse user each have exactly one `LOW_STOCK`; a user of a different warehouse has none.
  - `stayingBelowLevelDoesNotRepeat` — then reserve 1 more → still one `LOW_STOCK` each.
  - `crossingToZeroSendsOutOnly` — on hand 10, level 5, reserve 10 → `OUT_OF_STOCK` only.
  - `noReorderLevelNeverLow` — on hand 10, no level, reserve 6 → nothing.
  - `adminInWarehouseGetsOne` — admin who is also a member of `wh` → one `LOW_STOCK`.
  - `shortReservationNotifiesAdmins` — on hand 3, reserve 5 → admin has `ORDER_SHORT` with entity `ORDER`/orderId.
- [ ] **Step 2: Run** `--tests '*ReservationsIT'` — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `--tests '*ReservationsIT' --tests '*AllocatorIT' --tests '*OrderStockIT' --tests '*OrdersIT'` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(inventory): low/out-of-stock and short-order notifications on reserve`

### Task 5: Order lifecycle notifications

**Files:**
- Modify: `src/main/java/ph/thecoffeejunkie/crm/sales/OrderService.java` (`release`)
- Modify: `src/main/java/ph/thecoffeejunkie/crm/sales/DeliveredListener.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/fulfilment/Fulfilment.java` (`cancelForOrder`)
- Modify: `src/main/java/ph/thecoffeejunkie/crm/sales/PortalOrderService.java` (decline)
- Test: `src/test/java/ph/thecoffeejunkie/crm/fulfilment/DeliveryOrdersIT.java`, `src/test/java/ph/thecoffeejunkie/crm/sales/PortalOrdersIT.java`

**Interfaces:**
- Consumes: Task 1 (`toUsers`, types). Rep = `OrderStockMapper.rep(o)`.
- `cancelForOrder`: inside the `ifPresent`, after cancelling: `toUsers(admins, RELEASED_ORDER_CHANGED, "ORDER", orderId, …)` and `toUsers(users.idsForWarehouse(d.warehouseId), RELEASED_ORDER_CHANGED, "DELIVERY_ORDER", d.id, …)`.

- [ ] **Step 1: Write failing tests**
  - `DeliveryOrdersIT.releaseNotifiesRep` (`ORDER_RELEASED`, admin releases).
  - `DeliveryOrdersIT.deliveredNotifiesRep` (`ORDER_DELIVERED`).
  - `DeliveryOrdersIT.cancellingReleasedOrderNotifiesAdminsAndWarehouse` — admin row `ORDER`/orderId, warehouse row `DELIVERY_ORDER`/doId.
  - `DeliveryOrdersIT.revisingReleasedOrderNotifiesWarehouse` — same via the revise endpoint.
  - `PortalOrdersIT.declineNotifiesAdminsAndRep` (`QUOTE_DECLINED`).
- [ ] **Step 2: Run** `--tests '*DeliveryOrdersIT' --tests '*PortalOrdersIT'` — Expected: FAIL.
- [ ] **Step 3: Implement** per copy table.
- [ ] **Step 4: Run** same — Expected: PASS.
- [ ] **Step 5: Commit** `feat: release, delivered, released-order-changed and decline notifications`

### Task 6: Approvals — subject links, list filters, contract snapshot

**Files:**
- Modify: `src/main/java/ph/thecoffeejunkie/crm/approvals/Approvals.java` (`request`, `decide`, `list`)
- Modify: `src/main/java/ph/thecoffeejunkie/crm/approvals/ApprovalController.java`
- Modify: approval request repository (filter query)
- Test: `src/test/java/ph/thecoffeejunkie/crm/approvals/ApprovalsIT.java`; OpenAPI snapshot

**Interfaces:**
- Produces: `GET /api/approvals?status=&mine=&subjectType=&subjectId=1&subjectId=2` (`@RequestParam(required = false) String subjectType`, `@RequestParam(required = false) List<Long> subjectId`).
- Produces: private `Link link(String subjectType, long subjectId, long approvalId)` → `ORDER`/id; `INVOICE` → `ORDER`/`jdbc select order_id from invoice where id = ?`; `STOCK_ADJUSTMENT`/id; else `APPROVAL`/approvalId. Used by both notifications.

- [ ] **Step 1: Write failing tests**
  - `requestNotificationPointsAtOrder`, `invoiceVoidNotificationPointsAtItsOrder`, `writeOffNotificationPointsAtAdjustment` (assert `entity_type`/`entity_id` for both requested and decided rows).
  - `listFiltersBySubject` — two subjects, filter by one `subjectType` + two `subjectId`s returns exactly the matching ones.
  - `writeOffDecisionNotifiesWarehouseRequester` — approve a STOCK_WRITE_OFF requested by a warehouse user → that user has `APPROVAL_DECIDED` (spec: verify; add a notify call only if this fails).
- [ ] **Step 2: Run** `--tests '*ApprovalsIT' --tests '*AdjustmentsIT'` — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `--tests '*ApprovalsIT' --tests '*AdjustmentsIT'` — PASS; then `.\gradlew test --no-daemon --tests '*OpenApiSnapshotTest' -DupdateSnapshot=true`, then the full `.\gradlew test --no-daemon` — Expected: all PASS.
- [ ] **Step 5: Commit** `feat(approvals): link notifications to the subject record; filter by subject` (include the updated snapshot)

## Frontend (`crm-dashboard-ui`)

### Task 7: Regenerate API and notification links

**Files:**
- Modify: `src/api/schema.d.ts` (generated)
- Modify: `src/app/shell/NotificationBell.tsx` (`DETAIL_PAGE`, `LIST_PAGE`, footer link)
- Test: `src/app/shell/NotificationBell.test.tsx`

**Interfaces:**
- Produces: `notificationLink` mapping per spec section 2 (`PRODUCT`→`/inventory`, `STOCK_ADJUSTMENT`→`/stock`, `SALES_TARGET`→`/dashboard`, `FOLLOW_UP`→`/follow-ups`; detail `ORDER`, `DELIVERY_ORDER`, `TRANSFER`; no `APPROVAL`).

- [ ] **Step 1:** `npm run gen:api` (backend must be at Task 6). Expected: `schema.d.ts` gains `unread`, `subjectType`, `subjectId` params and new enum values.
- [ ] **Step 2: Write failing tests** — `it.each` over every map entry asserting `notificationLink(...)`; `notificationLink({entityType: "APPROVAL", entityId: 1}) === undefined`; open the bell → link "See all notifications" has `href="/notifications"`.
- [ ] **Step 3: Run** `npx vitest run src/app/shell/NotificationBell.test.tsx` — FAIL.
- [ ] **Step 4: Implement.** Footer: `<Link to="/notifications" onClick={() => setOpen(false)}>See all notifications</Link>` under the list, `text-xs text-brand-500`, centered, top border like the header.
- [ ] **Step 5: Run** same — PASS. **Commit** `feat(notifications): link map and See all link`

### Task 8: Notifications page

**Files:**
- Create: `src/features/notifications/NotificationsPage.tsx`, `src/features/notifications/NotificationsPage.test.tsx`
- Modify: `src/app/router.tsx` (add `/notifications`; change `/inbox` to `<Navigate to="/notifications" replace/>`)

**Interfaces:**
- Consumes: `notificationLink` (Task 7); `GET /notifications?page=&size=20&unread=`, `POST /notifications/{id}/read`, `POST /notifications/read-all`.
- Query key `["notifications", "page", unread]` (inside the `["notifications"]` prefix the bell's SSE handler invalidates); use `useInfiniteQuery` for Load more.

- [ ] **Step 1: Write failing tests**
  - renders titles/bodies, unread rows have `font-medium` (bold)
  - clicking the "Unread" chip refetches with `unread: true`
  - clicking a linked row posts `/notifications/{id}/read` and navigates (assert route via a `MemoryRouter` + `Routes` stub)
  - clicking a row with no link only posts read and stays on `/notifications`
  - "Load more" requests `page: 1`; hidden on the last page
  - "Mark all read" posts `/notifications/read-all`
- [ ] **Step 2: Run** `npx vitest run src/features/notifications` — FAIL.
- [ ] **Step 3: Implement** following `InboxPage` layout primitives (`h1` `text-title-sm`, `EmptyState` "You are all caught up.", `Skeleton`), chips styled like existing saved-view chips.
- [ ] **Step 4: Run** same — PASS. **Commit** `feat(notifications): Notifications page`

### Task 9: PendingApprovals on order and stock pages

**Files:**
- Create: `src/features/approvals/PendingApprovals.tsx`, `src/features/approvals/PendingApprovals.test.tsx` (port the two `InboxPage.test.tsx` cases)
- Modify: `src/features/approvals/api.ts` (`fetchApprovals` accepts `subjectType`, `subjectId`)
- Modify: `src/features/sales/OrderWorkspace.tsx`, `src/features/fulfilment/StockPage.tsx`

**Interfaces:**
- Produces: `export default function PendingApprovals({subjects, title}: {subjects: {type: string; ids: number[]}[]; title?: string})` — one `fetchApprovals({status: "PENDING", subjectType, subjectId})` per subject group, query key `["approvals", "pending", type, ids]`; admin: Approve/Reject (reject modal, note required); requester: Cancel; renders `null` when empty or while loading.
- OrderWorkspace: `subjects=[{type: "ORDER", ids: [order.id]}, {type: "INVOICE", ids: invoices.map(i => i.id)}]` using the existing `fetchInvoices(orderId)` query (skip the INVOICE group when empty). StockPage: `[{type: "STOCK_ADJUSTMENT", ids: []}]` with `title="Write-offs awaiting approval"` (empty `ids` = no id filter).

- [ ] **Step 1: Write failing tests** — ported Inbox cases (admin sees Approve/Reject and reject needs a note; sales sees Cancel only on own request) against `PendingApprovals`; "renders nothing when there are no pending approvals"; `OrderWorkspace.test.tsx`: requests approvals for ORDER and INVOICE subjects.
- [ ] **Step 2: Run** `npx vitest run src/features/approvals src/features/sales/OrderWorkspace.test.tsx` — FAIL.
- [ ] **Step 3: Implement**, moving the mutation/modal logic out of `InboxPage.tsx`.
- [ ] **Step 4: Run** same — PASS. **Commit** `feat(approvals): decide approvals on the order and stock pages`

### Task 10: Remove Inbox

**Files:**
- Delete: `src/features/approvals/InboxPage.tsx`, `src/features/approvals/InboxPage.test.tsx`
- Modify: `src/app/shell/nav.ts` (drop Inbox item and `"approvals"` from the badge union; mobile tab Inbox → `tab("Dashboard", "/dashboard", PieChartIcon)`), `src/app/shell/Sidebar.tsx` (drop approvals badge query), `src/app/router.tsx` (drop `InboxPage` import)
- Test: `src/app/shell/nav.test.ts`, `src/app/shell/MorePage.test.tsx`, `src/app/shell/Header.test.tsx`

- [ ] **Step 1: Update tests** — no role's nav contains `/inbox` or `/notifications`; `mobileTabsFor("ADMIN"|"SALES")` contains Dashboard and not Inbox; router test or `nav.test.ts` asserts `/inbox` redirects to `/notifications`.
- [ ] **Step 2: Run** `npx vitest run src/app/shell` — FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `npm test`, `npm run lint`, `npm run build` — Expected: all pass, no reference to `InboxPage`.
- [ ] **Step 5: Commit** `feat: remove Inbox; Notifications page reached from the bell`

### Task 11: Graph refresh and final check

- [ ] Run `graphify update .` in both repos.
- [ ] Backend full `.\gradlew test --no-daemon`; frontend `npm test && npm run build` — Expected: green.
- [ ] Manual smoke (via the `run` skill): as SALES record a payment → admin bell shows "Payment to verify…", click opens the order with Verify; as ADMIN request→decide a discount from the order page; See all → `/notifications`, Unread chip works.
