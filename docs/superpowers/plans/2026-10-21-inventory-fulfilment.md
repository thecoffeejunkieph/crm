# Redesign #3 Inventory & Fulfilment — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stock per product per warehouse with a movement ledger, reservations at Confirm, receipts, adjustments,
transfers, and delivery orders that replace the manual Release/Fulfil stubs, scanned on the Android app.

**Architecture:** Two new Spring modules on the Foundation conventions. `inventory` owns stock levels, the ledger,
receipts, adjustments, transfers, reservations and the `Allocator`, and every write goes through one locked ledger
path. `fulfilment` owns delivery orders and depends on `inventory`. `sales` calls both facades and learns of delivery
through an `OrderDelivered` event, so nothing below `sales` depends on it. A React feature folder `fulfilment` adds the
Fulfil screens. The native scanner is a Capacitor plugin used only on Android.

**Tech Stack:** As #2 (Java 21, Spring Boot 4.0.6, Postgres 17, Flyway, Testcontainers, Modulith-test, zxing 3.5.3,
openhtmltopdf 1.1.97; React 19, TanStack Query 5, react-hook-form + zod, Vitest, Playwright, Capacitor 8). One
addition: `@capacitor-mlkit/barcode-scanning`, using the major whose `peerDependencies` accept `@capacitor/core` 8.

**Spec:** `docs/superpowers/specs/2026-10-21-inventory-fulfilment-design.md`. Read it before any task. `§n` refers to
it. The #2 plan (`2026-10-14-sales-lifecycle.md`) defines every convention used below that this plan doesn't restate.

## Global Constraints

- Branch `redesign/inventory` from the current HEAD in both `crm` and `crm-dashboard-ui`. Use worktrees at
  `.worktrees/redesign-inventory`, because `crm`'s main checkout has uncommitted local changes that must not be
  touched.
- Modules:
  - `inventory` depends on `catalog`, `warehouses`, `approvals`, `settings`, `audit`, `notifications`, `identity`
    (`Users`) and `shared`.
  - `fulfilment` depends on `inventory`, `files`, `audit`, `notifications`, `identity` and `shared`.
  - `sales` adds `inventory` and `fulfilment`.
  - Nothing depends on `sales`. `ModularityTest` must stay green.
- Quantities `numeric(12,3)` / `BigDecimal` with scale 3. Compare with `compareTo`, never `equals`. Money as in #2.
- Document numbers `<PREFIX>-<YYYY>-%05d` (Manila year from `SalesTime.MANILA` + `Clock`). The serial comes from
  `document_counter` rows `STOCK_RECEIPT` (`SR`), `STOCK_ADJUSTMENT` (`ADJ`), `STOCK_TRANSFER` (`TR`) and
  `DELIVERY_ORDER` (`DO`).
- Every `stock_level` write goes through `StockLedger.post(...)` (Task 2). No other class updates `on_hand` or
  `reserved`. When several rows are touched, lock them in `(product_id, warehouse_id)` order.
- Error codes:
  - new, all `UNPROCESSABLE_CONTENT`: `INSUFFICIENT_STOCK`, `ORDER_SHORT`, `PICK_INCOMPLETE`, `PROOF_REQUIRED`;
  - reused: `INVALID_STATE`, `VALIDATION_FAILED`, `NOT_FOUND`, `STALE_VERSION`.
- Scoping (§6):
  - WAREHOUSE acts only where `Scope.warehouseIds` contains the warehouse.
  - Anything out of scope → 404 `NOT_FOUND`.
  - SALES reads stock everywhere and acts on nothing in `inventory` except cover-shortage on its own orders.
- Every task that adds or changes an endpoint, in the same commit:
  - adds its rows to `SecurityMatrixTest.MATRIX`;
  - regenerates `openapi.json` (`./gradlew test --tests '*OpenApiSnapshotTest' -DupdateSnapshot=true`);
  - adds create-POST paths to `IdempotencyFilter.DEFAULT_PATHS`.
- Audit entity types: `STOCK_RECEIPT`, `STOCK_ADJUSTMENT`, `TRANSFER`, `DELIVERY_ORDER`. Ship-from changes and
  cover-shortage audit under `ORDER`.
- `cost` never reaches a SALES or WAREHOUSE response. That includes the adjustment approval's `requestedValue`, which
  carries `qty` and `value` only for the ADMIN approver; the approvals list already hides it from other roles.
- Frontend:
  - generated types only (`npm run gen:api` with
    `OPENAPI_PATH="C:/Users/The Coffee Junkie/Documents/Codes/crm/crm/.worktrees/redesign-inventory/openapi.json"`);
  - quantities shown with up to 3 decimals and the product unit (`2.5 kg`) via `numberFormat.ts`.

## Review Focus

- **Double-tap Dispatch on a phone.** The second request must be 422 `INVALID_STATE` with stock deducted once, never
  twice → test in Task 6.
- **Same product on two lines of one order.** Both lines reserve against one `stock_level`, the total reserved never
  exceeds on hand, and the second line is the one that goes short → test in Task 2.
- **Product archived while it has stock or reservations.** Its stock row stays visible with history, existing
  reservations still dispatch, and a new receipt for it is 400 → test in Task 3.
- **Warehouse user assigned to only one end of a transfer.** A destination-only user can't dispatch it and a
  source-only user can't receive it; both get 404, not 403 → test in Task 5.
- **Bad proof photo** (a PDF, or larger than the upload limit). Deliver is 400 and the delivery order stays
  `DISPATCHED` with nothing stored → test in Task 6.

---

# Part A — Backend (`crm`)

### Task 1: Schema V5, settings and shared document numbers

**Files:**
- Create: `src/main/resources/db/migration/V5__inventory_fulfilment.sql`,
  `shared/DocumentCounters.java`
- Modify: `settings/SettingKey.java`, `sales/InvoiceService.java` (use `DocumentCounters`),
  `shared/ErrorCode.java`, `audit/AuditAction.java`, `notifications/NotificationType.java`
- Test: `shared/InventorySchemaIT.java`, `settings/SettingsIT.java` (add cases), `sales/InvoiceNumberingIT.java`
  (must stay green unchanged)

**Interfaces:**
- Tables exactly as spec §2. Additional details:
  - `stock_level` has `CHECK (reserved >= 0 AND on_hand >= reserved)` and is unique on `(product_id, warehouse_id)`.
  - `stock_reservation.short_qty >= 0`, `reserved_qty >= 0`, and both can't be 0 at once (`CHECK`).
  - `delivery_order` gets a partial unique index on `(sales_order_id) WHERE status <> 'CANCELLED'`.
  - `stock_transfer` has `CHECK (from_warehouse_id <> to_warehouse_id)`.
  - Indexes: `stock_movement (product_id, warehouse_id, created_at)`, `stock_reservation (product_id, warehouse_id)`,
    `stock_reservation (sales_order_id)`, `delivery_order (warehouse_id, status)`,
    `stock_transfer (status)`.
  - `REVOKE UPDATE, DELETE, TRUNCATE ON stock_movement FROM crm_app`.
