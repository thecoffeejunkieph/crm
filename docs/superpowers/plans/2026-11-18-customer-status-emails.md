# Customer Status Emails Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Email a customer's portal contacts when their order is confirmed, a payment is received or not accepted, the order is out for delivery, and it is delivered or picked up.

**Architecture:** Fulfilment and sales publish domain events (two new, one extended). One package-private listener in `sales`, `CustomerStatusMails`, handles all five after commit on `applicationTaskExecutor` and sends through the existing `HtmlMail`. Fulfilment exposes one read, `Fulfilment.mailView`, that returns the delivery order with its photo bytes, so sales never touches storage keys and fulfilment gains no dependency on sales.

**Tech Stack:** Java 21, Spring Boot 4, Spring events (`@TransactionalEventListener`), Thymeleaf mail templates, MinIO via `StorageService`, JUnit 5 + Testcontainers ITs.

**Spec:** `docs/superpowers/specs/2026-11-18-customer-status-emails-design.md`

## Global Constraints

- Backend only (`crm/crm`). No DTO or endpoint changes: no OpenAPI snapshot update, no `npm run gen:api`.
- Recipients are exactly `Customers.portalContactEmails(customerId)`. Empty list → send nothing.
- Mail goes only after commit, on the `applicationTaskExecutor` bean. Each recipient in its own `try`; failures are `log.warn`, never rethrown, never retried, never audited.
- Subjects, verbatim (`{company}` = `HtmlMail.company()`):
  - `Order {number} confirmed — {company}`
  - `Payment received for {number} — {company}`
  - `Payment for {number} was not accepted — {company}`
  - `Order {orderNumber} is out for delivery — {company}`
  - `Order {orderNumber} delivered — {company}` / pickup: `Order {orderNumber} picked up — {company}`
- Templates live in `src/main/resources/templates/mail/`, follow `invoice-sent.html`, and use `th:text` only (never `th:utext`): the reject reason is staff-typed text shown to customers.
- Portal link variable `link` = `{app.frontend-url}/portal/signin`.
- Times shown to customers are Asia/Manila.
- Gradle in Claude's shell: prefix with `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/Users/Public/gtmp` and pass `--no-daemon`. If `build/` is locked by a running dev server, add `-I` with an init script setting `layout.buildDirectory = file('C:/Users/Public/gbuild')`.
- Commit only the files each task touches. The working tree has unrelated uncommitted catalog changes. Never `git add -A`.

## Review Focus

1. Customer with no portal-enabled contacts: the action succeeds and no mail is sent. Test in Task 3.
2. A contact with `portal_enabled = false` is never mailed, even when another contact is. Test in Task 3.
3. One address fails at SMTP: the other contacts still get the mail. Test in Task 3.
4. A proof photo whose object is missing from storage: the delivered mail still goes, without that photo. Test in Task 2 (`mailView` skips it).
5. An action that fails or rolls back (a dispatch refused by its state check) mails nobody. Test in Task 4.

---

### Task 1: Events: `PaymentRejected`, `DeliveryDispatched`, `OrderDelivered.deliveryOrderId`

**Files:**
- Create: `src/main/java/ph/thecoffeejunkie/crm/sales/PaymentRejected.java`
- Create: `src/main/java/ph/thecoffeejunkie/crm/fulfilment/DeliveryDispatched.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/fulfilment/OrderDelivered.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/sales/PaymentService.java` (`reject`, ~line 127)
- Modify: `src/main/java/ph/thecoffeejunkie/crm/fulfilment/DeliveryService.java` (`dispatch` ~line 146, `deliver` ~line 186)
- Test: `src/test/java/ph/thecoffeejunkie/crm/sales/PaymentsIT.java`, `src/test/java/ph/thecoffeejunkie/crm/fulfilment/DeliveryOrdersIT.java`

**Interfaces:**
- Produces:
  - `public record PaymentRejected(long orderId, long paymentId)` in `sales`. Published in `reject` after `markRejected`.
  - `public record DeliveryDispatched(long deliveryOrderId, long salesOrderId)` in `fulfilment`. Published in `dispatch` after the audit record.
  - `public record OrderDelivered(long orderId, long deliveryOrderId, Instant deliveredAt)`. Component order changes, so update every constructor call.

- [ ] **Step 1: Write the failing tests**

