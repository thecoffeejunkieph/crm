# Graph Report - crm  (2026-11-15)

## Corpus Check
- 570 files · ~253,520 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 17 file(s) not represented in the graph (top: .properties 6, (none) 3, .example 2)

## Summary
- 4812 nodes · 17059 edges · 198 communities (118 shown, 80 thin omitted)
- Extraction: 86% EXTRACTED · 14% INFERRED · 0% AMBIGUOUS · INFERRED: 2462 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `7f2ef4db`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Scope
- .order
- org.springframework.jdbc.core.JdbcTemplate
- ErrorCode
- AuditAction
- org.junit.jupiter.api.Test
- .lead
- PaymentService
- org.springframework.transaction.annotation.Transactional
- ApprovalsIT
- UserAdminIT
- OrderService
- Customers
- DealsFixture
- TwoFactorIT
- TaxClass
- PortalAuthIT
- Part A — Backend (`crm`)
- jakarta.persistence.Entity
- UserAdminService
- SendQuoteIT
- Redesign #3 — Inventory & Fulfilment
- PortalOrderService
- java.time.YearMonth
- org.springframework.stereotype.Component
- CurrentActor
- ReportRange
- DeliveryService
- SettingKey
- OrderStockIT
- .l
- LoginThrottle
- .update
- PasswordResetIT
- .redeem
- InvoicesIT
- .at
- .createdOrder
- .contains
- org.springframework.data.jpa.domain.Specification
- Inventory
- .as
- org.springframework.http.ResponseEntity
- org.springframework.data.jpa.repository.JpaRepository
- DeliveryOrdersIT
- AttachmentService
- FollowUpService
- .json
- OrderAction
- org.springframework.context.annotation.Bean
- TransferService
- Redesign #4 — POS
- NotificationType
- TransfersIT
- org.springframework.data.redis.core.StringRedisTemplate
- .csrf
- TargetsIT
- Part A — Backend (`crm`)
- PaymentsIT
- OrderResponse
- V5__inventory_fulfilment.sql
- .create
- Deals / opportunities (F-130) — design
- jakarta.servlet.http.HttpServletResponse
- Follow-ups (F-141) — design
- Part A — Backend (`crm`)
- .of
- StockReadsIT
- HtmlMail
- LeadService
- LoginIT
- SessionManagementIT
- PdfRenderer
- IntegrationTest.java
- .find
- WarehouseInsightsIT
- ProductUnit
- NotificationStreams
- CatalogIT
- .receive
- Backend (`crm/crm`)
- LogRedactorTest
- jakarta.servlet.http.HttpServletRequest
- Backend (`crm/crm`)
- .record
- app_user
- AuditLogIT
- Frontend (`crm-dashboard-ui`)
- SalesPermissions
- RedisTestSupport
- TargetMilestonesIT
- ServiceCallLoggingIT
- Design
- Notification
- DeliveryStatus
- .product
- SessionController
- Review Focus
- .setup
- Review Focus
- Warehouse
- org.springframework.web.bind.annotation.GetMapping
- .update
- WarehousesIT
- ServiceCallLogging.java
- PaymentMethod
- CoverShortageTest
- RateLimitFilterIT
- DealWonIT
- Redesign #1 — Foundation
- sales_order
- LeadSource
- PaymentTerms
- Review Focus
- .issue
- Leads (F-120) — design
- AttachmentsIT
- CustomerScopingIT
- AttachmentsFixture
- LeadSourcesIT
- Backend (`crm`)
- Review Focus
- CatalogPermissions
- Retail POS — Design
- Redesign #2 — Sales lifecycle
- InventorySchemaIT
- Review Focus
- FollowUpQueries
- LeadSourceBackfillIT
- LogLevelIT
- TargetService
- MinioHealthIndicator
- Attachments (M-08) — design
- SecurityMatrixTest
- package.json
- FollowUpsIT
- CustomerSourceIT
- CustomerPermissions
- Review Focus
- .check
- lead
- .account
- RedisSessionAdmin
- AppUser
- migrate.py
- Review Focus
- Review Focus
- Redesign #4 — Dashboards
- Reports (F-172)
- MovementType
- deal
- Notifications — design (2026-11-12)
- .requireNotFuture
- attachment
- .of
- org.springframework.core.env.Environment
- CrmApplication
- EncryptionSecretsCheckTest
- ProblemDetailAdviceTest
- Production cutover (P5-2)
- follow_up
- gradlew
- org.junit.jupiter.api.AfterEach
- SalesTime
- transform.sql
- PdfText
- 01-roles.sh
- V12__follow_up_reminder_run.sql
- LeadSources
- ReceiptsIT
- Bucket
- StaffPrincipal
- SalesTarget
- DocsDisabledTest

## God Nodes (most connected - your core abstractions)
1. `Role` - 180 edges
2. `IntegrationTest` - 153 edges
3. `Scope` - 142 edges
4. `AuditAction` - 122 edges
5. `CurrentActor` - 121 edges
6. `AuditLog` - 116 edges
7. `ErrorCode` - 110 edges
8. `Customers` - 98 edges
9. `DomainException` - 90 edges
10. `PageResponse` - 79 edges

## Surprising Connections (you probably didn't know these)
- `pg_temp.sys()` --reads_from--> `app_user`  [EXTRACTED]
  deploy/migrate/transform.sql → src/main/resources/db/migration/V1__baseline.sql
