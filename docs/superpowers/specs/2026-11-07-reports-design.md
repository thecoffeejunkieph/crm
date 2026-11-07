# Reports (F-172)

Date: 2026-11-07
Repos: `crm` (backend), `crm-dashboard-ui` (frontend)
Builds on: `2026-11-05-dashboards-design.md` (insights module, money and month rules)
Tracker: F-172 "Reports: sales by month, rep, customer, product" (F-172–175)

## Goal

Let the owner review the business over any period, and let reps review their own, with full lists instead of
the dashboard's top 10. The same reports will later get CSV/XLSX export from F-110; this item is screens only.

## Decisions

| Question | Decision |
|---|---|
| What is a sale? | Money collected: `cash_amount + withholding_amount` of payments with `status = VERIFIED`. Same rule as targets and commission. |
| Which date? | The Manila date of `payment.verified_at`. |
| Who gets credit? | `sales_order.rep_id` (the rep at confirm). Reassigning a customer later does not move credit. |
| Cancelled orders | Their verified payments still count. The money came in. |
| By product | Each payment is split across the order's lines pro rata, by `line_total / sales_order.total`. The shipping fee's share goes to one "Shipping" row. |
| Who can see | ADMIN: everything, optionally narrowed to one rep. SALES: only orders with `rep_id = me`, with no rep report and no cost or margin. Other roles: 403. |
| Export | None now. F-110 adds it. |
| Caching | None. Reports run on demand, and `payment (status, verified_at)` is already indexed. |

Out of scope: export (F-110), lead and deal funnels, invoiced or accrual views, forecasting, and saved or scheduled reports.

## API, in module `insights`

New `ReportController` and `ReportQueries`. They reuse `PAID`, `MANILA_DATE` and the money rounding in
`InsightsQueries` and `OrderTotals.money`. There are no new tables and no migration.

`GET /api/reports/sales?by=month|rep|customer|product&from=YYYY-MM-DD&to=YYYY-MM-DD[&repId=]`

**Range:**
- `from` and `to` are Manila dates, both inclusive. Each defaults to the first or last day of the current month.
- These return `400 REPORT_BAD_RANGE`:
  - `from` after `to`;
  - more than 24 months from `from` to `to` (`to` later than `from.plusMonths(24).minusDays(1)`);
  - a malformed date or an unknown `by`.

**Scope:**
- SALES is always filtered by `o.rep_id = currentUser`. SALES using `by=rep` or passing `repId` gets `403`.
- ADMIN with `repId` filters by `o.rep_id = repId`. `repId=0` means unassigned (`rep_id is null`).
- Roles other than ADMIN and SALES get `403`, both through `@PreAuthorize` and a SecurityConfig URL rule.

**Response:**

```
{ from, to, by,
  total: { collected, payments },          // over the whole filtered set
  rows: [ ... ] }                          // all rows, no paging
```

All reports share one row shape, `{ month, id, name, collected, payments, sharePct, margin, marginPct,
linesWithoutCost }`. Fields a report does not use are null. `id` is the rep, customer or product id, depending on
the report.

**Rows:**

| by | Row fields | Rows and order |
|---|---|---|
| `month` | `month` (`YYYY-MM`), `collected`, `payments` | Every month the range touches, zeros included, oldest first |
| `rep` | `repId` (null for Unassigned), `name`, `collected`, `payments`, `sharePct` | Every active SALES user (zeros included), plus any other rep and "Unassigned" with collected > 0, highest first, ties broken by name |
| `customer` | `customerId`, `name`, `collected`, `payments`, `sharePct` | Customers with collected > 0, highest first, ties broken by name |
| `product` | `productId` (null for Shipping), `name`, `collected`, `margin`, `marginPct`, `linesWithoutCost` | Products with allocated collected > 0 plus the Shipping row when it is > 0, highest first |

**Field rules:**
- `payments` is the count of distinct payments.
- `sharePct = collected / total.collected × 100`, rounded HALF_UP to 1 decimal place. It is null when the total is 0.

