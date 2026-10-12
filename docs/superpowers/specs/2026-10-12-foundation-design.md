# Redesign #1 — Foundation

Date: 2026-10-12
Repos: `crm` (backend), `crm-dashboard-ui` (frontend)
Branch: `phase/major_refactor`

## Context

The app grew feature by feature without an upfront design. This is the first of five sub-projects that rebuild it
on the same stack (Spring Boot 4 / Java 21, Redis, MinIO, React 19 + TailAdmin + Tailwind v4) as a modular
monolith, with a new schema on **PostgreSQL** (replacing MySQL) and a one-time data migration at cutover.

### What the owner asked for

- Fix four pains: a clunky sales flow, unreliable inventory, an inconsistent UI, and weak security and access control.
- A one-time migration into a new schema is acceptable. The app is in limited use.
- B2B orders: the quote is optional, payments are split or on credit terms, goods ship before or after payment, orders change
  after acceptance, each order ships once, and stock comes from multiple warehouses.
- 5–20 users in distinct roles. Sales staff also run the POS. Warehouse and delivery staff use phones (Android app) and
  scan QR codes on delivery orders, products and bins, and transfers.
- Security: an audit trail, approval rules, 2FA and session management, and data scoping by rep or warehouse.
- Customers can log in to a portal and see their previous orders.
- Postgres instead of MySQL. Quantities can be fractional (e.g. 2.5 kg).
- UI stays on Tailwind + TailAdmin, organised by job, with one Order workspace.

### Roadmap (each sub-project gets its own spec → plan → build)

1. **Foundation** (this spec): module layout, Postgres baseline, identity and sessions, authorization and scoping,
   approvals, audit, UI shell and component kit, conventions, and testing. It includes customers and warehouses as the
   first scoped vertical slice.
2. **Sales lifecycle**: Order aggregate (optional quote stage, revisions, approvals, credit terms, payments), plus the
   customer portal screens.
3. **Inventory & fulfilment**: multi-warehouse stock, reservations, transfers, delivery orders, and QR scanning.
4. **POS & dashboards**, re-homed onto the new model.
5. **Data migration & cutover**: MySQL → Postgres ETL with reconciliation.

## Decisions

| Topic | Decision |
|---|---|
| Strategy | Rebuild on the same foundation as a modular monolith. Carry over proven infrastructure code; rewrite the domain code |
| Database | PostgreSQL. Fresh Flyway `V1__baseline.sql`. Testcontainers Postgres in tests |
| Sessions | Spring Session on Redis. Opaque session id replaces the JWT |
| Web auth transport | `HttpOnly; Secure; SameSite=Lax` cookie, CSRF on (cookie-to-header). SPA and API are same-site |
| Android auth transport | The same session id in an `X-Auth-Token` header, stored in device secure storage |
| Accounts | Invite-only. Public registration removed |
| 2FA | TOTP, required for `ADMIN`, optional for others. 10 recovery codes |
| Roles | `ADMIN`, `SALES`, `WAREHOUSE`. One role per user. No permission matrix |
| Scoping | Enforced in queries. SALES → assigned customers. WAREHOUSE → assigned warehouses. Out of scope → 404 |
| Approvals | One generic `approval_request`, tied to a subject version. Thresholds in admin settings |
| Audit | Append-only `audit_event`, written in the same transaction. The app DB role cannot update or delete it |
| Portal identity | Customer contacts log in by email magic link. Each contact sees only their company's records |
| Errors | RFC 9457 `ProblemDetail` with a stable `code` |
| Frontend | Feature folders, generated OpenAPI types, `src/ui` kit, react-hook-form + zod |
| Navigation | Job-based sidebar (Sell / Fulfil / Insights / Admin), an Inbox, Ctrl+K search. Mobile bottom-tab shell with Scan |

## Out of scope for Foundation

The order, quote, invoice, payment, stock, delivery, transfer and POS domains (sub-projects 2–4). Data migration
(sub-project 5). Fine-grained permissions. Hash-chained audit. A metrics/tracing stack. Multi-tenant or branch
separation. SSO.

