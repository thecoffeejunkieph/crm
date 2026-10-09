# Redis Phase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Redis to the CRM API for token revocation, rate limiting, single-flight locks on customer links and the expiry job, dashboard caching, and idempotent creates.

**Architecture:** One Redis (Lettuce via `spring-boot-starter-data-redis`), used directly through `StringRedisTemplate` from three small components (`TokenRevocationService`, `RateLimiter`, `DistributedLock`) plus Spring Cache. Two servlet filters (`RateLimitFilter`, `IdempotencyFilter`) sit at the edge. Layered packages stay as they are (`config/`, `filter/`, `service/`); no Spring Modulith.

**Tech Stack:** Spring Boot 4.0.6, Java 21, Gradle (Groovy DSL), Spring Data Redis + Lettuce, Spring Cache, Testcontainers 2 (core `GenericContainer`), JUnit 5, Mockito.

**Spec:** CRM Refactor Tracker, workstream "REDIS" (R-01 to R-09) and its "MinIO & Redis" tab — https://claude.ai/artifact/Jw2SMuVU7AwAyfD6cmbgVi — plus the approved plan `~/.claude/plans/we-re-gonna-refactor-some-shimmering-biscuit.md`. R-08 (SSE notifications, marked NICE) is out of this plan.

## Global Constraints

- Not SaaS, not Spring Modulith: single company, layered packages under `ph.thecoffeejunkie.crm`.
- Match existing style: Lombok `@RequiredArgsConstructor` + `@Slf4j`, `@Value` for config, comments explain *why*.
- New dependencies allowed: `spring-boot-starter-data-redis`, `spring-boot-starter-cache` only (versions from the Boot BOM). No ShedLock, no Bucket4j, no Redisson.
- Redis key prefixes (from the tracker): `auth:deny:{jti}`, `auth:revoked-before:{username}`, `rl:{rule}:{ip}:{window}`, `lock:{name}`, `cache:{cacheName}::{key}`, `idem:{user}:{method}:{path}:{key}`.
- Image pin: `redis:7.4.2-alpine` in both compose files and tests.
- Prod/staging properties have no fallbacks for Redis host/password (same rule as the other secrets there).
- Whole-plan test command (excludes `CrmApplicationTests`, which needs a live MySQL): `.\gradlew.bat test --tests "ph.thecoffeejunkie.crm.*IT" --tests "ph.thecoffeejunkie.crm.*.*Test"`. Docker must be running.

## Review Focus

- **Redis unavailable at runtime.** Revocation check fails closed (request is unauthenticated); rate limiting, idempotency and caching fail open (request proceeds, ERROR/WARN logged). Tests: Task 1 `isRevoked_failsClosedWhenRedisDown`, Task 2 `allowsWhenRedisDown`, Task 5 `cacheErrorsFallThroughToMethod`, Task 6 `passesThroughWhenRedisDown`.
- **Restart of Redis must not resurrect revoked tokens.** Compose runs Redis with `--appendonly yes` and a volume (Task 1).
- **Client IP behind Traefik.** Rate limit keys use `request.getRemoteAddr()` with `server.forward-headers-strategy=native` in prod/staging so the real client IP is used and `X-Forwarded-For` from untrusted hops is ignored (Task 2).
- **Cache entries written by an older build.** A deserialization failure is a cache miss, not a 500 (Task 5, same error-handler test).
- **Idempotency replay after a failed first attempt.** A non-2xx first response releases the key so the client can retry (Task 6 `releasesKeyOnFailure`).

---

### Task 0: Commit the MinIO work

The working tree holds the finished, tested MinIO phase. Commit it so Redis commits have a clean BASE.

- [ ] **Step 1: Commit**

```bash
git add -A src build.gradle docker-compose.yml docker-compose.prod.yml .env.dev.example .env.staging.example
git commit -m "feat(storage): move uploads to MinIO with presigned redirects"
```
Expected: one commit; `git status --short` shows no tracked changes (`.env.*` real files are git-ignored).

---

### Task 1: Redis wiring and token revocation (R-01, R-02, R-09 part)

