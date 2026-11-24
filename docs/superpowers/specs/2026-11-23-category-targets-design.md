# Per-category sales targets — design

**Date:** 2026-11-23 · **Extends:** `2026-11-05-dashboards-design.md`

## What

The owner (ADMIN) can give each SALES rep a monthly target for each product category, for example "Coffee beans ₱30,000"
and "Equipment ₱80,000". These sit **on top of** the existing overall monthly target:

| Question | Decision |
|---|---|
| Overall target and commission | Unchanged. Commission still runs only on the overall target. |
| What a category target is | A goal with its own progress bar on the rep's dashboard, and its own 50/80/100% milestone notifications. No commission. |
| Period | Calendar month, Manila time, like the overall target. |
| What counts | Net sales collected (the same `CREDIT` figure as the overall target), split across the order's lines pro-rata by each line's net goods (`LINE_NET`). A payment of ₱500 on a ₱1,000 order with a ₱600 Coffee line and a ₱400 Tea line credits ₱300 to Coffee and ₱200 to Tea. Refunds (negative `money_movement`) split the same way. |
| Which category a line is in | The product's **current** `category_id`. Recategorizing a product moves its past sales too. Lines whose product has no category count toward no category target. |
| Zero target | Not allowed (`target_amount > 0`). To remove a target, clear it (DELETE). |
| Archived category | It can't get a new or changed target (422). An existing target stays listed, still shows progress, and can be cleared. |
| Who | Only an active SALES user can get a target (`422 INSIGHTS_NOT_A_REP`). Only ADMIN can read or write targets. |
| Copy from last month | Also copies category targets, per (rep, category) pair with no row in the target month, skipping inactive reps and archived categories. |
| Optimistic locking | Same as the overall target: a null `version` creates the row, otherwise it must match (409 `STALE_VERSION`). |

## Schema

```sql
CREATE TABLE sales_category_target (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  rep_id bigint NOT NULL REFERENCES app_user(id),
  month date NOT NULL CHECK (extract(day FROM month) = 1),
  category_id bigint NOT NULL REFERENCES product_category(id),
  target_amount numeric(19,2) NOT NULL CHECK (target_amount > 0),
  version bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by bigint NOT NULL REFERENCES app_user(id),
  UNIQUE (rep_id, month, category_id));
```

## API (ADMIN)

- `GET /api/insights/targets/{repId}/{month}/categories` → `CategoryTargetRow[]`: every active category plus any
  category with a target that month, sorted by name (case-insensitive). Unset rows have a null amount and version.
- `PUT /api/insights/targets/{repId}/{month}/categories/{categoryId}` body `{targetAmount, version}` → `CategoryTargetRow`
- `DELETE /api/insights/targets/{repId}/{month}/categories/{categoryId}?version=N` → 204
- `POST /api/insights/targets/copy` now returns `{copied, categoriesCopied}`.

`GET /api/insights/sales` gains `categories: CategoryProgress[]`, holding only categories with a target, sorted by name:
`{categoryId, name, target, netSales, progressPct, remaining, hit}`.

## Notifications

The type is `TARGET_MILESTONE` and the entity type is `SALES_TARGET`, so the bell's existing link to `/dashboard` works.
Titles: `You're at 80% of your Coffee target for November` and `Coffee target hit for November`. Body: `300.00 of 600.00`.
The overall milestone is sent first, then one notification per category that crossed a threshold, by category name.

## Out of scope

- Category progress on the admin dashboard's rep table. Add it when the owner asks to see it there.
- Snapshotting the category on the order line.
