# Automatic follow-ups — design (2026-11-17)

Spans both repos: `crm/crm` (backend) and `crm-dashboard-ui` (frontend). Builds on the follow-ups design
(`2026-11-05-follow-ups-design.md`), whose model, scoping, buckets and morning bell stay as they are.

## Goal

The system creates the follow-ups that lead to the next sale, so reps don't have to remember them. It
closes them once their reason is gone. A follow-up that goes overdue also gets its own one-time
notification.

## Decisions (from the user)

| Question | Decision |
|---|---|
| Rules | All seven: RESTOCK, WIN_BACK, QUOTE_CHASE (+ QUOTE_EXPIRING), QUOTE_DECLINED, FIRST_ORDER, PAYMENT_OVERDUE, DEAL_STALLED |
| Restock timing | Learned from each customer's reorder rhythm, with a fixed fallback for new customers |
| No rep on the customer | Customer's rep → order's rep (order-based rules) → a fallback admin chosen in Settings → skip |
| Mechanism | One daily scan creates follow-ups; event listeners plus the scan close them |
| Done by a rep | Never recreated for the same key, even if the reason still holds |
| Overdue | One `FOLLOW_UP_OVERDUE` notification per follow-up, the morning after its due date, for manual and auto follow-ups alike |

## 1. Data — `V24__auto_follow_ups.sql`

`follow_up` gains:
- `rule text null`, with CHECK in (`RESTOCK`, `WIN_BACK`, `QUOTE_CHASE`, `QUOTE_EXPIRING`, `QUOTE_DECLINED`,
  `FIRST_ORDER`, `PAYMENT_OVERDUE`, `DEAL_STALLED`). It is null for manual follow-ups.
- `auto_key text null unique`. It is set exactly when `rule` is set.
- `closed_reason text null`. It is set when the system closes the follow-up.
- `overdue_notified_at timestamptz null`. It records the one-time overdue notification.

Index: `(rule) where done_at is null and rule is not null`.

When the system closes a follow-up it sets `done_at = now()` and `done_by = null`. A null `done_by`
together with a non-null `closed_reason` means "closed by the system".

Assignee rule change: manual follow-ups still require an active SALES user. Auto follow-ups also accept
the fallback admin, who may be ADMIN-only.

## 2. Settings (`SettingKey`, admin-editable, validated like existing keys)

| Key | Default | Range |
|---|---|---|
| `followUps.auto.<RULE>.enabled` (one per rule) | `true` | boolean |
| `followUps.restock.fallbackDays` | 15 | 1–365 |
| `followUps.restock.rhythmPercent` | 80 | 10–100 |
| `followUps.restock.minDays` | 3 | 1–60 |
| `followUps.winBack.days` | 60 | 7–365 |
| `followUps.quoteChase.days` | 3 | 1–60 |
| `followUps.quoteExpiring.daysBefore` | 2 | 0–30 |
| `followUps.firstOrder.days` | 3 | 0–60 |
| `followUps.dealStalled.days` | 14 | 1–180 |
| `followUps.fallbackAdminId` | null | an active ADMIN user id, or null |

## 3. Daily scan — `followups/AutoFollowUps`

`@Scheduled(cron = "0 45 7 * * *", zone = "Asia/Manila")`. It runs under the same once-per-day marker
pattern as `FollowUpReminder` (marker table `auto_follow_up_run(day)`, insert … on conflict do nothing),
so one instance runs per day. `run(LocalDate today)` is callable from tests.

For each enabled rule, in the order listed below:
1. `rule.find(today, settings)` returns `List<Candidate(customerId, orderId?, repHint?, key, title, note?)>`.
2. Skip a candidate whose customer has `archived_at` set.
3. Resolve the assignee: the customer's `assigned_rep_id` (if it is an active user), else `repHint` (the
   order's rep, for order-based rules, if active), else `fallbackAdminId` (if active ADMIN), else skip and
   log at INFO.
4. `insert … on conflict (auto_key) do nothing`. Set `due_date = today`, `created_by` = the system user
   (the id the existing `SYSTEM` actor uses for audit), and write an audit `FOLLOW_UP_CREATED` with
   detail "auto: <RULE>".