- `sales_order`:
  - add `ship_from_warehouse_id bigint REFERENCES warehouse(id)`, backfilled with the default warehouse, then
    `SET NOT NULL`;
  - add `delivery_method text NOT NULL DEFAULT 'DELIVERY' CHECK (delivery_method IN ('DELIVERY','PICKUP'))`.
- `approval_request.type` CHECK is unchanged: `STOCK_WRITE_OFF` already exists.
- `INSERT INTO document_counter` the four rows with `1`.
- Settings:
  - `DELETE` the `approval.writeOffQtyMax` row and insert `('approval.writeOffValueMax', '5000')`;
  - `SettingKey`: replace `APPROVAL_WRITE_OFF_QTY_MAX` with
    `APPROVAL_WRITE_OFF_VALUE_MAX("approval.writeOffValueMax", number(BigDecimal.ZERO, null))`.
- `public final class DocumentCounters` (`@Component`):
  - `public long next(String name)`: the single-statement `update document_counter … returning` that
    `InvoiceService` uses today, moved here;
  - `public String format(String prefix, String counter)` → `prefix-YYYY-%05d`.
  - `InvoiceService` calls `next("INVOICE")` and keeps its own `SI-%05d` format.
- `ErrorCode` gains the four codes in Global Constraints.
- `AuditAction` gains `STOCK_RECEIVED`, `STOCK_ADJUSTED`, `TRANSFER_CREATED`, `TRANSFER_DISPATCHED`,
  `TRANSFER_RECEIVED`, `TRANSFER_CANCELLED`, `DELIVERY_CREATED`, `PICKED`, `DISPATCHED`, `DELIVERED`,
  `DELIVERY_CANCELLED`, `SHIP_FROM_CHANGED`, `SHORTAGE_COVERED`.
- `NotificationType` gains `ORDER_RESERVED`, `SHORT_PICK`, `RESERVATION_TAKEN`, `DELIVERY_CREATED`,
  `TRANSFER_INBOUND`.

- [ ] **Step 1: Write failing tests**
  - `InventorySchemaIT`, run as `crm_app` through JdbcTemplate:
    - `reservedAboveOnHandRejected`;
    - `movementCannotBeUpdatedOrDeleted`;
    - `secondLiveDeliveryOrderPerOrderRejected`: a `CANCELLED` row plus a new one is fine;
    - `transferToSameWarehouseRejected`;
    - `existingOrdersBackfilledWithDefaultWarehouse`;
    - `countersSeeded`.
  - `SettingsIT`:
    - `writeOffValueMaxDefaults5000`;
    - `oldWriteOffQtyKeyGone`: a PUT of `approval.writeOffQtyMax` → 400.
- [ ] **Step 2:** Run `./gradlew test --tests '*InventorySchemaIT' --tests '*SettingsIT'`. Expected: FAIL.
- [ ] **Step 3:** Write the migration, `DocumentCounters` and the enum additions. Point `InvoiceService` at
  `DocumentCounters`.
- [ ] **Step 4:** Run the same command, then `./gradlew test`. Expected: all PASS, `InvoiceNumberingIT` included.
  Regenerate the snapshot, since the setting key changed.
- [ ] **Step 5:** Commit `feat(db): inventory and fulfilment schema, write-off value setting`.

### Task 2: Ledger, reservations and the Allocator

**Files:**
- Create in `inventory/`: `package-info.java`, `StockLevel.java`, `StockLevelRepository.java`,
  `StockMovement.java`, `StockMovementRepository.java`, `MovementType.java`, `StockLedger.java`,
  `StockReservation.java`, `StockReservationRepository.java`, `Allocator.java`, `Inventory.java`,
  `OrderStock.java`, `ReserveLine.java`, `LineStock.java`
- Test: `inventory/AllocatorIT.java`, `inventory/ReservationsIT.java`, `inventory/InventoryFixture.java`

**Interfaces:**
- `enum MovementType { RECEIPT, ADJUSTMENT, TRANSFER_OUT, TRANSFER_IN, DISPATCH }` (public).
- `StockLedger` is package-private and runs inside the caller's transaction (`@Transactional(propagation = MANDATORY)`):
  - `StockLevel lock(long productId, long warehouseId)` → `SELECT … FOR UPDATE`. It inserts the row with zeros first
    when it's missing (`INSERT … ON CONFLICT DO NOTHING`, then select).
  - `void post(MovementType t, long productId, long warehouseId, BigDecimal qty, String refType, long refId, String reason, Long linkedOrderId)`
    locks the row, inserts the movement, and applies `qty` to `on_hand`. When `qty > 0` it then calls
    `allocator.allocate(productId, warehouseId, linkedOrderId)`. A negative result, or `on_hand < reserved`,
    throws `INSUFFICIENT_STOCK`. The decrease-below-reserved exception is Task 4's.
- `Allocator.allocate(long productId, long warehouseId, Long linkedOrderId)` (package-private):
  - fills short reservations in the order linked order first, then `confirmed_at`, then `sales_order_id`, then
    `order_line_id`;
  - moves qty `short → reserved` and raises `stock_level.reserved`;
  - collects order ids whose total short reached 0 and, after commit, sends `ORDER_RESERVED` to `rep_user_id`:
    "Order {number} is fully reserved".
- `public record ReserveLine(long orderLineId, long productId, BigDecimal qty)`.
- `public record OrderStock(long orderId, String orderNumber, long repUserId, long warehouseId, Instant confirmedAt, List<ReserveLine> lines)`.
- `public record LineStock(long orderLineId, BigDecimal reservedQty, BigDecimal shortQty)`.
- `Inventory` (public facade, `@Transactional`):
  - `void reserve(OrderStock o)`: per line in `(productId, orderLineId)` order, reserve `min(qty, available)` and
    record the rest as short.
  - `void adjust(OrderStock o)`:
    - If the existing reservations are at a different warehouse, `release(o.orderId())` then `reserve(o)`.
    - Otherwise diff by `orderLineId`. A removed line frees all of it. A decrease frees short first, then reserved (§3). An increase reserves what's available and adds the rest to short.
    - Re-run `allocate` for every level that freed stock.
  - `void release(long orderId)`: delete the reservations, lower `reserved`, re-run `allocate` per level.
  - `Map<Long, LineStock> stockFor(long orderId)` and `BigDecimal totalShort(long orderId)`.

