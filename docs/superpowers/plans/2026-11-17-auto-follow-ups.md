# Automatic Follow-ups Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A daily scan creates sales follow-ups from seven rules, closes them when their reason goes away, and sends a one-time notification when any follow-up goes overdue.

**Architecture:**
- An `AutoFollowUps` engine in the `followups` module runs a list of `AutoRule` beans. Each rule is one class with its own SQL.
- New rows are deduplicated by a unique `auto_key`.
- Closing works two ways: the engine runs a close pass, and listeners close rows as soon as `sales`/`deals` events fire. Those modules only publish events and never depend on `followups`.
- Settings live in the existing generic `SettingKey`/`Settings` store.

**Tech Stack:** Spring Boot, JPA + JdbcTemplate/NamedParameterJdbcTemplate, Flyway/Postgres, `@Scheduled`; React + Vite + generated OpenAPI client, vitest.

**Spec:** `docs/superpowers/specs/2026-11-17-auto-follow-ups-design.md`

## Global Constraints

**Repos and branches**
- Backend `crm/crm`, branch `phase/major_refactor_ui`. Frontend `crm-dashboard-ui`, branch `phase/major_refactor`. Never push.
- Never stage `src/main/resources/application.properties`. Stage files by path.

**Build and tooling**
- Gradle from Claude's shell: `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/Users/Public/gtmp timeout 2400 ./gradlew --no-daemon -I C:/Users/Public/gbuild-init.gradle test --tests '<pattern>'`.
- Baseline failures that are not ours: ModularityTest, SecretsNotLoggedIT, ServiceCallLoggingIT.
- Use `graphify query` before broad grepping. Run `graphify update .` before each commit.
- After a DTO or endpoint change, regenerate the snapshot (`--tests '*OpenApiSnapshotTest' -DupdateSnapshot=true`) and add `SecurityMatrixTest` rows for new routes.

**Data**
- Migration: `src/main/resources/db/migration/V24__auto_follow_ups.sql` (the only one).
- All dates are Manila calendar dates via `FollowUpDay.today(clock)`. Tests use a fixed `Clock`.

**Scheduling**
- Scan cron `"0 45 7 * * *"`, zone `"Asia/Manila"`.
- Overdue pass runs inside the existing 08:00 `FollowUpReminder` transaction.

**Exact copy (titles, bodies, reasons)**
- RESTOCK `Check if {customer} still has stock — last order {SO}, {n} days ago`
- WIN_BACK `{customer} hasn't ordered in {n} days — check in`
- QUOTE_CHASE `Follow up on quote {SO} (₱{total})`
- QUOTE_EXPIRING `Quote {SO} expires {d MMM} — confirm or extend`
- QUOTE_DECLINED `Ask {customer} why they declined {SO}`
- FIRST_ORDER `How did {customer} find their first order ({SO})?`
- PAYMENT_OVERDUE `Collect ₱{balance} on {SO} ({n} days overdue)`
- DEAL_STALLED `Move deal "{title}" forward ({Stage}, {n} days in stage)`
- Overdue notification: title `Overdue: {title}`, body `Due {d MMM} · {customer}`, entity `CUSTOMER`/{customerId}.
- Money format: `₱` + `#,##0.00`. Stage in title case (`Proposal`).

**Settings keys and defaults (spec §2)**

| Key | Default | Range |
|---|---|---|
| `followUps.auto.<RULE>.enabled` | true | — |
| `followUps.restock.fallbackDays` | 15 | 1–365 |
| `followUps.restock.rhythmPercent` | 80 | 10–100 |
| `followUps.restock.minDays` | 3 | 1–60 |
| `followUps.winBack.days` | 60 | 7–365 |
| `followUps.quoteChase.days` | 3 | 1–60 |
| `followUps.quoteExpiring.daysBefore` | 2 | 0–30 |
| `followUps.firstOrder.days` | 3 | 0–60 |
| `followUps.dealStalled.days` | 14 | 1–180 |
| `followUps.fallbackAdminId` | null | active ADMIN id |

**Rollout limits**
- FIRST_ORDER delivery within the last 30 days.
- QUOTE_DECLINED decline within the last 30 days.
- PAYMENT_OVERDUE due date within the last 60 days.
- First overdue pass marks follow-ups overdue by more than 7 days as notified without sending.

## Review Focus