- `pg_temp.uid()` --reads_from--> `app_user`  [EXTRACTED]
  deploy/migrate/transform.sql → src/main/resources/db/migration/V1__baseline.sql
- `sales_target` --references--> `app_user`  [EXTRACTED]
  src/main/resources/db/migration/V10__insights.sql → src/main/resources/db/migration/V1__baseline.sql
- `ApprovalController` --references--> `Approvals`  [EXTRACTED]
  src/main/java/ph/thecoffeejunkie/crm/approvals/ApprovalController.java → src/main/java/ph/thecoffeejunkie/crm/approvals/Approvals.java
- `ApprovalRequestRepository` --references--> `ApprovalRequest`  [EXTRACTED]
  src/main/java/ph/thecoffeejunkie/crm/approvals/ApprovalRequestRepository.java → src/main/java/ph/thecoffeejunkie/crm/approvals/ApprovalRequest.java

## Import Cycles
- None detected.

## Communities (198 total, 80 thin omitted)

### Community 0 - "Scope"
Cohesion: 0.08
Nodes (13): Invoice, InvoiceDocument, SuppressWarnings, InvoiceRepository, InvoiceResponse, InvoiceService, InvoiceStatus, ISSUED (+5 more)

### Community 2 - "org.springframework.jdbc.core.JdbcTemplate"
Cohesion: 0.09
Nodes (34): Multipart, Order, org.junit.jupiter.api.BeforeEach, org.springframework.cache.CacheManager, org.springframework.context.ApplicationContext, org.springframework.jdbc.core.JdbcTemplate, org.springframework.test.context.event.ApplicationEvents, org.springframework.test.context.event.RecordApplicationEvents (+26 more)

### Community 3 - "ErrorCode"
Cohesion: 0.03
Nodes (60): io.minio.MinioClient, org.slf4j.Logger, org.springframework.boot.context.event.ApplicationReadyEvent, org.springframework.core.task.TaskExecutor, org.springframework.security.crypto.password.PasswordEncoder, org.springframework.transaction.PlatformTransactionManager, AuditChanges, FileController (+52 more)

### Community 4 - "AuditAction"
Cohesion: 0.03
Nodes (80): AuditAction, ACCOUNT_LOCKED, APPROVAL_CANCELLED, APPROVAL_DECIDED, APPROVAL_REQUESTED, ARCHIVED, ASSIGNMENT_CHANGED, ATTACHMENT_ADDED (+72 more)

### Community 5 - "org.junit.jupiter.api.Test"
Cohesion: 0.04
Nodes (14): java.util.zip.ZipOutputStream, org.junit.jupiter.api.Test, AuditChangesTest, StorageDetectTest, FollowUpReminderTest, TotpTest, ModularityTest, SettingsIT (+6 more)

### Community 6 - ".lead"
Cohesion: 0.08
Nodes (5): LeadConvertIT, LeadDuplicatesIT, LeadEditIT, LeadsFixture, LeadsIT

### Community 7 - "PaymentService"
Cohesion: 0.09
Nodes (16): org.springframework.web.multipart.MultipartFile, Payment, PaymentController, PaymentRepository, PaymentRequest, PaymentResponse, Attachment, FORM_2307 (+8 more)

### Community 8 - "org.springframework.transaction.annotation.Transactional"
Cohesion: 0.06
Nodes (21): java.sql.ResultSet, org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate, org.springframework.transaction.annotation.Transactional, AuditEventResponse, CustomerSummary, MovementResponse, ReceiptLineRequest, ReceiptRequest (+13 more)

### Community 9 - "ApprovalsIT"
Cohesion: 0.06
Nodes (21): ApprovalDecided, ApprovalRequest, ApprovalResponse, Link, ApprovalStatus, APPROVED, CANCELLED, PENDING (+13 more)

### Community 11 - "OrderService"
Cohesion: 0.10
Nodes (9): RequiredApproval, CustomerSalesProfile, DiscountType, AMOUNT, PERCENT, LineRequest, OrderRequest, OrderService (+1 more)

### Community 12 - "Customers"
Cohesion: 0.12
Nodes (7): ContactRequest, ContactResponse, CustomerContact, CustomerContactRepository, CustomerRequest, CustomerResponse, Customers

### Community 13 - "DealsFixture"
Cohesion: 0.09
Nodes (5): DealEditIT, DealLinkIT, DealReassignIT, DealsFixture, DealsIT

### Community 14 - "TwoFactorIT"
Cohesion: 0.13
Nodes (6): jakarta.servlet.http.Cookie, org.springframework.test.web.servlet.MvcResult, Base32, Totp, Base32Test, TwoFactorIT

### Community 15 - "TaxClass"
Cohesion: 0.06
Nodes (23): com.fasterxml.jackson.annotation.JsonInclude, TaxClass, VAT_EXEMPT, VATABLE, ZERO_RATED, TaxTreatment, REGULAR, VAT_EXEMPT (+15 more)

### Community 17 - "Part A — Backend (`crm`)"
Cohesion: 0.07
Nodes (27): Explicitly deferred (not in this plan), Global Constraints, Part A — Backend (`crm`), Part B — Frontend (`crm-dashboard-ui`), Redesign #2 Sales Lifecycle — Implementation Plan, Review Focus, Task 10: Payments — record, verify, reject, 2307, Task 11: Release, fulfil, cancel (+19 more)

