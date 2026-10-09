# In-App Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Staff get a stored, live-pushed notification when something they need to act on happens (customer accepts/declines a quote, a payment needs verifying, an invoice is paid, a delivery order is created or delivered, a quote expires).

**Architecture:** Notifications are rows in MySQL (`notification` table, Flyway V5), so nothing is lost when a browser is closed. A service hook in each business flow calls `NotificationService`, which saves the rows and publishes each one to Redis channel `notify:{email}`. Every API instance subscribes to `notify:*` and writes the event to that user's open `SseEmitter`s at `GET /api/v1/notifications/stream`. REST endpoints list, count and mark notifications read. Layered packages as they are today.

**Tech Stack:** Spring Boot 4.0.6, Java 21, Spring MVC `SseEmitter`, Spring Data Redis pub/sub (`RedisMessageListenerContainer`), Spring Data JPA + Flyway (MySQL), Jackson 3 (`tools.jackson.databind.json.JsonMapper`), JUnit 5, Mockito, Testcontainers Redis via `RedisTestSupport`.

**Spec:** User request "add live and working notifications; you decide the events", plus CRM Refactor Tracker item R-08 ("In-app notifications over SSE with Redis pub/sub fan-out", channel `notify:{userId}`) — https://claude.ai/artifact/Jw2SMuVU7AwAyfD6cmbgVi. The frontend is not in this repo; this plan delivers the API the frontend's bell consumes.

## Events (decided)

| Type | Fired in | Recipients | Title | Body |
|---|---|---|---|---|
| `QUOTATION_ACCEPTED` | `QuotationAcceptanceService.accept` (customer link and staff accept) | quotation sales rep, else admins | `Quotation {qNo} accepted` | `{customer} accepted. Invoice {invNo} was created and emailed.` |
| `QUOTATION_REJECTED` | `QuotationEmailService.respondLocked` (customer link) | quotation sales rep, else admins | `Quotation {qNo} declined` | `{customer} declined the quotation.` |
| `QUOTATION_EXPIRED` | `QuotationExpiryService.expireOverdueQuotations`, one per quotation | quotation sales rep, else admins | `Quotation {qNo} expired` | `{customer} did not respond by {expiryDate}.` |
| `PAYMENT_AWAITING_VERIFICATION` | `InvoiceService.uploadProofOfPayment`, and `recordPayment` when status turns `FOR_PAYMENT_VERIFICATION` | all active ADMIN users | `Payment to verify: {invNo}` | `{customer}'s payment for invoice {invNo} is waiting for verification.` |
| `INVOICE_PAID` | `InvoiceService.markPaid` | invoice sales rep, else admins | `Invoice {invNo} paid` | `{customer} paid in full. Delivery order {doNo} was created.` |
| `DELIVERY_ORDER_CREATED` | `InvoiceService.markPaid` (after `createForInvoice`) | all active WAREHOUSE users | `New delivery order {doNo}` | `For invoice {invNo}, {customer}. Prepare it for pickup.` |
| `DELIVERY_ORDER_DELIVERED` | `DeliveryOrderService.markDelivered` | the invoice's sales rep, else admins | `Delivered: {doNo}` | `Invoice {invNo} for {customer} was delivered.` |

`{customer}` = `NotificationService.customerName(Customer)`: `firstName + " " + lastName`, or `"A customer"` when the customer is null. The user who caused the event (current authenticated principal) is never notified about it.

Left out on purpose: low-stock alerts (no reorder level exists), customer assignment, invoice cancel. Add them by adding an enum value + one call.

## Global Constraints

- Layered packages under `ph.thecoffeejunkie.crm` (`entity/`, `constant/`, `repository/`, `dto/response/`, `service/`, `controller/`, `config/`). No Spring Modulith, no new Gradle dependencies.
- Match existing style: Lombok `@RequiredArgsConstructor` + `@Slf4j`, comments explain *why*.
- Redis channel: `notify:{email}`; listener subscribes `PatternTopic("notify:*")`. (Users are keyed by email; `CRMUser` has no numeric id.)
- SSE: event name `notification` (data = `NotificationResponse` JSON, SSE `id` = notification id) and `unread-count` (data = `{"count":N}`) sent once on connect. Emitter timeout 30 minutes; heartbeat comment `ping` every 25 s.
- Repositories use derived query methods only: the test suite has no MySQL, so JPQL would go unverified.
- Notifications must never break the business action: `NotificationService` catches and logs every failure.
- Whole-plan test command (Docker running): `.\gradlew.bat test --tests "ph.thecoffeejunkie.crm.*IT" --tests "ph.thecoffeejunkie.crm.*.*Test"`.

