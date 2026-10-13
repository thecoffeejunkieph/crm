# Redesign #2 — Sales lifecycle

Date: 2026-10-14
Repos: `crm` (backend), `crm-dashboard-ui` (frontend)
Builds on: `2026-10-12-foundation-design.md`

## Context

Foundation shipped identity, scoping, approvals, audit, the UI shell, customers and warehouses. This sub-project adds
what the business actually sells through: products, orders (with an optional quote stage), BIR-compliant invoices,
payments with withholding tax, and the customer portal screens.

The old app splits this across `Quotation`, `Invoice` and `InvoicePayment` with separate screens — the "clunky sales
flow" the owner asked to fix. The redesign uses **one Order aggregate** and **one Order workspace**.

### Success

- A rep takes a customer from draft → quote → confirmed → released → invoiced → fully paid on one page.
- A rep can revise an accepted order; approvals and the invoice follow the revision automatically.
- A customer contact sees their orders and invoices in the portal, accepts quotes, and uploads payment proof.
- Invoices satisfy BIR: gapless serial numbers, VAT breakdown, never deleted.

## Decisions

| Topic | Decision |
|---|---|
| Aggregate | One `sales_order` from draft to fulfilled. The quote is a stage of the order, not a separate record |
| Catalog | Minimal `product` catalog lives in #2 (module `catalog`). #3 adds stock on top |
| Product cost | Visible to ADMIN only. Server-side below-cost check still applies to SALES |
| Invoices | At most one active invoice per order. All payments settle that one invoice |
| Tax | VAT-registered. Prices are entered VAT-inclusive. Product tax class + customer tax treatment override |
| Invoice numbering | Gapless, from a locked counter row in the issuing transaction |
| Change after invoicing | Void the invoice (keeps its number, marked VOID) and reissue with the next number |
| Withholding | Payments record cash + withholding; BIR Form 2307 uploaded any time; missing-2307 report |
| Credit control | Checked at confirm: exposure vs. limit, plus any overdue invoice → `CREDIT_LIMIT` approval |
| Fulfilment gate | Explicit Release step. Cash customers need full payment or a `CREDIT_LIMIT` approval |
| Portal | View, download PDFs, accept/decline quotes, upload payment proof (pending until a rep verifies) |
| Payments link to | The order, not the invoice — void/reissue needs no carry-over |

## Out of scope for #2

