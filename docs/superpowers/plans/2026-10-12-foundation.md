# Redesign #1 Foundation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the app's foundation — Postgres baseline, invite-only identity with Redis sessions, CSRF and 2FA,
scoped authorization, approvals, audit, customers/warehouses slice — plus the new TailAdmin shell and component kit.

**Architecture:** Spring Boot modular monolith (package per module, Modulith-verified) on PostgreSQL with Flyway,
Spring Session on Redis, ProblemDetail errors. React 19 SPA with feature folders, generated OpenAPI types,
react-hook-form + zod, and a `src/ui` kit on TailAdmin styles.

**Tech Stack:** Java 21, Spring Boot 4.0.6, Spring Security, Spring Session Data Redis, Spring Data JPA, Flyway,
PostgreSQL 17, Redis 7, MinIO, Testcontainers, Spring Modulith (test), springdoc-openapi, zxing; React 19, Vite 6,
Tailwind 4, TanStack Query 5, axios, react-router 7, react-hook-form, zod, Vitest, Playwright, Capacitor 8.

**Spec:** `docs/superpowers/specs/2026-10-12-foundation-design.md` — read it before any task. Section numbers below
(`§3`) refer to it.

## Global Constraints

- Work on branch `redesign/foundation`, created from `phase/major_refactor_ui` in `crm` and from `phase/major_refactor`
  in `crm-dashboard-ui` (`C:\Users\The Coffee Junkie\Documents\Codes\crm-dashboard-ui`). The legacy domain code is
  **deleted** on this branch; the old system keeps running from the old branches until cutover (#5).
- Base package `ph.thecoffeejunkie.crm`; modules are its direct subpackages: `shared, identity, audit, settings,
  approvals, warehouses, customers, portal, notifications, files`. Dependency direction: `portal → customers → identity → warehouses`; `approvals → identity`; `audit` and `settings` use only `shared` (+ `audit` for settings); `notifications → identity` (for `Users`). Any module may use `shared`, `audit`, `settings`, `notifications`. Only facades, DTOs, events and enums used by other modules
  are `public`; entities and repositories are package-private.
- API root `/api` (no version). Errors are `ProblemDetail` with `code` and `requestId` (§8).
- Money `numeric(19,2)` / `BigDecimal`; quantity `numeric(12,3)`; times `timestamptz` / `Instant`; enums `text` + `CHECK`.
- Every aggregate root has `created_at, updated_at, version` (`@Version`); conflict → 409 `STALE_VERSION`.
- Master data is archived (`archived_at`), never deleted.
- Emails are normalised with `trim().toLowerCase(Locale.ROOT)` everywhere they are stored or looked up.
- Passwords: BCrypt strength 12, min length 12, must not equal the email.
- Sessions: 2h idle, 12h absolute (staff), 7d idle (portal). Cookie `TCJSESSION`, `HttpOnly; Secure(configurable); SameSite=Lax`.
- One-time tokens: 32 random bytes, base64url; only SHA-256 hex stored.
- Portal and action links point to `${app.frontend-url}` (env `APP_FRONTEND_URL`).
- Rate limits (Redis, per IP, 60s window): login 10 failures, `/api/auth/totp` 10 failures, `/api/auth/password/forgot` 5,
  `/api/portal/auth/request-link` 5, `/api/auth/invites/accept` 20, `/api/auth/password/reset` 20, `/api/portal/auth/redeem` 20.
- No new runtime dependency beyond those listed in spec §9 "Dependency changes".

## Review Focus

- **Last admin.** Archiving or demoting the only active ADMIN (including yourself) must be refused with 409 `LAST_ADMIN`, otherwise nobody can ever administer the app again → test in Task 9.
- **Archived principal with a live session.** An archived user or contact must lose access immediately, not at idle timeout → tests in Task 9 (user) and Task 14 (contact).
- **Token double-redeem.** Two concurrent redeems of one invite/reset/portal token: exactly one succeeds → test in Task 9 (atomic `UPDATE … WHERE used_at IS NULL`).
- **Fresh install has no users.** Invite-only with no registration needs a first admin → bootstrap from `app.bootstrap.admin-email` in Task 9.
- **Bad settings values.** A negative or non-numeric threshold must be rejected with 400, not stored and later crash an approval check → test in Task 5.

---

# Part A — Backend (`crm`)

### Task 1: Branch, dependencies, legacy removal, test harness

**Files:**
- Modify: `build.gradle`, `src/main/resources/application*.properties`
- Delete: every class under `src/main/java/ph/thecoffeejunkie/crm/` except those moved below; `src/main/resources/db/migration/*`, `db/dummy-data.sql`, `templates/pdf/**`; legacy tests except those moved below
- Move (package rename only, behaviour unchanged):
  - `service/RateLimiter`, `filter/RateLimitFilter`, `filter/IdempotencyFilter`, `service/DistributedLock`, `service/EncryptionService`, `config/CacheConfig`, `config/RedisListenerConfig`, `config/CorsConfig` → `shared/`
  - `service/StorageService` → `files/`
  - Tests `RedisTestSupport`, `RateLimitFilterIT`, `IdempotencyFilterIT`, `IdempotencyFilterPathsTest`, `DistributedLockIT`, `CacheConfigIT`, `RedisListenerConfigIT`, `RedisTimeoutConfigTest`, `StorageServiceIT` → matching test packages
- Create: `src/main/resources/db/migration/V1__baseline.sql` (empty placeholder, filled in Task 3), `deploy/postgres/init/01-roles.sql`, `src/test/java/ph/thecoffeejunkie/crm/IntegrationTest.java`, `src/test/java/ph/thecoffeejunkie/crm/ModularityTest.java`

**Interfaces:**
- Produces: `@IntegrationTest` meta-annotation = `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` + an `ApplicationContextInitializer` that starts one shared `postgres:17-alpine` container (with `deploy/postgres/init/01-roles.sql` copied to `/docker-entrypoint-initdb.d/`) and the `RedisTestSupport` Redis, and sets `spring.datasource.*` to `crm_app`, `spring.flyway.user/password` to `crm_owner`.
- Produces: `deploy/postgres/init/01-roles.sql` creating `crm_owner` (owner of database `crm`) and `crm_app` (login, no ownership), passwords from `:'owner_pw'`/`:'app_pw'` psql vars with defaults `crm_owner`/`crm_app` for local and test.

- [ ] **Step 1:** `git switch -c redesign/foundation` in `crm`.
- [ ] **Step 2:** In `build.gradle` remove `jjwt-*`, `mysql-connector-j`, `flyway-mysql`, `openpdf`, `flying-saucer-pdf-openpdf`, `batik-*`. Add `spring-boot-starter-session-data-redis`, `org.postgresql:postgresql` (runtime), `org.flywaydb:flyway-database-postgresql`, `org.springdoc:springdoc-openapi-starter-webmvc-ui` (latest 3.0.x, the Boot 4 line), `com.google.zxing:core:3.5.3`, test `org.testcontainers:testcontainers-postgresql`, test `org.springframework.modulith:spring-modulith-starter-test` with `mavenBom 'org.springframework.modulith:spring-modulith-bom:2.0.x'` (latest 2.0 patch). Keep `org.thymeleaf:thymeleaf` (mail templates).
- [ ] **Step 3:** Delete legacy code/tests/migrations, move the carried-over classes, fix their imports. In `RateLimitFilter` and `IdempotencyFilter` set rules/paths to empty lists for now (Task 15 fills them); adjust their tests to register rules through a package-private constructor taking the list.
- [ ] **Step 4:** Properties: datasource URL `jdbc:postgresql://${DB_HOST:localhost}:5432/crm`; `spring.flyway.user/password` from `DB_OWNER_USERNAME/PASSWORD`; `spring.datasource.username/password` from `DB_APP_USERNAME/PASSWORD`; remove `baseline-on-migrate`, quotation/invoice token properties and PDF bank properties; add `spring.session.timeout=2h`, `spring.session.redis.repository-type=indexed`, `server.servlet.session.cookie.name=TCJSESSION`, `app.frontend-url=${APP_FRONTEND_URL:http://localhost:5173}`. Create `application-test.properties` with `app.frontend-url=http://app.test`, `spring.mail.host=localhost`, `spring.mail.port=3025`.
- [ ] **Step 5: Write `ModularityTest`**
```java
@Test void modulesHaveNoCyclesOrReachIns() {
    ApplicationModules.of(CrmApplication.class).verify();
}
```
- [ ] **Step 6:** Run `./gradlew test` — Expected: PASS (context loads against empty Postgres schema; carried ITs pass; Modularity passes).
- [ ] **Step 7:** Commit `chore(foundation): postgres, spring session, legacy domain removed`.

### Task 2: Shared kernel — errors, request id, principals, paging

**Files:**
- Create in `shared/`: `ErrorCode.java`, `DomainException.java`, `ProblemDetailAdvice.java`, `RequestIdFilter.java`, `StaffPrincipal.java`, `ContactPrincipal.java`, `Role.java`, `Scope.java`, `CurrentActor.java`, `PageResponse.java`, `PageQuery.java`
- Create: `src/main/resources/logback-spring.xml`
- Test: `shared/ProblemDetailAdviceTest.java`, `shared/RequestIdFilterTest.java`

**Interfaces:**
- `enum ErrorCode { VALIDATION_FAILED, NOT_FOUND, STALE_VERSION, APPROVAL_REQUIRED, RATE_LIMITED, ACCOUNT_LOCKED, TOTP_REQUIRED, TOTP_INVALID, TOKEN_EXPIRED, FORBIDDEN, UNAUTHORIZED, DUPLICATE, LAST_ADMIN }` each with an `HttpStatus` (400,404,409,422,429,401,401,401,410,403,401,409,409).
- `class DomainException extends RuntimeException` — `DomainException(ErrorCode code, String detail)`, `DomainException(ErrorCode code, String detail, Map<String,Object> properties)`; getters `code()`, `properties()`.
- `ProblemDetailAdvice` (`@RestControllerAdvice`) maps: `DomainException` → its status + `code` + properties; `MethodArgumentNotValidException`/`ConstraintViolationException` → 400 `VALIDATION_FAILED` with `errors: [{field, message}]`; `ObjectOptimisticLockingFailureException` → 409 `STALE_VERSION`; `AccessDeniedException` → 403 `FORBIDDEN`; `NoResourceFoundException` → 404 `NOT_FOUND`; anything else → 500, logged with stack trace, body has only `title`, `status`, `requestId`. Every body carries `requestId` from MDC.
- `RequestIdFilter` (highest precedence): `X-Request-Id` header if it matches `[A-Za-z0-9-]{1,64}`, else random UUID; put in MDC `requestId`, echo in response header; clears MDC after. A second filter, registered as a `FilterRegistrationBean` ordered after Spring Security's chain, puts MDC `userId` = principal name.
- `enum Role { ADMIN, SALES, WAREHOUSE }`
- `record StaffPrincipal(long userId, String email, String displayName, Role role, Set<Long> warehouseIds) implements AuthenticatedPrincipal, Serializable` — `getName()` returns `"user:" + userId`.
- `record ContactPrincipal(long contactId, long customerId, String email, String displayName) implements AuthenticatedPrincipal, Serializable` — `getName()` returns `"contact:" + contactId`.
- `record Scope(long userId, Role role, Set<Long> warehouseIds)` with `boolean isAdmin()`.
- `@Component class CurrentActor` — `Optional<StaffPrincipal> staff()`, `Optional<ContactPrincipal> contact()`, `Scope scope()` (throws `DomainException(UNAUTHORIZED)` if no staff principal), `String clientIp()` (`request.getRemoteAddr()`).
- `record PageResponse<T>(int pageNumber, int pageSize, int totalPages, long totalElements, List<T> content)` with `static <T> PageResponse<T> of(Page<T>)`; `pageNumber` is 1-based.
- `record PageQuery(@Min(1) Integer page, @Min(1) @Max(100) Integer size, String sort)` with `Pageable toPageable(Set<String> sortableFields, Sort defaultSort)`; defaults page 1, size 20; `sort` is `field,asc|desc`; an unknown field → `DomainException(VALIDATION_FAILED)`.
- `logback-spring.xml`: profile `!dev` → JSON via Boot's structured logging (`logging.structured.format.console=ecs`); `dev` keeps the pattern with `%X{requestId}`.

- [ ] **Step 1: Write failing tests**
  - `ProblemDetailAdviceTest` (standalone MockMvc with a test controller): `domainExceptionBecomesProblemDetail` → status 409, `$.code == "DUPLICATE"`, `$.requestId` non-empty, extra property `$.field == "email"`; `validationErrorsListFields` → 400, `$.errors[0].field == "name"`; `unexpectedErrorHidesDetail` → 500, `$.detail` absent, no `"Exception"` substring in body.
  - `RequestIdFilterTest`: valid incoming id echoed; `"bad id!"` replaced by a UUID.
- [ ] **Step 2:** Run `./gradlew test --tests '*ProblemDetailAdviceTest' --tests '*RequestIdFilterTest'` — Expected: FAIL (classes missing).
- [ ] **Step 3:** Implement the interfaces above. Delete the legacy-style JSON body in `RateLimitFilter` and write a ProblemDetail with code `RATE_LIMITED` instead (`Retry-After` kept).
- [ ] **Step 4:** Run the same tests plus `RateLimitFilterIT` — Expected: PASS.
- [ ] **Step 5:** Commit `feat(shared): problem details, request ids, principals, paging`.

### Task 3: Baseline schema and database roles

**Files:**
- Modify: `src/main/resources/db/migration/V1__baseline.sql`
- Test: `src/test/java/ph/thecoffeejunkie/crm/shared/BaselineSchemaIT.java`

The baseline holds every Foundation table (spec §3–§7, plus `notification`). Write it exactly as below; later tasks
only map entities onto it.

```sql
-- conventions: bigint identity ids, timestamptz, text+CHECK enums, archived_at for master data
CREATE TABLE warehouse (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code text NOT NULL, name text NOT NULL, address text,
  is_default boolean NOT NULL DEFAULT false,
  archived_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0);
CREATE UNIQUE INDEX warehouse_code_uq ON warehouse (lower(code)) WHERE archived_at IS NULL;
CREATE UNIQUE INDEX warehouse_one_default ON warehouse (is_default) WHERE is_default AND archived_at IS NULL;

CREATE TABLE app_user (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  email text NOT NULL, password_hash text, first_name text NOT NULL, last_name text NOT NULL, phone text,
  role text NOT NULL CHECK (role IN ('ADMIN','SALES','WAREHOUSE')),
  totp_secret_enc text, totp_enabled_at timestamptz, totp_last_step bigint,
  failed_logins int NOT NULL DEFAULT 0, locked_until timestamptz,
  invite_email_status text CHECK (invite_email_status IN ('PENDING','SENT','FAILED')),
  archived_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0);
CREATE UNIQUE INDEX app_user_email_uq ON app_user (email) WHERE archived_at IS NULL;

CREATE TABLE user_warehouse (
  user_id bigint NOT NULL REFERENCES app_user(id), warehouse_id bigint NOT NULL REFERENCES warehouse(id),
  PRIMARY KEY (user_id, warehouse_id));

CREATE TABLE recovery_code (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id bigint NOT NULL REFERENCES app_user(id), code_hash text NOT NULL, used_at timestamptz);

CREATE TABLE one_time_token (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  purpose text NOT NULL CHECK (purpose IN ('INVITE','PASSWORD_RESET','PORTAL_LOGIN','QUOTE_RESPONSE','PAYMENT_PROOF')),
  subject_type text NOT NULL, subject_id bigint NOT NULL, token_hash text NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL, used_at timestamptz, created_by bigint REFERENCES app_user(id),
  created_at timestamptz NOT NULL DEFAULT now());

CREATE TABLE app_setting (
  key text PRIMARY KEY, value jsonb NOT NULL,
  updated_by bigint REFERENCES app_user(id), updated_at timestamptz NOT NULL DEFAULT now());
INSERT INTO app_setting (key, value) VALUES
  ('approval.discountPercentMax', '15'), ('approval.writeOffQtyMax', '10'), ('pos.quickAddAssignment', '"SELF"');

CREATE TABLE audit_event (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  at timestamptz NOT NULL DEFAULT now(),
  actor_type text NOT NULL CHECK (actor_type IN ('USER','CONTACT','SYSTEM')),
  actor_id bigint, actor_name text, action text NOT NULL,
  entity_type text, entity_id bigint, summary text NOT NULL, changes jsonb,
  ip text, request_id text);
CREATE INDEX audit_entity_idx ON audit_event (entity_type, entity_id, at DESC);
CREATE INDEX audit_actor_idx ON audit_event (actor_type, actor_id, at DESC);
CREATE INDEX audit_at_idx ON audit_event (at DESC);

CREATE TABLE approval_request (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  type text NOT NULL CHECK (type IN ('DISCOUNT','BELOW_COST','CREDIT_LIMIT','STOCK_WRITE_OFF')),
  subject_type text NOT NULL, subject_id bigint NOT NULL, subject_version bigint NOT NULL,
  requested_value jsonb NOT NULL, reason text NOT NULL,
  status text NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
  requested_by bigint NOT NULL REFERENCES app_user(id), requested_at timestamptz NOT NULL DEFAULT now(),
  decided_by bigint REFERENCES app_user(id), decided_at timestamptz, decision_note text,
  version bigint NOT NULL DEFAULT 0);
CREATE INDEX approval_subject_idx ON approval_request (subject_type, subject_id, type, subject_version);
CREATE UNIQUE INDEX approval_one_pending ON approval_request (type, subject_type, subject_id, subject_version)
  WHERE status = 'PENDING';

CREATE TABLE customer (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  type text NOT NULL CHECK (type IN ('BUSINESS','INDIVIDUAL')),
  display_name text NOT NULL, business_name text, tin text, address text, phone text, email text,
  credit_limit numeric(19,2) CHECK (credit_limit IS NULL OR credit_limit >= 0),
  payment_terms text NOT NULL DEFAULT 'DUE_ON_RECEIPT'
    CHECK (payment_terms IN ('DUE_ON_RECEIPT','NET_15','NET_30','NET_60')),
  assigned_rep_id bigint REFERENCES app_user(id), source text, notes text,
  archived_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0);
CREATE INDEX customer_rep_idx ON customer (assigned_rep_id);
CREATE INDEX customer_name_idx ON customer (lower(display_name));

CREATE TABLE customer_contact (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  customer_id bigint NOT NULL REFERENCES customer(id), name text NOT NULL, email text NOT NULL, phone text,
  portal_enabled boolean NOT NULL DEFAULT true,
  archived_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0);
CREATE UNIQUE INDEX contact_email_uq ON customer_contact (email) WHERE archived_at IS NULL;

CREATE TABLE notification (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  recipient_user_id bigint NOT NULL REFERENCES app_user(id),
  type text NOT NULL, entity_type text, entity_id bigint, title text NOT NULL, body text,
  read_at timestamptz, created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX notification_recipient_idx ON notification (recipient_user_id, read_at, created_at DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO crm_app;
REVOKE UPDATE, DELETE, TRUNCATE ON audit_event FROM crm_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO crm_app;
ALTER DEFAULT PRIVILEGES FOR ROLE crm_owner IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO crm_app;
ALTER DEFAULT PRIVILEGES FOR ROLE crm_owner IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO crm_app;
```

- [ ] **Step 1: Write `BaselineSchemaIT` (`@IntegrationTest`, uses `JdbcTemplate` = app role)**
  - `appRoleCanInsertAndReadAudit` → insert one row, select count = 1.
  - `appRoleCannotUpdateAudit` → `update audit_event set summary='x'` throws `DataAccessException` whose message contains `permission denied`.
  - `appRoleCannotDeleteAudit` → same for `delete from audit_event`.
  - `onlyOneDefaultWarehouse` → second `is_default=true` insert throws `DuplicateKeyException`.
- [ ] **Step 2:** Run `./gradlew test --tests '*BaselineSchemaIT'` — Expected: FAIL (tables missing).
- [ ] **Step 3:** Write the migration above.
- [ ] **Step 4:** Run it — Expected: PASS.
- [ ] **Step 5:** Commit `feat(db): postgres foundation baseline with audit write-only grants`.

### Task 4: Audit module

**Files:**
- Create in `audit/`: `AuditLog.java` (public facade), `AuditAction.java` (public enum), `AuditChanges.java` (public), `AuditEvent.java` (entity), `AuditEventRepository.java`, `AuditEventResponse.java` (public), `ActivityController.java`
- Test: `audit/AuditLogIT.java`, `audit/AuditChangesTest.java`

**Interfaces:**
- `enum AuditAction { CREATED, UPDATED, ARCHIVED, LOGIN_SUCCEEDED, LOGIN_FAILED, ACCOUNT_LOCKED, TOTP_ENROLLED, TOTP_RESET, TOTP_DISABLED, RECOVERY_CODE_USED, SESSION_REVOKED, PASSWORD_CHANGED, PASSWORD_RESET, INVITE_SENT, INVITE_ACCEPTED, ROLE_CHANGED, ASSIGNMENT_CHANGED, APPROVAL_REQUESTED, APPROVAL_DECIDED, APPROVAL_CANCELLED, SELF_APPROVED, SETTING_CHANGED, PORTAL_LOGIN }`
- `final class AuditChanges` — `static AuditChanges diff()`, `AuditChanges field(String name, Object oldValue, Object newValue)` (skips when `Objects.equals`; `BigDecimal` compared with `compareTo`), `boolean isEmpty()`, `Map<String, List<Object>> toMap()`.
- `AuditLog`:
  - `@Transactional(propagation = MANDATORY) void record(AuditAction action, String entityType, Long entityId, String summary, AuditChanges changes)` — actor from `CurrentActor` (staff → `USER`, contact → `CONTACT`, none → `SYSTEM`), `actor_name` = principal `displayName`, `ip`, `request_id` from MDC.
  - `@Transactional(propagation = MANDATORY) void recordFor(String actorType, Long actorId, String actorName, AuditAction action, String entityType, Long entityId, String summary)` — for login events where no principal exists yet.
  - `PageResponse<AuditEventResponse> history(String entityType, long entityId, PageQuery page)` — callers enforce scope first.
  - `PageResponse<AuditEventResponse> search(String actorType, Long actorId, String entityType, AuditAction action, Instant from, Instant to, PageQuery page)`.
- `record AuditEventResponse(long id, Instant at, String actorType, Long actorId, String actorName, String action, String entityType, Long entityId, String summary, Map<String, List<Object>> changes)`
- `GET /api/admin/activity?actorType=&actorId=&entityType=&action=&from=&to=&page=&size=` → `PageResponse<AuditEventResponse>`, `@PreAuthorize("hasRole('ADMIN')")`. Default sort `at,desc`.
- `changes` mapped as `jsonb` via `@JdbcTypeCode(SqlTypes.JSON)`.

- [ ] **Step 1: Write failing tests**
  - `AuditChangesTest`: `unchangedFieldsSkipped`; `bigDecimalScaleIgnored` (`new BigDecimal("1.0")` vs `"1.00"` → empty).
  - `AuditLogIT`: `recordOutsideTransactionFails` (`IllegalTransactionStateException`); `auditRolledBackWithBusinessChange` (in `TransactionTemplate`: record + throw → count 0); `staffActorCaptured` (with `SecurityContext` holding a `StaffPrincipal` → `actor_type='USER'`, `actor_name` = displayName); `activityEndpointAdminOnly` (SALES → 403, ADMIN → 200 with the row).
- [ ] **Step 2:** Run `./gradlew test --tests '*Audit*'` — Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run — Expected: PASS. Also run `ModularityTest`.
- [ ] **Step 5:** Commit `feat(audit): append-only audit log and activity API`.

### Task 5: Settings module

**Files:**
- Create in `settings/`: `Settings.java` (public facade), `SettingKey.java` (public enum), `AppSetting.java`, `AppSettingRepository.java`, `SettingsController.java`
- Test: `settings/SettingsIT.java`

**Interfaces:**
- `enum SettingKey { APPROVAL_DISCOUNT_PERCENT_MAX("approval.discountPercentMax"), APPROVAL_WRITE_OFF_QTY_MAX("approval.writeOffQtyMax"), POS_QUICK_ADD_ASSIGNMENT("pos.quickAddAssignment") }` each carrying a validator: the two thresholds are numbers `0 ≤ v ≤ 100` (percent) / `v ≥ 0` (qty, BigDecimal); quick-add is `"SELF"` or `"UNASSIGNED"`.
- `Settings`: `BigDecimal decimal(SettingKey)`, `String text(SettingKey)` — `@Cacheable("settings")`; `@Transactional @CacheEvict(value="settings", allEntries=true) void update(Map<SettingKey, JsonNode> values)` — validates all before writing any; audits `SETTING_CHANGED` per changed key with `AuditChanges` (old → new).
- `GET /api/admin/settings` → `Map<String, Object>` keyed by setting key string; `PUT /api/admin/settings` body same shape (unknown key → 400). Both ADMIN-only.

- [ ] **Step 1: Write failing tests** — `seededDefaultsReadable` (15, 10, `SELF`); `updateIsAudited`; `negativeThresholdRejected` (PUT `{"approval.discountPercentMax": -1}` → 400 `VALIDATION_FAILED`, value still 15); `nonNumericRejected` (`"abc"` → 400); `unknownKeyRejected`; `salesCannotRead` (403).
- [ ] **Step 2:** Run `./gradlew test --tests '*SettingsIT'` — Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run — Expected: PASS.
- [ ] **Step 5:** Commit `feat(settings): admin settings with validation and audit`.

### Task 6: Identity — security config, sessions, login, logout, me

**Files:**
- Create in `identity/`: `AppUser.java`, `AppUserRepository.java`, `SecurityConfig.java`, `SessionConfig.java`, `HeaderOrCookieSessionIdResolver.java`, `AbsoluteSessionTimeoutFilter.java`, `LoginService.java`, `AuthController.java`, `MeController.java`, `PasswordPolicy.java`, `Principals.java`, DTO records `LoginRequest`, `LoginResponse`, `MeResponse`
- Test: `identity/LoginIT.java`, `identity/SessionTransportIT.java`, `identity/PasswordPolicyTest.java`

**Interfaces:**
- `SecurityConfig`: CSRF on via `csrf.spa()` (or, if absent in this Spring Security version, `CookieCsrfTokenRepository.withHttpOnlyFalse()` + the reference-docs SPA request handler); cookie customizer sets `domain` from `app.csrf.cookie-domain` when non-blank. CSRF ignored when the request has header `X-Auth-Token` or `X-Client-Type: ANDROID`. Permit-all: `GET /api/auth/csrf`, `POST /api/auth/login`, `/api/auth/totp` (controller requires `PRE_2FA`), `POST /api/auth/invites/accept`, `/api/auth/password/forgot`, `/api/auth/password/reset`, `/api/portal/auth/**`, `/v3/api-docs/**` (dev/test profiles only). `/api/portal/**` (other) requires `hasRole('CUSTOMER')`; every other `/api/**` requires one of `hasAnyRole('ADMIN','SALES','WAREHOUSE')`. Entry point → 401 ProblemDetail `UNAUTHORIZED`. `@EnableMethodSecurity`.
- Authorities: staff get `ROLE_<role>`; partial logins get only `PRE_2FA` or `ENROL_2FA`; contacts get `ROLE_CUSTOMER`.
- `HeaderOrCookieSessionIdResolver implements HttpSessionIdResolver`: resolve from `X-Auth-Token` header if present, else cookie `TCJSESSION`; `setSessionId` writes the header when the request carried `X-Auth-Token` or `X-Client-Type: ANDROID`, else the cookie; `expireSession` likewise. Cookie serializer: `HttpOnly`, `SameSite=Lax`, `Secure` from `app.session.cookie-secure` (default true; false in dev).
- `AbsoluteSessionTimeoutFilter` (after security context load): staff principal and `now - session.getCreationTime() > 12h` → invalidate session, 401 `UNAUTHORIZED`.
- `Principals`: `StaffPrincipal staffOf(AppUser u, Set<Long> warehouseIds)`; `void establish(HttpServletRequest, HttpServletResponse, Authentication)` — `request.changeSessionId()` (creating session if absent), save context via `HttpSessionSecurityContextRepository`, set session attributes `clientType`, `clientIp`, `userAgent`.
- `PasswordPolicy.check(String email, String password)` → throws `DomainException(VALIDATION_FAILED, …, Map.of("field","password"))` if `< 12` chars or equals email (case-insensitive).
- `LoginService.login(LoginRequest req, HttpServletRequest, HttpServletResponse) → LoginResponse`, `@Transactional(noRollbackFor = DomainException.class)`:
  - unknown/archived email or wrong password → `failed_logins++`; at 5 → `locked_until = now+15m`, `failed_logins = 0`, audit `ACCOUNT_LOCKED`; audit `LOGIN_FAILED` (unknown email: `SYSTEM` actor, summary `"Failed login for <email>"`); throw `DomainException(UNAUTHORIZED, "Invalid email or password")`.
  - `locked_until > now` → `DomainException(ACCOUNT_LOCKED)` (password not checked).
  - user with no `password_hash` (invite pending) → same as wrong password.
  - success: `failed_logins = 0`; if `totp_enabled_at != null` → session with `PRE_2FA`, `next = "TOTP"`; else if role ADMIN → session with `ENROL_2FA`, `next = "ENROL_TOTP"`; else full session, `next = "DONE"`, audit `LOGIN_SUCCEEDED`.
- `record LoginRequest(@NotBlank String email, @NotBlank String password, String clientType)` (`WEB` default | `ANDROID`); `record LoginResponse(String next, String sessionToken)` — `sessionToken` only when `clientType == ANDROID`.
- `GET /api/auth/csrf` → 204 (forces the CSRF token cookie to be written).
- `POST /api/auth/logout` → invalidate session, 204 (authenticated or not).
- `GET /api/me` → `record MeResponse(long id, String email, String firstName, String lastName, String phone, Role role, List<Long> warehouseIds, boolean totpEnabled)`; `PUT /api/me {firstName,lastName,phone}`; (`POST /api/me/password` comes in Task 8).

- [ ] **Step 1: Write failing tests**
  - `PasswordPolicyTest`: 11 chars rejected; equals email (different case) rejected; 12 chars accepted.
  - `LoginIT` (users inserted via repository with BCrypt hashes):
    - `loginWithoutCsrfFromBrowserRejected` → 403.
    - `loginSetsHttpOnlyLaxCookieAndMeWorks` → GET csrf, POST login with `X-XSRF-TOKEN`, `Set-Cookie` contains `TCJSESSION`, `HttpOnly`, `SameSite=Lax`; GET `/api/me` with that cookie → 200 email matches.
    - `emailIsNormalised` → login with `"  Ana@TCJ.PH "` succeeds for stored `ana@tcj.ph`.
    - `wrongPasswordFiveTimesLocks` → 5×401, 6th with correct password → 401 `ACCOUNT_LOCKED`; audit has `ACCOUNT_LOCKED`.
    - `unknownEmailSameResponseAsWrongPassword` → identical status and `code`.
    - `adminWithoutTotpMustEnrol` → `next == "ENROL_TOTP"`, `/api/me` → 403.
    - `sessionIdRotatesOnLogin` → a session cookie sent before login differs from the one after.
    - `absoluteTimeoutEnforced` → session with creation time shifted 13h back (via `FindByIndexNameSessionRepository` fixture or a `Clock` bean injected into the filter) → 401.
    - `logoutInvalidatesSession` → after logout, `/api/me` with old cookie → 401.
  - `SessionTransportIT`: `androidLoginReturnsTokenAndHeaderWorksWithoutCsrf` → login with `X-Client-Type: ANDROID` and `clientType: "ANDROID"` (no CSRF) → body `sessionToken`; `GET /api/me` with `X-Auth-Token` → 200; `PUT /api/me` with the header and no CSRF token → 200.
- [ ] **Step 2:** Run `./gradlew test --tests 'ph.thecoffeejunkie.crm.identity.*'` — Expected: FAIL.
- [ ] **Step 3:** Implement. Inject a `Clock` bean (`Clock.systemUTC()`) used by login lockout and the timeout filter so tests can shift time.
- [ ] **Step 4:** Run — Expected: PASS.
- [ ] **Step 5:** Commit `feat(identity): redis sessions, csrf, login with lockout`.

### Task 7: Warehouses module

**Files:**
- Create in `warehouses/`: `Warehouses.java` (public facade), `Warehouse.java`, `WarehouseRepository.java`, `WarehouseController.java`, records `WarehouseRequest`, `WarehouseResponse` (public)
- Test: `warehouses/WarehousesIT.java`

**Interfaces:**
- `Warehouses`: `List<WarehouseResponse> listActive()`; `WarehouseResponse get(long id)`; `boolean allActive(Set<Long> ids)`; `create(WarehouseRequest)`; `update(long id, WarehouseRequest, long version)`; `archive(long id)` — archiving the default warehouse → 400 `VALIDATION_FAILED`, detail "Choose another default warehouse first." Setting `isDefault=true` clears the previous default in the same transaction. All writes audited (`CREATED/UPDATED/ARCHIVED`, entity `WAREHOUSE`).
- `record WarehouseRequest(@NotBlank String code, @NotBlank String name, String address, boolean isDefault, Long version)`; `record WarehouseResponse(long id, String code, String name, String address, boolean isDefault, long version)`.
- `GET /api/warehouses` (any staff), `GET /api/warehouses/{id}` (any staff), `POST /api/warehouses`, `PUT /api/warehouses/{id}`, `POST /api/warehouses/{id}/archive` (ADMIN). Duplicate active code → 409 `DUPLICATE`.

- [ ] **Step 1: Write failing tests** — `adminCreatesAndListsWarehouse`; `duplicateCodeIgnoringCaseRejected`; `newDefaultReplacesOld`; `cannotArchiveDefault`; `staleVersionRejected` (409 `STALE_VERSION`); `salesCanListButNotCreate`.
- [ ] **Step 2:** Run `./gradlew test --tests '*WarehousesIT'` — Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run — Expected: PASS.
- [ ] **Step 5:** Commit `feat(warehouses): warehouse master data`.

### Task 8: Identity — session management

**Files:**
- Create in `identity/`: `RedisSessionAdmin.java` (implements `SessionAdmin`), `SessionController.java`, record `SessionResponse`
- Create in `identity/`: `SessionAdmin.java` (public interface)
- Modify: `MeController.java` (add `POST /api/me/password`)
- Test: `identity/SessionManagementIT.java`

**Interfaces:**
- `public interface SessionAdmin { void revokeAll(String principalName); void revokeAllExcept(String principalName, String keepSessionId); }` — principal names are `user:<id>` / `contact:<id>`.
- `RedisSessionAdmin` uses `FindByIndexNameSessionRepository<? extends Session>`: `findByPrincipalName(name)`, `deleteById(id)`. Audit `SESSION_REVOKED` per explicit revoke (not for bulk revokes triggered by another audited action).
- `record SessionResponse(String id, String clientType, String clientIp, String userAgent, Instant createdAt, Instant lastAccessedAt, boolean current)` — `id` is the SHA-256 hex of the real session id (never expose the real id); delete endpoints take this hash.
- Endpoints: `GET /api/me/sessions`; `DELETE /api/me/sessions/{hash}`; `DELETE /api/me/sessions` (all except current); `GET /api/admin/users/{id}/sessions`; `DELETE /api/admin/users/{id}/sessions/{hash}`; `DELETE /api/admin/users/{id}/sessions` (ADMIN).
- `POST /api/me/password {currentPassword, newPassword}` → wrong current → 400 `VALIDATION_FAILED` field `currentPassword`; success revokes all other sessions, audit `PASSWORD_CHANGED`.

- [ ] **Step 1: Write failing tests** — `listsOwnSessionsWithCurrentFlag` (two logins → 2 entries, one `current`); `sessionIdNeverExposed` (response JSON doesn't contain either raw id); `revokeOtherDevice`; `revokeAllOthersKeepsCurrent`; `adminRevokesUsersSessions`; `salesCannotListOthersSessions` (403); `passwordChangeKeepsCurrentRevokesOthers`.
- [ ] **Step 2:** Run `./gradlew test --tests '*SessionManagementIT'` — Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `./gradlew test --tests 'ph.thecoffeejunkie.crm.identity.*'` — Expected: PASS.
- [ ] **Step 5:** Commit `feat(identity): session listing and revocation`.

### Task 9: Identity — tokens, mail, invites, user admin, password reset, bootstrap

**Files:**
- Create in `identity/`: `OneTimeTokens.java` (public facade), `TokenPurpose.java` (public enum), `OneTimeToken.java`, `OneTimeTokenRepository.java`, `IdentityMailer.java`, `UserAdminService.java`, `UserAdminController.java`, `PasswordResetService.java`, `InviteAcceptController.java` (also hosts forgot/reset), `BootstrapAdmin.java`, `Users.java`, `UserSummary.java`, records `InviteUserRequest`, `UpdateUserRequest`, `UserResponse`, `AcceptInviteRequest`, `ResetPasswordRequest`
- Create: `src/main/resources/templates/mail/invite.html`, `password-reset.html`, `portal-login.html` (plain Thymeleaf HTML, one CTA link each)
- Test: `identity/OneTimeTokensIT.java`, `identity/UserAdminIT.java`, `identity/PasswordResetIT.java`, `identity/BootstrapAdminIT.java`

**Interfaces:**
- `enum TokenPurpose { INVITE, PASSWORD_RESET, PORTAL_LOGIN, QUOTE_RESPONSE, PAYMENT_PROOF }`
- `OneTimeTokens`: `String issue(TokenPurpose p, String subjectType, long subjectId, Duration ttl, Long createdBy)` → raw token (also invalidates earlier unused tokens of same purpose+subject by setting `used_at`); `Optional<Subject> redeem(TokenPurpose p, String rawToken)` → single-use purposes do `UPDATE one_time_token SET used_at = now() WHERE token_hash = ? AND purpose = ? AND used_at IS NULL AND expires_at > now() RETURNING subject_type, subject_id`; `record Subject(String type, long id)`.
- `IdentityMailer`: `void sendInvite(AppUser, String link)`, `sendPasswordReset(String email, String link)`. Sends via `JavaMailSender`; when `app.mail.log-links=true` (dev only) also logs the link at INFO. Invite sending runs in `@TransactionalEventListener(AFTER_COMMIT)` on `InviteIssued(long userId, String link)` and records `invite_email_status` `SENT`/`FAILED` in a `REQUIRES_NEW` transaction.
- Links: invite `${app.frontend-url}/invite/{token}` (TTL 24h); reset `${app.frontend-url}/reset-password/{token}` (TTL 1h).
- `UserAdminService` (ADMIN):
  - `UserResponse invite(InviteUserRequest)` — `record InviteUserRequest(@Email String email, @NotBlank String firstName, @NotBlank String lastName, String phone, @NotNull Role role, Set<Long> warehouseIds)`; warehouse ids required non-empty for WAREHOUSE role and must be `Warehouses.allActive`; duplicate active email → 409 `DUPLICATE`; audit `INVITE_SENT`.
  - `UserResponse update(long id, UpdateUserRequest)` — `record UpdateUserRequest(String firstName, String lastName, String phone, Role role, Set<Long> warehouseIds, long version)`; role change audited `ROLE_CHANGED`, warehouse change `ASSIGNMENT_CHANGED`; either change revokes all the user's sessions via `SessionAdmin.revokeAll("user:" + id)` (Task 8).
  - `void archive(long id)` — revokes sessions, audit `ARCHIVED`.
  - `void resendInvite(long id)` — only when `password_hash IS NULL`.
  - Last-admin guard: demoting or archiving when it would leave zero active ADMINs → 409 `LAST_ADMIN`.
  - `PageResponse<UserResponse> list(String q, Role role, boolean includeArchived, PageQuery)`.
- `record UserResponse(long id, String email, String firstName, String lastName, String phone, Role role, List<Long> warehouseIds, boolean totpEnabled, boolean invitePending, String inviteEmailStatus, Instant archivedAt, long version)`.
- Endpoints (ADMIN): `GET /api/admin/users`, `POST /api/admin/users`, `PUT /api/admin/users/{id}`, `POST /api/admin/users/{id}/archive`, `POST /api/admin/users/{id}/resend-invite`.
- Public: `POST /api/auth/invites/accept {token, password}` → sets password (policy), audit `INVITE_ACCEPTED`, 204; invalid/used/expired → 410 `TOKEN_EXPIRED`. `POST /api/auth/password/forgot {email}` → always 202; issues reset only for active users with a password. `POST /api/auth/password/reset {token, password}` → sets password, `failed_logins=0`, `locked_until=null`, revokes all sessions, audit `PASSWORD_RESET`, 204.
- `Users` (public facade in `identity`, used by customers, approvals, notifications): `Optional<UserSummary> find(long id)`; `boolean isActiveWithRole(long id, Role role)`; `List<Long> activeIdsWithRole(Role role)`; `Map<Long,String> displayNames(Collection<Long> ids)`; `record UserSummary(long id, String email, String displayName, Role role, boolean active)`.
- `BootstrapAdmin` (`ApplicationReadyEvent`): if `app.bootstrap.admin-email` is set and `app_user` has zero rows → create ADMIN (first/last name `Admin`/`User`) and issue an invite exactly like `invite(...)`.

- [ ] **Step 1: Write failing tests** (use GreenMail on port 3025 or a mocked `JavaMailSender` capturing messages; extract tokens from the captured link)
  - `OneTimeTokensIT`: `storesOnlyHash` (raw token not in table); `expiredTokenRejected`; `concurrentRedeemOnlyOneWins` (two threads redeem the same token via `CompletableFuture` → exactly one `Optional.isPresent()`); `reissueInvalidatesPrevious`.
  - `UserAdminIT`: `inviteEmailsLinkAndAcceptSetsPassword` → then login succeeds; `inviteAcceptReusedTokenGone` (410); `warehouseRoleNeedsWarehouses` (400); `cannotDemoteLastAdmin` (409 `LAST_ADMIN`); `cannotArchiveSelfAsLastAdmin`; `archivedUserLosesSessionImmediately` (logged-in SALES session → admin archives → next `/api/me` with that cookie → 401); `roleChangeRevokesSessions`; `salesCannotInvite` (403); `failedInviteEmailMarkedFailed` (mail sender throws → `inviteEmailStatus == "FAILED"`, user still created).
  - `PasswordResetIT`: `forgotForUnknownEmailStill202AndSendsNothing`; `resetUnlocksAndRevokesSessions`.
  - `BootstrapAdminIT`: empty table + property → one ADMIN with pending invite; non-empty table → nothing created.
- [ ] **Step 2:** Run `./gradlew test --tests '*OneTimeTokensIT' --tests '*UserAdminIT' --tests '*PasswordResetIT' --tests '*BootstrapAdminIT'` — Expected: FAIL.
- [ ] **Step 3:** Implement. Add test dependency `com.icegreen:greenmail-junit5` only if not mocking `JavaMailSender` — prefer the mock.
- [ ] **Step 4:** Run — Expected: PASS.
- [ ] **Step 5:** Commit `feat(identity): invites, user admin, password reset, bootstrap admin`.

### Task 10: Identity — TOTP 2FA and recovery codes

**Files:**
- Create in `identity/`: `Totp.java`, `Base32.java`, `TotpService.java`, `RecoveryCode.java`, `RecoveryCodeRepository.java`, `QrCodes.java`, `TotpController.java`, records `TotpSetupResponse`, `TotpCodeRequest`, `RecoveryCodesResponse`
- Test: `identity/TotpTest.java`, `identity/Base32Test.java`, `identity/TwoFactorIT.java`

**Interfaces:**
- `final class Totp` — `static int codeAt(byte[] secret, long step)` (RFC 6238, HMAC-SHA1, 6 digits); `static long stepAt(Instant t)` (`epochSecond / 30`); `static OptionalLong verify(byte[] secret, String code, Instant now, long lastUsedStep)` → matching step in `[step-1, step+1]` that is `> lastUsedStep`, else empty.
- `Base32.encode(byte[])` / `decode(String)` — RFC 4648 alphabet, no padding, decode case-insensitive and ignores spaces.
- `QrCodes.svg(String text)` → SVG string (zxing `QRCodeWriter`, 240px, margin 1).
- Secret: 20 random bytes; stored encrypted with `EncryptionService` using key from `app.security.encryption-key` (env `ENCRYPTION_KEY`) and existing `encryption.salt`.
- `TotpService`:
  - `TotpSetupResponse setup(long userId)` → new secret held in session attribute `pendingTotpSecret` (not persisted yet); `record TotpSetupResponse(String otpauthUri, String secret, String qrSvg)`; URI `otpauth://totp/TCJ%20CRM:{email}?secret={base32}&issuer=TCJ%20CRM&algorithm=SHA1&digits=6&period=30`.
  - `RecoveryCodesResponse enable(long userId, String code)` → verifies against pending secret, persists encrypted secret + `totp_enabled_at` + `totp_last_step`, replaces recovery codes with 10 new ones (format `xxxxx-xxxxx`, lowercase base32, SHA-256 stored), audit `TOTP_ENROLLED`; `record RecoveryCodesResponse(List<String> codes)`.
  - `void verifyLogin(long userId, String code)` / `void verifyRecovery(long userId, String recoveryCode)` — failure increments `failed_logins` with the same lockout rule as password; recovery success marks code used and audits `RECOVERY_CODE_USED`.
  - `void disable(long userId, String code)` — refused (403 `FORBIDDEN`) for ADMIN; audit `TOTP_DISABLED`.
  - `void adminReset(long targetUserId)` — clears secret, `totp_enabled_at`, recovery codes; revokes the target's sessions; audit `TOTP_RESET`.
- Endpoints:
  - `POST /api/auth/totp {code | recoveryCode}` — requires `PRE_2FA`; success upgrades session to full authorities (`Principals.establish`, rotates id), audit `LOGIN_SUCCEEDED`, returns `LoginResponse("DONE", token-if-android)`; failure 401 `TOTP_INVALID`.
  - `POST /api/me/totp/setup` and `POST /api/me/totp/enable {code}` — allowed for full sessions **and** `ENROL_2FA` sessions; for `ENROL_2FA`, a successful enable upgrades the session to full authorities.
  - `DELETE /api/me/totp {code}` — full session.
  - `POST /api/admin/users/{id}/totp-reset` — ADMIN.

- [ ] **Step 1: Write failing tests**
  - `TotpTest`: RFC 6238 Appendix B SHA-1 vectors truncated to 6 digits — secret ASCII `"12345678901234567890"`: T=59 → `287082`, T=1111111109 → `081804`, T=1234567890 → `005924`, T=2000000000 → `279037`; `previousStepAccepted`; `twoStepsAgoRejected`; `replaySameStepRejected` (lastUsedStep = matched step → empty).
  - `Base32Test`: RFC 4648 vectors `"foobar"` → `MZXW6YTBOI`; round-trip of 20 random bytes.
  - `TwoFactorIT`: `adminEnrolsThenFullAccess` (login → `ENROL_TOTP` → setup → enable with computed code → `/api/me` 200, 10 codes returned); `loginRequiresTotpOnceEnabled` (`next == "TOTP"`, `/api/me` 403, totp → 200); `recoveryCodeWorksOnce`; `wrongTotpCountsTowardLockout`; `adminCannotDisableOwnTotp`; `adminResetClearsAndRevokes` (target's session → 401, next login non-admin → `DONE`); `secretEncryptedAtRest` (column value ≠ base32 secret).
- [ ] **Step 2:** Run `./gradlew test --tests '*Totp*' --tests '*Base32Test' --tests '*TwoFactorIT'` — Expected: FAIL.
- [ ] **Step 3:** Implement (`javax.crypto.Mac` `HmacSHA1`; dynamic truncation per RFC 4226 §5.3).
- [ ] **Step 4:** Run — Expected: PASS.
- [ ] **Step 5:** Commit `feat(identity): totp 2fa with recovery codes`.

### Task 11: Notifications (carried over, re-keyed to users)

**Files:**
- Create in `notifications/`: `Notifications.java` (public facade), `NotificationType.java` (public enum), `Notification.java`, `NotificationRepository.java`, `NotificationStreams.java`, `NotificationController.java`, record `NotificationResponse`; move `RedisListenerConfig` here from `shared` if it only serves notifications
- Source: recover legacy versions with `git show phase/major_refactor_ui:src/main/java/ph/thecoffeejunkie/crm/service/NotificationService.java` (and `NotificationStreams`, `NotificationController`, their tests) and adapt
- Test: `notifications/NotificationsIT.java`, carried `NotificationStreamsIT`

**Interfaces:**
- `enum NotificationType { APPROVAL_REQUESTED, APPROVAL_DECIDED }` (later sub-projects add more).
- `Notifications`: `void toUser(long userId, NotificationType type, String entityType, Long entityId, String title, String body)`; `void toRole(Role role, …same…)` (recipients from `Users.activeIdsWithRole`) — both store rows and publish over Redis **after commit**; `long unreadCount(long userId)`; `void markRead(long userId, long id)`; `void markAllRead(long userId)`; `PageResponse<NotificationResponse> list(long userId, PageQuery)`.
- Streams keyed by `userId` (Redis channel `notifications:user:{id}`), heartbeat 25s unchanged.
- Endpoints (any staff, own rows only): `GET /api/notifications`, `GET /api/notifications/unread-count`, `POST /api/notifications/{id}/read`, `POST /api/notifications/read-all`, `GET /api/notifications/stream` (SSE). Another user's id → 404.

- [ ] **Step 1: Write failing tests** — `toRoleSkipsArchivedUsers`; `notRetainedWhenTransactionRollsBack`; `cannotReadOthersNotification` (404); carried stream test passes on user ids.
- [ ] **Step 2:** Run `./gradlew test --tests 'ph.thecoffeejunkie.crm.notifications.*'` — Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run — Expected: PASS.
- [ ] **Step 5:** Commit `feat(notifications): user-keyed notifications and SSE`.

### Task 12: Approvals module

**Files:**
- Create in `approvals/`: `Approvals.java` (public facade), `ApprovalType.java`, `ApprovalStatus.java` (public enums), `ApprovalRequest.java`, `ApprovalRequestRepository.java`, `ApprovalController.java`, records `CreateApprovalRequest`, `DecisionRequest`, `ApprovalResponse`
- Test: `approvals/ApprovalsIT.java`

**Interfaces:**
- `Approvals.ensureApproved(ApprovalType type, String subjectType, long subjectId, long subjectVersion, Map<String,Object> requestedValue)` — staff ADMIN → audit `SELF_APPROVED` (summary `"self-approved (admin)"`) and return; an `APPROVED` row matching all four keys exists → return; else throw `DomainException(APPROVAL_REQUIRED, "Approval required", Map.of("type", type, "subjectType", subjectType, "subjectId", subjectId, "subjectVersion", subjectVersion, "requestedValue", requestedValue))`.
- `ApprovalResponse request(CreateApprovalRequest)` — `record CreateApprovalRequest(@NotNull ApprovalType type, @NotBlank String subjectType, long subjectId, long subjectVersion, @NotNull Map<String,Object> requestedValue, @NotBlank String reason)`; if a PENDING one exists for the same four keys → return it (idempotent); audit `APPROVAL_REQUESTED`; `Notifications.toRole(ADMIN, APPROVAL_REQUESTED, "APPROVAL", id, …)`.
- `ApprovalResponse decide(long id, DecisionRequest)` — `record DecisionRequest(@NotNull Boolean approve, String note)`; only PENDING → else 409 `STALE_VERSION`; audit `APPROVAL_DECIDED`; notify requester `APPROVAL_DECIDED`.
- `void cancel(long id)` — requester only, PENDING only; audit `APPROVAL_CANCELLED`.
- Names resolved with `Users.displayNames`. `record ApprovalResponse(long id, ApprovalType type, String subjectType, long subjectId, long subjectVersion, Map<String,Object> requestedValue, String reason, ApprovalStatus status, long requestedById, String requestedByName, Instant requestedAt, Long decidedById, String decidedByName, Instant decidedAt, String decisionNote)`.
- Endpoints: `POST /api/approvals` (SALES, WAREHOUSE, ADMIN); `GET /api/approvals?status=&mine=` (ADMIN sees all; others only their own regardless of `mine`); `POST /api/approvals/{id}/decision` (ADMIN); `POST /api/approvals/{id}/cancel` (requester). Not own & not admin → 404.

- [ ] **Step 1: Write failing tests** — `ensureApprovedThrowsWithDetails` (`code == APPROVAL_REQUIRED`, properties present); `approvedMatchPasses`; `newerSubjectVersionNeedsNewApproval`; `adminSelfApprovesAndIsAudited`; `duplicatePendingRequestReturnsSame`; `decideNotifiesRequester`; `salesCannotDecide` (403); `cannotDecideTwice` (409); `salesSeesOnlyOwnRequests`.
- [ ] **Step 2:** Run `./gradlew test --tests '*ApprovalsIT'` — Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run — Expected: PASS.
- [ ] **Step 5:** Commit `feat(approvals): versioned approval requests`.

### Task 13: Customers module (first scoped slice)

**Files:**
- Create in `customers/`: `Customers.java` (public facade), `CustomerType.java`, `PaymentTerms.java` (public enums; copy labels/days from the legacy enum), `Customer.java`, `CustomerContact.java`, `CustomerRepository.java` (extends `JpaSpecificationExecutor`), `CustomerContactRepository.java`, `CustomerScopes.java`, `CustomerController.java`, `CustomerPermissions.java`, records `CustomerRequest`, `CustomerResponse`, `CustomerSummary`, `ContactRequest`, `ContactResponse`, `CustomerLookupItem`, `AssignmentRequest`
- Test: `customers/CustomerScopingIT.java`, `customers/CustomersIT.java`

**Interfaces:**
- `CustomerScopes.visibleTo(Scope s)` → ADMIN: `archived_at IS NULL` only (or all with `includeArchived`); SALES: `assigned_rep_id = s.userId()`; WAREHOUSE: `disjunction()`.
- Repositories are only used inside `Customers`; lookups by id go through `findVisible(long id, Scope s)` → `findOne(idEquals(id).and(visibleTo(s)))`; absent → `DomainException(NOT_FOUND)`.
- `@Component("customerPermissions") CustomerPermissions` — `boolean canRead()` (ADMIN, SALES), `canCreate()` (ADMIN, SALES), `canEdit()` (ADMIN, SALES), `canArchive()` (ADMIN), `canAssign()` (ADMIN).
- `record CustomerRequest(@NotNull CustomerType type, @NotBlank String displayName, String businessName, String tin, String address, String phone, @Email String email, @PositiveOrZero BigDecimal creditLimit, @NotNull PaymentTerms paymentTerms, String source, String notes, Long version)` — SALES may not set `creditLimit` (field ignored on create, 403 `FORBIDDEN` if changed on update).
- `record CustomerResponse(long id, CustomerType type, String displayName, String businessName, String tin, String address, String phone, String email, BigDecimal creditLimit, PaymentTerms paymentTerms, Long assignedRepId, String assignedRepName, String source, String notes, List<ContactResponse> contacts, Instant archivedAt, long version)`.
- `record CustomerSummary(long id, String displayName, CustomerType type, String phone, String email, String assignedRepName, Instant archivedAt)`.
- `record ContactRequest(@NotBlank String name, @Email @NotBlank String email, String phone, boolean portalEnabled, Long version)`; `record ContactResponse(long id, String name, String email, String phone, boolean portalEnabled, long version)`.
- `record CustomerLookupItem(long id, String displayName, String phone, String email)`.
- Archiving a contact, or a customer (all its contacts), calls `SessionAdmin.revokeAll("contact:" + contactId)`.
- `assignedRepName` via `Users.displayNames`.
- `Customers` also exposes for the portal (Task 14): `Optional<ContactPrincipal> portalContactByEmail(String email)` (active contact, `portalEnabled`, customer not archived) and `boolean contactActive(long contactId)`.
- Endpoints (`@PreAuthorize` via `@customerPermissions`):
  - `GET /api/customers?q=&includeArchived=&page=&size=&sort=` → `PageResponse<CustomerSummary>` (scoped; `q` matches display name, business name, email, phone, case-insensitive contains; sortable `displayName, createdAt`).
  - `GET /api/customers/{id}` → `CustomerResponse` (scoped).
  - `POST /api/customers` → created customer assigned to the creating SALES user; ADMIN-created → unassigned unless `assignedRepId` is passed through `PUT …/assignment`.
  - `PUT /api/customers/{id}`; `POST /api/customers/{id}/archive` (ADMIN); `PUT /api/customers/{id}/assignment {repId | null}` (ADMIN; `Users.isActiveWithRole(repId, SALES)` else 400; audit `ASSIGNMENT_CHANGED`).
  - `POST /api/customers/{id}/contacts`; `PUT /api/customers/{id}/contacts/{contactId}`; `POST /api/customers/{id}/contacts/{contactId}/archive` — duplicate active contact email → 409 `DUPLICATE`. Contact events are audited with entity `CUSTOMER`, id = customer id, summary naming the contact.
  - `GET /api/customers/{id}/history?page=&size=` → `AuditLog.history("CUSTOMER", id, …)` after `findVisible`.
  - `GET /api/customers/lookup?q=` → max 20 `CustomerLookupItem`, all active customers, ADMIN and SALES (POS lookup exception, §4); `q` at least 2 chars else 400.
- Settings `POS_QUICK_ADD_ASSIGNMENT` is not consumed yet (POS lives in #4).

- [ ] **Step 1: Write failing tests**
  - `CustomerScopingIT`: `salesSeesOnlyAssigned` (two reps, list shows own only); `otherRepsCustomerIs404` (GET, PUT, history, contacts all 404 — not 403); `warehouseGets403OnCustomers`; `lookupSpansAllCustomersButMinimalFields` (JSON has no `creditLimit`, `notes`, `contacts`); `adminSeesUnassigned`.
  - `CustomersIT`: `salesCreateAssignsSelf`; `salesCannotChangeCreditLimit` (403); `adminReassignmentAudited`; `duplicateContactEmailRejected` (case-insensitive); `historyShowsFieldChanges` (update phone → history has `changes.phone == [old,new]`); `staleVersionOnConcurrentEdit` (409); `lookupNeedsTwoChars` (400).
- [ ] **Step 2:** Run `./gradlew test --tests 'ph.thecoffeejunkie.crm.customers.*'` — Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run — Expected: PASS; run `ModularityTest`.
- [ ] **Step 5:** Commit `feat(customers): scoped customers and contacts`.

### Task 14: Portal identity (`portal` module)

A separate module so the dependency direction stays `portal → customers → identity` (identity must not depend on customers).

**Files:**
- Create in `portal/`: `PortalAuthService.java`, `PortalAuthController.java`, `PortalSessionGuard.java`, `PortalMailer.java`; records `PortalLinkRequest`, `PortalRedeemRequest`, `PortalMeResponse`
- Create: `src/main/resources/templates/mail/portal-login.html`
- Modify: `identity/Principals.java` → `public` (portal uses `establish`)
- Test: `portal/PortalAuthIT.java`

**Interfaces:**
- `POST /api/portal/auth/request-link {email}` → always 202. If `Customers.portalContactByEmail` matches: `OneTimeTokens.issue(PORTAL_LOGIN, "CONTACT", contactId, 15 min, null)`, mail link `${app.frontend-url}/portal/signin/{token}` after commit.
- `POST /api/portal/auth/redeem {token}` → redeem; contact must still be active → session with `ContactPrincipal`, authority `ROLE_CUSTOMER`, `setMaxInactiveInterval(7 days)`, audit `PORTAL_LOGIN` (actor CONTACT); invalid → 410 `TOKEN_EXPIRED`.
- `GET /api/portal/me` → `record PortalMeResponse(long contactId, String name, String email, long customerId, String customerName)`.
- `POST /api/portal/auth/logout` → invalidate, 204.
- `PortalSessionGuard` (a `FilterRegistrationBean` ordered after Spring Security's filter chain, so `identity` needs no reference to it): contact principal and `!Customers.contactActive(contactId)` → invalidate, 401. (Contacts are few and requests light; one indexed lookup per portal request is acceptable.)
- Staff sessions on `/api/portal/**` (except `/api/portal/auth/**`) → 403; contact sessions on any other `/api/**` → 403 (already from Task 6 rules — test it here).

- [ ] **Step 1: Write failing tests** — `requestLinkAlways202AndOnlyMailsKnownContacts`; `redeemCreatesCustomerSession` (`/api/portal/me` → customer id); `tokenSingleUse` (2nd redeem 410); `expiredAfter15Minutes` (clock shift); `contactCannotCallStaffApi` (`/api/customers` → 403); `staffCannotCallPortalApi` (`/api/portal/me` with staff session → 403); `archivedContactLosesSession` (401 on next call); `portalDisabledContactGetsNoMail`.
- [ ] **Step 2:** Run `./gradlew test --tests '*PortalAuthIT'` — Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run — Expected: PASS; `ModularityTest` PASS.
- [ ] **Step 5:** Commit `feat(portal): magic-link customer sessions`.

### Task 15: Rate limits, idempotency, OpenAPI snapshot, security matrix

**Files:**
- Modify: `shared/RateLimitFilter.java` (rules from Global Constraints), `shared/IdempotencyFilter.java` (paths `/api/customers`, `/api/customers/*/contacts`, `/api/approvals`, `/api/admin/users`, `/api/warehouses`)
- Create: `src/test/java/ph/thecoffeejunkie/crm/SecurityMatrixTest.java`, `src/test/java/ph/thecoffeejunkie/crm/OpenApiSnapshotTest.java`, `openapi.json` (repo root, generated)

**Interfaces:**
- `SecurityMatrixTest` (`@IntegrationTest`): reads every mapping from `RequestMappingHandlerMapping.getHandlerMethods()` into keys `"<METHOD> <pattern>"`; a static `Map<String, Set<Who>>` with `enum Who { ANON, ADMIN, SALES, WAREHOUSE, CUSTOMER }` lists who may call each key. For every key × every `Who`, perform the request (path variables = `1`, empty JSON body, with CSRF token and the corresponding `SecurityContext` via `SecurityMockMvcRequestPostProcessors.authentication(...)`):
  - not allowed & ANON → status 401; not allowed & authenticated → 403 or 404;
  - allowed → status not in {401, 403}.
  - A key present in the app but missing from the table → test fails naming the key. A key in the table but not in the app → fails too.
  - Extra case: each mutating staff endpoint called with a cookie-style session and **no** CSRF token → 403.
- `OpenApiSnapshotTest`: GET `/v3/api-docs` → pretty-printed JSON; if `-DupdateSnapshot=true` write `openapi.json`, else assert equal to the committed file (message: "run ./gradlew test --tests '*OpenApiSnapshotTest' -DupdateSnapshot=true").

- [ ] **Step 1:** Write `SecurityMatrixTest` with the full table for every endpoint from Tasks 4–14 and `OpenApiSnapshotTest`; add one IT case per new rate-limit rule to `RateLimitFilterIT` (e.g. `forgotPasswordLimitedAfter5`).
- [ ] **Step 2:** Run `./gradlew test --tests '*SecurityMatrixTest' --tests '*RateLimitFilterIT' --tests '*OpenApiSnapshotTest'` — Expected: FAIL (rules missing, snapshot missing).
- [ ] **Step 3:** Fill the rule/path lists; fix any endpoint whose authorization disagrees with spec §4 (the table is the source of truth from the spec, not from the code); generate the snapshot with `-DupdateSnapshot=true`; wire `systemProp` passthrough in `build.gradle` `test { systemProperty 'updateSnapshot', System.getProperty('updateSnapshot') }`.
- [ ] **Step 4:** Run `./gradlew test` — Expected: all PASS.
- [ ] **Step 5:** Commit `test(security): endpoint authorization matrix, rate limits, openapi snapshot`.

### Task 16: Deployment and CI

**Files:**
- Modify: `docker-compose.yml`, `docker-compose.prod.yml`, `docker-compose.local.yml`, `.env.dev.example`, `.env.staging.example`, `Dockerfile` (if it references MySQL), `.gitignore` (add `.superpowers/`)
- Create: `.github/workflows/backend.yml`

**Interfaces:**
- Compose: `postgres:17-alpine` replaces `mysql` (volume `pg_data`, healthcheck `pg_isready -U crm_owner -d crm`, not published), mounting `deploy/postgres/init` to `/docker-entrypoint-initdb.d`; `POSTGRES_DB=crm`, `POSTGRES_USER=crm_owner`. API env: `DB_HOST`, `DB_OWNER_USERNAME/PASSWORD`, `DB_APP_USERNAME/PASSWORD`, `ENCRYPTION_KEY`, `ENCRYPTION_SALT`, `APP_FRONTEND_URL`, `CORS_ALLOWED_ORIGINS`, `SESSION_COOKIE_SECURE`, `CSRF_COOKIE_DOMAIN` (prod `thecoffeejunkie.ph`), `BOOTSTRAP_ADMIN_EMAIL`. Remove `AUTH_SECRET_KEY`, `QUOTATION_RESPONSE_SECRET`, `INVOICE_*`.
- `docker-compose.local.yml` adds `axllent/mailpit` (SMTP 1025, UI 8025) and the dev profile points mail at it with `app.mail.log-links=true`.
- Add `spring-boot-starter-actuator`: `management.server.port=8081`, `management.endpoints.web.exposure.include=health,info`; health covers db, redis, mail, plus a `MinioHealthIndicator` in `files` (`bucketExists`, 2s timeout). Port 8081 is not published or routed by Traefik.
- Workflow: on PR and push to `redesign/**`, `master`: JDK 21 temurin, `./gradlew test` (Docker available on `ubuntu-latest` for Testcontainers).

- [ ] **Step 1:** Make the changes.
- [ ] **Step 2:** Run `docker compose -f docker-compose.local.yml up -d` then `./gradlew bootRun --args='--spring.profiles.active=dev'` with `BOOTSTRAP_ADMIN_EMAIL=admin@local.test` — Expected: app starts, Flyway applies V1 as `crm_owner`, the invite link appears in the log and in Mailpit.
- [ ] **Step 3:** Commit `chore(deploy): postgres compose, mailpit, backend CI`.

---

# Part B — Frontend (`crm-dashboard-ui`)

Run `npm run gen:api` whenever the backend `openapi.json` changes. All API calls use the generated types
(`components["schemas"]["…"]`); no hand-written DTO types.

### Task 17: Branch, dependencies, structure, API client, auth state

**Files:**
- Delete: `src/pages/**` (except `OtherPage/NotFound.tsx` → move), `src/components/**` except `components/ui/**` and `components/common/{ThemeToggleButton,PageMeta,ScrollToTop,GridShape}.tsx`, `src/layout/Create*Form.tsx`, `src/layout/PosLayout.tsx`, `src/utils/**` except `format.ts`, `cssVar.ts`, `src/routes/RouteGuards.tsx`, `src/context/AuthContext.tsx`
- Move: `components/ui/**` → `src/ui/` (keep files, fix imports), `layout/` → `src/app/shell/`, `context/{Theme,Sidebar}Context.tsx` → `src/app/`
- Create: `src/api/client.ts`, `src/api/schema.d.ts` (generated), `src/api/errors.ts`, `src/app/auth/AuthProvider.tsx`, `src/app/auth/guards.tsx`, `src/app/router.tsx`, `src/app/queryClient.ts`, `vitest.config.ts`
- Modify: `package.json`, `src/App.tsx`, `src/config/env.ts`
- Test: `src/api/errors.test.ts`, `src/api/client.test.ts`

**Interfaces:**
- `package.json`: add `react-hook-form`, `zod`, `@hookform/resolvers`, `@capacitor/preferences`; dev `openapi-typescript`, `vitest`, `@testing-library/react`, `jsdom`, `@playwright/test`; remove `lodash`, `@types/lodash`, `react-select` (replaced by `Combobox`). Scripts: `"gen:api": "openapi-typescript ${OPENAPI_PATH:-../crm/crm/openapi.json} -o src/api/schema.d.ts"`, `"test": "vitest run"`, `"e2e": "playwright test"`.
- `env.ts`: `API_BASE_URL` now means the API origin + `/api` (e.g. `http://localhost:8080/api`).
- `client.ts`: `export const api = axios.create({ baseURL: API_BASE_URL, withCredentials: true, withXSRFToken: true, xsrfCookieName: "XSRF-TOKEN", xsrfHeaderName: "X-XSRF-TOKEN", timeout: 15000 })`. On `Capacitor.isNativePlatform()`: request interceptor adds `X-Client-Type: ANDROID` and `X-Auth-Token` from `Preferences.get({key:"sessionToken"})`; `export async function storeSessionToken(token: string | null)`. Response interceptor: 401 (except on `/auth/*` and `/me`) → `window.location.assign("/signin?next=" + encodeURIComponent(current))`. `export async function primeCsrf()` → `GET /auth/csrf`.
- `errors.ts`: `type Problem = { status: number; code?: string; detail?: string; requestId?: string; errors?: {field: string; message: string}[]; [k: string]: unknown }`; `export function toProblem(e: unknown): Problem`; `export function messageFor(p: Problem): string` — fixed copy per code:
  - `UNAUTHORIZED` "Invalid email or password." · `ACCOUNT_LOCKED` "Too many attempts. Try again in 15 minutes." · `TOTP_INVALID` "That code didn't work. Check your authenticator app and try again." · `TOKEN_EXPIRED` "This link has expired or was already used." · `RATE_LIMITED` "Too many attempts. Please wait a minute." · `STALE_VERSION` "This record changed since you opened it. Reload to see the latest." · `DUPLICATE` "That already exists." · `LAST_ADMIN` "At least one active admin is required." · `FORBIDDEN` "You don't have access to that." · `NOT_FOUND` "Not found." · default "Something went wrong (ref {requestId})."
  - `export function applyFieldErrors(p: Problem, setError: UseFormSetError<any>): boolean`.
- `AuthProvider`: `useAuth(): { status: "loading"|"authenticated"|"unauthenticated"|"unreachable"; me?: Me; refresh(): Promise<void>; signOut(): Promise<void> }` — `me` from `GET /me` via TanStack Query key `["me"]`; no sessionStorage.
- `guards.tsx`: `<RequireStaff roles?={Role[]} />`, `<RequireGuest />`, `<RequireCustomer />` (uses `GET /portal/me`).

- [ ] **Step 1:** `git switch -c redesign/foundation` in `crm-dashboard-ui`; edit `package.json`; `npm install`; `npm run gen:api`.
- [ ] **Step 2: Write failing tests** — `errors.test.ts`: `toProblem` from an axios error with ProblemDetail body keeps `code`/`requestId`; network error → `{status: 0}`; `messageFor({status:409, code:"LAST_ADMIN"})` returns the exact copy; unknown code uses the requestId fallback. `client.test.ts`: config has `withXSRFToken === true` and `xsrfHeaderName === "X-XSRF-TOKEN"`.
- [ ] **Step 3:** Run `npm test` — Expected: FAIL.
- [ ] **Step 4:** Implement; delete legacy files; `App.tsx` renders `<AuthProvider><RouterProvider router={router}/></AuthProvider>` with placeholder routes.
- [ ] **Step 5:** Run `npm test && npx tsc -b && npm run lint` — Expected: PASS.
- [ ] **Step 6:** Commit `chore(ui): foundation structure, typed api client, auth state`.

### Task 18: Component kit

**Files:**
- Create in `src/ui/`: `Field.tsx`, `TextInput.tsx`, `MoneyInput.tsx`, `QuantityInput.tsx`, `Select.tsx`, `Combobox.tsx`, `DateInput.tsx`, `DateRangeInput.tsx`, `DataTable.tsx`, `useTableParams.ts`, `Drawer.tsx`, `ConfirmDialog.tsx`, `StatusBadge.tsx`, `StageTracker.tsx`, `Tabs.tsx`, `EmptyState.tsx`, `Skeleton.tsx`, `index.ts`; keep moved `Button`, `Modal`, `Badge`, `Dropdown`
- Create: `src/ui/numberFormat.ts`
- Test: `src/ui/numberFormat.test.ts`, `src/ui/useTableParams.test.ts`

**Interfaces:**
- `numberFormat.ts`: `formatMoney(v: string | number): string` → `"₱1,234.50"` (en-PH, 2dp); `parseMoney(s: string): string | null` → `"1234.50"` (strips `₱`, commas; >2dp → null); `formatQty(v, unit?)` up to 3dp, trailing zeros trimmed (`2.500` → `"2.5 kg"`); `parseQty(s): string | null` (>3dp → null). Values travel as strings to avoid float drift.
- `MoneyInput`/`QuantityInput`: controlled `{ value: string; onChange(v: string): void; unit?: string }`, `inputMode="decimal"`, format on blur.
- `Field({ label, hint, error, children, required })` — renders `<label htmlFor>`, `aria-invalid`, `aria-describedby` for hint/error.
- `useTableParams(defaults)` → `{ page, size, sort, q, filters, set(patch) }` backed by `useSearchParams`; `set` resets `page` to 1 when anything but `page` changes.
- `DataTable<T>({ columns, rows, total, loading, params, onParamsChange, rowHref?, emptyState, columnStorageKey })` — server-side paging/sorting UI; column visibility in `localStorage[columnStorageKey]` wrapped in try/catch; skeleton rows while loading; keyboard-focusable rows when `rowHref`.
- `Drawer({ open, onClose, title, children, footer })` right-side panel, focus trapped, `Esc` closes.
- `StageTracker({ stages: {key,label}[], current: string, done: string[] })`; `StatusBadge({ tone: "neutral"|"info"|"success"|"warning"|"danger", children })` (text always present).
- `Combobox<T>({ value, onChange, search(q): Promise<T[]>, itemToString, minChars = 2 })` debounced 250ms with existing `useDebouncedValue` (move to `src/ui/`).

- [ ] **Step 1: Write failing tests** — `numberFormat.test.ts`: `formatMoney(1234.5) === "₱1,234.50"`; `parseMoney("₱1,234.50") === "1234.50"`; `parseMoney("1.234") === null`; `formatQty("2.500","kg") === "2.5 kg"`; `parseQty("1.2345") === null`. `useTableParams.test.ts`: changing `q` resets page to 1; changing `page` keeps `q`.
- [ ] **Step 2:** Run `npm test` — Expected: FAIL.
- [ ] **Step 3:** Implement; components use TailAdmin tokens/classes already in `index.css`, dark variants included.
- [ ] **Step 4:** Run `npm test && npx tsc -b` — Expected: PASS.
- [ ] **Step 5:** Commit `feat(ui): component kit`.

### Task 19: App shell

**Files:**
- Create in `src/app/shell/`: `nav.ts`, `Sidebar.tsx` (rewrite of `AppSidebar`), `Header.tsx` (rewrite of `AppHeader`), `CommandPalette.tsx`, `MobileTabs.tsx`, `AppLayout.tsx` (rewrite), `NotificationBell.tsx`
- Test: `src/app/shell/nav.test.ts`

**Interfaces:**
- `nav.ts`: `type NavItem = { label: string; to: string; icon: ReactNode; roles: Role[]; badge?: "approvals" }`; `type NavGroup = { label?: string; items: NavItem[] }`; `export const NAV: NavGroup[]` = Inbox (`/inbox`, all staff, badge approvals); **Sell**: Customers (`/customers`, ADMIN SALES); **Fulfil**: (empty in Foundation — group hidden when empty); **Insights**: (empty); **Admin**: Users (`/admin/users`), Warehouses (`/admin/warehouses`), Settings (`/admin/settings`), Activity log (`/admin/activity`) — ADMIN. `export function navFor(role: Role): NavGroup[]` drops items not allowed and empty groups.
- `MobileTabs` (shown below `md` breakpoint or when `Capacitor.isNativePlatform()`): `mobileTabsFor(role)` → WAREHOUSE `[Tasks /inbox, Me /profile]`; SALES `[Inbox /inbox, Customers /customers, Me /profile]`; ADMIN `[Inbox, Customers, Admin /admin/users, Me]`. The component supports an optional centre `scan` slot; it stays unset until #3 adds Stock, Scan and Moves.
- `CommandPalette`: `Ctrl+K` / `⌘K`; searches `GET /customers?q=&size=5` (ADMIN, SALES); `Enter` navigates; `Esc` closes; shows "No matches" empty state.
- `NotificationBell`: unread count + list from `/notifications`, live via `EventSource(API_BASE_URL + "/notifications/stream", { withCredentials: true })`; on native, poll every 30s instead (EventSource cannot send headers).
- Header user menu: Profile, Security (`/profile/security`), Sign out (`POST /auth/logout`, clear query cache, `storeSessionToken(null)`).

- [ ] **Step 1: Write failing tests** — `navFor("WAREHOUSE")` has no Customers and no Admin; `navFor("SALES")` has Inbox and Customers only; `navFor("ADMIN")` has the Admin group with 4 items; no group returned with zero items.
- [ ] **Step 2:** Run `npm test` — Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `npm test && npx tsc -b && npm run lint` — Expected: PASS.
- [ ] **Step 5:** Commit `feat(ui): job-based shell, command palette, mobile tabs`.

### Task 20: Auth screens

**Files:**
- Create in `src/features/identity/`: `SignInPage.tsx`, `TotpStepPage.tsx`, `EnrolTotpPage.tsx`, `AcceptInvitePage.tsx`, `ForgotPasswordPage.tsx`, `ResetPasswordPage.tsx`, `schemas.ts`, `api.ts`
- Test: `src/features/identity/schemas.test.ts`

**Interfaces:**
- Routes (guest layout = existing `AuthPageLayout` look): `/signin`, `/signin/totp`, `/signin/enrol`, `/invite/:token`, `/forgot-password`, `/reset-password/:token`.
- `schemas.ts`: `signInSchema` (email, password non-empty); `newPasswordSchema(email)` → `{password, confirm}` min 12, `!= email` (case-insensitive), confirm matches — messages "Use at least 12 characters.", "Your password can't be your email.", "Passwords don't match."; `totpSchema` 6 digits or recovery `xxxxx-xxxxx`.
- Flow: SignIn calls `primeCsrf()` then `POST /auth/login`; `next` → `DONE` → `?next` or `/inbox`; `TOTP` → `/signin/totp`; `ENROL_TOTP` → `/signin/enrol`. On native, store `sessionToken`.
- Enrol page: shows `qrSvg` (inject via `<img src={"data:image/svg+xml;utf8," + encodeURIComponent(svg)}>`, never `dangerouslySetInnerHTML`), the base32 secret for manual entry, code input; on success shows the 10 recovery codes with Copy and Download (.txt) and a required "I saved these" checkbox before Continue.
- Token pages call `history.replaceState` to strip the token from the URL after reading it.

- [ ] **Step 1: Write failing tests** — `newPasswordSchema("a@b.ph")` rejects 11 chars, rejects `"A@B.PH"`-equal password, rejects mismatch with the exact messages; `totpSchema` accepts `"123456"` and `"abcde-fghij"`, rejects `"12345"`.
- [ ] **Step 2:** Run `npm test` — Expected: FAIL.
- [ ] **Step 3:** Implement with react-hook-form + zod; API errors through `applyFieldErrors` / `messageFor`.
- [ ] **Step 4:** Run `npm test && npx tsc -b` — Expected: PASS.
- [ ] **Step 5:** Commit `feat(ui): sign-in, 2fa, invite and password reset screens`.

### Task 21: Profile & security, user and warehouse admin

**Files:**
- Create: `src/features/identity/ProfilePage.tsx`, `SecurityPage.tsx` (password, 2FA enable/disable, sessions list with revoke); `src/features/admin/UsersPage.tsx`, `UserDrawer.tsx` (invite/edit: role, warehouses multi-select required for WAREHOUSE, archive, resend invite, reset 2FA, sessions); `src/features/warehouses/WarehousesPage.tsx`, `WarehouseDrawer.tsx`; `api.ts` per feature; `src/features/admin/userSchema.ts`
- Test: `src/features/admin/userSchema.test.ts`

**Interfaces:**
- `userSchema`: WAREHOUSE role requires ≥1 warehouse ("Pick at least one warehouse."); email required/valid.
- Users table columns: Name, Email, Role, Warehouses, 2FA (badge), Status (`Invite pending` warning / `Invite email failed` danger with Resend / `Active` success / `Archived` neutral).
- Archive and 2FA reset go through `ConfirmDialog` with the user's name in the message.
- Sessions list: device (clientType + parsed browser from userAgent), IP, last active (relative), "This device" badge; revoke buttons; "Sign out all other devices".

- [ ] **Step 1: Write failing test** for `userSchema` rules.
- [ ] **Step 2:** Run `npm test` — Expected: FAIL.
- [ ] **Step 3:** Implement; `LAST_ADMIN`, `DUPLICATE`, `STALE_VERSION` surface via `messageFor` as toasts, field errors inline.
- [ ] **Step 4:** Run `npm test && npx tsc -b && npm run lint` — Expected: PASS.
- [ ] **Step 5:** Commit `feat(ui): profile, security, user and warehouse admin`.

### Task 22: Settings, activity log, inbox, approval dialog

**Files:**
- Create: `src/features/admin/SettingsPage.tsx`, `ActivityLogPage.tsx`; `src/features/approvals/InboxPage.tsx`, `RequestApprovalDialog.tsx`, `ApprovalCard.tsx`, `useApprovalRequired.ts`, `api.ts`; `src/features/admin/settingsSchema.ts`
- Test: `src/features/admin/settingsSchema.test.ts`, `src/features/approvals/useApprovalRequired.test.ts`

**Interfaces:**
- `settingsSchema`: discount 0–100, write-off ≥ 0, quick-add `SELF|UNASSIGNED`.
- Activity log: DataTable with filters (actor, entity type, action, date range via `DateRangeInput`), each row's `changes` rendered as "field: old → new" lines.
- Inbox: ADMIN sees PENDING approvals with Approve / Reject (note) actions; others see their own requests with status and Cancel. Badge count = PENDING count (ADMIN) via `GET /approvals?status=PENDING`, refreshed on `APPROVAL_*` notifications.
- `useApprovalRequired()` → `{ handle(e: unknown): boolean; dialog: ReactNode }` — when `toProblem(e).code === "APPROVAL_REQUIRED"` opens `RequestApprovalDialog` prefilled from the problem properties (`type, subjectType, subjectId, subjectVersion, requestedValue`) and returns `true`; reason is required.

- [ ] **Step 1: Write failing tests** — settings ranges; `handle` returns `true` and exposes the problem payload for an `APPROVAL_REQUIRED` problem, `false` for `DUPLICATE`.
- [ ] **Step 2:** Run `npm test` — Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `npm test && npx tsc -b && npm run lint` — Expected: PASS.
- [ ] **Step 5:** Commit `feat(ui): settings, activity log, approvals inbox`.

### Task 23: Customers screens

**Files:**
- Create in `src/features/customers/`: `CustomersPage.tsx`, `CustomerPage.tsx` (tabs Details · Contacts · History), `CustomerDrawer.tsx`, `ContactDrawer.tsx`, `AssignRepDialog.tsx`, `customerSchema.ts`, `api.ts`
- Test: `src/features/customers/customerSchema.test.ts`

**Interfaces:**
- Routes `/customers`, `/customers/:id` (ADMIN, SALES).
- `customerSchema`: `displayName` required; `businessName` required when `type === "BUSINESS"` ("Business name is required for business customers."); `email` optional valid; `creditLimit` via `parseMoney`, ≥ 0, field hidden for SALES.
- List: DataTable (Name, Type, Phone, Email, Rep, Status) with search box bound to `q`, "Show archived" toggle (ADMIN).
- History tab renders `AuditEventResponse` lines (who, when, summary, changes).
- 404 on `/customers/:id` renders the shared `NotFound` (never "forbidden").

- [ ] **Step 1: Write failing test** for `customerSchema`.
- [ ] **Step 2:** Run `npm test` — Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `npm test && npx tsc -b && npm run lint` — Expected: PASS.
- [ ] **Step 5:** Commit `feat(ui): customers list, page, contacts, history`.

### Task 24: Portal sign-in

**Files:**
- Create in `src/features/portal/`: `PortalSignInPage.tsx` (email → "Check your inbox"), `PortalRedeemPage.tsx` (`/portal/signin/:token`), `PortalHomePage.tsx` (`/portal`, greeting + empty "Your orders will appear here." state), `PortalLayout.tsx` (move legacy `components/portal/PortalLayout.tsx` here, adapt), `api.ts`

**Interfaces:**
- Routes: `/portal/signin`, `/portal/signin/:token`, `/portal` (`RequireCustomer`). Redeem page strips token from history, then navigates to `/portal`; `TOKEN_EXPIRED` shows "This sign-in link has expired or was already used." with a button back to `/portal/signin`.
- The request-link page always shows the same confirmation regardless of response (no account enumeration).

- [ ] **Step 1:** Implement (no unit-testable logic beyond what Task 17 tests; covered by Task 25 e2e).
- [ ] **Step 2:** Run `npx tsc -b && npm run lint` — Expected: PASS.
- [ ] **Step 3:** Commit `feat(ui): customer portal sign-in`.

### Task 25: Playwright smoke and frontend CI

**Files:**
- Create: `playwright.config.ts`, `e2e/helpers/mailpit.ts`, `e2e/helpers/totp.ts`, `e2e/foundation.spec.ts`, `.github/workflows/frontend.yml`

**Interfaces:**
- Preconditions (documented at top of `foundation.spec.ts`): backend running with `docker-compose.local.yml` (Postgres, Redis, MinIO, Mailpit), `BOOTSTRAP_ADMIN_EMAIL=admin@local.test`, fresh DB; frontend `npm run dev`. Base URLs from `E2E_APP_URL` (default `http://localhost:5173`), `E2E_MAILPIT_URL` (default `http://localhost:8025`).
- `mailpit.ts`: `latestLinkTo(email: string, pathPrefix: string): Promise<string>` via Mailpit `GET /api/v1/search?query=to:"<email>"` then message HTML, regex the first `href` starting with `${E2E_APP_URL}${pathPrefix}`.
- `totp.ts`: `totp(base32Secret: string, at = Date.now()): string` (node `crypto` HMAC-SHA1).
- Tests (serial):
  1. `bootstrap admin accepts invite, enrols 2FA, reaches inbox` — saves recovery codes acknowledgement.
  2. `admin invites a SALES rep and a second SALES rep; each sees only their own customers` — creates one customer per rep, asserts the other's customer URL shows Not Found.
  3. `revoking a session signs that device out` — two browser contexts, revoke from the first, second's next navigation lands on `/signin`.
  4. `customer contact signs in to the portal by magic link`.
- Workflow `frontend.yml`: checkout this repo and `thecoffeejunkieph/crm` into `backend/`; `npm ci`; `OPENAPI_PATH=backend/openapi.json npm run gen:api`; `git diff --exit-code src/api/schema.d.ts` (drift check); `npx tsc -b`; `npm run lint`; `npm test`. Playwright is run locally, not in CI.

- [ ] **Step 1:** Write the helpers and spec.
- [ ] **Step 2:** Run the preconditions, then `npm run e2e` — Expected: 4 passed.
- [ ] **Step 3:** Commit `test(ui): foundation e2e smoke and CI`.

---

## Explicitly deferred (not in this plan)

- Document-number sequences helper (§2) — first needed by orders in #2.
- `files` HTTP endpoint — carried `StorageService` only; per-entity file access rules arrive with the entities in #2/#3.
- Scan button behaviour, QR label rendering for DO/bins/transfers — #3 (`QrCodes` from Task 9 is reused).
- Export auditing — no exports exist yet.