Then the safety-net close pass: every open auto follow-up whose close condition (§4) holds is closed.

One rule throwing an error must not stop the others. Each rule runs in its own transaction and its
failure is logged at ERROR.

### Restock rhythm

`RestockRhythm.dueAfter(List<LocalDate> deliveries, settings) → int days`
- `deliveries` holds the customer's delivered-order dates, newest first, at most 6 (5 gaps).
- With fewer than 2 previous deliveries (fewer than 1 gap), return `fallbackDays`.
- Otherwise: `max(minDays, round_half_up(avg(gaps) × rhythmPercent / 100))`.

### 4. Rules (each is one class in `followups/rules/`, implementing `AutoRule`)

All dates are Manila calendar dates. "Delivered" means a delivery order with `delivered_at` set, or the
order's status is FULFILLED (use whichever the order lifecycle stores).

| Rule | Matches | Key | Title | Closes when |
|---|---|---|---|---|
| RESTOCK | customer's last delivered order `d`: `d.date + RestockRhythm ≤ today`, and no order of the customer confirmed after `d` | `RESTOCK:order:{d.id}` | `Check if {customer} still has stock — last order {SO}, {n} days ago` | the customer has an order confirmed after `d` |
| WIN_BACK | last delivered order ≥ `winBack.days` ago, and the customer has no order in CONFIRMED/RELEASED | `WIN_BACK:customer:{cid}:{lastOrderId}` | `{customer} hasn't ordered in {n} days — check in` | same as RESTOCK |
| QUOTE_CHASE | order QUOTED, latest `QUOTE_SENT` audit for this revision ≥ `quoteChase.days` ago | `QUOTE_CHASE:order:{id}:{revision}` | `Follow up on quote {SO} (₱{total})` | the order is no longer QUOTED at that revision |
| QUOTE_EXPIRING | order QUOTED, `quote_valid_until − daysBefore ≤ today ≤ quote_valid_until` | `QUOTE_EXPIRING:order:{id}:{revision}` | `Quote {SO} expires {date} — confirm or extend` | same as QUOTE_CHASE |
| QUOTE_DECLINED | a `QUOTE_DECLINED` audit on the order dated before today | `QUOTE_DECLINED:order:{id}:{revision}` | `Ask {customer} why they declined {SO}` | never (rep closes it) |
| FIRST_ORDER | the customer's first-ever delivered order was ≥ `firstOrder.days` ago, and that delivery is within the last 30 days (so existing customers aren't flooded on rollout) | `FIRST_ORDER:customer:{cid}` | `How did {customer} find their first order ({SO})?` | never |
| PAYMENT_OVERDUE | the order's derived payment status is OVERDUE (`Balances.status`) and the order is not CANCELLED | `PAYMENT_OVERDUE:order:{id}` | `Collect ₱{balance} on {SO} ({n} days overdue)` | the balance is ≤ 0 or the order is CANCELLED |
| DEAL_STALLED | deal stage is in OPEN, and its last `DEAL_STAGE_CHANGED` audit (else `created_at`) is ≥ `dealStalled.days` ago | `DEAL_STALLED:deal:{id}:{lastStageChangeDate}` | `Move deal "{title}" forward ({stage}, {n} days in stage)` | the stage changes, or the deal is WON/LOST |

Order-based rules set `order_id` and pass the order's `rep_id` as `repHint`. DEAL_STALLED sets only
`customer_id` and passes the deal's `rep_id` as `repHint`. The note carries the deal title, because
follow-ups cannot point at a deal.

Rollout: the same 30-day "recent trigger" limit applies to QUOTE_DECLINED (decline audit within 30
days). It also applies to PAYMENT_OVERDUE, by limiting it to orders that went overdue no more than 60
days ago. This way the first run produces a manageable list.

### Closing on events (immediate)

Listeners in `followups` (`@TransactionalEventListener(AFTER_COMMIT)`, `REQUIRES_NEW`) close the
matching open auto follow-ups with a `closed_reason`:

