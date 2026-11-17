# Graph Report - crm  (2026-11-17)

## Corpus Check
- 631 files · ~287,199 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 17 file(s) not represented in the graph (top: .properties 6, (none) 3, .example 2)

## Summary
- 5426 nodes · 19383 edges · 235 communities (151 shown, 84 thin omitted)
- Extraction: 85% EXTRACTED · 15% INFERRED · 0% AMBIGUOUS · INFERRED: 2872 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `c5932e10`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- SalesFixture
- java.util.regex.Pattern
- org.springframework.jdbc.core.JdbcTemplate
- MailIntegrationTest
- AuditAction
- org.junit.jupiter.api.Test
- .lead
- .create
- org.springframework.web.bind.annotation.GetMapping
- TaxClass
- UserAdminIT
- .customer
- AuditLog
- DealsFixture
- TwoFactorIT
- BalancesTest
- PortalAuthIT
- Part A — Backend (`crm`)
- jakarta.persistence.Entity
- PaymentTerms
- ServiceCallLoggingIT
- Redesign #3 — Inventory & Fulfilment
- SettingKey
- .followUp
- Scope
- AttachmentsFixture
- ReportRange
- DeliveryService
- SettingsIT
- OrderStockIT
- .l
- LeadDuplicates
- MeController
- org.springframework.http.ResponseEntity
- Sheets
- RefundsIT
- .at
- .createdOrder
- FollowUpsIT
- PdfRenderer
- Inventory
- Catalog
- AutoFollowUps
- org.springframework.data.jpa.repository.JpaRepository
- DeliveryOrdersIT
- AttachmentService
- FollowUpService
- StorageDetectTest
- OrderStatus
- org.springframework.context.annotation.Bean
- TransferService
- Redesign #4 — POS
- ApprovalStatus
- AppUser
- ProductUnit
- .csrf
- FollowUpsFixture
- Part A — Backend (`crm`)
- PaymentsIT
- OrderService
- V5__inventory_fulfilment.sql
- UI polish bundle — design (2026-11-17)
- Deals / opportunities (F-130) — design
- java.time.YearMonth
- Follow-ups (F-141) — design
- Part A — Backend (`crm`)
- ProductController
- Settings
- LoginIT
- LeadService
- TotpService
- SessionManagementIT
- AppUserRepository
- .receive
- PaymentService
- .delivered
- Automatic follow-ups — design (2026-11-17)
- NotificationStreams
- CatalogIT
- OrderService.java
- Backend (`crm/crm`)
- org.springframework.transaction.annotation.Transactional
- jakarta.servlet.http.HttpServletRequest
- Backend (`crm/crm`)
- FollowUpRule
- app_user
- .check
- Frontend (`crm-dashboard-ui`)
- ServiceCallLogging.java
- ApprovalsIT
- Review Focus
- HtmlMail
- ApprovalType
- Design
- Users
- .run
- .product
- NotificationsIT
- Review Focus
- ErrorCode
- Review Focus
- SettingKey.java
- IntegrationTest.java
- org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
- WarehousesIT
- InvoiceService
- RedisTestSupport
- LogRedactorTest
- RateLimiter
- AttachmentAccess
- Redesign #1 — Foundation
- sales_order
- CreateApprovalRequest
- DealWonIT
- Review Focus
- AttachmentsIT
- .issue
- Leads (F-120) — design
- org.springframework.data.jpa.domain.Specification
- CustomerScopingIT
- InvoicesIT
- LeadSourcesIT
- Backend (`crm`)
- Review Focus
- Refunds — design (2026-11-16)
- Retail POS — Design
- Redesign #2 — Sales lifecycle
- CurrentActor
- Review Focus
- org.springframework.stereotype.Component
- LeadSourceBackfillIT
- LeadController
- PasswordResetIT
- NotificationStreamsIT
- Attachments (M-08) — design
- SecurityMatrixTest
- package.json
- StockReadsIT
- CustomerSourceIT
- IdempotencyFilterIT
- Review Focus
- Review Focus
- lead
- org.springframework.data.redis.core.StringRedisTemplate
- RetryingListenerContainer
- Warehouses
- migrate.py
- Review Focus
- Review Focus
- Redesign #4 — Dashboards
- Reports (F-172)
- MovementType
- deal
- Notifications — design (2026-11-12)
- Review Focus
- WinBackRule
- RedisSessionAdmin
- ReceiptsIT
- CrmApplication
- .verify
- LogLevelIT
- org.springframework.core.env.Environment
- SalesTime
- .receive
- Production cutover (P5-2)
- DocumentController
- follow_up
- gradlew
- MinioHealthIndicator
- RestockRule.java
- InvoiceDocument
- DocsDisabledTest
- transform.sql
- LogRedactor
- 01-roles.sh
- V12__follow_up_reminder_run.sql
- Notification
- .of
- DealStalledRuleIT
- PortalOrderService
- DealOrder
- StaffPrincipal
- .record
- StorageService
- .dueAfterDays
- LeadSources
- refund
- AuditLogIT
- PaymentOverdueRuleIT
- .displayNames
- AutoConfirmIT
- RefundStatus
- .shouldNotFilter
- .json
- FileController.java
- org.springframework.web.bind.annotation.RestController
- RuleSettings
- .scope
- FakeRule
- OrderLine
- FirstOrderRuleIT
- AuditEvent
- ScanController.java
- EncryptionSecretsCheckTest
- .call
- TestController
- CacheConfig.java
- TargetController
- org.junit.jupiter.api.AfterEach
- .availability
- SessionTransportIT
- ApprovalPushAfterCommitIT
- PdfText

## God Nodes (most connected - your core abstractions)
1. `Role` - 194 edges
2. `IntegrationTest` - 173 edges
3. `Scope` - 149 edges
4. `AuditAction` - 127 edges
5. `CurrentActor` - 124 edges
6. `AuditLog` - 123 edges
7. `ErrorCode` - 112 edges
8. `Customers` - 101 edges
9. `DomainException` - 93 edges
10. `PageQuery` - 81 edges