In `PaymentsIT`, extend `rejectNotifiesRepWithReason`:
```java
assertThat(events.stream(PaymentRejected.class)).singleElement()
        .satisfies(e -> { assertThat(e.orderId()).isEqualTo(order); assertThat(e.paymentId()).isEqualTo(p); });
```
In `DeliveryOrdersIT`, add `dispatchPublishesDeliveryDispatched`: `long id = dispatched("4");`, then assert a single `DeliveryDispatched` with `deliveryOrderId() == id` and `salesOrderId()` equal to `select sales_order_id from delivery_order where id = ?`. Extend `deliverPublishesOrderDelivered` with `assertThat(e.deliveryOrderId()).isEqualTo(id)`. Extend `doubleDispatchDeductsOnce` with `assertThat(events.stream(DeliveryDispatched.class)).hasSize(1)`.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew test --no-daemon --tests '*PaymentsIT' --tests '*DeliveryOrdersIT'`
Expected: compilation failure (`PaymentRejected`, `DeliveryDispatched`, `deliveryOrderId()` not found).

- [ ] **Step 3: Implement**

Add the two records, add the `deliveryOrderId` component to `OrderDelivered`, and publish in `reject`, `dispatch` and `deliver` (`new OrderDelivered(d.salesOrderId, d.id, d.deliveredAt)`). `PaymentService` already has `events`. Inject `ApplicationEventPublisher` into `DeliveryService` if it isn't already there; it already publishes `OrderDelivered`, so reuse that field.

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew test --no-daemon --tests '*PaymentsIT' --tests '*DeliveryOrdersIT' --tests '*FollowUp*'`
Expected: PASS. The follow-up rules listen to `OrderDelivered`, so this confirms the component change broke nothing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ph/thecoffeejunkie/crm/sales/PaymentRejected.java src/main/java/ph/thecoffeejunkie/crm/fulfilment/DeliveryDispatched.java src/main/java/ph/thecoffeejunkie/crm/fulfilment/OrderDelivered.java src/main/java/ph/thecoffeejunkie/crm/sales/PaymentService.java src/main/java/ph/thecoffeejunkie/crm/fulfilment/DeliveryService.java src/test/java/ph/thecoffeejunkie/crm/sales/PaymentsIT.java src/test/java/ph/thecoffeejunkie/crm/fulfilment/DeliveryOrdersIT.java
git commit -m "feat(events): payment rejected, delivery dispatched, delivered carries delivery order"
```

---

### Task 2: `StorageService.read` and `Fulfilment.mailView`

**Files:**
- Modify: `src/main/java/ph/thecoffeejunkie/crm/files/StorageService.java`
- Create: `src/main/java/ph/thecoffeejunkie/crm/fulfilment/DeliveryMailView.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/fulfilment/Fulfilment.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/files/StorageServiceIT.java` (existing; find it with Glob `**/StorageServiceIT.java`), `src/test/java/ph/thecoffeejunkie/crm/fulfilment/DeliveryOrdersIT.java`

**Interfaces:**
- Produces:
  - `public byte[] read(String key)` on `StorageService`. Uses `client.getObject(GetObjectArgs…)`. A missing object or I/O error throws `java.io.UncheckedIOException` or `IllegalStateException`. The caller decides what to skip.
  - `public record DeliveryMailView(String number, String orderNumber, DeliveryMethod method, String shipTo, String receiverName, Instant deliveredAt, List<Line> lines, List<HtmlMail.Attachment> photos)` with nested `public record Line(String description, BigDecimal qty, String unit)`.
  - `@Transactional(readOnly = true) public Optional<DeliveryMailView> mailView(long deliveryOrderId)` on `Fulfilment`. Empty when the id does not exist. Photos are named `{number}-proof-{n}.{ext}` (n from 1), with the content type from `StorageService.detect(bytes, key)`. A key that fails to read is skipped with `log.warn`.

- [ ] **Step 1: Write the failing tests**

`StorageServiceIT.readReturnsStoredBytes`: `put("test/read.png", PNG_BYTES, "image/png")`, then `assertThat(storage.read("test/read.png")).isEqualTo(PNG_BYTES)`. Also `readMissingThrows`: `assertThatThrownBy(() -> storage.read("test/none.png"))`.

`DeliveryOrdersIT.mailViewCarriesLinesAndPhotos`: `long id = dispatched("4"); deliverAll(id, "Juan", PNG, PNG)`. Then `fulfilment.mailView(id)` has `receiverName "Juan"`, `lines` of size 1 with `qty` `4.000`, and `photos` of size 2 with filenames ending `-proof-1.png` and `-proof-2.png` and bytes equal to `PNG`.

`DeliveryOrdersIT.mailViewSkipsUnreadablePhoto` (Review Focus 4): deliver with one PNG, then `jdbc.update` the delivery order's `proof_keys` to append `"delivery-orders/0/proof/missing.png"`. Match the column's storage type by checking how `proofKeys` is mapped in `DeliveryOrder`. `mailView(id).photos()` has size 1.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew test --no-daemon --tests '*StorageServiceIT' --tests '*DeliveryOrdersIT.mailView*'`
Expected: compilation failure (`read`, `mailView` undefined).