### Community 18 - "jakarta.persistence.Entity"
Cohesion: 0.07
Nodes (20): DealOrder.Key, jakarta.persistence.Entity, jakarta.persistence.IdClass, jakarta.persistence.Table, org.hibernate.annotations.Immutable, AuditEvent, Product, DealOrder (+12 more)

### Community 19 - "UserAdminService"
Cohesion: 0.17
Nodes (4): InviteUserRequest, UpdateUserRequest, UserAdminService, UserResponse

### Community 20 - "SendQuoteIT"
Cohesion: 0.16
Nodes (10): jakarta.mail.Session, java.lang.annotation.Retention, java.lang.annotation.Target, org.springframework.mail.javamail.JavaMailSender, org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor, org.springframework.test.context.bean.override.mockito.MockitoBean, SecretsNotLoggedIT, MailIntegrationTest (+2 more)

### Community 21 - "Redesign #3 — Inventory & Fulfilment"
Cohesion: 0.07
Nodes (27): 1. Modules, 2. Data (Flyway, Postgres), 3. Stock rules, 4. Flows, 5. API, 6. Access, 7. Audit and notifications, 8. Frontend (+19 more)

### Community 22 - "PortalOrderService"
Cohesion: 0.15
Nodes (6): DeclineRequest, PortalOrderController, PortalOrderService, PortalPaymentResponse, ContactPrincipal, Override

### Community 23 - "java.time.YearMonth"
Cohesion: 0.05
Nodes (29): java.time.YearMonth, org.springframework.cache.annotation.Cacheable, org.springframework.jdbc.core.namedparam.MapSqlParameterSource, AdminInsights, Margin, MonthAmount, Operations, ProductRow (+21 more)

### Community 24 - "org.springframework.stereotype.Component"
Cohesion: 0.12
Nodes (9): org.springframework.context.event.EventListener, org.springframework.stereotype.Component, CustomerReassigned, DealReassignmentListener, OrderConfirmedListener, ReassignmentListener, TargetMilestones, OrderConfirmed (+1 more)

### Community 25 - "CurrentActor"
Cohesion: 0.07
Nodes (32): jakarta.validation.Validator, org.springframework.context.ApplicationEventPublisher, org.springframework.data.domain.Sort, org.springframework.stereotype.Service, Approvals, AuditLog, Logger, Pattern (+24 more)

### Community 26 - "ReportRange"
Cohesion: 0.10
Nodes (18): parse(), ReportBy, CUSTOMER, MONTH, PRODUCT, REP, Named, ProductSum (+10 more)

### Community 27 - "DeliveryService"
Cohesion: 0.16
Nodes (7): DeliveryOrderController, DeliveryLineResponse, DeliveryOrderResponse, DeliveryService, OrderDelivered, PickLineRequest, PickRequest

### Community 28 - "SettingKey"
Cohesion: 0.15
Nodes (20): bankAccounts(), field(), invalid(), number(), of(), SettingKey, APPROVAL_DISCOUNT_PERCENT_MAX, APPROVAL_WRITE_OFF_VALUE_MAX (+12 more)

### Community 30 - ".l"
Cohesion: 0.17
Nodes (4): LineStock, AllocatorIT, Line, ReservationsIT

### Community 31 - "LoginThrottle"
Cohesion: 0.13
Nodes (12): LoginRequest, LoginResponse, Outcome, LoginThrottle, Result, ALREADY_LOCKED, COUNTED, LOCKED (+4 more)

### Community 37 - ".createdOrder"
Cohesion: 0.07
Nodes (3): OrdersIT, ReleaseCancelIT, ResultActions

### Community 39 - "org.springframework.data.jpa.domain.Specification"
Cohesion: 0.18
Nodes (4): org.springframework.data.jpa.domain.Specification, Customer, CustomerScopes, OrderScopes

### Community 40 - "Inventory"
Cohesion: 0.10
Nodes (12): Allocator, Inventory, Override, Key, OrderStock, ReserveLine, StockLedger, StockLevel (+4 more)

### Community 42 - "org.springframework.http.ResponseEntity"
Cohesion: 0.07
Nodes (21): jakarta.validation.ConstraintViolationException, org.springframework.dao.OptimisticLockingFailureException, org.springframework.http.HttpHeaders, org.springframework.http.HttpStatusCode, org.springframework.http.ProblemDetail, org.springframework.http.ResponseEntity, org.springframework.security.access.AccessDeniedException, org.springframework.web.bind.annotation.ExceptionHandler (+13 more)

### Community 43 - "org.springframework.data.jpa.repository.JpaRepository"
Cohesion: 0.08
Nodes (17): org.springframework.data.jpa.repository.JpaRepository, org.springframework.data.jpa.repository.JpaSpecificationExecutor, org.springframework.data.jpa.repository.Lock, org.springframework.data.jpa.repository.Modifying, org.springframework.data.jpa.repository.Query, ApprovalRequestRepository, AuditEventRepository, ProductRepository (+9 more)

### Community 45 - "AttachmentService"
Cohesion: 0.07
Nodes (18): org.junit.jupiter.api.BeforeAll, org.testcontainers.containers.MinIOContainer, org.testcontainers.junit.jupiter.Testcontainers, Attachment, AttachmentAccess, InvoiceRef, InvoiceRow, OrderRow (+10 more)

### Community 46 - "FollowUpService"
Cohesion: 0.17
Nodes (4): FollowUp, FollowUpRequest, FollowUpResponse, FollowUpService