## Surprising Connections (you probably didn't know these)
- `pg_temp.sys()` --reads_from--> `app_user`  [EXTRACTED]
  deploy/migrate/transform.sql → src/main/resources/db/migration/V1__baseline.sql
- `pg_temp.uid()` --reads_from--> `app_user`  [EXTRACTED]
  deploy/migrate/transform.sql → src/main/resources/db/migration/V1__baseline.sql
- `sales_target` --references--> `app_user`  [EXTRACTED]
  src/main/resources/db/migration/V10__insights.sql → src/main/resources/db/migration/V1__baseline.sql
- `ApprovalController` --references--> `Approvals`  [EXTRACTED]
  src/main/java/ph/thecoffeejunkie/crm/approvals/ApprovalController.java → src/main/java/ph/thecoffeejunkie/crm/approvals/Approvals.java
- `ApprovalDecided` --references--> `ApprovalType`  [EXTRACTED]
  src/main/java/ph/thecoffeejunkie/crm/approvals/ApprovalDecided.java → src/main/java/ph/thecoffeejunkie/crm/approvals/ApprovalType.java

## Import Cycles
- None detected.

## Communities (235 total, 84 thin omitted)

### Community 0 - "SalesFixture"
Cohesion: 0.11
Nodes (4): OrderScopingIT, ReportsIT, SalesFixture, SortRequestIT

### Community 1 - "java.util.regex.Pattern"
Cohesion: 0.22
Nodes (5): java.util.regex.Pattern, org.springframework.core.annotation.Order, RequestIdFilter, NoSpringCsrfPostProcessorTest, RequestIdFilterTest

### Community 2 - "org.springframework.jdbc.core.JdbcTemplate"
Cohesion: 0.09
Nodes (32): Multipart, org.junit.jupiter.api.BeforeEach, org.springframework.cache.CacheManager, org.springframework.context.ApplicationContext, org.springframework.jdbc.core.JdbcTemplate, org.springframework.jdbc.datasource.DriverManagerDataSource, org.springframework.test.context.event.ApplicationEvents, org.springframework.test.context.event.RecordApplicationEvents (+24 more)

### Community 3 - "MailIntegrationTest"
Cohesion: 0.11
Nodes (14): jakarta.mail.Session, java.lang.annotation.Retention, java.lang.annotation.Target, org.junit.jupiter.api.extension.ExtendWith, org.springframework.boot.test.system.CapturedOutput, org.springframework.boot.test.system.OutputCaptureExtension, org.springframework.mail.javamail.JavaMailSender, org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor (+6 more)

### Community 4 - "AuditAction"
Cohesion: 0.02
Nodes (83): AuditAction, ACCOUNT_LOCKED, APPROVAL_CANCELLED, APPROVAL_DECIDED, APPROVAL_REQUESTED, ARCHIVED, ASSIGNMENT_CHANGED, ATTACHMENT_ADDED (+75 more)

### Community 5 - "org.junit.jupiter.api.Test"
Cohesion: 0.04
Nodes (13): org.junit.jupiter.api.Test, AuditChangesTest, FollowUpReminderTest, ModularityTest, PdfRendererTest, ReviseIT, BaselineSchemaIT, InventorySchemaIT (+5 more)

### Community 6 - ".lead"
Cohesion: 0.08
Nodes (5): LeadConvertIT, LeadDuplicatesIT, LeadEditIT, LeadsFixture, LeadsIT

### Community 7 - ".create"
Cohesion: 0.11
Nodes (15): AdjustmentMode, COUNTED, DELTA, AdjustmentRequest, AdjustmentResponse, AdjustmentSource, MANUAL, RETURN_DAMAGED (+7 more)

### Community 8 - "org.springframework.web.bind.annotation.GetMapping"
Cohesion: 0.07
Nodes (19): org.springframework.web.bind.annotation.GetMapping, AuditEventResponse, CustomerSummary, MovementResponse, StockController, CustomerAccount, CustomerInvoiceRow, CustomerSalesController (+11 more)

### Community 9 - "TaxClass"
Cohesion: 0.13
Nodes (11): TaxClass, VAT_EXEMPT, VATABLE, ZERO_RATED, TaxTreatment, REGULAR, VAT_EXEMPT, ZERO_RATED (+3 more)

### Community 10 - "UserAdminIT"
Cohesion: 0.17
Nodes (3): jakarta.mail.internet.MimeMessage, ResultActions, UserAdminIT

### Community 12 - "AuditLog"
Cohesion: 0.11
Nodes (18): java.security.SecureRandom, org.slf4j.Logger, org.springframework.core.task.TaskExecutor, org.springframework.data.domain.Sort, org.springframework.security.crypto.password.PasswordEncoder, org.springframework.stereotype.Service, org.springframework.transaction.PlatformTransactionManager, AuditChanges (+10 more)

### Community 13 - "DealsFixture"
Cohesion: 0.09
Nodes (5): DealEditIT, DealLinkIT, DealReassignIT, DealsFixture, DealsIT

### Community 14 - "TwoFactorIT"
Cohesion: 0.16
Nodes (4): jakarta.servlet.http.Cookie, org.springframework.test.web.servlet.MvcResult, Base32, TwoFactorIT

### Community 15 - "BalancesTest"
Cohesion: 0.25
Nodes (3): Balances, Result, BalancesTest

### Community 17 - "Part A — Backend (`crm`)"
Cohesion: 0.07
Nodes (27): Explicitly deferred (not in this plan), Global Constraints, Part A — Backend (`crm`), Part B — Frontend (`crm-dashboard-ui`), Redesign #2 Sales Lifecycle — Implementation Plan, Review Focus, Task 10: Payments — record, verify, reject, 2307, Task 11: Release, fulfil, cancel (+19 more)