---

## 1. Backend module layout

Package by feature under `ph.thecoffeejunkie.crm.<module>`:

| Module | Foundation contents |
|---|---|
| `shared` | ProblemDetail handling, pagination, money/quantity types, document-number sequences, `CurrentUser`/`Scope`, after-commit event helpers |
| `identity` | Users, invites, password, TOTP, sessions, login rate limit and lockout, portal magic links |
| `audit` | `AuditLog.record(...)`, activity-log query API |
| `approvals` | `approval_request`, the settings that trigger approvals, decision API |
| `settings` | `app_setting` key/value store, admin settings API |
| `customers` | Customers, contacts, rep assignment (the first scoped slice) |
| `warehouses` | Warehouse master data and user↔warehouse assignment (stock itself comes in #3) |
| `notifications` | Carried over: stored notifications and SSE with Redis fan-out |
| `files` | Carried over: MinIO storage and presigned URLs |

Later sub-projects add `catalog`, `sales`, `inventory`, `fulfilment`, `pos` and `portal`.

Rules:
- Each module exposes **one public service facade plus its DTOs**. Entities, repositories and helpers are package-private.
- Cross-module side effects use Spring application events, handled `@TransactionalEventListener(AFTER_COMMIT)`.
- A Spring Modulith `ApplicationModules.of(CrmApplication.class).verify()` test fails the build on cycles or
  reach-ins. Modulith is a **test-scope** dependency only.

**Carried over** (moved into modules, behaviour unchanged): `RateLimiter`/`RateLimitFilter`, `IdempotencyFilter`,
`DistributedLock`, `StorageService` (MinIO), `NotificationService`/`NotificationStreams`, Thymeleaf→PDF rendering,
SMTP timeout config, `RequestLoggingInterceptor` (folded into the MDC filter).

**Removed:** `JwtUtil`, `JWTFilter`, `TokenRevocationService`, `RegistrationService`, `/auth/register`,
`/auth/check-token`, and the jjwt dependencies.

## 2. Data conventions (Postgres)

- IDs are `bigint generated always as identity`. Documents also get a human number from a Postgres sequence, formatted
  `<PREFIX>-<YY>-<5-digit seq>` (e.g. `SO-26-00042`). The sequence never resets, so the year is display only.
- Money is `numeric(19,2)`, PHP only. Quantity is `numeric(12,3)`. Java uses `BigDecimal` for both, with explicit `RoundingMode.HALF_UP`.
- Time is `timestamptz`, stored in UTC. The UI displays Asia/Manila.
- Every aggregate root has `created_at`, `updated_at` and `version bigint` (JPA `@Version`). A conflict → 409 `STALE_VERSION`.
- Enums are `text` + `CHECK (col in (...))`, mapped with `@Enumerated(STRING)`.
- Master data (users, customers, warehouses, later products) is archived via `archived_at timestamptz null`, never
  deleted. Transactions are never deleted, only cancelled or voided with a reason.
- Uniqueness that ignores archived rows uses partial unique indexes, e.g. `unique (lower(email)) where archived_at is null`.
- **Two DB roles:** `crm_owner` runs Flyway and owns the schema. `crm_app` is the runtime user, with DML on all tables
  except `audit_event`, where it has `INSERT, SELECT` only. Flyway grants these in the baseline.

## 3. Identity & sessions

### Tables

- `app_user(id, email, password_hash, first_name, last_name, phone, role, totp_secret_enc null,
  totp_enabled_at null, totp_last_step bigint null, failed_logins int, locked_until null, archived_at null, created_at, updated_at, version)`
- `user_warehouse(user_id, warehouse_id)` assigns warehouses to WAREHOUSE users.
- `recovery_code(id, user_id, code_hash, used_at null)`
- `one_time_token(id, purpose, subject_type, subject_id, token_hash, expires_at, used_at null, created_by null)`.
  `purpose` is one of `INVITE`, `PASSWORD_RESET`, `PORTAL_LOGIN`, `QUOTE_RESPONSE`, `PAYMENT_PROOF`.
  Only the SHA-256 of the token is stored. `INVITE`, `PASSWORD_RESET` and `PORTAL_LOGIN` are single-use
  (`used_at` is set on redeem). `QUOTE_RESPONSE` and `PAYMENT_PROOF` stay valid until their action completes or they expire;
  #2 defines their lifetimes.
- Spring Session tables live in Redis, not Postgres.

The TOTP secret is encrypted at rest with the existing `EncryptionService` (AES-GCM, key from env).

### Flows

- **Invite.** An ADMIN creates a user (email, name, role, warehouses) → an `INVITE` token is emailed (valid 24h) →
  the user sets a password (minimum 12 characters, rejected if it equals the email) → ADMINs go straight to TOTP enrolment.
- **Login.** `POST /api/auth/login {email, password}`.
  - Wrong credentials → 401 generic (no user enumeration). `failed_logins++`, and after 5 failures `locked_until = now + 15 min`.
    The existing Redis IP rate limit stays in front.
  - With 2FA enabled: the session is created holding only `PRE_2FA`, and the response is `{ next: "TOTP" }`. Then
    `POST /api/auth/totp {code}` or `{recoveryCode}` upgrades the session to the full role authority, and the
    session id is rotated.
  - An ADMIN without 2FA gets the session holding only `ENROL_2FA`, and the response is `{ next: "ENROL_TOTP" }`.
  - On success the session id is rotated (session fixation protection) and `failed_logins = 0`.
- **TOTP:** RFC 6238, SHA-1, 6 digits, 30s step, ±1 step tolerance. Implemented with `javax.crypto.Mac`, no
  dependency. A code can't be reused: an accepted code's time step must be greater than `app_user.totp_last_step`, which is then updated.
  Enrolment returns an `otpauth://` URI and a backend-rendered QR SVG (zxing). Enabling it requires one valid
  code, and then returns 10 recovery codes once (stored hashed).
- **Sessions:** 2h idle and 12h absolute. The absolute limit is checked against a `createdAt` session attribute,
  because Spring Session only does idle timeout natively. Indexed by principal name
  (`FindByIndexNameSessionRepository`), the endpoints are:
  - `GET /api/me/sessions`, `DELETE /api/me/sessions/{id}`, `DELETE /api/me/sessions` (all others)
  - `GET/DELETE /api/admin/users/{id}/sessions[/{sid}]` for ADMIN
  - Archiving a user, a role change, a password change and a 2FA reset all invalidate that user's sessions.
  - Session attributes for the device list: user agent, client type (`WEB`/`ANDROID`), IP, created, last access.
- **Transport:** `CookieHttpSessionIdResolver` and `HeaderHttpSessionIdResolver("X-Auth-Token")`, combined in one
  resolver that checks the header first. CSRF (`CookieCsrfTokenRepository.withHttpOnlyFalse()` + the SPA request
  handler) applies to cookie-authenticated requests only. Header-authenticated requests are not CSRF-exposed. The Android app is
  identified at login by `clientType: "ANDROID"`, and its response body carries the session id for secure storage.
- **Password reset:** "Forgot password" emails a `PASSWORD_RESET` token (valid 1h). Completing a reset kills all of that
  user's sessions. The response is identical whether or not the email exists.

### Portal identity

- `customer_contact` (owned by `customers`) is the portal principal. `POST /api/portal/auth/request-link {email}`
  emails a `PORTAL_LOGIN` token (valid 15 min) if the email matches an active contact. The response is always 202.
- `POST /api/portal/auth/redeem {token}` creates a session with authority `CUSTOMER`, principal
  `contact:<id>`, 7-day idle timeout, and the `customerId` stored on the session.
- All `/api/portal/**` endpoints require `CUSTOMER` and read only rows where `customer_id = session.customerId`.
  `CUSTOMER` sessions are rejected on every non-portal endpoint, and staff sessions on portal endpoints.
- Single-action email links (`QUOTE_RESPONSE`, `PAYMENT_PROOF`) work without a session. Their endpoints are
  defined in #2.

### Settings

`app_setting(key text primary key, value jsonb, updated_by, updated_at)`. Defaults are seeded by the baseline
migration. Read through `Settings.get(key, type)` with a 60s Redis cache that is evicted on write. ADMIN edits are audited.

## 4. Authorization & scoping

### Roles

| Role | Access |
|---|---|
| `ADMIN` | Everything. Decides approvals. Manages users, settings and assignments |
| `SALES` | Assigned customers and their orders. POS. Read-only catalog and stock availability |
| `WAREHOUSE` | Stock, delivery orders and transfers for assigned warehouses. Read-only catalog |

Endpoint rules are declared with `@PreAuthorize` on controller methods. Each module keeps its expressions in one
`<Module>Permissions` class referenced by name (e.g. `@PreAuthorize("@customerPermissions.canEdit()")`), so the
rules for a module are readable in one file.

### Scoping

- `Scope` is a request-scoped bean built from the session: `{ userId, role, warehouseIds }`.
- Each scoped module's repository is accessed only through methods that take a `Scope` and apply a JPA
  `Specification`. For example, `CustomerScopes.visibleTo(scope)` → `assigned_rep_id = :userId` for SALES, and no
  restriction for ADMIN. Scoped repositories do not expose `findById` to the facade; `findVisible(id, scope)` does.
- A record that exists but is out of scope → `404 NOT_FOUND`, the same as a missing one.
- **POS customer lookup exception:** `GET /api/customers/lookup?q=` is open to SALES across all customers and
  returns only `id, displayName, phone, email`. A POS sale credits the rep who rang it up, without reassigning the
  customer or widening the rep's scope.
- Customers quick-added from POS go to the rep who added them by default. The setting `pos.quickAddAssignment` (`SELF` | `UNASSIGNED`)
  changes this. Unassigned customers are visible to ADMIN only (and through the lookup above).
- Reassigning a customer is ADMIN-only and audited.

## 5. Approvals

- `approval_request(id, type, subject_type, subject_id, subject_version, requested_value jsonb, reason,
  status, requested_by, requested_at, decided_by null, decided_at null, decision_note null)`.
  `status` is one of `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`.
- **Types and settings** (the checks live in the sub-project that owns the action; Foundation provides the mechanism):

| Type | Trigger setting | Default |
|---|---|---|
| `DISCOUNT` | `approval.discountPercentMax` | 15 |
| `BELOW_COST` | Unit price < product cost | always |
| `CREDIT_LIMIT` | Order would exceed the customer's credit limit | always |
| `STOCK_WRITE_OFF` | `approval.writeOffQtyMax` | 10 |

- **Flow:** the domain check fails → 422 `APPROVAL_REQUIRED` with `{type, subjectType, subjectId, subjectVersion,
  requestedValue}` → the UI offers "Request approval" (reason required) → `POST /api/approvals` → ADMINs get an
  SSE notification and an Inbox item → `POST /api/approvals/{id}/decision {approve|reject, note}`.
- `Approvals.isApproved(type, subjectType, subjectId, subjectVersion)` is what domains call. If the subject changes, its
  version bumps and the approval no longer matches. No stale approvals.
- ADMINs never need approval for their own actions, but those actions are still audited with the reason
  "self-approved (admin)".
- A requester can cancel their pending request.

## 6. Audit

- `audit_event(id, at, actor_type, actor_id, action, entity_type, entity_id, summary, changes jsonb, ip,
  request_id)`. `actor_type` is one of `USER`, `CONTACT`, `SYSTEM`. `changes` is `{field: [old, new]}`.
- Written via `AuditLog.record(action, entity, summary, changes)` from the service layer in the **same
  transaction** as the change. If the business change rolls back, so does its audit row.
- Recorded:
  - CRUD on all business entities.
  - Security events: login success/failure, lockout, 2FA enrol/reset, recovery code used, session revoked,
    password reset, invite sent/accepted, role or assignment change, approval requested/decided.
  - Exports downloaded.
- Login failures for unknown emails are recorded with `actor_type = SYSTEM`, and the attempted email goes in `summary`.
- Indexes on `(entity_type, entity_id, at desc)`, `(actor_type, actor_id, at desc)` and `(at desc)`.
- **API:** `GET /api/admin/activity?actor=&entityType=&action=&from=&to=` (ADMIN), and
  `GET /api/<module>/{id}/history`, returned from each module facade under that module's scope rules.
- Retention: keep everything.

## 7. Customers & warehouses (first vertical slice)

They're included so that scoping, audit, portal identity and the UI kit are proven end to end before #2.

- `customer(id, type BUSINESS|INDIVIDUAL, display_name, business_name null, tin null, address, phone, email,
  credit_limit numeric(19,2) null, payment_terms, assigned_rep_id null, source, notes, archived_at, …)`
- `customer_contact(id, customer_id, name, email, phone, portal_enabled bool, archived_at, …)`, with a
  unique active email across contacts.
- `warehouse(id, code, name, address, is_default, archived_at, …)`, with a partial unique index ensuring exactly one default.
- Screens: Customers list (DataTable), the customer page (Details · Contacts · History tabs; Orders and Invoices tabs come in #2),
  create/edit drawer, Warehouses admin list, and warehouse assignment on the user form.

## 8. Errors & observability

- `ProblemDetail` everywhere through one `@RestControllerAdvice`. The fields are `type`, `title`, `status`, `detail`, `code`,
  `requestId`, and `errors[]` for field validation.
- Foundation codes: `VALIDATION_FAILED`, `NOT_FOUND`, `STALE_VERSION`, `APPROVAL_REQUIRED`, `RATE_LIMITED`,
  `ACCOUNT_LOCKED`, `TOTP_REQUIRED`, `TOTP_INVALID`, `TOKEN_EXPIRED`, `FORBIDDEN`. Sub-projects add their own.
- 500s return `requestId` only. The stack trace is logged.
- Side effects (email, SSE) run after commit. A failed email is recorded on the record (e.g. invite
  `emailStatus = FAILED`), and the UI shows "Not sent – Retry". It never rolls back the business change.
- The Redis client keeps short timeouts. If Redis is down, rate limiting and caching fail open, but sessions fail closed (no login).
- JSON logs (Logback encoder that ships with Boot) with MDC `requestId` and `userId`. The `X-Request-Id` header is echoed back.
- Actuator exposes `health` and `info` only, on the management port, and is not routed publicly.

## 9. Frontend (`crm-dashboard-ui`)

### Structure

```
src/app/        router, shell (sidebar, header, mobile tabs), auth, query client, error mapper
src/ui/         component kit
src/features/   identity, admin (users, settings, activity), approvals, customers, warehouses
src/api/        generated OpenAPI types (openapi-typescript) + one axios instance
```

- **Types:** backend `springdoc-openapi` → `npm run gen:api` writes `src/api/schema.d.ts`. CI regenerates the file and
  fails if it differs from the committed copy.
- **Axios:** `withCredentials`, `xsrfCookieName: "XSRF-TOKEN"`, `xsrfHeaderName: "X-XSRF-TOKEN"`. On Android
  (`Capacitor.isNativePlatform()`) it sends `X-Auth-Token` from `@capacitor/preferences` instead (backed by
  Android encrypted storage). A 401 → go to `/signin?next=…`.
- **Auth state:** `GET /api/me` is the only source of truth. The sessionStorage user snapshot is removed.

### Shell

- **Desktop:** the TailAdmin sidebar, grouped as
  - **Inbox** (badge: pending approvals for ADMIN; later, payments to verify)
  - **Sell:** Orders\*, Customers, POS\*
  - **Fulfil:** Deliveries\*, Stock\*, Transfers\*
  - **Insights:** Dashboard\*
  - **Admin:** Users, Warehouses, Settings, Activity log

  \* = added by later sub-projects. Items are hidden by role.
- **Header:** Ctrl+K global search (customers in Foundation; orders and products added later), notifications, user menu
  (Profile, Security: password / 2FA / sessions, Sign out).
- **Mobile** (< 768px, or the Android app): bottom tabs with a centre Scan button. The tabs depend on role, and the Scan
  action is wired in #3.
- Dark mode is kept.

### Component kit (`src/ui`)

`Button, Field, TextInput, MoneyInput, QuantityInput, Select, Combobox, DateInput (native), DateRangeInput
(flatpickr), DataTable, Drawer, Modal, ConfirmDialog, StatusBadge, StageTracker, Tabs, EmptyState, Skeleton,
toast` (react-hot-toast, already installed).

- `DataTable`: server paging, sorting and filters kept in URL search params. Column visibility is stored per user in
  `localStorage`.
- Forms use `react-hook-form` + `zod` (`@hookform/resolvers`). ProblemDetail `errors[]` map onto the matching fields.
- `APPROVAL_REQUIRED` responses open a shared Request-approval dialog.
- Accessibility basics: visible focus, labelled inputs, status shown as text plus colour.

### Foundation screens

Sign in → TOTP step → TOTP enrolment (QR + recovery codes) · Accept invite (set password) · Forgot/reset
password · Profile & Security (password, 2FA, sessions) · Admin: Users (invite, role, warehouses, archive,
reset 2FA, sessions) · Settings (approval thresholds, POS quick-add assignment) · Activity log · Inbox
(approvals) · Customers (list, page, contacts, history) · Warehouses · Portal sign-in (request link, redeem)
with an empty "Your orders" landing page (filled in #2).

### Dependency changes

Added: `react-hook-form`, `zod`, `@hookform/resolvers`, `@capacitor/preferences`, and dev-only `openapi-typescript`,
`vitest`, `@playwright/test`.
Removed: `lodash`. `flatpickr` is kept for ranges only.

Backend added: `spring-boot-starter-session-data-redis`, `springdoc-openapi-starter-webmvc-ui`,
`org.postgresql:postgresql`, `flyway-database-postgresql`, `com.google.zxing:core`, test-scoped
`spring-modulith-starter-test`, `testcontainers-postgresql`.
Backend removed: `jjwt-*`, `mysql-connector-j`, `flyway-mysql`.

## 10. Testing

- **Unit (no Spring):** TOTP (RFC 6238 test vectors), token hashing and expiry, the approval-match logic, money and quantity
  rounding.
- **Integration** (Testcontainers Postgres + Redis): login, lockout, the 2FA steps, session listing and revocation,
  invite, password reset, portal magic link, scope filters, audit row written and rolled back together with the
  change, and the `crm_app` role unable to `UPDATE`/`DELETE` `audit_event`.
- **Security matrix:** one table-driven test lists every endpoint mapping and its expected outcome for anonymous / ADMIN
  / SALES / WAREHOUSE / CUSTOMER, plus "missing CSRF → 403" on cookie-authenticated mutations. A mapping missing from the table
  fails the test.
- **Modulith** `verify()`.
- **Frontend:** Vitest for the zod schemas, the error mapper and formatters. Playwright smoke tests for the invite → set password →
  enrol 2FA → sign in path, SALES seeing only their own customers, and session revoke.
- **CI** (GitHub Actions): backend `./gradlew test`, frontend `tsc` + `eslint` + `vitest`, and the OpenAPI drift check.

## 11. Deployment changes

- Postgres 17 replaces MySQL in the `docker-compose*.yml` files. The two DB roles are created by an init script.
- The SPA and API are served same-site (e.g. `app.<domain>` + `api.<domain>`). CORS allows only the app origin. Cookies are
  `Secure; SameSite=Lax`.
- The old system keeps running on MySQL until cutover (#5). Foundation is developed and tested against a fresh Postgres.
