# Logging across the app — design

Status: approved in chat 2026-11-05. Not a tracker item; requested alongside M-08 ("logging for every step, for the
whole app, so we can see what's happening and debug easily").

## Goal

When something goes wrong, find what happened from one ID: the reference on the error toast leads to the request's
log lines, the business steps it took and the service calls behind them.

## Today

- `RequestIdFilter` puts `requestId` in MDC and the `X-Request-Id` response header; `UserIdMdcFilter` adds the user.
- Dev prints a readable pattern with `reqId`; staging/prod print ECS JSON to the console (`logging.structured.format.console=ecs`).
- Only 19 classes log. Sales, inventory, fulfilment, catalog, customers, approvals, POS, insights and follow-ups say
  nothing; there is no per-request line; jobs and listeners run with no ID; the UI never shows the request ID.

## Decisions (from the user)

- Logs are read from the console and from files. No log viewer service.

## Design

### 1. Request line — `shared/AccessLogFilter`
- Runs right after `RequestIdFilter` (so `requestId` is set) and after security (so the user is known).
- One line when the response completes: `{method} {path} → {status} in {ms}ms user={id} {role} q={queryKeys}`.
  Path only, never the query string values (they can carry tokens); query parameter *names* are listed.
- Level: 2xx/3xx INFO, 4xx WARN with the ProblemDetail `code` when present, 5xx ERROR (the stack trace is logged once,
  by `ProblemDetailAdvice`, not again here).
- Skipped: `/actuator/**`, `/api/notifications/stream`, static assets.

### 2. Business step line — `AuditLog.record`
- Every call also logs INFO, logger `crm.audit`: `{action} {entityType} {entityId} "{summary}"` plus changed field
  names (not values). One change covers every module, and the audit trail and the log cannot disagree.
- `recordFor` (portal contacts, system) logs the same with the actor type.

### 3. Service calls — `shared/ServiceCallLogging`
- One `MethodInterceptor` advisor applied to every `@Service` and `@Component` bean in `ph.thecoffeejunkie.crm`
  (Spring AOP proxy, no AspectJ dependency; excluded: filters, config, the logging classes themselves, `AuditLog`).
- DEBUG, logger = the target class: `→ method(args)` on entry, `← method in {ms}ms` on exit.
- WARN on exit when the call took ≥ 500 ms, at any level setting (`slow → method in {ms}ms`).
- On exception: DEBUG `✗ method threw {ExceptionClass}: {message}` (the 4xx/5xx line and the advice carry the rest).
- `application.properties` keeps `logging.level.ph.thecoffeejunkie.crm=DEBUG` for dev; staging/prod set it to INFO.

### 4. Redaction — `shared/LogRedactor`
- Arguments are rendered by `LogRedactor.render(Object)`: records and beans field by field, collections with at most
  5 items and a count, strings cut at 200 chars, `byte[]`/`InputStream`/`MultipartFile` as `<n bytes>`.
- Masked to `***` by name (parameter or field, case-insensitive, contains): `password`, `secret`, `token`, `code`
  (TOTP/recovery), `otp`, `cookie`, `authorization`, `link`, `key` (except `idempotencyKey`, logged).
- Emails masked to first letter + domain (`j***@acme.com`); fields named `tin` masked except the last 3 digits.
- Test: a fixed list of known secret values passed through every request type of the identity and portal flows must
  not appear in captured log output.

### 5. Background work
- `@Scheduled` methods and `@EventListener`/`@TransactionalEventListener` methods run with MDC `jobId`
  (`job-<8 hex>`) and `job` (class.method) set by `shared/JobMdc`, applied by the same advisor when the method has one
  of those annotations, and cleared afterwards. If a listener runs inside a request, the `requestId` stays and
  `jobId` is added.
- Dev pattern shows `[reqId=%X{requestId}%X{jobId}]`; ECS JSON carries both keys.

### 6. Changing detail at runtime
- Expose the actuator `loggers` endpoint (`management.endpoints.web.exposure.include=health,info,loggers`) on the
  management port only, ADMIN-only via the existing management security. `POST /actuator/loggers/ph.thecoffeejunkie.crm`
  `{"configuredLevel":"DEBUG"}` turns service-call lines on in prod without a redeploy.

### 7. Files — `logback-spring.xml`
- Add a `RollingFileAppender` next to `CONSOLE`: `${LOG_PATH:-logs}/crm.log`, daily rollover
  (`crm.%d{yyyy-MM-dd}.%i.log.gz`), `maxHistory=14`, `totalSizeCap=1GB`, `maxFileSize=100MB`.
- Format: ECS JSON in staging/prod (same as console), the readable pattern in dev.
- `docker-compose.prod.yml`: mount `./logs:/app/logs` for the api service. `logs/` added to `.gitignore`.

### 8. UI (crm-dashboard-ui)
- The API client reads `X-Request-Id` from every error response and attaches it to the problem object (`requestId`).
- `toastError` appends ` (Ref {first 6 chars})` when a request ID is present.
- `import.meta.env.DEV`: failed API calls log `console.warn("[api]", method, url, status, code, requestId)`.
- CORS: expose `X-Request-Id` (`Access-Control-Expose-Headers`) so the browser can read it.

## Error handling

- Logging never throws into the request: the interceptor and `LogRedactor` catch their own failures and log a single
  `log-render-failed` line instead of the arguments.
- Rendering is lazy: arguments are only rendered when DEBUG is enabled for that logger.

## Testing

- `AccessLogFilterTest`: 200 → INFO line with method, path, status, user; 422 → WARN with code; query values absent.
- `AuditLog` IT: recording an action emits the `crm.audit` line.
- `ServiceCallLoggingIT`: a service call emits entry/exit at DEBUG; a slow call (stubbed clock/sleep ≥ 500 ms) emits
  WARN at INFO level; an exception emits the `✗` line and still propagates unchanged.
- `LogRedactorTest`: each masked name, email and TIN masking, collection cap, byte arrays, a cyclic object.
- `SecretsNotLoggedIT`: login, TOTP enrol, password reset, invite accept, portal magic link — none of the known
  secret values appear in captured output.
- `JobMdcTest`: a scheduled method logs with `jobId`; MDC is clean afterwards.
- UI: `toastError` shows the Ref; the client attaches `requestId` from the header.

## Out of scope

Log viewer services (Loki/Grafana/Seq), shipping logs off the VPS, distributed tracing, metrics dashboards, logging
request/response bodies.
