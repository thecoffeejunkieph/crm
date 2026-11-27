# Portal quote requests and reorder — design (2026-11-22)

Both repos: `crm/crm` (API) and `crm-dashboard-ui` (portal screen). Tracker item: O-02 (Smoother operations).

## Goal

A customer asks for a quote from the portal instead of messaging staff: they pick items and quantities, or start
from a past order ("Order again"). Staff get a draft order to price and send as a normal quote.

## Decisions

| Question | Decision |
|---|---|
| What the customer sees in the picker | Active products: SKU, name, unit. **No prices**: staff price the draft (customers can have special pricing) |
| What is created | A `DRAFT` sales order for the contact's company, lines at catalog default price, no discount |
| Portal visibility of the draft | None, as today: drafts stay internal. The customer sees it once staff send the quote (existing quote mail) |
| Rep / created by | `repId` = the customer's assigned rep (may be null). `createdBy` = that rep, else the first active admin |
| Notes | `Requested via portal by {contact}.` plus the customer's note (max 1000 chars) |
| Who is told | New notification `QUOTE_REQUESTED`: the assigned rep, or every active admin when there is none |
| Audit | `CREATED` on the order, actor = the contact |
| Limits | 1–50 lines, qty > 0, unknown or archived product → 400 with the line's field. `Idempotency-Key` required; `RateLimitFilter` rule: 10 requests/min |
| Reorder | `GET /orders/{id}/reorder` returns the order's still-active products with summed quantities. The order JSON itself keeps no `productId` (`PortalOrdersIT` asserts this) |
| Archived customer | 400, same rule as staff create |

## API

| Method | Path | Body / query | Response |
|---|---|---|---|
| GET | `/api/portal/products` | `q`, `page`, `size` | `PageResponsePortalProduct` (`id, sku, name, unit`) |
| GET | `/api/portal/orders/{id}/reorder` | — | `PortalProduct[]` with `qty` |
| POST | `/api/portal/quote-requests` | `{lines: [{productId, qty}], notes}` + `Idempotency-Key` | 201 `{number}` |

`/api/portal/**` is already CUSTOMER-only; the service scopes to the contact's own company.

## UI

- `/portal/request` (`PortalRequestPage`): product search, lines with quantity, note, Submit. Success → toast
  "Request sent. We'll email you a quote." and back to My orders.
- `/portal/request?from={orderId}`: pre-filled from that order's lines (only products still in the active list).
- "Request a quote" button on My orders; "Order again" link on an order page.

## Out of scope

Showing pending requests in the portal, customer-specific price lists, editing a request after sending.