- [ ] **Step 1: Write failing tests**
  - `InventoryFixture` seeds products, warehouses and on-hand stock, posting `RECEIPT` through `StockLedger`.
  - `AllocatorIT`:
    - `linkedOrderFilledFirst`;
    - `thenOldestConfirmed`;
    - `partialFillLeavesRemainderShort`;
    - `fractionalQuantities`: on hand 2.5 kg, two orders of 1.25 → both reserved, available 0.000;
    - `fullyReservedNotifiesRepAfterCommit`.
  - `ReservationsIT`:
    - `reserveTakesAvailableRestShort`;
    - `concurrentConfirmsNeverOversell`: two threads, each with its own transaction, reserve 5 of the last 5 kg →
      one reserved 5 and one short 5, and `on_hand >= reserved` holds;
    - `sameProductTwoLines` (Review Focus): on hand 6, lines of 4 and 4 → line 1 reserved 4, line 2 reserved 2 /
      short 2;
    - `adjustDecreaseFreesShortFirst`;
    - `adjustRemovedLineFreesAndReallocates`: another order's short fills;
    - `adjustNewWarehouseMovesReservations`;
    - `releaseFreesAndReallocates`.
- [ ] **Step 2:** Run `./gradlew test --tests '*AllocatorIT' --tests '*ReservationsIT'`. Expected: FAIL.
- [ ] **Step 3:** Implement. Run `ModularityTest` too.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Commit `feat(inventory): stock ledger, reservations and allocation`.

### Task 3: Stock reads, availability and receipts

**Files:**
- Create in `inventory/`: `StockController.java`, `InventoryPermissions.java`, `StockLevelResponse.java`,
  `MovementResponse.java`, `AvailabilityResponse.java`, `StockReceipt.java`, `StockReceiptLine.java`,
  `StockReceiptRepository.java`, `ReceiptRequest.java`, `ReceiptResponse.java`, `ReceiptService.java`
- Modify: `SecurityMatrixTest`, `IdempotencyFilter`, snapshot
- Test: `inventory/StockReadsIT.java`, `inventory/ReceiptsIT.java`

**Interfaces:**
- `InventoryPermissions` (bean `inventoryPermissions`, same pattern as `SalesPermissions`):
  - `boolean canRead(long warehouseId)`: ADMIN or SALES, or WAREHOUSE assigned to it.
  - `boolean canAct(long warehouseId)`: ADMIN, or WAREHOUSE assigned to it.
  - A false result → `NOT_FOUND`.
- `GET /api/stock?warehouseId&q&lowOnly&page` → `PageResponse<StockLevelResponse>`:
  - `record StockLevelResponse(long productId, String sku, String name, ProductUnit unit, boolean archived, long warehouseId, BigDecimal onHand, BigDecimal reserved, BigDecimal available, BigDecimal short_)`.
    The JSON name is `short`, via `@JsonProperty`.
  - `lowOnly=true` → available = 0 and some reservation is short.
  - `warehouseId` is required for WAREHOUSE; missing → `VALIDATION_FAILED`.
- `GET /api/stock/{productId}/movements?warehouseId&page` → `PageResponse<MovementResponse>`, newest first:
  - `record MovementResponse(long id, MovementType type, BigDecimal qty, String refType, long refId, String refNumber, String reason, String createdByName, Instant createdAt)`.
- `GET /api/products/{id}/availability` → `List<AvailabilityResponse>` across all active warehouses:
  - `record AvailabilityResponse(long warehouseId, String warehouseName, BigDecimal onHand, BigDecimal reserved, BigDecimal available)`.
  - ADMIN and SALES only.
- `POST /api/stock/receipts` with `record ReceiptRequest(@NotNull Long warehouseId, @NotBlank String supplierName, String reference, @NotNull LocalDate receivedOn, String notes, @NotEmpty List<@Valid Line> lines)`
  and `Line(@NotNull Long productId, @Positive @Digits(integer=9, fraction=3) BigDecimal qty)`:
  - posts one `RECEIPT` per line through `StockLedger`;
  - number from `DocumentCounters.format("SR", "STOCK_RECEIPT")`;
  - audit `STOCK_RECEIVED`;
  - an archived or unknown product → 400 field `lines[i].productId`.
- `GET /api/stock/receipts?warehouseId&page` and `GET /api/stock/receipts/{id}`.

- [ ] **Step 1: Write failing tests**
  - `StockReadsIT`:
    - `availableIsOnHandMinusReserved`;
    - `lowOnlyListsShortProducts`;
    - `warehouseUserSeesOnlyAssigned` (another warehouse → 404);
    - `salesReadsAnyWarehouse`;
    - `movementsNewestFirstWithRefNumber`;
    - `availabilityPerWarehouse`;
    - `costNeverInBody`.
  - `ReceiptsIT`:
    - `receiptRaisesOnHandAndAllocates` (a short order becomes reserved);
    - `numberFormatSR` (`SR-2026-00001`);
    - `salesCannotReceive` (403 by URL rule);
    - `warehouseUserOtherWarehouse404`;
    - `qtyWithFourDecimals400`;
    - `archivedProductKeepsStockButRejectsReceipt` (Review Focus): the stock row is still listed with
      `archived=true`, and the receipt → 400;
    - `idempotentRetryCreatesOneReceipt`.
- [ ] **Step 2:** Run `./gradlew test --tests '*StockReadsIT' --tests '*ReceiptsIT'`. Expected: FAIL.
- [ ] **Step 3:** Implement. Add matrix rows (reads for ADMIN/SALES/WAREHOUSE, receipts for ADMIN/WAREHOUSE,
  availability for ADMIN/SALES). Regenerate the snapshot.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Commit `feat(inventory): stock views, availability and receipts`.

### Task 4: Adjustments with write-off approval

**Files:**
- Create in `inventory/`: `StockAdjustment.java`, `StockAdjustmentRepository.java`, `AdjustmentMode.java`,
  `AdjustmentSource.java`, `AdjustmentStatus.java`, `AdjustmentRequest.java`, `AdjustmentResponse.java`,
  `AdjustmentService.java`, `AdjustmentApprovalListener.java`
- Modify: `StockLedger.java` (decrease below reserved), `StockController.java`, `Inventory.java`, matrix,
  idempotency, snapshot
- Test: `inventory/AdjustmentsIT.java`

**Interfaces:**
- `record AdjustmentRequest(@NotNull Long warehouseId, @NotNull Long productId, @NotNull AdjustmentMode mode, @PositiveOrZero BigDecimal countedQty, BigDecimal delta, @NotBlank String reason)`.
  `COUNTED` needs `countedQty`; `DELTA` needs a non-zero `delta`. Anything else → 400.
- `POST /api/stock/adjustments` → `AdjustmentResponse(id, number, status, delta, …)`:
  - `delta = counted − on_hand` under the row lock;
  - `value = |delta| × cost`, and `delta < 0 && value > approval.writeOffValueMax` → save `PENDING_APPROVAL`;
  - for a pending adjustment, create the request with
    `approvals.requestOnBehalf(new CreateApprovalRequest(STOCK_WRITE_OFF, "STOCK_ADJUSTMENT", id, 0, {"qty": |delta|, "value": value}, reason), actor)`
    and return **201** with status `PENDING_APPROVAL`, not a 422, because the record itself was created;
  - otherwise post an `ADJUSTMENT` movement, set `POSTED`, audit `STOCK_ADJUSTED`.
