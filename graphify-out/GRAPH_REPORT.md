# Graph Report - crm-status-emails  (2026-11-18)

## Corpus Check
- 638 files · ~292,528 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 18 file(s) not represented in the graph (top: .properties 6, (none) 4, .example 2)

## Summary
- 5495 nodes · 21007 edges · 219 communities (157 shown, 62 thin omitted)
- Extraction: 80% EXTRACTED · 20% INFERRED · 0% AMBIGUOUS · INFERRED: 4266 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `f311f71b`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- .createdOrder
- org.springframework.jdbc.core.JdbcTemplate
- org.junit.jupiter.api.Test
- ErrorCode
- CurrentActor
- org.springframework.web.bind.annotation.GetMapping
- org.springframework.stereotype.Component
- AuditAction
- ApprovalsIT
- PageQuery
- Customers
- OrderService
- .lead
- DealService
- .transition
- DealsFixture
- .salesProfileVisible
- .record
- StorageServiceIT
- java.time.YearMonth
- TransferService
- .scope
- CustomerStatusMailsIT
- jakarta.servlet.http.HttpServletRequest
- jakarta.persistence.Entity
- org.junit.jupiter.api.AfterEach
- .compute
- Inventory
- NotificationStreamsIT
- .receive
- RefundsIT
- org.springframework.transaction.annotation.Transactional
- Task 2: Insights module, month parsing and sales targets
- .contains
- DeliveryOrdersIT
- OrderResponse
- LeadService
- MailIntegrationTest
- RefundService
- StaffPrincipal
- org.springframework.context.annotation.Bean
- PortalOrderService
- Task 9: Identity — tokens, mail, invites, user admin, password reset, bootstrap
- SettingKey
- Invoice
- org.springframework.data.jpa.repository.Query
- TwoFactorIT
- org.springframework.http.ResponseEntity
- PaymentsIT
- InsightsFixture
- AttachmentService
- RuleSettings
- .csrf
- org.springframework.data.jpa.domain.Specification
- org.springframework.data.jpa.repository.JpaRepository
- StockReadsIT
- Sheets
- .run
- ProductUnit
- CustomersIT
- TransfersIT
- OrderBrief
- DealStalledRuleIT
- OrderAction
- Scope
- LeadSources
- FollowUpRule
- V5__inventory_fulfilment.sql
- NotificationType
- RateLimitFilterIT
- PortalOrdersIT
- .at
- LoginIT
- org.springframework.scheduling.annotation.Scheduled
- FollowUpsIT
- .customer
- PortalAuthIT
- DeliveryOrder
- .product
- org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
- RequiredApproval
- .toUser
- Redesign #3 — Inventory & Fulfilment
- java.util.regex.Pattern
- Part B — Frontend (`crm-dashboard-ui`)
- StorageService
- .update
- sales_order
- CacheConfig
- .json
- AdjustmentsIT
- AppUser
- org.springframework.data.redis.core.StringRedisTemplate
- WarehousesIT
- Task 13: Customers module (first scoped slice)
- CatalogIT
- LogRedactorTest
- ServiceCallLoggingIT
- QuoteRulesIT
- app_user
- SessionManagementIT
- .matchContact
- AutoFollowUpsIT
- NotificationStreams
- LoginThrottle
- .list
- UI polish bundle — design (2026-11-17)
- ReportRange
- .report
- IdempotencyFilterIT
- FollowUpEditIT
- CreditMemoIT
- TotpService
- AttachmentsFixture
- SessionController
- TaxClass
- .decode
- SettingKey.java
- .recordFor
- DealWonIT
- ServiceCallLogging
- Backend (`crm`)
- .create
- AttachmentsIT
- LeadSourcesIT
- Task 10: Identity — TOTP 2FA and recovery codes
- FollowUpReassignIT
- TargetMilestonesIT
- Automatic follow-ups — design (2026-11-17)
- SecurityMatrixTest
- OpenApiSnapshotTest
- Task 1: Range parsing and error code
- Redesign #1 — Foundation
- .idsForWarehouse
- MeController
- Task 1: Branch, dependencies, legacy removal, test harness
- MovementType
- PasswordResetIT
- Refunds — design (2026-11-16)
- LeadSourceBackfillIT
- RedisSessionAdmin
- RestockRule.java
- PaymentOverdueRule
- .receive
- .customers
- ScanIT
- Frontend (`crm-dashboard-ui`)
- Review Focus
- Notifications Implementation Plan
- Attachments (M-08) — design
- Follow-ups (F-141) — design
- .of
- CustomerSourceIT
- LogLevelIT
- DealOrder
- transform.sql
- Leads (F-120) — design
- package.json
- RestockRhythmTest
- LogRedactor
- lead
- FakeRule
- PaymentOverdueRuleIT
- Redesign #2 — Sales lifecycle
- 3. Order lifecycle
- FileController.java
- .check
- follow_up
- migrate.py
- LogLevelController.java
- ReportBy
- .redeem
- RefundStatus
- deal
- Production cutover (P5-2)
- 9. Frontend (`crm-dashboard-ui`)
- 2. Data model
- 4. Money, invoices and payments
- Reports (F-172)
- CrmApplication
- .call
- TestController
- 5. Access and API
- SalesTarget
- InventoryOverviewIT
- gradlew
- DocsDisabledTest
- .availability
- .requireNotFuture
- ApprovalPushAfterCommitIT
- SummarySource
- Context
- SalesTime
- user_role
- PdfText
- 01-roles.sh
- V12__follow_up_reminder_run.sql

## God Nodes (most connected - your core abstractions)
1. `Role` - 195 edges
2. `IntegrationTest` - 173 edges
3. `Scope` - 151 edges
4. `AuditAction` - 137 edges
5. `CurrentActor` - 129 edges
6. `AuditLog` - 123 edges
7. `ErrorCode` - 116 edges
8. `Customers` - 104 edges
9. `DomainException` - 93 edges
10. `PageQuery` - 82 edges

## Surprising Connections (you probably didn't know these)
- `Portal accept and decline` --references--> `ApprovalDecided`  [INFERRED]
  docs/superpowers/specs/2026-10-14-sales-lifecycle-design.md → src/main/java/ph/thecoffeejunkie/crm/approvals/ApprovalDecided.java
- `API` --references--> `AuditAction`  [INFERRED]
  docs/superpowers/specs/2026-11-05-leads-design.md → src/main/java/ph/thecoffeejunkie/crm/audit/AuditAction.java
- `4. Rules (each is one class in `followups/rules/`, implementing `AutoRule`)` --references--> `AutoRule`  [INFERRED]
  docs/superpowers/specs/2026-11-17-auto-follow-ups-design.md → src/main/java/ph/thecoffeejunkie/crm/followups/AutoRule.java
- `6. API` --references--> `FollowUpResponse`  [INFERRED]
  docs/superpowers/specs/2026-11-17-auto-follow-ups-design.md → src/main/java/ph/thecoffeejunkie/crm/followups/FollowUpResponse.java
