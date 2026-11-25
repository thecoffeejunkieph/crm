# Graph Report - feat-purchasing-terms  (2026-11-25)

## Corpus Check
- 742 files · ~339,818 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 18 file(s) not represented in the graph (top: .properties 6, (none) 4, .example 2)

## Summary
- 6331 nodes · 24424 edges · 243 communities (171 shown, 72 thin omitted)
- Extraction: 80% EXTRACTED · 20% INFERRED · 0% AMBIGUOUS · INFERRED: 4931 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `c742adb0`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- org.springframework.web.bind.annotation.GetMapping
- CurrentActor
- OrderService
- ErrorCode
- org.springframework.jdbc.core.JdbcTemplate
- org.junit.jupiter.api.Test
- PageQuery
- Customers
- Settings
- org.springframework.transaction.annotation.Transactional
- AuditAction
- DealService
- PortalOrderService
- jakarta.servlet.http.HttpServletRequest
- FollowUpRule
- jakarta.servlet.http.HttpServletResponse
- java.time.YearMonth
- ModularityTest
- Scope
- OrderAction
- .lead
- TwoFactorIT
- Inventory
- .as
- ReportRange
- Role
- jakarta.persistence.Entity
- org.springframework.transaction.support.TransactionTemplate
- ApprovalsIT
- PaymentService
- RefundService
- TaxClass
- .reserve
- .diff
- org.junit.jupiter.api.AfterEach
- Task 2: Insights module, month parsing and sales targets
- AppUserRepository
- org.springframework.data.redis.core.StringRedisTemplate
- RefundsIT
- org.springframework.data.jpa.repository.JpaRepository
- OrdersIT
- DeliveryOrdersIT
- NotificationStreams
- OrderStockIT
- NotificationsIT
- PortalOrdersIT
- PurchaseOrderService
- UserAdminIT
- .createdOrder
- RedisTestSupport
- .at
- .order
- TransferService
- PaymentsIT
- DeliveryService
- AttachmentsIT
- HtmlMail
- ApprovalType
- ProductCategories
- .csrf
- org.springframework.context.event.EventListener
- .customer
- RestockRuleIT
- .run
- CustomerStatusMailsIT
- .contains
- SupplierService
- DistributedLock
- .followUp
- Design
- FollowUpService
- V5__inventory_fulfilment.sql
- ProductUnit
- PortalAuthIT
- .issue
- CustomerAddresses
- SettingKey
- PasswordResetService
- SendPurchaseOrderIT
- Redesign #1 — Foundation
- .as
- NotificationType
- SecurityMatrixTest
- LoginIT
- StockReadsIT
- .update
- .product
- RateLimitFilterIT
- CatalogIT
- SupplierCreditIT
- Redesign #3 — Inventory & Fulfilment
- org.springframework.stereotype.Component
- ProblemDetailAdvice
- IntegrationTest.java
- Part B — Frontend (`crm-dashboard-ui`)
- .update
- AdjustmentsIT
- PaymentVerified
- sales_order
- Sheets
- InvoicesIT
- .json
- Refunds — design (2026-11-16)
- org.springframework.transaction.event.TransactionalEventListener
- LeadSources
- PoResponse
- PdfRenderer
- WarehousesIT
- LogRedactorTest
- app_user
- SessionManagementIT
- .receive
- CategoryTargetsIT
- Redesign #2 — Sales lifecycle
- Task 13: Customers module (first scoped slice)
- ProblemDetailAdviceTest
- .customers
- FollowUpsIT
- UI polish bundle — design (2026-11-17)
- .parseForPlanning
- OrderStock
- FollowUpEditIT
- TargetMilestonesIT
- AdSpendIT
- SuppliersIT
- PurchaseOrdersIT
- ReceivePurchaseOrderIT
- PurchaseOrderMailIT
- Retail POS — Design
- ServiceCallLogging
- lombok.extern.slf4j.Slf4j
- PasswordResetIT
- OrderDelivered
- StorageService
- org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
- OpenApiSnapshotTest
- DealWonIT
- AdPerformanceService
- Statements
- SuggestionsIT
- Automatic follow-ups — design (2026-11-17)
- RestockRule.java
- LeadSourcesIT
- AuditLogIT
- StorageServiceIT
- Purchasing: supplier downpayment terms, PO document, resend
- SettingKey.java
- Task 3: Service calls and background IDs
- ReportsIT
- SuggestionQuery
- Redesign #4 — Dashboards
- PurchaseOrderDocumentIT
- Purchasing: suppliers, credit limits, purchase orders, reorder suggestions
- FollowUpQueries
- Bucket
- Review Focus
- DealStalledRuleIT
- CustomerStatusMails
- StorageDetectTest
- LeadSourceBackfillIT
- RedisSessionAdmin
- lead
- V30__purchasing.sql
- MinioHealthIndicator
- Frontend (`crm-dashboard-ui`)
- Attachments (M-08) — design
- .invite
- .receive
- CreditQuery
- CustomerSourceIT
- LogLevelIT
- transform.sql
- Backend (`crm`)
- .address
- Notifications — design (2026-11-12)
- package.json
- LogRedactor
- FakeRule
- Retail POS Implementation Plan
- ProductReorderFieldsIT
- 3. Order lifecycle
- WarehouseResponse
- PoStatus
- .call
- follow_up
- SuggestionMathTest
- migrate.py
- Review Focus
- Review Focus
- RefundDisposition
- SheetsTest
- deal
- Production cutover (P5-2)
- Reports (F-172)
- Customer self-service profile — design (2026-11-22)
- CrmApplication
- EncryptionSecretsCheckTest
- FileController
- SalesCategoryTargetRepository
- OrderLine
- SalesPermissions
- WarehouseRepository
- V26__product_category.sql
- SessionTransportIT
- ApprovalPushAfterCommitIT
- Task 1: Table, entity and the Manila day
- LogLevelController
- gradlew
- DocsDisabledTest
- QrCodes
- AdjustmentMode
- customer_address
- SummarySource
- Automatic Follow-ups Implementation Plan
- attachment
- user_role
- 01-roles.sh
- V12__follow_up_reminder_run.sql

## God Nodes (most connected - your core abstractions)
1. `Role` - 213 edges
2. `IntegrationTest` - 204 edges
3. `Scope` - 156 edges
4. `AuditAction` - 147 edges
5. `CurrentActor` - 141 edges
6. `AuditLog` - 138 edges
7. `ErrorCode` - 127 edges
8. `Customers` - 123 edges
9. `DomainException` - 108 edges
10. `PageQuery` - 94 edges

## Surprising Connections (you probably didn't know these)
- `Portal accept and decline` --references--> `ApprovalDecided`  [INFERRED]
  docs/superpowers/specs/2026-10-14-sales-lifecycle-design.md → src/main/java/ph/thecoffeejunkie/crm/approvals/ApprovalDecided.java
- `API` --references--> `AuditAction`  [INFERRED]
  docs/superpowers/specs/2026-11-05-leads-design.md → src/main/java/ph/thecoffeejunkie/crm/audit/AuditAction.java
- `API (portal, CUSTOMER only, scoped to the contact's company)` --references--> `ContactRequest`  [INFERRED]
  docs/superpowers/specs/2026-11-22-customer-self-service-design.md → src/main/java/ph/thecoffeejunkie/crm/customers/ContactRequest.java