- `AdjustmentApprovalListener` follows `AutoConfirmListener`'s pattern for `ApprovalDecided` with `subjectType`
  `STOCK_ADJUSTMENT`:
  - `APPROVED` → re-resolve a `COUNTED` delta against the current `on_hand` and post;
  - `REJECTED` → set `REJECTED`.
- Decrease below reserved, in `StockLedger.post` for `ADJUSTMENT` only, before applying:
  - excess = `reserved − (on_hand + qty)`; if positive, take it from reservations youngest `confirmed_at` first,
    moving it to `short_qty`;
  - send `RESERVATION_TAKEN` to each affected rep: "Stock count took {qty} {unit} of {sku} from order {number}".
- `Inventory.postSystemAdjustment(long warehouseId, long productId, BigDecimal delta, AdjustmentSource source, String reason, String refType, long refId) -> long`
  is used by Tasks 5 and 6:
  - always `POSTED`, no approval;
  - `TRANSIT_LOSS` records with no movement (spec §4 Transfers).
- `GET /api/stock/adjustments?warehouseId&status&page`.

- [ ] **Step 1: Write failing tests** in `AdjustmentsIT`:
  - `countedResolvesDelta`;
  - `increasePostsImmediately`;
  - `smallDecreasePosts`;
  - `largeDecreasePendsThenPostsOnApproval`: cost 100, threshold 5000, delta −60 → pending; approve → posted;
  - `countedRecomputedAtApproval`: a receipt of 10 between request and approval → posted delta is 10 smaller;
  - `rejectedLeavesStock`;
  - `decreaseBelowReservedTakesYoungest`: 2 orders reserved, count lower → the younger order is short and its rep
    notified;
  - `pendingResponseOmitsCostForWarehouse`;
  - `reasonRequired`.
- [ ] **Step 2:** Run `./gradlew test --tests '*AdjustmentsIT'`. Expected: FAIL.
- [ ] **Step 3:** Implement. Add matrix rows (ADMIN, WAREHOUSE). Regenerate the snapshot.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Commit `feat(inventory): counted and delta adjustments with write-off approval`.

### Task 5: Transfers and cover shortage

**Files:**
- Create in `inventory/`: `StockTransfer.java`, `StockTransferLine.java`, `StockTransferRepository.java`,
  `TransferStatus.java`, `TransferRequest.java`, `ReceiveRequest.java`, `TransferResponse.java`,
  `TransferService.java`, `TransferController.java`, `CoverShortage.java`
- Modify: `Inventory.java`, matrix, idempotency, snapshot
- Test: `inventory/TransfersIT.java`, `inventory/CoverShortageTest.java`

**Interfaces:**
- `POST /api/transfers` with `TransferRequest(fromWarehouseId, toWarehouseId, Long salesOrderId, notes, lines[{productId, qty}])`
  creates a `DRAFT` numbered `TR-…`. `PUT /api/transfers/{id}` (with `version`) edits it, `DRAFT` only.
- `POST /api/transfers/{id}/dispatch`:
  - `canAct(from)`, `DRAFT` only;
  - posts `TRANSFER_OUT` of `−qty` per line, which throws `INSUFFICIENT_STOCK` when available stock is short;
  - sends `TRANSFER_INBOUND` to the destination's warehouse users (`Users.idsForWarehouse(long)`, a new public
    method on `identity/Users`).
- `POST /api/transfers/{id}/receive` with `ReceiveRequest(lines[{lineId, receivedQty}])`:
  - `canAct(to)`, `DISPATCHED` only, and every line present;
  - `receivedQty > qty` → 400;
  - posts `TRANSFER_IN` with `linkedOrderId = salesOrderId`;
  - per gap, calls `postSystemAdjustment(from, …, TRANSIT_LOSS, "Transit loss on TR-…")`.
- `POST /api/transfers/{id}/cancel`: `DRAFT` only.
- `GET /api/transfers?status&warehouseId&page`: WAREHOUSE sees transfers where either end is assigned.
  `GET /api/transfers/{id}`.
- `CoverShortage` is pure:
  `static Map<Long, List<ReserveLine>> plan(Map<Long, BigDecimal> shortByProduct, Map<Long, Map<Long, BigDecimal>> availableByProductByWarehouse, long shipFrom)`
  → the source warehouse to its lines.
  - For each product, use the single warehouse with the most available stock if it covers the shortage.
  - Otherwise take from warehouses in descending available order until covered or exhausted.
  - Never use `shipFrom`. Break ties by lower warehouse id.
- `Inventory.coverShortage(long orderId, long actorId) -> List<TransferResponse>` builds that plan from the order's
  short reservations and creates one linked `DRAFT` per source warehouse (audit `TRANSFER_CREATED`). No short → an
  empty list. Sales owns the endpoint (Task 7).

- [ ] **Step 1: Write failing tests**
  - `CoverShortageTest`:
    - `singleSourcePreferred`;
    - `splitsOnlyWhenNoSingleCovers`;
    - `neverShipFrom`;
    - `tieBreaksByLowerId`;
    - `partialWhenNothingCovers`.
  - `TransfersIT`:
    - `dispatchMovesStockOut`;
    - `dispatchInsufficient422`;
    - `receiveLessPostsTransitLossNoMovement`: destination +8, the source ledger shows only `TRANSFER_OUT −10`, and
      the adjustment record exists;
    - `receiveMore400`;
    - `linkedOrderFilledFirstOnReceive`;
    - `cancelOnlyDraft`;
    - `destinationOnlyUserCannotDispatch404` and `sourceOnlyUserCannotReceive404` (Review Focus);
    - `destinationUsersNotifiedOnDispatch`.