1. **A customer with orders delivered on the same day** (zero-day gaps) must not drive the rhythm to 0. `minDays` floors it. → Task 2, `RestockRhythmTest.sameDayDeliveriesFloorAtMinDays`.
2. **The fallback admin being deactivated or demoted after it was set** must cause a skip, not an insert with an invalid assignee. → Task 2, `AutoFollowUpsIT.inactiveFallbackAdminSkips`.
3. **A rep reassigned away from the customer while an auto follow-up is open.** The existing `ReassignmentListener` must move auto rows like manual ones. → Task 3, `RestockRuleIT.reassignmentMovesAutoFollowUp`.
4. **A quote revised and re-sent** must close the old revision's chase and start a new one for the new revision. → Task 4, `QuoteRulesIT.reviseClosesOldChaseAndNewRevisionIsChasedAgain`.
5. **An order with a partial payment that later goes overdue** must produce a follow-up titled with the remaining balance, not the total. → Task 4, `PaymentOverdueRuleIT.titleUsesBalance`.

---

### Task 1: Schema, settings keys and FollowUp fields

**Files:**
- Create: `V24__auto_follow_ups.sql`
- Modify: `settings/SettingKey.java`, `settings/Settings.java`, `followups/FollowUp.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/settings/SettingsIT.java` (extend, or create beside existing settings tests)

**Interfaces:**
- Produces:
  - Columns on `follow_up`:
    - `rule text null` (CHECK in the 8 rule names)
    - `auto_key text null unique`
    - `closed_reason text null`
    - `overdue_notified_at timestamptz null`
    - `created_by` becomes nullable; auto rows store null.
  - Table `auto_follow_up_run(day date primary key)`.
  - Index `(rule) where done_at is null and rule is not null`.
  - `public enum FollowUpRule { RESTOCK, WIN_BACK, QUOTE_CHASE, QUOTE_EXPIRING, QUOTE_DECLINED, FIRST_ORDER, PAYMENT_OVERDUE, DEAL_STALLED }` in `followups`.
  - `FollowUp` fields: `FollowUpRule rule`, `String autoKey`, `String closedReason`, `Instant overdueNotifiedAt`; `createdBy` becomes `Long`.
  - `SettingKey` entries:
    - one `FOLLOW_UPS_AUTO_<RULE>_ENABLED` per rule (key `followUps.auto.<RULE>.enabled`), validated by a new `bool()` validator
    - the eight numeric keys above, using `number(min,max)` with whole-number validation
    - `FOLLOW_UPS_FALLBACK_ADMIN_ID`, using a new validator that accepts null or a positive integer
  - Defaults are returned by `Settings.all()` when a key is unset.
  - New accessors `public boolean Settings.bool(SettingKey)`, `public int Settings.integer(SettingKey)`, `public Long Settings.longOrNull(SettingKey)`.

- [ ] **Step 1: Failing tests**
  - `followUpSettingsHaveDefaults`: GET `/api/settings` contains `followUps.restock.fallbackDays = 15`, `followUps.restock.rhythmPercent = 80`, `followUps.auto.RESTOCK.enabled = true` and `followUps.fallbackAdminId = null`.
  - `followUpSettingsValidate`: PUT `followUps.winBack.days = 3` → 400 `VALIDATION_FAILED`; PUT `= 90` → 200 and readback 90; PUT `followUps.auto.RESTOCK.enabled = "yes"` → 400.
- [ ] **Step 2: Run** `--tests '*SettingsIT'` and expect FAIL.
- [ ] **Step 3: Implement.** Fallback-admin activity is checked at use (Task 2), not at save.
- [ ] **Step 4: Run** `--tests '*SettingsIT' --tests '*FollowUp*'` and expect PASS. Regenerate the OpenAPI snapshot if the settings schema is typed.
- [ ] **Step 5: Commit** `feat(follow-ups): auto follow-up schema and settings`.

### Task 2: Engine (`AutoFollowUps`, `AutoRule`, `RestockRhythm`)

**Files:**
- Create in `followups/`:
  - `AutoRule.java`
  - `Candidate.java`
  - `AutoFollowUps.java`
  - `RestockRhythm.java`
  - `RuleSettings.java` (a record snapshot of the settings)
- Test:
  - `src/test/java/.../followups/RestockRhythmTest.java`
  - `src/test/java/.../followups/AutoFollowUpsIT.java`

