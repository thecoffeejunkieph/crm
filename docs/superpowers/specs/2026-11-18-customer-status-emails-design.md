# Customer status emails — design (2026-11-18)

Backend only: `crm/crm`. No API, DTO or screen changes, so no OpenAPI snapshot update and no `npm run gen:api`.
Tracker item: O-01 (Smoother operations).

## Goal

Customers hear about their order without asking: when it is confirmed, when a payment is received or not
accepted, when it leaves the warehouse, and when it is delivered or picked up. This cuts the "where's my order?"
and "did you get my payment?" messages staff answer by hand today.

## Decisions

| Question | Decision |
|---|---|
| Recipients | `Customers.portalContactEmails(customerId)`: the same portal-enabled contacts that get quotes and invoices. None → nothing is sent |
| Timing | After the transaction commits, on `applicationTaskExecutor`. A rolled-back action never mails anyone |
| Failure | Logged at WARN per recipient, not retried, the same as quote and invoice mails |
| Audit | None. Sends are logged, not audited (status mails are a by-product of an audited action) |
| On/off switch | None. Always on. Add an admin setting later if a need appears |
| Pickup "ready" mail | None. A pickup goes from PICKING straight to DELIVERED, so there is no "ready" moment to report |
| Partial deliveries | Not a case: a sales order has at most one non-cancelled delivery order (`Fulfilment.forOrder`), so one dispatched and one delivered mail per order |
| Proof photos | Attached to the delivered mail (at most `MAX_PROOF_PHOTOS`, already enforced on upload) |

## 1. Emails

| Template (`templates/mail/`) | Trigger | Subject | Content |
|---|---|---|---|
| `order-confirmed.html` | `OrderConfirmed` | `Order {number} confirmed — {company}` | Order number, grand total, portal link |
| `payment-received.html` | `PaymentVerified` | `Payment received for {number} — {company}` | Amount, method, received-on date, balance remaining, portal link |
| `payment-rejected.html` | new `PaymentRejected` | `Payment for {number} was not accepted — {company}` | Amount, reason, portal link to upload again |
| `order-dispatched.html` | new `DeliveryDispatched` | `Order {orderNumber} is out for delivery — {company}` | Delivery order number, ship-to, lines (product, quantity) |
| `order-delivered.html` | `OrderDelivered` | `Order {orderNumber} delivered — {company}` (pickup: `… picked up — …`) | Receiver name, time (Asia/Manila), lines; proof photos attached |

Templates follow the existing `quote-sent.html` / `invoice-sent.html` layout. Portal link is
`{app.frontend-url}/portal/signin`, as in the quote mail.

## 2. Events

| Event | Change | Published in |
|---|---|---|
| `OrderConfirmed(orderId, customerId)` | unchanged | `OrderService` confirm |
| `PaymentVerified(orderId, paymentId, repId, verifiedAt)` | unchanged | `PaymentService.verified` |
| `PaymentRejected(orderId, paymentId)` | **new**, `sales` package | `PaymentService.reject`, after `markRejected` |
| `DeliveryDispatched(deliveryOrderId, salesOrderId)` | **new**, `fulfilment` package | `DeliveryService.dispatch`, after the audit record |
| `OrderDelivered(orderId, deliveredAt)` | **gains `deliveryOrderId`** → `OrderDelivered(orderId, deliveryOrderId, deliveredAt)` | `DeliveryService.deliver` |

`DeliveredListener` keeps working (it only reads `orderId` and `deliveredAt`). `DeliveryOrdersIT.deliverPublishesOrderDelivered`
is updated for the new component.

## 3. Components

One listener in `sales`. Sales already depends on fulfilment (`Fulfilment`, `DeliveryRequest`, `OrderDelivered`), and
fulfilment knows nothing about customers, so the delivery mails live in sales too; fulfilment only publishes events and
exposes one read. No dependency from fulfilment to sales is added.

**`sales/CustomerStatusMails`** (package-private `@Component`). Every handler is
`@TransactionalEventListener(phase = AFTER_COMMIT)` and hands the work to `applicationTaskExecutor`, where it loads what it
needs in a read-only transaction and calls `HtmlMail.send`. Each recipient is sent in its own `try`, so one bad address
does not stop the rest. Recipients always come from the sales order's `customerId`.
- `on(OrderConfirmed)` → load order; send `order-confirmed`.
- `on(PaymentVerified)` → load order and payment; send `payment-received` with `order.balance()` read after commit.
- `on(PaymentRejected)` → load payment; **skip unless `submittedByType == CONTACT`** (a customer's portal upload).
  A payment a rep recorded is an internal correction and does not mail the customer. Send `payment-rejected`.
- `on(DeliveryDispatched)` → `fulfilment.mailView(deliveryOrderId)`; send `order-dispatched`.
- `on(OrderDelivered)` → `fulfilment.mailView(deliveryOrderId)`; send `order-delivered` with the photos as attachments.
  A photo that cannot be read is skipped (WARN) and the mail still goes.

**`Fulfilment.mailView(long deliveryOrderId)`** (new public, read-only) returns a new public record
`DeliveryMailView(number, orderNumber, method, shipTo, receiverName, deliveredAt, lines[description, qty, unit], photos[filename, contentType, bytes])`.
Photo bytes are read through a new `StorageService.read(String key): byte[]` inside fulfilment, so sales never touches storage keys.

## 4. Error handling

- No portal contacts → no mail, no log noise beyond DEBUG.
- Template or SMTP failure → WARN with template, order number and address; the business action is already committed and unaffected.
- Listener runs after commit, so an exception there cannot roll back the action.

## 5. Testing

Integration tests with `@MailIntegrationTest` and `MailSupport.stub(sender)`, as in `PasswordResetIT`:

1. Confirming an order mails each portal contact `order-confirmed`; a customer with no portal contacts gets nothing.
2. Verifying a payment mails `payment-received` with the remaining balance.
3. Rejecting a portal-uploaded payment mails `payment-rejected` with the reason; rejecting a rep-recorded payment sends nothing.
4. Dispatching a delivery order mails `order-dispatched`; dispatch that fails its state check sends nothing (rollback).
5. Delivering mails `order-delivered` with the proof photo attached; a pickup uses the "picked up" subject.
6. `deliverPublishesOrderDelivered` asserts the new `deliveryOrderId`.

## Out of scope

Customer opt-out or admin on/off setting, SMS or Messenger, retries or an outbox, "ready for pickup",
in-portal notification feed, emails for refunds and credit memos (credit memos are already downloadable in the portal).
