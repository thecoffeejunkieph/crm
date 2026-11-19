# Graph Report - crm  (2026-11-20)

## Corpus Check
- 647 files · ~295,507 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 17 file(s) not represented in the graph (top: .properties 6, (none) 3, .example 2)

## Summary
- 5559 nodes · 19927 edges · 233 communities (151 shown, 82 thin omitted)
- Extraction: 85% EXTRACTED · 15% INFERRED · 0% AMBIGUOUS · INFERRED: 2976 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `e31b9547`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- .as
- AttachmentsIT
- org.springframework.jdbc.core.JdbcTemplate
- UserAdminIT.java
- AuditAction
- org.junit.jupiter.api.Test
- .lead
- .create
- PageQuery
- TaxClass
- UserAdminIT
- .customer
- AppUser
- DealsFixture
- TwoFactorIT
- HtmlMail
- PortalAuthIT
- Part A — Backend (`crm`)
- jakarta.persistence.Entity
- RefundBadge
- ServiceCallLoggingIT
- Redesign #3 — Inventory & Fulfilment
- SettingKey
- .followUp
- DealService
- ProductCategories
- ReportRange
- DeliveryService
- SettingsIT
- OrderStockIT
- .receive
- AdminInsights
- MeController
- ProblemDetailAdvice
- Sheets
- RefundsIT
- .at
- .createdOrder
- FollowUpsIT
- org.springframework.stereotype.Component
- Inventory
- Catalog
- OrderService.java
- org.springframework.data.jpa.repository.JpaRepository
- DeliveryOrdersIT
- Scope
- FollowUpService
- CommissionTest
- OrderStatus
- TransfersIT
- TransferService
- Redesign #4 — POS
- lombok.extern.slf4j.Slf4j
- org.springframework.transaction.event.TransactionalEventListener
- ProductUnit
- .csrf
- FollowUpEditIT
- Part A — Backend (`crm`)
- PaymentsIT
- LeadDuplicates
- V5__inventory_fulfilment.sql
- UI polish bundle — design (2026-11-17)
- Deals / opportunities (F-130) — design
- InsightsQueries
- Follow-ups (F-141) — design
- Part A — Backend (`crm`)
- .update
- LoginIT
- .diff
- TotpService.java
- SessionManagementIT
- CustomerSalesController
- .onHand
- PaymentService
- .delivered
- Automatic follow-ups — design (2026-11-17)
- NotificationStreams
- CatalogIT
- NotificationType
- Backend (`crm/crm`)
- .record
- jakarta.servlet.http.HttpServletRequest
- Backend (`crm/crm`)
- QuoteExpiringRule
- app_user
- .check
- Frontend (`crm-dashboard-ui`)
- ServiceCallLogging.java
- Approvals
- Review Focus
- CustomerStatusMailsIT
- CoverShortageTest
- Design
- AuditLog
- .run
- InventoryFixture
- NotificationsIT
- Review Focus
- ErrorCode
- Review Focus
- SettingKey.java
- IntegrationTest.java
- org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
- WarehousesIT
- OrderService
- RedisTestSupport
- LogRedactorTest
- RateLimitFilterIT
- WarehouseInsights
- Redesign #1 — Foundation
- sales_order
- DealWonIT
- java.time.YearMonth
- Review Focus
- org.springframework.data.redis.core.StringRedisTemplate
- OneTimeTokens
- Leads (F-120) — design
- PaymentOverdueRule
- CustomerScopingIT
- .json
- LeadSourcesIT
- Backend (`crm`)
- Review Focus
- Refunds — design (2026-11-16)
- Retail POS — Design
- Redesign #2 — Sales lifecycle
- Customer status emails — design (2026-11-18)
- Review Focus
- RedisSessionAdmin
- LeadSourceBackfillIT
- DealOrder
- NotificationStreamsIT
- Attachments (M-08) — design
- SecurityMatrixTest
- package.json
- StockReadsIT
- CustomerSourceIT
- CustomerContact
- Review Focus
- Review Focus
- lead
- org.springframework.web.filter.OncePerRequestFilter
- org.junit.jupiter.params.ParameterizedTest
- .receive
- migrate.py
- Review Focus
- Review Focus
- Redesign #4 — Dashboards
- Reports (F-172)
- MovementType
- deal
- Notifications — design (2026-11-12)
- Review Focus
- FollowUpRule
- org.springframework.jdbc.core.namedparam.MapSqlParameterSource
- ReceiptsIT
- CrmApplication
- MinioHealthIndicator
- LogLevelIT
- PdfText
- org.springframework.context.annotation.Bean
- FirstOrderRuleIT
- Production cutover (P5-2)
- Candidate
- follow_up
- gradlew
- .encode
- RestockRule.java
- Review Focus
- DocsDisabledTest
- transform.sql
- DocumentController
- 01-roles.sh
- V12__follow_up_reminder_run.sql
- LabelService
- SalesPermissions
- DealStalledRuleIT
- org.springframework.transaction.annotation.Transactional
- CustomerPermissions
- StaffPrincipal
- RefundDisposition
- StorageService
- .dueAfterDays
- LeadSources
- QrCodes
- AuditLogIT
- PaymentOverdueRuleIT
- TargetService
- AutoConfirmIT
- RefundService
- AccessLogFilter
- .version
- SalesTime
- org.springframework.web.bind.annotation.ResponseStatus
- DealStalledRule
- CurrentActor
- FakeRule
- QuoteChaseRule
- RuleSettings
- EncryptionSecretsCheckTest
- TestController
- org.springframework.context.annotation.Configuration
- org.junit.jupiter.api.AfterEach
- LogLevelController
- ApprovalPushAfterCommitIT
- org.springframework.http.ResponseEntity
- V26__product_category.sql
- attachment
- user_role

## God Nodes (most connected - your core abstractions)
1. `Role` - 196 edges
2. `IntegrationTest` - 173 edges
3. `Scope` - 151 edges
4. `AuditAction` - 129 edges
5. `AuditLog` - 126 edges
6. `CurrentActor` - 124 edges
7. `ErrorCode` - 114 edges
8. `Customers` - 104 edges
9. `DomainException` - 97 edges
10. `PageQuery` - 81 edges

