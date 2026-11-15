# Follow-ups (F-141) — design

Status: approved in chat 2026-11-05. Tracker item F-141 (backlog after the roadmap).

## Goal

Reps stop forgetting follow-ups. A follow-up is a dated to-do on a customer ("call Acme back Friday"),
optionally about one order ("chase payment on SO-0042"). A rep opening the app sees what is overdue and
due today without hunting for it.

## Decisions (from the user)

- A follow-up belongs to a customer; it may also point at one order of that customer.
- A rep creates follow-ups for themselves on their own customers. An admin creates them for any rep and
  sees everyone's. Warehouse users have no access.
- Reminders: Today / Overdue / Upcoming lists, plus one bell notification at 08:00 Manila per assignee
  with something due.
- When a customer is reassigned, its open follow-ups held by the old rep move to the new rep.

## Data — Flyway `V11__follow_ups.sql`

`follow_up`
| column | type | rule |
|---|---|---|
| id | bigint identity | |
| customer_id | bigint not null → customer | |
| order_id | bigint null → sales_order | must belong to `customer_id` (checked in service) |
| assignee_id | bigint not null → app user | active SALES user at create/reassign |
| title | varchar(200) not null | trimmed, 1–200 chars |
| note | text null | ≤ 2000 chars |
| due_date | date not null | Manila calendar date, no time |
| done_at | timestamptz null | set by complete, cleared by reopen |
| done_by | bigint null | |
| created_by | bigint not null | |
| created_at, updated_at | timestamptz | |
| version | bigint | optimistic lock |

Indexes: `(assignee_id, due_date) where done_at is null`, `(customer_id)`, `(order_id)`.
No delete: a mistaken follow-up is marked done (archive-not-delete convention).

## Rules

- **Visibility:** SALES sees follow-ups where `assignee_id = me`. ADMIN sees all. WAREHOUSE gets 403 on
  every route (URL rule in `SecurityConfig`, row in `SecurityMatrixTest`).
- **Create:** SALES only on a customer visible to them, and `assignee_id` is forced to themselves (a
  supplied assignee is ignored). ADMIN may set `assigneeId`; default is the customer's `assigned_rep_id`;
  if the customer has no rep and none is given → 422 `FOLLOW_UP_NO_ASSIGNEE`.
- **Order check:** `orderId` on another customer → 422 `FOLLOW_UP_ORDER_MISMATCH`.
- **Assignee check:** not an active SALES user → 422 `FOLLOW_UP_NOT_A_REP`.
- **Edit / done / reopen:** the assignee or an admin; others get 404 (not visible). Edit takes `version`;
  stale → 409 `STALE_VERSION`. Only ADMIN may change `assigneeId`. Done on a done item, or reopen on an
  open one, is a no-op returning the current state.
- **Buckets** (today = Manila date at request time): overdue = open and `due_date < today`; today = open
  and `due_date = today`; upcoming = open and `due_date > today`; done = `done_at is not null`, newest first.
  Open buckets sort by `due_date`, then `id`.
- **Reassignment:** `Customers.assign` publishes `CustomerReassigned(customerId, fromRepId, toRepId)`
  (customers must not depend on follow-ups). Follow-ups handles it in the same transaction
  (`@EventListener`): open rows with `assignee_id = fromRepId` move to `toRepId`. When `toRepId` is null
  they stay with the old rep. Done rows never move. One audit entry per moved row.
- **Audit:** `FOLLOW_UP_CREATED`, `FOLLOW_UP_UPDATED`, `FOLLOW_UP_DONE`, `FOLLOW_UP_REOPENED`,
  `FOLLOW_UP_REASSIGNED`; entity type `FOLLOW_UP`.
- **Morning bell:** `@Scheduled(cron = "0 0 8 * * *", zone = "Asia/Manila")`. Takes
  `DistributedLock.tryLock("follow-ups-due:" + today, 23h)` so a day sends once across instances and
  restarts. For each assignee with open follow-ups due today or overdue: one `FOLLOW_UPS_DUE`
  notification, text "3 due today, 1 overdue" (omit a zero part), link `/follow-ups`. Nobody else gets one.

## API — `/api/follow-ups`

- `GET ?bucket=overdue|today|upcoming|done&customerId=&orderId=&assigneeId=&page=&size=` → page of
  `FollowUpResponse {id, customerId, customerName, orderId, orderNumber, assigneeId, assigneeName, title,
  note, dueDate, doneAt, version}`. `bucket` defaults to `today`; unknown bucket → 400. `assigneeId` is
  honoured for ADMIN only.
- `GET /counts?customerId=&orderId=` → `{overdue, today, upcoming}` for the caller's scope.
- `POST` `{customerId, orderId?, assigneeId?, title, note?, dueDate}` → 201. Honours `Idempotency-Key`.
- `PUT /{id}` `{title, note?, dueDate, assigneeId?, orderId?, version}` → 200.
- `POST /{id}/done`, `POST /{id}/reopen` → 200.
- OpenAPI snapshot refreshed; `npm run gen:api` in the UI.

## Screens (crm-dashboard-ui)

- **Follow-ups page** `/follow-ups`, nav item for ADMIN and SALES with a badge = overdue + today.
  Tabs Overdue / Today / Upcoming / Done. Row: done checkbox, title, customer link, order number link if
  any, due date (overdue in danger tone). Admin gets a rep filter. Empty state per tab.
- **Customer page rail:** "Follow-ups" section (like Contacts) listing open ones with an Add button.
- **Order page rail:** the same section filtered to the order; Add pre-selects the order.
- **Add/edit dialog:** title, due date (native `<input type="date">`, default tomorrow), note, optional
  order picker (this customer's orders); admin also gets an assignee picker defaulting to the customer's rep.
- **Sales dashboard:** a "Follow-ups" stat "2 overdue · 3 today" linking to `/follow-ups`
  (from `/counts`, not from the cached insights payload).
- **Bell:** `FOLLOW_UPS_DUE` links to `/follow-ups`.
- Ticking done is not optimistic: the row moves once the server answers (no undo toast); a 409 reloads the list with
  "Someone else changed this follow-up. Reloaded."

## Testing

- Integration: scoping per role (rep sees own only, warehouse 403, rep cannot create for another rep or on
  another rep's customer); order/customer mismatch; non-rep assignee; buckets around Manila midnight (fixed
  clock); version conflict; done/reopen idempotent; reassignment moves open rows only and only the old
  rep's; reassign to no rep leaves rows; morning job sends one per assignee, none when nothing is due, and
  only once per day with the lock held.
- `SecurityMatrixTest` rows for every route; OpenAPI snapshot.
- Vitest: page tabs and badge, dialog defaults and validation, customer/order rail sections, dashboard stat.
- e2e `follow-ups.spec.ts`: rep adds a follow-up due today on a customer, sees it under Today, marks it
  done, sees it under Done.

## Out of scope

Due times and per-task reminders, recurring follow-ups, follow-ups on leads (F-120 does not exist yet),
email reminders, comments on follow-ups.