| Event | Closes | Reason |
|---|---|---|
| `OrderConfirmed` | that customer's RESTOCK and WIN_BACK | `{customer} ordered {SO}` |
| quote accepted, declined, revised or order cancelled (publish `QuoteClosed(orderId)` from `sales` where no event exists) | QUOTE_CHASE and QUOTE_EXPIRING for that order | `Quote {SO} {accepted/declined/revised/cancelled}` |
| `PaymentVerified` (if the balance is now ≤ 0), order cancelled | PAYMENT_OVERDUE for that order | `Paid in full` / `Order cancelled` |
| deal stage changed / won / lost (publish `DealStageChanged(dealId)` from `deals` if missing) | DEAL_STALLED for that deal (key prefix `DEAL_STALLED:deal:{id}:`) | `Deal moved to {stage}` |

`sales` and `deals` never depend on `followups`. Only events flow in that direction.

## 5. Overdue notification

`FollowUpReminder` (08:00) gets a second pass in the same marker transaction. For each open follow-up
with `due_date < today` and `overdue_notified_at is null`, it sends one `FOLLOW_UP_OVERDUE` notification
to the assignee and sets `overdue_notified_at = now()`.
- Title: `Overdue: {title}`
- Body: `Due {d MMM} · {customer}`
- Entity: `CUSTOMER`, `{customerId}`

New `NotificationType.FOLLOW_UP_OVERDUE`; the frontend `notificationLink` maps it to the customer page.

Rollout guard: on the first run, follow-ups already overdue by more than 7 days are marked notified
without sending.

## 6. API

- `FollowUpResponse` gains `rule` (nullable enum) and `closedReason`.
- `GET /api/follow-ups` gains `source=all|manual|auto` (default `all`).
- `GET /api/settings/follow-ups` and `PUT /api/settings/follow-ups` (ADMIN) read and write all §2
  keys as one DTO `FollowUpAutomationSettings`, using the existing settings service and `SETTING_CHANGED`
  audit. If the existing generic settings endpoint already covers keyed reads and writes, reuse it
  instead and add no new route.
- OpenAPI snapshot refreshed. SecurityMatrixTest rows for any new route.

## 7. Frontend (`crm-dashboard-ui`)

- **Auto tag** on follow-up rows (page, customer/order rails, dashboard list). Its tooltip shows the rule
  label, from a `RULE_LABEL` map: Restock check, Win-back, Quote chase, Quote expiring, Declined quote,
  First-order check-in, Payment overdue, Stalled deal.
- In the Done tab, rows closed by the system show `Closed: {closedReason}`.
- Follow-ups page: a **Source** filter (All / Manual / Auto).
- **Settings → Follow-up automation** (ADMIN): one row per rule with a toggle and its day field(s), the
  restock rhythm % and minimum days, and a fallback-admin picker (active admins).
- `notificationLink` handles `FOLLOW_UP_OVERDUE` → `/customers/{id}`.
- Auto follow-ups can be edited and ticked like manual ones.

## 8. Testing

- **Unit:** `RestockRhythmTest`. Fewer than 2 deliveries → fallback; average × percent, rounded half-up;
  the `minDays` floor; only the 5 newest gaps are used.
- **Integration, one test class per rule (fixed clock):**
  - matches → exactly one follow-up with the expected title, assignee, order and due date
  - a second `run` the same or the next day creates nothing
  - non-matching cases (customer reordered, quote answered, paid, deal moved, archived customer, rule
    disabled)
  - the rollout limits
- **Assignee fallback:** customer rep → order rep → fallback admin → skip.
- **Event close:** each listener closes its own rule's open follow-ups and sets `closed_reason`. A
  follow-up a rep already ticked done is untouched. Its key is not recreated afterwards.
- **Failure isolation:** one rule throwing does not stop the others.
- **Overdue:** a follow-up due yesterday is notified once; it is not notified again the next day; manual
  follow-ups are included; the rollout guard applies.
- **Scan runs once per day** (marker), across two calls.
- SecurityMatrixTest and OpenAPI snapshot.
- **Vitest:** Auto tag and tooltip, closed reason in Done, Source filter, settings form validation and
  save, `FOLLOW_UP_OVERDUE` link.

## Out of scope

Re-chasing after a rep closes an auto follow-up, follow-ups linked to deals, per-product shelf life,
digests for customers with no owner, per-rep rule preferences, due times.
