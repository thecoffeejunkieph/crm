# Purchasing: suppliers, credit limits, purchase orders, reorder suggestions

Date: 2026-11-24
Status: approved design, pending spec review

## Goal

Track suppliers and the credit line each one extends to us, raise purchase orders (POs) against them, and get reminded what to reorder based on inventory. Success: open "Reorder suggestions", see items grouped by supplier, turn a group into a draft PO in one click, and know before sending whether it breaks that supplier's credit limit.

## Decisions

| Topic | Decision |
|---|---|
| Credit used | Received but unpaid: value of goods received on POs minus payments recorded, as a running balance per supplier (payments are not matched to deliveries). |
| Suggestion rule | Reorder level triggers, "reorder up to" target sets quantity; sales velocity shown as a hint only. |
| Product to supplier | One preferred supplier per product. Any product may still be added to any PO by hand. |
| Over credit limit | PO needs approval through the existing approvals module; within limit it is sent directly. |
| Structure | New Spring Modulith module `purchasing`. It depends on `inventory`, `catalog`, `warehouses`, `approvals`, `notifications`; nothing depends on it. |
| Suggestions storage | Computed on read; no stored suggestions table. |

## Out of scope

PO PDF/email to supplier, separate supplier bill/invoice records, payment-to-delivery matching, per-supplier price lists, multi-currency, payment terms enforcement (stored for reference only).

## Data model (`V30__purchasing.sql`)

Conventions as V1: identity bigint ids, `numeric(12,3)` quantities, `numeric(14,2)` money, `created_at`/`updated_at`/`version` on mutable tables.

- `supplier`: `code` (unique), `name`, `contact_name`, `phone`, `email`, `address`, `payment_terms_days`, `lead_time_days`, `credit_limit` (null = no limit, `>= 0`), `opening_balance` (default 0, debt owed at go-live), `notes`, `archived_at`.
- `product` adds `preferred_supplier_id` (FK supplier, nullable) and `reorder_target numeric(12,3)` (`>= 0`, null = not set). Validation: when both are set, `reorder_target >= reorder_level`.
- `purchase_order`: `number` (unique, `PO-` sequence like other documents), `supplier_id`, `warehouse_id` (deliver to, defaults to default warehouse), `status` in `DRAFT, PENDING_APPROVAL, SENT, PARTIALLY_RECEIVED, RECEIVED, CANCELLED`, `expected_on`, `notes`, `rejection_reason`, `approval_request_id`, `sent_at`, `closed_short` (boolean), `created_by`.
- `purchase_order_line`: `po_id` (cascade), `product_id`, `qty > 0`, `unit_cost >= 0` (defaults to `product.cost`), `received_qty >= 0` default 0, `CHECK (received_qty <= qty)`.
- `stock_receipt` adds `purchase_order_id` (FK, nullable).
- `supplier_payment`: `supplier_id`, `amount > 0`, `paid_on`, `method`, `reference`, `notes`, `created_by`, `created_at`.
- `approval_request.type` check constraint (if present) gains `PO_OVER_CREDIT`; `ApprovalType` enum gains the same.

### Money (computed, never stored)

- **Balance** = `opening_balance` + Σ(`received_qty` × `unit_cost`) over the supplier's non-cancelled POs − Σ payments.
- **Committed** = Σ((`qty` − `received_qty`) × `unit_cost`) over POs in `PENDING_APPROVAL`, `SENT`, `PARTIALLY_RECEIVED` that are not `closed_short`.
- **Available** = `credit_limit` − balance − committed (null when no limit).
- **Projected exposure** for a PO being sent = balance + committed + this PO's total.

Stock receipts not linked to a PO do not affect the balance.

## PO lifecycle

```
DRAFT --send (within limit)--> SENT --receive--> PARTIALLY_RECEIVED --receive--> RECEIVED
  |  \--send (over limit)--> PENDING_APPROVAL --approved--> SENT        \--close short--> RECEIVED
  |                                  \--rejected--> DRAFT (rejection_reason set)
  \-- cancel allowed from DRAFT, PENDING_APPROVAL, SENT while nothing received
```