**Interfaces:**
- Consumes: Task 1.
- Produces:
  - `public interface AutoRule`:
    - `FollowUpRule rule();`
    - `List<Candidate> find(LocalDate today, RuleSettings s);`
    - `Map<Long, String> toClose(LocalDate today, RuleSettings s);` returns open auto follow-up ids of this rule that should close, with the reason. It is the safety-net pass.
  - `public record Candidate(long customerId, Long orderId, Long repHint, String key, String title, String note)`
  - `public record RuleSettings(Set<FollowUpRule> enabled, int restockFallbackDays, int restockRhythmPercent, int restockMinDays, int winBackDays, int quoteChaseDays, int quoteExpiringDaysBefore, int firstOrderDays, int dealStalledDays, Long fallbackAdminId)` with `static RuleSettings from(Settings)`.
  - `final class RestockRhythm { static int dueAfterDays(List<LocalDate> deliveriesNewestFirst, RuleSettings s) }`
    - Uses at most the 6 newest dates (5 gaps).
    - With fewer than 2 dates, returns `fallbackDays`.
    - Otherwise returns `max(minDays, avgGapDays × percent / 100, HALF_UP)`.
  - `AutoFollowUps`:
    - `@Scheduled run()` → `int run(LocalDate today)` returns the number of rows created; it returns 0 when the day's marker already exists.
    - `public int close(String autoKeyPrefixOrExact, boolean prefix, String reason)` closes open rows with `done_at = now()`, `done_by = null`, `closed_reason = reason` and returns the count.
  - Run order:
    1. Insert the marker in its own transaction.
    2. For each enabled rule, in its own `TransactionTemplate`: `find` → skip archived customers → resolve assignee → `insert … on conflict (auto_key) do nothing` with `due_date = today`, `created_by = null`, plus an audit `FOLLOW_UP_CREATED` with detail `"auto: " + rule`.
    3. Then run `toClose` and close.
    4. Catch and log at ERROR per rule.
  - Assignee: the customer's `assigned_rep_id` if it is an active user; else `repHint` if active; else `fallbackAdminId` if it is an active user holding the ADMIN role; else skip and log at INFO. Resolve this through the existing `Users`/identity API used by `FollowUpService`.

- [ ] **Step 1: Failing tests**
  - `RestockRhythmTest`:
    - `fewerThanTwoDeliveriesUsesFallback` → 15
    - `averageGapTimesPercent` (gaps 20, 20, 20 → 16)
    - `roundsHalfUp` (gaps 10, 11 → avg 10.5 × 0.8 = 8.4 → 8; gaps 13, 12 → 10)
    - `onlyFiveNewestGapsCount`
    - `sameDayDeliveriesFloorAtMinDays` (gaps 0, 0 → 3)
  - `AutoFollowUpsIT`. Register a `@TestConfiguration` fake `AutoRule` whose candidates and closes the test controls. Assert:
    - `createsOneRowPerCandidateDueToday`
    - `sameKeyIsNeverCreatedTwice`, including after a rep marks the row done
    - `secondRunSameDayDoesNothing` (marker)
    - `assigneeFallsBackRepThenHintThenAdmin`
    - `noAssigneeSkips`
    - `inactiveFallbackAdminSkips`
    - `archivedCustomerSkipped`
    - `disabledRuleCreatesNothing`
    - `failingRuleDoesNotStopOthers` (a second fake rule throws)
    - `closeSetsReasonAndLeavesDoneRowsAlone`
- [ ] **Step 2: Run** and expect FAIL. **Step 3: Implement.** **Step 4: Run** `--tests '*RestockRhythmTest' --tests '*AutoFollowUpsIT' --tests '*FollowUp*'` and expect PASS.
- [ ] **Step 5: Commit** `feat(follow-ups): auto follow-up engine and restock rhythm`.

### Task 3: Customer rules (RESTOCK, WIN_BACK, FIRST_ORDER) and the order-confirmed close

**Files:**
- Create in `followups/rules/`: `RestockRule.java`, `WinBackRule.java`, `FirstOrderRule.java`
- Create: `followups/AutoCloseListeners.java`
- Test: `followups/rules/RestockRuleIT.java`, `WinBackRuleIT.java`, `FirstOrderRuleIT.java`

**Interfaces:**
- Consumes:
  - Task 2's `AutoRule`, `Candidate`, `RestockRhythm`, `AutoFollowUps.close`.
  - Event `sales.OrderConfirmed(long orderId, long customerId)`.