### Community 48 - "OrderAction"
Cohesion: 0.08
Nodes (25): org.junit.jupiter.params.provider.Arguments, org.junit.jupiter.params.provider.MethodSource, OrderAction, ACCEPT, CANCEL, CONFIRM, COVER_SHORTAGE, DECLINE (+17 more)

### Community 49 - "org.springframework.context.annotation.Bean"
Cohesion: 0.08
Nodes (22): org.springframework.boot.web.servlet.FilterRegistrationBean, org.springframework.context.annotation.Bean, org.springframework.context.annotation.Configuration, org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity, org.springframework.security.config.annotation.web.builders.HttpSecurity, org.springframework.security.config.annotation.web.configuration.EnableWebSecurity, org.springframework.security.web.SecurityFilterChain, org.springframework.session.web.http.CookieSerializer (+14 more)

### Community 50 - "TransferService"
Cohesion: 0.08
Nodes (19): AdjustmentSource, MANUAL, SHORT_PICK, TRANSIT_LOSS, ReceiveLineRequest, ReceiveRequest, StockTransfer, TransferController (+11 more)

### Community 51 - "Redesign #4 — POS"
Cohesion: 0.12
Nodes (15): Backend (`crm`), Decisions, Elsewhere, Endpoints — `PosController`, `ADMIN` and `SALES`, Frontend (`crm-dashboard-ui`), Goal, Other backend changes, Out of scope (+7 more)

### Community 52 - "NotificationType"
Cohesion: 0.07
Nodes (23): NotificationType, APPROVAL_DECIDED, APPROVAL_REQUESTED, DELIVERY_CREATED, FOLLOW_UPS_DUE, LOW_STOCK, ORDER_CONFIRMED, ORDER_DELIVERED (+15 more)

### Community 54 - "org.springframework.data.redis.core.StringRedisTemplate"
Cohesion: 0.13
Nodes (13): lombok.extern.slf4j.Slf4j, lombok.RequiredArgsConstructor, org.springframework.cache.annotation.CachingConfigurer, org.springframework.cache.annotation.EnableCaching, org.springframework.cache.interceptor.CacheErrorHandler, org.springframework.data.redis.core.script.RedisScript, org.springframework.data.redis.core.StringRedisTemplate, CacheConfig (+5 more)

### Community 57 - "Part A — Backend (`crm`)"
Cohesion: 0.09
Nodes (21): Explicitly deferred (not in this plan), Global Constraints, Part A — Backend (`crm`), Part B — Frontend (`crm-dashboard-ui`), Redesign #3 Inventory & Fulfilment — Implementation Plan, Review Focus, Task 10: Stock page, ledger, stock in and adjust, Task 11: Transfers (+13 more)

### Community 59 - "OrderResponse"
Cohesion: 0.14
Nodes (5): OrderController, OrderResponse, OrderStockMapper, SendQuoteResponse, TransitionRequest

### Community 60 - "V5__inventory_fulfilment.sql"
Cohesion: 0.13
Nodes (25): user_warehouse, warehouse, warehouse_code_uq, warehouse_one_default, product, product_name_idx, product_sku_uq, delivery_order (+17 more)

### Community 61 - ".create"
Cohesion: 0.10
Nodes (5): AdjustmentApprovalListener, AdjustmentRequest, AvailabilityResponse, WarehouseRequest, WarehouseResponse

### Community 62 - "Deals / opportunities (F-130) — design"
Cohesion: 0.18
Nodes (10): API, Approach, Data — Flyway `V15__deals.sql`, Deals / opportunities (F-130) — design, Decisions (from the user), Goal, Out of scope, Rules (+2 more)

### Community 63 - "jakarta.servlet.http.HttpServletResponse"
Cohesion: 0.12
Nodes (16): jakarta.servlet.FilterChain, jakarta.servlet.http.HttpServletResponse, java.util.regex.Pattern, org.springframework.beans.factory.annotation.Autowired, org.springframework.core.annotation.Order, org.springframework.util.AntPathMatcher, org.springframework.web.filter.OncePerRequestFilter, org.springframework.web.util.ContentCachingResponseWrapper (+8 more)

### Community 64 - "Follow-ups (F-141) — design"
Cohesion: 0.20
Nodes (9): API — `/api/follow-ups`, Data — Flyway `V11__follow_ups.sql`, Decisions (from the user), Follow-ups (F-141) — design, Goal, Out of scope, Rules, Screens (crm-dashboard-ui) (+1 more)

### Community 65 - "Part A — Backend (`crm`)"
Cohesion: 0.06
Nodes (31): Explicitly deferred (not in this plan), Global Constraints, Part A — Backend (`crm`), Part B — Frontend (`crm-dashboard-ui`), Redesign #1 Foundation — Implementation Plan, Review Focus, Task 10: Identity — TOTP 2FA and recovery codes, Task 11: Notifications (carried over, re-keyed to users) (+23 more)

### Community 68 - "HtmlMail"
Cohesion: 0.20
Nodes (6): org.springframework.transaction.event.TransactionalEventListener, IdentityMailer, InviteIssued, PortalMailer, Attachment, HtmlMail

### Community 69 - "LeadService"
Cohesion: 0.06
Nodes (24): com.fasterxml.jackson.annotation.JsonIgnore, jakarta.validation.constraints.AssertTrue, Candidate, ContactMatches, Match, Lead, LeadAssignRequest, LeadController (+16 more)