- **Edit**: header and lines editable only in `DRAFT`.
- **Send** `POST /api/purchase-orders/{id}/send`: requires at least one line. If the supplier has no limit or projected exposure ≤ limit, set `SENT` and `sent_at`. Otherwise create a `PO_OVER_CREDIT` approval (payload: supplier, PO number, PO total, amount over limit) and set `PENDING_APPROVAL`.
- **Approval listener** (pattern of `AdjustmentApprovalListener`): approved → `SENT`; rejected → `DRAFT` with `rejection_reason`.
- **Receive** `POST /api/purchase-orders/{id}/receipts` `{receivedOn, reference, lines:[{lineId, qty}]}`: allowed in `SENT`/`PARTIALLY_RECEIVED`. Each qty > 0 and ≤ remaining; over-receipt is rejected (extras go on a manual receipt). Calls the inventory receipt API with `purchase_order_id` and supplier name, increments `received_qty`, sets status. All in one transaction.
- **Close short** `POST /api/purchase-orders/{id}/close`: from `PARTIALLY_RECEIVED`; sets `RECEIVED`, `closed_short = true`, releases committed credit.
- **Cancel** `POST /api/purchase-orders/{id}/cancel`: from `DRAFT`, `PENDING_APPROVAL` (cancels the pending approval request), `SENT`, only when no line has `received_qty > 0`.
- Optimistic locking via `version`; status transitions validated in the entity.

## Suppliers and payments

- CRUD `/api/suppliers` (archive, not delete). List rows include limit, balance, committed, available and a flag: `OVER` (available < 0) or `NEAR` (used ≥ 90% of limit).
- `GET /api/suppliers/{id}/statement?from&to`: dated entries for PO receipts (value) and payments, with opening and running balance.
- `POST /api/suppliers/{id}/payments`, `GET /api/suppliers/{id}/payments`, `DELETE /api/suppliers/{id}/payments/{paymentId}` (ADMIN only, audited).

## Reorder suggestions

Per non-archived product with both `reorder_level` and `reorder_target` set, across all warehouses:

- available = Σ(`on_hand` − `reserved`)
- backorder = Σ `stock_reservation.short_qty`
- incoming = Σ remaining qty on POs in `PENDING_APPROVAL`, `SENT`, `PARTIALLY_RECEIVED` (not closed short)
- position = available + incoming − backorder
- suggested when position ≤ `reorder_level`; quantity = `reorder_target − position`
- hint: `perDay` = DISPATCH movement qty over last 30 days ÷ 30; `coverDays` = available ÷ `perDay` (null when `perDay` = 0); supplier `lead_time_days` shown alongside
- `onDraftPo`: number of a draft PO already containing the product, if any; such lines are unticked by default in the UI

Endpoints:
- `GET /api/purchasing/suggestions` → groups by preferred supplier, plus a "no supplier" group.
- `POST /api/purchasing/suggestions/draft` `{supplierId, warehouseId?, lines:[{productId, qty}]}` → creates a `DRAFT` PO, returns it.

### Daily reminder

Scheduled job at 07:00 (app time zone). Computes suggestions; if non-empty and the set of suggested product ids differs from the last reminder's, notify ADMIN users: "N items to reorder across M suppliers", linking to the suggestions page. The last-reminded set is kept in a one-row table `reorder_reminder_state`. No Today-queue tile; the notification covers it.

## Permissions

The codebase gates by role (ADMIN, SALES, WAREHOUSE), not permission strings, so: ADMIN does everything (suppliers, POs, suggestions, payments). WAREHOUSE may read POs and receive POs for warehouses it can act on (`InventoryPermissions.canAct`). SALES has no purchasing access. Approving uses existing approval rights (ADMIN). Security matrix tests get rows for every new endpoint.

Because only admins send POs, the over-limit approval is an admin confirming their own over-limit PO: a deliberate second step, recorded in the approvals log.

## Frontend (`crm-dashboard-ui`)

New "Purchasing" nav section:
- **Suppliers**: list with credit columns and NEAR/OVER badges; detail with statement, payments, "Record payment".
- **Purchase orders**: list filtered by status; detail with lines and receipt history, actions Send / Receive / Close short / Cancel; a credit-check panel on drafts shows projected exposure and whether sending will need approval.
- **Reorder suggestions**: tables grouped by supplier, tick boxes, editable qty, velocity hint, "Create draft PO" per group.
- **Product form**: Preferred supplier and Reorder up to fields.

API client regenerated from the OpenAPI snapshot (`npm run gen:api`).

## Errors

- Invalid transition, over-receipt, cancel after receipt, send with no lines → 409/400 with a message naming the PO and line.
- Archived supplier cannot receive new POs; archived products cannot be added to lines.
- Concurrent edits → existing optimistic-lock 409 handling.

## Testing

- Unit: suggestion formula (trigger edge at exactly `reorder_level`, backorder, incoming, zero velocity) and money math (opening balance, partial receipt, closed short, cancelled PO).
- Integration (Testcontainers): send within limit → SENT; over limit → approval → SENT; rejection → DRAFT; partial receive creates a linked stock receipt and raises `on_hand`; payment lowers balance; draft from suggestions.
- Security matrix rows; `OpenApiSnapshotTest` snapshot updated; `ModularityTest` shows no new cycles versus baseline.
