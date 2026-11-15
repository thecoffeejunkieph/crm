# Notifications — design (2026-11-12)

Spans both repos: `crm/crm` (backend) and `crm-dashboard-ui` (frontend).

## Goal

Every role learns about the events that need their attention through the bell, and clicking a notification
opens the record it is about. The Inbox page goes away; a full Notifications page replaces it, reachable only
from the bell.

## What already exists (unchanged)

- `notification` table (persistent, per recipient, `read_at`), `Notifications.toUser/toRole` storing in the
  business transaction and pushing over SSE/Redis after commit, unread-count stream, polling on native.
- Bell dropdown with click-through via `notificationLink()` and mark-read / mark-all-read.
- Existing triggers: approval requested/decided, quote accepted/declined (rep), portal payment (rep), order
  auto-confirmed, fully reserved, reservation taken, short pick, delivery created (warehouse), transfer inbound
  (warehouse), follow-ups due, adjustment could not be posted.

## 1. Notification catalogue

"New" = not built yet; "existing" = fires today and stays. Links are resolved by the frontend from
`entityType`/`entityId` (section 2).

| Recipient | Event | Type | Status | entityType → opens |
|---|---|---|---|---|
| Admins (`toRole(ADMIN)`) | Payment awaiting verification — recorded by a non-admin, or uploaded via portal | `PAYMENT_SUBMITTED` | new for admin | `ORDER` → `/orders/:id` |
| | Product crosses into low stock after a reservation | `LOW_STOCK` | new | `PRODUCT` → `/inventory` |
| | Product crosses into out of stock after a reservation | `OUT_OF_STOCK` | new | `PRODUCT` → `/inventory` |
| | Confirmed order reserved with a shortfall | `ORDER_SHORT` | new | `ORDER` |
| | Customer declined a quote in the portal | `QUOTE_DECLINED` | new for admin | `ORDER` |
| | Released order cancelled or revised (its PICKING delivery order is cancelled) | `RELEASED_ORDER_CHANGED` | new | `ORDER` |
| | Approval requested | `APPROVAL_REQUESTED` | existing, link changes (section 3) | subject's record |
| Rep (order's `repId`, else `createdBy`) | Payment verified (incl. a payment an admin records directly on their order) | `PAYMENT_VERIFIED` | new | `ORDER` |
| | Payment rejected, body = reason | `PAYMENT_REJECTED` | new | `ORDER` |
| | Monthly target reached 50%, 80%, 100% | `TARGET_MILESTONE` | new | `SALES_TARGET` → `/dashboard` |
| | Order released to the warehouse | `ORDER_RELEASED` | new | `ORDER` |
| | Order delivered (FULFILLED) | `ORDER_DELIVERED` | new | `ORDER` |
| | Portal accept/decline, portal payment, short pick, fully reserved, reservation taken, approval decided | existing types | existing | as today |
| Warehouse users of the warehouse (`users.idsForWarehouse`) | Delivery order ready to pick | `DELIVERY_CREATED` | existing | `DELIVERY_ORDER` |
| | Transfer inbound | `TRANSFER_INBOUND` | existing | `TRANSFER` |
| | Released order cancelled or revised | `RELEASED_ORDER_CHANGED` | new | `DELIVERY_ORDER` → `/delivery-orders/:id` |
| | Low / out of stock, for the reserving order's ship-from warehouse | `LOW_STOCK` / `OUT_OF_STOCK` | new | `PRODUCT` |
| | Their write-off approved or rejected | `APPROVAL_DECIDED` | existing (generic decide notifies the requester); verify in tests, add only if missing | `STOCK_ADJUSTMENT` → `/stock` |

### Rules

- **Low / out of stock** uses the Inventory page rule (`InventoryOverviewService`): product-wide
  `available = sum(on_hand) - sum(reserved)`; OUT when `available <= 0`, LOW when `available <= reorder_level`.
  It fires only on a crossing within one reservation: `before > level && after <= level` (LOW) or
  `before > 0 && after <= 0` (OUT). Crossing both at once sends OUT only. Products with no `reorder_level` never
  send LOW. No state is stored; the next alert needs the product to recover and drop again.
- **Target milestones** are checked when a payment becomes VERIFIED (verify, or admin record), because
  `netSales` only moves then. Thresholds are 50, 80, 100 of `Commission.progressPct`. `before` = progress
  without this payment, `after` = with it; notify the highest threshold with `before < t <= after`, at most one
  notification per payment. Month = the payment's `verified_at` month (Asia/Manila, same as `InsightsQueries`).
  No target or a zero target → nothing.
- **No self-notification.** `Notifications.send` removes the current staff user's id from the recipients.
  System actions (no staff principal) and portal contacts remove nobody.

## 2. Notifications page, bell, routing (frontend)

- New route `/notifications` → `NotificationsPage`, not added to `NAV` or mobile tabs.
  - Newest first from `GET /api/notifications` (paged), "Load more" at the end.
  - Chips All / Unread (Unread passes `unread=true`); "Mark all read".
  - Unread rows bold; clicking a row marks it read and navigates via `notificationLink()`; rows without a link
    only mark read.
- Bell footer gets a "See all notifications" link to `/notifications` that closes the dropdown.
- `notificationLink()` map:
  - detail pages: `ORDER` → `/orders/:id`, `DELIVERY_ORDER` → `/delivery-orders/:id`, `TRANSFER` → `/transfers/:id`
  - list pages: `PRODUCT` → `/inventory`, `STOCK_ADJUSTMENT` → `/stock`, `SALES_TARGET` → `/dashboard`,
    `FOLLOW_UP` → `/follow-ups`
  - `APPROVAL` → `/inbox` is removed.
- Inbox removal: delete `InboxPage.tsx` and its test; remove the Inbox `NAV` item and the `approvals` sidebar
  badge (and its query in `Sidebar.tsx`); mobile Inbox tab becomes Dashboard (`/dashboard`); `/inbox` route
  becomes `<Navigate to="/notifications" replace/>`.
- The bell's SSE handler keeps invalidating `["approvals"]` so `PendingApprovals` refreshes live.
- Out of scope: deleting notifications, retention/pruning.

## 3. Approvals on record pages

### Backend

- `GET /api/approvals` gains optional `subjectType` and repeatable `subjectId` filters (combined with the
  existing `status` / `mine`).
- `Approvals.request` and `Approvals.decide` notify with the record that shows the approval, not `APPROVAL`:

  | Approval subject | Notification entity |
  |---|---|
  | `ORDER` (DISCOUNT, BELOW_COST, CREDIT_LIMIT) | `ORDER` / subjectId |
  | `INVOICE` (INVOICE_VOID) | `ORDER` / `select order_id from invoice where id = subjectId` |
  | `STOCK_ADJUSTMENT` (STOCK_WRITE_OFF) | `STOCK_ADJUSTMENT` / subjectId |
  | anything else | `APPROVAL` / approval id (no link) |

### Frontend

- New `features/approvals/PendingApprovals.tsx`, built from the Inbox logic: lists PENDING approvals for given
  subjects with `ApprovalCard`; admins get Approve and Reject (reject dialog, note required); the requester gets
  Cancel on their own. Renders nothing when the list is empty.
- `OrderWorkspace`: `PendingApprovals` at the top for subject ORDER = order id plus subject INVOICE = the order's
  invoice ids.
- `StockPage`: `PendingApprovals` at the top for subject STOCK_ADJUSTMENT, titled "Write-offs awaiting approval".
- `ApprovalBanner` (requesting) is unchanged.
- Accepted trade-off: there is no single "all pending approvals" page; admins use Unread on `/notifications`.

## 4. Backend changes

- `NotificationType`: add `PAYMENT_VERIFIED`, `PAYMENT_REJECTED`, `LOW_STOCK`, `OUT_OF_STOCK`, `ORDER_SHORT`,
  `ORDER_RELEASED`, `ORDER_DELIVERED`, `RELEASED_ORDER_CHANGED`, `TARGET_MILESTONE`. `notification.type` is
  `text` without a check constraint: no migration.
- `Notifications.send`: drop the current staff user from recipients. `list(userId, unreadOnly, page)` +
  repository `findByRecipientUserIdAndReadAtIsNull`; controller `@RequestParam(defaultValue = "false") boolean unread`.

| Trigger | Change |
|---|---|
| Rep records a pending payment | `PaymentService.record`, non-admin branch: `toRole(ADMIN, PAYMENT_SUBMITTED, ORDER, …)` |
| Portal payment | `PortalOrderService.submitPayment`: also `toRole(ADMIN, PAYMENT_SUBMITTED, …)`; rep call kept |
| Payment verified | `PaymentService.verify`, and `record` admin branch: `toUser(rep, PAYMENT_VERIFIED, …)`; publish `PaymentVerified` |
| Payment rejected | `PaymentService.reject`: `toUser(rep, PAYMENT_REJECTED, …, body = reason)`. Not for `rejectPendingFor` (order cancelled) |
| Target milestones | New `PaymentVerified(long repId, long paymentId, Instant verifiedAt)` record in `sales`. New `insights/TargetMilestones` `@EventListener` (same transaction): `after = InsightsQueries.netSales(rep, month)`, `before = after - this payment's credit amount`, target from `TargetService.find`, apply the rule in section 1. Reads `InsightsQueries` directly, never the cached `InsightsService`. Skipped when the order has no rep |
| Low / out of stock, order short | `Inventory.reserve`: read product-wide available for the affected products before allocating and after; notify crossings to admins + `idsForWarehouse(shipFrom)`; if `totalShort(orderId) > 0` afterwards, `toRole(ADMIN, ORDER_SHORT, ORDER, …)` |
| Quote declined | `PortalOrderService` decline: also `toRole(ADMIN, QUOTE_DECLINED, …)` |
| Released | `OrderService.release`: `toUser(rep, ORDER_RELEASED, …)` |
| Delivered | `DeliveredListener.on`, after FULFILLED: `toUser(rep, ORDER_DELIVERED, …)` |
| Released order changed | `Fulfilment.cancelForOrder`, inside the `ifPresent` (only when a PICKING DO is cancelled — both cancel and revise call it): `toRole(ADMIN, …, ORDER, orderId)` and `toUser(each idsForWarehouse(d.warehouseId), …, DELIVERY_ORDER, d.id)` |
| Approval links | `Approvals.request` / `decide`: map subject per section 3 |
| Approval filters | `ApprovalController.list` / `Approvals.list`: `subjectType`, `subjectId` |

- Contract: `gradlew test --tests '*OpenApiSnapshotTest' -DupdateSnapshot=true`, then `npm run gen:api` in the
  frontend.

## 5. Testing

Backend (extend existing ITs; one assertion per trigger on recipient + type + entity):

- `NotificationsIT`: self-action sends nothing; `unread=true` filter.
- Payments (`PortalOrdersIT` / order payment ITs): pending record → admins; portal → admins + rep; verify →
  rep; reject → rep with reason.
- `TargetMilestones`: 40% → 85% sends exactly one, for 80%; 85% → 90% sends none; 95% → 100% sends hit; no
  target sends none.
- `ReservationsIT`: crossing to LOW notifies admins + ship-from warehouse; a second reservation still below
  the line sends nothing; crossing to zero sends OUT only; short reservation → `ORDER_SHORT`.
- `ApprovalsIT`: notification entity for ORDER, INVOICE (→ its order), STOCK_ADJUSTMENT subjects; list
  filters by subject.
- `DeliveryOrdersIT`: release → rep; delivered → rep; cancel/revise of a released order → admins + warehouse.

Frontend:

- `NotificationsPage`: renders list, Unread chip, click marks read and navigates.
- `NotificationBell`: "See all notifications" link.
- `PendingApprovals` on the order page: admin approves/rejects, requester cancels, hidden when empty.
- `nav.test.ts` / `MorePage.test.tsx` / `Header.test.tsx`: no Inbox; mobile tab is Dashboard; `/inbox`
  redirects.
- `notificationLink` covers every entity type in the map.