## Review Focus

- **Notification failure during a business action** (DB or Redis down while a customer accepts a quote). The accept/pay/deliver still succeeds; failure is logged at WARN. Test: Task 2 `neverThrows`.
- **Redis down.** Rows still save; publish falls back to this instance's emitters; app still boots with the listener container unable to connect. Tests: Task 1 `publishFallsBackToLocalWhenRedisDown`, `listenerContainerStartsWhenRedisDown`.
- **User acting on their own record** (admin who is also the sales rep marks the invoice paid). No self-notification. Test: Task 2 `skipsTheActingUser`.
- **Marking someone else's notification read** by guessing ids. 404, row untouched. Test: Task 2 `markReadOfOtherUsersNotificationIs404`.
- **Async dispatch of the SSE request under Spring Security** (stateless JWT filter does not run on ASYNC dispatch, so the emitter completing would hit an empty security context and log "response already committed"). ASYNC dispatches are permitted. Test: Task 3 `streamAsyncDispatchIsNotRejected`.

---

### Task 1: Live delivery — `NotificationStreams` + Redis fan-out

**Files:**
- Create: `src/main/java/ph/thecoffeejunkie/crm/constant/NotificationType.java`
- Create: `src/main/java/ph/thecoffeejunkie/crm/dto/response/NotificationResponse.java`
- Create: `src/main/java/ph/thecoffeejunkie/crm/service/NotificationStreams.java`
- Create: `src/main/java/ph/thecoffeejunkie/crm/config/RedisListenerConfig.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/service/NotificationStreamsIT.java`

**Interfaces:**
- Consumes: `RedisTestSupport.template()`, `deadTemplate()`, `connectionFactory()`, `deadConnectionFactory()`.
- Produces: `enum NotificationType { QUOTATION_ACCEPTED, QUOTATION_REJECTED, QUOTATION_EXPIRED, PAYMENT_AWAITING_VERIFICATION, INVOICE_PAID, DELIVERY_ORDER_CREATED, DELIVERY_ORDER_DELIVERED }`.
- Produces: `record NotificationResponse(Long id, NotificationType type, Long entityId, String title, String body, boolean read, LocalDateTime createdAt)`. `entityId` is the quotation / invoice / delivery order id implied by `type`; the frontend routes on it.
- Produces: `@Component NotificationStreams implements MessageListener`, ctor `(StringRedisTemplate, JsonMapper)`:
  - `SseEmitter open(String email, long unreadCount)`
  - `void publish(String email, NotificationResponse notification)` — never throws
  - `void onMessage(Message message, byte[] pattern)`
  - `@Scheduled(fixedRate = 25_000) void heartbeat()`
- Produces: `RedisListenerConfig.notificationListenerContainer(RedisConnectionFactory, NotificationStreams): RedisMessageListenerContainer` (`@Bean`).

- [ ] **Step 1: Write the failing test**

`NotificationStreamsIT` uses a nested `@RestController` (same pattern as `IdempotencyFilterIT`) mapping `GET /stream?email=…&unread=…` to `streams.open(email, unread)`, with `MockMvcBuilders.standaloneSetup`. Build the container with `new RedisListenerConfig().notificationListenerContainer(RedisTestSupport.connectionFactory(), streams)`, then `afterPropertiesSet()` + `start()`; stop it in `@AfterEach`. Use a unique email per test (`UUID + "@t.ph"`). Poll `getResponse().getContentAsString()` up to 3 s for async assertions.

