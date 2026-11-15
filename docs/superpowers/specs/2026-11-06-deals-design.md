# Deals / opportunities (F-130) — design

Status: approved in chat 2026-11-06. Tracker item F-130 (backlog after the roadmap). Builds on F-120 leads.

## Goal

Reps track big or slow sales — a café's monthly beans supply, a hotel chain trial — from first talk to a
confirmed order, with an estimated value and expected close date, so nobody loses track of a deal in progress
and the pipeline value is visible by stage.

## Decisions (from the user)

- A deal tracks a potential sale worth chasing over weeks, with value, stage and expected close date.
- A deal belongs to a customer only (convert a lead first).
- Quotes/orders link to a deal; the deal is Won when a linked order is confirmed. Lost is set by hand with a reason.
- Stages are a fixed list: Discovery → Proposal → Negotiation → Won / Lost.

## Approach

A new Spring Modulith module `deals` owns `deal` and `deal_order`. `sales` publishes `OrderConfirmed`; `deals`
listens in the same transaction. `sales` never depends on `deals`. `deals` depends on `customers` (visibility,
names, `CustomerReassigned`) and on a small public lookup in `sales`.

## Data — Flyway `V15__deals.sql`

`deal`
| column | type | rule |
|---|---|---|
| id | bigint identity | |
| customer_id | bigint not null → customer | visible to the creator, not archived |
| rep_id | bigint null → app_user | the customer's rep at create; follows reassignment |
| title | varchar(160) not null | trimmed, 1–160 |
| stage | varchar(20) not null | DISCOVERY, PROPOSAL, NEGOTIATION, WON, LOST; starts DISCOVERY |
| value | numeric(14,2) null | estimated ₱, ≥ 0 |
| expected_close | date null | |
| lost_reason | varchar(500) null | required when LOST, cleared on reopen |
| notes | text null | ≤ 4000 |
| won_at | timestamptz null | |
| won_order_id | bigint null → sales_order | the order whose confirmation won it |
| created_by | bigint not null → app_user | |
| created_at, updated_at | timestamptz | |
| version | bigint | optimistic lock |

Checks: `stage <> 'LOST' OR lost_reason IS NOT NULL`. Indexes: `(rep_id, stage)`, `(customer_id)`, `(stage)`.

`deal_order`
| column | type | rule |
|---|---|---|
| deal_id | bigint not null → deal | |
| order_id | bigint not null → sales_order | unique: an order is on at most one deal |
| linked_at | timestamptz not null | |
| linked_by | bigint not null → app_user | |

Primary key `(deal_id, order_id)`, unique index on `order_id`. No delete of deals: a dead deal is Lost. Unlink
deletes the `deal_order` row (it is a link, not a record).

## Rules

Visibility (`DealScopes.visibleTo`): ADMIN all; SALES `rep_id = me`; WAREHOUSE none (403 at the URL layer).
Create requires the customer to be visible to the caller (`Customers.salesProfileVisible`) and not archived; the
deal's rep is the customer's assigned rep (null if the customer is unassigned — visible to admin only).

Reassignment: on `CustomerReassigned(customerId, from, to)` every open (not WON/LOST) deal of that customer gets
`rep_id = to`, audited `DEAL_REASSIGNED`. Won/Lost deals keep their rep.

Stages:
- DISCOVERY, PROPOSAL, NEGOTIATION move freely among each other.
- LOST requires `lostReason` (1–500); a Lost deal can go back to any open stage (reason cleared). While Lost,
  only reopening is allowed (`DEAL_CLOSED` otherwise).
- WON is final: edits, stage changes, link and unlink answer 409 `DEAL_CLOSED`.
- Manual WON is allowed only when a linked order is already CONFIRMED, RELEASED or FULFILLED; `won_order_id` is
  the earliest confirmed one. Otherwise 422 `DEAL_WON_NEEDS_ORDER`.
- Stage changes and edits carry the version; a stale version is 409 `STALE_VERSION`.

Automatic Won:
- `sales` publishes `OrderConfirmed(long orderId, long customerId)` from `OrderService.markConfirmed`, the single
  point every confirm path goes through (staff confirm, portal quote acceptance, auto-confirm). A revision that
  returns a released order to CONFIRMED does not publish.
- `deals` listens with `@EventListener` (same transaction): if the order is linked to an open deal, the deal
  becomes WON with `won_at` now and `won_order_id` the order, audited `DEAL_WON` (actor as the confirmer). A
  Lost deal is not changed.
- Known limit: a deal stays Won if its winning order is later cancelled.