**Product allocation:**
- **Line amount:** the line's allocated amount is the sum over the order's in-range payments of `PAID × l.line_total / o.total`.
- **Shipping amount:** the Shipping row is the same sum with `o.shipping_fee` in place of `l.line_total`.
- **Zero totals:** orders with `o.total = 0` are skipped (`nullif`).
- **Margin:** the sum of `allocated × (l.line_total − l.unit_cost × l.qty) / l.line_total`, over lines with a non-null `unit_cost` and `line_total > 0`. `marginPct` is margin / allocated amount of those lines × 100, rounded to 1 decimal.
- **Lines without cost:** `linesWithoutCost` counts the product's distinct lines with a null `unit_cost`. Shipping has no margin.
- **Rounding:** each row is rounded HALF_UP to 2 decimal places, so product rows may differ from `total.collected` by a few centavos.
- **SALES users:** `margin`, `marginPct` and `linesWithoutCost` are null.

Add `REPORT_BAD_RANGE` (400) to `shared/ErrorCode`.

## Frontend, in `crm-dashboard-ui`

`src/features/reports/`, built from the `src/ui` kit, with types from `npm run gen:api`.

**Route and navigation:**
- The route is `/reports`, for ADMIN and SALES. WAREHOUSE is redirected to `/insights`.
- The sidebar gets "Insights → Reports", and the phone More page gets the same link.

**Top bar:**
- **Range presets:** This month (default), Last month, This quarter, This year and Custom. Custom shows two `<input type="date">`.
- **Tabs:** By month, By rep (ADMIN only), By customer, By product.
- **Rep select:** ADMIN only. It lists active reps and "Unassigned", and applies to every tab except By rep.
- **URL state:** `?by=&from=&to=&rep=`, so a report can be bookmarked and Back works.

**Body:**
- **Total strip:** collected and the payment count.
- **Table:** click a column header to sort, with a total row at the bottom. On phones the rows stack, as in the admin dashboard's reps table.
- **By month:** `AmountBars` (from `features/insights`) sits above the table.
- **Row links:**
  - a customer row opens the customer page;
  - a product row opens the product;
  - a rep row switches to By customer, filtered to that rep;
  - Shipping and Unassigned rows are not links.
- **By product (ADMIN):** shows margin and margin %, plus "N lines without cost" when that count is above 0. A note under the table says that product rows are rounded separately.
- **States:**
  - while loading: skeletons;
  - nothing in range: "No payments collected in this period";
  - a 400: the problem detail's message, shown at the range picker.

## Testing

**Backend:** `ReportsIT` (`@IntegrationTest`), with fixed data:
- **Payments that count:** a verified payment counts, while pending and rejected ones do not. A payment received in October and verified in November counts in November.
- **Months:**
  - zero months appear;
  - a range from December to February gives three months;
  - `from` and `to` are inclusive at the Manila day edges (23:30 Manila on `to` counts, 00:30 the day after does not).
- **Reps:**
  - when a customer is reassigned after confirm, the credit stays with the original rep;
  - an unassigned customer's payment goes in the Unassigned row;
  - an active rep with no payments shows a zero row;
  - `repId=0` returns only unassigned money.
- **Customers:** ordered by collected, then by name. `sharePct` is right.
- **Product split:** one order has two lines plus shipping and is paid in two payments. The rows are exact to the centavo and add up to collected, except for the documented rounding. A line with no cost is excluded from margin and counted.
- **SALES scope:** only own orders count, margin fields are null, `by=rep` returns 403 and `repId` returns 403.
- **Errors:** `from > to`, a range over 24 months, a bad date and an unknown `by` each return 400 `REPORT_BAD_RANGE`. WAREHOUSE gets 403.
- **Shared checks:** the SecurityMatrixTest row and the OpenAPI snapshot are refreshed.

**Frontend:**
- **Vitest:** the page renders each tab from fixture data. SALES does not see the By rep tab, the rep select or the margin columns. Sorting works, the presets compute the right dates, and the URL holds the state.
- **Playwright:** `reports.spec.ts`. Admin records and verifies a payment on a confirmed order, then sees it under the month, the customer and the product.