Folded: the wiring has no deliverable of its own; token revocation is its first consumer. Refresh-token rotation (also in R-02) is left out: it needs a refresh flow in the frontend, which is not in this repo. A denylist plus logout-all gives real logout without changing the frontend contract.

**Files:**
- Modify: `build.gradle` (add `org.springframework.boot:spring-boot-starter-data-redis`)
- Modify: `src/main/resources/application-dev.properties`, `application-prod.properties`, `application-staging.properties`
- Modify: `docker-compose.yml`, `docker-compose.prod.yml`, `.env.dev.example`, `.env.staging.example`
- Create: `src/main/java/ph/thecoffeejunkie/crm/service/TokenRevocationService.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/util/JwtUtil.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/filter/JWTFilter.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/service/AuthenticationService.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/controller/AuthenticationController.java`
- Create: `src/test/java/ph/thecoffeejunkie/crm/RedisTestSupport.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/service/TokenRevocationServiceIT.java`, `src/test/java/ph/thecoffeejunkie/crm/util/JwtUtilTest.java`

**Interfaces:**
- Produces: `RedisTestSupport.template(): StringRedisTemplate` (static, backed by one shared `GenericContainer("redis:7.4.2-alpine")` on port 6379, started once); `RedisTestSupport.deadTemplate(): StringRedisTemplate` (points at a closed port, for fail-open/closed tests).
- Produces: `TokenRevocationService(StringRedisTemplate)` with `void revoke(String jti, Instant expiresAt)`, `void revokeAllFor(String username)`, `boolean isRevoked(String jti, String username, Instant issuedAt)`.
- Produces: `JwtUtil.extractJti(String): String`, `JwtUtil.extractIssuedAt(String): Instant`, `JwtUtil.extractTokenFromRequest(HttpServletRequest): String` (Bearer header first, else `jwt` cookie, else empty string — the logic currently inline in `JWTFilter`).

- [ ] **Step 1: Write `RedisTestSupport` and the failing tests**

`JwtUtilTest.generatedTokensCarryUniqueJtiAndIssuedAt`: set `secretKey` via `ReflectionTestUtils` to a 48-char string; two tokens for the same `User("a@b.ph", "x", List.of())` have non-null, different `extractJti`, and `extractIssuedAt` within 5 s of now.

`TokenRevocationServiceIT`:
- `revokedJtiIsRevokedUntilExpiry`: `revoke("j1", now+60s)` → `isRevoked("j1", "a@b.ph", now)` true; `isRevoked("j2", "a@b.ph", now)` false; `template.getExpire("auth:deny:j1")` between 1 and 60.
- `revokeAllForRevokesTokensIssuedBeforeOnly`: `revokeAllFor("a@b.ph")`; token issued `now-5s` → revoked; token issued `now+2s` → not revoked; other user's old token → not revoked.
- `alreadyExpiredTokenIsNotStored`: `revoke("j3", now-1s)` → `template.hasKey("auth:deny:j3")` false.
- `isRevoked_failsClosedWhenRedisDown`: service on `deadTemplate()` → `isRevoked(...)` returns true.

- [ ] **Step 2: Run, expect FAIL**

Run: `.\gradlew.bat test --tests "ph.thecoffeejunkie.crm.service.TokenRevocationServiceIT" --tests "ph.thecoffeejunkie.crm.util.JwtUtilTest"`
Expected: compilation failure — `TokenRevocationService`, `extractJti` not defined.

- [ ] **Step 3: Implement**

