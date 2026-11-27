# Purchasing: supplier downpayment terms, PO document, resend

Date: 2026-11-25
Status: approved design, pending spec review
Builds on: `2026-11-24-purchasing-design.md`

## Goal

1. Edit a supplier from the suppliers list, not only from its detail page.
2. Support suppliers whose terms are "X% paid up front, the rest on credit" (for example a ₱2,500,000 limit with 50% downpayment per order).
3. Give every PO a document in the style of the existing PDFs (invoice, quote) that can be viewed, downloaded as PDF or PNG, and emailed to the supplier (automatically on send, and again on demand).

## Decisions

| Topic | Decision |
|---|---|
| Downpayment timing | Must be paid before the PO can be sent. |
| How it is recorded | As an ordinary supplier payment, optionally linked to a PO. |
| Credit math | Unchanged. The downpayment lowers the balance when recorded; receipt adds the full value; the net on credit is the remainder. |
| Image format | PNG, a full-page screenshot of the same HTML in the same Chromium that prints the PDF. |
| Emailing | Automatic when a PO becomes `SENT` (direct or after approval), if the supplier has an email; plus a manual Resend. |

## Out of scope

Automatic refund of a downpayment on cancel, per-PO term overrides, multiple downpayment stages, emailing drafts.

## Data model (`V31__purchasing_terms.sql`)

- `supplier` adds `downpayment_percent numeric(5,2) not null default 0`, `CHECK (downpayment_percent between 0 and 100)`.
- `supplier_payment` adds `purchase_order_id bigint` (FK `purchase_order`, nullable). A linked payment must belong to the PO's supplier (validated in the service).

## Supplier edit and terms

- **Suppliers list**: each row gets an Edit action opening the existing `SupplierDrawer`. The detail page keeps its Edit button.
- **Supplier form**: new "Downpayment %" field (0–100, default 0). Supplier request/response DTOs gain `downpaymentPercent`.
- **Payments**: `SupplierPaymentRequest` gains optional `purchaseOrderId`; the response gains `purchaseOrderId` and `purchaseOrderNumber`. Linking is refused (400) when the PO belongs to another supplier or is `CANCELLED`.
- **Statement**: a linked payment's entry reads "Downpayment – PO-0012".

## Send rule

- **Required** = `round_half_up(downpayment_percent / 100 × PO total, 2)`.
- **Paid** = Σ amounts of payments linked to the PO.
- `POST /api/purchase-orders/{id}/send` is refused with 409 `INVALID_STATE` ("PO-0012 needs a downpayment of ₱X before sending; ₱Y recorded") when paid < required. The check runs before the credit check.
- `PoResponse` gains `downpaymentRequired` and `downpaymentPaid` (both 0 when the supplier has no term).
- **Approval path**: the check happens at send time, so an over-limit PO only reaches `PENDING_APPROVAL` once the downpayment is met.
- **Edits**: a draft stays editable after a downpayment; a higher total raises the requirement and Send asks for the difference.
- **Cancel**: linked payments stay, so the supplier balance shows the prepaid amount. Deleting the payment (ADMIN, audited) undoes it if the supplier refunded.

## PO document

- **Template**: `templates/pdf/purchase-order.html`, reusing `fragments/parts.html`, the logo and the compiled Tailwind of the existing PDFs. Run `npm run build` in `tailwind/` after adding classes. Contents:
  - PO number, date, status; drafts show a "DRAFT" tag.
  - Supplier block (name, contact, phone, email, address) and deliver-to warehouse.
  - Expected date; line table (product, SKU, qty, unit cost, line total); PO total.
  - Terms box when the supplier has a downpayment or payment-terms days, e.g. "50% downpayment ₱X (paid ₱Y) · balance on 30-day credit".
  - Notes.
- **Renderer**: `PdfRenderer` gains `renderPng(template, vars)`, sharing template processing and the locked browser; it uses `page.screenshot(fullPage)` at an A4-like viewport width instead of `page.pdf`.
- **Endpoints**:
  - `GET /api/purchase-orders/{id}.pdf` and `GET /api/purchase-orders/{id}.png`: ADMIN, and WAREHOUSE for POs it can read; any status; `Content-Disposition` filename `PO-0012.pdf` / `.png`.
  - `POST /api/purchase-orders/{id}/resend`: ADMIN only; allowed in `SENT` and `PARTIALLY_RECEIVED`, otherwise 409. Returns `{to: [...]}`. An empty list means the supplier has no email and nothing was sent.

## Emailing

- New `PurchasingMailer` in `purchasing` and mail template `templates/mail/po-sent.html` in the style of `invoice-sent.html`. It sends after commit, attaching the PDF rendered off the transaction, the same way `SalesMailer.invoiceAfterCommit` does.
- **Triggers**: `send` when the result is `SENT`, `PoApprovalListener` on approval, and `resend`.
- **Audit**: each email is audited as "Sent PO PO-0012 to x@y" (new `AuditAction.PO_SENT`).

## Frontend (`crm-dashboard-ui`)

- **Suppliers list**: Edit row action.
- **Supplier drawer**: Downpayment % field.
- **PO page**:
  - View (opens the PDF in a new tab), Download PDF, Download image, Resend (on `SENT`/`PARTIALLY_RECEIVED`; toast lists recipients or "This supplier has no email, so nothing was sent").
  - "Record downpayment" button on drafts with an unmet requirement, opening `PaymentDialog` with the PO linked and the remaining amount prefilled.
  - The credit-check panel shows "Downpayment ₱X required, ₱Y recorded"; Send is disabled until met.
- Regenerate the API client (`npm run gen:api`).

## Testing

- **IT, downpayment**:
  - Send is refused without the downpayment, then succeeds after a linked payment.
  - Linking a payment to another supplier's PO is refused.
  - Credit available after send equals limit − (total − downpayment).
- **IT, document**:
  - The `.pdf` body starts with `%PDF`; the `.png` body starts with the PNG signature.
  - Resend on a draft is 409.
  - Resend with no supplier email returns an empty `to`.
  - Send emails the supplier (mail captured as in existing mail ITs).
- **Contract checks**: security matrix rows for the three new endpoints; OpenAPI snapshot updated.
- **Frontend tests**: Send is disabled with an unmet downpayment; the Edit row action opens the drawer.