- `Task 7: Insights page for sales and warehouse, routing and nav` --references--> `SalesInsights`  [INFERRED]
  docs/superpowers/plans/2026-11-05-dashboards.md → src/main/java/ph/thecoffeejunkie/crm/insights/SalesInsights.java

## Import Cycles
- None detected.

## Communities (219 total, 62 thin omitted)

### Community 0 - ".createdOrder"
Cohesion: 0.06
Nodes (17): Task 14: Missing-2307 report, customer order/invoice lists, final sweep, Task 6: Orders — create, edit, read, list, scope, history, Task 8: Confirm and the approval checks, Global Constraints, Review Focus, Task 1: Backend sort infrastructure (`SortRequest`) applied to Orders and Customers, Task 2: Order search by customer name, Task 3: Sort keys on every other paged endpoint (+9 more)

### Community 1 - "org.springframework.jdbc.core.JdbcTemplate"
Cohesion: 0.10
Nodes (24): org.junit.jupiter.api.BeforeEach, org.springframework.cache.CacheManager, org.springframework.jdbc.core.JdbcTemplate, org.springframework.test.context.event.ApplicationEvents, org.springframework.test.context.event.RecordApplicationEvents, org.springframework.test.web.servlet.MockMvc, org.springframework.test.web.servlet.request.RequestPostProcessor, org.springframework.test.web.servlet.ResultActions (+16 more)

### Community 2 - "org.junit.jupiter.api.Test"
Cohesion: 0.05
Nodes (17): Task 2: Shared kernel — errors, request id, principals, paging, Task 3: Baseline schema and database roles, Task 5: Settings module, Task 1: Schema V2 and sales settings, Task 1: Schema V5, settings and shared document numbers, org.junit.jupiter.api.Test, org.springframework.mock.env.MockEnvironment, StorageDetectTest (+9 more)

### Community 3 - "ErrorCode"
Cohesion: 0.05
Nodes (47): org.springframework.boot.context.event.ApplicationReadyEvent, org.springframework.stereotype.Service, AuditChanges, DomainException, ErrorCode, ACCOUNT_LOCKED, APPROVAL_REQUIRED, ATTACHMENT_NOT_YOURS (+39 more)

### Community 4 - "CurrentActor"
Cohesion: 0.08
Nodes (29): jakarta.validation.Validator, org.springframework.context.ApplicationEventPublisher, org.springframework.data.domain.Sort, Approvals, AuditLog, Logger, Pattern, Catalog (+21 more)

### Community 5 - "org.springframework.web.bind.annotation.GetMapping"
Cohesion: 0.05
Nodes (25): org.springframework.http.HttpStatus, org.springframework.security.web.csrf.CsrfToken, org.springframework.validation.annotation.Validated, org.springframework.web.bind.annotation.GetMapping, org.springframework.web.bind.annotation.PutMapping, org.springframework.web.bind.annotation.RequestMapping, org.springframework.web.bind.annotation.ResponseStatus, org.springframework.web.bind.annotation.RestController (+17 more)

### Community 6 - "org.springframework.stereotype.Component"
Cohesion: 0.05
Nodes (24): lombok.extern.slf4j.Slf4j, org.slf4j.Logger, org.springframework.context.event.EventListener, org.springframework.core.task.TaskExecutor, org.springframework.security.crypto.password.PasswordEncoder, org.springframework.stereotype.Component, org.springframework.transaction.PlatformTransactionManager, OrderConfirmedListener (+16 more)

### Community 7 - "AuditAction"
Cohesion: 0.02
Nodes (83): AuditAction, ACCOUNT_LOCKED, APPROVAL_CANCELLED, APPROVAL_DECIDED, APPROVAL_REQUESTED, ARCHIVED, ASSIGNMENT_CHANGED, ATTACHMENT_ADDED (+75 more)

### Community 8 - "ApprovalsIT"
Cohesion: 0.06
Nodes (27): Task 12: Approvals module, Task 2: Approvals — several requirements at once, system requests, decision event, Global Constraints, Refunds Implementation Plan, ApprovalDecided, ApprovalRequest, ApprovalResponse, Link (+19 more)

### Community 9 - "PageQuery"
Cohesion: 0.06
Nodes (19): Other backend changes, org.springframework.data.domain.Page, org.springframework.data.domain.Pageable, AuditEventResponse, DeliveryOrderController, MovementResponse, StockController, CustomerInvoiceRow (+11 more)

### Community 10 - "Customers"
Cohesion: 0.06
Nodes (23): com.microsoft.playwright.Browser, com.microsoft.playwright.Playwright, jakarta.annotation.PreDestroy, org.springframework.cache.annotation.CacheEvict, org.thymeleaf.TemplateEngine, Customers, CustomerSalesProfile, DocumentController (+15 more)

### Community 11 - "OrderService"
Cohesion: 0.09
Nodes (9): Task 17: Order workspace — header, lines, totals, approvals, Task 1: Snapshots at confirm and the V10 migration, The credited rep is the existing `sales_order.rep_id`, LineRequest, OrderRequest, OrderService, OrderStockMapper, QuoteClosed (+1 more)

### Community 12 - ".lead"
Cohesion: 0.07
Nodes (8): Task 7: UI — api, Lead sources page, customer form source, Task 8: Frontend, Data — Flyway `V14__leads.sql`, LeadConvertIT, LeadDuplicatesIT, LeadEditIT, LeadsFixture, LeadsIT

### Community 13 - "DealService"
Cohesion: 0.08
Nodes (17): Deal, DealController, ResponseEntity, DealReassignmentListener, DealRef, DealRequest, DealResponse, DealScopes (+9 more)

### Community 14 - ".transition"
Cohesion: 0.11
Nodes (6): Task 11: Release, fulfil, cancel, Task 7: Sales integration, Task 5: Order lifecycle notifications, org.springframework.test.web.servlet.RequestBuilder, OrderStockIT, ReleaseCancelIT

### Community 15 - "DealsFixture"
Cohesion: 0.09
Nodes (7): Task 1: Table, create, list, get, summary, Task 3: Linking orders, DealEditIT, DealLinkIT, DealReassignIT, DealsFixture, DealsIT

### Community 16 - ".salesProfileVisible"
Cohesion: 0.06
Nodes (15): com.fasterxml.jackson.annotation.JsonIgnore, Task 1: Migration, lead sources on customers, jakarta.validation.constraints.AssertTrue, ContactRequest, ContactResponse, Customer, CustomerContact, CustomerContactRepository (+7 more)

### Community 17 - ".record"
Cohesion: 0.09
Nodes (18): Endpoints — `PosController`, `ADMIN` and `SALES`, 2. Events, org.springframework.web.multipart.MultipartFile, Payment, PaymentController, PaymentRepository, PaymentRequest, PaymentResponse (+10 more)

