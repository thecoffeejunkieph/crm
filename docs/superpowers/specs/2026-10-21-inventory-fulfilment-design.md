# Redesign #3 — Inventory & Fulfilment

Date: 2026-10-21
Repos: `crm` (backend), `crm-dashboard-ui` (frontend + Android app)
Builds on: `2026-10-12-foundation-design.md` (#1), `2026-10-14-sales-lifecycle-design.md` (#2)

## Context

#2 left two stubs: `release` only sets `RELEASED`, and `FULFILLED` is a manual ADMIN action. This sub-project adds
stock to the catalog, holds it for confirmed orders, and replaces both stubs with real delivery orders. It fixes the
owner's "unreliable inventory" pain: two reps can no longer sell the same stock, and every quantity change has a
recorded reason.

### Goals

- Stock per product per warehouse, with on hand, reserved and available, and a full movement history.
- Confirmed orders reserve stock. An order that can't be fully reserved is visibly short and can't be released.
- Each order ships once, from one warehouse. Transfers move stock between warehouses to cover shortages.
- Warehouse staff pick, dispatch and deliver on their phones by scanning QR codes.

## Decisions

| Topic | Decision |
|---|---|
| Ship-from | One shipping warehouse per order. Shortfalls are covered by transfers, never split shipments |
| Reservation | At Confirm. Takes what is available; the rest is recorded as short. Release requires zero short |
| Stock model | Append-only `stock_movement` ledger plus a `stock_level` balance row, updated in the same transaction under a row lock |
| Granularity | Per warehouse. No bins. A warehouse QR opens that warehouse's stock list |
| Stock in | Simple receipts (supplier name, reference, date, lines). No purchase orders or supplier master |
| Corrections | Adjustments (counted qty or delta) with a required reason. Decreases valued above a threshold need an approval |
| Delivery flow | `PICKING → DISPATCHED → DELIVERED`. Pickup collapses Dispatch and Delivered into one "Picked up" step |
| Stock leaves | At Dispatch. Delivered requires a proof photo and receiver name, and fulfils the order |
| Short pick | Picker records qty found. The gap posts as an adjustment; the order returns to short and can't dispatch |
| Transfers | Manual, plus a Cover shortage shortcut on a short order. `DRAFT → DISPATCHED → RECEIVED` |
| Allocation | Incoming stock goes to the linked order first, then to short reservations by oldest `confirmed_at` |
| Quantities | `numeric(12,3)`, matching `order_line.qty` |
| QR codes | `crm:DO:{id}`, `crm:TR:{id}`, `crm:PR:{productId}`, `crm:WH:{warehouseId}`. No secrets; scanning only navigates |
| Scanning | Android app: `@capacitor-mlkit/barcode-scanning`. Desktop browser: no camera scan; type-or-tap and Ctrl+K |

## Out of scope

Returns and RMAs, purchase orders and suppliers, bin locations, batch/lot/expiry tracking, stock valuation (FIFO or
average cost), split shipments, courier integration, offline mode on the phone, and camera scanning in a desktop
browser.

---

## 1. Modules

Two new modules, in the existing package-per-module style (`ph.thecoffeejunkie.crm.<module>`, package-private
entities, one public facade per module like `Catalog` and `Warehouses`).

- **`inventory`**: stock levels, the movement ledger, receipts, adjustments, transfers, reservations and the
  `Allocator`. Public facade: `Inventory`. Reservations live here because allocation must update them in the same
  transaction as the stock level, and `inventory` may not depend on `fulfilment`.
- **`fulfilment`**: delivery orders. Public facade: `Fulfilment`. Depends on `inventory`.

`sales` calls `Fulfilment` on confirm, revise, cancel and release. `fulfilment` reports back to `sales` through a
`DeliveredEvent` (order id), so `sales` keeps owning order status. `inventory` never calls `sales`.

## 2. Data (Flyway, Postgres)

All quantities `numeric(12,3)`. All tables get `created_at`, and mutable ones get `updated_at` and `version`.

### inventory

- **`stock_level`**: `product_id`, `warehouse_id` (unique pair), `on_hand`, `reserved`.
  `CHECK (reserved >= 0 AND on_hand >= reserved)`, so available can never go below zero, even through a bug.
  Created lazily at first movement.
- **`stock_movement`**: append-only. `product_id`, `warehouse_id`, `type` in
  `RECEIPT, ADJUSTMENT, TRANSFER_OUT, TRANSFER_IN, DISPATCH`, `qty` (signed), `ref_type`, `ref_id`, `reason`,
  `created_by`, `created_at`. The app DB role gets `INSERT, SELECT` only, like `audit_event`.
- **`stock_receipt`** + **`stock_receipt_line`**: `number` (`SR-YYYY-NNNNN`), `warehouse_id`, `supplier_name`,
  `reference`, `received_on`, `notes`; lines `product_id`, `qty > 0`.
- **`stock_adjustment`**: `number` (`ADJ-YYYY-NNNNN`), `warehouse_id`, `product_id`, `mode` in `COUNTED, DELTA`,
  `counted_qty` or `delta`, resolved `delta`, `reason` (required), `source` in `MANUAL, SHORT_PICK, TRANSIT_LOSS`,
  `status` in `POSTED, PENDING_APPROVAL, REJECTED`, `approval_request_id`.
- **`stock_transfer`** + **`stock_transfer_line`**: `number` (`TR-YYYY-NNNNN`), `from_warehouse_id`,
  `to_warehouse_id` (different), `status` in `DRAFT, DISPATCHED, RECEIVED, CANCELLED`, `sales_order_id` (nullable),
  `dispatched_at/by`, `received_at/by`; lines `product_id`, `qty > 0`, `received_qty` (null until received).

Document numbers use the gapless counter from #2.

- **`stock_reservation`**: `order_line_id` (unique), `sales_order_id`, `order_number`, `rep_user_id`, `product_id`,
  `warehouse_id`, `reserved_qty`, `short_qty`, `confirmed_at` (copied from the order, for allocation order and
  notifications).

### fulfilment

- **`delivery_order`** + **`delivery_order_line`**: `number` (`DO-YYYY-NNNNN`), `sales_order_id` (unique among
  non-cancelled), `warehouse_id`, `method` in `DELIVERY, PICKUP`, `status` in
  `PICKING, DISPATCHED, DELIVERED, CANCELLED`, `dispatched_at/by`, `delivered_at/by`, `proof_key` (MinIO),
  `receiver_name`, `customer_name` and `ship_to` (snapshots of the customer's display name and address); lines `order_line_id`, `product_id`, `qty`, `picked_qty` (null until picked).

### sales

- **`sales_order.ship_from_warehouse_id`**: not null. It defaults to the default warehouse at creation and can be
  changed until Release. Changing it on a confirmed order releases and re-reserves at the new warehouse. Existing
  orders are backfilled with the default warehouse.
- **`sales_order.delivery_method`**: `DELIVERY | PICKUP`, default `DELIVERY`.

### settings

- **`approval.writeOffValueMax`**: PHP. A decrease whose `|delta| × product.cost` exceeds it needs approval.
  Default `5000`. It replaces Foundation's unused `approval.writeOffQtyMax`.

## 3. Stock rules

Every write to `stock_level` goes through `inventory`, which in one transaction:
1. locks the row (`SELECT … FOR UPDATE`, creating it if missing),
2. inserts the `stock_movement`,
3. updates `on_hand`/`reserved`,
4. if `on_hand` rose, calls `Allocator.allocate(productId, warehouseId, linkedOrderId)`.

Locks are always taken in `(product_id, warehouse_id)` order when a document touches several rows, to avoid
deadlocks.

### Allocator

`allocate` fills short reservations for that product and warehouse from `available`:
1. the reservations of the linked order (a transfer's `sales_order_id`), if any,
2. then the others by `confirmed_at`, then `sales_order_id`.

Each fill moves qty from `short_qty` to `reserved_qty` and raises `stock_level.reserved` by the same amount. When an
order's total `short_qty` reaches zero, its rep gets a notification ("Order SO-… is fully reserved").

### Reservation lifecycle (called from `OrderService`)

- **Confirm** → `reserve(order)`: per line, reserve `min(qty, available)` and record the rest as `short_qty`.
- **Revise** (CONFIRMED or RELEASED) → `adjust(order)`: diff per line. A decrease frees short first, then reserved, so stock already held stays held.
  An increase reserves what's available and adds the rest as short. Freed stock re-runs `allocate`. A revision of a
  RELEASED order whose delivery order is `PICKING` cancels that delivery order and moves the order back to
  `CONFIRMED` (re-release needed). A revision after Dispatch is refused with `INVALID_STATE`.
- **Cancel** → `release(order)`: deletes the reservations, lowers `reserved`, re-runs `allocate`. A `PICKING`
  delivery order is cancelled. After Dispatch, cancel is refused with `INVALID_STATE` (returns are out of
  scope).
- **Change ship-from** → `release` then `reserve` at the new warehouse.

## 4. Flows

### Release

`OrderService.release` keeps #2's payment/credit gate and adds: total `short_qty` must be zero, else `ORDER_SHORT`
(422, with the short lines in `details`). On success it calls `Fulfilment.createDeliveryOrder(order)`, which creates
the delivery order in `PICKING`, one line per order line, and notifies WAREHOUSE users assigned to the warehouse.

### Pick

`POST /delivery-orders/{id}/pick` with `{lines: [{lineId, pickedQty}]}`, in `PICKING`. It can be sent more than once
until dispatch.
- `pickedQty > qty` → `400 VALIDATION_FAILED`.
- `pickedQty < qty` is a short pick. In one transaction:
  - post a `SHORT_PICK` adjustment of `pickedQty − qty` on the warehouse (reason "Short pick on DO-…"), with no
    approval needed;
  - move that amount from the line's reservation to `short_qty`;
  - notify the rep.
- The adjustment lowers `on_hand` and `reserved` together. The short gap then fills by allocation like any other
  shortage (a stock-in, a transfer, or the rep revising the order down). Re-picking the line after the gap is filled
  is allowed.

### Dispatch

`POST /delivery-orders/{id}/dispatch`. It requires every line `picked_qty = qty` (else `PICK_INCOMPLETE`) and no
short reservations on the order (else `ORDER_SHORT`). Per line it posts a `DISPATCH` movement of `−qty` and lowers
`reserved` by `qty`. Then it deletes the reservations and sets `DISPATCHED`.

### Deliver

`POST /delivery-orders/{id}/deliver`, multipart: `photo` (image, validated like other uploads) and `receiverName`.
Both are required (`PROOF_REQUIRED`). The photo is stored at `delivery-orders/{id}/proof/{uuid}.{ext}` and the order
moves to `DELIVERED`. Publishes `DeliveredEvent` → `sales` sets the order `FULFILLED`.

For `PICKUP` orders, `deliver` is allowed straight from `PICKING` once fully picked. It does the dispatch postings and
the delivery in one step, and the UI labels it "Picked up".

`POST /orders/{id}/fulfil` is removed.

### Stock in

`POST /stock/receipts` (Idempotency-Key) posts `RECEIPT` movements per line, which triggers allocation.

### Adjustment

`POST /stock/adjustments` with `{warehouseId, productId, mode, countedQty | delta, reason}`.
- `COUNTED` resolves `delta = counted − on_hand` at the moment of posting.
- Increases, and decreases at or under the threshold, post immediately.
- Decreases over the threshold create an `approval_request` (the existing type `STOCK_WRITE_OFF`, subject type `STOCK_ADJUSTMENT`, subject the adjustment) and
  return `PENDING_APPROVAL`. Approval posts it. A `COUNTED` adjustment re-resolves its delta at approval time.
  Rejection sets `REJECTED`.
- A decrease that would take `on_hand` below `reserved` first takes the excess out of reservations: youngest
  `confirmed_at` first, moving it to `short_qty`, and notifying those reps. Then it posts. It is never refused for
  that reason.

### Transfers

- `POST /transfers` creates a `DRAFT`. Lines can be edited while it is a draft.
- `/dispatch` posts `TRANSFER_OUT` at the source. It needs available stock at the source, else `INSUFFICIENT_STOCK`.
- `/receive` with `{lines: [{lineId, receivedQty}]}` posts `TRANSFER_IN` of `receivedQty` at the destination, which
  allocates with the transfer's `sales_order_id` first. Where `receivedQty < qty`, a `TRANSIT_LOSS` adjustment
  records the gap against the source with no approval needed. The stock already left at
  dispatch, so it posts no movement: it is a record of the loss, not a balance change. `receivedQty > qty` → `400 VALIDATION_FAILED`.
- `/cancel` works only from `DRAFT`.
- `POST /orders/{id}/cover-shortage` drafts a transfer to the ship-from warehouse, linked to the order. Per short
  line it picks the warehouse with the most available stock and splits across warehouses only if no single one
  covers the line. That means one draft per source warehouse. Returns the created transfers.

## 5. API

ProblemDetail errors, the scoping rules of §6, and Idempotency-Key on every create POST.

| Method | Path |
|---|---|
| `GET` | `/api/stock?warehouseId&q&lowOnly&page` — levels with on hand, reserved, available |
| `GET` | `/api/stock/{productId}/movements?warehouseId&page` |
| `GET` | `/api/products/{id}/availability` — per warehouse, for the order workspace |
| `POST` | `/api/stock/receipts` · `GET /api/stock/receipts?warehouseId` · `GET /api/stock/receipts/{id}` |
| `POST` | `/api/stock/adjustments` · `GET /api/stock/adjustments?warehouseId&status` |
| `GET`, `POST` | `/api/transfers` · `GET`, `PUT /api/transfers/{id}` |
| `POST` | `/api/transfers/{id}/dispatch`, `/receive`, `/cancel` |
| `POST` | `/api/orders/{id}/cover-shortage` |
| `GET` | `/api/delivery-orders?status&warehouseId&page` · `GET /api/delivery-orders/{id}` |
| `POST` | `/api/delivery-orders/{id}/pick`, `/dispatch`, `/deliver` |
| `GET` | `/api/scan/{code}` → `{type: "DELIVERY_ORDER"|"TRANSFER"|"PRODUCT"|"WAREHOUSE", id}`; 404 if unknown or out of scope |
| `GET` | `/api/labels?type&ids` — printable PDF of QR labels (zxing + the existing PDF renderer) |

`OrderResponse` gains `shipFromWarehouseId`, `deliveryMethod`, per-line `reservedQty`/`shortQty`, `totalShort`, and a
`deliveryOrder` summary. `PortalOrderResponse` gains `deliveryStatus` (`PREPARING | OUT_FOR_DELIVERY | DELIVERED`,
plus `deliveredAt`) and no proof photo.

New error codes: `INSUFFICIENT_STOCK`, `ORDER_SHORT`, `PICK_INCOMPLETE`, `PROOF_REQUIRED`. Reused:
`INVALID_STATE`, `VALIDATION_FAILED`, `NOT_FOUND`.

## 6. Access

| Role | Stock / movements | Receipts, adjustments | Transfers | Delivery orders | Cover shortage |
|---|---|---|---|---|---|
| ADMIN | all | all | all | all | yes |
| SALES | read, all warehouses | none | read | read, own orders | own orders |
| WAREHOUSE | assigned warehouses | assigned warehouses | create/dispatch at source, receive at destination (assigned) | assigned warehouse | no |

Out of scope → 404, as in #1. Approving a stock adjustment is ADMIN, through the existing approvals inbox.

## 7. Audit and notifications

- Movements are their own trail. Receipts, adjustments (and their approval), transfer and delivery-order status
  changes, ship-from changes and cover-shortage write `audit_event`.
- Notifications (existing SSE):
  - rep: order fully reserved; short pick; reservation taken by an adjustment;
  - warehouse users of the warehouse: delivery order created; transfer dispatched to them;
  - ADMIN: adjustment pending approval (via the approvals inbox badge).

## 8. Frontend

**Fulfil** (sidebar group, already present in `nav.ts` and empty):
- **Stock**: DataTable per warehouse (warehouse switcher): product, unit, on hand, reserved, available, low-stock
  flag (available ≤ 0 with short reservations). A row opens a drawer with the movement ledger. Actions: Stock in,
  Adjust.
- **Transfers**: list with saved views *Draft*, *In transit*, *Received*, *All*. The transfer page has a lines editor
  in Draft, and Dispatch / Receive with large tap targets.
- **Delivery orders**: list with saved views *To pick*, *To dispatch*, *Out for delivery*, *Delivered*. The page shows
  ordered and picked qty per line. **Pick mode**: scanning a product jumps to its line and opens a qty keypad;
  without a camera, tap a line. Dispatch. Deliver: camera photo + receiver name (labelled "Picked up" for pickups).
- **Labels**: print QR labels from any delivery order, transfer, product or warehouse page.

**Order workspace:**
- ship-from warehouse select and delivery method, editable until Release;
- per-line badge *Reserved* or *Short 2 kg* (amber);
- **Cover shortage** button when short;
- Release disabled with the reason while short;
- **Delivery** tab with delivery-order status, timeline and proof photo;
- Mark fulfilled removed.

**Inbox:** stock adjustments pending approval, alongside the existing approval types.

**Mobile shell:** WAREHOUSE tabs become Deliveries · Scan · Stock. The Scan slot in `MobileTabs` is filled only when
`Capacitor.isNativePlatform()`. Scan → `GET /api/scan/{code}` → navigate. In Pick mode a product scan matches a line
instead.

**Portal:** order pages show the delivery status and date.

**Search:** Ctrl+K adds delivery orders and transfers by number.

**Dependency added:** `@capacitor-mlkit/barcode-scanning`.

## 9. Testing

- **Unit:** `Allocator`:
  - the linked order goes first, then oldest confirmed;
  - a partial fill;
  - fractional quantities (2.5 kg).
- **Unit:** cover-shortage source choice: single source preferred, split only when needed.
- **Integration (Testcontainers Postgres + Redis + MinIO):**
  - two concurrent confirms on the last 5 kg → one reserved, one short 5, `available` never negative;
  - release blocked while short; allowed after a receipt fills it;
  - short pick → adjustment posted, order short again, dispatch refused;
  - transfer received with less than sent → transit-loss record, destination gets only what arrived, linked order
    filled first;
  - over-threshold adjustment pends, and posts on approval with a re-resolved counted delta;
  - a decrease below reserved takes from the youngest order;
  - deliver without a photo → `PROOF_REQUIRED`; with one → order `FULFILLED`;
  - pickup delivers straight from `PICKING`;
  - revise a released order whose delivery order is picking → delivery order cancelled, order back to `CONFIRMED`;
  - cancel after dispatch refused;
  - ledger check: for every `stock_level`, the sum of movements equals `on_hand`;
  - the security matrix and OpenAPI snapshot extended to every new endpoint.
- **UI (Vitest):** availability badges, Release disabled reason, Cover shortage, Pick-mode qty entry, Scan routing.
- **UI (Playwright e2e):** confirm a short order → cover shortage → dispatch and receive the transfer → release → pick
  → dispatch → deliver → order shows Fulfilled, and the portal shows Delivered.
- **Manual:** native scanning on an Android device.