### Community 18 - "jakarta.persistence.Entity"
Cohesion: 0.09
Nodes (14): jakarta.persistence.Entity, jakarta.persistence.PreUpdate, jakarta.persistence.Table, DeliveryOrderLine, RecoveryCode, StockMovement, StockReceipt, StockReceiptLine (+6 more)

### Community 19 - "PaymentTerms"
Cohesion: 0.10
Nodes (12): CustomerType, BUSINESS, INDIVIDUAL, PaymentTerms, DUE_ON_RECEIPT, NET_15, NET_30, NET_60 (+4 more)

### Community 20 - "ServiceCallLoggingIT"
Cohesion: 0.14
Nodes (6): org.springframework.context.annotation.Import, ServiceCallLoggingIT.Probes, LoggingProbeService, ProbeEvent, Probes, ServiceCallLoggingIT

### Community 21 - "Redesign #3 — Inventory & Fulfilment"
Cohesion: 0.07
Nodes (27): 1. Modules, 2. Data (Flyway, Postgres), 3. Stock rules, 4. Flows, 5. API, 6. Access, 7. Audit and notifications, 8. Frontend (+19 more)

### Community 22 - "SettingKey"
Cohesion: 0.07
Nodes (28): SettingKey, APPROVAL_DISCOUNT_PERCENT_MAX, APPROVAL_WRITE_OFF_VALUE_MAX, COMPANY_ADDRESS, COMPANY_NAME, COMPANY_TIN, FOLLOW_UPS_AUTO_DEAL_STALLED_ENABLED, FOLLOW_UPS_AUTO_FIRST_ORDER_ENABLED (+20 more)

### Community 24 - "Scope"
Cohesion: 0.08
Nodes (18): Deal, DealRef, DealRequest, DealResponse, DealScopes, DealService, DealStage, DISCOVERY (+10 more)

### Community 26 - "ReportRange"
Cohesion: 0.10
Nodes (18): parse(), ReportBy, CUSTOMER, MONTH, PRODUCT, REP, Named, ProductSum (+10 more)

### Community 27 - "DeliveryService"
Cohesion: 0.11
Nodes (14): DeliveryOrder, DeliveryOrderController, DeliveryOrderRepository, DeliveryLineResponse, DeliveryOrderResponse, DeliveryService, DeliveryStatus, CANCELLED (+6 more)

### Community 30 - ".l"
Cohesion: 0.17
Nodes (4): LineStock, AllocatorIT, Line, ReservationsIT

### Community 31 - "LeadDuplicates"
Cohesion: 0.23
Nodes (6): Candidate, ContactMatches, Match, LeadDuplicates, LeadDuplicatesResponse, PhoneKey

### Community 32 - "MeController"
Cohesion: 0.13
Nodes (8): org.springframework.web.bind.annotation.DeleteMapping, ChangePasswordRequest, MeController, UpdateMeRequest, MeResponse, SessionAdmin, SessionController, SessionResponse

### Community 33 - "org.springframework.http.ResponseEntity"
Cohesion: 0.11
Nodes (17): jakarta.validation.ConstraintViolationException, org.springframework.dao.OptimisticLockingFailureException, org.springframework.http.HttpHeaders, org.springframework.http.HttpStatusCode, org.springframework.http.ProblemDetail, org.springframework.http.ResponseEntity, org.springframework.security.access.AccessDeniedException, org.springframework.web.bind.annotation.ExceptionHandler (+9 more)

### Community 34 - "Sheets"
Cohesion: 0.13
Nodes (5): ImportResult, RowError, Sheets, Req, SheetsTest

### Community 36 - ".at"
Cohesion: 0.05
Nodes (6): AdminInsightsIT, ReportsIT, SalesInsightsIT, TargetMilestonesIT, TargetsIT, WarehouseInsightsIT

### Community 37 - ".createdOrder"
Cohesion: 0.06
Nodes (6): jakarta.mail.Multipart, ConfirmIT, OrdersIT, ReleaseCancelIT, ResultActions, SendQuoteIT

### Community 39 - "PdfRenderer"
Cohesion: 0.23
Nodes (5): com.microsoft.playwright.Browser, com.microsoft.playwright.Playwright, jakarta.annotation.PreDestroy, QuoteDocument, PdfRenderer

### Community 40 - "Inventory"
Cohesion: 0.11
Nodes (9): Inventory, Override, Key, OrderStock, ReserveLine, ReturnLine, StockLevel, StockReservation (+1 more)

### Community 41 - "Catalog"
Cohesion: 0.11
Nodes (12): Catalog, Product, ProductRepository, ProductRequest, ProductSheets, AdjustmentService, Allocator, InventoryPermissions (+4 more)

### Community 42 - "AutoFollowUps"
Cohesion: 0.14
Nodes (7): org.springframework.boot.autoconfigure.condition.ConditionalOnProperty, org.springframework.scheduling.annotation.Scheduled, AutoFollowUps, AutoFollowUpsSchedule, FollowUpReminder, FollowUpReminderSchedule, FollowUpDayTest

### Community 43 - "org.springframework.data.jpa.repository.JpaRepository"
Cohesion: 0.07
Nodes (17): org.springframework.data.jpa.repository.JpaRepository, org.springframework.data.jpa.repository.JpaSpecificationExecutor, org.springframework.data.jpa.repository.Lock, org.springframework.data.jpa.repository.Modifying, org.springframework.data.jpa.repository.Query, ApprovalRequestRepository, AuditEventRepository, DealRepository (+9 more)

### Community 45 - "AttachmentService"
Cohesion: 0.18
Nodes (9): Attachment, AttachmentRepository, AttachmentResponse, AttachmentService, Checked, SubjectType, CUSTOMER, INVOICE (+1 more)