- `build.gradle`: add the starter.
- Properties: dev `spring.data.redis.host=${REDIS_HOST:localhost}`, `spring.data.redis.port=${REDIS_PORT:6379}`, `spring.data.redis.password=${REDIS_PASSWORD:}`; prod/staging `host=${REDIS_HOST}`, `port=${REDIS_PORT:6379}`, `password=${REDIS_PASSWORD}`.
- Compose: `redis` service, image `redis:7.4.2-alpine`, command `redis-server --appendonly yes --requirepass <pw>` (dev compose: `${REDIS_PASSWORD:-devredis}`; deployed: `${REDIS_PASSWORD}`), volume `redis_data:/data`, healthcheck `redis-cli -a <pw> ping` → `PONG`; api gets `REDIS_HOST: redis`, `REDIS_PASSWORD`, and `depends_on: redis: condition: service_healthy`. Not published to the host in the deployed file; `6379:6379` in dev compose. Add `REDIS_HOST`/`REDIS_PASSWORD` to `.env.dev.example` (`localhost`, `devredis`) and `.env.staging.example` (`redis`, placeholder).
- `JwtUtil.createToken`: add `.id(UUID.randomUUID().toString())`. Add the three methods above; `JWTFilter` calls `extractTokenFromRequest` instead of its inline branch.
- `TokenRevocationService`: `revoke` sets `auth:deny:{jti}`="1" with TTL = `expiresAt - now`, skipped when ≤ 0. `revokeAllFor` sets `auth:revoked-before:{username}` = now epoch seconds, TTL 10 h (the token lifetime in `JwtUtil`). `isRevoked` = deny key exists OR `issuedAt.getEpochSecond() <= revokedBefore`. Any `RuntimeException` from Redis → log ERROR, return true (fail closed).
- `JWTFilter`: after `validateToken` succeeds, skip authentication when `isRevoked(jti, username, issuedAt)`.
- `AuthenticationService.isTokenValid`: also false when revoked.
- `AuthenticationController`: `logout(HttpServletRequest)` revokes the request's token (if parseable; ignore `JwtException`) then clears the cookie. New `POST /api/v1/auth/logout-all` (authenticated, not in `permitAll`): `revokeAllFor(current username)` + clear cookie, 200. Remove the token value from the `check-token` INFO log line (it writes a live credential to the logs).

- [ ] **Step 4: Run, expect PASS**

Run: same as Step 2. Expected: `TokenRevocationServiceIT` 4/4, `JwtUtilTest` 1/1.

- [ ] **Step 5: Commit**

```bash
git add -A build.gradle docker-compose.yml docker-compose.prod.yml .env.dev.example .env.staging.example src
git commit -m "feat(auth): Redis-backed token revocation with logout and logout-all"
```

---

### Task 2: Rate limiting (R-03)

**Files:**
- Create: `src/main/java/ph/thecoffeejunkie/crm/service/RateLimiter.java`
- Create: `src/main/java/ph/thecoffeejunkie/crm/filter/RateLimitFilter.java`
- Modify: `application-prod.properties`, `application-staging.properties` (`server.forward-headers-strategy=native`)
- Test: `src/test/java/ph/thecoffeejunkie/crm/filter/RateLimitFilterIT.java`

**Interfaces:**
- Consumes: `RedisTestSupport.template()`, `deadTemplate()`.
- Produces: `RateLimiter(StringRedisTemplate)` with `long retryAfterSeconds(String rule, String clientId, int limit, Duration window)` — returns 0 when allowed, else seconds until the window resets. Fixed window: key `rl:{rule}:{clientId}:{epochSeconds / windowSeconds}`, `INCR`, `EXPIRE window` on first hit. Redis error → log WARN, return 0 (fail open).
- Produces: `RateLimitFilter(RateLimiter)` `@Component`, rules (method-agnostic, `AntPathMatcher` on servlet path):
  - `login`: `/api/v1/auth/login`, 10 per 60 s
  - `quote-link`: `/api/v1/quotations/*/respond`, 20 per 60 s
  - `payment-link`: `/api/v1/invoices/*/proof-of-payment`, 20 per 60 s
  
  Over the limit: status 429, header `Retry-After: <n>`, JSON body `{"status":429,"error":"Too Many Requests","message":"Too many attempts. Try again in <n> seconds."}`, chain not invoked.

- [ ] **Step 1: Write the failing test** — `RateLimitFilterIT` (filter built on the real `RateLimiter`, driven with `MockHttpServletRequest`/`MockHttpServletResponse`/`MockFilterChain`):
  - `blocksEleventhLoginFromSameIpWithin60s`: 10 POSTs to `/api/v1/auth/login` from `203.0.113.5` → 200 and chain invoked; 11th → 429, `Retry-After` in 1..60, body contains `"status":429`.
  - `limitsArePerIp`: after the above, same path from `203.0.113.6` → passes.
  - `unlistedPathsAreNeverLimited`: 50 GETs to `/api/v1/products` → all pass.
  - `allowsWhenRedisDown`: filter on `deadTemplate()` → 11 logins all pass.
  Each test uses a unique IP so tests do not share windows.
