# Purchasing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Suppliers with credit limits and a running payable balance, purchase orders with an over-limit approval gate and receiving through existing stock receipts, and reorder suggestions with a daily reminder.

**Architecture:** New Spring Modulith module `ph.thecoffeejunkie.crm.purchasing` in the backend (`Codes/crm/crm`). It calls `inventory` only through a new public method on the `Inventory` facade, `approvals` through `Approvals.requestOnBehalf`, and reads stock/sales figures for suggestions with one SQL query (same approach as `InventoryOverviewService`). Nothing depends on `purchasing`. Frontend (`Codes/crm-dashboard-ui`) gets a `features/purchasing` folder.

**Tech Stack:** Spring Boot, JPA, JdbcTemplate, Flyway, PostgreSQL, Testcontainers ITs; React, Vite, TanStack Query, generated `api/schema.d.ts`, Vitest.

**Spec:** `docs/superpowers/specs/2026-11-24-purchasing-design.md`

## Global Constraints

- Migration file `src/main/resources/db/migration/V30__purchasing.sql`; quantities `numeric(12,3)`, money `numeric(14,2)`.
- Money math: line value = `qty × unit_cost` rounded `HALF_UP` to 2 decimals per line, then summed.
- Roles replace the spec's permission names (the codebase has roles, not permission strings): ADMIN = `PURCHASING_VIEW` + `PURCHASING_MANAGE` + `PURCHASING_PAY`. WAREHOUSE may read POs and receive POs whose warehouse passes `InventoryPermissions.canAct`. SALES has no purchasing access.
- Document numbers via `DocumentCounters.format("PO", "PURCHASE_ORDER")`; seed `INSERT INTO document_counter VALUES ('PURCHASE_ORDER', 1)`.
- Approval type `PO_OVER_CREDIT`, subject type `"PURCHASE_ORDER"`.
- Notification type `REORDER_DUE`; reminder cron `0 0 7 * * *`, zone `Asia/Manila`, recipients `Notifications.toRole(Role.ADMIN, …)`.
- Reminder dedupe state lives in table `reorder_reminder_state`, not the settings store (settings are admin-edited, validated values).
- NEAR flag at used ≥ 90% of limit; OVER when available < 0.
- Velocity window: 30 days of `DISPATCH` movements.
- Gradle from Claude's shell: `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/Users/Public/gtmp ./gradlew --no-daemon …`. `ModularityTest` already fails on baseline; compare against baseline, not green.
- Staff Today-queue tile: not built (decided here; the notification covers it).

## Review Focus

1. **Two receipts on the same PO at once** could both pass the "≤ remaining" check and over-receive. Expect: the PO row is locked (`PESSIMISTIC_WRITE`) for receive/close/cancel/send; test in Task 6.
2. **Approval decided after the PO was cancelled or re-edited** must not resurrect it. Expect: listener only changes rows still `PENDING_APPROVAL`; test in Task 5.
3. **Supplier with no credit limit** must never need approval and show `available = null`, not 0. Test in Tasks 4 and 5.
4. **Product with reorder level but no target, or target below level** must not be suggested / must be rejected with 400. Tests in Tasks 2 and 7.
5. **Archived supplier or archived product** must be rejected on PO create, line edit, and draft-from-suggestions. Test in Task 3.

---

### Task 1: Schema, Supplier CRUD, security wiring