- Rule definitions:
  - "Delivered" means `delivery_order.status = 'DELIVERED'`, dated `delivered_at` in Manila.
  - RESTOCK:
    - Uses each customer's last delivered order `d`, with that customer's delivered dates feeding `RestockRhythm`.
    - Matches when `d.date + days ≤ today` and the customer has no `sales_order` with `confirmed_at > d.delivered_at`. Use the order's existing confirm timestamp column or the CONFIRMED audit.
    - Key `RESTOCK:order:{d.orderId}`. `repHint` is the order's `rep_id`. `n` = today − d.date.
  - WIN_BACK:
    - Matches when the last delivered order is ≥ `winBackDays` ago and the customer has no order in `CONFIRMED`/`RELEASED`.
    - Key `WIN_BACK:customer:{cid}:{lastOrderId}`. `orderId` is null and `repHint` is the last order's rep.
  - FIRST_ORDER:
    - Matches when the customer's first delivered order date is ≤ today − `firstOrderDays` and ≥ today − 30.
    - Key `FIRST_ORDER:customer:{cid}`. `orderId` is that order.
  - `toClose` for RESTOCK and WIN_BACK: open rows of the rule whose customer has an order confirmed after the row's `created_at`. Reason `{customer} ordered {SO}`. FIRST_ORDER never closes.
  - `AutoCloseListeners.onOrderConfirmed(OrderConfirmed e)` (`@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW`) closes that customer's open RESTOCK and WIN_BACK rows with the same reason.

- [ ] **Step 1: Failing tests** (fixed clock; seed delivered orders the way `DeliveryOrdersIT`/`SalesFixture` do):
  - `RestockRuleIT`:
    - `firesAtLearnedRhythm` (deliveries 40, 20, 0 days before → fires on day 16 after the last, not 15)
    - `newCustomerUsesFallback`
    - `titleAndAssignee`: exact title text, assignee = customer rep
    - `notWhenCustomerAlreadyReordered`
    - `confirmedOrderClosesItImmediately` (`closed_reason` = `Acme ordered SO-…`)
    - `reassignmentMovesAutoFollowUp`
  - `WinBackRuleIT`:
    - `firesAfterSixtyDays`
    - `notWithOpenOrder`
    - `newCycleAfterNextOrder` (a different key)
  - `FirstOrderRuleIT`:
    - `firesThreeDaysAfterFirstDelivery`
    - `notForSecondOrder`
    - `rolloutIgnoresFirstDeliveriesOlderThanThirtyDays`
- [ ] **Step 2: Run** and expect FAIL. **Step 3: Implement.** **Step 4: Run** `--tests '*RuleIT' --tests '*AutoFollowUpsIT'` and expect PASS.
- [ ] **Step 5: Commit** `feat(follow-ups): restock, win-back and first-order rules`.

### Task 4: Order rules (QUOTE_CHASE, QUOTE_EXPIRING, QUOTE_DECLINED, PAYMENT_OVERDUE) and their closes

**Files:**
- Create in `followups/rules/`: `QuoteChaseRule.java`, `QuoteExpiringRule.java`, `QuoteDeclinedRule.java`, `PaymentOverdueRule.java`
- Create: `sales/QuoteClosed.java`
- Modify:
  - `sales/OrderService.java` and `sales/PortalOrderService.java`, to publish `QuoteClosed` on accept, decline (portal and staff), revise, confirm-from-QUOTED and cancel
  - `followups/AutoCloseListeners.java`
- Test: `followups/rules/QuoteRulesIT.java`, `followups/rules/PaymentOverdueRuleIT.java`