### Community 70 - "LoginIT"
Cohesion: 0.21
Nodes (3): javax.sql.DataSource, org.springframework.session.data.redis.RedisIndexedSessionRepository, LoginIT

### Community 72 - "PdfRenderer"
Cohesion: 0.27
Nodes (5): com.microsoft.playwright.Browser, com.microsoft.playwright.Playwright, jakarta.annotation.PreDestroy, org.thymeleaf.TemplateEngine, PdfRenderer

### Community 73 - "IntegrationTest.java"
Cohesion: 0.18
Nodes (14): org.springframework.boot.test.context.SpringBootTest, org.springframework.boot.test.context.TestConfiguration, org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc, org.springframework.context.ApplicationContextInitializer, org.springframework.context.ConfigurableApplicationContext, org.springframework.test.context.ActiveProfiles, org.springframework.test.context.ContextConfiguration, org.testcontainers.containers.PostgreSQLContainer (+6 more)

### Community 74 - ".find"
Cohesion: 0.12
Nodes (9): LabelController, LabelKind, DO, PR, TR, WH, Label, ScanController (+1 more)

### Community 76 - "ProductUnit"
Cohesion: 0.08
Nodes (20): ProductUnit, BOX, G, KG, L, M, ML, PACK (+12 more)

### Community 77 - "NotificationStreams"
Cohesion: 0.14
Nodes (11): org.springframework.data.redis.connection.Message, org.springframework.data.redis.connection.MessageListener, org.springframework.scheduling.annotation.Scheduled, org.springframework.web.servlet.mvc.method.annotation.SseEmitter, NotificationResponse, Override, NotificationStreams, StreamController (+3 more)