**Files:**
- Create: `src/main/resources/db/migration/V30__purchasing.sql`
- Create: `src/main/java/ph/thecoffeejunkie/crm/purchasing/{package-info.java, Supplier.java, SupplierRepository.java, SupplierRequest.java, SupplierResponse.java, SupplierService.java, SupplierController.java}`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/identity/SecurityConfig.java` (near line 74/91)
- Modify: `src/test/java/ph/thecoffeejunkie/crm/SecurityMatrixTest.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/purchasing/SuppliersIT.java`, `PurchasingFixture.java`

**Interfaces:**
- Produces: tables `supplier`, `purchase_order`, `purchase_order_line`, `supplier_payment`, `reorder_reminder_state` (`id smallint PRIMARY KEY CHECK (id = 1)`, `product_ids bigint[] NOT NULL`, `sent_at timestamptz NOT NULL`); columns `product.preferred_supplier_id`, `product.reorder_target`, `stock_receipt.purchase_order_id`; `approval_request_type_check` re-created with `'PO_OVER_CREDIT'` added (drop/add like V23); index `purchase_order (supplier_id, status)`.
- Produces: `SupplierResponse(long id, String code, String name, String contactName, String phone, String email, String address, Integer paymentTermsDays, Integer leadTimeDays, BigDecimal creditLimit, BigDecimal openingBalance, String notes, boolean archived, SupplierCredit credit)` — `credit` is null until Task 4 fills it.
- Produces: `PurchasingFixture(JdbcTemplate, TransactionTemplate, StockLedger)` wrapping `InventoryFixture` plus `long supplier(String code, BigDecimal limit)`.
- Endpoints: `GET/POST /api/suppliers`, `GET/PUT /api/suppliers/{id}`, `POST /api/suppliers/{id}/archive`.

- [ ] **Step 1: Write the migration** exactly per the spec's data model plus the Interfaces above. Columns from spec "Data model"; `purchase_order.status` check lists the six statuses; `purchase_order_line CHECK (received_qty <= qty)`.
- [ ] **Step 2: Write failing `SuppliersIT`**: `adminCreatesAndListsSupplier` (POST returns 201, list contains code), `duplicateCodeIs409`, `negativeCreditLimitIs400`, `archivedSupplierHiddenFromDefaultList`, `salesAndWarehouseGet403` on `GET /api/suppliers`.
- [ ] **Step 3: Run** `./gradlew --no-daemon test --tests '*SuppliersIT'` → FAIL (404s).
- [ ] **Step 4: Implement** the entity/service/controller following `catalog` product CRUD; `SupplierRequest` with `@NotBlank code, name`, `@PositiveOrZero creditLimit, openingBalance`, `@PositiveOrZero paymentTermsDays, leadTimeDays`. SecurityConfig: `/api/suppliers/**`, `/api/purchasing/**` → `hasRole("ADMIN")`; `/api/purchase-orders/**` → `hasAnyRole("ADMIN","WAREHOUSE")` (service enforces ADMIN for everything except GET and receive). Add SecurityMatrixTest rows for every endpoint this plan adds (A for supplier/payment/suggestion endpoints, AW for `GET /api/purchase-orders`, `GET /api/purchase-orders/{id}`, `POST /api/purchase-orders/{id}/receipts`, A for the rest).
- [ ] **Step 5: Run** `SuppliersIT` and `SecurityMatrixTest` → PASS.
- [ ] **Step 6: Commit** `feat(purchasing): suppliers and purchasing schema`.

### Task 2: Product preferred supplier and reorder target

**Files:**
- Modify: `catalog/Product.java`, `ProductRequest.java`, `ProductResponse.java`, `Catalog.java` (save/validate), `ProductSheets.java` (import/export columns if it maps reorder level)
- Test: `src/test/java/ph/thecoffeejunkie/crm/catalog/ProductReorderFieldsIT.java`

**Interfaces:**
- Produces: `ProductRequest.preferredSupplierId (Long)`, `ProductRequest.reorderTarget (BigDecimal)`; same on `ProductResponse`.

- [ ] **Step 1: Failing tests**: `savesPreferredSupplierAndTarget`, `targetBelowReorderLevelIs400` (field `reorderTarget`), `unknownOrArchivedSupplierIs400` (field `preferredSupplierId`).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement.** Catalog may not import `purchasing`; validate the supplier with `jdbc.queryForObject("select exists(select 1 from supplier where id = ? and archived_at is null)", Boolean.class, id)`.
- [ ] **Step 4: Run** → PASS, plus existing `catalog` ITs.
- [ ] **Step 5: Commit** `feat(catalog): preferred supplier and reorder target`.

### Task 3: Purchase orders — draft, edit, cancel, read

**Files:**
- Create: `purchasing/{PurchaseOrder.java, PurchaseOrderLine.java, PoStatus.java, PurchaseOrderRepository.java, PoRequest.java, PoResponse.java, PurchaseOrderService.java, PurchaseOrderController.java}`
- Test: `src/test/java/ph/thecoffeejunkie/crm/purchasing/PurchaseOrdersIT.java`

**Interfaces:**
- Produces: `PoStatus { DRAFT, PENDING_APPROVAL, SENT, PARTIALLY_RECEIVED, RECEIVED, CANCELLED }`.
- Produces: `PoRequest(@NotNull Long supplierId, Long warehouseId, LocalDate expectedOn, String notes, @NotEmpty List<@Valid Line> lines)`, `Line(@NotNull Long productId, @Positive BigDecimal qty, @PositiveOrZero BigDecimal unitCost)` — null `unitCost` defaults to `ProductInfo.cost()` (0 when null); null `warehouseId` → default warehouse.
- Produces: `PoResponse(long id, String number, long supplierId, String supplierName, long warehouseId, PoStatus status, LocalDate expectedOn, String notes, String rejectionReason, Long approvalRequestId, Instant sentAt, boolean closedShort, BigDecimal total, List<LineResponse> lines, long version)`, `LineResponse(long id, long productId, String sku, String name, BigDecimal qty, BigDecimal unitCost, BigDecimal receivedQty)`.
- Produces: `PurchaseOrderService.createDraft(PoRequest) : PoResponse` (used by Task 7), `PurchaseOrderRepository.lockById(long) : Optional<PurchaseOrder>` with `@Lock(PESSIMISTIC_WRITE)`.
- Endpoints: `GET /api/purchase-orders?status&supplierId` (paged, `PageQuery`), `POST /api/purchase-orders`, `GET/PUT /api/purchase-orders/{id}`, `POST /api/purchase-orders/{id}/cancel`.

- [ ] **Step 1: Failing tests**: `createDraftNumbersPoAndDefaultsCostAndWarehouse` (number starts `PO-`, unitCost = product cost), `editOnlyInDraft` (PUT on SENT → 409; set status via jdbc), `cancelFromDraftAndSent`, `cancelAfterReceiptIs409` (jdbc set `received_qty`), `archivedSupplierOrProductIs400`, `warehouseUserSeesOnlyOwnWarehousePos` (list filtered by `canRead`), `warehouseUserCannotCreate` (403/404).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement.** Transition checks live in `PurchaseOrder` methods (`requireStatus(PoStatus...)`) throwing `DomainException(ErrorCode.CONFLICT, …)` naming the PO number. Audit create/cancel via `AuditLog`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(purchasing): draft purchase orders`.

### Task 4: Credit math, payments, statement

**Files:**
- Create: `purchasing/{SupplierCredit.java, CreditQuery.java, SupplierPayment.java, SupplierPaymentRepository.java, PaymentRequest.java, PaymentResponse.java, StatementResponse.java}`
- Modify: `SupplierService.java`, `SupplierController.java`
- Test: `purchasing/SupplierCreditIT.java`

**Interfaces:**
- Produces: `SupplierCredit(BigDecimal limit, BigDecimal balance, BigDecimal committed, BigDecimal available, String flag)` — `available` and `flag` null when `limit` null; `flag` ∈ `"OVER"`, `"NEAR"`, null.
- Produces: `CreditQuery.forSuppliers(Collection<Long> ids) : Map<Long, SupplierCredit>` — one SQL per call, formulas exactly as spec "Money"; used by Task 5.
- Produces: `StatementResponse(BigDecimal opening, List<Entry> entries, BigDecimal closing)`, `Entry(LocalDate date, String kind /* RECEIPT|PAYMENT */, String reference, BigDecimal amount, BigDecimal balance)`. Receipt value = Σ over the receipt's lines of qty × that PO line's unit_cost (join `stock_receipt.purchase_order_id` → `stock_receipt_line.product_id` → `purchase_order_line`).
- Endpoints: `POST/GET /api/suppliers/{id}/payments`, `DELETE /api/suppliers/{id}/payments/{paymentId}`, `GET /api/suppliers/{id}/statement?from&to`.

- [ ] **Step 1: Failing tests**: `balanceIsOpeningPlusReceivedMinusPaid` (opening 1000, PO line 10 @ 12.50 with 4 received, payment 30 → balance 1020.00), `committedCountsRemainingOnOpenPosOnly` (draft and cancelled and closed-short excluded; pending-approval included), `noLimitMeansNullAvailableAndFlag`, `nearAtNinetyPercentOverBelowZero`, `paymentDeleteIsAudited`, `statementRunningBalance`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**; supplier list/get fill `credit`. Audit payment create/delete.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(purchasing): supplier credit, payments and statement`.