### Community 18 - "StorageServiceIT"
Cohesion: 0.05
Nodes (34): Attachments (M-08) Implementation Plan, Global Constraints, Review Focus, Task 1: StorageService — detect, put, presign for download, Task 2: Table, entity and access, Task 3: Upload, list, download, archive, Task 4: UI — FilesSection, Task 5: Place it, and e2e (+26 more)

### Community 19 - "java.time.YearMonth"
Cohesion: 0.10
Nodes (24): Task 8: Owner dashboard page, Task 7: Dashboard style C and "See more", API, in module `insights`, Dashboards, java.time.YearMonth, org.springframework.cache.annotation.Cacheable, org.springframework.jdbc.core.namedparam.MapSqlParameterSource, AdminInsights (+16 more)

### Community 20 - "TransferService"
Cohesion: 0.08
Nodes (20): CatalogPermissions, AdjustmentSource, MANUAL, RETURN_DAMAGED, SHORT_PICK, TRANSIT_LOSS, ReceiveLineRequest, ReceiveRequest (+12 more)

### Community 21 - ".scope"
Cohesion: 0.09
Nodes (10): org.springframework.security.access.prepost.PreAuthorize, org.springframework.web.bind.annotation.PostMapping, AttachmentController, CustomerController, CustomerSummary, FollowUpController, InvoiceController, OrderController (+2 more)

### Community 22 - "CustomerStatusMailsIT"
Cohesion: 0.10
Nodes (9): Task 7: PDF rendering, quote PDF and Send quote, Review Focus, Task 3: `CustomerStatusMails`: confirmed, payment received, payment rejected, Task 4: Dispatched and delivered emails, jakarta.mail.internet.MimeMessage, jakarta.mail.Multipart, CustomerStatusMailsIT, ResultActions (+1 more)

### Community 23 - "jakarta.servlet.http.HttpServletRequest"
Cohesion: 0.08
Nodes (20): jakarta.servlet.FilterChain, jakarta.servlet.http.HttpServletRequest, jakarta.servlet.http.HttpServletResponse, org.springframework.beans.factory.annotation.Autowired, org.springframework.security.core.Authentication, org.springframework.security.web.context.HttpSessionSecurityContextRepository, org.springframework.session.web.http.CookieHttpSessionIdResolver, org.springframework.util.AntPathMatcher (+12 more)

### Community 24 - "jakarta.persistence.Entity"
Cohesion: 0.08
Nodes (17): jakarta.persistence.Entity, jakarta.persistence.PreUpdate, jakarta.persistence.Table, org.hibernate.annotations.Immutable, AuditEvent, DeliveryOrderLine, RecoveryCode, StockMovement (+9 more)

### Community 26 - ".compute"
Cohesion: 0.08
Nodes (19): Explicitly deferred (not in this plan), Part B — Frontend (`crm-dashboard-ui`), Task 15: Types, products screen, settings and customer fields, Task 16: Orders list and new order, Task 18: Workspace tabs — invoice, payments, revisions, history, Task 19: Customer sales tabs and the Missing 2307 report, Task 20: Portal order screens, Task 21: Sales end-to-end smoke (+11 more)

### Community 27 - "Inventory"
Cohesion: 0.10
Nodes (11): 3. Stock rules, Allocator, Reservation lifecycle (called from `OrderService`), Inventory, Key, OrderStock, ReserveLine, ReturnLine (+3 more)

### Community 28 - "NotificationStreamsIT"
Cohesion: 0.09
Nodes (17): AssertTrue, Events (decided), Global Constraints, In-App Notifications Implementation Plan, Review Focus, Task 1: Live delivery — `NotificationStreams` + Redis fan-out, Task 2: Stored notifications — entity, migration, `NotificationService`, org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory (+9 more)

### Community 29 - ".receive"
Cohesion: 0.18
Nodes (7): Task 2: Ledger, reservations and the Allocator, Task 4: Low / out of stock and order short on reservation, LineStock, AllocatorIT, InventoryFixture, Line, ReservationsIT

### Community 30 - "RefundsIT"
Cohesion: 0.15
Nodes (8): Review Focus, Task 1: Schema, enums and `Inventory.receiveReturn`, Task 2: Request, approve, reject, cancel, Task 3: Payout — stock, credit memo number, notification, Task 6: Order responses, security matrix, API snapshot, Task 7: Frontend, Environment, RefundsIT

### Community 31 - "org.springframework.transaction.annotation.Transactional"
Cohesion: 0.07
Nodes (14): org.springframework.transaction.annotation.Transactional, LabelKind, DO, PR, TR, WH, Label, LabelService (+6 more)