- [ ] **Step 2: Run, expect FAIL** — `.\gradlew.bat test --tests "ph.thecoffeejunkie.crm.filter.RateLimitFilterIT"`. Expected: compilation failure (`RateLimiter` undefined).
- [ ] **Step 3: Implement** the two classes and the property.
- [ ] **Step 4: Run, expect PASS** — same command, 4/4.
- [ ] **Step 5: Commit** — `git commit -m "feat(security): Redis rate limits on login and customer links"`

---

### Task 3: Single-flight locks on customer links and staff accept (R-04)

The tracker asked for one-time link tokens. The services already refuse a second response once a quotation is resolved or an invoice is no longer UNPAID; the real gap is two concurrent clicks both passing that check (two invoices, double stock reservation). A per-resource lock around check-then-act closes it.

**Files:**
- Create: `src/main/java/ph/thecoffeejunkie/crm/service/DistributedLock.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/service/QuotationEmailService.java` (`respond`)
- Modify: `src/main/java/ph/thecoffeejunkie/crm/service/QuotationAcceptanceService.java` (`acceptById`)
- Modify: `src/main/java/ph/thecoffeejunkie/crm/service/InvoicePaymentPortalService.java` (`handleUpload`)
- Test: `src/test/java/ph/thecoffeejunkie/crm/service/DistributedLockIT.java`, `src/test/java/ph/thecoffeejunkie/crm/service/QuotationRespondLockTest.java`