- [ ] **Step 2:** Run `./gradlew test --tests '*TransfersIT' --tests '*CoverShortageTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement. Matrix: ADMIN and WAREHOUSE act; SALES reads. Regenerate the snapshot.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Commit `feat(inventory): transfers with transit loss and cover-shortage planning`.

### Task 6: Delivery orders

**Files:**
- Create in `fulfilment/`: `package-info.java`, `DeliveryOrder.java`, `DeliveryOrderLine.java`,
  `DeliveryOrderRepository.java`, `DeliveryStatus.java`, `DeliveryMethod.java`, `DeliveryRequest.java`,
  `DeliverySummary.java`, `DeliveryOrderResponse.java`, `PickRequest.java`, `OrderDelivered.java`,
  `DeliveryService.java`, `DeliveryOrderController.java`, `Fulfilment.java`
- Modify: `inventory/Inventory.java`, matrix, snapshot
- Test: `fulfilment/DeliveryOrdersIT.java`

**Interfaces:**
- `public enum DeliveryMethod { DELIVERY, PICKUP }` and `public enum DeliveryStatus { PICKING, DISPATCHED, DELIVERED, CANCELLED }`.
- `public record DeliveryRequest(long orderId, String orderNumber, long repUserId, long warehouseId, DeliveryMethod method, String customerName, String shipTo, List<Line> lines)`
  with `Line(long orderLineId, long productId, String description, ProductUnit unit, BigDecimal qty)`.
- `public record DeliverySummary(long id, String number, DeliveryStatus status, DeliveryMethod method, Instant dispatchedAt, Instant deliveredAt, String receiverName, String proofUrl)`.
  `proofUrl` is `StorageService.url(key)`.
- `public record OrderDelivered(long orderId, Instant deliveredAt)` is published from `deliver` in the same
  transaction.
- `Fulfilment` (public facade):
  - `long create(DeliveryRequest r)` → `PICKING`, numbered `DO-…`, audit `DELIVERY_CREATED`, `DELIVERY_CREATED` to
    the warehouse users;
  - `void cancelForOrder(long orderId)`: `PICKING` → `CANCELLED` (audit). `DISPATCHED`/`DELIVERED` →
    `INVALID_STATE` "This order has already left the warehouse". No delivery order → no-op;
  - `Optional<DeliverySummary> forOrder(long orderId)`.
- `Inventory` additions for this task:
  - `void consume(long orderId, String refNumber, long refId)` posts `DISPATCH` of `−qty` per reservation, lowers
    `reserved` by the same qty, deletes the reservations. Any short → `ORDER_SHORT`.
  - `void shortPick(long orderLineId, BigDecimal gap, String doNumber, long doId)` posts
    `postSystemAdjustment(…, −gap, SHORT_PICK, "Short pick on {doNumber}")`, which moves `gap` from that line's
    `reserved_qty` to `short_qty` (the adjustment lowers `on_hand` and `reserved` together), then sends
    `SHORT_PICK` to the rep.
- Endpoints:
  - `GET /api/delivery-orders?status&warehouseId&page`:
    - WAREHOUSE sees assigned warehouses;
    - SALES sees its own orders (`repUserId = actor`);
    - ADMIN sees all.
  - `GET /api/delivery-orders/{id}`.
  - `POST /{id}/pick` with `PickRequest(lines[{lineId, pickedQty}])`:
    - `PICKING` only; can be repeated;
    - `pickedQty > qty` → 400;
    - a `pickedQty` lower than the line's previous `picked_qty` (or `qty` on the first pick) calls `shortPick` for
      the difference;
    - audit `PICKED`.
  - `POST /{id}/dispatch`:
    - `PICKING` + `DELIVERY` only, otherwise `INVALID_STATE`;
    - every `picked_qty = qty`, else `PICK_INCOMPLETE`;
    - `inventory.consume`, then `DISPATCHED`, audit.
  - `POST /{id}/deliver`, multipart `photo` + `receiverName`:
    - either missing → `PROOF_REQUIRED`;
    - `DELIVERY` must be `DISPATCHED`; `PICKUP` must be `PICKING` and fully picked (consume, then deliver);
    - store with `storage.store("delivery-orders/{id}/proof", photo, StorageService.IMAGE_TYPES)` **after** the
      state checks;
    - `DELIVERED`, audit, publish `OrderDelivered`.
- The delivery-order row is locked (`findByIdForUpdate`) on every action, so a double tap serialises.

- [ ] **Step 1: Write failing tests** in `DeliveryOrdersIT`, creating delivery orders through `Fulfilment.create`
  with stock reserved via `InventoryFixture`:
  - `createNotifiesWarehouseUsers`;
  - `shortPickPostsAdjustmentAndBlocksDispatch` (`ORDER_SHORT`);
  - `repickAfterReceiptAllowsDispatch`;
  - `dispatchNeedsFullPick` (`PICK_INCOMPLETE`);
  - `dispatchDeductsAndClearsReservations`;
  - `doubleDispatchDeductsOnce` (Review Focus): two parallel dispatches → one 200 and one 422 `INVALID_STATE`, with
    exactly one `DISPATCH` movement per line;
  - `deliverNeedsPhotoAndName`;
  - `deliverPdfProof400StaysDispatched` (Review Focus): nothing in MinIO under the prefix;
  - `deliverPublishesOrderDelivered` (`@RecordApplicationEvents`);
  - `pickupDeliversFromPicking`;
  - `cancelForOrderAfterDispatch422`;
  - `warehouseUserOtherWarehouse404`;
  - `salesSeesOnlyOwnOrders`.
- [ ] **Step 2:** Run `./gradlew test --tests '*DeliveryOrdersIT'`. Expected: FAIL.
- [ ] **Step 3:** Implement. Matrix: pick, dispatch and deliver are ADMIN/WAREHOUSE; reads are all three roles.
  Regenerate the snapshot.
- [ ] **Step 4:** Run, including `ModularityTest`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(fulfilment): delivery orders with pick, dispatch and proof of delivery`.

### Task 7: Sales integration

**Files:**
- Modify in `sales/`: `SalesOrder.java`, `OrderRequest.java`, `OrderResponse.java`, `LineResponse.java`,
  `OrderAction.java`, `OrderStateMachine.java`, `OrderService.java`, `OrderController.java`, `SalesPermissions.java`,
  `PortalOrderResponse.java`, `PortalOrderService.java`
- Create: `sales/DeliveredListener.java`, `sales/OrderStockMapper.java`
- Modify tests: `sales/OrderStateMachineTest.java`, `sales/ReleaseCancelIT.java`, `sales/ReviseIT.java`; matrix,
  idempotency, snapshot
- Test: `sales/OrderStockIT.java`

**Interfaces:**
- `SalesOrder` and `OrderRequest` gain `shipFromWarehouseId` (default = `Warehouses` default on create) and
  `deliveryMethod`.
  - Changing either on `CONFIRMED` goes through revise, audit `SHIP_FROM_CHANGED`, then `inventory.adjust`.
  - Changing either on `RELEASED`+ → `INVALID_STATE`.
  - An archived warehouse → 400.
- `OrderStockMapper.of(SalesOrder o) -> OrderStock` uses `repId ?? createdBy`.
- `OrderService` hooks, each in the existing transaction:
  - confirm (both paths, including `confirmAsSystem`) → `inventory.reserve`;
  - revise → `inventory.adjust`. For `RELEASED`: `fulfilment.cancelForOrder` (which propagates `INVALID_STATE`
    after dispatch), then status `CONFIRMED`, `releasedAt = null`;
  - cancel → `fulfilment.cancelForOrder`, then `inventory.release`;
  - release → after the #2 gate, `inventory.totalShort > 0` → `ORDER_SHORT`, whose problem `details` lists
    `[{orderLineId, sku, shortQty}]`. Then `fulfilment.create(...)` with the customer's `display_name` and
    `address`.
- `OrderAction`:
  - remove `FULFIL`, its endpoint, `fulfil()` and the matrix row;
  - add `COVER_SHORTAGE`, allowed in `CONFIRMED`. It appears in `allowedActions` only when `totalShort > 0`.
- `POST /api/orders/{id}/cover-shortage` (ADMIN, SALES in scope) → `List<TransferResponse>`, audit
  `SHORTAGE_COVERED`.