### Community 46 - "FollowUpService"
Cohesion: 0.09
Nodes (17): FollowUp, FollowUpCounts, Bucket, DONE, OVERDUE, TODAY, UPCOMING, FollowUpDay (+9 more)

### Community 48 - "OrderStatus"
Cohesion: 0.18
Nodes (9): org.junit.jupiter.params.provider.MethodSource, OrderStateMachine, OrderStatus, CANCELLED, CONFIRMED, DRAFT, FULFILLED, QUOTED (+1 more)

### Community 49 - "org.springframework.context.annotation.Bean"
Cohesion: 0.07
Nodes (29): jakarta.servlet.FilterChain, org.springframework.boot.web.servlet.FilterRegistrationBean, org.springframework.context.annotation.Bean, org.springframework.context.annotation.Configuration, org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity, org.springframework.security.config.annotation.web.builders.HttpSecurity, org.springframework.security.config.annotation.web.configuration.EnableWebSecurity, org.springframework.security.web.SecurityFilterChain (+21 more)

### Community 50 - "TransferService"
Cohesion: 0.06
Nodes (19): CatalogPermissions, CustomerPermissions, CoverShortage, ReceiveLineRequest, ReceiveRequest, StockTransfer, TransferController, TransferLineRequest (+11 more)

### Community 51 - "Redesign #4 — POS"
Cohesion: 0.12
Nodes (15): Backend (`crm`), Decisions, Elsewhere, Endpoints — `PosController`, `ADMIN` and `SALES`, Frontend (`crm-dashboard-ui`), Goal, Other backend changes, Out of scope (+7 more)

### Community 52 - "ApprovalStatus"
Cohesion: 0.12
Nodes (11): org.springframework.transaction.event.TransactionalEventListener, ApprovalDecided, ApprovalStatus, APPROVED, CANCELLED, PENDING, REJECTED, AutoCloseListeners (+3 more)

### Community 53 - "AppUser"
Cohesion: 0.11
Nodes (10): AppUser, LoginRequest, LoginService, Outcome, LoginThrottle, Result, ALREADY_LOCKED, COUNTED (+2 more)

### Community 54 - "ProductUnit"
Cohesion: 0.10
Nodes (20): com.fasterxml.jackson.annotation.JsonInclude, ProductUnit, BOX, G, KG, L, M, ML (+12 more)

### Community 56 - "FollowUpsFixture"
Cohesion: 0.22
Nodes (3): FollowUpEditIT, PlatformTransactionManager, FollowUpsFixture

### Community 57 - "Part A — Backend (`crm`)"
Cohesion: 0.09
Nodes (21): Explicitly deferred (not in this plan), Global Constraints, Part A — Backend (`crm`), Part B — Frontend (`crm-dashboard-ui`), Redesign #3 Inventory & Fulfilment — Implementation Plan, Review Focus, Task 10: Stock page, ledger, stock in and adjust, Task 11: Transfers (+13 more)

### Community 59 - "OrderService"
Cohesion: 0.09
Nodes (14): RequiredApproval, CustomerSalesProfile, ApprovalChecks, DiscountType, AMOUNT, PERCENT, LineRequest, OrderRequest (+6 more)

### Community 60 - "V5__inventory_fulfilment.sql"
Cohesion: 0.12
Nodes (27): user_warehouse, warehouse, warehouse_code_uq, warehouse_one_default, delivery_order_line_delivery_order_idx, stock_transfer_line_transfer_idx, product, product_name_idx (+19 more)

### Community 61 - "UI polish bundle — design (2026-11-17)"
Cohesion: 0.10
Nodes (19): 1. Sorting on every table, 2. Order search by customer name, 3. Image viewer, 4. Notification date and time, 5. Dashboard style C, Backend, Component, Dashboards (+11 more)

### Community 62 - "Deals / opportunities (F-130) — design"
Cohesion: 0.18
Nodes (10): API, Approach, Data — Flyway `V15__deals.sql`, Deals / opportunities (F-130) — design, Decisions (from the user), Goal, Out of scope, Rules (+2 more)

### Community 63 - "java.time.YearMonth"
Cohesion: 0.06
Nodes (28): java.time.YearMonth, org.springframework.cache.annotation.Cacheable, org.springframework.jdbc.core.namedparam.MapSqlParameterSource, AdminInsights, Margin, MonthAmount, Operations, ProductRow (+20 more)

### Community 64 - "Follow-ups (F-141) — design"
Cohesion: 0.20
Nodes (9): API — `/api/follow-ups`, Data — Flyway `V11__follow_ups.sql`, Decisions (from the user), Follow-ups (F-141) — design, Goal, Out of scope, Rules, Screens (crm-dashboard-ui) (+1 more)

### Community 65 - "Part A — Backend (`crm`)"
Cohesion: 0.06
Nodes (31): Explicitly deferred (not in this plan), Global Constraints, Part A — Backend (`crm`), Part B — Frontend (`crm-dashboard-ui`), Redesign #1 Foundation — Implementation Plan, Review Focus, Task 10: Identity — TOTP 2FA and recovery codes, Task 11: Notifications (carried over, re-keyed to users) (+23 more)

### Community 67 - "Settings"
Cohesion: 0.17
Nodes (3): AppSettingRepository, SuppressWarnings, Settings

### Community 68 - "LoginIT"
Cohesion: 0.21
Nodes (3): javax.sql.DataSource, org.springframework.session.data.redis.RedisIndexedSessionRepository, LoginIT

### Community 69 - "LeadService"
Cohesion: 0.21
Nodes (6): com.fasterxml.jackson.annotation.JsonIgnore, jakarta.validation.constraints.AssertTrue, Lead, LeadConvertRequest, LeadRequest, LeadService

### Community 70 - "TotpService"
Cohesion: 0.11
Nodes (6): javax.crypto.SecretKey, QrCodes, RecoveryCodesResponse, TotpService, TotpSetupResponse, Base32Test