**Interfaces:**
- Produces: `public record QuoteClosed(long orderId, String outcome)`, where `outcome` ∈ `accepted`, `declined`, `revised`, `confirmed`, `cancelled`.
- Consumes: `PaymentVerified(orderId, …)` and `Balances.status` / order `balance()` for overdue.
- Rule definitions:
  - QUOTE_CHASE:
    - Matches when the order is `QUOTED` and the latest `audit_event` with action `QUOTE_SENT` on (`ORDER`, id) is ≥ `quoteChaseDays` ago.
    - Key `QUOTE_CHASE:order:{id}:{revision}`. The title total is `o.total`.
  - QUOTE_EXPIRING:
    - Matches when the order is `QUOTED` and `quote_valid_until − daysBefore ≤ today ≤ quote_valid_until`.
    - Key `QUOTE_EXPIRING:order:{id}:{revision}`.
  - QUOTE_DECLINED:
    - Matches when a `QUOTE_DECLINED` audit on the order is dated before today and within 30 days.
    - Key `QUOTE_DECLINED:order:{id}:{revision}`. Use the revision current at the decline. If that's not recoverable, use the order's current revision.
  - PAYMENT_OVERDUE:
    - Matches when the order is not `CANCELLED`, `total − settled_amount > 0`, `due_date < today` and `due_date ≥ today − 60`.
    - Key `PAYMENT_OVERDUE:order:{id}`. `n` = today − due_date. The balance is `total − settled_amount`.
  - `toClose`:
    - Quote rules close when the order is no longer `QUOTED` at the key's revision. Reason `Quote {SO} {outcome-or-"closed"}`.
    - Payment closes when the balance is ≤ 0 (`Paid in full`) or the order is cancelled (`Order cancelled`).
  - Listeners:
    - `onQuoteClosed` closes prefix `QUOTE_CHASE:order:{id}:` and `QUOTE_EXPIRING:order:{id}:` with reason `Quote {SO} {outcome}`.
    - `onPaymentVerified` closes `PAYMENT_OVERDUE:order:{id}` if the balance is ≤ 0.
    - `onQuoteClosed` with outcome `cancelled` also closes the payment row with reason `Order cancelled`.

- [ ] **Step 1: Failing tests:**
  - `QuoteRulesIT`:
    - `chaseFiresThreeDaysAfterSend`
    - `expiringFiresTwoDaysBeforeValidUntil`
    - `declinedFiresNextDay`
    - `declinedRolloutLimit`
    - `acceptClosesChaseAndExpiring`
    - `reviseClosesOldChaseAndNewRevisionIsChasedAgain`
  - `PaymentOverdueRuleIT`:
    - `firesWhenOverdue`
    - `titleUsesBalance` (total 10,000, verified 4,000 → `Collect ₱6,000.00 on …`)
    - `paymentInFullClosesIt`
    - `rolloutLimitSixtyDays`
- [ ] **Step 2: Run** and expect FAIL. **Step 3: Implement.** **Step 4: Run** `--tests '*RuleIT' --tests '*RulesIT' --tests '*OrdersIT' --tests '*PortalOrdersIT'` and expect PASS.
- [ ] **Step 5: Commit** `feat(follow-ups): quote and payment rules`.

### Task 5: DEAL_STALLED and the deal-stage close

**Files:**
- Create: `followups/rules/DealStalledRule.java`, `deals/DealStageChanged.java`
- Modify: `deals/DealService.java` (publish the event wherever `DEAL_STAGE_CHANGED` is audited, around line 124, and on won/lost), `followups/AutoCloseListeners.java`
- Test: `followups/rules/DealStalledRuleIT.java`

**Interfaces:**
- Produces: `public record DealStageChanged(long dealId, DealStage stage)`.
- Rule definition:
  - Matches when the deal stage is in `DISCOVERY`/`PROPOSAL`/`NEGOTIATION` and `lastChange` = the latest `DEAL_STAGE_CHANGED` audit on (`DEAL`, id), else `created_at`, is ≥ `dealStalledDays` ago.
  - Key `DEAL_STALLED:deal:{id}:{lastChange date ISO}`. `orderId` is null and `repHint` is the deal's `rep_id`. The note is the deal title.
  - `toClose`: rows whose key's date no longer equals the deal's last change date, or whose deal is WON/LOST. Reason `Deal moved to {Stage}`.
  - The listener closes prefix `DEAL_STALLED:deal:{id}:`.

- [ ] **Step 1: Failing tests:**
  - `firesAfterFourteenDaysInStage`
  - `noteEditDoesNotReset` (an update with no stage change keeps it firing)
  - `stageChangeClosesItAndRestartsTheClock`
  - `wonDealNotChased`
- [ ] **Step 2–4:** run and expect FAIL, implement, run `--tests '*DealStalledRuleIT' --tests '*Deal*IT'` and expect PASS.
- [ ] **Step 5: Commit** `feat(follow-ups): stalled deal rule`.

### Task 6: Overdue notification