- `4. Rules (each is one class in `followups/rules/`, implementing `AutoRule`)` --references--> `AutoRule`  [INFERRED]
  docs/superpowers/specs/2026-11-17-auto-follow-ups-design.md → src/main/java/ph/thecoffeejunkie/crm/followups/AutoRule.java
- `6. API` --references--> `FollowUpResponse`  [INFERRED]
  docs/superpowers/specs/2026-11-17-auto-follow-ups-design.md → src/main/java/ph/thecoffeejunkie/crm/followups/FollowUpResponse.java

## Import Cycles
- None detected.

## Communities (243 total, 72 thin omitted)

### Community 0 - "org.springframework.web.bind.annotation.GetMapping"
Cohesion: 0.04
Nodes (35): org.springframework.http.HttpHeaders, org.springframework.http.HttpStatus, org.springframework.http.MediaType, org.springframework.http.ResponseEntity, org.springframework.security.access.prepost.PreAuthorize, org.springframework.security.web.csrf.CsrfToken, org.springframework.validation.annotation.Validated, org.springframework.web.bind.annotation.GetMapping (+27 more)

### Community 1 - "CurrentActor"
Cohesion: 0.07
Nodes (32): jakarta.validation.Validator, org.springframework.context.ApplicationEventPublisher, org.springframework.data.domain.Pageable, org.springframework.data.domain.Sort, org.springframework.data.jpa.domain.Specification, org.springframework.stereotype.Service, Approvals, AuditLog (+24 more)

### Community 2 - "OrderService"
Cohesion: 0.05
Nodes (26): Task 1: Snapshots at confirm and the V10 migration, `PosService.checkout` — one `@Transactional`, Cost snapshot, Data model — `V10__insights.sql`, The credited rep is the existing `sales_order.rep_id`, RequiredApproval, DeliveryMethod, DELIVERY (+18 more)

### Community 3 - "ErrorCode"
Cohesion: 0.05
Nodes (52): java.util.regex.Pattern, org.springframework.boot.context.event.ApplicationReadyEvent, org.springframework.security.crypto.password.PasswordEncoder, org.springframework.transaction.PlatformTransactionManager, AuditChanges, ScanController, InsightsMonth, DomainException (+44 more)

### Community 4 - "org.springframework.jdbc.core.JdbcTemplate"
Cohesion: 0.10
Nodes (17): org.junit.jupiter.api.BeforeEach, org.springframework.cache.CacheManager, org.springframework.jdbc.core.JdbcTemplate, org.springframework.test.web.servlet.MockMvc, org.springframework.test.web.servlet.request.RequestPostProcessor, org.springframework.test.web.servlet.ResultActions, ContextLoadsIT, FollowUpsFixture (+9 more)

### Community 5 - "org.junit.jupiter.api.Test"
Cohesion: 0.05
Nodes (16): Task 3: Baseline schema and database roles, Task 5: Settings module, Task 11: Release, fulfil, cancel, Task 1: Schema V2 and sales settings, Task 1: Schema V5, settings and shared document numbers, Task 5: Duplicate warning, org.junit.jupiter.api.Test, FollowUpReminderTest (+8 more)

### Community 6 - "PageQuery"
Cohesion: 0.05
Nodes (23): Global Constraints, UI Polish Bundle Implementation Plan, java.sql.ResultSet, ActivityController, AuditEventResponse, AdjustmentRequest, AdjustmentResponse, AdjustmentStatus (+15 more)

### Community 7 - "Customers"
Cohesion: 0.06
Nodes (24): com.fasterxml.jackson.annotation.JsonIgnore, Rules, jakarta.validation.constraints.AssertTrue, ContactRequest, ContactResponse, Customer, CustomerContact, CustomerContactRepository (+16 more)

### Community 8 - "Settings"
Cohesion: 0.05
Nodes (26): Task 1: Schema, settings keys and FollowUp fields, `PosSaleService.checkout` — one `@Transactional`, Decisions, Goal, Out of scope, Statements of account, AR aging and overdue reminders — design (2026-11-22), ApprovalChecks, AutoConfirmListener (+18 more)

### Community 9 - "org.springframework.transaction.annotation.Transactional"
Cohesion: 0.04
Nodes (16): Task 4: Fire the events, Task 6: Conversion, org.springframework.transaction.annotation.Transactional, LabelKind, DO, PR, TR, WH (+8 more)

### Community 10 - "AuditAction"
Cohesion: 0.02
Nodes (87): AuditAction, ACCOUNT_LOCKED, AD_SPEND_SET, APPROVAL_CANCELLED, APPROVAL_DECIDED, APPROVAL_REQUESTED, ARCHIVED, ASSIGNMENT_CHANGED (+79 more)

### Community 11 - "DealService"
Cohesion: 0.06
Nodes (26): DealOrder.Key, Task 6: UI — api, list, drawer, nav, jakarta.persistence.IdClass, Deal, ResponseEntity, DealOrder, Key, DealOrderRepository (+18 more)

### Community 12 - "PortalOrderService"
Cohesion: 0.05
Nodes (25): 5. API, PaymentTerms, DUE_ON_RECEIPT, NET_15, NET_30, NET_60, DeclineRequest, InvoiceSummary (+17 more)

### Community 13 - "jakarta.servlet.http.HttpServletRequest"
Cohesion: 0.06
Nodes (21): jakarta.servlet.http.HttpServletRequest, org.springframework.security.core.Authentication, org.springframework.security.web.context.HttpSessionSecurityContextRepository, org.springframework.web.bind.annotation.DeleteMapping, AuthController, LoginRequest, LoginResponse, LoginService (+13 more)

### Community 14 - "FollowUpRule"
Cohesion: 0.05
Nodes (27): AutoRule, Candidate, FollowUpRule, DEAL_STALLED, FIRST_ORDER, PAYMENT_OVERDUE, QUOTE_CHASE, QUOTE_DECLINED (+19 more)

### Community 15 - "jakarta.servlet.http.HttpServletResponse"
Cohesion: 0.06
Nodes (33): jakarta.servlet.FilterChain, jakarta.servlet.http.HttpServletResponse, org.springframework.beans.factory.annotation.Autowired, org.springframework.boot.web.servlet.FilterRegistrationBean, org.springframework.context.annotation.Bean, org.springframework.context.annotation.Configuration, org.springframework.core.annotation.Order, org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity (+25 more)

### Community 16 - "java.time.YearMonth"
Cohesion: 0.07
Nodes (35): Frontend (`crm-dashboard-ui`), Task 10: End-to-end smoke, Task 6: API module and target card, Task 7: Insights page for sales and warehouse, routing and nav, Task 8: Owner dashboard page, Task 9: Sales targets admin page, Task 5: Insights and reports net of refunds, API, in module `insights` (+27 more)

### Community 17 - "ModularityTest"
Cohesion: 0.07
Nodes (14): Review Focus, Task 1: Table, create, list, get, summary, Task 2: Edit and stages (except Won), Task 3: Linking orders, Task 5: Follow the customer on reassignment, Task 7: UI — deal page, Task 8: UI — customer rail and order link, Task 9: e2e (+6 more)

### Community 18 - "Scope"
Cohesion: 0.07
Nodes (21): Attachment, AttachmentAccess, InvoiceRef, InvoiceRow, OrderRow, AttachmentRepository, AttachmentResponse, AttachmentService (+13 more)

