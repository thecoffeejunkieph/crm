# Statements of account, AR aging and overdue reminders — design (2026-11-22)

Both repos: `crm/crm` (API, PDF, job) and `crm-dashboard-ui` (report, buttons). Tracker item: O-03 (Smoother operations).

## Goal

Staff see who owes what and how late, customers get a statement they can pay from, and late customers are reminded
without a rep chasing them by hand.

## Decisions

| Question | Decision |
|---|---|
| What is "open" | An order in `CONFIRMED`, `RELEASED` or `FULFILLED` with `settled_amount < total` (same rule as the credit check and `hasOverdue`). Balance = `total - settled_amount` |
| Statement kind | **Open-item** statement (what is unpaid today), not an activity ledger. One row per open order: order no., active invoice no. and date, due date, total, paid, balance, days overdue |
| Aging buckets | By days past the order's due date, Manila date: Current (no due date or not yet due), 1–30, 31–60, 61–90, Over 90 |
| Statement PDF | Letterhead "STATEMENT OF ACCOUNT", customer block, open items, aging summary, total due, the company's bank accounts. Rendered on demand, not stored |
| Who can get it | Staff who can see the customer: `GET /api/customers/{id}/statement.pdf`. Portal contact, own company only: `GET /api/portal/statement.pdf` |
| AR aging report | `GET /api/reports/ar-aging`: one row per customer with an open balance, scoped like the other reports, sorted by most overdue (Over 90, then 61–90, …). Not paged: only customers who owe appear |
| Reminder schedule | Daily 09:00 Manila. A customer gets one email when any open order is exactly 1, 7, 14 or 30 days overdue, or a further multiple of 30 |
| Reminder content | The overdue orders and amounts, total overdue, portal link, statement PDF attached. Sent to the customer's portal contacts (as O-01 mails) |
| Run once | Redis lock `overdue-reminders:{date}` held 23 h and never released, so a second instance or a restart the same day sends nothing |
| Missed day | No catch-up: a milestone on a day the job didn't run is skipped; the next milestone still fires |
| Tests | `app.sales.overdue-reminders.scheduled=false`; the IT calls the job with a fixed date |

## Out of scope

Activity statements with a running balance, reminder settings per customer, CSV export of the aging report, emailing
the statement from the staff screen.