- `openSendsUnreadCountFirst`: open with unread 3 → content starts with `event:unread-count\ndata:{"count":3}`.
- `publishReachesSubscriberThroughRedis`: open streams for `a` and `b`; `publish(a, new NotificationResponse(7L, INVOICE_PAID, 9L, "Invoice INV-1 paid", "x", false, now))` → `a`'s content contains `event:notification`, `id:7` and `"title":"Invoice INV-1 paid"`; after 1 s, `b`'s content does not contain `INV-1`.
- `publishFallsBackToLocalWhenRedisDown`: streams built on `deadTemplate()`, no container → `publish` does not throw and the open stream's content contains the title.
- `listenerContainerStartsWhenRedisDown`: `assertTimeoutPreemptively(Duration.ofSeconds(10), …)` building + `afterPropertiesSet()` + `start()` a container on `deadConnectionFactory()` → no exception.

- [ ] **Step 2: Run, expect FAIL**

Run: `.\gradlew.bat test --tests "ph.thecoffeejunkie.crm.service.NotificationStreamsIT"`
Expected: compilation failure — `NotificationStreams` not defined.

- [ ] **Step 3: Implement**

- `NotificationStreams`: `ConcurrentHashMap<String, Set<SseEmitter>>` (sets from `ConcurrentHashMap.newKeySet()`). `open` creates `new SseEmitter(Duration.ofMinutes(30).toMillis())`, registers removal on completion/timeout/error, sends `unread-count`, returns it. `publish` serialises with `JsonMapper`, `convertAndSend("notify:" + email, json)`; on `RuntimeException` logs WARN and delivers to local emitters directly (single-instance still works without Redis). `onMessage` takes the email from the channel after `notify:` and delivers the body (UTF-8). Delivery: `SseEmitter.event().id(…).name("notification").data(json, MediaType.APPLICATION_JSON)`; the SSE id comes from `JsonMapper.readTree(json).get("id")`, since the Redis message carries only the JSON. Any `IOException`/`IllegalStateException` on send removes that emitter. `heartbeat` sends `SseEmitter.event().comment("ping")` to every emitter with the same removal rule.
- `RedisListenerConfig`: container on the factory, `addMessageListener(streams, new PatternTopic("notify:*"))`. If Step 4's `listenerContainerStartsWhenRedisDown` fails, set `container.setRecoveryBackoff(new FixedBackOff(5000, FixedBackOff.UNLIMITED_ATTEMPTS))` so it retries in the background instead of failing startup.

- [ ] **Step 4: Run, expect PASS**

