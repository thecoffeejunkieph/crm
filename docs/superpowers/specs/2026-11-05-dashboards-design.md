# Redesign #4 — Dashboards

Date: 2026-11-05
Repos: `crm` (backend), `crm-dashboard-ui` (frontend)
Builds on: `2026-10-14-sales-lifecycle-design.md`, `2026-10-21-inventory-fulfilment-design.md`, `2026-11-02-pos-design.md`
Completes: tracker item P4-2 (the POS half shipped at `e7cf8c3` / `f25fe20`)

## Goal

One dashboard per role, on the new order model. The owner (ADMIN) sees the whole company. A sales rep sees only
what they sold, with a progress bar toward a monthly target set by the owner; hitting the target earns commission,
which the dashboard shows. The warehouse sees today's work. The legacy `DashboardService` /
`WarehouseDashboardService` were deleted in `a28dbab`; nothing is carried over except the Redis cache setup in
`shared/CacheConfig`.

## Decisions

| Question | Decision |
|---|---|
| When does a sale count toward a target? | When its payment is `VERIFIED`. |
| Which month? | The Manila date of `payment.verified_at`. A closed month does not change when a late payment is received-dated into it. |
| What amount? | `cash_amount + withholding_amount` (`Payment.amount()`), VAT-inclusive — matches the invoice total. |
| Who gets credit? | The customer's assigned rep **at confirm**, stored on the order. Reassigning the customer later does not move credit. |
| Customer with no rep | No one is credited; shows as "Unassigned" on the owner view. |
| POS sales | Same rule: customer's rep, not the cashier. |
| Target period | Calendar month, Manila time. |
| Commission | If collected ≥ target and target > 0: `collected × commission_pct / 100`, HALF_UP to 2 places. Otherwise 0. |
| Where set | Per rep, per month, by ADMIN, with "copy from last month". |
| Home page | `/` sends every staff role to `/insights`. Inbox stays in the nav. |

Out of scope: low-stock alerts (no reorder level exists), lead/deal funnels (backlog F-120/F-130), CSV export
and reports (F-172), commission payout records or month locking.

## Data model — `V10__insights.sql`

```sql
alter table sales_order add column credited_rep_id bigint null references app_user(id);
alter table order_line  add column unit_cost numeric(19,2) null;

create table sales_target (
  id              bigserial primary key,
  rep_id          bigint not null references app_user(id),
  month           date   not null check (extract(day from month) = 1),
  target_amount   numeric(19,2) not null check (target_amount >= 0),
  commission_pct  numeric(5,2)  not null check (commission_pct >= 0 and commission_pct <= 100),
  version         bigint not null default 0,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  updated_by      bigint not null references app_user(id),
  unique (rep_id, month)
);
create index on sales_order (credited_rep_id);
create index on payment (status, verified_at);
```

Backfill in the same migration, for orders in `CONFIRMED`, `RELEASED`, `FULFILLED`:
`credited_rep_id = customer.assigned_rep_id` (current value) and `order_line.unit_cost = product.cost` (current
value). Money columns use `numeric(19,2)` like the rest of the schema.

### Where the snapshots are taken

`OrderService.confirm` is the single path every confirm takes (manual confirm, `confirmAsSystem` from
`AutoConfirmListener`, and `PosService.checkout`). Inside it, after the checks pass:

- `order.creditedRepId = customer.assignedRepId`
- each line's `unitCost = product.cost`

Never changed afterwards — not by revisions, not by reassignment. A revision that returns the order to an earlier
state and re-confirms takes fresh snapshots (it passes through `confirm` again). `unit_cost` is never serialised
in `OrderResponse`, `LineResponse` or any POS/portal response.

`sales_target.rep_id` must be an active user with role `SALES` (checked in the service, `422 INSIGHTS_NOT_A_REP`).

## Figures

All amounts VAT-inclusive pesos, money HALF_UP to 2 places as in `OrderTotals.money`. "Month" is the requested
month in `Asia/Manila`; "today" is today in Manila regardless of the month picker.

| Figure | Definition |
|---|---|
| Collected | Σ `cash_amount + withholding_amount` of payments with `status = VERIFIED` and Manila date of `verified_at` in month. |
| Invoiced | Σ `invoice.total` with `status = ISSUED` and Manila date of `issued_at` in month. A voided invoice drops out; its replacement counts in its own issue month. |
| Outstanding | All-time Σ balance of `ISSUED` invoices not fully settled by verified payments (reuse `Balances`). |
| Overdue | The part of Outstanding past due date (reuse the existing overdue rule behind `PaymentStatus.OVERDUE`). |
| To verify | Count and Σ of payments with `status = PENDING`. |
| Gross margin (ADMIN) | Over lines of orders with an `ISSUED` invoice issued in month: Σ (line net − `unit_cost × qty`). Lines with null `unit_cost` are excluded and counted as `linesWithoutCost`. Margin % = margin / Σ line net of included lines. |
| Commission | See Decisions. Computed on read; nothing stored. A payment rejected after verification lowers it. |

Rep scoping: every sales figure for the rep view filters `sales_order.credited_rep_id = currentUser`.

## API — module `insights`

All `GET` take `?month=YYYY-MM` (default: current Manila month). Month after the current one, more than 24 months
back, or malformed → `400 INSIGHTS_BAD_MONTH`. Wrong role → 403 via the existing ProblemDetail/403 matrix.

### `GET /api/insights/sales` — role SALES, always the signed-in user (no `repId` parameter)