## Surprising Connections (you probably didn't know these)
- `pg_temp.sys()` --reads_from--> `app_user`  [EXTRACTED]
  deploy/migrate/transform.sql → src/main/resources/db/migration/V1__baseline.sql
- `pg_temp.uid()` --reads_from--> `app_user`  [EXTRACTED]
  deploy/migrate/transform.sql → src/main/resources/db/migration/V1__baseline.sql
- `sales_target` --references--> `app_user`  [EXTRACTED]
  src/main/resources/db/migration/V10__insights.sql → src/main/resources/db/migration/V1__baseline.sql
- `product_category_id_idx` --indexes--> `product`  [EXTRACTED]
  src/main/resources/db/migration/V26__product_category.sql → src/main/resources/db/migration/V2__catalog_sales.sql
- `ApprovalController` --references--> `Approvals`  [EXTRACTED]
  src/main/java/ph/thecoffeejunkie/crm/approvals/ApprovalController.java → src/main/java/ph/thecoffeejunkie/crm/approvals/Approvals.java

## Import Cycles
- None detected.

## Communities (233 total, 82 thin omitted)

### Community 1 - "AttachmentsIT"
Cohesion: 0.12
Nodes (4): AttachmentAccessIT, AttachmentsAllOrNothingIT, AttachmentsFixture, AttachmentsIT

### Community 2 - "org.springframework.jdbc.core.JdbcTemplate"
Cohesion: 0.09
Nodes (31): org.junit.jupiter.api.BeforeEach, org.springframework.cache.CacheManager, org.springframework.context.ApplicationContext, org.springframework.jdbc.core.JdbcTemplate, org.springframework.jdbc.datasource.DriverManagerDataSource, org.springframework.test.context.event.ApplicationEvents, org.springframework.test.context.event.RecordApplicationEvents, org.springframework.test.web.servlet.MockMvc (+23 more)

### Community 3 - "UserAdminIT.java"
Cohesion: 0.12
Nodes (13): jakarta.annotation.PostConstruct, jakarta.mail.Session, java.lang.annotation.Retention, java.lang.annotation.Target, org.springframework.core.env.Environment, org.springframework.mail.javamail.JavaMailSender, org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor, org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder (+5 more)

### Community 4 - "AuditAction"
Cohesion: 0.02
Nodes (84): AuditAction, ACCOUNT_LOCKED, APPROVAL_CANCELLED, APPROVAL_DECIDED, APPROVAL_REQUESTED, ARCHIVED, ASSIGNMENT_CHANGED, ATTACHMENT_ADDED (+76 more)

### Community 5 - "org.junit.jupiter.api.Test"
Cohesion: 0.03
Nodes (16): org.junit.jupiter.api.Test, AuditChangesTest, StorageDetectTest, FollowUpDayTest, FollowUpReminderTest, TotpTest, ModularityTest, NoSpringCsrfPostProcessorTest (+8 more)

### Community 6 - ".lead"
Cohesion: 0.08
Nodes (5): LeadConvertIT, LeadDuplicatesIT, LeadEditIT, LeadsFixture, LeadsIT

### Community 7 - ".create"
Cohesion: 0.09
Nodes (15): AdjustmentMode, COUNTED, DELTA, AdjustmentRequest, AdjustmentResponse, AdjustmentSource, MANUAL, RETURN_DAMAGED (+7 more)

### Community 8 - "PageQuery"
Cohesion: 0.07
Nodes (15): org.springframework.data.domain.Page, org.springframework.data.domain.Pageable, org.springframework.data.domain.Sort, AuditEventResponse, ProductInfo, CustomerSummary, MovementResponse, Missing2307Row (+7 more)

### Community 9 - "TaxClass"
Cohesion: 0.09
Nodes (10): TaxClass, VAT_EXEMPT, VATABLE, ZERO_RATED, Balances, Line, OrderTotals, Result (+2 more)

### Community 12 - "AppUser"
Cohesion: 0.07
Nodes (14): org.springframework.security.crypto.password.PasswordEncoder, AppUser, LoginRequest, LoginService, Outcome, LoginThrottle, Result, ALREADY_LOCKED (+6 more)

### Community 13 - "DealsFixture"
Cohesion: 0.10
Nodes (5): DealEditIT, DealLinkIT, DealReassignIT, DealsFixture, DealsIT

### Community 14 - "TwoFactorIT"
Cohesion: 0.16
Nodes (3): org.springframework.test.web.servlet.MvcResult, Totp, TwoFactorIT

### Community 15 - "HtmlMail"
Cohesion: 0.09
Nodes (11): DeliveryDispatched, DeliveryMailView, Line, OrderDelivered, IdentityMailer, InviteIssued, PortalMailer, Mail (+3 more)

### Community 17 - "Part A — Backend (`crm`)"
Cohesion: 0.07
Nodes (27): Explicitly deferred (not in this plan), Global Constraints, Part A — Backend (`crm`), Part B — Frontend (`crm-dashboard-ui`), Redesign #2 Sales Lifecycle — Implementation Plan, Review Focus, Task 10: Payments — record, verify, reject, 2307, Task 11: Release, fulfil, cancel (+19 more)

### Community 18 - "jakarta.persistence.Entity"
Cohesion: 0.07
Nodes (17): jakarta.persistence.Entity, jakarta.persistence.PreUpdate, jakarta.persistence.Table, org.hibernate.annotations.Immutable, AuditEvent, DeliveryOrderLine, RecoveryCode, StockMovement (+9 more)

### Community 19 - "RefundBadge"
Cohesion: 0.40
Nodes (5): of(), RefundBadge, FULL, NONE, PARTIAL

### Community 20 - "ServiceCallLoggingIT"
Cohesion: 0.09
Nodes (12): org.junit.jupiter.api.extension.ExtendWith, org.springframework.boot.test.system.CapturedOutput, org.springframework.boot.test.system.OutputCaptureExtension, org.springframework.context.annotation.Import, ServiceCallLoggingIT.Probes, JobMdc, AuditLogLoggingIT, AccessLogFilterIT (+4 more)

### Community 21 - "Redesign #3 — Inventory & Fulfilment"
Cohesion: 0.07
Nodes (27): 1. Modules, 2. Data (Flyway, Postgres), 3. Stock rules, 4. Flows, 5. API, 6. Access, 7. Audit and notifications, 8. Frontend (+19 more)