Run: same as Step 2. Expected: 4/4.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ph/thecoffeejunkie/crm/constant/NotificationType.java src/main/java/ph/thecoffeejunkie/crm/dto/response/NotificationResponse.java src/main/java/ph/thecoffeejunkie/crm/service/NotificationStreams.java src/main/java/ph/thecoffeejunkie/crm/config/RedisListenerConfig.java src/test/java/ph/thecoffeejunkie/crm/service/NotificationStreamsIT.java
git commit -m "feat(notifications): SSE streams with Redis pub/sub fan-out"
```

---

### Task 2: Stored notifications — entity, migration, `NotificationService`

**Files:**
- Create: `src/main/resources/db/migration/V5__notifications.sql`
- Create: `src/main/java/ph/thecoffeejunkie/crm/entity/Notification.java`
- Create: `src/main/java/ph/thecoffeejunkie/crm/repository/NotificationRepository.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/repository/CRMUserRepository.java`
- Create: `src/main/java/ph/thecoffeejunkie/crm/service/NotificationService.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/service/NotificationServiceTest.java`

**Interfaces:**
- Consumes: `NotificationStreams.publish(String, NotificationResponse)`, `NotificationType`, `NotificationResponse` (Task 1).
- Produces: `Notification extends BaseEntity` — `recipientEmail` (String), `type` (`@Enumerated(STRING) NotificationType`), `entityId` (Long), `title`, `body` (String), `readAt` (LocalDateTime, null = unread).
- Produces: `NotificationRepository extends JpaRepository<Notification, Long>`: `Page<Notification> findByRecipientEmailOrderByIdDesc(String, Pageable)`, `long countByRecipientEmailAndReadAtIsNull(String)`, `Optional<Notification> findByIdAndRecipientEmail(Long, String)`, `List<Notification> findByRecipientEmailAndReadAtIsNull(String)`.
- Produces: `CRMUserRepository.findByActiveTrueAndRolesContaining(String role): List<CRMUser>` (roles is a comma-separated string; `ADMIN`/`SALES`/`WAREHOUSE` never contain each other).
- Produces: `NotificationService(NotificationRepository, CRMUserRepository, NotificationStreams)`:
  - `void toOwner(CRMUser ownerOrNull, NotificationType type, Long entityId, String title, String body)` — owner, or all active ADMINs when null
  - `void toRole(Role role, NotificationType type, Long entityId, String title, String body)`
  - `static String customerName(Customer customer)`
  - `PageResponse<NotificationResponse> list(String email, PageRequest pageRequest)`
  - `long unreadCount(String email)`
  - `void markRead(String email, Long id)` — `ResourceNotFoundException.of("Notification", id)` when not found for that email
  - `void markAllRead(String email)`

- [ ] **Step 1: Write the failing test**

`NotificationServiceTest` (Mockito mocks for both repositories and `NotificationStreams`; `saveAll` answers by assigning ids 1, 2, … and returning its argument; clear `SecurityContextHolder` in `@AfterEach`):

- `toOwnerSavesOneRowAndPublishes`: owner `rep@x.ph` → one saved `Notification` with that recipient, type `INVOICE_PAID`, entityId 9, given title/body, `readAt` null; `streams.publish("rep@x.ph", response)` with `id` 1 and `read` false.
- `toOwnerFallsBackToAdminsWhenOwnerNull`: `findByActiveTrueAndRolesContaining("ADMIN")` returns `a1`, `a2` → two rows, two publishes.
- `toRoleNotifiesActiveUsersInRole`: `toRole(Role.WAREHOUSE, …)` queries `"WAREHOUSE"` and saves one row per user.
- `skipsTheActingUser`: authentication name `rep@x.ph` in `SecurityContextHolder`; `toOwner(rep, …)` → no `saveAll`, no `publish`.
- `neverThrows`: `saveAll` throws `RuntimeException` → `toOwner` returns normally; `streams` untouched.
- `markReadOfOtherUsersNotificationIs404`: `findByIdAndRecipientEmail(5L, "me@x.ph")` empty → `ResourceNotFoundException`; `save` never called.
- `markAllReadStampsOnlyUnread`: two unread rows → both get non-null `readAt` and are saved.
- `customerNameHandlesNull`: `customerName(null)` = `"A customer"`; `"Ana"`/`"Cruz"` → `"Ana Cruz"`.

- [ ] **Step 2: Run, expect FAIL**

Run: `.\gradlew.bat test --tests "ph.thecoffeejunkie.crm.service.NotificationServiceTest"`
Expected: compilation failure — `NotificationService` not defined.

- [ ] **Step 3: Implement**

- `V5__notifications.sql`, following V1 conventions:

```sql
-- In-app notifications for staff. Rows outlive the live SSE push, so a user who was offline
-- still sees what happened; read_at NULL = unread.
CREATE TABLE `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `recipient_email` varchar(255) NOT NULL,
  `type` enum('QUOTATION_ACCEPTED','QUOTATION_REJECTED','QUOTATION_EXPIRED','PAYMENT_AWAITING_VERIFICATION','INVOICE_PAID','DELIVERY_ORDER_CREATED','DELIVERY_ORDER_DELIVERED') NOT NULL,
  `entity_id` bigint DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `body` varchar(1000) DEFAULT NULL,
  `read_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notification_recipient_read` (`recipient_email`, `read_at`),
  CONSTRAINT `fk_notification_recipient` FOREIGN KEY (`recipient_email`) REFERENCES `users` (`email`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

- `NotificationService.toOwner/toRole` → one private `send(Collection<String> emails, …)`: drop the current principal's name, dedupe, return if empty; build rows, `saveAll`, then `publish` each mapped response. Whole body in `try { … } catch (RuntimeException e) { log.warn(…) }`. `list` maps to `PageResponse` like `InvoiceService.findAll`.

- [ ] **Step 4: Run, expect PASS**

Run: same as Step 2. Expected: 8/8.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V5__notifications.sql src/main/java/ph/thecoffeejunkie/crm/entity/Notification.java src/main/java/ph/thecoffeejunkie/crm/repository src/main/java/ph/thecoffeejunkie/crm/service/NotificationService.java src/test/java/ph/thecoffeejunkie/crm/service/NotificationServiceTest.java
git commit -m "feat(notifications): stored notifications with owner/role recipients"
```