### Community 80 - "Backend (`crm/crm`)"
Cohesion: 0.12
Nodes (15): Backend (`crm/crm`), Dashboards (#4) Implementation Plan, Frontend (`crm-dashboard-ui`), Global Constraints, Review Focus, Task 10: End-to-end smoke, Task 1: Snapshots at confirm and the V10 migration, Task 2: Insights module, month parsing and sales targets (+7 more)

### Community 81 - "LogRedactorTest"
Cohesion: 0.13
Nodes (7): LogRedactor, Render, Create, Customer, Login, LogRedactorTest, Node

### Community 82 - "jakarta.servlet.http.HttpServletRequest"
Cohesion: 0.12
Nodes (11): jakarta.servlet.http.HttpServletRequest, org.springframework.security.core.Authentication, org.springframework.security.web.context.HttpSessionSecurityContextRepository, org.springframework.session.web.http.CookieHttpSessionIdResolver, HeaderOrCookieSessionIdResolver, Override, Principals, RecoveryCodesResponse (+3 more)

### Community 83 - "Backend (`crm/crm`)"
Cohesion: 0.11
Nodes (17): Backend (`crm/crm`), Frontend (`crm-dashboard-ui`), Global Constraints, Notification copy (exact), Notifications Implementation Plan, Review Focus, Task 10: Remove Inbox, Task 11: Graph refresh and final check (+9 more)

### Community 84 - ".record"
Cohesion: 0.05
Nodes (23): org.springframework.cache.annotation.CacheEvict, Deal, DealController, ResponseEntity, DealOrderRepository, DealRef, DealRequest, DealResponse (+15 more)

### Community 85 - "app_user"
Cohesion: 0.18
Nodes (20): app_setting, app_user, app_user_email_uq, approval_one_pending, approval_request, approval_subject_idx, audit_actor_idx, audit_at_idx (+12 more)

### Community 87 - "Frontend (`crm-dashboard-ui`)"
Cohesion: 0.13
Nodes (14): Backend (`crm/crm`), Frontend (`crm-dashboard-ui`), Global Constraints, POS (#4) Implementation Plan, Review Focus, Task 1: Sales channel and POS warehouse setting, Task 2: Upload proof after recording a payment, Task 3: POS product list (+6 more)

### Community 90 - "RedisTestSupport"
Cohesion: 0.07
Nodes (16): AssertTrue, org.springframework.context.annotation.AnnotationConfigApplicationContext, org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory, org.springframework.data.redis.connection.RedisConnectionFactory, org.springframework.data.redis.listener.RedisMessageListenerContainer, org.testcontainers.containers.GenericContainer, Override, RedisListenerConfig (+8 more)

### Community 92 - "ServiceCallLoggingIT"
Cohesion: 0.12
Nodes (11): org.junit.jupiter.api.extension.ExtendWith, org.springframework.boot.test.system.CapturedOutput, org.springframework.boot.test.system.OutputCaptureExtension, org.springframework.context.annotation.Import, ServiceCallLoggingIT.Probes, AuditLogLoggingIT, AccessLogFilterIT, LoggingProbeService (+3 more)

### Community 93 - "Design"
Cohesion: 0.12
Nodes (16): 1. Request line — `shared/AccessLogFilter`, 2. Business step line — `AuditLog.record`, 3. Service calls — `shared/ServiceCallLogging`, 4. Redaction — `shared/LogRedactor`, 5. Background work, 6. Changing detail at runtime, 7. Files — `logback-spring.xml`, 8. UI (crm-dashboard-ui) (+8 more)

### Community 94 - "Notification"
Cohesion: 0.28
Nodes (5): org.springframework.data.domain.Page, org.springframework.data.domain.Pageable, StockReceiptRepository, Notification, NotificationRepository

### Community 95 - "DeliveryStatus"
Cohesion: 0.22
Nodes (5): DeliveryStatus, CANCELLED, DELIVERED, DISPATCHED, PICKING

### Community 97 - "SessionController"
Cohesion: 0.25
Nodes (3): org.springframework.web.bind.annotation.DeleteMapping, SessionController, SessionResponse

### Community 98 - "Review Focus"
Cohesion: 0.17
Nodes (11): Global Constraints, Redis Phase Implementation Plan, Review Focus, Task 0: Commit the MinIO work, Task 1: Redis wiring and token revocation (R-01, R-02, R-09 part), Task 2: Rate limiting (R-03), Task 3: Single-flight locks on customer links and staff accept (R-04), Task 4: Run the quotation expiry job on one instance (R-05) (+3 more)

### Community 100 - "Review Focus"
Cohesion: 0.15
Nodes (12): Deals (F-130) Implementation Plan, Global Constraints, Review Focus, Task 1: Table, create, list, get, summary, Task 2: Edit and stages (except Won), Task 3: Linking orders, Task 4: Won — automatic and by hand, Task 5: Follow the customer on reassignment (+4 more)

### Community 102 - "org.springframework.web.bind.annotation.GetMapping"
Cohesion: 0.05
Nodes (37): org.springframework.boot.logging.LoggingSystem, org.springframework.http.HttpStatus, org.springframework.security.access.prepost.PreAuthorize, org.springframework.security.web.csrf.CsrfToken, org.springframework.validation.annotation.Validated, org.springframework.web.bind.annotation.GetMapping, org.springframework.web.bind.annotation.PostMapping, org.springframework.web.bind.annotation.PutMapping (+29 more)

### Community 105 - "ServiceCallLogging.java"
Cohesion: 0.14
Nodes (9): java.lang.reflect.Method, org.aopalliance.intercept.MethodInvocation, org.springframework.aop.Advisor, org.springframework.aop.Pointcut, org.springframework.beans.factory.config.BeanPostProcessor, org.springframework.context.annotation.Role, org.springframework.core.ParameterNameDiscoverer, JobMdc (+1 more)

### Community 106 - "PaymentMethod"
Cohesion: 0.25
Nodes (7): PaymentMethod, BANK_TRANSFER, CARD, CASH, CHECK, GCASH, OTHER

### Community 108 - "RateLimitFilterIT"
Cohesion: 0.18
Nodes (8): org.junit.jupiter.params.ParameterizedTest, org.junit.jupiter.params.provider.CsvSource, org.junit.jupiter.params.provider.ValueSource, org.springframework.mock.web.MockFilterChain, org.springframework.mock.web.MockHttpServletResponse, Override, IdempotencyFilterPathsTest, RateLimitFilterIT

### Community 110 - "Redesign #1 — Foundation"
Cohesion: 0.07
Nodes (28): 10. Testing, 11. Deployment changes, 1. Backend module layout, 2. Data conventions (Postgres), 3. Identity & sessions, 4. Authorization & scoping, 5. Approvals, 6. Audit (+20 more)

### Community 111 - "sales_order"
Cohesion: 0.18
Nodes (15): order_revision, payment_status_verified_idx, sales_order_rep_idx, sales_target, document_counter, invoice, invoice_one_active, invoice_order_idx (+7 more)

### Community 113 - "PaymentTerms"
Cohesion: 0.05
Nodes (25): CustomerType, BUSINESS, INDIVIDUAL, PaymentTerms, DUE_ON_RECEIPT, NET_15, NET_30, NET_60 (+17 more)

### Community 114 - "Review Focus"
Cohesion: 0.14
Nodes (13): Global Constraints, Leads (F-120) Implementation Plan, Review Focus, Task 10: e2e, Task 1: Migration, lead sources on customers, Task 2: Lead sources API, Task 3: Leads module — create, list, get, counts, Task 4: Edit, status, assign (+5 more)

### Community 116 - ".issue"
Cohesion: 0.11
Nodes (11): jakarta.persistence.EntityManager, BootstrapAdmin, Subject, TokenPurpose, INVITE, PASSWORD_RESET, PAYMENT_PROOF, PORTAL_LOGIN (+3 more)

### Community 117 - "Leads (F-120) — design"
Cohesion: 0.18
Nodes (10): API, Approach, Data — Flyway `V14__leads.sql`, Decisions (from the user), Goal, Leads (F-120) — design, Out of scope, Rules (+2 more)

### Community 122 - "Backend (`crm`)"
Cohesion: 0.12
Nodes (15): Backend (`crm`), Frontend (`crm-dashboard-ui`), Global Constraints, Retail POS Implementation Plan, Review Focus, Task 10: Payment, success view and printed receipt, Task 1: Schema, enums and response fields, Task 2: `InventoryService.sellForPos` (+7 more)

### Community 123 - "Review Focus"
Cohesion: 0.22
Nodes (8): Attachments (M-08) Implementation Plan, Global Constraints, Review Focus, Task 1: StorageService — detect, put, presign for download, Task 2: Table, entity and access, Task 3: Upload, list, download, archive, Task 4: UI — FilesSection, Task 5: Place it, and e2e

### Community 125 - "Retail POS — Design"
Cohesion: 0.11
Nodes (18): Backend (`crm`), Backend tests, Checkout flow, Components — `src/components/pos/`, Decisions, Emailed, Endpoints — new `PosController` at `/api/v1/pos`, `@PreAuthorize("hasAnyAuthority('ADMIN','SALES')")`, Frontend checks (+10 more)

### Community 126 - "Redesign #2 — Sales lifecycle"
Cohesion: 0.06
Nodes (33): 1. Modules, 2. Data model, 3. Order lifecycle, 4. Money, invoices and payments, 5. Access and API, 6. Screens (`crm-dashboard-ui`), 7. Testing, approvals (+25 more)

### Community 128 - "Review Focus"
Cohesion: 0.22
Nodes (8): Global Constraints, Reports (F-172) Implementation Plan, Review Focus, Task 1: Range parsing and error code, Task 2: Month, rep and customer reports plus endpoint, Task 3: Product report with pro-rata split, Task 4: Reports page, Task 5: End-to-end smoke

### Community 129 - "FollowUpQueries"
Cohesion: 0.21
Nodes (3): FollowUpCounts, Due, FollowUpQueries

### Community 130 - "LeadSourceBackfillIT"
Cohesion: 0.35
Nodes (3): org.flywaydb.core.Flyway, org.springframework.jdbc.datasource.DriverManagerDataSource, LeadSourceBackfillIT

### Community 132 - "TargetService"
Cohesion: 0.23
Nodes (4): TargetController, TargetRequest, TargetRow, TargetService

### Community 133 - "MinioHealthIndicator"
Cohesion: 0.32
Nodes (4): org.springframework.boot.health.contributor.Health, org.springframework.boot.health.contributor.HealthIndicator, Override, MinioHealthIndicator

### Community 134 - "Attachments (M-08) — design"
Cohesion: 0.20
Nodes (9): API — `/api/attachments`, Attachments (M-08) — design, Data — Flyway `V13__attachments.sql`, Decisions (from the user), Goal, Out of scope, Rules, Screens (crm-dashboard-ui) (+1 more)

### Community 135 - "SecurityMatrixTest"
Cohesion: 0.17
Nodes (8): org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping, SecurityMatrixTest, Who, ADMIN, ANON, CUSTOMER, SALES, WAREHOUSE

### Community 136 - "package.json"
Cohesion: 0.22
Nodes (8): tailwindcss, @tailwindcss/cli, devDependencies, tailwindcss, @tailwindcss/cli, private, scripts, build

### Community 137 - "FollowUpsIT"
Cohesion: 0.07
Nodes (6): FollowUpReminder, FollowUpEditIT, PlatformTransactionManager, FollowUpReassignIT, FollowUpReminderIT, FollowUpsIT

### Community 140 - "Review Focus"
Cohesion: 0.17
Nodes (11): Follow-ups (F-141) Implementation Plan, Global Constraints, Review Focus, Task 1: Table, entity and the Manila day, Task 2: Create, list and counts, Task 3: Edit, done and reopen, Task 4: Follow the customer on reassignment, Task 5: Morning bell (+3 more)

### Community 142 - "lead"
Cohesion: 0.42
Nodes (8): lead, lead_customer_idx, lead_email_idx, lead_phone_idx, lead_rep_status_idx, lead_source, lead_source_name_uq, lead_status_idx

### Community 144 - "RedisSessionAdmin"
Cohesion: 0.36
Nodes (4): org.springframework.session.FindByIndexNameSessionRepository, org.springframework.session.Session, Override, RedisSessionAdmin

### Community 145 - "AppUser"
Cohesion: 0.12
Nodes (7): jakarta.persistence.PreUpdate, java.security.SecureRandom, javax.crypto.SecretKey, AppUser, RecoveryCodeRepository, TotpService, EncryptionService

### Community 146 - "migrate.py"
Cohesion: 0.43
Nodes (6): copy_legacy(), env(), main(), One-time MySQL -> Postgres migration (P5-1). See deploy/migrate/CUTOVER.md.…, Records which referenced uploads exist on disk, so proofs can become…, stat_files()

### Community 147 - "Review Focus"
Cohesion: 0.22
Nodes (8): Events (decided), Global Constraints, In-App Notifications Implementation Plan, Review Focus, Task 1: Live delivery — `NotificationStreams` + Redis fan-out, Task 2: Stored notifications — entity, migration, `NotificationService`, Task 3: REST + SSE endpoints, Task 4: Fire the events

### Community 148 - "Review Focus"
Cohesion: 0.20
Nodes (9): Global Constraints, Logging Across the App Implementation Plan, Review Focus, Task 1: LogRedactor, Task 2: Request line and business-step line, Task 3: Service calls and background IDs, Task 4: Files, levels and the loggers endpoint, Task 5: Secrets never logged (+1 more)

### Community 149 - "Redesign #4 — Dashboards"
Cohesion: 0.12
Nodes (15): API — module `insights`, Caching, Cost snapshot, Data model — `V10__insights.sql`, Decisions, Figures, Frontend — `crm-dashboard-ui`, `GET /api/insights/admin` — role ADMIN (+7 more)

### Community 150 - "Reports (F-172)"
Cohesion: 0.29
Nodes (6): API, in module `insights`, Decisions, Frontend, in `crm-dashboard-ui`, Goal, Reports (F-172), Testing

### Community 151 - "MovementType"
Cohesion: 0.29
Nodes (6): MovementType, ADJUSTMENT, DISPATCH, RECEIPT, TRANSFER_IN, TRANSFER_OUT

### Community 152 - "deal"
Cohesion: 0.52
Nodes (6): deal, deal_customer_idx, deal_order, deal_order_order_uq, deal_rep_stage_idx, deal_stage_idx

### Community 153 - "Notifications — design (2026-11-12)"
Cohesion: 0.17
Nodes (11): 1. Notification catalogue, 2. Notifications page, bell, routing (frontend), 3. Approvals on record pages, 4. Backend changes, 5. Testing, Backend, Frontend, Goal (+3 more)

### Community 157 - "org.springframework.core.env.Environment"
Cohesion: 0.53
Nodes (3): jakarta.annotation.PostConstruct, org.springframework.core.env.Environment, EncryptionSecretsCheck

### Community 158 - "CrmApplication"
Cohesion: 0.53
Nodes (4): org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration, org.springframework.scheduling.annotation.EnableScheduling, CrmApplication

### Community 163 - "ProblemDetailAdviceTest"
Cohesion: 0.20
Nodes (3): Body, ProblemDetailAdviceTest, TestController

### Community 164 - "Production cutover (P5-2)"
Cohesion: 0.33
Nodes (5): Afterwards, Before the window, Production cutover (P5-2), Rollback, Steps

### Community 166 - "follow_up"
Cohesion: 0.70
Nodes (4): follow_up, follow_up_customer_idx, follow_up_open_idx, follow_up_order_idx

### Community 167 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 169 - "org.junit.jupiter.api.AfterEach"
Cohesion: 0.07
Nodes (4): org.junit.jupiter.api.AfterEach, ScanIT, SessionTransportIT, ApprovalPushAfterCommitIT

### Community 172 - "transform.sql"
Cohesion: 0.17
Nodes (6): l, o, pg_temp.sys(), pg_temp.uid(), user_role, user_role_role_idx

### Community 191 - "LeadSources"
Cohesion: 0.27
Nodes (3): LeadSourceRequest, LeadSourceResponse, LeadSources

### Community 194 - "Bucket"
Cohesion: 0.17
Nodes (8): Bucket, DONE, OVERDUE, TODAY, UPCOMING, FollowUpDay, parse(), FollowUpDayTest

### Community 195 - "StaffPrincipal"
Cohesion: 0.16
Nodes (9): org.springframework.http.MediaType, org.springframework.security.authentication.UsernamePasswordAuthenticationToken, org.springframework.security.core.AuthenticatedPrincipal, org.springframework.security.core.authority.SimpleGrantedAuthority, org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, Override, StaffPrincipal (+1 more)

## Knowledge Gaps
- **649 isolated node(s):** `o`, `l`, `01-roles.sh script`, `PENDING`, `APPROVED` (+644 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 887 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **80 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Role` connect `org.springframework.jdbc.core.JdbcTemplate` to `Scope`, `ErrorCode`, `TargetService`, `org.junit.jupiter.api.Test`, `.lead`, `LogLevelIT`, `org.springframework.transaction.annotation.Transactional`, `ApprovalsIT`, `FollowUpsIT`, `UserAdminIT`, `DealsFixture`, `TwoFactorIT`, `AppUser`, `jakarta.persistence.Entity`, `UserAdminService`, `SendQuoteIT`, `CurrentActor`, `ReportRange`, `LoginThrottle`, `Inventory`, `.as`, `org.springframework.data.jpa.repository.JpaRepository`, `AttachmentService`, `NotificationType`, `.csrf`, `StaffPrincipal`, `LoginIT`, `SessionManagementIT`, `CatalogIT`, `AuditLogIT`, `.as`, `.product`, `org.springframework.web.bind.annotation.GetMapping`, `WarehousesIT`, `CustomerScopingIT`, `AttachmentsFixture`, `LeadSourcesIT`?**
  _High betweenness centrality (0.055) - this node is a cross-community bridge._
- **Why does `ErrorCode` connect `ErrorCode` to `org.springframework.jdbc.core.JdbcTemplate`, `StaffPrincipal`, `org.springframework.web.bind.annotation.GetMapping`, `org.springframework.transaction.annotation.Transactional`, `org.springframework.http.ResponseEntity`, `AttachmentService`, `org.springframework.context.annotation.Bean`, `jakarta.servlet.http.HttpServletRequest`, `CurrentActor`, `SettingKey`, `jakarta.servlet.http.HttpServletResponse`?**
  _High betweenness centrality (0.044) - this node is a cross-community bridge._
- **Why does `AuditAction` connect `AuditAction` to `ErrorCode`, `org.springframework.web.bind.annotation.GetMapping`, `PaymentService`, `org.springframework.transaction.annotation.Transactional`, `AttachmentService`, `AppUser`, `jakarta.persistence.Entity`, `.record`, `CurrentActor`?**
  _High betweenness centrality (0.033) - this node is a cross-community bridge._
- **What connects `o`, `l`, `01-roles.sh script` to the rest of the system?**
  _649 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Scope` be split into smaller, more focused modules?**
  _Cohesion score 0.08200290275761973 - nodes in this community are weakly interconnected._
- **Should `org.springframework.jdbc.core.JdbcTemplate` be split into smaller, more focused modules?**
  _Cohesion score 0.08735521235521236 - nodes in this community are weakly interconnected._
- **Should `ErrorCode` be split into smaller, more focused modules?**
  _Cohesion score 0.03446630888491354 - nodes in this community are weakly interconnected._