- `DeliveredListener`: `@EventListener` (synchronous, same transaction) on `OrderDelivered` → `FULFILLED`,
  `fulfilledAt`, audit `FULFILLED`.
- `OrderResponse` gains:
  - `shipFromWarehouseId`, `shipFromWarehouseName`, `deliveryMethod`, `totalShort`,
    `deliveryOrder: DeliverySummary | null`;
  - `LineResponse` gains `reservedQty` and `shortQty` (null before confirm).
- `PortalOrderResponse` gains `deliveryStatus` (`PREPARING` when released and picking, `OUT_FOR_DELIVERY` when
  dispatched, `DELIVERED`, otherwise null) and `deliveredAt`. It never includes `proofUrl`.

- [ ] **Step 1: Write failing tests**
  - `OrderStockIT`:
    - `confirmReserves`;
    - `confirmShortStillConfirms`;
    - `releaseBlockedWhileShort` (`ORDER_SHORT` with details);
    - `releaseCreatesDeliveryOrder`;
    - `coverShortageDraftsLinkedTransfer`;
    - `coverShortageHiddenWhenNotShort`;
    - `reviseReleasedPickingCancelsDoAndReturnsToConfirmed`;
    - `reviseAfterDispatch422`;
    - `cancelReleasesReservations`;
    - `cancelAfterDispatch422`;
    - `shipFromChangeMovesReservations`;
    - `shipFromChangeAfterRelease422`;
    - `deliveredFulfils`;
    - `portalSeesDeliveryStatusNotProof`.
  - Update `OrderStateMachineTest` (no `FULFIL`, `COVER_SHORTAGE` on `CONFIRMED`) and `ReleaseCancelIT` (drop the
    fulfil cases; the release tests seed stock).