### Task 5: Send with credit check and approval

**Files:**
- Modify: `approvals/ApprovalType.java` (add `PO_OVER_CREDIT`), `PurchaseOrderService.java`, `PurchaseOrderController.java`
- Create: `purchasing/PoApprovalListener.java`
- Test: `purchasing/SendPurchaseOrderIT.java`

**Interfaces:**
- Consumes: `CreditQuery.forSuppliers`, `Approvals.requestOnBehalf(CreateApprovalRequest, long userId)`, `Approvals.cancelForSubject(long approvalId)`, event `ApprovalDecided`.
- Produces: `POST /api/purchase-orders/{id}/send` → `PoResponse`; `GET /api/purchase-orders/{id}/credit-check` → `CreditCheck(BigDecimal exposure, BigDecimal limit, BigDecimal over, boolean needsApproval)` for the draft panel.
- Produces: `PurchaseOrderService.decided(long poId, boolean approved, String reason)`.

- [ ] **Step 1: Failing tests**: `withinLimitGoesSent`, `noLimitGoesSent`, `overLimitCreatesApprovalAndPending` (approval type `PO_OVER_CREDIT`, requestedValue has `total` and `over`), `approvedMovesToSent`, `rejectedReturnsToDraftWithReason`, `decisionAfterCancelChangesNothing`, `sendWithNoLinesIs400`, `cancelPendingCancelsApproval`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement.** Exposure per spec; reason text: `"PO " + number + " exceeds credit with " + supplierName + " by " + over`. Listener mirrors `AdjustmentApprovalListener` (AFTER_COMMIT, REQUIRES_NEW, filter on subject type `"PURCHASE_ORDER"`), and `decided` ignores rows not `PENDING_APPROVAL`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(purchasing): send purchase orders with credit approval`.

### Task 6: Receive and close short

**Files:**
- Modify: `inventory/Inventory.java`, `inventory/ReceiptService.java`, `inventory/StockReceipt.java` (field `purchaseOrderId`)
- Modify: `PurchaseOrderService.java`, `PurchaseOrderController.java`
- Create: `purchasing/PoReceiveRequest.java`
- Test: `purchasing/ReceivePurchaseOrderIT.java`

**Interfaces:**
- Produces: `public ReceiptResponse Inventory.receiveForPurchase(ReceiptRequest req, long purchaseOrderId)` delegating to a package-private `ReceiptService.receive(ReceiptRequest, Long purchaseOrderId)`; existing `receive(req)` calls it with null. Warehouse permission check stays inside.
- Produces: `PoReceiveRequest(@NotNull LocalDate receivedOn, String reference, @NotEmpty List<@Valid Line> lines)`, `Line(@NotNull Long lineId, @Positive BigDecimal qty)`.
- Endpoints: `POST /api/purchase-orders/{id}/receipts` → `PoResponse`; `POST /api/purchase-orders/{id}/close` → `PoResponse`.

- [ ] **Step 1: Failing tests**: `partialReceiveCreatesLinkedReceiptAndRaisesStock` (`stock_receipt.purchase_order_id` set, `on_hand` up, status PARTIALLY_RECEIVED), `fullReceiveIsReceived`, `overReceiveIs409`, `receiveDraftIs409`, `warehouseUserReceivesOwnWarehouseOnly`, `closeShortReleasesCommitted` (via `CreditQuery`), `concurrentReceivesCannotOverReceive` (two threads, each receiving the full remaining qty; exactly one succeeds), `receiptFillsShortReservations` (existing allocation still runs).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**: lock PO via `lockById`, validate, call `inventory.receiveForPurchase` with `supplierName` from supplier, increment `received_qty`, set status, all in one transaction.
- [ ] **Step 4: Run** → PASS, plus `ReceiptsIT`.
- [ ] **Step 5: Commit** `feat(purchasing): receive purchase orders into stock`.

### Task 7: Reorder suggestions and draft from suggestions

**Files:**
- Create: `purchasing/{SuggestionQuery.java, SuggestionResponse.java, SuggestionController.java, DraftFromSuggestionsRequest.java}`
- Test: `purchasing/SuggestionMathTest.java` (pure), `purchasing/SuggestionsIT.java`

**Interfaces:**
- Produces: `SuggestionResponse(List<Group> groups)`, `Group(Long supplierId, String supplierName, Integer leadTimeDays, List<Item> items)`, `Item(long productId, String sku, String name, String unit, BigDecimal available, BigDecimal backorder, BigDecimal incoming, BigDecimal reorderLevel, BigDecimal reorderTarget, BigDecimal suggestedQty, BigDecimal perDay, BigDecimal coverDays, String onDraftPo)`.
- Produces: `static Optional<BigDecimal> SuggestionQuery.suggest(BigDecimal available, BigDecimal incoming, BigDecimal backorder, BigDecimal level, BigDecimal target)`; `SuggestionQuery.compute() : SuggestionResponse` (used by Task 8).
- Endpoints: `GET /api/purchasing/suggestions`; `POST /api/purchasing/suggestions/draft` with `DraftFromSuggestionsRequest(@NotNull Long supplierId, Long warehouseId, @NotEmpty List<@Valid PoRequest.Line> lines)` → `PoResponse` via `PurchaseOrderService.createDraft`.

- [ ] **Step 1: Failing unit test `SuggestionMathTest`**: position exactly at level → suggested `target − position`; one above level → empty; backorder 5 with available 10, level 8, target 20 → 15; incoming covers it → empty.
- [ ] **Step 2: Failing `SuggestionsIT`**: `groupsByPreferredSupplierWithNoSupplierGroup`, `productWithoutTargetNotSuggested`, `archivedProductNotSuggested`, `pendingApprovalPoCountsAsIncoming`, `draftPoFlaggedOnDraftPo` (value = PO number), `velocityFromLast30DaysDispatch` (60 units dispatched 10 days ago → perDay 2.000; a dispatch 31 days ago ignored), `zeroVelocityCoverDaysNull`, `draftFromSuggestionsCreatesDraftPo`.
- [ ] **Step 3: Run** → FAIL.
- [ ] **Step 4: Implement** `compute()` as one SQL over `product`, `stock_level`, `stock_reservation`, `stock_movement`, `purchase_order(_line)`, `supplier`, then apply `suggest` in Java. `perDay` scale 3, `coverDays` scale 1, both `HALF_UP`. Groups sorted by supplier name, "no supplier" last.
- [ ] **Step 5: Run** → PASS.
- [ ] **Step 6: Commit** `feat(purchasing): reorder suggestions`.

### Task 8: Daily reorder reminder

**Files:**
- Modify: `notifications/NotificationType.java` (add `REORDER_DUE`)
- Create: `purchasing/ReorderReminderSchedule.java`
- Test: `purchasing/ReorderReminderIT.java`

**Interfaces:**
- Consumes: `SuggestionQuery.compute()`, `Notifications.toRole(Role, NotificationType, String entityType, Long entityId, String title, String body)`.
- Produces: `ReorderReminderSchedule.run()` (public, called by the `@Scheduled` method and the test).

- [ ] **Step 1: Failing tests**: `notifiesAdminsWhenItemsDue` (title `"8 items to reorder across 3 suppliers"` shape: `n + " items to reorder across " + m + " suppliers"`, singular "item"/"supplier" when 1), `noNotificationWhenNothingDue`, `noRepeatWhenSameSet`, `notifiesAgainWhenSetChanges`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** following `OverdueReminderSchedule`; upsert `reorder_reminder_state` with sorted product ids after sending.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(purchasing): daily reorder reminder`.

