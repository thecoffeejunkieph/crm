# Redesign #4 — POS

Date: 2026-11-02 (requirements), 2026-11-03 (design)
Repos: `crm` (backend), `crm-dashboard-ui` (frontend)
Builds on: `2026-10-14-sales-lifecycle-design.md`, `2026-10-21-inventory-fulfilment-design.md`
Supersedes: `2026-10-11-pos-design.md` (old `Invoice`-based model; reference for UX only)

## Goal

A counter screen for walk-in, cash-and-carry sales. The cashier picks a customer and products, takes payment
(split tender allowed), and the goods leave at once. Every POS sale is a normal sales order that ends `FULFILLED`
with an `ISSUED` BIR invoice and verified payments, so invoices, payments, stock and reports look exactly as if the
sale had gone through the B2B path.

## Decisions

| Topic | Decision |
|---|---|
| Sale model | A `SalesOrder` with `channel = POS`, driven through the existing confirm, reserve, release, invoice, payment and dispatch steps in one transaction |
| Payment | Fully paid at checkout. Payments must equal the order total exactly. Split tender allowed |
| Price overrides | Unit price and line discount editable, capped at `approval.discountPercentMax` and never below cost. No approvals at the counter |
| Customer | Always a real customer: picked, or quick-added (name, phone, optional email) |
| Receipt | The invoice PDF, printed from the browser; optional email via `SalesMailer` |
| Stock | Sold from one warehouse, `pos.warehouseId`. Any shortage rejects the sale |
| Proof of payment | Optional at checkout; uploadable afterwards for any payment |
| Access | `ADMIN`, `SALES` |

## Out of scope

- Senior citizen/PWD discount (VAT-exempt totals, ID capture, invoice layout change). Own backlog item, for POS and
  B2B together.
- Pay-later / credit sales at the counter. Use a B2B order.
- Discounts over the cap or below cost at the counter. Use a B2B order, which goes through approvals.
- Refunds, returns and POS-specific cancel. Invoice void/reissue works as for any order.
- Separate thermal receipt layout, barcode scanning, cash drawer sessions, end-of-day reconciliation, offline mode,
  multiple registers.

## Backend (`crm`)

### Schema — `V9__pos.sql`

- `sales_order.channel text not null default 'B2B' check (channel in ('B2B','POS'))`. Existing rows become `B2B`.
  Mapped to `SalesChannel { B2B, POS }` (`@Enumerated(STRING)`) on `SalesOrder`.

### Settings

- New `SettingKey.POS_WAREHOUSE_ID("pos.warehouseId", …)`, editable on the admin `SettingsPage`: the id of an
  active warehouse. Unset means the POS is
  not configured.
- Existing `pos.quickAddAssignment` (`SELF` / `UNASSIGNED`) decides whether a quick-added customer is assigned to the
  cashier.

### Endpoints — `PosController`, `ADMIN` and `SALES`

**`GET /api/pos/products?q=&categoryId=&page=&size=`** → page of
`{id, sku, name, categoryId, unit, price, pictureUrl, available, minUnitPrice}` plus a top-level
`discountCapPct`.

- Active products only; `q` matches name or SKU, case-insensitive.
- `available` = on hand − reserved in `pos.warehouseId` (0 when there is no stock row).
- `minUnitPrice` = the lowest net unit price the cashier may charge: `max(cost, price × (1 − cap/100))`. It lets
  the UI enforce the rules without sending cost to `SALES`; cost itself is never in the response.
- 409 `POS_NOT_CONFIGURED` when `pos.warehouseId` is unset.

**`POST /api/pos/sales`** → `201 {orderId, orderNumber, invoiceId, invoiceNumber, total, changeDue}`

```json
{
  "customerId": 12,
  "lines": [{ "productId": 3, "qty": 2, "unitPrice": 550.00, "discountPct": 10 }],
  "payments": [
    { "method": "CASH", "amount": 500.00, "tendered": 1000.00 },
    { "method": "GCASH", "amount": 490.00, "reference": "1234567890" }
  ],
  "emailInvoice": true
}
```

- Added to the idempotency filter's create paths, so a retried request with the same `Idempotency-Key` returns the
  first response.
- `tendered` is accepted only on `CASH` and must be ≥ `amount`. `changeDue` = Σ(`tendered` − `amount`) over cash
  payments.
- No duplicate `productId` in `lines` (the client merges them).

**`PUT /api/payments/{id}/proof`** (multipart `file`) → `PaymentResponse`

- For any payment not `REJECTED`, not only POS ones. Mirrors `PUT /api/payments/{id}/form-2307`: magic-byte check
  through `StorageService` under the existing `payments/proof` prefix, replaces an existing proof (old object deleted
  after save), audited `PAYMENT_PROOF_ATTACHED`.
- Same access rules as recording a payment on that order.

### `PosService.checkout` — one `@Transactional`

1. **Configured:** read `pos.warehouseId`; 409 `POS_NOT_CONFIGURED` if unset or the warehouse is archived.
2. **Validate:** customer exists and is active; lines not empty; products active; qty > 0.
3. **Price rules:** for each line, `discountPct` ≤ `approval.discountPercentMax` and net unit price ≥
   `minUnitPrice` (same formula as the product list). A
   violation → 422 `POS_PRICE_NOT_ALLOWED` with `{productId, minUnitPrice}` per offending line.
