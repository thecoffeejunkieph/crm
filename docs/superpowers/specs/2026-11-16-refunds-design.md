# Refunds — design (2026-11-16)

Spans both repos: `crm/crm` (backend) and `crm-dashboard-ui` (frontend).

## Goal

Staff can refund money on an order that has collected payments — either money only (overcharge, goodwill,
price adjustment) or for goods the customer sends back — partially or in full. Every refund is approved,
paid out with a recorded method and proof, documented with a numbered credit memo, and reflected in stock,
payment status, net sales, commission and reports.

## Decisions

| Question | Decision |
|---|---|
| Kinds | Both: `MONEY_ONLY` and `GOODS_RETURN` |
| Partial | Yes: any amount up to collected; any quantity up to delivered-less-returned |
| Who | Existing approval flow: `Approvals.require(ApprovalType.REFUND)` — admins pass (audited), others need an APPROVED request |
| Payout | Recorded with an existing `PaymentMethod`, reference, optional proof file. No store credit |
| Returned goods | Per line: `RESTOCK` (sellable again) or `DAMAGED` (returned then written off) |
| Paperwork | Numbered credit memo PDF referencing the original invoice |
| Model | New `Refund` aggregate, not negative `Payment` rows (payments carry verification, withholding and 2307 semantics that do not apply) |

## 1. Data model

New Flyway migration (next `V23__refunds.sql`).

`refund`
- `id`, `order_id` (FK `sales_order`), `kind` (`MONEY_ONLY` | `GOODS_RETURN`), `amount numeric(14,2) > 0`, `reason text not null`
- `status` (`REQUESTED` | `APPROVED` | `PAID` | `REJECTED` | `CANCELLED`)
- `approval_request_id` (nullable; null when an admin bypassed)
- payout: `method`, `reference`, `proof_key`, `paid_on date`, `paid_by`, `paid_at`
- `credit_memo_no text unique` (null until PAID)
- `requested_by`, `created_at`, `updated_at`, `version`

`refund_line` (only for `GOODS_RETURN`; at least one)
- `id`, `refund_id`, `order_line_id`, `qty > 0`, `disposition` (`RESTOCK` | `DAMAGED`), `amount numeric(14,2)`

`refund.amount` for goods returns = sum of line amounts. A line amount = `qty × the line's effective unit price`
(VAT-inclusive, after line and whole-order discount, as `OrderTotals` already computes), rounded HALF_UP to
centavos. For money-only the user types the amount.

## 2. Lifecycle

```
REQUESTED ──approve──▶ APPROVED ──record payout──▶ PAID
    │  └──reject──▶ REJECTED
    └──cancel (requester/admin)──▶ CANCELLED      APPROVED ──cancel──▶ CANCELLED
```

1. **Request** — `POST /api/orders/{id}/refunds`. Allowed for anyone with access to the order
   (`OrderAccess`) when the order has ≥ 1 VERIFIED payment.
2. **Approval** — inside the request transaction, `Approvals.require(REFUND, …)`. Admin → status goes
   straight to `APPROVED` (audited). Non-admin → `REQUESTED` plus an approval request; the existing
   `APPROVAL_REQUESTED` / `APPROVAL_DECIDED` notifications fire. An `ApprovalDecided` listener moves the refund
   to `APPROVED` or `REJECTED`.
3. **Payout** — `POST /api/refunds/{id}/payout` (method, reference, paidOn, optional proof upload via the
   existing attachments/storage). Allowed to whoever may record payments on the order today (same `SalesPermissions` check).
4. **On PAID**, one transaction:
   - Goods lines post a `RETURN` movement (new `MovementType`) into the warehouse whose delivery order shipped
     that line (when a line shipped from several, the dialog asks which warehouse; default the latest delivery's). `DAMAGED` quantities additionally post an `ADJUSTMENT` out with reason "Returned damaged", so the
     ledger shows both the return and the write-off.
   - Assign `credit_memo_no` from the `CM-` sequence (same mechanism as invoice numbers).
   - Publish `RefundPaid`.
5. **Cancel** — `POST /api/refunds/{id}/cancel` while `REQUESTED` or `APPROVED`.

### Guards (checked on request and again on payout, under the order's row lock)
- Sum of non-rejected, non-cancelled refunds ≤ verified collected amount.
- Per order line: returned qty (non-rejected, non-cancelled) ≤ delivered qty.
- `GOODS_RETURN` only when the order is `RELEASED` or `FULFILLED`.
- `CANCELLED` orders that collected money can take `MONEY_ONLY` refunds.

## 3. Money and payment status

- `paidRefunds` = sum of `PAID` refunds on the order.
- `effective total = order total − paidRefunds`; `settled = verified payments − paidRefunds`.
  `Balances.status` is fed these, so the balance and payment status do not change on refund (a PAID order stays PAID).
- A separate derived **refund badge** on order responses: `NONE` | `PARTIAL` (0 < paidRefunds < collected) |
  `FULL` (paidRefunds = collected).

## 4. Insights, reports, dashboard

- `InsightsQueries.netSales` subtracts each PAID refund's net-of-VAT amount in the month of `paid_on`.
  Commission and target progress follow. Already-sent `TARGET_MILESTONE` notifications are not retracted.
- Sales reports: add a **Refunds** column; totals are net of refunds.
- Dashboard Top Products subtracts returned qty/amount; Top Customers subtracts refunded amounts.

## 5. Notifications

- New `NotificationType.REFUND_PAID` → order's rep and `toRole(ADMIN)` (actor self-skip as today):
  "Refund ₱{amount} paid on {orderNo} ({cmNo})", `entityType ORDER`.
- `RESTOCK` returns re-evaluate low/out-of-stock the same way receipts do (no new alert type).

## 6. Credit memo

- `GET /api/refunds/{id}/credit-memo.pdf` (staff) and the portal equivalent for the order's customer.
- Template reuses the invoice PDF setup (Tailwind PDF classes, seller snapshot from the original invoice).
  Shows: CM number and date, customer, original invoice number and date, refunded lines (goods) or one line
  "Adjustment — {reason}" (money only), VAT-able / VAT-exempt / zero-rated / VAT split, total.
- Issued once at PAID, never edited. No credit-memo void in v1: a mistake is corrected by a further refund
  or a new payment. After changing template classes run `npm run build` in `tailwind/`.

## 7. Frontend (`crm-dashboard-ui`)

- Order page: **Refund** button when the order has collected money. Dialog:
  - Money only: amount (max = refundable) + reason.
  - Goods return: per delivered line, qty (max = returnable) + Restock/Damaged toggle; amount computed live; reason.
- **Refunds** panel under Payments: status, amount, CM link, **Record payout** (APPROVED), **Cancel**.
- Orders list: Refunded / Partially refunded badge.
- Portal order page: refund rows and credit memo download.
- Regenerate the client with `npm run gen:api` after the backend snapshot update.

## 8. Testing

- `RefundsIT`: over-refund and over-return rejected; non-admin goes through approval, admin bypasses;
  reject/cancel paths; PAID posts RETURN (and DAMAGED write-off); payment status stays PAID; badge values;
  CM number assigned once and unique; net sales drop in the payout month; `REFUND_PAID` recipients.
- Extend `LedgerConsistencyIT` (returns keep the ledger consistent) and `ReportsIT` (refunds column, net totals).
- Security matrix: new endpoints.
- `OpenApiSnapshotTest -DupdateSnapshot=true`, then frontend `gen:api` and typecheck.

## Out of scope

Store credit / customer balance; credit memo void; refunding shipping separately from lines; refunds on
orders with no verified payment; customer-initiated refund requests from the portal.