### Task 9: API contract and module check

- [ ] **Step 1:** `./gradlew --no-daemon test --tests '*OpenApiSnapshotTest' -DupdateSnapshot=true` → snapshot file changes, test PASS.
- [ ] **Step 2:** `./gradlew --no-daemon test --tests '*ModularityTest'`; compare violations against `git stash` baseline. Expected: no violation mentioning `purchasing`.
- [ ] **Step 3:** Full `./gradlew --no-daemon test` → only baseline ModularityTest failures.
- [ ] **Step 4: Commit** `chore(api): snapshot for purchasing`.
- [ ] **Step 5:** In `crm-dashboard-ui`: `npm run gen:api` → `src/api/schema.d.ts` gains `SupplierResponse`, `PoResponse`, `SuggestionResponse`. Commit there `chore(api): regenerate for purchasing`.

### Task 10 (frontend): Suppliers pages

**Files (in `crm-dashboard-ui/src`):**
- Create: `features/purchasing/api.ts`, `SuppliersPage.tsx`, `SupplierPage.tsx`, `SupplierDrawer.tsx`, `PaymentDialog.tsx`, `CreditBadge.tsx`, tests `SuppliersPage.test.tsx`, `SupplierPage.test.tsx`
- Modify: `app/router.tsx`, `app/shell/nav.ts` (new "Purchasing" group, ADMIN only: Suppliers, Purchase orders, Reorder)