**Interfaces:**
- Produces: `DistributedLock(StringRedisTemplate)` with `boolean tryLock(String name, Duration ttl)` (`SET lock:{name} <token> NX PX ttl`) and `void unlock(String name)` (deletes only if this JVM's token still holds it — compare-and-delete via a small Lua script). Redis error in `tryLock` → log ERROR, return true (fail open: a Redis outage must not stop customers accepting quotes; the status checks still stop sequential repeats).
- Lock names: `quotation-accept:{id}` (shared by `respond` and `acceptById`), `invoice-proof:{id}`. TTL 2 minutes; always `unlock` in `finally`.
- When the lock is held: `respond` and `handleUpload` return status 409, heading `Already Processing`, body `We're already processing a response for this link. Please refresh in a moment.`; `acceptById` throws `InvalidRequestException("This quotation is already being processed")`.

- [ ] **Step 1: Write the failing tests**
  - `DistributedLockIT`: `secondTryLockFailsUntilUnlock`; `lockExpiresAfterTtl` (ttl 300 ms, sleep 500 ms, `tryLock` true again); `unlockDoesNotReleaseSomeoneElsesLock` (lock A, set key value to `other`, `unlock` → key still present); `tryLockFailsOpenWhenRedisDown` (`deadTemplate()` → true).
  - `QuotationRespondLockTest` (Mockito; real `QuotationResponseTokenService` with secret via `ReflectionTestUtils`, `DistributedLock` on `RedisTestSupport.template()`): pre-acquire `quotation-accept:5`; `respond(5, token(5), "ACCEPT")` → status 409; `quotationAcceptanceService` and `repository` have no interactions.
- [ ] **Step 2: Run, expect FAIL** — `.\gradlew.bat test --tests "ph.thecoffeejunkie.crm.service.DistributedLockIT" --tests "ph.thecoffeejunkie.crm.service.QuotationRespondLockTest"`. Expected: compilation failure (`DistributedLock` undefined).
- [ ] **Step 3: Implement** `DistributedLock`; wrap the body of `respond` after the token/id checks, the body of `acceptById`, and the body of `handleUpload` after `resolveInvoice` in `tryLock`/`finally unlock`.
- [ ] **Step 4: Run, expect PASS** — same command, 5/5.
- [ ] **Step 5: Commit** — `git commit -m "fix(quotes,invoices): single-flight lock around customer link actions"`

---

### Task 4: Run the quotation expiry job on one instance (R-05)

ShedLock is replaced by Task 3's lock: same mechanism, no new dependency.

**Files:**
- Modify: `src/main/java/ph/thecoffeejunkie/crm/service/QuotationExpiryService.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/service/QuotationExpiryServiceTest.java`

**Interfaces:**
- Consumes: `DistributedLock.tryLock(String, Duration)`.
- Lock name `job:quotation-expiry`, TTL 10 minutes, never unlocked (holding it past the run stops a second instance whose clock fires a few seconds later).

- [ ] **Step 1: Write the failing test** — Mockito `QuotationRepository`, real `DistributedLock` on `RedisTestSupport.template()`:
  - `skipsWhenAnotherInstanceHoldsTheLock`: pre-acquire `job:quotation-expiry` → `expireOverdueQuotations()` → no repository interactions.
  - `expiresWhenLockIsFree`: delete key first; repository returns one quotation with status `SENT` → after run its status is `EXPIRED` and `saveAll` called once.
- [ ] **Step 2: Run, expect FAIL** — `.\gradlew.bat test --tests "ph.thecoffeejunkie.crm.service.QuotationExpiryServiceTest"`. Expected: first test fails (repository is queried).
- [ ] **Step 3: Implement** — early `return` when `tryLock` is false; log INFO `Skipping quotation expiry; another instance holds the lock`.
- [ ] **Step 4: Run, expect PASS** — 2/2.
- [ ] **Step 5: Commit** — `git commit -m "fix(quotes): run nightly expiry on a single instance"`

---

### Task 5: Dashboard caching (R-06)

**Files:**
- Modify: `build.gradle` (`spring-boot-starter-cache`)
- Create: `src/main/java/ph/thecoffeejunkie/crm/config/CacheConfig.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/service/DashboardService.java`, `WarehouseDashboardService.java`
- Modify: every record reachable from `DashboardSummaryResponse`, `SalesSummaryResponse`, `WarehouseDashboardSummaryResponse` → `implements Serializable`
- Modify: `src/main/resources/application.properties`
- Test: `src/test/java/ph/thecoffeejunkie/crm/config/CacheConfigIT.java`

**Interfaces:**
- Produces: `CacheConfig` — `@EnableCaching`, implements `CachingConfigurer`, `errorHandler()` returns a handler that logs WARN and swallows get/put/evict/clear errors (Redis down or an entry from an older build = cache miss).
- Properties: `spring.cache.type=redis`, `spring.cache.redis.time-to-live=60s`, `spring.cache.redis.key-prefix=cache:`. JDK serialization (default) — hence `Serializable` on the DTOs.
- Cache names: `dashboard-summary` on `getSummary(from, to)`, `sales-summary` on `getSalesSummary(from, to)`, `warehouse-summary` on `WarehouseDashboardService.getSummary()`. No eviction on writes, a deliberate departure from the tracker's "evicted on writes": dashboards aggregate quotations, invoices and stock across many services, and a 60 s TTL bounds staleness without touching every write path. Product-list caching is dropped: a paged single-table query, not worth a cache.

- [ ] **Step 1: Write the failing test** — `CacheConfigIT` with a tiny `@SpringJUnitConfig` context: `CacheConfig`, a `RedisCacheManager` on `RedisTestSupport`'s connection factory (60 s TTL), and a test bean with `@Cacheable("dashboard-summary") String summary(LocalDate from, LocalDate to)` that counts calls.
  - `secondCallIsServedFromCache`: two calls with the same args → counter 1; different args → counter 2.
  - `cacheErrorsFallThroughToMethod`: same bean on a cache manager built from a dead connection factory → call returns the value, counter increments, no exception.
  - `dashboardResponsesAreSerializable`: reflect over the three response records recursively (record components, list element types via generic signature) → every `ph.thecoffeejunkie.crm` type implements `Serializable`.
- [ ] **Step 2: Run, expect FAIL** — `.\gradlew.bat test --tests "ph.thecoffeejunkie.crm.config.CacheConfigIT"`. Expected: compilation failure (`CacheConfig` undefined).
- [ ] **Step 3: Implement** config, annotations, `Serializable`, properties.
- [ ] **Step 4: Run, expect PASS** — 3/3.
- [ ] **Step 5: Commit** — `git commit -m "perf(dashboard): cache dashboard summaries in Redis for 60s"`

---

### Task 6: Idempotency-Key on creates (R-07)

**Files:**
- Create: `src/main/java/ph/thecoffeejunkie/crm/filter/IdempotencyFilter.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/filter/IdempotencyFilterIT.java`

**Interfaces:**
- Produces: `IdempotencyFilter(StringRedisTemplate)` `@Component` (`OncePerRequestFilter`, runs after Spring Security so the user is known; user = authenticated name or `anonymous`).
- Applies to POST only, paths: `/api/v1/quotations`, `/api/v1/invoices`, `/api/v1/invoices/*/payments`, `/api/v1/inventory/receive`, `/api/v1/inventory/reserve`, `/api/v1/inventory/release`. Header `Idempotency-Key` is optional: absent = no-op (the frontend does not send it yet). Max key length 100, else 400.
- Key `idem:{user}:POST:{path}:{headerValue}`, TTL 24 h. `SET NX` value `IN_PROGRESS`; on success run the chain with `ContentCachingResponseWrapper`; 2xx → store JSON `{"status":..,"contentType":..,"body":..}` (body as UTF-8 string); non-2xx → delete key. If the key exists: `IN_PROGRESS` → 409 `{"status":409,"error":"Conflict","message":"A request with this Idempotency-Key is still being processed."}`; stored → replay status, content type, body, plus header `Idempotent-Replayed: true`. Redis error → log WARN and run the chain normally.

- [ ] **Step 1: Write the failing test** — `IdempotencyFilterIT` with `MockMvcBuilders.standaloneSetup(new CountingController()).addFilters(filter)`; `CountingController` has `POST /api/v1/quotations` returning `{"n":<count>}` and a `?fail=true` mode returning 500.
  - `sameKeyRunsOnceAndReplays`: two POSTs with `Idempotency-Key: k1` → handler count 1; both bodies `{"n":1}`; second has `Idempotent-Replayed: true`.
  - `noHeaderIsNotDeduplicated`: two POSTs without the header → count 2.
  - `keysAreScopedPerUser`: same key, `user1` then `user2` (set via `SecurityContextHolder`) → count 2.
  - `releasesKeyOnFailure`: `k2` with `fail=true` → 500; retry `k2` without fail → 200 and handler ran.
  - `inProgressKeyReturns409`: pre-set `idem:anonymous:POST:/api/v1/quotations:k3` = `IN_PROGRESS` → 409.
  - `passesThroughWhenRedisDown`: filter on `deadTemplate()` → 200, handler ran.
  - `unlistedPathsIgnored`: POST `/api/v1/customers` with a key twice → count 2 (add that mapping to `CountingController`).
- [ ] **Step 2: Run, expect FAIL** — `.\gradlew.bat test --tests "ph.thecoffeejunkie.crm.filter.IdempotencyFilterIT"`. Expected: compilation failure.
- [ ] **Step 3: Implement** the filter.
- [ ] **Step 4: Run, expect PASS** — 7/7.
- [ ] **Step 5: Commit** — `git commit -m "feat(api): Idempotency-Key support on create endpoints"`

---

### Task 7: Full suite, live smoke test, tracker

- [ ] **Step 1: Whole suite** — `.\gradlew.bat test --tests "ph.thecoffeejunkie.crm.*IT" --tests "ph.thecoffeejunkie.crm.*.*Test"`. Expected: all pass (MinIO + Redis ITs included).
- [ ] **Step 2: Live smoke** — throwaway MySQL (3307), MinIO (9100), Redis (6380, password `devredis`) containers; `bootRun` on 8089 with matching env. Expected: login 200; `/api/v1/users/me` 200; `logout` then reuse the old cookie value as `Authorization: Bearer` → 401; 11th bad login in a minute → 429 with `Retry-After`; `GET /api/v1/dashboard/summary` twice → `redis-cli KEYS 'cache:*'` shows `cache:dashboard-summary::…`; create a product and a warehouse, then POST `/api/v1/inventory/receive` twice with the same `Idempotency-Key` → stock rises once and the second response has `Idempotent-Replayed: true`. Tear everything down afterwards.
- [ ] **Step 3: Tracker** — set R-01..R-07 and R-09 to Done with one-line notes in the artifact's `progress` collection; R-08 stays Not started.