4. **Totals and payments:** compute totals with `OrderTotals`. Σ payment `amount` must equal the order total →
   otherwise 422 `POS_PAYMENT_MISMATCH` with `{total, paid}`.
5. **Order:** create through `OrderService` with `channel = POS`, the POS warehouse as ship-from, then confirm. If
   `ApprovalChecks.forConfirm` returns anything (step 3 should make this impossible), roll back with 422
   `POS_APPROVAL_REQUIRED`.
6. **Stock:** `Inventory.reserve(...)`. If any reservation has `shortQty > 0`, roll back with 409
   `POS_OUT_OF_STOCK` and `{productId, requested, available}` per short line.
7. **Invoice, payments:** issue the invoice through the existing issue path (gapless `SI-…` number, frozen
   snapshot) while `CONFIRMED`, then record each payment through `PaymentService` as staff (status `VERIFIED`).
8. **Release and dispatch at the counter:** a new `OrderService.fulfilAtCounter` (balance is 0, so no
   `forRelease` approval) sets `RELEASED`, calls `Inventory.consume(orderId, invoiceNumber, invoiceId)` to post
   `DISPATCH` per line, and sets `FULFILLED`. It never creates a delivery order (the B2B `release()` always does).
   Audit `POS_SALE` with the invoice number and total.
9. **After commit:** if `emailInvoice` and the customer has an email, send the invoice PDF via `SalesMailer`. A
   mail failure is logged and does not affect the sale.

Any exception rolls back everything: order, reservations, invoice counter, payments and stock movements. No gap in
invoice numbers, no orphan order.

`OrderStateMachine` is not changed: the service walks the order through the normal transitions.

### Other backend changes

- Orders list: `channel` filter (`OrderFilter`), `channel` in `OrderSummary` / `OrderResponse`.
- Dashboards are the other half of tracker item P4-2 and are not designed here.

## Frontend (`crm-dashboard-ui`)

### Route and access

`/pos` in the shell sidebar, visible to `ADMIN` and `SALES`. When the product request returns
`POS_NOT_CONFIGURED`, the page shows an `EmptyState`; admins get a link to Settings.

### `src/features/pos/`

- **`PosPage.tsx`** — product grid left, cart right. At phone width (`useIsPhone`) the cart becomes a bottom
  `Drawer`.
  - Grid: search (`useDebouncedValue`), category `Chips`, tiles with picture, name, price and `available`. A tile
    with `available = 0` is disabled; adding more than `available` is blocked by `QuantityInput`'s max.
  - Cart: customer `Combobox` with a "+ New customer" option, lines with `QuantityInput` and net price, totals in
    the `TotalsRail` layout. **Charge** is enabled when a customer is set and there is at least one line.
- **`PosLineDialog.tsx`** — edit unit price and discount %. Fields refuse a discount over the cap or a net price
  below `minUnitPrice` (the cap comes with the product list response as `discountCapPct`).
- **`QuickAddCustomerDialog.tsx`** — name, phone, optional email. Posts to the existing customers API with type
  `INDIVIDUAL` and `DUE_ON_RECEIPT` terms, then selects the result.
- **`TenderDialog.tsx`** — tender rows (method, amount; tendered for cash; reference for others) with live
  Remaining and Change. Confirm is enabled only when Remaining is 0. "Email invoice" is ticked when the customer has
  an email. Sends a fresh `Idempotency-Key` per checkout attempt.
- **Success panel** — invoice number, change due, **Print invoice** (opens the existing invoice PDF in a new tab
  for printing), **Open order**, **New sale** (clears cart and customer).
- **`posCart.ts`** — pure functions for line net price, totals, remaining, change and the price floor. Preview
  only; the server is the authority.
- Error handling: `POS_OUT_OF_STOCK` names the short products and refreshes the grid; `POS_PRICE_NOT_ALLOWED`
  highlights the lines; `POS_PAYMENT_MISMATCH` keeps the tender dialog open.

### Elsewhere

- `PaymentsTab`: **Upload proof** action on payment rows with no proof, calling `PUT /api/payments/{id}/proof`.
- `OrdersPage`: Channel filter; POS orders carry a `POS` badge.
- Regenerate API types from the OpenAPI snapshot.

## Testing

Backend (Testcontainers):

- `PosServiceIT`: happy path with split tender (order `FULFILLED`, invoice `ISSUED`, payments `VERIFIED`, order
  balance 0, `DISPATCH` movements, change due); and one test per rejection — not configured, price below floor,
  payment mismatch, out of stock. Each rejection asserts no order, no movement and that the next invoice number is
  unchanged.
- Idempotent retry returns the same invoice number.
- `PaymentController` test for proof upload: replaces proof, rejects a non-image/PDF, 403 for a user without access.
- OpenAPI snapshot updated.

Frontend (Vitest + one e2e):

- `posCart.test.ts`: totals, change, price floor.
- `TenderDialog.test.tsx`: Remaining/Change, Confirm disabled until Remaining is 0.
- `PosPage.test.tsx`: Charge disabled without customer or lines; out-of-stock error names the products.
- `PaymentsTab.test.tsx`: Upload proof action.
- e2e smoke: quick-add a customer, sell two products with cash + GCash, see the invoice number and the stock drop.