- [ ] **Step 3: Implement** the method, record and query as given in Interfaces. `Fulfilment` gains a `StorageService` constructor parameter.

- [ ] **Step 4: Run to verify they pass** with the same command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ph/thecoffeejunkie/crm/files/StorageService.java src/main/java/ph/thecoffeejunkie/crm/fulfilment/DeliveryMailView.java src/main/java/ph/thecoffeejunkie/crm/fulfilment/Fulfilment.java src/test/java/ph/thecoffeejunkie/crm/files/StorageServiceIT.java src/test/java/ph/thecoffeejunkie/crm/fulfilment/DeliveryOrdersIT.java
git commit -m "feat(fulfilment): mail view of a delivery order with proof photos"
```

---

### Task 3: `CustomerStatusMails`: confirmed, payment received, payment rejected

**Files:**
- Create: `src/main/java/ph/thecoffeejunkie/crm/sales/CustomerStatusMails.java`
- Create: `src/main/resources/templates/mail/order-confirmed.html`, `payment-received.html`, `payment-rejected.html`
- Test (create): `src/test/java/ph/thecoffeejunkie/crm/sales/CustomerStatusMailsIT.java`

**Interfaces:**
- Consumes: `OrderConfirmed`, `PaymentVerified`, `PaymentRejected` (Task 1); `Customers.portalContactEmails(long)`; `HtmlMail.send(to, subject, template, vars)`.
- Produces: package-private `@Component class CustomerStatusMails` with constructor `(HtmlMail, @Qualifier("applicationTaskExecutor") TaskExecutor, SalesOrderRepository, PaymentRepository, Customers, PlatformTransactionManager, @Value("${app.frontend-url}") String frontendUrl)`. Each handler is `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` and does `executor.execute(...)`. Inside, it reads through a `TransactionTemplate` with `setReadOnly(true)` and sends outside that read. Shared `private void sendAll(List<String> to, String subject, String template, Map<String,Object> vars, List<HtmlMail.Attachment> files)` with a per-address `try`/`log.warn`.

Template variables:
- `order-confirmed`: `number`, `total` (`order.total.toPlainString()`), `link`.
- `payment-received`: `number`, `amount` (`p.amount().toPlainString()`), `method` (enum name), `receivedOn`, `balance` (`order.balance().toPlainString()`), `link`.
- `payment-rejected`: `number`, `amount`, `reason` (`p.rejectReason`), `link`. **Skip entirely unless `"CONTACT".equals(p.submittedByType)`.** Use the same constant `PaymentService` uses for `submittedByType`.

Test class setup mirrors `SendQuoteIT` (`@MailIntegrationTest`, `MailSupport.stub`, `SalesFixture`, executor for `awaitIdle`). Seed a customer with portal contact `a` (enabled), contact `b` (enabled) and contact `off` (disabled) via `f.contact(customer, name, portalEnabled)`. Get addresses with `f.contactEmail(name)`. Add a private helper `to(MimeMessage)` returning the first recipient string.

- [ ] **Step 1: Write the failing tests**

- `confirmMailsPortalContacts`: create order, `f.stock(product, "10")`, `f.transition(asRep, order, "confirm", null)`. `awaitMail(mail, 2)`: recipients are `{a, b}` (never `off`, Review Focus 2), and the subject equals `"Order " + number + " confirmed — " + company`. Read `company` from `HtmlMail.company()` by autowiring `HtmlMail`.
- `noPortalContactsNoMail` (Review Focus 1): a second customer with no contacts. Confirm returns 200, then `awaitIdle(executor)` and `assertThat(MailSupport.sent(mail)).isEmpty()`.
- `oneFailingAddressDoesNotStopOthers` (Review Focus 3): `doThrow(new MailSendException("down")).when(mail).send(argThat((MimeMessage m) -> to(m).equals(emailA)))`. Confirm, then `awaitIdle`. The captured sends include one to `b`.
- `verifyMailsBalance`: order total 336.00 (3 × 112.00), confirmed. Record a 100 payment as admin and verify it (copy `record`/`verify` from `PaymentsIT`). The `payment-received` body contains `"236.00"`, and the subject is `"Payment received for " + number + " — " + company`.
- `rejectPortalPaymentMailsReason`: insert a pending payment with `submitted_by_type = 'CONTACT'` and `submitted_by_id = contactA`, then reject with reason `"No such transfer <x>"`. The mail body contains `"No such transfer &lt;x&gt;"` (escaped), and the subject is `"Payment for " + number + " was not accepted — " + company`.
- `rejectRepPaymentMailsNobody`: the same with `submitted_by_type = 'USER'`. After `awaitIdle`, the payment-rejected subject is absent from the sent mail. Filter by subject, because the confirm mail from setup is also captured.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew test --no-daemon --tests '*CustomerStatusMailsIT'`
Expected: FAIL (no mails sent; `awaitMail` times out).