**Interfaces:**
- Produces in `api.ts`: types from `components["schemas"]`, `purchasingKeys`, fetch/mutate functions for every endpoint in Tasks 1–7 (pattern of `features/inventory/api.ts`).
- Routes: `/suppliers`, `/suppliers/:id`.

- [ ] **Step 1: Failing tests**: list renders limit/balance/committed/available and an "Over limit" badge for flag OVER, "Near limit" for NEAR, "No limit" when limit null; detail shows statement rows with running balance; "Record payment" posts and refreshes balance.
- [ ] **Step 2: Run** `npm test -- purchasing` → FAIL.
- [ ] **Step 3: Implement** following `ProductsPage`/`ProductDrawer` patterns. Peso formatting via the existing money util.
- [ ] **Step 4: Run** → PASS; `npm run typecheck` (or `tsc -b`) clean.
- [ ] **Step 5: Commit** `feat(purchasing): suppliers pages`.

### Task 11 (frontend): Purchase order pages

**Files:** Create `features/purchasing/PurchaseOrdersPage.tsx`, `PurchaseOrderPage.tsx`, `PoLinesEditor.tsx`, `ReceivePoDialog.tsx`, `CreditCheckPanel.tsx`, tests. Modify `app/router.tsx` (`/purchase-orders`, `/purchase-orders/:id`, `/purchase-orders/new`), `nav.ts` (WAREHOUSE also sees "Purchase orders").

