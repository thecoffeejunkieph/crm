# Redesign #4 — POS (requirements)

Date: 2026-11-02
Repos: `crm` (backend), `crm-dashboard-ui` (frontend)
Builds on: `2026-10-14-sales-lifecycle-design.md`, `2026-10-21-inventory-fulfilment-design.md`
Supersedes: `2026-10-11-pos-design.md` (old model; reference for UX only)

Status: requirements captured ahead of brainstorming. Full design to follow when #4 starts.

## Requirements

### POS sales create an invoice

A POS checkout produces a normal sales `order` with an `ISSUED` invoice, not a separate sale record.

- The checkout transaction creates the order, moves it straight to the right status, and issues the invoice through
  the existing issue path (`document_counter`, frozen snapshots, `company.*` settings required). Invoice numbers
  stay gapless and shared with B2B (`SI-…`).
- The invoice shows in the order workspace **Invoice** tab and in the customer's invoice list like any other invoice:
  View PDF, Reissue (with `INVOICE_VOID` approval) and the void watermark all work unchanged.
- The old spec's `invoice.channel = POS` + "created already `PAID`" model is dropped. Paid status comes from
  payments, same as B2B.

### Proof of payment can be uploaded after checkout

POS payments are `payment` rows on the order, so they follow the existing payment rules.

- At checkout the cashier records one or more payments (split tender). Proof is optional at that point
  (`POST /api/orders/{id}/payments` already takes an optional `proof` part).
- After checkout, the rep can still upload proof for the sale's payments, e.g. a GCash or bank-transfer screenshot
  sent later. **Gap:** today proof can only be attached while recording a payment. Add
  `PUT /api/payments/{id}/proof` (multipart), mirroring `PUT /api/payments/{id}/form-2307`, with the UI action on
  the order's payments list.
- A sale that is not fully paid at checkout keeps a balance and appears unpaid, same as B2B.

## Open for brainstorming

- Whether a POS sale must be fully paid at checkout, or can be left with a balance (credit/pay-later).
- Which order status a POS sale lands in (`FULFILLED` immediately, since goods leave at the counter), and how the
  stock movement posts through `StockLedger` (see inventory plan note).
- Receipt vs. invoice PDF: whether the printed receipt is the invoice PDF or a separate receipt layout.
- Carry-overs from the old spec: price overrides and discounts, quick-add customer (`pos.quickAddAssignment`),
  emailed receipt, senior citizen/PWD discount.