### Community 22 - "SettingKey"
Cohesion: 0.07
Nodes (30): of(), SettingKey, APPROVAL_DISCOUNT_PERCENT_MAX, APPROVAL_WRITE_OFF_VALUE_MAX, COMPANY_ADDRESS, COMPANY_NAME, COMPANY_TIN, FOLLOW_UPS_AUTO_DEAL_STALLED_ENABLED (+22 more)

### Community 24 - "DealService"
Cohesion: 0.05
Nodes (24): org.springframework.data.jpa.domain.Specification, Deal, DealDeleted, DealRef, DealRequest, DealResponse, DealScopes, DealService (+16 more)

### Community 25 - "ProductCategories"
Cohesion: 0.13
Nodes (7): CatalogPermissions, ProductCategories, ProductCategory, ProductCategoryController, ProductCategoryRepository, ProductCategoryRequest, ProductCategoryResponse

### Community 26 - "ReportRange"
Cohesion: 0.10
Nodes (18): parse(), ReportBy, CUSTOMER, MONTH, PRODUCT, REP, Named, ProductSum (+10 more)

### Community 27 - "DeliveryService"
Cohesion: 0.13
Nodes (12): DeliveryOrder, DeliveryOrderController, DeliveryLineResponse, DeliveryOrderResponse, DeliveryService, DeliveryStatus, CANCELLED, DELIVERED (+4 more)

### Community 30 - ".receive"
Cohesion: 0.18
Nodes (4): OrderStock, ReserveLine, AllocatorIT, ReservationsIT

### Community 31 - "AdminInsights"
Cohesion: 0.25
Nodes (11): AdminInsights, Margin, Operations, ProductRow, CustomerAmount, DayAmount, NeedsAction, OrderRef (+3 more)

### Community 32 - "MeController"
Cohesion: 0.32
Nodes (4): ChangePasswordRequest, MeController, UpdateMeRequest, MeResponse

### Community 33 - "ProblemDetailAdvice"
Cohesion: 0.19
Nodes (13): jakarta.validation.ConstraintViolationException, org.springframework.dao.OptimisticLockingFailureException, org.springframework.http.HttpHeaders, org.springframework.http.HttpStatusCode, org.springframework.http.ProblemDetail, org.springframework.security.access.AccessDeniedException, org.springframework.web.bind.annotation.ExceptionHandler, org.springframework.web.bind.annotation.RestControllerAdvice (+5 more)

### Community 34 - "Sheets"
Cohesion: 0.11
Nodes (7): jakarta.validation.Validator, java.util.zip.ZipOutputStream, ImportResult, RowError, Sheets, Req, SheetsTest

### Community 36 - ".at"
Cohesion: 0.05
Nodes (6): AdminInsightsIT, ReportsIT, SalesInsightsIT, TargetMilestonesIT, TargetsIT, WarehouseInsightsIT

### Community 37 - ".createdOrder"
Cohesion: 0.07
Nodes (4): ConfirmIT, OrdersIT, ResultActions, SendQuoteIT

### Community 39 - "org.springframework.stereotype.Component"
Cohesion: 0.08
Nodes (18): com.microsoft.playwright.Browser, com.microsoft.playwright.Playwright, jakarta.annotation.PreDestroy, org.springframework.boot.autoconfigure.condition.ConditionalOnProperty, org.springframework.scheduling.annotation.Scheduled, org.springframework.stereotype.Component, org.thymeleaf.TemplateEngine, AutoFollowUpsSchedule (+10 more)

### Community 40 - "Inventory"
Cohesion: 0.12
Nodes (10): Allocator, Inventory, Override, Key, StockLedger, StockLevel, StockLevelRepository, StockMovementRepository (+2 more)

### Community 41 - "Catalog"
Cohesion: 0.14
Nodes (6): Catalog, Product, ProductController, ProductRequest, ProductResponse, ProductSheets

### Community 42 - "OrderService.java"
Cohesion: 0.13
Nodes (16): LineStock, OrderAction, ACCEPT, CANCEL, CONFIRM, COVER_SHORTAGE, DECLINE, EDIT (+8 more)

### Community 43 - "org.springframework.data.jpa.repository.JpaRepository"
Cohesion: 0.05
Nodes (24): org.springframework.data.jpa.repository.JpaRepository, org.springframework.data.jpa.repository.JpaSpecificationExecutor, org.springframework.data.jpa.repository.Lock, org.springframework.data.jpa.repository.Modifying, org.springframework.data.jpa.repository.Query, AuditEventRepository, ProductRepository, CustomerRepository (+16 more)

### Community 45 - "Scope"
Cohesion: 0.09
Nodes (15): Attachment, AttachmentAccess, InvoiceRef, InvoiceRow, OrderRow, AttachmentRepository, AttachmentResponse, AttachmentService (+7 more)

### Community 46 - "FollowUpService"
Cohesion: 0.09
Nodes (17): FollowUp, FollowUpCounts, Bucket, DONE, OVERDUE, TODAY, UPCOMING, FollowUpDay (+9 more)

### Community 48 - "OrderStatus"
Cohesion: 0.15
Nodes (11): org.junit.jupiter.params.provider.Arguments, org.junit.jupiter.params.provider.MethodSource, OrderStateMachine, OrderStatus, CANCELLED, CONFIRMED, DRAFT, FULFILLED (+3 more)

### Community 50 - "TransferService"
Cohesion: 0.12
Nodes (14): ReceiveLineRequest, ReceiveRequest, StockTransfer, TransferController, TransferLineRequest, TransferRequest, TransferLineResponse, TransferResponse (+6 more)

### Community 51 - "Redesign #4 — POS"
Cohesion: 0.12
Nodes (15): Backend (`crm`), Decisions, Elsewhere, Endpoints — `PosController`, `ADMIN` and `SALES`, Frontend (`crm-dashboard-ui`), Goal, Other backend changes, Out of scope (+7 more)

### Community 52 - "lombok.extern.slf4j.Slf4j"
Cohesion: 0.16
Nodes (7): lombok.extern.slf4j.Slf4j, org.springframework.boot.context.event.ApplicationReadyEvent, org.springframework.context.event.EventListener, CustomerReassigned, DealReassignmentListener, ReassignmentListener, BootstrapAdmin

