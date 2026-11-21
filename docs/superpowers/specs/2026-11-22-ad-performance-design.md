# Ad performance (spend vs acquired-customer value) — design

## Goal

The owner runs Facebook ads and wants to know, per month, whether the money spent brought in customers worth more
than it cost. Admins record monthly spend per lead source; a cohort report compares each month's spend with the
lifetime value of the customers acquired from that source in that month.

## Decisions

- **Cohort view, not revenue view.** A customer is credited to the month they were acquired, and their value is their
  lifetime value to date. Revenue from old customers is not attributed to later spend.
- **Spend per source per month**, any source (Facebook, Trade show, …), entered manually by an admin. No campaign
  tagging, no Meta API sync.
- **Tagging is the existing source.** `customer.source_id` / `lead.source_id` → `lead_source` already exist; "Facebook"
  is seeded. Nothing new on customers or leads.
- **Cohort month** = month of `lead.created_at` for the lead the customer was converted from
  (`customer.converted_from_lead_id`), else month of `customer.created_at`. Months are computed in `Asia/Manila`.
- **Value** = the existing lifetime value: Σ `money_movement.cash_amount + withholding_amount` over the customer's
  orders (verified payments less paid refunds), as of now.

## Data

Migration `V27__ad_spend.sql` (insights module owns the table, like `sales_target`):

```sql
CREATE TABLE ad_spend (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  source_id bigint NOT NULL REFERENCES lead_source(id),
  month date NOT NULL CHECK (extract(day FROM month) = 1),
  amount numeric(19,2) NOT NULL CHECK (amount >= 0),
  version bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by bigint NOT NULL REFERENCES app_user(id),
  UNIQUE (source_id, month));
```

## Report rows

One row per (month, source) in the requested range that has spend, leads, or new customers, plus one total row per
month.

| Field | Definition |
|---|---|
| `spend` | `ad_spend.amount`, or null when no row exists |
| `leads` | leads with that `source_id` whose `created_at` falls in the month |
| `newCustomers` | customers with that current `source_id` whose cohort month is the month (archived included) |
| `cohortValue` | Σ lifetime value of those customers |
| `costPerCustomer` | `spend / newCustomers`; null if spend null or `newCustomers = 0` |
| `valuePerCustomer` | `cohortValue / newCustomers`; null if `newCustomers = 0` |
| `roas` | `cohortValue / spend`; null if spend null or 0 |

Customers or leads with no source are left out of source rows and of month totals. The month total sums the source rows
and recomputes the ratios from those sums. Cohort value for older months grows as customers reorder; this is intended.

## API (all `ADMIN` only)

- `GET /api/insights/ad-spend?month=YYYY-MM` → `[{sourceId, sourceName, amount|null}]` for every non-archived source,
  plus archived sources that have spend that month.
- `PUT /api/insights/ad-spend/{sourceId}/{month}` body `{amount}` → upsert; `amount` ≥ 0, required.
- `DELETE /api/insights/ad-spend/{sourceId}/{month}` → remove the entry (no-op if absent).
- `GET /api/insights/ad-performance?from=YYYY-MM&to=YYYY-MM` → `{months: [{month, total: Row, rows: [Row]}]}`;
  defaults to the last 6 months ending with the current month; `from > to` or a range over 24 months is a 400.

Computation is one native SQL query in `InsightsQueries`. Unknown `sourceId` on PUT is a 404.

## Frontend (`crm-dashboard-ui`)

New admin-only page **Ad performance** in `features/insights`, routed and linked next to Sales targets.

- **Spend grid:** `MonthPicker` + one amount input per source; saves on blur (PUT), clearing the field deletes.
- **Cohort table:** range picker (default last 6 months); month header rows with totals, source rows beneath.
  Money as ₱, ROAS as `2.4×`, nulls as `—`.

After the backend change: `gradlew test --tests '*OpenApiSnapshotTest' -DupdateSnapshot=true`, then `npm run gen:api`.

## Testing

Backend `AdPerformanceIT` (Testcontainers, `InsightsFixture`):
- customer converted in December from an October lead counts in October
- customer created directly counts in its creation month
- refunds reduce cohort value
- null spend → null `costPerCustomer`/`roas`; zero new customers → null per-customer fields
- spend upsert overwrites; delete removes
- SALES gets 403 on every endpoint

Frontend: one test that the page renders the table and saves a spend amount.

## Out of scope

Per-campaign tagging, Meta Ads API sync, charts, SALES-role visibility.