### Community 19 - "OrderAction"
Cohesion: 0.03
Nodes (56): Backend (`crm/crm`), Task 1: Sales channel and POS warehouse setting, Task 3: POS product list, Task 4: Checkout, Backend (`crm`), Decisions, Elsewhere, Frontend (`crm-dashboard-ui`) (+48 more)

### Community 20 - ".lead"
Cohesion: 0.08
Nodes (5): LeadConvertIT, LeadDuplicatesIT, LeadEditIT, LeadsFixture, LeadsIT

### Community 21 - "TwoFactorIT"
Cohesion: 0.10
Nodes (11): Task 10: Identity — TOTP 2FA and recovery codes, jakarta.servlet.http.Cookie, javax.crypto.SecretKey, lombok.RequiredArgsConstructor, org.springframework.test.web.servlet.MvcResult, RecoveryCodesResponse, Totp, TotpSetupResponse (+3 more)

### Community 22 - "Inventory"
Cohesion: 0.09
Nodes (17): 3. Stock rules, Allocator, Reservation lifecycle (called from `OrderService`), AdjustmentSource, MANUAL, RETURN_DAMAGED, SHORT_PICK, TRANSIT_LOSS (+9 more)

### Community 23 - ".as"
Cohesion: 0.10
Nodes (3): Task 14: Missing-2307 report, customer order/invoice lists, final sweep, CustomerSelfServiceIT, ReportsIT

### Community 24 - "ReportRange"
Cohesion: 0.09
Nodes (24): Global Constraints, Reports (F-172) Implementation Plan, Review Focus, Task 1: Range parsing and error code, Task 4: Reports page, Task 5: End-to-end smoke, parse(), ReportBy (+16 more)

### Community 25 - "Role"
Cohesion: 0.11
Nodes (16): org.springframework.security.authentication.UsernamePasswordAuthenticationToken, org.springframework.security.core.AuthenticatedPrincipal, org.springframework.security.core.authority.SimpleGrantedAuthority, org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, UpdateUserRequest, UserSummary, dominant() (+8 more)

### Community 26 - "jakarta.persistence.Entity"
Cohesion: 0.07
Nodes (18): jakarta.persistence.Entity, jakarta.persistence.PreUpdate, jakarta.persistence.Table, org.hibernate.annotations.Immutable, AuditEvent, DeliveryOrderLine, RecoveryCode, StockAdjustment (+10 more)

### Community 27 - "org.springframework.transaction.support.TransactionTemplate"
Cohesion: 0.08
Nodes (17): Task 8: Scan resolver, QR labels, ledger check, Task 8: Daily reorder reminder, org.springframework.context.ApplicationContext, org.springframework.core.env.Environment, org.springframework.test.context.event.ApplicationEvents, org.springframework.test.context.event.RecordApplicationEvents, org.springframework.transaction.support.TransactionTemplate, ReorderReminderSchedule (+9 more)

### Community 28 - "ApprovalsIT"
Cohesion: 0.08
Nodes (9): Task 12: Approvals module, Task 2: Approvals — several requirements at once, system requests, decision event, Task 7: API — rule, closedReason, source filter, PasswordPolicy, ApprovalsIT, Guarded, ApplicationEvents, SuppressWarnings (+1 more)

### Community 29 - "PaymentService"
Cohesion: 0.08
Nodes (20): Endpoints — `PosController`, `ADMIN` and `SALES`, 4. Backend changes, 2. Events, Decisions, Goal, Out of scope, Staff Today queue — design (2026-11-23), Payment (+12 more)

### Community 30 - "RefundService"
Cohesion: 0.06
Nodes (26): Decisions, PaymentMethod, BANK_TRANSFER, CARD, CASH, CHECK, GCASH, OTHER (+18 more)

### Community 31 - "TaxClass"
Cohesion: 0.08
Nodes (20): Explicitly deferred (not in this plan), Part B — Frontend (`crm-dashboard-ui`), Task 15: Types, products screen, settings and customer fields, Task 16: Orders list and new order, Task 17: Order workspace — header, lines, totals, approvals, Task 18: Workspace tabs — invoice, payments, revisions, history, Task 19: Customer sales tabs and the Missing 2307 report, Task 20: Portal order screens (+12 more)

### Community 32 - ".reserve"
Cohesion: 0.16
Nodes (6): Task 2: Ledger, reservations and the Allocator, Task 4: Low / out of stock and order short on reservation, LineStock, AllocatorIT, Line, ReservationsIT

### Community 33 - ".diff"
Cohesion: 0.09
Nodes (15): Task 7: UI — api, Lead sources page, customer form source, Lead, LeadAssignRequest, LeadCounts, LeadRequest, LeadResponse, LeadScopes, LeadService (+7 more)