### Community 53 - "org.springframework.transaction.event.TransactionalEventListener"
Cohesion: 0.19
Nodes (9): org.springframework.transaction.event.TransactionalEventListener, ApprovalDecided, ApprovalStatus, APPROVED, CANCELLED, PENDING, REJECTED, AdjustmentApprovalListener (+1 more)

### Community 54 - "ProductUnit"
Cohesion: 0.09
Nodes (21): com.fasterxml.jackson.annotation.JsonInclude, ProductUnit, BOX, G, KG, L, M, ML (+13 more)

### Community 57 - "Part A — Backend (`crm`)"
Cohesion: 0.09
Nodes (21): Explicitly deferred (not in this plan), Global Constraints, Part A — Backend (`crm`), Part B — Frontend (`crm-dashboard-ui`), Redesign #3 Inventory & Fulfilment — Implementation Plan, Review Focus, Task 10: Stock page, ledger, stock in and adjust, Task 11: Transfers (+13 more)

### Community 59 - "LeadDuplicates"
Cohesion: 0.23
Nodes (6): Candidate, ContactMatches, Match, LeadDuplicates, LeadDuplicatesResponse, PhoneKey

### Community 60 - "V5__inventory_fulfilment.sql"
Cohesion: 0.12
Nodes (26): warehouse, warehouse_code_uq, warehouse_one_default, delivery_order_line_delivery_order_idx, stock_transfer_line_transfer_idx, product, product_name_idx, product_sku_uq (+18 more)

### Community 61 - "UI polish bundle — design (2026-11-17)"
Cohesion: 0.10
Nodes (19): 1. Sorting on every table, 2. Order search by customer name, 3. Image viewer, 4. Notification date and time, 5. Dashboard style C, Backend, Component, Dashboards (+11 more)

### Community 62 - "Deals / opportunities (F-130) — design"
Cohesion: 0.18
Nodes (10): API, Approach, Data — Flyway `V15__deals.sql`, Deals / opportunities (F-130) — design, Decisions (from the user), Goal, Out of scope, Rules (+2 more)

### Community 63 - "InsightsQueries"
Cohesion: 0.19
Nodes (6): org.springframework.cache.annotation.Cacheable, RepRow, Balance, InsightsQueries, RepAmounts, InsightsService

### Community 64 - "Follow-ups (F-141) — design"
Cohesion: 0.20
Nodes (9): API — `/api/follow-ups`, Data — Flyway `V11__follow_ups.sql`, Decisions (from the user), Follow-ups (F-141) — design, Goal, Out of scope, Rules, Screens (crm-dashboard-ui) (+1 more)

### Community 65 - "Part A — Backend (`crm`)"
Cohesion: 0.06
Nodes (31): Explicitly deferred (not in this plan), Global Constraints, Part A — Backend (`crm`), Part B — Frontend (`crm-dashboard-ui`), Redesign #1 Foundation — Implementation Plan, Review Focus, Task 10: Identity — TOTP 2FA and recovery codes, Task 11: Notifications (carried over, re-keyed to users) (+23 more)

### Community 68 - "LoginIT"
Cohesion: 0.21
Nodes (3): javax.sql.DataSource, org.springframework.session.data.redis.RedisIndexedSessionRepository, LoginIT

### Community 69 - ".diff"
Cohesion: 0.11
Nodes (13): Lead, LeadAssignRequest, LeadRequest, LeadResponse, LeadScopes, LeadService, LeadStatus, CONTACTED (+5 more)

### Community 70 - "TotpService.java"
Cohesion: 0.36
Nodes (3): java.security.SecureRandom, javax.crypto.SecretKey, EncryptionService

### Community 72 - "CustomerSalesController"
Cohesion: 0.15
Nodes (9): CustomerInvoiceRow, CustomerSalesController, OrderFilter, OrderSummary, PaymentStatus, OVERDUE, PAID, PARTIAL (+1 more)

### Community 74 - "PaymentService"
Cohesion: 0.08
Nodes (15): OrderStockMapper, Payment, PaymentRequest, PaymentResponse, Attachment, FORM_2307, PROOF, key() (+7 more)

### Community 75 - ".delivered"
Cohesion: 0.19
Nodes (6): ApplicationEventPublisher, PlatformTransactionManager, RestockRuleIT, ApplicationEventPublisher, PlatformTransactionManager, WinBackRuleIT

### Community 76 - "Automatic follow-ups — design (2026-11-17)"
Cohesion: 0.13
Nodes (14): 1. Data — `V24__auto_follow_ups.sql`, 2. Settings (`SettingKey`, admin-editable, validated like existing keys), 3. Daily scan — `followups/AutoFollowUps`, 4. Rules (each is one class in `followups/rules/`, implementing `AutoRule`), 5. Overdue notification, 6. API, 7. Frontend (`crm-dashboard-ui`), 8. Testing (+6 more)

### Community 77 - "NotificationStreams"
Cohesion: 0.14
Nodes (10): org.springframework.data.redis.connection.Message, org.springframework.data.redis.connection.MessageListener, org.springframework.web.servlet.mvc.method.annotation.SseEmitter, NotificationResponse, Override, NotificationStreams, StreamController, OpenApiSnapshotTest (+2 more)

### Community 79 - "NotificationType"
Cohesion: 0.08
Nodes (24): NotificationType, APPROVAL_DECIDED, APPROVAL_REQUESTED, DELIVERY_CREATED, FOLLOW_UP_OVERDUE, FOLLOW_UPS_DUE, LOW_STOCK, ORDER_CONFIRMED (+16 more)