### Community 72 - "AppUserRepository"
Cohesion: 0.14
Nodes (4): AppUserRepository, BootstrapAdmin, InviteUserRequest, UserAdminService

### Community 74 - "PaymentService"
Cohesion: 0.07
Nodes (24): OrderStockMapper, Payment, PaymentMethod, BANK_TRANSFER, CARD, CASH, CHECK, GCASH (+16 more)

### Community 75 - ".delivered"
Cohesion: 0.19
Nodes (6): ApplicationEventPublisher, PlatformTransactionManager, RestockRuleIT, ApplicationEventPublisher, PlatformTransactionManager, WinBackRuleIT

### Community 76 - "Automatic follow-ups — design (2026-11-17)"
Cohesion: 0.13
Nodes (14): 1. Data — `V24__auto_follow_ups.sql`, 2. Settings (`SettingKey`, admin-editable, validated like existing keys), 3. Daily scan — `followups/AutoFollowUps`, 4. Rules (each is one class in `followups/rules/`, implementing `AutoRule`), 5. Overdue notification, 6. API, 7. Frontend (`crm-dashboard-ui`), 8. Testing (+6 more)

### Community 77 - "NotificationStreams"
Cohesion: 0.17
Nodes (9): org.springframework.data.redis.connection.Message, org.springframework.data.redis.connection.MessageListener, org.springframework.web.servlet.mvc.method.annotation.SseEmitter, Override, NotificationStreams, StreamController, OpenApiSnapshotTest, SseEventBuilder (+1 more)

### Community 79 - "OrderService.java"
Cohesion: 0.05
Nodes (41): ProductInfo, NotificationType, APPROVAL_DECIDED, APPROVAL_REQUESTED, DELIVERY_CREATED, FOLLOW_UP_OVERDUE, FOLLOW_UPS_DUE, LOW_STOCK (+33 more)