```
{ month, target: { amount, commissionPct } | null,
  collected, progressPct, remaining, targetHit, commission,
  invoiced, outstanding, overdue, toVerify: { count, amount },
  collectedByDay: [{ date, amount }],                 // every day of the month, zeros included
  needsAction: { openQuotes: [OrderRef], unpaidConfirmed: [OrderRef], overdueInvoices: [InvoiceRef] },  // 5 oldest each
  topCustomers: [{ customerId, name, collected }] }   // top 5 by collected in month
```

### `GET /api/insights/admin` — role ADMIN

```
{ month, collected, collectedPrevMonth, invoiced,
  margin: { amount, pct, linesWithoutCost },
  outstanding, overdue, toVerify: { count, amount },
  collectedByMonth: [{ month, amount }],               // 12 months ending at `month`
  reps: [{ repId | null, name, target | null, commissionPct | null, collected, progressPct, targetHit, commission }],
                                                       // every active SALES user, plus one "Unassigned" row if non-zero
  byChannel: [{ channel, collected }],                 // B2B, POS
  topProducts: [{ productId, name, revenue, margin }], // top 10 by revenue
  topCustomers: [{ customerId, name, collected }],     // top 10
  operations: { deliveriesPicking, deliveriesDispatched, transfersInTransit, adjustmentsPendingApproval } }
```

### `GET /api/insights/warehouse` — roles WAREHOUSE, ADMIN

Scoped to `Scope.warehouseIds` (ADMIN: all). No money fields. Ignores `month`.

```
{ today: { toPick, dispatched, deliveredToday },
  transfers: { toDispatch, incoming },                 // DRAFT from mine; DISPATCHED to mine
  queue: [DeliveryRef] }                               // 10 oldest PICKING/DISPATCHED
```

### Targets — role ADMIN

- `GET /api/insights/targets?month=YYYY-MM` → every active SALES user with their target row or nulls.
- `PUT /api/insights/targets/{repId}/{month}` body `{ targetAmount, commissionPct, version | null }` → upsert;
  stale `version` → 409 (existing optimistic-lock handling). Audited (`SALES_TARGET_SET`).
- `POST /api/insights/targets/copy?from=YYYY-MM&to=YYYY-MM` → copies rows into `to` only for reps with no row
  there; returns the number copied. Audited.

Error codes added to `shared/ErrorCode`: `INSIGHTS_BAD_MONTH` 400, `INSIGHTS_NOT_A_REP` 422.

### Caching

`@Cacheable` on the three dashboard reads, cache `insights`, key `role:userId:month:warehouseIds`, existing 60s
Redis TTL; cache errors are misses (`CacheConfig`). Saving or copying a target evicts all `insights` entries
(`allEntries = true`) so the bar moves at once. Payments and orders do not evict; up to 60s staleness is accepted.

## Frontend — `crm-dashboard-ui`

`src/features/insights/`, built from the `src/ui` kit and redesign tokens; charts with the installed
`react-apexcharts`. Types from `npm run gen:api`.

- **Routes:** `/insights` renders the page for the signed-in role (`SalesDashboard`, `AdminDashboard`,
  `WarehouseDashboard`). `/` redirects to `/insights` for all staff roles (was `/inbox`).
  `/admin/sales-targets` — ADMIN only.
- **Nav:** sidebar "Insights → Dashboard" (all staff roles) and "Admin → Sales targets"; phone More page gets both.
- **Month picker** on sales and admin pages; warehouse page has none.
- **Sales page:** target card on top (bar, "₱X to go" or "Target hit" badge, rate, commission; "No target set" when
  null, no bar), KPI row, collected-by-day bars, needs-action lists linking to orders, top customers.
- **Admin page:** KPI row (margin shows "N lines without cost" when > 0), 12-month bars with vs-last-month delta,
  reps table (stacked list on phones), channel split, top products / customers, operations strip linking to
  Deliveries, Transfers and Inbox.
- **Warehouse page:** three today counters, transfer counters, queue list linking to delivery orders.
- **Targets page:** month picker, table of reps with editable target ₱ (`MoneyInput`) and commission %, save per row,
  "Copy from last month" button. A 409 reloads the row and says someone else changed it.
- Skeletons while loading; an empty month shows "No sales recorded".

## Testing

Backend (`@IntegrationTest`, Testcontainers):

- `InsightsIT` with fixed data:
  - verified, pending and rejected payments; one received in October and verified in November counts in November
  - customer reassigned after confirm: credit stays with the original rep
  - voided + reissued invoice: invoiced counts the replacement only
  - line with null cost: excluded from margin, counted in `linesWithoutCost`
  - target missed (commission 0), target exactly hit (commission = collected × pct), no target (null, commission 0)
  - unassigned customer appears in the admin "Unassigned" row only
  - warehouse scoping: a WAREHOUSE user sees only their warehouses
  - `unit_cost` absent from `OrderResponse` JSON
- Confirm snapshot: manual, auto-confirm and POS checkout all set `credited_rep_id` and `unit_cost`.
- Targets: upsert, stale version 409, copy fills only empty rows, non-SALES rep 422, audit event written, cache
  evicted (dashboard reflects the new target without waiting 60s).
- Access: SALES → admin 403; WAREHOUSE → sales 403; bad month 400. Added to the 403 matrix and OpenAPI snapshot.
- Migration backfill: an order confirmed before V10 gets the customer's current rep and the product's current cost.

Frontend: Vitest for the target card (no target / below / exactly hit / over), each role page rendering from
fixture data, the targets page save and 409 path, `/` redirect. Playwright smoke: admin sets a rep's target,
a payment for that rep is verified, the rep's dashboard shows the bar moved and commission once hit.