### Community 80 - "Backend (`crm/crm`)"
Cohesion: 0.12
Nodes (15): Backend (`crm/crm`), Dashboards (#4) Implementation Plan, Frontend (`crm-dashboard-ui`), Global Constraints, Review Focus, Task 10: End-to-end smoke, Task 1: Snapshots at confirm and the V10 migration, Task 2: Insights module, month parsing and sales targets (+7 more)

### Community 81 - ".record"
Cohesion: 0.04
Nodes (25): com.fasterxml.jackson.annotation.JsonIgnore, jakarta.validation.constraints.AssertTrue, ContactRequest, ContactResponse, Customer, CustomerRequest, CustomerResponse, CustomerScopes (+17 more)

### Community 82 - "jakarta.servlet.http.HttpServletRequest"
Cohesion: 0.11
Nodes (13): jakarta.servlet.http.HttpServletRequest, jakarta.servlet.http.HttpServletResponse, org.springframework.core.annotation.Order, org.springframework.security.core.Authentication, LoginResponse, Principals, TotpCodeRequest, TotpController (+5 more)

### Community 83 - "Backend (`crm/crm`)"
Cohesion: 0.11
Nodes (17): Backend (`crm/crm`), Frontend (`crm-dashboard-ui`), Global Constraints, Notification copy (exact), Notifications Implementation Plan, Review Focus, Task 10: Remove Inbox, Task 11: Graph refresh and final check (+9 more)

### Community 85 - "app_user"
Cohesion: 0.17
Nodes (21): app_setting, app_user, app_user_email_uq, approval_one_pending, approval_request, approval_subject_idx, audit_actor_idx, audit_at_idx (+13 more)

### Community 87 - "Frontend (`crm-dashboard-ui`)"
Cohesion: 0.13
Nodes (14): Backend (`crm/crm`), Frontend (`crm-dashboard-ui`), Global Constraints, POS (#4) Implementation Plan, Review Focus, Task 1: Sales channel and POS warehouse setting, Task 2: Upload proof after recording a payment, Task 3: POS product list (+6 more)

### Community 88 - "ServiceCallLogging.java"
Cohesion: 0.19
Nodes (8): java.lang.reflect.Method, org.aopalliance.intercept.MethodInvocation, org.springframework.aop.Advisor, org.springframework.aop.Pointcut, org.springframework.beans.factory.config.BeanPostProcessor, org.springframework.context.annotation.Role, org.springframework.core.ParameterNameDiscoverer, ServiceCallLogging

### Community 89 - "Approvals"
Cohesion: 0.07
Nodes (18): ApprovalRequest, ApprovalRequestRepository, ApprovalResponse, Approvals, Link, ApprovalType, BELOW_COST, CREDIT_LIMIT (+10 more)

### Community 90 - "Review Focus"
Cohesion: 0.17
Nodes (11): Automatic Follow-ups Implementation Plan, Global Constraints, Review Focus, Task 1: Schema, settings keys and FollowUp fields, Task 2: Engine (`AutoFollowUps`, `AutoRule`, `RestockRhythm`), Task 3: Customer rules (RESTOCK, WIN_BACK, FIRST_ORDER) and the order-confirmed close, Task 4: Order rules (QUOTE_CHASE, QUOTE_EXPIRING, QUOTE_DECLINED, PAYMENT_OVERDUE) and their closes, Task 5: DEAL_STALLED and the deal-stage close (+3 more)

### Community 91 - "CustomerStatusMailsIT"
Cohesion: 0.14
Nodes (4): jakarta.mail.internet.MimeMessage, jakarta.mail.Multipart, PasswordResetIT, CustomerStatusMailsIT

### Community 93 - "Design"
Cohesion: 0.12
Nodes (16): 1. Request line — `shared/AccessLogFilter`, 2. Business step line — `AuditLog.record`, 3. Service calls — `shared/ServiceCallLogging`, 4. Redaction — `shared/LogRedactor`, 5. Background work, 6. Changing detail at runtime, 7. Files — `logback-spring.xml`, 8. UI (crm-dashboard-ui) (+8 more)

### Community 94 - "AuditLog"
Cohesion: 0.07
Nodes (32): org.slf4j.Logger, org.springframework.context.ApplicationEventPublisher, org.springframework.core.task.TaskExecutor, org.springframework.stereotype.Service, org.springframework.transaction.PlatformTransactionManager, AuditLog, Logger, Pattern (+24 more)

### Community 96 - "InventoryFixture"
Cohesion: 0.14
Nodes (6): DeliveryLine, DeliveryRequest, InventoryFixture, ApplicationContext, Line, InventoryOverviewIT

### Community 98 - "Review Focus"
Cohesion: 0.17
Nodes (11): Global Constraints, Redis Phase Implementation Plan, Review Focus, Task 0: Commit the MinIO work, Task 1: Redis wiring and token revocation (R-01, R-02, R-09 part), Task 2: Rate limiting (R-03), Task 3: Single-flight locks on customer links and staff accept (R-04), Task 4: Run the quotation expiry job on one instance (R-05) (+3 more)

### Community 99 - "ErrorCode"
Cohesion: 0.05
Nodes (56): java.util.regex.Pattern, org.springframework.http.HttpStatus, org.springframework.security.web.csrf.CsrfToken, org.springframework.web.bind.annotation.RequestMapping, org.springframework.web.bind.annotation.RestController, ActivityController, AuditChanges, ScanController (+48 more)

### Community 100 - "Review Focus"
Cohesion: 0.15
Nodes (12): Deals (F-130) Implementation Plan, Global Constraints, Review Focus, Task 1: Table, create, list, get, summary, Task 2: Edit and stages (except Won), Task 3: Linking orders, Task 4: Won — automatic and by hand, Task 5: Follow the customer on reassignment (+4 more)

### Community 101 - "SettingKey.java"
Cohesion: 0.28
Nodes (11): bankAccounts(), bool(), field(), invalid(), number(), positiveIdOrNull(), text(), validate() (+3 more)

### Community 102 - "IntegrationTest.java"
Cohesion: 0.16
Nodes (15): org.springframework.boot.test.context.SpringBootTest, org.springframework.boot.test.context.TestConfiguration, org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc, org.springframework.context.ApplicationContextInitializer, org.springframework.context.ConfigurableApplicationContext, org.springframework.test.context.ActiveProfiles, org.springframework.test.context.ContextConfiguration, org.testcontainers.containers.PostgreSQLContainer (+7 more)

### Community 103 - "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate"
Cohesion: 0.12
Nodes (7): java.sql.ResultSet, org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate, Due, FollowUpQueries, Overdue, Raw, SalesReports

### Community 105 - "OrderService"
Cohesion: 0.05
Nodes (24): RequiredApproval, CustomerSalesProfile, DiscountType, AMOUNT, PERCENT, Invoice, InvoiceResponse, InvoiceService (+16 more)

### Community 106 - "RedisTestSupport"
Cohesion: 0.13
Nodes (7): org.springframework.context.annotation.AnnotationConfigApplicationContext, org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory, org.testcontainers.containers.GenericContainer, RedisListenerConfigIT, RedisTestSupport, CacheConfigIT, SummarySource

### Community 107 - "LogRedactorTest"
Cohesion: 0.13
Nodes (7): LogRedactor, Render, Create, Customer, Login, LogRedactorTest, Node

### Community 108 - "RateLimitFilterIT"
Cohesion: 0.31
Nodes (4): org.junit.jupiter.params.provider.CsvSource, org.springframework.mock.web.MockFilterChain, org.springframework.mock.web.MockHttpServletResponse, RateLimitFilterIT

### Community 109 - "WarehouseInsights"
Cohesion: 0.48
Nodes (4): DeliveryRef, Today, Transfers, WarehouseInsights

### Community 110 - "Redesign #1 — Foundation"
Cohesion: 0.07
Nodes (28): 10. Testing, 11. Deployment changes, 1. Backend module layout, 2. Data conventions (Postgres), 3. Identity & sessions, 4. Authorization & scoping, 5. Approvals, 6. Audit (+20 more)

### Community 111 - "sales_order"
Cohesion: 0.15
Nodes (20): order_revision, payment_status_verified_idx, sales_order_rep_idx, sales_target, money_movement, refund, refund_line, refund_line_refund_idx (+12 more)

### Community 113 - "java.time.YearMonth"
Cohesion: 0.19
Nodes (3): java.time.YearMonth, InsightsMonth, InsightsMonthTest

### Community 114 - "Review Focus"
Cohesion: 0.14
Nodes (13): Global Constraints, Leads (F-120) Implementation Plan, Review Focus, Task 10: e2e, Task 1: Migration, lead sources on customers, Task 2: Lead sources API, Task 3: Leads module — create, list, get, counts, Task 4: Edit, status, assign (+5 more)

### Community 115 - "org.springframework.data.redis.core.StringRedisTemplate"
Cohesion: 0.17
Nodes (7): lombok.RequiredArgsConstructor, org.springframework.data.redis.core.script.RedisScript, org.springframework.data.redis.core.StringRedisTemplate, DistributedLock, RateLimiter, Override, DistributedLockIT

### Community 116 - "OneTimeTokens"
Cohesion: 0.11
Nodes (11): jakarta.persistence.EntityManager, OneTimeTokens, Subject, TokenPurpose, INVITE, PASSWORD_RESET, PAYMENT_PROOF, PORTAL_LOGIN (+3 more)

### Community 117 - "Leads (F-120) — design"
Cohesion: 0.18
Nodes (10): API, Approach, Data — Flyway `V14__leads.sql`, Decisions (from the user), Goal, Leads (F-120) — design, Out of scope, Rules (+2 more)

### Community 118 - "PaymentOverdueRule"
Cohesion: 0.22
Nodes (3): Override, PaymentOverdueRule, Quotes

### Community 122 - "Backend (`crm`)"
Cohesion: 0.12
Nodes (15): Backend (`crm`), Frontend (`crm-dashboard-ui`), Global Constraints, Retail POS Implementation Plan, Review Focus, Task 10: Payment, success view and printed receipt, Task 1: Schema, enums and response fields, Task 2: `InventoryService.sellForPos` (+7 more)

### Community 123 - "Review Focus"
Cohesion: 0.22
Nodes (8): Attachments (M-08) Implementation Plan, Global Constraints, Review Focus, Task 1: StorageService — detect, put, presign for download, Task 2: Table, entity and access, Task 3: Upload, list, download, archive, Task 4: UI — FilesSection, Task 5: Place it, and e2e

### Community 124 - "Refunds — design (2026-11-16)"
Cohesion: 0.14
Nodes (13): 1. Data model, 2. Lifecycle, 3. Money and payment status, 4. Insights, reports, dashboard, 5. Notifications, 6. Credit memo, 7. Frontend (`crm-dashboard-ui`), 8. Testing (+5 more)

### Community 125 - "Retail POS — Design"
Cohesion: 0.11
Nodes (18): Backend (`crm`), Backend tests, Checkout flow, Components — `src/components/pos/`, Decisions, Emailed, Endpoints — new `PosController` at `/api/v1/pos`, `@PreAuthorize("hasAnyAuthority('ADMIN','SALES')")`, Frontend checks (+10 more)

### Community 126 - "Redesign #2 — Sales lifecycle"
Cohesion: 0.06
Nodes (33): 1. Modules, 2. Data model, 3. Order lifecycle, 4. Money, invoices and payments, 5. Access and API, 6. Screens (`crm-dashboard-ui`), 7. Testing, approvals (+25 more)

### Community 127 - "Customer status emails — design (2026-11-18)"
Cohesion: 0.20
Nodes (9): 1. Emails, 2. Events, 3. Components, 4. Error handling, 5. Testing, Customer status emails — design (2026-11-18), Decisions, Goal (+1 more)

### Community 128 - "Review Focus"
Cohesion: 0.22
Nodes (8): Global Constraints, Reports (F-172) Implementation Plan, Review Focus, Task 1: Range parsing and error code, Task 2: Month, rep and customer reports plus endpoint, Task 3: Product report with pro-rata split, Task 4: Reports page, Task 5: End-to-end smoke

### Community 129 - "RedisSessionAdmin"
Cohesion: 0.36
Nodes (4): org.springframework.session.FindByIndexNameSessionRepository, org.springframework.session.Session, Override, RedisSessionAdmin

### Community 132 - "DealOrder"
Cohesion: 0.31
Nodes (5): DealOrder.Key, jakarta.persistence.IdClass, DealOrder, Key, DealOrderRepository

### Community 133 - "NotificationStreamsIT"
Cohesion: 0.16
Nodes (6): org.springframework.data.redis.connection.RedisConnectionFactory, org.springframework.data.redis.listener.RedisMessageListenerContainer, Override, RedisListenerConfig, RetryingListenerContainer, NotificationStreamsIT

### Community 134 - "Attachments (M-08) — design"
Cohesion: 0.20
Nodes (9): API — `/api/attachments`, Attachments (M-08) — design, Data — Flyway `V13__attachments.sql`, Decisions (from the user), Goal, Out of scope, Rules, Screens (crm-dashboard-ui) (+1 more)

### Community 135 - "SecurityMatrixTest"
Cohesion: 0.17
Nodes (8): org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping, SecurityMatrixTest, Who, ADMIN, ANON, CUSTOMER, SALES, WAREHOUSE

### Community 136 - "package.json"
Cohesion: 0.22
Nodes (8): tailwindcss, @tailwindcss/cli, devDependencies, tailwindcss, @tailwindcss/cli, private, scripts, build

### Community 140 - "Review Focus"
Cohesion: 0.17
Nodes (11): Follow-ups (F-141) Implementation Plan, Global Constraints, Review Focus, Task 1: Table, entity and the Manila day, Task 2: Create, list and counts, Task 3: Edit, done and reopen, Task 4: Follow the customer on reassignment, Task 5: Morning bell (+3 more)

### Community 141 - "Review Focus"
Cohesion: 0.18
Nodes (10): Global Constraints, Review Focus, Task 1: Backend sort infrastructure (`SortRequest`) applied to Orders and Customers, Task 2: Order search by customer name, Task 3: Sort keys on every other paged endpoint, Task 4: Frontend sorting (DataTable `sortKey`, `SortableHeader`, `useSortedRows`), Task 5: Image viewer, Task 6: Notification date and time (+2 more)

### Community 142 - "lead"
Cohesion: 0.42
Nodes (8): lead, lead_customer_idx, lead_email_idx, lead_phone_idx, lead_rep_status_idx, lead_source, lead_source_name_uq, lead_status_idx

### Community 143 - "org.springframework.web.filter.OncePerRequestFilter"
Cohesion: 0.11
Nodes (14): jakarta.servlet.FilterChain, org.springframework.beans.factory.annotation.Autowired, org.springframework.boot.web.servlet.FilterRegistrationBean, org.springframework.util.AntPathMatcher, org.springframework.web.filter.OncePerRequestFilter, org.springframework.web.util.ContentCachingResponseWrapper, AbsoluteSessionTimeoutFilter, Override (+6 more)

### Community 144 - "org.junit.jupiter.params.ParameterizedTest"
Cohesion: 0.36
Nodes (4): org.junit.jupiter.params.ParameterizedTest, org.junit.jupiter.params.provider.ValueSource, Override, IdempotencyFilterPathsTest

### Community 145 - ".receive"
Cohesion: 0.11
Nodes (6): AvailabilityResponse, ReceiptLineRequest, ReceiptRequest, WarehouseController, WarehouseRequest, WarehouseResponse

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
Cohesion: 0.25
Nodes (7): MovementType, ADJUSTMENT, DISPATCH, RECEIPT, RETURN, TRANSFER_IN, TRANSFER_OUT

### Community 152 - "deal"
Cohesion: 0.52
Nodes (6): deal, deal_customer_idx, deal_order, deal_order_order_uq, deal_rep_stage_idx, deal_stage_idx

### Community 153 - "Notifications — design (2026-11-12)"
Cohesion: 0.17
Nodes (11): 1. Notification catalogue, 2. Notifications page, bell, routing (frontend), 3. Approvals on record pages, 4. Backend changes, 5. Testing, Backend, Frontend, Goal (+3 more)

### Community 154 - "Review Focus"
Cohesion: 0.18
Nodes (10): Global Constraints, Refunds Implementation Plan, Review Focus, Task 1: Schema, enums and `Inventory.receiveReturn`, Task 2: Request, approve, reject, cancel, Task 3: Payout — stock, credit memo number, notification, Task 4: Credit memo PDF, Task 5: Insights and reports net of refunds (+2 more)

### Community 155 - "FollowUpRule"
Cohesion: 0.11
Nodes (12): FollowUpRule, DEAL_STALLED, FIRST_ORDER, PAYMENT_OVERDUE, QUOTE_CHASE, QUOTE_DECLINED, QUOTE_EXPIRING, RESTOCK (+4 more)

### Community 158 - "CrmApplication"
Cohesion: 0.53
Nodes (4): org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration, org.springframework.scheduling.annotation.EnableScheduling, CrmApplication

### Community 159 - "MinioHealthIndicator"
Cohesion: 0.32
Nodes (4): org.springframework.boot.health.contributor.Health, org.springframework.boot.health.contributor.HealthIndicator, Override, MinioHealthIndicator

### Community 162 - "org.springframework.context.annotation.Bean"
Cohesion: 0.13
Nodes (13): org.springframework.context.annotation.Bean, org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity, org.springframework.security.config.annotation.web.builders.HttpSecurity, org.springframework.security.config.annotation.web.configuration.EnableWebSecurity, org.springframework.security.web.SecurityFilterChain, org.springframework.session.web.http.CookieHttpSessionIdResolver, org.springframework.session.web.http.CookieSerializer, org.springframework.session.web.http.HttpSessionIdResolver (+5 more)

### Community 164 - "Production cutover (P5-2)"
Cohesion: 0.33
Nodes (5): Afterwards, Before the window, Production cutover (P5-2), Rollback, Steps

### Community 165 - "Candidate"
Cohesion: 0.31
Nodes (3): Candidate, FirstOrderRule, Override

### Community 166 - "follow_up"
Cohesion: 0.36
Nodes (6): follow_up, follow_up_customer_idx, follow_up_open_idx, follow_up_order_idx, auto_follow_up_run, follow_up_open_rule_idx

### Community 167 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 169 - "RestockRule.java"
Cohesion: 0.25
Nodes (4): RestockRhythm, Delivery, Override, RestockRule

### Community 170 - "Review Focus"
Cohesion: 0.25
Nodes (7): Customer Status Emails Implementation Plan, Global Constraints, Review Focus, Task 1: Events: `PaymentRejected`, `DeliveryDispatched`, `OrderDelivered.deliveryOrderId`, Task 2: `StorageService.read` and `Fulfilment.mailView`, Task 3: `CustomerStatusMails`: confirmed, payment received, payment rejected, Task 4: Dispatched and delivered emails

### Community 172 - "transform.sql"
Cohesion: 0.22
Nodes (4): l, o, pg_temp.sys(), pg_temp.uid()

### Community 190 - "LabelService"
Cohesion: 0.40
Nodes (3): LabelController, Label, LabelService

### Community 193 - "org.springframework.transaction.annotation.Transactional"
Cohesion: 0.06
Nodes (18): org.springframework.transaction.annotation.Transactional, DeliverySummary, LabelKind, DO, PR, TR, WH, DeclineRequest (+10 more)

### Community 195 - "StaffPrincipal"
Cohesion: 0.10
Nodes (13): AssertTrue, org.springframework.http.MediaType, org.springframework.security.authentication.UsernamePasswordAuthenticationToken, org.springframework.security.core.AuthenticatedPrincipal, org.springframework.security.core.authority.SimpleGrantedAuthority, org.springframework.security.web.context.HttpSessionSecurityContextRepository, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, ContactPrincipal (+5 more)

### Community 203 - "RefundDisposition"
Cohesion: 0.29
Nodes (5): RefundDisposition, DAMAGED, RESTOCK, RefundLineRequest, Line

### Community 204 - "StorageService"
Cohesion: 0.11
Nodes (8): io.minio.MinioClient, org.junit.jupiter.api.BeforeAll, org.testcontainers.containers.MinIOContainer, org.testcontainers.junit.jupiter.Testcontainers, Detected, Pattern, StorageService, StorageServiceIT

### Community 207 - "LeadSources"
Cohesion: 0.22
Nodes (5): LeadSource, LeadSourceRepository, LeadSourceRequest, LeadSourceResponse, LeadSources

### Community 211 - "TargetService"
Cohesion: 0.15
Nodes (7): SalesTarget, SalesTargetRepository, TargetController, TargetMilestones, TargetRequest, TargetRow, TargetService

### Community 213 - "RefundService"
Cohesion: 0.07
Nodes (25): PaymentMethod, BANK_TRANSFER, CARD, CASH, CHECK, GCASH, OTHER, Refund (+17 more)

### Community 214 - "AccessLogFilter"
Cohesion: 0.29
Nodes (3): AccessLogFilter, Override, Registration

### Community 215 - ".version"
Cohesion: 0.12
Nodes (4): CreditMemoIT, Environment, PdfRendererTest, PortalOrdersIT

### Community 217 - "org.springframework.web.bind.annotation.ResponseStatus"
Cohesion: 0.09
Nodes (10): org.springframework.web.bind.annotation.DeleteMapping, org.springframework.web.bind.annotation.ResponseStatus, AcceptInviteRequest, ResetPasswordRequest, SessionController, SessionResponse, UserAdminController, UserResponse (+2 more)

### Community 219 - "CurrentActor"
Cohesion: 0.05
Nodes (24): org.springframework.security.access.prepost.PreAuthorize, org.springframework.validation.annotation.Validated, org.springframework.web.bind.annotation.GetMapping, org.springframework.web.bind.annotation.PostMapping, org.springframework.web.bind.annotation.PutMapping, org.springframework.web.multipart.MultipartFile, AttachmentController, AssignmentRequest (+16 more)

### Community 220 - "FakeRule"
Cohesion: 0.36
Nodes (3): FakeRule, Fakes, Override

### Community 222 - "RuleSettings"
Cohesion: 0.20
Nodes (4): AutoRule, Override, QuoteDeclinedRule, RuleSettings

### Community 227 - "TestController"
Cohesion: 0.19
Nodes (3): ScanResult, Body, TestController

### Community 228 - "org.springframework.context.annotation.Configuration"
Cohesion: 0.22
Nodes (10): org.springframework.cache.annotation.CachingConfigurer, org.springframework.cache.annotation.EnableCaching, org.springframework.cache.interceptor.CacheErrorHandler, org.springframework.context.annotation.Configuration, org.springframework.web.servlet.config.annotation.CorsRegistry, org.springframework.web.servlet.config.annotation.WebMvcConfigurer, CacheConfig, Override (+2 more)

### Community 230 - "org.junit.jupiter.api.AfterEach"
Cohesion: 0.05
Nodes (4): org.junit.jupiter.api.AfterEach, SessionTransportIT, ReleaseCancelIT, SortRequestIT

### Community 232 - "LogLevelController"
Cohesion: 0.60
Nodes (3): org.springframework.boot.logging.LoggingSystem, LogLevelBody, LogLevelController

### Community 238 - "org.springframework.http.ResponseEntity"
Cohesion: 0.09
Nodes (7): org.springframework.http.ResponseEntity, ApprovalController, FileController, NotificationController, PortalOrderController, FileControllerTest, CountingController

### Community 239 - "V26__product_category.sql"
Cohesion: 0.67
Nodes (3): product_category, product_category_id_idx, product_category_name_uq

## Knowledge Gaps
- **775 isolated node(s):** `o`, `l`, `01-roles.sh script`, `PENDING`, `APPROVED` (+770 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 1052 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **82 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `DeliveryMailView` connect `HtmlMail` to `attachment`, `.record`, `AuditLog`?**
  _High betweenness centrality (0.035) - this node is a cross-community bridge._
- **Why does `attachment` connect `attachment` to `app_user`, `HtmlMail`?**
  _High betweenness centrality (0.035) - this node is a cross-community bridge._
- **Why does `app_user` connect `app_user` to `follow_up`, `transform.sql`, `lead`, `sales_order`, `attachment`, `user_role`, `deal`, `V5__inventory_fulfilment.sql`?**
  _High betweenness centrality (0.035) - this node is a cross-community bridge._
- **What connects `o`, `l`, `01-roles.sh script` to the rest of the system?**
  _775 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `.as` be split into smaller, more focused modules?**
  _Cohesion score 0.12790697674418605 - nodes in this community are weakly interconnected._
- **Should `AttachmentsIT` be split into smaller, more focused modules?**
  _Cohesion score 0.1241565452091768 - nodes in this community are weakly interconnected._
- **Should `org.springframework.jdbc.core.JdbcTemplate` be split into smaller, more focused modules?**
  _Cohesion score 0.09185971685971686 - nodes in this community are weakly interconnected._