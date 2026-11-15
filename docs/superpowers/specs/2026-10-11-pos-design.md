# Retail POS — Design

Date: 2026-10-11
Repos: `crm` (backend), `crm-dashboard-ui` (frontend)

## Goal

A point-of-sale page for the retail side: walk-in, cash-and-carry sales with no quotation step. The cashier picks
products from the catalog, picks or quick-adds a customer, takes payment (split across methods if needed), and the
goods leave immediately. Stock is deducted at once from the default warehouse. Sales show up in the existing
dashboard and sales reports, and the customer gets a printed and/or emailed receipt.

## Decisions

| Topic | Decision |
|---|---|
| Sale model | A POS sale is an `Invoice` with `channel = POS`, created already `PAID` |
| Customer | Required. Picked from Customers or quick-added from the POS |
| Payment | Always paid in full at checkout. Split tender supported (several `InvoicePayment` rows) |
| Pricing | List price by default. Cashier may override a line's unit price, discount a line, and discount the whole sale (`PERCENT` / `FIXED`) |
| Product lookup | Name search, category filter, picture grid. No barcode/SKU |
| Stock | Deducted immediately (new `SALE` movement). No reservation, no delivery order |
| Receipt | Browser-printable receipt and emailed receipt (invoice PDF in receipt mode) |
| Access | `ADMIN`, `SALES` |
| Label | "Sales Receipt" — not "Official Receipt" (BIR compliance is out of scope) |

## Out of scope

Refunds/voids/returns, barcode scanning, BIR-compliant receipts, multiple stores/registers, cash drawer
sessions and end-of-day reconciliation, offline mode. Refunds would later add a `RETURN` stock movement.

## Backend (`crm`)

### Schema — `V8__pos_sales.sql`

- `invoice.channel enum('B2B','POS') NOT NULL DEFAULT 'B2B'`, mapped to new enum `SalesChannel { B2B, POS }`
  (`@Enumerated(STRING)`). Existing rows become `B2B`.
- `invoice_payment.amount_tendered DECIMAL(19,2) NULL` — set only on `CASH` payments.
- `invoice_item.list_price DECIMAL(19,2) NULL` — the product's list price at sale time, so the receipt can show
  "was ₱600" on overridden lines. Null on B2B items.
- `stock_movement.type` is a MySQL enum, so the migration redefines it as
  `enum('DELIVERY','RECEIPT','RELEASE','RESERVE','SALE') NOT NULL` and adds `StockMovementType.SALE`.

### Endpoints — new `PosController` at `/api/v1/pos`, `@PreAuthorize("hasAnyAuthority('ADMIN','SALES')")`

**`GET /api/v1/pos/products?q=&categoryId=&pageNumber=1&pageSize=24`** → `PageResponse<PosProductResponse>`

Active products only, filtered by name (contains, case-insensitive) and category. Each item:
`id, productName, categoryId, category, unit, price, pictureUrl, quantityAvailable` where
`quantityAvailable` is from the default warehouse's `InventoryItem` (0 when no record). The existing
`/api/v1/products` endpoint is not changed.

**`POST /api/v1/pos/sales`** → `PosSaleResponse` (covered by the existing `IdempotencyFilter`)

```json
{
  "customerId": 12,
  "items": [
    { "productId": 3, "quantity": 2, "unitPrice": 550.00, "discount": 10, "discountType": "PERCENT" }
  ],
  "discount": 50, "discountType": "FIXED",
  "payments": [
    { "method": "CASH", "amount": 500.00, "amountTendered": 1000.00 },
    { "method": "GCASH", "amount": 440.00 }
  ],
  "notes": "optional",
  "emailReceipt": true
}
```

`unitPrice`, line `discount`/`discountType` and sale `discount`/`discountType` are optional. Clients never send
totals.

Response: the `InvoiceResponse` fields the receipt needs (id, invoiceNumber, invoiceDate, paidAt, customer,
salesRep, items with original price, unit price, discount and total, subtotal, sale discount, totalAmount,
payments with method, amount, amountTendered and change) plus `change` (total cash change) and
`emailStatus` (`NOT_REQUESTED | SENT | FAILED`).

**`POST /api/v1/pos/sales/{id}/email`** → `InvoiceResponse`. Resends the receipt and calls `InvoiceEmailService.send`.
Returns 400 if the invoice is not `POS` or the customer has no email.

Customer quick-add uses the existing `POST /api/v1/customers`.

### `PosSaleService.checkout` — one `@Transactional`

1. **Validate request:** customer exists; at least one item; quantities > 0; no duplicate products (the client
   merges them); at least one payment.
2. **Price lines:** load each product and reject inactive ones (`InvalidRequestException`). Unit price is
   `unitPrice` if given (must be ≥ 0), else `product.price`. Line gross = unit price × qty. Line discount:
   `PERCENT` 0–100 of gross, `FIXED` ≤ gross. Line total = gross − discount, rounded HALF_UP to 2 decimals.
3. **Sale total:** subtotal = Σ line totals. Sale discount applies to the subtotal under the same rules.
   Total = subtotal − sale discount, which must be ≥ 0.
4. **Validate payments:** each `amount` > 0 and Σ `amount` must equal the total exactly. Otherwise reject with
   "Payments must equal the total". `amountTendered` is allowed only on `CASH` and must be ≥ `amount`.
   Change per cash line = tendered − amount.
5. **Persist:** `Invoice` with `channel=POS`, `status=PAID`, `paidAt=now`, `invoiceDate=dueDate=today`,
   `paymentTerms=DUE_ON_RECEIPT`, `salesRep` = current user, `totalAmount`, sale discount fields, notes. Also
   save its `InvoiceItem`s (price = effective unit price) and `InvoicePayment`s (`recordedAt=now`).