- [ ] **Step 2:** Run `./gradlew test --tests '*OrderStockIT' --tests '*OrderStateMachineTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement. Update the matrix (remove fulfil, add cover-shortage) and the snapshot.
- [ ] **Step 4:** Run `./gradlew test`. Expected: all PASS.
- [ ] **Step 5:** Commit `feat(sales): reserve on confirm, release into delivery orders, fulfil on delivery`.

### Task 8: Scan resolver, QR labels, ledger check

**Files:**
- Move: `sales/PdfRenderer.java` → `shared/PdfRenderer.java` (public class, same API); update `sales` imports.
- Create in `fulfilment/`: `ScanController.java`, `ScanResult.java`, `LabelController.java`, `LabelService.java`,
  `src/main/resources/templates/labels.html`
- Test: `fulfilment/ScanIT.java`, `fulfilment/LabelsIT.java`, `inventory/LedgerConsistencyIT.java`

**Interfaces:**
- Codes parse with `^crm:(DO|TR|PR|WH):(\d+)$`.
- `GET /api/scan/{code}` → `record ScanResult(String type, long id)` with `type` in
  `DELIVERY_ORDER, TRANSFER, PRODUCT, WAREHOUSE`. It returns 404 when the code is unparseable, unknown or out of
  scope, using the same read rules as the target's own `GET`.
- `GET /api/labels?type=DO|TR|PR|WH&ids=1,2` → `application/pdf`:
  - one 62 × 40 mm label per id, with a zxing QR (`QRCodeWriter`, 300 px, margin 1) embedded as a data-URI PNG, the
    number or SKU in large text, and the name or customer below;
  - at most 100 ids, more → 400;
  - out-of-scope ids → 404;
  - access: ADMIN and WAREHOUSE.
- `LedgerConsistencyIT.ledgerMatchesLevels`:
  - drives a mixed scenario (receipt, adjustments, a transfer with transit loss, a short pick, dispatch);
  - asserts for every `stock_level` that `on_hand = SUM(stock_movement.qty)` over the same product and warehouse;
  - asserts `reserved = SUM(stock_reservation.reserved_qty)` over the same product and warehouse.

- [ ] **Step 1: Write failing tests**
  - `ScanIT`:
    - `resolvesEachType`;
    - `garbage404`;
    - `outOfScopeDo404`.
  - `LabelsIT`:
    - `pdfContainsNumber` (via the existing `PdfText` helper);
    - `over100Rejected`;
    - `salesForbidden`.
  - `LedgerConsistencyIT.ledgerMatchesLevels`.
- [ ] **Step 2:** Run `./gradlew test --tests '*ScanIT' --tests '*LabelsIT' --tests '*LedgerConsistencyIT'`.
  Expected: FAIL.
- [ ] **Step 3:** Move `PdfRenderer`, then implement. Add matrix rows and regenerate the snapshot.
- [ ] **Step 4:** Run `./gradlew test`. Expected: all PASS.
- [ ] **Step 5:** Commit `feat(fulfilment): scan resolver, QR labels and ledger consistency check`.

---

# Part B — Frontend (`crm-dashboard-ui`)

Every task:
- regenerate types first (`npm run gen:api` with the Global Constraints path);
- finish with `npx tsc -b && npm run lint && npm test` all passing.

### Task 9: Types, navigation, settings and the fulfilment API

**Files:**
- Modify:
  - `src/api/schema.d.ts` (generated);
  - `src/app/shell/nav.ts` and `nav.test.ts`;
  - `src/app/router.tsx`;
  - `src/features/admin/api.ts`, `SettingsPage.tsx`, `settingsSchema.ts` and `settingsSchema.test.ts`;
  - `src/features/approvals/RequestApprovalDialog.test.tsx` and `useApprovalRequired.test.ts`, only if the
    generated types moved.
- Create: `src/features/fulfilment/api.ts`, `src/features/fulfilment/statusBadges.ts`

**Interfaces:**
- Nav, Fulfil group:
  - `Stock` (`/stock`, all roles);
  - `Transfers` (`/transfers`, ADMIN and WAREHOUSE);
  - `Delivery orders` (`/delivery-orders`, all roles).
- `mobileTabsFor("WAREHOUSE")` → `Deliveries` (`/delivery-orders`), `Stock` (`/stock`), `Me`. The Scan centre slot
  comes in Task 14.
- Settings:
  - `writeOffQtyMax` becomes `writeOffValueMax` (`approval.writeOffValueMax`);
  - field label "Stock write-off value needing approval above (₱)";
  - zod rule ≥ 0.
- `api.ts`: one typed function per endpoint in Tasks 3–8. Create POSTs send `Idempotency-Key` generated once per
  dialog open, as in #2.
- `statusBadges.ts`:
  - `deliveryStatusColor`: PICKING amber, DISPATCHED blue, DELIVERED green, CANCELLED red;
  - `transferStatusColor`: DRAFT gray, DISPATCHED blue, RECEIVED green, CANCELLED red;
  - `adjustmentStatusColor`: POSTED green, PENDING_APPROVAL amber, REJECTED red.

- [ ] **Step 1: Write failing tests**
  - `nav.test.ts`:
    - WAREHOUSE labels are `["Inbox", "Products", "Stock", "Transfers", "Delivery orders"]`;
    - SALES labels include `Stock` and `Delivery orders` but not `Transfers`;
    - the WAREHOUSE mobile tabs are `["Deliveries", "Stock", "Me"]`.
  - `settingsSchema.test.ts`: `writeOffValueMax: -1` is rejected.
- [ ] **Step 2–4:** `npm test` FAIL → implement → `npx tsc -b && npm run lint && npm test` PASS.
- [ ] **Step 5:** Commit `feat(ui): fulfil navigation, write-off value setting, fulfilment api`.

### Task 10: Stock page, ledger, stock in and adjust

**Files:**
- Create in `src/features/fulfilment/`: `StockPage.tsx`, `StockPage.test.tsx`, `MovementsDrawer.tsx`,
  `StockInDrawer.tsx`, `receiptSchema.ts`, `AdjustDialog.tsx`, `adjustSchema.ts`, `schemas.test.ts`
- Modify: `src/app/router.tsx`

**Interfaces:**
- `StockPage` `/stock`:
  - warehouse `Select`: WAREHOUSE gets assigned warehouses only, and the first is preselected;
  - `?warehouseId=` in the URL so a scanned `WH` code can deep-link;
  - search, and a *Low / short* toggle (`lowOnly`);
  - `DataTable` columns SKU, name, on hand, reserved, available, short. An amber `StatusBadge` "Short" appears when
    `short > 0`, and archived rows are greyed;
  - a row opens `MovementsDrawer` (paged ledger, ref number linking to the receipt, transfer or delivery order);
  - *Stock in* and *Adjust* only when the user can act on the selected warehouse.
- `receiptSchema`:
  - supplierName is required;
  - receivedOn defaults to today (Manila);
  - lines ≥ 1, each with qty > 0 and ≤ 3 decimals.
- `adjustSchema`:
  - mode `COUNTED | DELTA`;
  - counted ≥ 0, or delta ≠ 0;
  - reason is required.
- `AdjustDialog` shows the live resolved delta for `COUNTED` (counted − on hand). A `PENDING_APPROVAL` response gives
  the info toast "Sent for approval — stock changes once an admin approves."

- [ ] **Step 1: Write failing tests**
  - `schemas.test.ts`:
    - a qty with 4 decimals is rejected;
    - delta 0 is rejected;
    - a missing reason is rejected.
  - `StockPage.test.tsx`:
    - a WAREHOUSE user only sees assigned warehouses;
    - SALES sees no Stock in or Adjust buttons;
    - a short row shows the badge;
    - a pending adjust shows the approval toast.
- [ ] **Step 2–4:** FAIL → implement → PASS.
- [ ] **Step 5:** Commit `feat(ui): stock view, movements ledger, stock in and adjustments`.

### Task 11: Transfers

**Files:**
- Create in `src/features/fulfilment/`: `TransfersPage.tsx`, `TransferPage.tsx`, `TransferPage.test.tsx`,
  `transferSchema.ts`, `ReceiveDialog.tsx`
- Modify: `src/app/router.tsx`

**Interfaces:**
- `TransfersPage` `/transfers`: view tabs *Draft* (`status=DRAFT`), *In transit* (`DISPATCHED`), *Received*, *All*,
  plus *New transfer*.
- `TransferPage` `/transfers/:id`:
  - header with number, from → to, status, and a linked order chip;
  - a lines editor in `DRAFT` (product `Combobox`, `QuantityInput`);
  - *Dispatch* when the user can act on the source;
  - *Receive* when the user can act on the destination. It opens `ReceiveDialog`, with `receivedQty` per line
    prefilled to `qty` and a warning when a line is below it ("{gap} will be recorded as transit loss");
  - *Cancel* in `DRAFT`;
  - action buttons are full width below `md`.
- `transferSchema`:
  - from ≠ to;
  - lines ≥ 1;
  - qty > 0 with ≤ 3 decimals.
- A 422 `INSUFFICIENT_STOCK` shows the problem detail in a toast.

- [ ] **Step 1: Write failing tests** in `TransferPage.test.tsx`:
  - Dispatch is hidden for a destination-only user;
  - Receive prefills the qty and warns on a gap;
  - the same-warehouse schema error appears.
- [ ] **Step 2–4:** FAIL → implement → PASS.
- [ ] **Step 5:** Commit `feat(ui): transfers with dispatch and receive`.

### Task 12: Delivery orders, pick mode and proof

**Files:**
- Create in `src/features/fulfilment/`:
  `DeliveryOrdersPage.tsx`, `DeliveryOrderPage.tsx`, `DeliveryOrderPage.test.tsx`, `PickMode.tsx`,
  `PickMode.test.tsx`, `pickState.ts`, `pickState.test.ts`, `DeliverDialog.tsx`
- Modify: `src/app/router.tsx`

**Interfaces:**
- `DeliveryOrdersPage` `/delivery-orders`: views *To pick* (`PICKING`), *To dispatch* (`PICKING`, client-filtered to
  fully picked), *Out for delivery* (`DISPATCHED`), *Delivered*.
- `DeliveryOrderPage` `/delivery-orders/:id`:
  - customer, ship-to and order link;
  - lines with ordered and picked qty;
  - *Pick* → `PickMode`;
  - *Dispatch* (disabled with the reason "Pick every line first" until fully picked);
  - *Deliver*, or *Picked up* for `PICKUP` → `DeliverDialog`;
  - *Print label* → `/api/labels?type=DO&ids={id}` in a new tab.
- `pickState.ts`:
  - `applyScan(lines: PickLine[], productId: number) -> {lineId: number} | {error: "NOT_ON_ORDER"}` picks the first
    line for that product that isn't fully picked, otherwise the last line for it;
  - `isFullyPicked(lines) -> boolean`.
- `PickMode`:
  - a list of lines; tapping one (or a product scan via `applyScan`) opens a `QuantityInput` keypad prefilled with
    the remaining qty;
  - *Save picks* sends `POST /pick` with every touched line;
  - a server response that reduced a line shows "Short {gap} {unit} recorded — the order is short until stock
    arrives."
- `DeliverDialog`:
  - `<input type="file" accept="image/*" capture="environment">` (native camera on Android, file picker on desktop)
    plus receiverName, both required;
  - a 400 keeps the dialog open with the server message.

- [ ] **Step 1: Write failing tests**
  - `pickState.test.ts`:
    - a scan picks the first incomplete line of the same product;
    - an unknown product → `NOT_ON_ORDER`.
  - `PickMode.test.tsx`: a short save shows the short message.
  - `DeliveryOrderPage.test.tsx`:
    - Dispatch is disabled until fully picked;
    - a PICKUP order shows "Picked up" instead of Dispatch;
    - Deliver needs a photo.
- [ ] **Step 2–4:** FAIL → implement → PASS.
- [ ] **Step 5:** Commit `feat(ui): delivery orders with pick mode and proof of delivery`.

### Task 13: Order workspace, portal status and search

**Files:**
- Modify:
  - `src/features/sales/OrderWorkspace.tsx` and its test;
  - `orderActions.ts` and its test;
  - `LinesTab.tsx` and its test;
  - `src/features/portal/PortalOrderPage.tsx`;
  - `src/features/portal/portalOrders.test.tsx`;
  - `src/app/shell/CommandPalette.tsx` and its test.
- Create: `src/features/sales/DeliveryTab.tsx`, `src/features/sales/ShipFromCard.tsx`

**Interfaces:**
- `orderActions`:
  - remove `FULFIL` from `primaryAction`;
  - add `COVER_SHORTAGE` to `menuActions` (first);
  - `primaryAction` stays `SEND_QUOTE → CONFIRM → RELEASE`.
- `ShipFromCard`:
  - warehouse `Select` plus a Delivery/Pickup toggle;
  - editable when `allowedActions` has `EDIT` or `REVISE` and status ≠ `RELEASED`;
  - on a `CONFIRMED` order a change goes through the existing revise `ReasonDialog`.
- `LinesTab`: after confirm, each line shows a badge, *Reserved* (green) or *Short {qty} {unit}* (amber).
- Release button: when `totalShort > 0` it is disabled with the tooltip "Short {n} line(s) — cover the shortage or
  revise the order". A server `ORDER_SHORT` shows the same text.
- *Cover shortage* posts, then toasts "Drafted {n} transfer(s)" with links to each.
- `DeliveryTab` (shown once `deliveryOrder` exists): number (linked), status badge, dispatched and delivered times,
  receiver, proof photo thumbnail (opens `proofUrl`).
- Portal order page: a delivery status line — "Preparing", "Out for delivery", or "Delivered on {date}".
- Command palette: query results add delivery orders and transfers when the input matches `^(DO|TR)-`, via the list
  endpoints with `q`.

- [ ] **Step 1: Write failing tests**
  - `orderActions.test.ts`: CONFIRMED with `[RELEASE, COVER_SHORTAGE, CANCEL]` → primary RELEASE, menu
    `[COVER_SHORTAGE, CANCEL]`.
  - `OrderWorkspace.test.tsx`:
    - Release is disabled with the tooltip when short;
    - Cover shortage toasts the links;
    - the Delivery tab appears once a delivery order exists.
  - `LinesTab.test.tsx`: Short badge.
  - `portalOrders.test.tsx`: "Out for delivery".
  - `CommandPalette.test.tsx`: `DO-2026` searches delivery orders.
- [ ] **Step 2–4:** FAIL → implement → PASS.
- [ ] **Step 5:** Commit `feat(ui): order stock badges, cover shortage, delivery tab, portal delivery status`.

### Task 14: Native scan

**Files:**
- Modify: `package.json` (add `@capacitor-mlkit/barcode-scanning`), `src/app/shell/AppLayout.tsx` (fill the
  `MobileTabs` `scan` slot), `android/` (run `npx cap sync android`; add the plugin's camera permission and ML Kit
  meta-data if its README requires it)
- Create: `src/features/fulfilment/scan.ts`, `scan.test.ts`, `ScanButton.tsx`, `ScanButton.test.tsx`

**Interfaces:**
- `scan.ts`:
  - `parseCode(raw: string): {type: "DO" | "TR" | "PR" | "WH"; id: number} | null` (same regex as the backend);
  - `routeFor(r: ScanResult): string` maps `DELIVERY_ORDER` → `/delivery-orders/{id}`, `TRANSFER` →
    `/transfers/{id}`, `PRODUCT` → `/stock?productId={id}`, `WAREHOUSE` → `/stock?warehouseId={id}`.
- `ScanButton`:
  - rendered only when `isNativeShell()` and the role is WAREHOUSE or ADMIN;
  - `BarcodeScanner.scan({formats: [BarcodeFormat.QrCode]})` → `parseCode` → `GET /api/scan/{code}` → navigate;
  - in Pick mode (current route matches `/delivery-orders/:id` with pick open), a `PR` code is handed to `PickMode`
    through a small context, `ScanTarget`, instead of navigating;
  - camera permission denied → toast "Allow camera access in Settings to scan.";
  - a 404 → toast "Not found, or not in your warehouses."
- `StockPage` honours `?productId=` by filtering to that product.
- *Print labels* buttons: `TransferPage` (TR); `StockPage` row menu (PR); the WarehousesPage row menu (WH).

- [ ] **Step 1: Write failing tests**
  - `scan.test.ts`:
    - parses each type;
    - rejects `crm:XX:1` and `crm:DO:abc`.
  - `ScanButton.test.tsx` (mock `@capacitor-mlkit/barcode-scanning` and `isNativeShell`):
    - hidden on web;
    - navigates on a DO scan;
    - hands a PR scan to `ScanTarget` while picking;
    - shows the permission toast.
- [ ] **Step 2–4:** FAIL → implement → PASS. Run `npx cap sync android` and build the debug APK
  (`cd android && ./gradlew assembleDebug`). Expected: BUILD SUCCESSFUL.
- [ ] **Step 5:** Commit `feat(ui): native QR scanning and label printing`.
- [ ] **Step 6 (manual, owner):** On an Android device, scan a printed DO label and a product label in Pick mode.
  Note the result in the PR description.

### Task 15: Inventory end-to-end smoke

**Files:**
- Create: `e2e/inventory.spec.ts` (reuse the helpers from `e2e/sales.spec.ts`)

**Interfaces:**
- One scenario against the local stack (backend on the `redesign/inventory` worktree, `docker-compose.dev.yml`,
  Mailpit):
  1. ADMIN creates warehouses A (default) and B and a product.
  2. Stock in 10 at B.
  3. Create an order for 4 from A for a credit customer, then confirm → the line shows *Short 4*.
  4. Cover shortage → a draft transfer B→A; dispatch it, then receive it → the order line shows *Reserved*.
  5. Release → a delivery order appears.
  6. Pick 4, dispatch, then deliver with a fixture JPEG and a receiver name → the order shows Fulfilled.
  7. The portal contact (Mailpit link) sees "Delivered on …".

- [ ] **Step 1:** Write the spec.
- [ ] **Step 2:** Start the stack per Foundation Task 25's preconditions, then run `npm run e2e -- inventory`.
  Expected: 1 passed.
- [ ] **Step 3:** Commit `test(ui): inventory and fulfilment e2e smoke`.

---

## Explicitly deferred (not in this plan)

- Returns/RMA, purchase orders and suppliers, bins, lots and expiry, stock valuation, split shipments, couriers,
  offline mode, desktop camera scanning. See spec "Out of scope".
- POS on the order model (#4) will post its own `DISPATCH`-style movement through `StockLedger`; nothing here
  anticipates it.
- Data migration of legacy MySQL stock (#5) loads opening balances as `RECEIPT` movements with reference
  `OPENING`, so the ledger check holds from day one.
