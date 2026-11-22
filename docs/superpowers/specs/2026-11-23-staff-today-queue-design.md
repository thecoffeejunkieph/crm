# Staff Today queue — design (2026-11-23)

Both repos: `crm/crm` (one read endpoint) and `crm-dashboard-ui` (a card on the dashboard). Tracker item: O-05.

## Goal

When an admin or rep opens the dashboard, the first thing they see is what is waiting on them, each item one click
from where they act on it, instead of piecing it together from counts and notifications.

## Decisions

| Question | Decision |
|---|---|
| Where | `GET /api/insights/today` (ADMIN, SALES). A "Today" card at the top of the admin and sales dashboards. Warehouse already has its own queue |
| Freshness | Not cached (the insights payloads are); refetched on focus so a ticked-off item disappears |
| Sections | Payments to verify (`PENDING`), orders to release (`CONFIRMED`), portal quote requests (`DRAFT` orders made by O-02's portal request), follow-ups due (open, due today or earlier, assigned to me), approvals |
| Scope | ADMIN: the whole company, approvals awaiting a decision. SALES: orders of customers assigned to them (as the dashboard's "needs action"), and their own approval requests still pending ("waiting on admin"). Follow-ups are always the caller's own |
| Size | Each section: total count and the 5 oldest items. "See all" links go to the existing lists |
| Item | `kind`, `orderId` (null for follow-ups and stock adjustments), `title` (order no. · customer or the follow-up title), `detail`, `amount` (nullable), `since` (date it became due or was raised) |
| Approval links | Order, release, invoice and refund approvals resolve to their order; stock adjustments link to Stock |

## Out of scope

Assigning or snoozing items, push notifications for the queue, warehouse items on the admin card.