### Community 80 - "Backend (`crm/crm`)"
Cohesion: 0.12
Nodes (15): Backend (`crm/crm`), Dashboards (#4) Implementation Plan, Frontend (`crm-dashboard-ui`), Global Constraints, Review Focus, Task 10: End-to-end smoke, Task 1: Snapshots at confirm and the V10 migration, Task 2: Insights module, month parsing and sales targets (+7 more)

### Community 81 - "org.springframework.transaction.annotation.Transactional"
Cohesion: 0.08
Nodes (9): jakarta.validation.Validator, org.springframework.transaction.annotation.Transactional, ContactRequest, CustomerContact, CustomerContactRepository, CustomerRepository, CustomerRequest, Customers (+1 more)

### Community 82 - "jakarta.servlet.http.HttpServletRequest"
Cohesion: 0.09
Nodes (15): jakarta.servlet.http.HttpServletRequest, jakarta.servlet.http.HttpServletResponse, org.springframework.security.core.Authentication, org.springframework.security.web.context.HttpSessionSecurityContextRepository, org.springframework.security.web.csrf.CsrfToken, AuthController, Override, LoginResponse (+7 more)

### Community 83 - "Backend (`crm/crm`)"
Cohesion: 0.11
Nodes (17): Backend (`crm/crm`), Frontend (`crm-dashboard-ui`), Global Constraints, Notification copy (exact), Notifications Implementation Plan, Review Focus, Task 10: Remove Inbox, Task 11: Graph refresh and final check (+9 more)

### Community 84 - "FollowUpRule"
Cohesion: 0.11
Nodes (13): FollowUpRule, DEAL_STALLED, FIRST_ORDER, PAYMENT_OVERDUE, QUOTE_CHASE, QUOTE_DECLINED, QUOTE_EXPIRING, RESTOCK (+5 more)

### Community 85 - "app_user"
Cohesion: 0.18
Nodes (20): app_setting, app_user, app_user_email_uq, approval_one_pending, approval_request, approval_subject_idx, audit_actor_idx, audit_at_idx (+12 more)

### Community 87 - "Frontend (`crm-dashboard-ui`)"
Cohesion: 0.13
Nodes (14): Backend (`crm/crm`), Frontend (`crm-dashboard-ui`), Global Constraints, POS (#4) Implementation Plan, Review Focus, Task 1: Sales channel and POS warehouse setting, Task 2: Upload proof after recording a payment, Task 3: POS product list (+6 more)

### Community 88 - "ServiceCallLogging.java"
Cohesion: 0.23
Nodes (8): java.lang.reflect.Method, org.aopalliance.intercept.MethodInvocation, org.springframework.aop.Advisor, org.springframework.aop.Pointcut, org.springframework.beans.factory.config.BeanPostProcessor, org.springframework.context.annotation.Role, org.springframework.core.ParameterNameDiscoverer, ServiceCallLogging

### Community 90 - "Review Focus"
Cohesion: 0.17
Nodes (11): Automatic Follow-ups Implementation Plan, Global Constraints, Review Focus, Task 1: Schema, settings keys and FollowUp fields, Task 2: Engine (`AutoFollowUps`, `AutoRule`, `RestockRhythm`), Task 3: Customer rules (RESTOCK, WIN_BACK, FIRST_ORDER) and the order-confirmed close, Task 4: Order rules (QUOTE_CHASE, QUOTE_EXPIRING, QUOTE_DECLINED, PAYMENT_OVERDUE) and their closes, Task 5: DEAL_STALLED and the deal-stage close (+3 more)

### Community 91 - "HtmlMail"
Cohesion: 0.17
Nodes (6): org.thymeleaf.TemplateEngine, IdentityMailer, InviteIssued, PortalMailer, Attachment, HtmlMail

### Community 92 - "ApprovalType"
Cohesion: 0.12
Nodes (9): ApprovalType, BELOW_COST, CREDIT_LIMIT, DISCOUNT, INVOICE_VOID, REFUND, STOCK_WRITE_OFF, Guarded (+1 more)

### Community 93 - "Design"
Cohesion: 0.12
Nodes (16): 1. Request line — `shared/AccessLogFilter`, 2. Business step line — `AuditLog.record`, 3. Service calls — `shared/ServiceCallLogging`, 4. Redaction — `shared/LogRedactor`, 5. Background work, 6. Changing detail at runtime, 7. Files — `logback-spring.xml`, 8. UI (crm-dashboard-ui) (+8 more)

### Community 94 - "Users"
Cohesion: 0.14
Nodes (15): org.springframework.context.ApplicationEventPublisher, Approvals, Fulfilment, Users, TargetMilestones, Notifications, AutoConfirmListener, CreditMemoDocument (+7 more)

### Community 96 - ".product"
Cohesion: 0.16
Nodes (3): DeliveryLine, DeliveryRequest, ApplicationContext

### Community 98 - "Review Focus"
Cohesion: 0.17
Nodes (11): Global Constraints, Redis Phase Implementation Plan, Review Focus, Task 0: Commit the MinIO work, Task 1: Redis wiring and token revocation (R-01, R-02, R-09 part), Task 2: Rate limiting (R-03), Task 3: Single-flight locks on customer links and staff accept (R-04), Task 4: Run the quotation expiry job on one instance (R-05) (+3 more)

### Community 99 - "ErrorCode"
Cohesion: 0.05
Nodes (46): org.junit.jupiter.params.provider.Arguments, org.springframework.boot.context.event.ApplicationReadyEvent, DomainException, ErrorCode, ACCOUNT_LOCKED, APPROVAL_REQUIRED, ATTACHMENT_NOT_YOURS, ATTACHMENT_READ_ONLY (+38 more)

### Community 100 - "Review Focus"
Cohesion: 0.15
Nodes (12): Deals (F-130) Implementation Plan, Global Constraints, Review Focus, Task 1: Table, create, list, get, summary, Task 2: Edit and stages (except Won), Task 3: Linking orders, Task 4: Won — automatic and by hand, Task 5: Follow the customer on reassignment (+4 more)

### Community 101 - "SettingKey.java"
Cohesion: 0.25
Nodes (12): bankAccounts(), bool(), field(), invalid(), number(), of(), positiveIdOrNull(), text() (+4 more)

### Community 102 - "IntegrationTest.java"
Cohesion: 0.16
Nodes (15): org.springframework.boot.test.context.SpringBootTest, org.springframework.boot.test.context.TestConfiguration, org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc, org.springframework.context.ApplicationContextInitializer, org.springframework.context.ConfigurableApplicationContext, org.springframework.test.context.ActiveProfiles, org.springframework.test.context.ContextConfiguration, org.testcontainers.containers.PostgreSQLContainer (+7 more)

### Community 103 - "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate"
Cohesion: 0.10
Nodes (8): java.sql.ResultSet, org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate, Due, FollowUpQueries, Overdue, InventoryOverviewService, Raw, SalesReports

### Community 105 - "InvoiceService"
Cohesion: 0.11
Nodes (9): Invoice, InvoiceResponse, InvoiceService, InvoiceStatus, ISSUED, VOID, InvoiceSummary, ReissueRequest (+1 more)

### Community 106 - "RedisTestSupport"
Cohesion: 0.14
Nodes (8): AssertTrue, org.springframework.context.annotation.AnnotationConfigApplicationContext, org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory, org.testcontainers.containers.GenericContainer, RedisListenerConfigIT, RedisTestSupport, CacheConfigIT, SummarySource

### Community 107 - "LogRedactorTest"
Cohesion: 0.17
Nodes (5): Create, Customer, Login, LogRedactorTest, Node

### Community 108 - "RateLimiter"
Cohesion: 0.18
Nodes (9): org.junit.jupiter.params.ParameterizedTest, org.junit.jupiter.params.provider.CsvSource, org.springframework.mock.web.MockFilterChain, org.springframework.mock.web.MockHttpServletResponse, RateLimiter, Override, RateLimitFilter, Rule (+1 more)

### Community 109 - "AttachmentAccess"
Cohesion: 0.30
Nodes (4): AttachmentAccess, InvoiceRef, InvoiceRow, OrderRow

### Community 110 - "Redesign #1 — Foundation"
Cohesion: 0.07
Nodes (28): 10. Testing, 11. Deployment changes, 1. Backend module layout, 2. Data conventions (Postgres), 3. Identity & sessions, 4. Authorization & scoping, 5. Approvals, 6. Audit (+20 more)

### Community 111 - "sales_order"
Cohesion: 0.18
Nodes (15): order_revision, payment_status_verified_idx, sales_order_rep_idx, sales_target, document_counter, invoice, invoice_one_active, invoice_order_idx (+7 more)

### Community 112 - "CreateApprovalRequest"
Cohesion: 0.19
Nodes (5): ApprovalRequest, ApprovalResponse, Link, CreateApprovalRequest, DecisionRequest

### Community 114 - "Review Focus"
Cohesion: 0.14
Nodes (13): Global Constraints, Leads (F-120) Implementation Plan, Review Focus, Task 10: e2e, Task 1: Migration, lead sources on customers, Task 2: Lead sources API, Task 3: Leads module — create, list, get, counts, Task 4: Edit, status, assign (+5 more)

### Community 116 - ".issue"
Cohesion: 0.13
Nodes (10): jakarta.persistence.EntityManager, Subject, TokenPurpose, INVITE, PASSWORD_RESET, PAYMENT_PROOF, PORTAL_LOGIN, QUOTE_RESPONSE (+2 more)

### Community 117 - "Leads (F-120) — design"
Cohesion: 0.18
Nodes (10): API, Approach, Data — Flyway `V14__leads.sql`, Decisions (from the user), Goal, Leads (F-120) — design, Out of scope, Rules (+2 more)

### Community 118 - "org.springframework.data.jpa.domain.Specification"
Cohesion: 0.13
Nodes (5): org.springframework.data.jpa.domain.Specification, Customer, CustomerScopes, LeadScopes, OrderScopes

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

### Community 127 - "CurrentActor"
Cohesion: 0.10
Nodes (5): AttachmentController, InsightsController, SalesReportController, ReportController, CurrentActor

### Community 128 - "Review Focus"
Cohesion: 0.22
Nodes (8): Global Constraints, Reports (F-172) Implementation Plan, Review Focus, Task 1: Range parsing and error code, Task 2: Month, rep and customer reports plus endpoint, Task 3: Product report with pro-rata split, Task 4: Reports page, Task 5: End-to-end smoke

### Community 129 - "org.springframework.stereotype.Component"
Cohesion: 0.18
Nodes (6): org.springframework.context.event.EventListener, org.springframework.stereotype.Component, CustomerReassigned, DealReassignmentListener, OrderConfirmedListener, ReassignmentListener

### Community 131 - "LeadController"
Cohesion: 0.13
Nodes (9): LeadController, LeadResponse, LeadStatus, CONTACTED, CONVERTED, LOST, NEW, QUALIFIED (+1 more)

### Community 134 - "Attachments (M-08) — design"
Cohesion: 0.20
Nodes (9): API — `/api/attachments`, Attachments (M-08) — design, Data — Flyway `V13__attachments.sql`, Decisions (from the user), Goal, Out of scope, Rules, Screens (crm-dashboard-ui) (+1 more)

### Community 135 - "SecurityMatrixTest"
Cohesion: 0.19
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

### Community 143 - "org.springframework.data.redis.core.StringRedisTemplate"
Cohesion: 0.14
Nodes (10): lombok.extern.slf4j.Slf4j, lombok.RequiredArgsConstructor, org.springframework.beans.factory.annotation.Autowired, org.springframework.data.redis.core.script.RedisScript, org.springframework.data.redis.core.StringRedisTemplate, org.springframework.util.AntPathMatcher, org.springframework.web.util.ContentCachingResponseWrapper, DistributedLock (+2 more)

### Community 144 - "RetryingListenerContainer"
Cohesion: 0.31
Nodes (5): org.springframework.data.redis.connection.RedisConnectionFactory, org.springframework.data.redis.listener.RedisMessageListenerContainer, Override, RedisListenerConfig, RetryingListenerContainer

### Community 145 - "Warehouses"
Cohesion: 0.10
Nodes (13): LabelController, LabelKind, DO, PR, TR, WH, Label, LabelService (+5 more)

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

### Community 155 - "WinBackRule"
Cohesion: 0.22
Nodes (3): Reorders, Override, WinBackRule

### Community 156 - "RedisSessionAdmin"
Cohesion: 0.36
Nodes (4): org.springframework.session.FindByIndexNameSessionRepository, org.springframework.session.Session, Override, RedisSessionAdmin

### Community 158 - "CrmApplication"
Cohesion: 0.53
Nodes (4): org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration, org.springframework.scheduling.annotation.EnableScheduling, CrmApplication

### Community 161 - "org.springframework.core.env.Environment"
Cohesion: 0.53
Nodes (3): jakarta.annotation.PostConstruct, org.springframework.core.env.Environment, EncryptionSecretsCheck

### Community 163 - ".receive"
Cohesion: 0.29
Nodes (4): ReceiptLineRequest, ReceiptRequest, ReceiptLineResponse, ReceiptResponse

### Community 164 - "Production cutover (P5-2)"
Cohesion: 0.33
Nodes (5): Afterwards, Before the window, Production cutover (P5-2), Rollback, Steps

### Community 166 - "follow_up"
Cohesion: 0.36
Nodes (6): follow_up, follow_up_customer_idx, follow_up_open_idx, follow_up_order_idx, auto_follow_up_run, follow_up_open_rule_idx

### Community 167 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 168 - "MinioHealthIndicator"
Cohesion: 0.32
Nodes (4): org.springframework.boot.health.contributor.Health, org.springframework.boot.health.contributor.HealthIndicator, Override, MinioHealthIndicator

### Community 169 - "RestockRule.java"
Cohesion: 0.25
Nodes (4): RestockRhythm, Delivery, Override, RestockRule

### Community 172 - "transform.sql"
Cohesion: 0.13
Nodes (8): l, o, pg_temp.sys(), pg_temp.uid(), attachment, attachment_subject_idx, user_role, user_role_role_idx

### Community 190 - "Notification"
Cohesion: 0.36
Nodes (4): org.springframework.data.domain.Page, org.springframework.data.domain.Pageable, Notification, NotificationRepository

### Community 193 - "PortalOrderService"
Cohesion: 0.08
Nodes (16): DeclineRequest, PortalDeliveryStatus, DELIVERED, OUT_FOR_DELIVERY, PREPARING, PortalOrderResponse, PortalOrderService, PortalRefundResponse (+8 more)

### Community 194 - "DealOrder"
Cohesion: 0.31
Nodes (5): DealOrder.Key, jakarta.persistence.IdClass, DealOrder, Key, DealOrderRepository

### Community 195 - "StaffPrincipal"
Cohesion: 0.13
Nodes (11): org.springframework.http.MediaType, org.springframework.security.authentication.UsernamePasswordAuthenticationToken, org.springframework.security.core.AuthenticatedPrincipal, org.springframework.security.core.authority.SimpleGrantedAuthority, org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, ContactPrincipal, Override (+3 more)

### Community 203 - ".record"
Cohesion: 0.06
Nodes (13): RefundDisposition, DAMAGED, RESTOCK, RefundKind, GOODS_RETURN, MONEY_ONLY, RefundLineRequest, RefundPaid (+5 more)

### Community 204 - "StorageService"
Cohesion: 0.12
Nodes (8): io.minio.MinioClient, org.junit.jupiter.api.BeforeAll, org.testcontainers.containers.MinIOContainer, org.testcontainers.junit.jupiter.Testcontainers, Detected, Pattern, StorageService, StorageServiceIT

### Community 207 - "LeadSources"
Cohesion: 0.18
Nodes (5): LeadSource, LeadSourceRepository, LeadSourceRequest, LeadSourceResponse, LeadSources

### Community 208 - "refund"
Cohesion: 0.60
Nodes (5): money_movement, refund, refund_line, refund_line_refund_idx, refund_order_idx

### Community 211 - ".displayNames"
Cohesion: 0.25
Nodes (4): SalesTarget, SalesTargetRepository, TargetRow, TargetService

### Community 213 - "RefundStatus"
Cohesion: 0.29
Nodes (6): RefundStatus, APPROVED, CANCELLED, PAID, REJECTED, REQUESTED

### Community 214 - ".shouldNotFilter"
Cohesion: 0.38
Nodes (3): org.junit.jupiter.params.provider.ValueSource, Override, IdempotencyFilterPathsTest

### Community 215 - ".json"
Cohesion: 0.14
Nodes (3): CreditMemoIT, Environment, PortalOrdersIT

### Community 217 - "org.springframework.web.bind.annotation.RestController"
Cohesion: 0.06
Nodes (23): org.springframework.boot.logging.LoggingSystem, org.springframework.http.HttpStatus, org.springframework.validation.annotation.Validated, org.springframework.web.bind.annotation.PutMapping, org.springframework.web.bind.annotation.RequestMapping, org.springframework.web.bind.annotation.ResponseStatus, org.springframework.web.bind.annotation.RestController, ActivityController (+15 more)

### Community 218 - "RuleSettings"
Cohesion: 0.10
Nodes (12): AutoRule, Candidate, DealStalledRule, Override, FirstOrderRule, Override, Override, PaymentOverdueRule (+4 more)

### Community 219 - ".scope"
Cohesion: 0.06
Nodes (16): org.springframework.security.access.prepost.PreAuthorize, org.springframework.web.bind.annotation.PostMapping, org.springframework.web.multipart.MultipartFile, AssignmentRequest, ContactResponse, CustomerController, CustomerResponse, DealController (+8 more)

### Community 220 - "FakeRule"
Cohesion: 0.36
Nodes (3): FakeRule, Fakes, Override

### Community 228 - "CacheConfig.java"
Cohesion: 0.43
Nodes (5): org.springframework.cache.annotation.CachingConfigurer, org.springframework.cache.annotation.EnableCaching, org.springframework.cache.interceptor.CacheErrorHandler, CacheConfig, Override

## Knowledge Gaps
- **760 isolated node(s):** `o`, `l`, `01-roles.sh script`, `PENDING`, `APPROVED` (+755 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 1029 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **84 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Scope` connect `Scope` to `SalesFixture`, `org.springframework.jdbc.core.JdbcTemplate`, `org.springframework.web.bind.annotation.GetMapping`, `AuditLog`, `AttachmentsFixture`, `ReportRange`, `LeadDuplicates`, `AttachmentService`, `FollowUpService`, `.csrf`, `OrderService`, `PortalOrderService`, `StaffPrincipal`, `LeadService`, `PaymentService`, `.record`, `OrderService.java`, `org.springframework.transaction.annotation.Transactional`, `.scope`, `Users`, `ErrorCode`, `org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate`, `InvoiceService`, `AttachmentAccess`, `org.springframework.data.jpa.domain.Specification`, `CurrentActor`?**
  _High betweenness centrality (0.038) - this node is a cross-community bridge._
- **Why does `Role` connect `org.springframework.jdbc.core.JdbcTemplate` to `SalesFixture`, `MailIntegrationTest`, `.lead`, `org.springframework.web.bind.annotation.GetMapping`, `UserAdminIT`, `.customer`, `AuditLog`, `DealsFixture`, `TwoFactorIT`, `jakarta.persistence.Entity`, `Scope`, `AttachmentsFixture`, `ReportRange`, `SettingsIT`, `MeController`, `LogLevelIT`, `.at`, `AutoFollowUps`, `org.springframework.data.jpa.repository.JpaRepository`, `AppUser`, `.csrf`, `FollowUpsFixture`, `StaffPrincipal`, `LoginIT`, `SessionManagementIT`, `AppUserRepository`, `.record`, `CatalogIT`, `OrderService.java`, `org.springframework.transaction.annotation.Transactional`, `AuditLogIT`, `.displayNames`, `org.springframework.web.bind.annotation.RestController`, `ApprovalsIT`, `ApprovalType`, `Users`, `.product`, `ErrorCode`, `org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate`, `WarehousesIT`, `AttachmentAccess`, `CustomerScopingIT`, `LeadSourcesIT`, `CurrentActor`?**
  _High betweenness centrality (0.034) - this node is a cross-community bridge._
- **Why does `AuditAction` connect `AuditAction` to `MeController`, `ErrorCode`, `org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate`, `org.springframework.web.bind.annotation.GetMapping`, `PaymentService`, `.record`, `AuditLog`, `AttachmentService`, `OrderService.java`, `org.springframework.transaction.annotation.Transactional`, `AppUser`, `org.springframework.web.bind.annotation.RestController`, `Users`, `AuditEvent`?**
  _High betweenness centrality (0.033) - this node is a cross-community bridge._
- **What connects `o`, `l`, `01-roles.sh script` to the rest of the system?**
  _760 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `SalesFixture` be split into smaller, more focused modules?**
  _Cohesion score 0.10823529411764705 - nodes in this community are weakly interconnected._
- **Should `org.springframework.jdbc.core.JdbcTemplate` be split into smaller, more focused modules?**
  _Cohesion score 0.09054993678887484 - nodes in this community are weakly interconnected._
- **Should `MailIntegrationTest` be split into smaller, more focused modules?**
  _Cohesion score 0.10953058321479374 - nodes in this community are weakly interconnected._