- [ ] **Step 1: Failing tests**: draft shows CreditCheckPanel text `"This PO puts you ₱12,400.00 over the limit; it will need approval"` when `needsApproval`; Send/Cancel/Receive/Close short buttons shown only in the statuses the spec allows; receive dialog caps qty at remaining; rejected draft shows rejection reason.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**; handle 409 with existing `toastError`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(purchasing): purchase order pages`.

### Task 12 (frontend): Reorder suggestions, product fields, notification link

**Files:** Create `features/purchasing/ReorderPage.tsx` + test. Modify `features/catalog/ProductDrawer.tsx`, `productSchema.ts` (+test), `app/router.tsx` (`/reorder`), `app/shell/NotificationBell.tsx` (`REORDER_DUE` → `/reorder`).

- [ ] **Step 1: Failing tests**: groups render per supplier with "No supplier" last; lines with `onDraftPo` unticked and labelled `"on draft PO-0012"`; editing qty then "Create draft PO" posts ticked lines and navigates to the new PO; hint shows `"~2/day · covers 5 days · lead 7 days"`; product schema rejects reorder target below reorder level.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement.** "Create draft PO" disabled for the no-supplier group (no supplier to order from).
- [ ] **Step 4: Run** → PASS; full `npm test` and typecheck clean.
- [ ] **Step 5: Commit** `feat(purchasing): reorder suggestions page`.
