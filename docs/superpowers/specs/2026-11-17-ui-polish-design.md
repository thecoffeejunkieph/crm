# UI polish bundle — design (2026-11-17)

Spans both repos: `crm/crm` (backend) and `crm-dashboard-ui` (frontend). Five independent improvements, each
extending a flow that already exists.

## Decisions (from the user)

| Question | Decision |
|---|---|
| Sorting scope | Every visible column on every table, including linked names (customer, rep, warehouse, product) |
| Image viewer | Images only, in an in-page viewer with ‹ › controls; PDFs and other files keep opening in a new tab |
| Dashboard style | Option C: a gradient "hero" card for the headline metric, with a sparkline, plus soft tinted tiles for the other stats; gold/silver/bronze ranks |

## 1. Sorting on every table

### What exists
- `DataTable` columns accept `sortable`, and the sort is kept in the URL as `sort=field,dir` (`useTableParams`).
- On the backend, `PageQuery.toPageable(Set<String> sortable, Sort default)` validates the field against a
  per-endpoint whitelist and returns 400 `VALIDATION_FAILED` for anything else.
- Only 5 of 15 paged pages use sorting today, on 1–3 columns each.

### Paged lists (backend sort)
Mark every visible column `sortable` on these 15 `DataTable` pages: ActivityLog, Users, Products, Customer
page tabs, Customers, Deals, DeliveryOrders, Movements drawer, Stock, Transfers, Leads, CustomerSalesTabs,
Missing2307, Orders, Warehouses.

On each backing endpoint:
- **Own columns:** add every column the page shows to the endpoint's `SORTABLE` set.
- **Linked names:** columns whose entity holds only an id (e.g. `customerId`) map a sort key (`customer`,
  `rep`, `warehouse`, `product`, `assignee`) to a join on that table's display column. Do this through a
  small shared helper `shared/SortJoins` that turns a sort key into an `Order` inside the JPA `Specification`
  (a left join to the named table's display column, nulls last). Add an `@ManyToOne(fetch = LAZY,
  insertable = false, updatable = false)` read-only association only where the Specification approach needs
  one.
- **Ties:** the default sort and its tie-breaker stay as they are. Add `id` as the final tie-breaker
  everywhere, so paging is stable.
- **Column naming:** each page's column `key` must equal the backend sort key. Where they differ today,
  the column gets a `sortKey`, and DataTable uses `sortKey ?? key`.

### Unpaged tables (browser sort)
- Tables: AdminDashboard lists, ReportsPage, InventoryPage, SalesTargetsPage, LinesTab (read-only view),
  PortalOrderPage.
- New `ui/useSortedRows<T>(rows, columns, initial?)` returns `{rows, sort, toggle(key)}`. Comparison is
  numbers numeric, dates by time, strings `localeCompare` with `sensitivity: "base"`, nulls last.
- A shared `SortableHeader` cell is reused by DataTable and these tables, so the arrow and ARIA
  (`aria-sort`) look and behave the same.
- Browser sort state is not put in the URL.

## 2. Order search by customer name

- Backend: `OrderFilter.q` matches `lower(number) like %q%` **or** `lower(customer.display_name) like %q%`,
  using the existing `Like.contains` escaping. Customer has no association on the order, so this is a
  join/subquery in `OrderService`'s Specification.
- Frontend: the Orders search placeholder becomes "Search order no. or customer".
- The customer's own Orders tab is already filtered by customer and is unchanged.

## 3. Image viewer

### Component
`ui/ImageViewer.tsx`, a full-screen overlay with:
- the image fitted to the viewport;
- ‹ › buttons, ←/→ keys and horizontal swipe (pointer events, threshold 50px). Navigation wraps around;
- a counter `2 / 5` and the file name;
- Download (link with the `download` attribute) and Close buttons. Esc and a backdrop click also close it;
- focus trap via the existing `useFocusTrap`, returning focus to the opener on close;
- body scroll locked while open.

### Hook
`useImageViewer(items: {url: string; name: string}[])` returns `{open(index), viewer}`.

### Where it's used
- `fulfilment/ProofGallery.tsx`: thumbnails open the viewer on all proof photos of that delivery.
- `attachments/FileRow.tsx` and `ui/ViewLink.tsx` callers: when a file is an image (by content type if
  present, else by extension `jpg|jpeg|png|webp|gif|heic`), clicking opens the viewer on all image files of
  the same list. Non-images keep `target="_blank"`.

## 4. Notification date and time

Shared formatter `ui/formatWhen(iso, now = new Date())`, using Asia/Manila:

| When | Shown as |
|---|---|
| Less than 1 minute ago | `just now` |
| Less than 60 minutes ago | `5m ago` |
| Same Manila day | `3:42 PM` |
| Previous Manila day | `Yesterday 3:42 PM` |
| Otherwise | `16 Nov, 3:42 PM` |
| A different year | `16 Nov 2025, 3:42 PM` |

- **Bell dropdown items:** show `formatWhen` under the body, with the full date-time as `title` / `<time dateTime>`.
- **Notifications page:** replace `toLocaleString()` with `formatWhen`.

## 5. Dashboard style C

### Parts (`insights/parts.tsx`)
- **`HeroStat`.** Props: `{label, value, trend?: {pct: number; vs: string}, spark?: number[], progress?: {pct: number; caption: string}}`.
  - Brand gradient (`brand-500` → `brand-700`) with white text.
  - Trend shown as `▲ 12% vs October` / `▼ 3% vs October`.
  - Sparkline is an inline SVG polyline of up to 6 points. No library.
  - Progress bar is hidden when `progress` is absent.
- **`Stat`.** Gains `tone?: "purple" | "green" | "amber" | "blue" | "gray"`, which sets a tinted background (light and dark variants).
- **`Ranked`.** Shows rank chips for ranks 1–3 (gold `#ffd60a`, silver `#e5e5ea`, bronze `#f4d7bd`, with readable text colours), then plain numbers.
  - Shows 5 rows and a **See more** button that reveals 5 more per click (same pattern as `sales/ActivitySection`).
  - The button disappears when all rows are shown.