- [ ] **Step 3: Implement** `CustomerStatusMails` (three handlers) and the three templates per Interfaces and Global Constraints.

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew test --no-daemon --tests '*CustomerStatusMailsIT' --tests '*SendQuoteIT' --tests '*PaymentsIT' --tests '*ConfirmIT'`
Expected: PASS. `SendQuoteIT` still counts its own mails exactly. If a confirm in its setup now adds mail, adjust that test's expected count, not the feature.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ph/thecoffeejunkie/crm/sales/CustomerStatusMails.java src/main/resources/templates/mail/order-confirmed.html src/main/resources/templates/mail/payment-received.html src/main/resources/templates/mail/payment-rejected.html src/test/java/ph/thecoffeejunkie/crm/sales/CustomerStatusMailsIT.java
git commit -m "feat(mail): order confirmed, payment received and payment rejected emails to customers"
```

---

### Task 4: Dispatched and delivered emails

**Files:**
- Modify: `src/main/java/ph/thecoffeejunkie/crm/sales/CustomerStatusMails.java`
- Create: `src/main/resources/templates/mail/order-dispatched.html`, `order-delivered.html`
- Test: `src/test/java/ph/thecoffeejunkie/crm/sales/CustomerStatusMailsIT.java`

**Interfaces:**
- Consumes: `DeliveryDispatched`, `OrderDelivered` (Task 1); `Fulfilment.mailView(long)` returning `Optional<DeliveryMailView>` (Task 2). Add `Fulfilment` to the `CustomerStatusMails` constructor.
- Handlers: `on(DeliveryDispatched e)` and `on(OrderDelivered e)`. Load the sales order by `salesOrderId` / `orderId` for `customerId`, and `mailView(deliveryOrderId)`. An empty view means log and stop.
- `order-dispatched` vars: `orderNumber`, `number`, `shipTo`, `lines` (the `DeliveryMailView.Line` list; the template iterates it with `th:each`), `link`.
- `order-delivered` vars: `orderNumber`, `number`, `receiverName`, `deliveredAt` (formatted `MMM d, yyyy h:mm a` in Asia/Manila), `lines`, `pickup` (boolean, `method == PICKUP`), `link`. Attachments: `view.photos()`. Use the pickup subject when `pickup`.

Test setup: confirm, set `customer.credit_limit = 10000` so release succeeds unpaid (as `ReleaseCancelIT.creditCustomer`), then `release` as rep. Find the delivery order id with `select id from delivery_order where sales_order_id = ?`. Pick, dispatch and deliver as admin by copying `pick`, `act` and `deliverAll` from `DeliveryOrdersIT`. Filter captured mails by subject, since confirm mails are also sent.

- [ ] **Step 1: Write the failing tests**

- `dispatchMailsOutForDelivery`: after dispatch, one mail per enabled contact with subject `"Order " + number + " is out for delivery — " + company`. The body contains the delivery order number and the product description.
- `refusedDispatchMailsNobody` (Review Focus 5): dispatch before picking returns 422. After `awaitIdle`, there is no "out for delivery" subject.
- `deliverMailsProofPhoto`: deliver with one PNG and receiver `"Juan"`. The subject is `"Order " + number + " delivered — " + company`, the body contains `"Juan"`, and the attachments (copy `attachments()` from `SendQuoteIT`) are `[<DO number>-proof-1.png]`.
- `pickupMailsPickedUp`: before picking, `update delivery_order set method = 'PICKUP' where id = ?`. Pick, then deliver straight from PICKING. The subject is `"Order " + number + " picked up — " + company`.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew test --no-daemon --tests '*CustomerStatusMailsIT'`
Expected: the four new tests FAIL. Task 3's tests still PASS.

- [ ] **Step 3: Implement** the two handlers and templates.

- [ ] **Step 4: Run to verify they pass** with the same command. Expected: PASS.

- [ ] **Step 5: Full suite and graph**

Run: `./gradlew test --no-daemon` → expected BUILD SUCCESSFUL. Then `graphify update .` from `Codes/crm`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ph/thecoffeejunkie/crm/sales/CustomerStatusMails.java src/main/resources/templates/mail/order-dispatched.html src/main/resources/templates/mail/order-delivered.html src/test/java/ph/thecoffeejunkie/crm/sales/CustomerStatusMailsIT.java
git commit -m "feat(mail): out-for-delivery and delivered emails with proof photos"
```

After the last task, mark tracker item O-01 Done.