### Community 32 - "Task 2: Insights module, month parsing and sales targets"
Cohesion: 0.09
Nodes (10): Backend (`crm/crm`), Dashboards (#4) Implementation Plan, Global Constraints, Review Focus, Task 2: Insights module, month parsing and sales targets, Commission, InsightsMonth, CommissionTest (+2 more)

### Community 33 - ".contains"
Cohesion: 0.13
Nodes (10): Task 4: Owner dashboard, Review Focus, Task 2: Month, rep and customer reports plus endpoint, Task 3: Product report with pro-rata split, Task 4: Reports page, Task 5: End-to-end smoke, Task 5: Insights and reports net of refunds, AdminInsightsIT (+2 more)

### Community 34 - "DeliveryOrdersIT"
Cohesion: 0.12
Nodes (11): Task 6: Delivery orders, Task 1: Events: `PaymentRejected`, `DeliveryDispatched`, `OrderDelivered.deliveryOrderId`, 3. Components, 4. Error handling, 5. Testing, Customer status emails — design (2026-11-18), Decisions, Goal (+3 more)

### Community 35 - "OrderResponse"
Cohesion: 0.05
Nodes (32): Backend (`crm/crm`), Task 1: Sales channel and POS warehouse setting, Task 4: Checkout, Cost snapshot, Data model — `V10__insights.sql`, Decisions, Figures, Frontend — `crm-dashboard-ui` (+24 more)

### Community 36 - "LeadService"
Cohesion: 0.11
Nodes (14): Lead, LeadAssignRequest, LeadController, LeadRequest, LeadResponse, LeadScopes, LeadService, LeadStatus (+6 more)

### Community 37 - "MailIntegrationTest"
Cohesion: 0.09
Nodes (18): Task 2: Request line and business-step line, jakarta.annotation.PostConstruct, jakarta.mail.Session, java.lang.annotation.Retention, java.lang.annotation.Target, org.junit.jupiter.api.extension.ExtendWith, org.springframework.boot.test.system.CapturedOutput, org.springframework.boot.test.system.OutputCaptureExtension (+10 more)

### Community 38 - "RefundService"
Cohesion: 0.07
Nodes (23): Decisions, PaymentMethod, BANK_TRANSFER, CARD, CASH, CHECK, GCASH, OTHER (+15 more)

### Community 39 - "StaffPrincipal"
Cohesion: 0.13
Nodes (10): org.springframework.http.MediaType, org.springframework.security.authentication.UsernamePasswordAuthenticationToken, org.springframework.security.core.AuthenticatedPrincipal, org.springframework.security.core.authority.SimpleGrantedAuthority, org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, Override, StaffPrincipal (+2 more)

### Community 40 - "org.springframework.context.annotation.Bean"
Cohesion: 0.09
Nodes (24): Task 3: REST + SSE endpoints, Follow-ups (F-141) Implementation Plan, Global Constraints, org.springframework.boot.web.servlet.FilterRegistrationBean, org.springframework.context.annotation.Bean, org.springframework.context.annotation.Configuration, org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity, org.springframework.security.config.annotation.web.builders.HttpSecurity (+16 more)

### Community 41 - "PortalOrderService"
Cohesion: 0.09
Nodes (13): 5. API, DeclineRequest, PortalDeliveryStatus, DELIVERED, OUT_FOR_DELIVERY, PREPARING, PortalOrderResponse, PortalOrderService (+5 more)

### Community 42 - "Task 9: Identity — tokens, mail, invites, user admin, password reset, bootstrap"
Cohesion: 0.09
Nodes (16): Task 9: Identity — tokens, mail, invites, user admin, password reset, bootstrap, jakarta.persistence.EntityManager, AcceptInviteRequest, BootstrapAdmin, InviteUserRequest, OneTimeTokens, Subject, ResetPasswordRequest (+8 more)

### Community 43 - "SettingKey"
Cohesion: 0.06
Nodes (33): Global Constraints, Reports (F-172) Implementation Plan, Automatic Follow-ups Implementation Plan, Global Constraints, Task 1: Schema, settings keys and FollowUp fields, SettingKey, APPROVAL_DISCOUNT_PERCENT_MAX, APPROVAL_WRITE_OFF_VALUE_MAX (+25 more)

### Community 44 - "Invoice"
Cohesion: 0.07
Nodes (25): Backend (`crm`), Backend tests, Checkout flow, Components — `src/components/pos/`, Decisions, Emailed, Endpoints — new `PosController` at `/api/v1/pos`, `@PreAuthorize("hasAnyAuthority('ADMIN','SALES')")`, Frontend checks (+17 more)

### Community 45 - "org.springframework.data.jpa.repository.Query"
Cohesion: 0.10
Nodes (8): org.springframework.data.jpa.repository.Modifying, org.springframework.data.jpa.repository.Query, FollowUpRepository, AppUserRepository, RecoveryCodeRepository, StockLevelRepository, NotificationRepository, RefundRepository

### Community 46 - "TwoFactorIT"
Cohesion: 0.19
Nodes (3): jakarta.servlet.http.Cookie, org.springframework.test.web.servlet.MvcResult, TwoFactorIT

### Community 47 - "org.springframework.http.ResponseEntity"
Cohesion: 0.13
Nodes (17): Task 11: Notifications (carried over, re-keyed to users), jakarta.validation.ConstraintViolationException, org.springframework.dao.OptimisticLockingFailureException, org.springframework.http.HttpHeaders, org.springframework.http.HttpStatusCode, org.springframework.http.ProblemDetail, org.springframework.http.ResponseEntity, org.springframework.security.access.AccessDeniedException (+9 more)

### Community 48 - "PaymentsIT"
Cohesion: 0.18
Nodes (5): Task 10: Payments — record, verify, reject, 2307, Task 2: Upload proof after recording a payment, Task 2: Payment notifications and `PaymentVerified` event, org.springframework.mock.web.MockMultipartFile, PaymentsIT

### Community 49 - "InsightsFixture"
Cohesion: 0.10
Nodes (3): Task 5: Warehouse dashboard, InsightsFixture, WarehouseInsightsIT

### Community 50 - "AttachmentService"
Cohesion: 0.12
Nodes (13): Attachment, AttachmentAccess, InvoiceRef, InvoiceRow, OrderRow, AttachmentRepository, AttachmentResponse, AttachmentService (+5 more)

### Community 51 - "RuleSettings"
Cohesion: 0.10
Nodes (11): AutoRule, Candidate, DealStalledRule, Override, FirstOrderRule, Override, Override, QuoteDeclinedRule (+3 more)

### Community 53 - "org.springframework.data.jpa.domain.Specification"
Cohesion: 0.12
Nodes (7): org.springframework.data.jpa.domain.Specification, FollowUp, FollowUpRequest, FollowUpResponse, FollowUpService, ReassignmentListener, OrderScopes

### Community 54 - "org.springframework.data.jpa.repository.JpaRepository"
Cohesion: 0.11
Nodes (15): Task 2: `InventoryService.sellForPos`, org.springframework.data.jpa.repository.JpaRepository, org.springframework.data.jpa.repository.JpaSpecificationExecutor, org.springframework.data.jpa.repository.Lock, ApprovalRequestRepository, AuditEventRepository, ProductRepository, DealRepository (+7 more)

### Community 55 - "StockReadsIT"
Cohesion: 0.11
Nodes (10): Global Constraints, Part A — Backend (`crm`), Redesign #3 Inventory & Fulfilment — Implementation Plan, Review Focus, Task 3: Stock reads, availability and receipts, Task 3: POS product list, Override, ReceiptsIT (+2 more)

### Community 56 - "Sheets"
Cohesion: 0.11
Nodes (6): java.util.zip.ZipOutputStream, ImportResult, RowError, Sheets, Req, SheetsTest

### Community 57 - ".run"
Cohesion: 0.19
Nodes (8): Task 3: Customer rules (RESTOCK, WIN_BACK, FIRST_ORDER) and the order-confirmed close, FirstOrderRuleIT, ApplicationEventPublisher, PlatformTransactionManager, RestockRuleIT, ApplicationEventPublisher, PlatformTransactionManager, WinBackRuleIT

### Community 58 - "ProductUnit"
Cohesion: 0.08
Nodes (20): com.fasterxml.jackson.annotation.JsonInclude, ProductUnit, BOX, G, KG, L, M, ML (+12 more)

### Community 60 - "TransfersIT"
Cohesion: 0.18
Nodes (4): Task 5: Transfers and cover shortage, CoverShortage, CoverShortageTest, TransfersIT

### Community 61 - "OrderBrief"
Cohesion: 0.07
Nodes (23): Deals (F-130) Implementation Plan, Global Constraints, Review Focus, Task 2: Edit and stages (except Won), Task 5: Follow the customer on reassignment, Task 6: UI — api, list, drawer, nav, Task 7: UI — deal page, Task 8: UI — customer rail and order link (+15 more)

### Community 62 - "DealStalledRuleIT"
Cohesion: 0.12
Nodes (11): Task 5: DEAL_STALLED and the deal-stage close, Closing on events (immediate), 1. Emails, org.springframework.transaction.event.TransactionalEventListener, DealStageChanged, AutoCloseListeners, DeliveryDispatched, OrderConfirmed (+3 more)

### Community 63 - "OrderAction"
Cohesion: 0.07
Nodes (26): org.junit.jupiter.params.provider.Arguments, org.junit.jupiter.params.provider.MethodSource, OrderAction, ACCEPT, CANCEL, CONFIRM, COVER_SHORTAGE, DECLINE (+18 more)

### Community 64 - "Scope"
Cohesion: 0.10
Nodes (4): FollowUpCounts, LeadCounts, RevisionResponse, Scope

### Community 65 - "LeadSources"
Cohesion: 0.15
Nodes (6): LeadSource, LeadSourceController, LeadSourceRepository, LeadSourceRequest, LeadSourceResponse, LeadSources

### Community 66 - "FollowUpRule"
Cohesion: 0.09
Nodes (14): FollowUpRule, DEAL_STALLED, FIRST_ORDER, PAYMENT_OVERDUE, QUOTE_CHASE, QUOTE_DECLINED, QUOTE_EXPIRING, RESTOCK (+6 more)

### Community 67 - "V5__inventory_fulfilment.sql"
Cohesion: 0.12
Nodes (27): user_warehouse, warehouse, warehouse_code_uq, warehouse_one_default, delivery_order_line_delivery_order_idx, stock_transfer_line_transfer_idx, product, product_name_idx (+19 more)

### Community 68 - "NotificationType"
Cohesion: 0.07
Nodes (26): Task 4: Fire the events, Task 6: Conversion, NotificationType, APPROVAL_DECIDED, APPROVAL_REQUESTED, DELIVERY_CREATED, FOLLOW_UP_OVERDUE, FOLLOW_UPS_DUE (+18 more)

### Community 69 - "RateLimitFilterIT"
Cohesion: 0.16
Nodes (11): Global Constraints, Redis Phase Implementation Plan, Task 2: Rate limiting (R-03), org.junit.jupiter.params.provider.CsvSource, org.springframework.mock.web.MockFilterChain, org.springframework.mock.web.MockHttpServletResponse, RateLimiter, Override (+3 more)

### Community 70 - "PortalOrdersIT"
Cohesion: 0.21
Nodes (3): Task 13: Portal orders, accept/decline, portal payments, auto-confirm, AutoConfirmIT, PortalOrdersIT

### Community 72 - "LoginIT"
Cohesion: 0.18
Nodes (5): Task 6: Identity — security config, sessions, login, logout, me, javax.sql.DataSource, org.springframework.session.data.redis.RedisIndexedSessionRepository, LoginResponse, LoginIT

### Community 73 - "org.springframework.scheduling.annotation.Scheduled"
Cohesion: 0.10
Nodes (17): Task 1: Table, entity and the Manila day, org.springframework.boot.autoconfigure.condition.ConditionalOnProperty, org.springframework.scheduling.annotation.Scheduled, AutoFollowUpsSchedule, Bucket, DONE, OVERDUE, TODAY (+9 more)

### Community 74 - "FollowUpsIT"
Cohesion: 0.18
Nodes (4): Task 2: Create, list and counts, Review Focus, Task 7: API — rule, closedReason, source filter, FollowUpsIT

### Community 75 - ".customer"
Cohesion: 0.22
Nodes (3): Task 5: Morning bell, Task 6: Overdue notification, FollowUpReminderIT

### Community 76 - "PortalAuthIT"
Cohesion: 0.18
Nodes (3): Task 14: Portal identity (`portal` module), PortalLinkRequest, PortalAuthIT

### Community 77 - "DeliveryOrder"
Cohesion: 0.13
Nodes (11): DeliveryOrder, DeliveryOrderRepository, DeliveryLineResponse, DeliveryOrderResponse, DeliveryStatus, CANCELLED, DELIVERED, DISPATCHED (+3 more)

### Community 78 - ".product"
Cohesion: 0.17
Nodes (3): DeliveryLine, DeliveryRequest, ApplicationContext

### Community 79 - "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate"
Cohesion: 0.11
Nodes (9): java.sql.ResultSet, org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate, Due, FollowUpQueries, Overdue, FollowUpReminder, InventoryOverviewService, Raw (+1 more)

### Community 80 - "RequiredApproval"
Cohesion: 0.09
Nodes (15): Backend (`crm`), Decisions, Elsewhere, Frontend (`crm-dashboard-ui`), Goal, Out of scope, `PosService.checkout` — one `@Transactional`, Redesign #4 — POS (+7 more)

### Community 82 - "Redesign #3 — Inventory & Fulfilment"
Cohesion: 0.08
Nodes (23): 1. Modules, 2. Data (Flyway, Postgres), 4. Flows, 6. Access, 7. Audit and notifications, 8. Frontend, 9. Testing, Adjustment (+15 more)

### Community 83 - "java.util.regex.Pattern"
Cohesion: 0.10
Nodes (19): 1. Request line — `shared/AccessLogFilter`, 2. Business step line — `AuditLog.record`, 4. Redaction — `shared/LogRedactor`, 5. Background work, 6. Changing detail at runtime, 7. Files — `logback-spring.xml`, 8. UI (crm-dashboard-ui), Decisions (from the user) (+11 more)

### Community 84 - "Part B — Frontend (`crm-dashboard-ui`)"
Cohesion: 0.09
Nodes (21): Explicitly deferred (not in this plan), Part B — Frontend (`crm-dashboard-ui`), Task 17: Branch, dependencies, structure, API client, auth state, Task 18: Component kit, Task 19: App shell, Task 20: Auth screens, Task 21: Profile & security, user and warehouse admin, Task 22: Settings, activity log, inbox, approval dialog (+13 more)

### Community 85 - "StorageService"
Cohesion: 0.13
Nodes (10): Task 16: Deployment and CI, 8. Errors & observability, io.minio.MinioClient, org.springframework.boot.health.contributor.Health, org.springframework.boot.health.contributor.HealthIndicator, Override, MinioHealthIndicator, Detected (+2 more)

### Community 86 - ".update"
Cohesion: 0.19
Nodes (5): Part A — Backend (`crm`), Redesign #2 Sales Lifecycle — Implementation Plan, Review Focus, Task 12: Revise confirmed orders, ReviseIT

### Community 87 - "sales_order"
Cohesion: 0.15
Nodes (20): order_revision, payment_status_verified_idx, sales_order_rep_idx, sales_target, money_movement, refund, refund_line, refund_line_refund_idx (+12 more)

### Community 88 - "CacheConfig"
Cohesion: 0.14
Nodes (15): Task 5: Dashboard caching (R-06), API — module `insights`, Caching, `GET /api/insights/admin` — role ADMIN, `GET /api/insights/sales` — role SALES, always the signed-in user (no `repId` parameter), `GET /api/insights/warehouse` — roles WAREHOUSE, ADMIN, Targets — role ADMIN, org.springframework.cache.annotation.CachingConfigurer (+7 more)

### Community 89 - ".json"
Cohesion: 0.27
Nodes (4): Task 9: Invoices — issue, gapless numbers, void and reissue, PDF, Backend (`crm/crm`), Task 6: Approvals — subject links, list filters, contract snapshot, InvoicesIT

### Community 92 - "org.springframework.data.redis.core.StringRedisTemplate"
Cohesion: 0.20
Nodes (8): Task 3: Single-flight locks on customer links and staff accept (R-04), Task 4: Run the quotation expiry job on one instance (R-05), lombok.RequiredArgsConstructor, org.springframework.data.redis.core.script.RedisScript, org.springframework.data.redis.core.StringRedisTemplate, TrustedDevices, DistributedLock, DistributedLockIT

### Community 93 - "WarehousesIT"
Cohesion: 0.23
Nodes (6): Global Constraints, Part A — Backend (`crm`), Redesign #1 Foundation — Implementation Plan, Review Focus, Task 7: Warehouses module, WarehousesIT

### Community 94 - "Task 13: Customers module (first scoped slice)"
Cohesion: 0.22
Nodes (4): Task 13: Customers module (first scoped slice), AssignmentRequest, CustomerPermissions, CustomerScopingIT

### Community 96 - "LogRedactorTest"
Cohesion: 0.19
Nodes (6): Task 1: LogRedactor, Create, Customer, Login, LogRedactorTest, Node

### Community 97 - "ServiceCallLoggingIT"
Cohesion: 0.14
Nodes (7): Task 3: Service calls and background IDs, org.springframework.context.annotation.Import, ServiceCallLoggingIT.Probes, LoggingProbeService, ProbeEvent, Probes, ServiceCallLoggingIT

### Community 99 - "app_user"
Cohesion: 0.18
Nodes (20): app_setting, app_user, app_user_email_uq, approval_one_pending, approval_request, approval_subject_idx, audit_actor_idx, audit_at_idx (+12 more)

### Community 101 - ".matchContact"
Cohesion: 0.17
Nodes (7): Task 5: Duplicate warning, Candidate, ContactMatches, Match, LeadDuplicates, LeadDuplicatesResponse, PhoneKeyTest

### Community 103 - "NotificationStreams"
Cohesion: 0.19
Nodes (8): org.springframework.data.redis.connection.Message, org.springframework.data.redis.connection.MessageListener, org.springframework.web.servlet.mvc.method.annotation.SseEmitter, Override, NotificationStreams, StreamController, SseEventBuilder, tools.jackson.databind.json.JsonMapper

### Community 104 - "LoginThrottle"
Cohesion: 0.18
Nodes (8): LoginRequest, LoginService, Outcome, LoginThrottle, Result, ALREADY_LOCKED, COUNTED, LOCKED

### Community 105 - ".list"
Cohesion: 0.14
Nodes (11): Frontend (`crm-dashboard-ui`), Task 10: End-to-end smoke, Task 6: API module and target card, Task 7: Insights page for sales and warehouse, routing and nav, Task 9: Sales targets admin page, TargetRequest, TargetRow, DeliveryRef (+3 more)

### Community 106 - "UI polish bundle — design (2026-11-17)"
Cohesion: 0.11
Nodes (18): 1. Sorting on every table, 2. Order search by customer name, 3. Image viewer, 4. Notification date and time, 5. Dashboard style C, Backend, Component, Decisions (from the user) (+10 more)

### Community 107 - "ReportRange"
Cohesion: 0.33
Nodes (6): Named, ProductSum, RepFilter, ReportQueries, Sum, ReportRange

### Community 108 - ".report"
Cohesion: 0.22
Nodes (5): ReportRow, ReportService, SalesReport, Total, SalesReportController

### Community 109 - "IdempotencyFilterIT"
Cohesion: 0.23
Nodes (7): Review Focus, Task 0: Commit the MinIO work, Task 1: Redis wiring and token revocation (R-01, R-02, R-09 part), Task 6: Idempotency-Key on creates (R-07), Task 7: Full suite, live smoke test, tracker, CountingController, IdempotencyFilterIT

### Community 110 - "FollowUpEditIT"
Cohesion: 0.28
Nodes (3): Task 3: Edit, done and reopen, FollowUpEditIT, PlatformTransactionManager

### Community 111 - "CreditMemoIT"
Cohesion: 0.19
Nodes (4): Task 4: Credit memo PDF, CreditMemoIT, Environment, PdfRendererTest

### Community 112 - "TotpService"
Cohesion: 0.24
Nodes (4): java.security.SecureRandom, javax.crypto.SecretKey, TotpService, EncryptionService

### Community 114 - "SessionController"
Cohesion: 0.28
Nodes (3): org.springframework.web.bind.annotation.DeleteMapping, SessionController, SessionResponse

### Community 115 - "TaxClass"
Cohesion: 0.22
Nodes (7): Product, ProductRequest, ProductResponse, TaxClass, VAT_EXEMPT, VATABLE, ZERO_RATED

### Community 116 - ".decode"
Cohesion: 0.15
Nodes (4): Base32, QrCodes, TotpSetupResponse, Base32Test

### Community 117 - "SettingKey.java"
Cohesion: 0.25
Nodes (12): bankAccounts(), bool(), field(), invalid(), number(), of(), positiveIdOrNull(), text() (+4 more)

### Community 118 - ".recordFor"
Cohesion: 0.22
Nodes (3): Task 4: Audit module, AuditChangesTest, AuditLogIT

### Community 120 - "ServiceCallLogging"
Cohesion: 0.23
Nodes (8): java.lang.reflect.Method, org.aopalliance.intercept.MethodInvocation, org.springframework.aop.Advisor, org.springframework.aop.Pointcut, org.springframework.beans.factory.config.BeanPostProcessor, org.springframework.context.annotation.Role, org.springframework.core.ParameterNameDiscoverer, ServiceCallLogging

### Community 121 - "Backend (`crm`)"
Cohesion: 0.13
Nodes (14): Backend (`crm`), Frontend (`crm-dashboard-ui`), Global Constraints, Retail POS Implementation Plan, Review Focus, Task 10: Payment, success view and printed receipt, Task 1: Schema, enums and response fields, Task 3: `PosSaleService.checkout` (+6 more)

### Community 122 - ".create"
Cohesion: 0.17
Nodes (9): AdjustmentMode, COUNTED, DELTA, AdjustmentRequest, AdjustmentResponse, AdjustmentStatus, PENDING_APPROVAL, POSTED (+1 more)

### Community 125 - "Task 10: Identity — TOTP 2FA and recovery codes"
Cohesion: 0.22
Nodes (4): Task 10: Identity — TOTP 2FA and recovery codes, RecoveryCodesResponse, Totp, TotpTest

### Community 126 - "FollowUpReassignIT"
Cohesion: 0.32
Nodes (6): Review Focus, Task 4: Follow the customer on reassignment, Task 6: UI — api, page, route, nav badge, Task 7: UI — add/edit drawer and rail sections, Task 8: Dashboard stat, bell link and e2e, FollowUpReassignIT

### Community 128 - "Automatic follow-ups — design (2026-11-17)"
Cohesion: 0.14
Nodes (13): 1. Data — `V24__auto_follow_ups.sql`, 2. Settings (`SettingKey`, admin-editable, validated like existing keys), 3. Daily scan — `followups/AutoFollowUps`, 4. Rules (each is one class in `followups/rules/`, implementing `AutoRule`), 5. Overdue notification, 6. API, 7. Frontend (`crm-dashboard-ui`), 8. Testing (+5 more)

### Community 129 - "SecurityMatrixTest"
Cohesion: 0.19
Nodes (8): org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping, SecurityMatrixTest, Who, ADMIN, ANON, CUSTOMER, SALES, WAREHOUSE

### Community 130 - "OpenApiSnapshotTest"
Cohesion: 0.17
Nodes (11): Task 15: Rate limits, idempotency, OpenAPI snapshot, security matrix, Global Constraints, Leads (F-120) Implementation Plan, Review Focus, Task 10: e2e, Task 2: Lead sources API, Task 3: Leads module — create, list, get, counts, Task 4: Edit, status, assign (+3 more)

### Community 131 - "Task 1: Range parsing and error code"
Cohesion: 0.33
Nodes (3): Task 1: Range parsing and error code, ReportRangeTest, ThrowingCallable

### Community 132 - "Redesign #1 — Foundation"
Cohesion: 0.15
Nodes (12): 10. Testing, 11. Deployment changes, 2. Data conventions (Postgres), 4. Authorization & scoping, 5. Approvals, 6. Audit, 7. Customers & warehouses (first vertical slice), Decisions (+4 more)

### Community 133 - ".idsForWarehouse"
Cohesion: 0.15
Nodes (11): 1. Notification catalogue, 2. Notifications page, bell, routing (frontend), 3. Approvals on record pages, 4. Backend changes, 5. Testing, Backend, Frontend, Goal (+3 more)

### Community 134 - "MeController"
Cohesion: 0.36
Nodes (4): ChangePasswordRequest, MeController, UpdateMeRequest, MeResponse

### Community 135 - "Task 1: Branch, dependencies, legacy removal, test harness"
Cohesion: 0.21
Nodes (6): Task 1: Branch, dependencies, legacy removal, test harness, org.junit.jupiter.params.ParameterizedTest, org.junit.jupiter.params.provider.ValueSource, Override, IdempotencyFilterPathsTest, RedisTimeoutConfigTest

### Community 136 - "MovementType"
Cohesion: 0.17
Nodes (10): 2. Lifecycle, Guards (checked on request and again on payout, under the order's row lock), MovementType, ADJUSTMENT, DISPATCH, RECEIPT, RETURN, TRANSFER_IN (+2 more)

### Community 138 - "Refunds — design (2026-11-16)"
Cohesion: 0.18
Nodes (10): 1. Data model, 3. Money and payment status, 4. Insights, reports, dashboard, 5. Notifications, 6. Credit memo, 7. Frontend (`crm-dashboard-ui`), 8. Testing, Goal (+2 more)

### Community 139 - "LeadSourceBackfillIT"
Cohesion: 0.35
Nodes (3): org.flywaydb.core.Flyway, org.springframework.jdbc.datasource.DriverManagerDataSource, LeadSourceBackfillIT

### Community 140 - "RedisSessionAdmin"
Cohesion: 0.36
Nodes (4): org.springframework.session.FindByIndexNameSessionRepository, org.springframework.session.Session, Override, RedisSessionAdmin

### Community 141 - "RestockRule.java"
Cohesion: 0.25
Nodes (4): RestockRhythm, Delivery, Override, RestockRule

### Community 142 - "PaymentOverdueRule"
Cohesion: 0.22
Nodes (3): Override, PaymentOverdueRule, Quotes

### Community 143 - ".receive"
Cohesion: 0.29
Nodes (4): ReceiptLineRequest, ReceiptRequest, ReceiptLineResponse, ReceiptResponse

### Community 144 - ".customers"
Cohesion: 0.20
Nodes (9): Global Constraints, 1. Backend module layout, 3. Identity & sessions, Flows, Portal identity, Settings, Tables, 1. Modules (+1 more)

### Community 145 - "ScanIT"
Cohesion: 0.36
Nodes (4): Task 8: Scan resolver, QR labels, ledger check, org.springframework.context.ApplicationContext, LabelsIT, ScanIT

### Community 146 - "Frontend (`crm-dashboard-ui`)"
Cohesion: 0.20
Nodes (9): Frontend (`crm-dashboard-ui`), Global Constraints, POS (#4) Implementation Plan, Review Focus, Task 5: API types and POS API module, Task 6: Cart maths, Task 7: POS page, Task 8: Proof upload, channel filter and badge (+1 more)

### Community 147 - "Review Focus"
Cohesion: 0.20
Nodes (8): Global Constraints, Logging Across the App Implementation Plan, Review Focus, Task 4: Files, levels and the loggers endpoint, Task 5: Secrets never logged, Task 6: UI — Ref on errors, dev console, Global Constraints, 3. Service calls — `shared/ServiceCallLogging`

### Community 148 - "Notifications Implementation Plan"
Cohesion: 0.20
Nodes (9): Frontend (`crm-dashboard-ui`), Notification copy (exact), Notifications Implementation Plan, Review Focus, Task 10: Remove Inbox, Task 11: Graph refresh and final check, Task 7: Regenerate API and notification links, Task 8: Notifications page (+1 more)

### Community 149 - "Attachments (M-08) — design"
Cohesion: 0.20
Nodes (9): API — `/api/attachments`, Attachments (M-08) — design, Data — Flyway `V13__attachments.sql`, Decisions (from the user), Goal, Out of scope, Rules, Screens (crm-dashboard-ui) (+1 more)

### Community 150 - "Follow-ups (F-141) — design"
Cohesion: 0.20
Nodes (9): API — `/api/follow-ups`, Data — Flyway `V11__follow_ups.sql`, Decisions (from the user), Follow-ups (F-141) — design, Goal, Out of scope, Rules, Screens (crm-dashboard-ui) (+1 more)

### Community 154 - "DealOrder"
Cohesion: 0.31
Nodes (5): DealOrder.Key, jakarta.persistence.IdClass, DealOrder, Key, DealOrderRepository

### Community 155 - "transform.sql"
Cohesion: 0.22
Nodes (4): l, o, pg_temp.sys(), pg_temp.uid()

### Community 156 - "Leads (F-120) — design"
Cohesion: 0.22
Nodes (8): API, Decisions (from the user), Goal, Leads (F-120) — design, Out of scope, Rules, Testing, UI (crm-dashboard-ui)

### Community 157 - "package.json"
Cohesion: 0.22
Nodes (8): tailwindcss, @tailwindcss/cli, devDependencies, tailwindcss, @tailwindcss/cli, private, scripts, build

### Community 160 - "lead"
Cohesion: 0.42
Nodes (8): lead, lead_customer_idx, lead_email_idx, lead_phone_idx, lead_rep_status_idx, lead_source, lead_source_name_uq, lead_status_idx

### Community 161 - "FakeRule"
Cohesion: 0.36
Nodes (3): FakeRule, Fakes, Override

### Community 163 - "Redesign #2 — Sales lifecycle"
Cohesion: 0.25
Nodes (7): 6. Screens (`crm-dashboard-ui`), 7. Testing, Context, Decisions, Out of scope for #2, Redesign #2 — Sales lifecycle, Success

### Community 164 - "3. Order lifecycle"
Cohesion: 0.25
Nodes (8): 3. Order lifecycle, Cancel, Confirm, Draft and quote, Fulfil, Portal accept and decline, Release, Revise

### Community 167 - "follow_up"
Cohesion: 0.36
Nodes (6): follow_up, follow_up_customer_idx, follow_up_open_idx, follow_up_order_idx, auto_follow_up_run, follow_up_open_rule_idx

### Community 168 - "migrate.py"
Cohesion: 0.43
Nodes (6): copy_legacy(), env(), main(), One-time MySQL -> Postgres migration (P5-1). See deploy/migrate/CUTOVER.md.…, Records which referenced uploads exist on disk, so proofs can become…, stat_files()

### Community 169 - "LogLevelController.java"
Cohesion: 0.52
Nodes (3): org.springframework.boot.logging.LoggingSystem, LogLevelBody, LogLevelController

### Community 170 - "ReportBy"
Cohesion: 0.33
Nodes (6): parse(), ReportBy, CUSTOMER, MONTH, PRODUCT, REP

### Community 172 - "RefundStatus"
Cohesion: 0.29
Nodes (6): RefundStatus, APPROVED, CANCELLED, PAID, REJECTED, REQUESTED

### Community 173 - "deal"
Cohesion: 0.52
Nodes (6): deal, deal_customer_idx, deal_order, deal_order_order_uq, deal_rep_stage_idx, deal_stage_idx

### Community 174 - "Production cutover (P5-2)"
Cohesion: 0.33
Nodes (5): Afterwards, Before the window, Production cutover (P5-2), Rollback, Steps

### Community 175 - "9. Frontend (`crm-dashboard-ui`)"
Cohesion: 0.33
Nodes (6): 9. Frontend (`crm-dashboard-ui`), Component kit (`src/ui`), Dependency changes, Foundation screens, Shell, Structure

### Community 176 - "2. Data model"
Cohesion: 0.33
Nodes (6): 2. Data model, approvals, catalog, customers, sales, settings

### Community 177 - "4. Money, invoices and payments"
Cohesion: 0.33
Nodes (6): 4. Money, invoices and payments, Documents, Issue invoice, OrderTotals, Payments, Void and reissue

### Community 178 - "Reports (F-172)"
Cohesion: 0.33
Nodes (5): Decisions, Frontend, in `crm-dashboard-ui`, Goal, Reports (F-172), Testing

### Community 179 - "CrmApplication"
Cohesion: 0.53
Nodes (4): org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration, org.springframework.scheduling.annotation.EnableScheduling, CrmApplication

### Community 182 - "5. Access and API"
Cohesion: 0.40
Nodes (5): 5. Access and API, Audit and notifications, Authorization, Endpoints, Errors

### Community 185 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 191 - "Context"
Cohesion: 0.67
Nodes (3): Context, Roadmap (each sub-project gets its own spec → plan → build), What the owner asked for

## Knowledge Gaps
- **556 isolated node(s):** `o`, `l`, `01-roles.sh script`, `PENDING`, `APPROVED` (+551 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 828 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **62 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AuditAction` connect `AuditAction` to `.createdOrder`, `org.junit.jupiter.api.Test`, `ErrorCode`, `CurrentActor`, `org.springframework.stereotype.Component`, `PageQuery`, `Customers`, `.transition`, `.record`, `CustomerStatusMailsIT`, `jakarta.persistence.Entity`, `Leads (F-120) — design`, `LogLevelController.java`, `PaymentsIT`, `AttachmentService`, `PortalOrdersIT`, `org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate`, `.update`, `.json`, `AppUser`, `.recordFor`?**
  _High betweenness centrality (0.048) - this node is a cross-community bridge._
- **Why does `Scope` connect `Scope` to `.createdOrder`, `org.springframework.jdbc.core.JdbcTemplate`, `ErrorCode`, `Redesign #1 — Foundation`, `CurrentActor`, `org.springframework.web.bind.annotation.GetMapping`, `PageQuery`, `Customers`, `OrderService`, `DealService`, `.customers`, `.salesProfileVisible`, `.record`, `.scope`, `org.springframework.transaction.annotation.Transactional`, `OrderResponse`, `LeadService`, `RefundService`, `StaffPrincipal`, `Invoice`, `AttachmentService`, `org.springframework.data.jpa.domain.Specification`, `Sheets`, `CustomersIT`, `OrderBrief`, `org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate`, `.matchContact`, `ReportRange`, `.report`, `AttachmentsFixture`?**
  _High betweenness centrality (0.046) - this node is a cross-community bridge._
- **Why does `Role` connect `org.springframework.jdbc.core.JdbcTemplate` to `.createdOrder`, `org.junit.jupiter.api.Test`, `ErrorCode`, `CurrentActor`, `org.springframework.web.bind.annotation.GetMapping`, `org.springframework.stereotype.Component`, `MeController`, `ApprovalsIT`, `PageQuery`, `.lead`, `DealsFixture`, `.salesProfileVisible`, `jakarta.persistence.Entity`, `LogLevelIT`, `Inventory`, `org.springframework.transaction.annotation.Transactional`, `MailIntegrationTest`, `StaffPrincipal`, `Task 9: Identity — tokens, mail, invites, user admin, password reset, bootstrap`, `org.springframework.data.jpa.repository.Query`, `TwoFactorIT`, `InsightsFixture`, `.csrf`, `CustomersIT`, `Scope`, `LeadSources`, `NotificationType`, `LoginIT`, `.customer`, `.product`, `org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate`, `StorageService`, `AppUser`, `WarehousesIT`, `Task 13: Customers module (first scoped slice)`, `CatalogIT`, `SessionManagementIT`, `ReportRange`, `AttachmentsFixture`, `.recordFor`, `LeadSourcesIT`?**
  _High betweenness centrality (0.037) - this node is a cross-community bridge._
- **What connects `o`, `l`, `01-roles.sh script` to the rest of the system?**
  _556 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `.createdOrder` be split into smaller, more focused modules?**
  _Cohesion score 0.05691985532076908 - nodes in this community are weakly interconnected._
- **Should `org.springframework.jdbc.core.JdbcTemplate` be split into smaller, more focused modules?**
  _Cohesion score 0.10184656386826575 - nodes in this community are weakly interconnected._
- **Should `org.junit.jupiter.api.Test` be split into smaller, more focused modules?**
  _Cohesion score 0.045103092783505154 - nodes in this community are weakly interconnected._