6. **Deduct stock:** `InventoryService.sellForPos(invoice)`. For each line in the default warehouse, check
   `quantityAvailable ≥ qty` (else `InsufficientStockException` naming the product), then
   `quantityOnHand −= qty`, saved through `saveWithLockHandling`, with a `SALE` movement where
   `referenceType="POS"` and `referenceId=invoice.id`. Any failure rolls back the whole sale.
7. **Email:** runs *after* the transaction commits. `PosController` calls `checkout` (transactional) and then,
   if `emailReceipt` is set and the customer has an email, calls `InvoiceEmailService.send` synchronously before
   responding. Failures are caught, logged and reported as `emailStatus=FAILED`. They never roll back the sale.
   With no email requested, or no customer email, `emailStatus=NOT_REQUESTED`.

### Guarding B2B flows on POS invoices

POS invoices are already `PAID`, so `markPaid`, `cancel`, `recordPayment` and `uploadProofOfPayment` must reject
them explicitly with `InvalidRequestException("Not allowed on POS sales")`. The public payment portal must return
404 for them. No delivery order is ever created for a POS invoice.

Dashboard and report queries are unchanged, since they already count `PAID` invoices.

### Backend tests

- `PosSaleServiceTest` (unit, mocked repos): line override, line `PERCENT`/`FIXED`, sale discount, HALF_UP
  rounding; discount bigger than gross rejected; inactive product rejected; payments short or over rejected; split
  tender accepted; cash change computed; `amountTendered` on non-cash rejected.
- `InventoryService` SALE: on-hand decremented, reserved untouched, movement recorded; insufficient stock throws.
- Guard tests: `markPaid`, `cancel`, `recordPayment` on a POS invoice throw.
- `InvoiceEmailService`: POS invoice produces receipt subject and body; B2B unchanged.

## Frontend (`crm-dashboard-ui`)

### Routing and access

- `/pos` route inside `AppLayout`, behind a new `RequirePosAccess` guard (`hasAnyRole(user, ["ADMIN","SALES"])`)
  in `routes/RouteGuards.tsx`.
- "POS" entry in `AppSidebar`, visible to the same roles.

### Components — `src/components/pos/`

- `posApi.ts` — react-query: `usePosProducts(q, categoryId, page)`, `useCheckout()`, `useEmailReceipt()`.
- `ProductGrid.tsx` — 300 ms debounced search, category select, cards showing picture (`utils/productPicture`),
  name, price and "N left". Sold-out cards are disabled. Tapping adds 1, capped at `quantityAvailable`. Paged.
- `Cart.tsx` — lines with qty stepper, line total, edit (✎) and remove. Footer shows subtotal, sale discount
  (`%`/`₱` toggle), total and a Charge button that stays disabled until there is a customer and at least one line.
- `LineEditor.tsx` — modal to override unit price and set line discount.
- `CustomerPicker.tsx` — search-as-you-type over `GET /customers?name=`. **+** opens `QuickAddCustomerModal`
  (first and last name, email, phone, type defaulting to `INDIVIDUAL`), which posts to `/customers` and selects the result.
- `PaymentModal.tsx` — tender rows (method + amount, plus "tendered" for cash), live "Remaining" and "Change".
  The "Email receipt" checkbox is ticked by default when the customer has an email. Confirm is enabled only when
  the payments equal the total.
- `PosReceipt.tsx` — printable receipt (see below).
- `posTotals.ts` — pure functions mirroring the server pricing rules, used only for the on-screen preview.

`pages/Pos.tsx` holds the cart in `useReducer` (no global store). It uses a two-pane layout on desktop. Below
`lg`, the cart becomes a bottom sheet with a sticky "Cart (n) · ₱total" bar.

### Checkout flow

1. Opening `PaymentModal` creates an `Idempotency-Key` (`crypto.randomUUID()`) that is reused for every retry of that
   checkout and discarded once a sale succeeds or the modal is closed.
2. On success, show a success view (invoice number, total, change) with **Print receipt**, **Email receipt**
   (resend) and **New sale** (clears the cart and customer). If `emailStatus=FAILED`, show a toast with Resend.
3. On an insufficient-stock error: toast the message, refetch the product grid, and flag the line in the cart.
   Other errors go through `utils/errors.ts` and toasts.
4. Receipts and success views render from the **server response**, never from cart state.

### Frontend checks

`posTotals.ts` gets a small self-check covering the same cases as the backend pricing tests. Manual run-through:
add items, override, discount, split tender, print preview, email.

## Receipt

### Printed (`PosReceipt.tsx`)

Rendered into a container that is hidden on screen, printed with `window.print()`. A `@media print` style hides everything else.
It is a single column, `max-width: 80mm`, which works for thermal printers and A4.

Contents: logo, store name, "Sales Receipt", invoice number, date and time, cashier, customer. Then lines
(qty × unit price = total, with "was ₱X" when overridden and the discount when applied), subtotal, sale discount,
**total**. Then payments (method and amount; for cash also tendered and change), notes, and "Thank you".

### Emailed

- `InvoiceEmailService` branches on `channel == POS`. Subject is "Your receipt – {invoiceNumber}". The body
  shows "Paid", the items and the payments list. It leaves out the due date, payment terms, bank details and the
  upload-proof button. B2B email is unchanged.
- `InvoicePdfService` passes a `receipt` flag to the existing template. When it's set, the template titles the
  document "Sales Receipt", hides due date and terms, shows "PAID" and adds a payments table. It stays one
  template with conditionals.