### Community 35 - "Task 2: Insights module, month parsing and sales targets"
Cohesion: 0.09
Nodes (12): Backend (`crm/crm`), Dashboards (#4) Implementation Plan, Global Constraints, Review Focus, Task 2: Insights module, month parsing and sales targets, Task 4: Copy from last month includes category targets, 1. Notification catalogue, Rules (+4 more)

### Community 36 - "AppUserRepository"
Cohesion: 0.08
Nodes (11): org.springframework.data.jpa.repository.Modifying, AppUserRepository, Base32, BootstrapAdmin, Result, ALREADY_LOCKED, COUNTED, LOCKED (+3 more)

### Community 37 - "org.springframework.data.redis.core.StringRedisTemplate"
Cohesion: 0.10
Nodes (23): Task 2: Request line and business-step line, Testing, jakarta.mail.Multipart, jakarta.mail.Session, java.lang.annotation.Retention, java.lang.annotation.Target, org.junit.jupiter.api.extension.ExtendWith, org.springframework.boot.logging.LoggingSystem (+15 more)

### Community 38 - "RefundsIT"
Cohesion: 0.16
Nodes (7): Review Focus, Task 2: Request, approve, reject, cancel, Task 3: Payout — stock, credit memo number, notification, Task 6: Order responses, security matrix, API snapshot, Task 7: Frontend, Environment, RefundsIT

### Community 39 - "org.springframework.data.jpa.repository.JpaRepository"
Cohesion: 0.10
Nodes (17): Task 2: `InventoryService.sellForPos`, org.springframework.data.jpa.repository.JpaRepository, org.springframework.data.jpa.repository.JpaSpecificationExecutor, org.springframework.data.jpa.repository.Lock, org.springframework.data.jpa.repository.Query, ApprovalRequestRepository, AuditEventRepository, ProductRepository (+9 more)

### Community 40 - "OrdersIT"
Cohesion: 0.08
Nodes (10): Task 6: Orders — create, edit, read, list, scope, history, Review Focus, Task 1: Backend sort infrastructure (`SortRequest`) applied to Orders and Customers, Task 2: Order search by customer name, Task 3: Sort keys on every other paged endpoint, Task 4: Frontend sorting (DataTable `sortKey`, `SortableHeader`, `useSortedRows`), Task 5: Image viewer, Task 6: Notification date and time (+2 more)

### Community 41 - "DeliveryOrdersIT"
Cohesion: 0.15
Nodes (7): Task 6: Delivery orders, Customer Status Emails Implementation Plan, Global Constraints, Review Focus, Task 1: Events: `PaymentRejected`, `DeliveryDispatched`, `OrderDelivered.deliveryOrderId`, Task 2: `StorageService.read` and `Fulfilment.mailView`, DeliveryOrdersIT

### Community 42 - "NotificationStreams"
Cohesion: 0.10
Nodes (17): Events (decided), Global Constraints, In-App Notifications Implementation Plan, Review Focus, Task 1: Live delivery — `NotificationStreams` + Redis fan-out, Task 2: Stored notifications — entity, migration, `NotificationService`, org.springframework.data.redis.connection.Message, org.springframework.data.redis.connection.MessageListener (+9 more)

### Community 43 - "OrderStockIT"
Cohesion: 0.16
Nodes (4): Task 7: Sales integration, Task 5: Order lifecycle notifications, org.springframework.test.web.servlet.RequestBuilder, OrderStockIT

### Community 44 - "NotificationsIT"
Cohesion: 0.08
Nodes (11): Task 3: REST + SSE endpoints, Task 11: Notifications (carried over, re-keyed to users), org.springframework.data.domain.Page, org.springframework.web.servlet.config.annotation.CorsRegistry, org.springframework.web.servlet.config.annotation.WebMvcConfigurer, Notification, NotificationController, NotificationRepository (+3 more)

### Community 45 - "PortalOrdersIT"
Cohesion: 0.12
Nodes (12): Task 13: Portal orders, accept/decline, portal payments, auto-confirm, Global Constraints, Refunds Implementation Plan, API, Decisions, Goal, Out of scope, Portal quote requests and reorder — design (2026-11-22) (+4 more)

### Community 46 - "PurchaseOrderService"
Cohesion: 0.11
Nodes (10): Global Constraints, Purchasing Terms and PO Document Implementation Plan, CreditCheck, PoResendResponse, PurchaseOrder, PurchaseOrderController, PurchaseOrderDocument, PurchaseOrderRepository (+2 more)

### Community 47 - "UserAdminIT"
Cohesion: 0.15
Nodes (6): Task 9: Identity — tokens, mail, invites, user admin, password reset, bootstrap, AcceptInviteRequest, AppUser, ResetPasswordRequest, ResultActions, UserAdminIT

### Community 48 - ".createdOrder"
Cohesion: 0.13
Nodes (5): Task 7: PDF rendering, quote PDF and Send quote, Task 8: Confirm and the approval checks, ConfirmIT, ResultActions, SendQuoteIT

### Community 49 - "RedisTestSupport"
Cohesion: 0.10
Nodes (16): AssertTrue, Task 5: Dashboard caching (R-06), Task 5: `PosController`, receipt email status and idempotency, Task 1: Branch, dependencies, legacy removal, test harness, org.junit.jupiter.params.ParameterizedTest, org.junit.jupiter.params.provider.ValueSource, org.springframework.context.annotation.AnnotationConfigApplicationContext, org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory (+8 more)

### Community 50 - ".at"
Cohesion: 0.19
Nodes (5): Task 3: Sales rep dashboard, Review Focus, Task 2: Category progress on the rep dashboard (backend), Task 6: Category progress card on the sales dashboard (frontend), SalesInsightsIT

### Community 51 - ".order"
Cohesion: 0.16
Nodes (5): Task 4: Owner dashboard, Task 3: Product report with pro-rata split, Task 7: Dashboard style C and "See more", AdminInsightsIT, ReportsProductIT

### Community 52 - "TransferService"
Cohesion: 0.13
Nodes (13): ReceiveLineRequest, ReceiveRequest, TransferController, TransferLineRequest, TransferRequest, TransferLineResponse, TransferResponse, TransferService (+5 more)

### Community 53 - "PaymentsIT"
Cohesion: 0.18
Nodes (5): Task 10: Payments — record, verify, reject, 2307, Task 2: Upload proof after recording a payment, Task 2: Payment notifications and `PaymentVerified` event, org.springframework.mock.web.MockMultipartFile, PaymentsIT

### Community 54 - "DeliveryService"
Cohesion: 0.13
Nodes (13): DeliveryOrder, DeliveryOrderController, DeliveryOrderRepository, DeliveryLineResponse, DeliveryOrderResponse, DeliveryService, DeliveryStatus, CANCELLED (+5 more)

### Community 55 - "AttachmentsIT"
Cohesion: 0.13
Nodes (4): AttachmentAccessIT, AttachmentsAllOrNothingIT, AttachmentsFixture, AttachmentsIT

### Community 56 - "HtmlMail"
Cohesion: 0.11
Nodes (14): org.slf4j.Logger, org.springframework.core.task.TaskExecutor, org.thymeleaf.TemplateEngine, DeliveryMailView, Line, IdentityMailer, InviteIssued, PortalMailer (+6 more)

### Community 57 - "ApprovalType"
Cohesion: 0.10
Nodes (19): ApprovalController, ApprovalRequest, ApprovalResponse, Link, ApprovalStatus, APPROVED, CANCELLED, PENDING (+11 more)

### Community 58 - "ProductCategories"
Cohesion: 0.13
Nodes (7): CatalogPermissions, ProductCategories, ProductCategory, ProductCategoryController, ProductCategoryRepository, ProductCategoryRequest, ProductCategoryResponse

### Community 60 - "org.springframework.context.event.EventListener"
Cohesion: 0.07
Nodes (19): Follow-ups (F-141) Implementation Plan, Global Constraints, Deals (F-130) Implementation Plan, Global Constraints, API, Approach, Data — Flyway `V15__deals.sql`, Deals / opportunities (F-130) — design (+11 more)

### Community 61 - ".customer"
Cohesion: 0.21
Nodes (3): Task 2: Engine (`AutoFollowUps`, `AutoRule`, `RestockRhythm`), org.springframework.cache.annotation.CacheEvict, AutoFollowUpsIT

### Community 62 - "RestockRuleIT"
Cohesion: 0.16
Nodes (8): Task 3: Customer rules (RESTOCK, WIN_BACK, FIRST_ORDER) and the order-confirmed close, FirstOrderRuleIT, ApplicationEventPublisher, PlatformTransactionManager, RestockRuleIT, ApplicationEventPublisher, PlatformTransactionManager, WinBackRuleIT

### Community 63 - ".run"
Cohesion: 0.23
Nodes (4): Task 4: Order rules (QUOTE_CHASE, QUOTE_EXPIRING, QUOTE_DECLINED, PAYMENT_OVERDUE) and their closes, AutoFollowUps, PaymentOverdueRuleIT, QuoteRulesIT

### Community 64 - "CustomerStatusMailsIT"
Cohesion: 0.19
Nodes (4): Task 3: `CustomerStatusMails`: confirmed, payment received, payment rejected, Task 4: Dispatched and delivered emails, jakarta.mail.internet.MimeMessage, CustomerStatusMailsIT

### Community 65 - ".contains"
Cohesion: 0.18
Nodes (13): Review Focus, Task 2: Ad performance report, Task 3: Ad performance page (`crm-dashboard-ui`), Ad performance (spend vs acquired-customer value) — design, API (all `ADMIN` only), Data, Decisions, Frontend (`crm-dashboard-ui`) (+5 more)

### Community 66 - "SupplierService"
Cohesion: 0.12
Nodes (7): SupplierCredit, SupplierPayment, SupplierPaymentRepository, SupplierPaymentResponse, SupplierRequest, SupplierResponse, SupplierService

### Community 67 - "DistributedLock"
Cohesion: 0.14
Nodes (13): Global Constraints, Redis Phase Implementation Plan, Review Focus, Task 0: Commit the MinIO work, Task 1: Redis wiring and token revocation (R-01, R-02, R-09 part), Task 3: Single-flight locks on customer links and staff accept (R-04), Task 4: Run the quotation expiry job on one instance (R-05), Task 6: Idempotency-Key on creates (R-07) (+5 more)

### Community 68 - ".followUp"
Cohesion: 0.16
Nodes (9): Review Focus, Task 4: Follow the customer on reassignment, Task 5: Morning bell, Task 6: UI — api, page, route, nav badge, Task 7: UI — add/edit drawer and rail sections, Task 8: Dashboard stat, bell link and e2e, Task 6: Overdue notification, FollowUpReassignIT (+1 more)

### Community 69 - "Design"
Cohesion: 0.06
Nodes (27): Frontend (`crm-dashboard-ui`), Global Constraints, Notification copy (exact), Notifications Implementation Plan, Review Focus, Task 10: Remove Inbox, Task 11: Graph refresh and final check, Task 7: Regenerate API and notification links (+19 more)

### Community 70 - "FollowUpService"
Cohesion: 0.17
Nodes (5): FollowUp, FollowUpRepository, FollowUpRequest, FollowUpResponse, FollowUpService

### Community 71 - "V5__inventory_fulfilment.sql"
Cohesion: 0.11
Nodes (28): user_warehouse, warehouse, warehouse_code_uq, warehouse_one_default, delivery_order_line_delivery_order_idx, stock_transfer_line_transfer_idx, product, product_name_idx (+20 more)

### Community 72 - "ProductUnit"
Cohesion: 0.09
Nodes (19): com.fasterxml.jackson.annotation.JsonInclude, ProductUnit, BOX, G, KG, L, M, ML (+11 more)

### Community 73 - "PortalAuthIT"
Cohesion: 0.16
Nodes (4): Task 14: Portal identity (`portal` module), PortalLinkRequest, PortalRedeemRequest, PortalAuthIT

### Community 74 - ".issue"
Cohesion: 0.13
Nodes (10): jakarta.persistence.EntityManager, Subject, TokenPurpose, INVITE, PASSWORD_RESET, PAYMENT_PROOF, PORTAL_LOGIN, QUOTE_RESPONSE (+2 more)

### Community 75 - "CustomerAddresses"
Cohesion: 0.17
Nodes (6): AddressRequest, AddressResponse, CustomerAddress, CustomerAddresses, CustomerAddressRepository, PortalProfileController

### Community 76 - "SettingKey"
Cohesion: 0.07
Nodes (29): SettingKey, APPROVAL_DISCOUNT_PERCENT_MAX, APPROVAL_WRITE_OFF_VALUE_MAX, COMPANY_ADDRESS, COMPANY_NAME, COMPANY_TIN, FOLLOW_UPS_AUTO_DEAL_STALLED_ENABLED, FOLLOW_UPS_AUTO_FIRST_ORDER_ENABLED (+21 more)

### Community 77 - "PasswordResetService"
Cohesion: 0.11
Nodes (8): java.security.SecureRandom, ForgotPasswordRequest, InviteAcceptController, OneTimeTokens, PasswordResetService, PortalAuthController, PortalAuthService, PortalMeResponse

### Community 78 - "SendPurchaseOrderIT"
Cohesion: 0.24
Nodes (3): Task 5: Send with credit check and approval, Task 2: Send requires the downpayment, SendPurchaseOrderIT

### Community 79 - "Redesign #1 — Foundation"
Cohesion: 0.07
Nodes (26): 10. Testing, 11. Deployment changes, 2. Data conventions (Postgres), 3. Identity & sessions, 4. Authorization & scoping, 5. Approvals, 6. Audit, 7. Customers & warehouses (first vertical slice) (+18 more)

### Community 81 - "NotificationType"
Cohesion: 0.08
Nodes (26): NotificationType, APPROVAL_DECIDED, APPROVAL_REQUESTED, DELIVERY_CREATED, FOLLOW_UP_OVERDUE, FOLLOW_UPS_DUE, LOW_STOCK, ORDER_CONFIRMED (+18 more)

### Community 82 - "SecurityMatrixTest"
Cohesion: 0.10
Nodes (18): Task 15: Rate limits, idempotency, OpenAPI snapshot, security matrix, API — `/api/follow-ups`, Data — Flyway `V11__follow_ups.sql`, Decisions (from the user), Follow-ups (F-141) — design, Goal, Out of scope, Rules (+10 more)

### Community 83 - "LoginIT"
Cohesion: 0.21
Nodes (4): Task 6: Identity — security config, sessions, login, logout, me, javax.sql.DataSource, org.springframework.session.data.redis.RedisIndexedSessionRepository, LoginIT

### Community 84 - "StockReadsIT"
Cohesion: 0.18
Nodes (3): Task 3: Stock reads, availability and receipts, ReceiptsIT, StockReadsIT

### Community 85 - ".update"
Cohesion: 0.15
Nodes (4): Product, ProductController, ProductRequest, ProductResponse

### Community 87 - "RateLimitFilterIT"
Cohesion: 0.21
Nodes (8): Task 2: Rate limiting (R-03), org.junit.jupiter.params.provider.CsvSource, org.springframework.mock.web.MockFilterChain, RateLimiter, Override, RateLimitFilter, Rule, RateLimitFilterIT

### Community 89 - "SupplierCreditIT"
Cohesion: 0.23
Nodes (4): Task 4: Credit math, payments, statement, Task 1: Downpayment percent and PO-linked payments, ResultActions, SupplierCreditIT

### Community 90 - "Redesign #3 — Inventory & Fulfilment"
Cohesion: 0.08
Nodes (23): 1. Modules, 2. Data (Flyway, Postgres), 4. Flows, 6. Access, 7. Audit and notifications, 8. Frontend, 9. Testing, Adjustment (+15 more)

### Community 91 - "org.springframework.stereotype.Component"
Cohesion: 0.15
Nodes (9): jakarta.annotation.PostConstruct, org.springframework.boot.autoconfigure.condition.ConditionalOnProperty, org.springframework.scheduling.annotation.Scheduled, org.springframework.stereotype.Component, AutoFollowUpsSchedule, FollowUpReminder, FollowUpReminderSchedule, OverdueReminderSchedule (+1 more)

### Community 92 - "ProblemDetailAdvice"
Cohesion: 0.22
Nodes (12): jakarta.validation.ConstraintViolationException, org.springframework.dao.OptimisticLockingFailureException, org.springframework.http.HttpStatusCode, org.springframework.http.ProblemDetail, org.springframework.security.access.AccessDeniedException, org.springframework.web.bind.annotation.ExceptionHandler, org.springframework.web.bind.annotation.RestControllerAdvice, org.springframework.web.bind.MethodArgumentNotValidException (+4 more)

### Community 93 - "IntegrationTest.java"
Cohesion: 0.15
Nodes (16): org.springframework.boot.test.context.SpringBootTest, org.springframework.boot.test.context.TestConfiguration, org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc, org.springframework.context.ApplicationContextInitializer, org.springframework.context.ConfigurableApplicationContext, org.springframework.test.context.ActiveProfiles, org.springframework.test.context.ContextConfiguration, org.testcontainers.containers.MinIOContainer (+8 more)

### Community 94 - "Part B — Frontend (`crm-dashboard-ui`)"
Cohesion: 0.09
Nodes (21): Explicitly deferred (not in this plan), Part B — Frontend (`crm-dashboard-ui`), Task 17: Branch, dependencies, structure, API client, auth state, Task 18: Component kit, Task 19: App shell, Task 20: Auth screens, Task 21: Profile & security, user and warehouse admin, Task 22: Settings, activity log, inbox, approval dialog (+13 more)

### Community 95 - ".update"
Cohesion: 0.19
Nodes (5): Part A — Backend (`crm`), Redesign #2 Sales Lifecycle — Implementation Plan, Review Focus, Task 12: Revise confirmed orders, ReviseIT

### Community 97 - "PaymentVerified"
Cohesion: 0.13
Nodes (10): API (ADMIN), Notifications, Out of scope, Per-category sales targets — design, Schema, What, CategoryTargetRow, Category (+2 more)

### Community 98 - "sales_order"
Cohesion: 0.15
Nodes (20): order_revision, payment_status_verified_idx, sales_order_rep_idx, sales_target, money_movement, refund, refund_line, refund_line_refund_idx (+12 more)

### Community 99 - "Sheets"
Cohesion: 0.17
Nodes (3): ImportResult, RowError, Sheets

### Community 100 - "InvoicesIT"
Cohesion: 0.26
Nodes (5): Task 9: Invoices — issue, gapless numbers, void and reissue, PDF, Backend (`crm/crm`), Task 1: Notifications core — types, self-skip, multi-user send, unread filter, Task 6: Approvals — subject links, list filters, contract snapshot, InvoicesIT

### Community 101 - ".json"
Cohesion: 0.16
Nodes (4): Task 4: Credit memo PDF, CreditMemoIT, Environment, PdfRendererTest

### Community 102 - "Refunds — design (2026-11-16)"
Cohesion: 0.09
Nodes (19): 2. Lifecycle, 3. Money and payment status, 4. Insights, reports, dashboard, 5. Notifications, 6. Credit memo, 7. Frontend (`crm-dashboard-ui`), 8. Testing, Goal (+11 more)

### Community 103 - "org.springframework.transaction.event.TransactionalEventListener"
Cohesion: 0.14
Nodes (5): org.springframework.transaction.event.TransactionalEventListener, DealDeleted, AutoCloseListeners, AdjustmentApprovalListener, PoApprovalListener

### Community 104 - "LeadSources"
Cohesion: 0.22
Nodes (5): LeadSource, LeadSourceRepository, LeadSourceRequest, LeadSourceResponse, LeadSources

### Community 105 - "PoResponse"
Cohesion: 0.19
Nodes (5): DraftFromSuggestionsRequest, PoLineRequest, PoRequest, PoLineResponse, PoResponse

### Community 106 - "PdfRenderer"
Cohesion: 0.19
Nodes (6): com.microsoft.playwright.Browser, com.microsoft.playwright.Page, com.microsoft.playwright.Playwright, jakarta.annotation.PreDestroy, DocumentController, PdfRenderer

### Community 107 - "WarehousesIT"
Cohesion: 0.23
Nodes (6): Global Constraints, Part A — Backend (`crm`), Redesign #1 Foundation — Implementation Plan, Review Focus, Task 7: Warehouses module, WarehousesIT

### Community 108 - "LogRedactorTest"
Cohesion: 0.19
Nodes (6): Task 1: LogRedactor, Create, Customer, Login, LogRedactorTest, Node

### Community 109 - "app_user"
Cohesion: 0.18
Nodes (20): app_setting, app_user, app_user_email_uq, approval_one_pending, approval_request, approval_subject_idx, audit_actor_idx, audit_at_idx (+12 more)

### Community 113 - "Redesign #2 — Sales lifecycle"
Cohesion: 0.10
Nodes (19): 1. Modules, 4. Money, invoices and payments, 5. Access and API, 6. Screens (`crm-dashboard-ui`), 7. Testing, Audit and notifications, Authorization, Context (+11 more)

### Community 114 - "Task 13: Customers module (first scoped slice)"
Cohesion: 0.26
Nodes (3): Task 13: Customers module (first scoped slice), CustomerPermissions, CustomerScopingIT

### Community 115 - "ProblemDetailAdviceTest"
Cohesion: 0.15
Nodes (6): Task 2: Shared kernel — errors, request id, principals, paging, org.springframework.mock.web.MockHttpServletResponse, Body, ProblemDetailAdviceTest, TestController, RequestIdFilterTest

### Community 116 - ".customers"
Cohesion: 0.12
Nodes (15): Global Constraints, Task 1: Migration, lead sources on customers, Ad Performance Implementation Plan, Global Constraints, 1. Backend module layout, API, Approach, Data — Flyway `V14__leads.sql` (+7 more)

### Community 118 - "UI polish bundle — design (2026-11-17)"
Cohesion: 0.11
Nodes (18): 1. Sorting on every table, 2. Order search by customer name, 3. Image viewer, 4. Notification date and time, 5. Dashboard style C, Backend, Component, Decisions (from the user) (+10 more)

### Community 119 - ".parseForPlanning"
Cohesion: 0.17
Nodes (4): AdSpendController, AdSpendRequest, AdSpendRow, TargetController

### Community 120 - "OrderStock"
Cohesion: 0.25
Nodes (4): CoverShortage, OrderStock, ReserveLine, CoverShortageTest

### Community 121 - "FollowUpEditIT"
Cohesion: 0.28
Nodes (3): Task 3: Edit, done and reopen, FollowUpEditIT, PlatformTransactionManager

### Community 122 - "TargetMilestonesIT"
Cohesion: 0.42
Nodes (3): Task 3: Target milestones, Task 3: Category milestone notifications, TargetMilestonesIT

### Community 123 - "AdSpendIT"
Cohesion: 0.24
Nodes (4): Task 1: Schema, enums and `Inventory.receiveReturn`, Task 1: Spend storage and API, 1. Data model, AdSpendIT

### Community 124 - "SuppliersIT"
Cohesion: 0.17
Nodes (9): Global Constraints, Purchasing Implementation Plan, Review Focus, Task 10 (frontend): Suppliers pages, Task 11 (frontend): Purchase order pages, Task 12 (frontend): Reorder suggestions, product fields, notification link, Task 1: Schema, Supplier CRUD, security wiring, ResultActions (+1 more)

### Community 127 - "PurchaseOrderMailIT"
Cohesion: 0.26
Nodes (5): Review Focus, Task 4: Email the PO to the supplier on send, plus resend, Task 5: Frontend supplier edit, downpayment field and linked payments, Task 6: Frontend PO page document actions and downpayment gate, PurchaseOrderMailIT

### Community 128 - "Retail POS — Design"
Cohesion: 0.11
Nodes (17): Backend (`crm`), Backend tests, Checkout flow, Components — `src/components/pos/`, Decisions, Emailed, Endpoints — new `PosController` at `/api/v1/pos`, `@PreAuthorize("hasAnyAuthority('ADMIN','SALES')")`, Frontend checks (+9 more)

### Community 129 - "ServiceCallLogging"
Cohesion: 0.19
Nodes (8): java.lang.reflect.Method, org.aopalliance.intercept.MethodInvocation, org.springframework.aop.Advisor, org.springframework.aop.Pointcut, org.springframework.beans.factory.config.BeanPostProcessor, org.springframework.context.annotation.Role, org.springframework.core.ParameterNameDiscoverer, ServiceCallLogging

### Community 130 - "lombok.extern.slf4j.Slf4j"
Cohesion: 0.16
Nodes (9): lombok.extern.slf4j.Slf4j, org.springframework.cache.annotation.CachingConfigurer, org.springframework.cache.annotation.EnableCaching, org.springframework.cache.interceptor.CacheErrorHandler, org.springframework.data.redis.core.script.RedisScript, Override, RetryingListenerContainer, CacheConfig (+1 more)

### Community 132 - "OrderDelivered"
Cohesion: 0.12
Nodes (13): Global Constraints, Part A — Backend (`crm`), Redesign #3 Inventory & Fulfilment — Implementation Plan, Review Focus, 3. Components, 4. Error handling, 5. Testing, Customer status emails — design (2026-11-18) (+5 more)

### Community 133 - "StorageService"
Cohesion: 0.20
Nodes (5): Task 1: StorageService — detect, put, presign for download, io.minio.MinioClient, Detected, Pattern, StorageService

### Community 134 - "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate"
Cohesion: 0.18
Nodes (5): org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate, TodayQueries, Item, Section, TodayQueue

### Community 135 - "OpenApiSnapshotTest"
Cohesion: 0.14
Nodes (12): Global Constraints, Leads (F-120) Implementation Plan, Review Focus, Task 10: e2e, Task 2: Lead sources API, Task 3: Leads module — create, list, get, counts, Task 4: Edit, status, assign, Task 8: UI — leads list and lead form (+4 more)

### Community 137 - "AdPerformanceService"
Cohesion: 0.22
Nodes (5): AdMonth, AdPerformance, AdRow, AdPerformanceService, AdCohortRow

### Community 138 - "Statements"
Cohesion: 0.22
Nodes (4): AgingBuckets, ArAgingRow, OpenItem, Statements

### Community 140 - "Automatic follow-ups — design (2026-11-17)"
Cohesion: 0.13
Nodes (14): 1. Data — `V24__auto_follow_ups.sql`, 2. Settings (`SettingKey`, admin-editable, validated like existing keys), 3. Daily scan — `followups/AutoFollowUps`, 4. Rules (each is one class in `followups/rules/`, implementing `AutoRule`), 5. Overdue notification, 6. API, 7. Frontend (`crm-dashboard-ui`), 8. Testing (+6 more)

### Community 141 - "RestockRule.java"
Cohesion: 0.17
Nodes (5): RestockRhythm, Reorders, Delivery, Override, RestockRule

### Community 143 - "AuditLogIT"
Cohesion: 0.25
Nodes (3): Task 4: Audit module, AuditChangesTest, AuditLogIT

### Community 144 - "StorageServiceIT"
Cohesion: 0.19
Nodes (4): Task 3: Upload, list, download, archive, org.junit.jupiter.api.BeforeAll, org.testcontainers.junit.jupiter.Testcontainers, StorageServiceIT

### Community 145 - "Purchasing: supplier downpayment terms, PO document, resend"
Cohesion: 0.14
Nodes (12): Data model (`V31__purchasing_terms.sql`), Decisions, Emailing, Frontend (`crm-dashboard-ui`), Goal, Out of scope, PO document, Purchasing: supplier downpayment terms, PO document, resend (+4 more)

### Community 146 - "SettingKey.java"
Cohesion: 0.31
Nodes (10): bankAccounts(), bool(), field(), invalid(), number(), of(), positiveIdOrNull(), text() (+2 more)

### Community 147 - "Task 3: Service calls and background IDs"
Cohesion: 0.18
Nodes (4): Task 3: Service calls and background IDs, LoggingProbeService, ProbeEvent, Probes

### Community 149 - "SuggestionQuery"
Cohesion: 0.24
Nodes (7): Task 5: Admin editor for category targets (frontend), Task 9: API contract and module check, Row, SuggestionQuery, SuggestionGroup, SuggestionItem, SuggestionResponse

### Community 150 - "Redesign #4 — Dashboards"
Cohesion: 0.15
Nodes (12): API — module `insights`, Caching, Decisions, Figures, Frontend — `crm-dashboard-ui`, `GET /api/insights/admin` — role ADMIN, `GET /api/insights/sales` — role SALES, always the signed-in user (no `repId` parameter), `GET /api/insights/warehouse` — roles WAREHOUSE, ADMIN (+4 more)

### Community 152 - "Purchasing: suppliers, credit limits, purchase orders, reorder suggestions"
Cohesion: 0.17
Nodes (11): Daily reminder, Decisions, Errors, Frontend (`crm-dashboard-ui`), Goal, Out of scope, Permissions, PO lifecycle (+3 more)

### Community 153 - "FollowUpQueries"
Cohesion: 0.20
Nodes (4): FollowUpCounts, Due, FollowUpQueries, Overdue

### Community 154 - "Bucket"
Cohesion: 0.18
Nodes (11): Bucket, DONE, OVERDUE, TODAY, UPCOMING, FollowUpDay, parse(), Source (+3 more)

### Community 156 - "Review Focus"
Cohesion: 0.35
Nodes (3): Review Focus, Task 8: Frontend, RestockRhythmTest

### Community 158 - "CustomerStatusMails"
Cohesion: 0.29
Nodes (5): 1. Emails, DeliveryDispatched, CustomerStatusMails, Mail, PaymentRejected

### Community 160 - "LeadSourceBackfillIT"
Cohesion: 0.35
Nodes (3): org.flywaydb.core.Flyway, org.springframework.jdbc.datasource.DriverManagerDataSource, LeadSourceBackfillIT

### Community 161 - "RedisSessionAdmin"
Cohesion: 0.36
Nodes (4): org.springframework.session.FindByIndexNameSessionRepository, org.springframework.session.Session, Override, RedisSessionAdmin

### Community 162 - "lead"
Cohesion: 0.31
Nodes (9): lead, lead_customer_idx, lead_email_idx, lead_phone_idx, lead_rep_status_idx, lead_source, lead_source_name_uq, lead_status_idx (+1 more)

### Community 163 - "V30__purchasing.sql"
Cohesion: 0.27
Nodes (9): purchase_order, purchase_order_line, purchase_order_line_po_idx, purchase_order_line_product_idx, purchase_order_supplier_status_idx, reorder_reminder_state, supplier_payment, supplier_payment_supplier_idx (+1 more)

### Community 165 - "MinioHealthIndicator"
Cohesion: 0.24
Nodes (6): Task 16: Deployment and CI, 8. Errors & observability, org.springframework.boot.health.contributor.Health, org.springframework.boot.health.contributor.HealthIndicator, Override, MinioHealthIndicator

### Community 166 - "Frontend (`crm-dashboard-ui`)"
Cohesion: 0.20
Nodes (9): Frontend (`crm-dashboard-ui`), Global Constraints, POS (#4) Implementation Plan, Review Focus, Task 5: API types and POS API module, Task 6: Cart maths, Task 7: POS page, Task 8: Proof upload, channel filter and badge (+1 more)

### Community 167 - "Attachments (M-08) — design"
Cohesion: 0.20
Nodes (9): API — `/api/attachments`, Attachments (M-08) — design, Data — Flyway `V13__attachments.sql`, Decisions (from the user), Goal, Out of scope, Rules, Screens (crm-dashboard-ui) (+1 more)

### Community 169 - ".receive"
Cohesion: 0.27
Nodes (4): ReceiptLineRequest, ReceiptRequest, PoReceiveLine, PoReceiveRequest

### Community 170 - "CreditQuery"
Cohesion: 0.29
Nodes (3): CreditQuery, SupplierStatementEntry, SupplierStatementResponse

### Community 173 - "transform.sql"
Cohesion: 0.22
Nodes (4): l, o, pg_temp.sys(), pg_temp.uid()

### Community 174 - "Backend (`crm`)"
Cohesion: 0.22
Nodes (8): Backend (`crm`), Task 1: Schema, enums and response fields, Task 3: `PosSaleService.checkout`, Task 4: POS product search, Task 6: Guard B2B flows against POS invoices, Task 7: Receipt email and PDF, validate(), tools.jackson.databind.JsonNode

### Community 175 - ".address"
Cohesion: 0.22
Nodes (8): 2. Data model, approvals, catalog, customers, sales, settings, Data model (`V30__purchasing.sql`), Money (computed, never stored)

### Community 176 - "Notifications — design (2026-11-12)"
Cohesion: 0.22
Nodes (8): 2. Notifications page, bell, routing (frontend), 3. Approvals on record pages, 5. Testing, Backend, Frontend, Goal, Notifications — design (2026-11-12), What already exists (unchanged)

### Community 177 - "package.json"
Cohesion: 0.22
Nodes (8): tailwindcss, @tailwindcss/cli, devDependencies, tailwindcss, @tailwindcss/cli, private, scripts, build

### Community 179 - "FakeRule"
Cohesion: 0.36
Nodes (3): FakeRule, Fakes, Override

### Community 180 - "Retail POS Implementation Plan"
Cohesion: 0.25
Nodes (7): Frontend (`crm-dashboard-ui`), Global Constraints, Retail POS Implementation Plan, Review Focus, Task 10: Payment, success view and printed receipt, Task 8: Pricing preview and API client, Task 9: Route, sidebar and the selling screen

### Community 182 - "3. Order lifecycle"
Cohesion: 0.25
Nodes (8): 3. Order lifecycle, Cancel, Confirm, Draft and quote, Fulfil, Portal accept and decline, Release, Revise

### Community 184 - "PoStatus"
Cohesion: 0.25
Nodes (7): PoStatus, CANCELLED, DRAFT, PARTIALLY_RECEIVED, PENDING_APPROVAL, RECEIVED, SENT

### Community 186 - "follow_up"
Cohesion: 0.36
Nodes (6): follow_up, follow_up_customer_idx, follow_up_open_idx, follow_up_order_idx, auto_follow_up_run, follow_up_open_rule_idx

### Community 188 - "migrate.py"
Cohesion: 0.43
Nodes (6): copy_legacy(), env(), main(), One-time MySQL -> Postgres migration (P5-1). See deploy/migrate/CUTOVER.md.…, Records which referenced uploads exist on disk, so proofs can become…, stat_files()

### Community 189 - "Review Focus"
Cohesion: 0.29
Nodes (6): Attachments (M-08) Implementation Plan, Global Constraints, Review Focus, Task 2: Table, entity and access, Task 4: UI — FilesSection, Task 5: Place it, and e2e

### Community 190 - "Review Focus"
Cohesion: 0.29
Nodes (6): Global Constraints, Logging Across the App Implementation Plan, Review Focus, Task 4: Files, levels and the loggers endpoint, Task 5: Secrets never logged, Task 6: UI — Ref on errors, dev console

### Community 191 - "RefundDisposition"
Cohesion: 0.29
Nodes (5): RefundDisposition, DAMAGED, RESTOCK, RefundLineRequest, Line

### Community 193 - "deal"
Cohesion: 0.52
Nodes (6): deal, deal_customer_idx, deal_order, deal_order_order_uq, deal_rep_stage_idx, deal_stage_idx

### Community 194 - "Production cutover (P5-2)"
Cohesion: 0.33
Nodes (5): Afterwards, Before the window, Production cutover (P5-2), Rollback, Steps

### Community 195 - "Reports (F-172)"
Cohesion: 0.33
Nodes (5): Decisions, Frontend, in `crm-dashboard-ui`, Goal, Reports (F-172), Testing

### Community 196 - "Customer self-service profile — design (2026-11-22)"
Cohesion: 0.33
Nodes (5): API (portal, CUSTOMER only, scoped to the contact's company), Customer self-service profile — design (2026-11-22), Decisions, Goal, Out of scope

### Community 197 - "CrmApplication"
Cohesion: 0.53
Nodes (4): org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration, org.springframework.scheduling.annotation.EnableScheduling, CrmApplication

### Community 204 - "V26__product_category.sql"
Cohesion: 0.40
Nodes (4): product_category, product_category_id_idx, product_category_name_uq, sales_category_target

### Community 209 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 212 - "AdjustmentMode"
Cohesion: 0.50
Nodes (3): AdjustmentMode, COUNTED, DELTA

### Community 213 - "customer_address"
Cohesion: 0.83
Nodes (3): customer_address, customer_address_customer, customer_address_one_default

## Knowledge Gaps
- **620 isolated node(s):** `o`, `l`, `01-roles.sh script`, `PENDING`, `APPROVED` (+615 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 938 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **72 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `app_user` connect `app_user` to `deal`, `lead`, `sales_order`, `V30__purchasing.sql`, `V5__inventory_fulfilment.sql`, `V26__product_category.sql`, `transform.sql`, `attachment`, `user_role`, `follow_up`?**
  _High betweenness centrality (0.053) - this node is a cross-community bridge._
- **Why does `AuditAction` connect `AuditAction` to `org.springframework.web.bind.annotation.GetMapping`, `CurrentActor`, `ErrorCode`, `org.junit.jupiter.api.Test`, `PageQuery`, `org.springframework.transaction.annotation.Transactional`, `Scope`, `.as`, `Role`, `jakarta.persistence.Entity`, `PaymentService`, `AppUserRepository`, `PortalOrdersIT`, `PurchaseOrderService`, `.createdOrder`, `PaymentsIT`, `PasswordResetService`, `.update`, `InvoicesIT`, `.customers`?**
  _High betweenness centrality (0.045) - this node is a cross-community bridge._
- **Why does `DeliveryMailView` connect `HtmlMail` to `attachment`, `DeliveryOrdersIT`, `OrderService`, `CustomerStatusMails`?**
  _High betweenness centrality (0.038) - this node is a cross-community bridge._
- **What connects `o`, `l`, `01-roles.sh script` to the rest of the system?**
  _620 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `org.springframework.web.bind.annotation.GetMapping` be split into smaller, more focused modules?**
  _Cohesion score 0.04128468216866007 - nodes in this community are weakly interconnected._
- **Should `CurrentActor` be split into smaller, more focused modules?**
  _Cohesion score 0.06750700280112044 - nodes in this community are weakly interconnected._
- **Should `OrderService` be split into smaller, more focused modules?**
  _Cohesion score 0.054375804375804374 - nodes in this community are weakly interconnected._