### Dashboards
- **Admin.**
  - Hero = Collected: trend from `collectedPrevMonth`, spark from the last 6 points of `collectedByMonth`, progress against the sum of reps' targets (from `reps[].target`; hidden if the sum is 0).
  - Tiles: Invoiced (purple), Gross margin (green), Outstanding (amber, sub `₱x overdue`).
- **Sales rep.**
  - Hero = my net sales: progress vs my target, spark from my monthly history if present in `SalesInsights`, else no spark.
  - Other stats as tiles.
- **Warehouse.**
  - Hero = orders to pick: no trend, no spark.
  - Other operations counts as tiles.
- **Top lists.** Backend `InsightsService` raises the `topProducts` and `topCustomers` limit from 5 to 20, for both the admin and the sales rep's top customers.
- **Data.** No other backend data changes. Trends for Invoiced and Margin are out of scope.

## Testing

### Backend
- One IT per changed list endpoint: sort by a linked name (asc and desc order is correct, nulls last) and an unknown sort field returning 400.
- `OrdersIT.searchMatchesCustomerName`, with case-insensitive and partial matching.
- Insights tests: assert up to 20 top rows.
- Regenerate the OpenAPI snapshot if any DTO changes.

### Vitest
- `useSortedRows`: numbers, dates, strings, nulls last.
- Each page has sortable headers that update `sort`.
- ImageViewer: arrows, keys and wrap-around; Esc closes; download link; non-images still open a new tab.
- `formatWhen`: each row of the table, including across Manila midnight.
- HeroStat: trend sign, spark rendered, progress hidden when there is no target.
- Ranked: 5 rows shown, See more adds 5, button gone at the end, medal chips on ranks 1–3.

## Out of scope
- Multi-column sort.
- Server-side sort for unpaged tables.
- PDF preview.
- Image zoom and pan.
- Trends for metrics other than Collected.
- Saving dashboard layout preferences.