---

### Task 3: REST + SSE endpoints

**Files:**
- Create: `src/main/java/ph/thecoffeejunkie/crm/controller/NotificationController.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/config/SecurityConfig.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/controller/NotificationControllerTest.java`

**Interfaces:**
- Consumes: `NotificationService.list/unreadCount/markRead/markAllRead` (Task 2), `NotificationStreams.open` (Task 1).
- Produces, all under `/api/v1/notifications`, any authenticated role, current user = `Authentication.getName()`:
  - `GET ?pageNumber=1&pageSize=20` → `PageResponse<NotificationResponse>` (1-based page, like `InvoiceController.getAll`)
  - `GET /unread-count` → `{"count": N}`
  - `POST /{id}/read` → 204
  - `POST /read-all` → 204
  - `GET /stream` (`produces = text/event-stream`) → `streams.open(email, service.unreadCount(email))`

The browser's `EventSource` cannot send an `Authorization` header; it authenticates with the existing `jwt` cookie (`new EventSource(url, { withCredentials: true })`), which `JwtUtil.extractTokenFromRequest` already reads and `CorsConfig` already allows.

- [ ] **Step 1: Write the failing test**

`NotificationControllerTest`: `@WebMvcTest(NotificationController.class)` + `@Import({SecurityConfig.class, GlobalExceptionHandler.class})`, same `@MockitoBean` set as `BasicAuthDisabledTest` plus `NotificationService` and `NotificationStreams`; requests use `with(user("me@x.ph"))`.

- `unreadCountReturnsCount`: service returns 2 → `{"count":2}`.
- `markReadOfOtherUsersNotificationIs404`: `markRead("me@x.ph", 5L)` throws `ResourceNotFoundException.of("Notification", 5L)` → 404.
- `streamOpensForCurrentUser`: `streams.open("me@x.ph", 4)` returns an `SseEmitter`; service unread 4 → `request().asyncStarted()`, `streams.open` verified with those args.
- `streamAsyncDispatchIsNotRejected`: the emitter returned by the mock is `complete()`d after the first perform; `mvc.perform(asyncDispatch(result))` → status 200. (MockMvc may carry the test user into the async dispatch and pass before the fix; it pins the rule either way.)
- `unauthenticatedStreamIs401`: no user → 401.

- [ ] **Step 2: Run, expect FAIL**

Run: `.\gradlew.bat test --tests "ph.thecoffeejunkie.crm.controller.NotificationControllerTest"`
Expected: compilation failure — `NotificationController` not defined.

- [ ] **Step 3: Implement**

- Controller as specified above.
- `SecurityConfig`: first rule in `authorizeHttpRequests` is `.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()`, with a comment: the original REQUEST dispatch was already authorized; the stateless `JWTFilter` does not run on ASYNC dispatch, so without this an SSE stream ending logs an access-denied on a committed response.

- [ ] **Step 4: Run, expect PASS**