Linking orders:
- `POST /api/deals/{id}/orders {orderId}`: the order must belong to the deal's customer, be DRAFT or QUOTED, and
  not be on another deal — else 422 `DEAL_ORDER_MISMATCH`. Linking an order already on this deal is a no-op 200.
- Unlink is allowed while the deal is open; never for the order that won it.
- `sales` exposes `public Optional<OrderBrief> brief(long orderId)` and `List<OrderBrief> briefs(Collection<Long>)`
  with `record OrderBrief(long id, String number, long customerId, OrderStatus status, BigDecimal total)`.
- The order workspace shows the deal: `deals` exposes `GET /api/deals/by-order/{orderId}` → `{id, title}` or 204,
  scoped like the deal (a rep who cannot see the deal gets 204).

## API

| method + path | who | notes |
|---|---|---|
| GET `/api/deals?stage=&q=&customerId=&repId=&page=&size=` | ADMIN, SALES | no `stage` → open deals; `q` on title and customer name; `repId` admin only; sort `expected_close asc nulls last, id` |
| GET `/api/deals/summary` | ADMIN, SALES | per stage `{count, value}` within scope, plus open totals |
| GET `/api/deals/{id}` | ADMIN, SALES | includes linked orders (`OrderBrief`) |
| POST `/api/deals` | ADMIN, SALES | `{customerId, title, value, expectedClose, notes}` → 201 |
| PUT `/api/deals/{id}` | ADMIN, SALES | `{title, value, expectedClose, notes, version}`; customer cannot change |
| POST `/api/deals/{id}/stage` | ADMIN, SALES | `{stage, lostReason, version}` |
| POST `/api/deals/{id}/orders` | ADMIN, SALES | `{orderId}` |
| POST `/api/deals/{id}/orders/{orderId}/unlink` | ADMIN, SALES | |
| GET `/api/deals/by-order/{orderId}` | ADMIN, SALES | |

Error codes: `DEAL_CLOSED` 409, `DEAL_LOST_REASON` 422, `DEAL_ORDER_MISMATCH` 422, `DEAL_WON_NEEDS_ORDER` 422.
Audit: `DEAL_CREATED, DEAL_UPDATED, DEAL_STAGE_CHANGED, DEAL_WON, DEAL_ORDER_LINKED, DEAL_ORDER_UNLINKED,
DEAL_REASSIGNED`; entity type `"DEAL"`. All routes in SecurityMatrixTest; OpenAPI snapshot refreshed;
`/api/deals` and `/api/deals/*/orders` on the IdempotencyFilter paths.

## UI (crm-dashboard-ui)

- Nav: **Deals** in the Sell group after Leads, ADMIN and SALES.
- `/deals`: stage tabs with count and ₱ total (Open, Discovery, Proposal, Negotiation, Won, Lost), search, rep
  filter for admin, **New deal** (drawer: customer picker, title, value, expected close, notes). Rows: title,
  customer, value, expected close (overdue dates in danger tone for open deals), stage, rep.
- `/deals/:id`: details, notes, stage buttons (other open stages, "Mark lost" with a reason dialog, "Reopen" when
  Lost, "Mark won" only when a linked order is confirmed), Edit, linked orders (number, status, total, link to
  the order, Unlink while open), **New quote** (creates an order for the customer via the existing create
  endpoint, links it, opens the order workspace) and **Link order** (picker of the customer's DRAFT/QUOTED
  orders). Won: banner "Won with <order>"; Lost: the reason and Reopen.
- Customer page: a **Deals** rail section listing the customer's open deals with Add (hidden when archived).
- Order workspace: "Deal: <title>" link when the order is on a deal.

## Testing

- Backend ITs: scopes per role (warehouse 403, hidden 404); create on another rep's or an archived customer →
  404; rep = customer's rep; each stage rule incl. Lost reason, reopen, Won final; manual Won with and without a
  confirmed order; link rules (other customer, already on another deal, past QUOTED, idempotent re-link);
  unlink rules; auto-Won on staff confirm, portal acceptance and auto-confirm; no Won on revision; Lost deal not
  auto-won; reassignment moves open deals only; summary totals within scope; by-order lookup scoped.
- SecurityMatrixTest, ModularityTest (deals → customers, sales; sales does not depend on deals), OpenAPI snapshot.
- UI unit tests: list tabs and totals, deal drawer validation, stage actions, linked orders and Unlink, New quote
  flow, customer Deals section, order workspace deal link.
- e2e `deals.spec.ts`: admin creates a customer, a deal on it → New quote → add a line → confirm → deal shows Won.

## Out of scope

Drag-and-drop board, pipeline charts and forecasting (F-172), deals on leads, admin-managed stages,
probability/weighted value, multiple currencies, splitting a deal across reps, reopening a Won deal.