Stock reservation, delivery orders and warehouse views (#3). POS and the senior citizen/PWD discount (#4). Data
migration (#5). Refunds, credit/debit memos, deposit or progress invoices. Customer-specific price lists,
multi-currency, recurring orders. Overdue reminder emails. Quote requests from the portal.

---

## 1. Modules

| Module | Contents |
|---|---|
| `catalog` | `product`, the `Catalog` facade, product API |
| `sales` | Orders, revisions, invoices, payments, document counter, quote/invoice PDFs, missing-2307 report, sales API |
| `portal` | Gains the portal order/invoice/payment endpoints, delegating to the `Sales` facade |
| `customers` | Gains `tax_treatment` |

`sales` depends on `catalog`, `customers`, `approvals`, `audit`, `settings`, `files` and `notifications` through
their facades only. Modulith `verify()` covers the new modules.

## 2. Data model

Migration `V2__catalog_sales.sql`. Conventions from Foundation §2 apply: money `numeric(19,2)` VAT-inclusive,
quantity `numeric(12,3)`, `text` + `CHECK` enums, `timestamptz`, `version` on aggregate roots.

### catalog

- `product(id, sku, name, description, unit, price, cost null, tax_class, archived_at, created_at, updated_at,
  version)`
  - `unique (lower(sku)) where archived_at is null`.
  - `unit` in `PC, BOX, PACK, KG, G, L, ML, M`. New units come by migration.
  - `tax_class` in `VATABLE, VAT_EXEMPT, ZERO_RATED`.
  - `price >= 0`, `cost >= 0` when present.

### customers

- `customer.tax_treatment text not null default 'REGULAR'` in `REGULAR, ZERO_RATED, VAT_EXEMPT`. Anything but
  `REGULAR` overrides every product tax class on that customer's orders.

### sales

- `sales_order(id, number, customer_id, rep_id, status, revision int default 0, payment_terms, quote_valid_until null,
  shipping_fee default 0, notes, terms, vatable_sales, vat, vat_exempt_sales, zero_rated_sales, total,
  quoted_at, customer_accepted_at, confirmed_at, released_at, fulfilled_at, cancelled_at, cancel_reason,
  created_by, created_at, updated_at, version)`
  - `number` is `SO-<YY>-<5 digits>` from the Foundation sequence (gaps are acceptable for orders).
  - `status` in `DRAFT, QUOTED, CONFIRMED, RELEASED, FULFILLED, CANCELLED`.
  - `rep_id` is copied from the customer at creation. `payment_terms` is copied from the customer and editable.
  - Totals are cached, recomputed by `OrderTotals` on every line change.
- `order_line(id, order_id, position, product_id, description, unit, qty, unit_price, discount_pct, tax_class,
  line_total)`
  - `description`, `unit`, `tax_class` are snapshots taken when the line is added; catalog edits don't rewrite
    orders.
  - `qty > 0`, `unit_price >= 0`, `discount_pct between 0 and 100`.
- `order_revision(id, order_id, revision, snapshot jsonb, reason, created_by, created_at)` — the order's state
  *before* a revision of a confirmed or released order.
- `invoice(id, number, order_id, order_revision, status, issued_at, due_date, customer_snapshot jsonb,
  lines_snapshot jsonb, vatable_sales, vat, vat_exempt_sales, zero_rated_sales, shipping_fee, total,
  void_reason null, voided_by null, voided_at null, replaced_by_id null, issued_by)`
  - `status` in `ISSUED, VOID`.
  - `unique (order_id) where status = 'ISSUED'` — at most one active invoice per order.
  - Fully frozen: never reads the live order or customer after issue.
  - `number` is `SI-<5 digits>` from `document_counter`.
- `document_counter(name text primary key, next_value bigint not null)` — seeded with `('INVOICE', 1)`.
- `payment(id, order_id, status, method, cash_amount, withholding_amount default 0, received_on, reference,
  proof_file_id null, form2307_file_id null, submitted_by_type, submitted_by_id, verified_by null,
  verified_at null, reject_reason null, created_at, updated_at, version)`
  - `status` in `PENDING, VERIFIED, REJECTED`.
  - `method` in `CASH, BANK_TRANSFER, CHECK, GCASH, CARD, OTHER`.
  - `submitted_by_type` in `USER, CONTACT`.
  - `cash_amount >= 0`, `withholding_amount >= 0`, `cash_amount + withholding_amount > 0`.

### approvals

- `approval_request.type` gains `INVOICE_VOID`.

### settings

| Key | Default |
|---|---|
| `sales.quoteValidityDays` | 15 |
| `sales.withholdingSuggestPercent` | 1 |
| `company.name`, `company.tin`, `company.address` | empty — required before an invoice can be issued |
| `invoice.footerText` | empty — BIR permit (PTU/ATP) text printed on invoices |

## 3. Order lifecycle

```
DRAFT ──send quote──▶ QUOTED ──confirm / portal accept──▶ CONFIRMED ──release──▶ RELEASED ──fulfil──▶ FULFILLED
  └──────────────────── confirm (skip quote) ──────────────┘
any state before FULFILLED ──cancel──▶ CANCELLED
```

Transitions live in one table-driven `OrderStateMachine`. A disallowed transition → 422 `INVALID_STATE`.

**Payment status** is derived, never stored: `UNPAID` (nothing verified), `PARTIAL`, `PAID` (balance 0),
`OVERDUE` (active invoice past `due_date` with balance > 0).

**Balance** = active invoice `total` (or the order `total` if no invoice is issued) − Σ verified
(`cash_amount + withholding_amount`).

### Draft and quote

- Lines and header are freely editable in `DRAFT` and `QUOTED`. Editing a `QUOTED` order returns it to `DRAFT`.
- **Send quote** (`DRAFT → QUOTED`): requires ≥ 1 line. Sets `quoted_at` and
  `quote_valid_until = today + sales.quoteValidityDays`. Emails every active contact of the customer a portal link
  (the existing portal magic-link login) with the quote PDF attached.
- A quote is **expired** when `quote_valid_until < today` (Asia/Manila). Derived; no scheduler. An expired quote
  can't be confirmed or accepted (422 `QUOTE_EXPIRED`) until the rep sends it again.

### Confirm

`DRAFT | QUOTED → CONFIRMED`, by a rep, or by a portal accept. All checks run and **every** failing type is returned
together:

| Check | Approval type |
|---|---|
| Any line `discount_pct > approval.discountPercentMax` | `DISCOUNT` |
| Any line net unit price (`unit_price × (1 − discount_pct/100)`) below the product's current `cost` | `BELOW_COST` |
| Exposure > `credit_limit`, when a limit is set | `CREDIT_LIMIT` |
| The customer has any `OVERDUE` order | `CREDIT_LIMIT` |

- **Exposure** = Σ balances of the customer's other non-cancelled orders that are `CONFIRMED` or later + this order's
  `total`.
- Customers with no `credit_limit` are cash customers: the limit check is skipped at confirm; the Release gate
  covers them. The overdue check still applies.
- Each check passes if `Approvals.isApproved(type, "ORDER", id, version)` is true. ADMINs pass all checks
  (audited "self-approved (admin)"), per Foundation §5.
- Failure → 422 `APPROVAL_REQUIRED` with `approvals: [{type, requestedValue}]` alongside the Foundation fields. This
  extends the Foundation payload from one type to a list.

### Portal accept and decline

- **Accept** (`QUOTED`, not expired): sets `customer_accepted_at`, then attempts confirm as `SYSTEM`.
  - Checks pass → `CONFIRMED`.
  - Checks fail → the order stays `QUOTED` with `customer_accepted_at` set. The system creates one approval request
    per failing type (requested by the order's rep, reason "Customer accepted via portal") and notifies the rep.
  - An `ApprovalDecided` after-commit listener retries confirm for orders with `customer_accepted_at` set. It is
    idempotent: it does nothing if the order is no longer `QUOTED` or any approval is still missing.
- **Decline** (`QUOTED`): records the reason in the audit trail, notifies the rep, returns the order to `DRAFT`.
- Editing an accepted-but-unconfirmed order returns it to `DRAFT` and clears `customer_accepted_at`.

### Revise

`PUT /api/orders/{id}` on a `CONFIRMED` or `RELEASED` order is a revision and requires `reason`:

1. Write `order_revision` with the current state.
2. Apply the change, `revision += 1`, version bumps (old approvals stop matching).
3. Re-run the confirm checks. Failure → 422 `APPROVAL_REQUIRED`; nothing is saved.
4. If an `ISSUED` invoice exists: requires `INVOICE_VOID` approval (ADMIN exempt), then voids it and issues a new
   one in the same transaction (§4).

`FULFILLED` and `CANCELLED` orders can't be revised.

### Release

`CONFIRMED → RELEASED`.

- Credit customers (`credit_limit` set): allowed.
- Cash customers: allowed when the balance is 0; otherwise requires `CREDIT_LIMIT` approval (`requestedValue`
  `{"reason": "SHIP_BEFORE_PAYMENT", "balance": …}`).

In #2 release only sets the status. #3 turns released orders into delivery orders.

### Fulfil

`RELEASED → FULFILLED` via a manual **Mark fulfilled** action, ADMIN only. #3 replaces it with delivery-order
completion.

### Cancel

Any state before `FULFILLED`, with a required reason.

- Verified payments exist → 422 `HAS_PAYMENTS`. Refunds happen outside the system for now.
- Pending payments are rejected automatically with reason "Order cancelled".
- An `ISSUED` invoice is voided, with the same `INVOICE_VOID` rule as revise.

## 4. Money, invoices and payments

### OrderTotals

A pure class with no Spring or JPA dependency.

1. Line net = `round(qty × unit_price × (1 − discount_pct/100), 2, HALF_UP)`.
2. Effective tax class = customer `tax_treatment` if not `REGULAR`, else the line's `tax_class`. The shipping fee is
   a `VATABLE` amount under the same override.
3. Sum gross per class at **order level** (not per line, to avoid accumulated rounding):
   - `vatable_sales = round(vatable_gross / 1.12, 2, HALF_UP)`
   - `vat = vatable_gross − vatable_sales` (remainder, so the breakdown always sums exactly)
   - `vat_exempt_sales`, `zero_rated_sales` = their gross
   - `total = vatable_gross + vat_exempt_sales + zero_rated_sales`

### Issue invoice

`POST /api/orders/{id}/invoice` on a `CONFIRMED | RELEASED | FULFILLED` order with no active invoice.

- Requires the `company.*` settings to be filled (422 `VALIDATION_FAILED` naming the missing keys).
- In one transaction: lock the order row; `SELECT … FOR UPDATE` the `INVOICE` counter, take `next_value`,
  increment; insert the invoice with frozen customer (name, TIN, address) and lines snapshots and the totals.
  A rollback rolls back the counter, so numbers are gapless.
- `due_date = issued_at (Asia/Manila date) + payment_terms days`.

### Void and reissue

- Triggered by revise, cancel, or `POST /api/orders/{id}/invoice/reissue {reason}` (fixes a wrong customer TIN or
  address without changing the order).
- Requires `INVOICE_VOID` approval (subject = the order and its version), ADMIN exempt.
- Sets the old invoice `VOID` with reason, `voided_by`, `voided_at`; issues the new invoice (next number) and sets
  `replaced_by_id`. Cancel voids without reissuing.
- Payments are attached to the order, so balances carry over with no extra logic.

### Payments

- **Recorded by a rep** → `VERIFIED` immediately.
- **Submitted from the portal** → `PENDING`; a proof file is required. The rep gets an Inbox item to verify or
  reject (reason required). Only `VERIFIED` payments count towards the balance.
- Recording or verifying locks the order row, then rejects anything that would make the balance negative
  (422 `OVERPAYMENT`).
- Allowed on any non-cancelled order from `CONFIRMED` onwards, including before invoicing (pay before ship).
- **Withholding:** for `BUSINESS` customers the form suggests
  `sales.withholdingSuggestPercent` % of `vatable_sales + vat_exempt_sales + zero_rated_sales`. The rep can edit or
  clear it.
- **Form 2307:** `PUT /api/payments/{id}/form-2307` attaches a file at any time, by a rep, or by the contact who
  submitted the payment.
- **Missing-2307 report:** verified payments with `withholding_amount > 0` and no `form2307_file_id`, filterable by
  customer, rep and date range. Exportable as CSV (audited, per Foundation §6).

### Documents

- Quote and invoice PDFs are Thymeleaf templates rendered by the PDF rendering carried over in Foundation.
- The quote PDF renders from the live order. The invoice PDF renders **on demand from the frozen invoice**, so
  nothing is stored and a reprint is identical. Void invoices print with a **VOID** watermark.
- The invoice PDF shows: seller name, VAT-registered TIN and address; "SALES INVOICE" and the serial number; date;
  buyer name, TIN and address; lines (description, qty, unit, unit price, amount); VATable sales, VAT, VAT-exempt
  sales, zero-rated sales, total; payment terms and due date; `invoice.footerText`.

> **Compliance note (not code):** BIR requires a computerized invoicing system to be registered (Permit to Use or
> CAS acceptance) before it prints invoices in production. The owner must obtain it before go-live and put the
> permit text in `invoice.footerText`.

## 5. Access and API

### Authorization

| Role | Catalog | Orders, invoices, payments | `cost` |
|---|---|---|---|
| ADMIN | read/write | all | yes |
| SALES | read | orders whose customer is visible via `CustomerScopes.visibleTo(scope)` | no |
| WAREHOUSE | read | none in #2 | no |
| Contact (portal) | — | own company's orders in `QUOTED` or later, their invoices and payments | no |

- Order scope follows the customer: reassigning a customer moves its orders to the new rep.
- An order that returns to `DRAFT` (edited or declined quote) disappears from the portal until it is sent again.
- Out of scope → 404 `NOT_FOUND`.
- `cost` is omitted from the product DTO for non-ADMIN (separate response type, not a nulled field). Portal DTOs
  omit internal notes, rep details and approval information.
- Mark fulfilled is ADMIN only. Everything else on orders is ADMIN or SALES within scope.

### Endpoints

Staff:

| Method | Path |
|---|---|
| `GET`, `POST` | `/api/products` (`?q=&includeArchived=`) |
| `GET`, `PUT` | `/api/products/{id}` |
| `POST` | `/api/products/{id}/archive` |
| `GET` | `/api/orders?status=&paymentStatus=&customerId=&repId=&q=` |
| `POST` | `/api/orders` |
| `GET`, `PUT` | `/api/orders/{id}` (PUT on CONFIRMED/RELEASED = revise, `reason` required) |
| `POST` | `/api/orders/{id}/send-quote`, `/confirm`, `/release`, `/fulfil`, `/cancel` |
| `GET` | `/api/orders/{id}/revisions`, `/history`, `/quote.pdf` |
| `POST` | `/api/orders/{id}/invoice`, `/api/orders/{id}/invoice/reissue` |
| `GET` | `/api/invoices/{id}.pdf` |
| `GET`, `POST` | `/api/orders/{id}/payments` |
| `POST` | `/api/payments/{id}/verify`, `/api/payments/{id}/reject` |
| `PUT` | `/api/payments/{id}/form-2307` |
| `GET` | `/api/reports/missing-2307` (`?format=csv` for export) |
| `GET` | `/api/customers/{id}/orders`, `/api/customers/{id}/invoices` |

Portal:

| Method | Path |
|---|---|
| `GET` | `/api/portal/orders`, `/api/portal/orders/{id}` |
| `POST` | `/api/portal/orders/{id}/accept`, `/api/portal/orders/{id}/decline` |
| `GET`, `POST` | `/api/portal/orders/{id}/payments` |
| `PUT` | `/api/portal/payments/{id}/form-2307` |
| `GET` | `/api/portal/orders/{id}/quote.pdf`, `/api/portal/invoices/{id}.pdf` |

- Payment `POST`s and invoice issue/reissue require an `Idempotency-Key` (existing `IdempotencyFilter`).
- State-changing order requests carry the order `version`; a mismatch → 409 `STALE_VERSION`.

### Errors

New codes: `INVALID_STATE`, `QUOTE_EXPIRED`, `HAS_PAYMENTS`, `OVERPAYMENT`. `APPROVAL_REQUIRED` gains
`approvals[]`.

### Audit and notifications

- Audited in the same transaction: product CRUD/archive; order create, edit, every transition, revision; invoice
  issue and void; payment record, submit, verify, reject, 2307 attach; quote accept/decline (actor `CONTACT`);
  missing-2307 export.
- After-commit notifications (SSE + Inbox) to the order's rep: quote accepted or declined, portal payment
  submitted, an auto-created approval decided, auto-confirm succeeded.

## 6. Screens (`crm-dashboard-ui`)

Feature folders `src/features/catalog` and `src/features/sales`, using the `src/ui` kit, generated OpenAPI types and
react-hook-form + zod, as in Foundation.

- **Sell → Orders:** DataTable with saved views *Quotes*, *To confirm*, *To release*, *Unpaid*, *Overdue*, *All*.
  Columns: number, customer, status, payment status, total, balance, rep, updated. **New order** → customer picker
  → workspace.
- **Order workspace** `/orders/:id`:
  - Header: number, customer, status and payment pills, and one primary action that follows the state (Send
    quote → Confirm → Release → Mark fulfilled). Issue invoice, Revise, Reissue invoice and Cancel sit in a "⋯"
    menu.
  - Approval banner when a 422 lists approvals: one row per type with **Request approval** and its status.
  - Tabs: **Lines** (editable grid in DRAFT/QUOTED with product search, qty, price, discount; below-cost warning
    for ADMIN; read-only once confirmed with an *Edit (revise)* mode that asks for a reason), **Invoice** (active
    invoice + greyed void ones, PDF links), **Payments** (list, *Record payment* drawer with the withholding
    suggestion, verify/reject, 2307 upload), **Revisions** (snapshot list with a line diff against current),
    **History** (audit feed).
  - Right rail: VATable sales, VAT, exempt, zero-rated, shipping, total, paid, balance.
- **Customer page:** fills Foundation's *Orders* and *Invoices* tab placeholders.
- **Sell → Products:** list and create/edit drawer (ADMIN); read-only without cost for SALES.
- **Insights → Missing 2307:** the report with CSV export.
- **Settings:** company details and invoice footer fields on the existing admin settings screen.
- **Portal:** *My orders* list (status, balance) and an order page with lines and totals, **Accept**/**Decline**
  while the quote is valid, quote and invoice PDFs, **Upload payment proof** (amount, withholding, date,
  reference, file) and the status of each submitted payment.

## 7. Testing

Same harness as Foundation: Testcontainers Postgres, endpoint authorization matrix, OpenAPI snapshot, Modulith
`verify()`.

- **Unit:** `OrderTotals` (mixed classes, customer override, discount rounding, shipping, breakdown always sums to
  total); `OrderStateMachine` (every allowed and rejected transition); exposure calculation; withholding
  suggestion; derived payment status.
- **Integration:**
  - Concurrent invoice issue plus a forced rollback → numbers are consecutive with no gaps or duplicates.
  - Second active invoice per order is rejected by the partial unique index.
  - Void and reissue keeps payments and balance; the void invoice keeps its number.
  - Each approval type blocks, then passes once approved; a revision invalidates the old approval.
  - Portal accept → approvals auto-created → approved → order auto-confirmed; listener is idempotent.
  - Racing payments on one order → exactly one gets `OVERPAYMENT`.
  - Cash-customer release blocked until paid or approved.
  - Expired quote can't be confirmed or accepted.
  - Scoping: another rep's order and another company's order → 404.
  - `cost` absent from SALES and portal JSON.
  - Authorization matrix and OpenAPI snapshot extended to every new endpoint.
- **Frontend:** unit tests for the workspace's state → actions mapping and the zod schemas.