**Files:**
- Modify: `notifications/NotificationType.java` (+`FOLLOW_UP_OVERDUE`), `followups/FollowUpReminder.java`, `followups/FollowUpQueries.java`
- Test: `src/test/java/.../followups/FollowUpReminderIT.java` (extend)

**Interfaces:**
- `FollowUpReminder.sendFor(today)`, inside the same marker transaction after the summary:
  - For each open row with `due_date < today` and `overdue_notified_at is null`: if `due_date ≥ today − 7`, send `notifications.toUser(assignee, FOLLOW_UP_OVERDUE, "CUSTOMER", customerId, "Overdue: " + title, "Due " + d MMM + " · " + customerName)`.
  - Then set `overdue_notified_at = now()` for all of them, including rows that were not sent (the rollout guard).

- [ ] **Step 1: Failing tests:**
  - `overdueSentOnceTheDayAfterDue`
  - `notSentAgainNextDay`
  - `manualAndAutoBothNotified`
  - `oldOverdueMarkedWithoutSending` (due 10 days ago)
  - `summaryStillSent`
- [ ] **Step 2–4:** run and expect FAIL, implement, run `--tests '*FollowUpReminder*'` and expect PASS.
- [ ] **Step 5: Commit** `feat(follow-ups): one-time overdue notification`.

### Task 7: API — rule, closedReason, source filter

**Files:**
- Modify: `followups/FollowUpResponse.java` (+`FollowUpRule rule`, `String closedReason`), `followups/FollowUpController.java` and `FollowUpQueries`/`FollowUpService` (`source` param `all|manual|auto`, default `all`; unknown → 400)
- Test: existing follow-ups IT class (extend), `OpenApiSnapshotTest`

- [ ] **Step 1: Failing tests:**
  - `sourceAutoListsOnlyAutoRows`
  - `sourceManual`
  - `unknownSource400`
  - `responseCarriesRuleAndClosedReason`
- [ ] **Step 2–4:** run and expect FAIL, implement, regenerate the snapshot, then run the full backend suite once (`timeout 4000`). Expect only the baseline failures.
- [ ] **Step 5:** run `graphify update .` and commit `feat(follow-ups): expose rule and source filter`.

### Task 8: Frontend

**Files (`crm-dashboard-ui`):**
- Create: `src/features/followups/ruleLabels.ts`
- Modify:
  - `src/features/followups/FollowUpRow.tsx`: Auto tag with a tooltip `RULE_LABEL[rule]`; Done rows with `closedReason` show `Closed: {closedReason}`
  - `src/features/followups/FollowUpsPage.tsx`: a Source filter (All/Manual/Auto) passed as `source`
  - `src/features/followups/api.ts`
  - `src/features/admin/SettingsPage.tsx`: a "Follow-up automation" section with one row per rule (toggle plus day field), the restock percent and minimum days, and a fallback-admin select of active admins. It saves through the existing settings PUT.
  - `src/app/shell/NotificationBell.tsx`: `notificationLink` already maps `CUSTOMER`; just confirm `FOLLOW_UP_OVERDUE` routes there.
- Tests: `FollowUpRow`/`FollowUpsPage.test.tsx`, `SettingsPage.test.tsx`, `NotificationBell.test.tsx`

**Interfaces:**
- `RULE_LABEL: Record<FollowUpRule, string>` = `{RESTOCK: "Restock check", WIN_BACK: "Win-back", QUOTE_CHASE: "Quote chase", QUOTE_EXPIRING: "Quote expiring", QUOTE_DECLINED: "Declined quote", FIRST_ORDER: "First-order check-in", PAYMENT_OVERDUE: "Payment overdue", DEAL_STALLED: "Stalled deal"}`.

- [ ] **Step 1:** run `npm run gen:api`, then write failing tests:
  - Auto tag shown only when `rule` is set, with the right tooltip
  - Done row shows `Closed: Acme ordered SO-0060`
  - Source filter sends `source=auto`
  - Settings section renders the defaults, rejects win-back days of 3 with an inline error, and saves the changed keys only
  - A `FOLLOW_UP_OVERDUE` notification links to `/customers/{id}`
- [ ] **Step 2–4:** `npm test` should FAIL, then implement, then `npm test`, `npm run build` and `npm run lint` (0 errors).
- [ ] **Step 5:** run `graphify update .` and commit `feat(follow-ups): auto tag, source filter, automation settings`.