Run: same as Step 2, then `BasicAuthDisabledTest` still passes. Expected: 5/5 and 1/1.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ph/thecoffeejunkie/crm/controller/NotificationController.java src/main/java/ph/thecoffeejunkie/crm/config/SecurityConfig.java src/test/java/ph/thecoffeejunkie/crm/controller/NotificationControllerTest.java
git commit -m "feat(notifications): list, read and SSE stream endpoints"
```

---

### Task 4: Fire the events

**Files:**
- Modify: `src/main/java/ph/thecoffeejunkie/crm/service/QuotationAcceptanceService.java` (`accept`)
- Modify: `src/main/java/ph/thecoffeejunkie/crm/service/QuotationEmailService.java` (`respondLocked`, reject branch)
- Modify: `src/main/java/ph/thecoffeejunkie/crm/service/QuotationExpiryService.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/service/InvoiceService.java` (`uploadProofOfPayment`, `recordPayment`, `markPaid`)
- Modify: `src/main/java/ph/thecoffeejunkie/crm/service/DeliveryOrderService.java` (`markDelivered`)
- Modify: `src/test/java/ph/thecoffeejunkie/crm/service/QuotationRespondLockTest.java`, `QuotationExpiryServiceTest.java` (new constructor arg)
- Test: `src/test/java/ph/thecoffeejunkie/crm/service/NotificationEventsTest.java`

**Interfaces:**
- Consumes: `NotificationService.toOwner`, `toRole`, `customerName` (Task 2); `Role.ADMIN`, `Role.WAREHOUSE`.
- Produces: nothing new; each service gets `NotificationService` as a constructor dependency. Titles/bodies are exactly those in the Events table.

Call each hook after the state change is saved, so a failed save never notifies. `InvoiceService` → `NotificationService` → … has no cycle (`NotificationService` depends only on repositories and `NotificationStreams`).

- [ ] **Step 1: Write the failing test**

`NotificationEventsTest`, plain Mockito, one test per event, each building the service under test with mocks and verifying the exact `NotificationService` call:

- `acceptNotifiesSalesRep`: quotation `Q-1` with rep, customer Ana Cruz; invoice email returns `INV-1` → `toOwner(rep, QUOTATION_ACCEPTED, quotationId, "Quotation Q-1 accepted", "Ana Cruz accepted. Invoice INV-1 was created and emailed.")`.
- `customerRejectNotifiesSalesRep`: `respond(id, validToken, "REJECT")` → `toOwner(rep, QUOTATION_REJECTED, id, "Quotation Q-1 declined", "Ana Cruz declined the quotation.")`.
- `expiryNotifiesEachRep`: two overdue quotations → two `toOwner(…, QUOTATION_EXPIRED, …)` calls; body ends `did not respond by 2026-10-01.` for expiryDate 2026-10-01.
- `proofUploadNotifiesAdmins`: `toRole(Role.ADMIN, PAYMENT_AWAITING_VERIFICATION, invoiceId, "Payment to verify: INV-1", "Ana Cruz's payment for invoice INV-1 is waiting for verification.")`.
- `fullPaymentRecordedNotifiesAdmins` / `partialPaymentDoesNotNotify`: `recordPayment` covering the balance → same `toRole` call; partial → `verifyNoInteractions(notificationService)`.
- `markPaidNotifiesRepAndWarehouse`: `createForInvoice` returns DO `DO-1` → `toOwner(rep, INVOICE_PAID, invoiceId, "Invoice INV-1 paid", "Ana Cruz paid in full. Delivery order DO-1 was created.")` and `toRole(Role.WAREHOUSE, DELIVERY_ORDER_CREATED, doId, "New delivery order DO-1", "For invoice INV-1, Ana Cruz. Prepare it for pickup.")`.
- `deliveredNotifiesRep`: `toOwner(rep, DELIVERY_ORDER_DELIVERED, doId, "Delivered: DO-1", "Invoice INV-1 for Ana Cruz was delivered.")`.

- [ ] **Step 2: Run, expect FAIL**

Run: `.\gradlew.bat test --tests "ph.thecoffeejunkie.crm.service.NotificationEventsTest"`
Expected: compilation failure — constructors don't take `NotificationService`.

- [ ] **Step 3: Implement** the seven calls per the Events table; update the two existing tests' constructor calls with `mock(NotificationService.class)`.

- [ ] **Step 4: Run the whole suite, expect PASS**

Run: `.\gradlew.bat test --tests "ph.thecoffeejunkie.crm.*IT" --tests "ph.thecoffeejunkie.crm.*.*Test"`
Expected: BUILD SUCCESSFUL, no failures.

- [ ] **Step 5: Verify live against the running stack**

Start the dev stack (`docker compose up -d`, then the app with the dev profile). Flyway logs `Migrating schema ... to version "5 - notifications"`. Log in as an ADMIN in one terminal and keep `curl -N -b "jwt=<token>" http://localhost:8080/api/v1/notifications/stream` open; it prints `event:unread-count` at once and `:ping` every 25 s. As a different user, upload proof of payment for an UNPAID invoice through its portal link → the curl prints `event:notification` with `"type":"PAYMENT_AWAITING_VERIFICATION"` within a second; `GET /api/v1/notifications/unread-count` goes up by one; `POST /api/v1/notifications/read-all` brings it to 0.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ph/thecoffeejunkie/crm/service src/test/java/ph/thecoffeejunkie/crm/service
git commit -m "feat(notifications): notify on quote, payment and delivery events"
```
