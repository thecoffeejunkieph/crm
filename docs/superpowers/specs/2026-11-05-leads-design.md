# Leads (F-120) — design

Status: approved in chat 2026-11-05. Tracker item F-120 (backlog after the roadmap). F-130 opportunities builds on it.

## Goal

Reps record people who are interested but are not customers yet, work them through a few stages, and turn
the good ones into customers without retyping. Every lead and customer carries a source from one list, so
later reports (F-172) can count where business comes from.

## Decisions (from the user)

- Reps (and admin) type leads in by hand. No public web form, no CSV import (F-110 is separate).
- Lead sources are an admin-managed list, shared by leads and customers.
- Converting makes a customer (or links an existing one) and keeps the lead, marked Converted.
- Access follows customers: a SALES user sees and works only leads assigned to them; ADMIN sees all and
  assigns; WAREHOUSE has no access.

## Approach

A new Spring Modulith module `leads`, separate from `customers`. Leads never appear in customer lists,
pickers, orders or quotes. Conversion calls the `customers` module's public API; `customers` does not
depend on `leads`.

## Data — Flyway `V14__leads.sql`

`lead_source`
| column | type | rule |
|---|---|---|
| id | bigint identity | |
| name | varchar(60) not null | trimmed, 1–60 chars; unique on `lower(name)` |
| archived_at | timestamptz null | archived sources leave pick-lists, stay on old records |
| created_at, updated_at | timestamptz | |
| version | bigint | |

Seeded: Walk-in, Referral, Facebook, Website, Phone call, Trade show.

`lead`
| column | type | rule |
|---|---|---|
| id | bigint identity | |
| contact_name | varchar(120) not null | trimmed, required |
| company | varchar(160) null | |
| phone | varchar(40) null | |
| email | varchar(254) null | trimmed, valid email |
| social_media | varchar(1000) null | |
| address | text null | |
| interest | text null | what they are after, ≤ 2000 chars |
| notes | text null | ≤ 4000 chars |
| source_id | bigint null → lead_source | must be a non-archived source when set or changed |
| assigned_rep_id | bigint null → app user | active SALES user |
| status | varchar(20) not null | NEW, CONTACTED, QUALIFIED, LOST, CONVERTED |
| lost_reason | varchar(500) null | required when LOST, cleared on reopen |
| customer_id | bigint null → customer | set on conversion |
| converted_at | timestamptz null | |
| converted_by | bigint null | |
| created_by | bigint not null | |
| created_at, updated_at | timestamptz | |
| version | bigint | optimistic lock |

At least one of phone, email or social media is required, so the lead can be contacted.
Indexes: `(assigned_rep_id, status)`, `(status)`, `lower(email)`, `phone`, `(customer_id)`.
No delete: a dead lead is marked Lost.

Customers move to the source list:
- `customer.source_id bigint null → lead_source` added.
- Backfill: every distinct trimmed, non-blank `customer.source` (case-insensitive) becomes a `lead_source` row
  (matching a seeded name if equal ignoring case), and `customer.source_id` points at it.
- `customer.source` is dropped. `CustomerRequest.source` / `CustomerResponse.source` become `sourceId` and
  `sourceName`.
- `customer.converted_from_lead_id bigint null` is added and set by conversion, so the customer response can
  show `convertedFromLeadId` without `customers` depending on `leads`.

## Rules

Visibility (`LeadScopes.visibleTo`, the only place that decides):
- ADMIN: all leads. SALES: `assigned_rep_id = me`. WAREHOUSE: none (403 on every route).
- A lead a SALES user creates is assigned to them. ADMIN may pick an active SALES rep or leave it unassigned.
- Only ADMIN reassigns (`/assign`); the target must be an active SALES user.

Status:
- NEW, CONTACTED, QUALIFIED move freely among each other.
- LOST requires `lostReason` (1–500 chars). A Lost lead can go back to NEW, CONTACTED or QUALIFIED; the reason
  is cleared.
- CONVERTED is set only by conversion and is final: any edit, status change, assign or convert on a converted
  lead answers 409 `LEAD_CONVERTED`.
- Status changes carry the version; a stale version answers 409 like other optimistic-lock conflicts.

Conversion (`POST /api/leads/{id}/convert`, Idempotency-Key path):
- Allowed from NEW, CONTACTED, QUALIFIED. From LOST: 409 `LEAD_LOST` (reopen first).
- Body is exactly one of:
  - `{customer: CustomerRequest, version}` — creates a customer through `Customers.create` (made public).
    The customer takes the lead's rep as `assignedRepId` and, unless the request names one, the lead's source.
  - `{customerId, version}` — links an existing customer. It must be visible to the caller and not archived,
    else 404 like any hidden customer.
- In one transaction: customer created (or found), `customer.converted_from_lead_id` set (only if empty),
  lead set to CONVERTED with `customer_id`, `converted_at`, `converted_by`. Any failure rolls back both.
- A repeated request with the same Idempotency-Key replays the first response; a new request on the now
  converted lead gets 409 `LEAD_CONVERTED`. Either way only one customer exists.

Duplicates (`GET /api/leads/duplicates?phone=&email=&excludeId=`):
- Matches open (not Converted, not Lost) leads and non-archived customers by exact `lower(email)` or by phone
  with non-digits stripped. Returns up to 5 of each with id and name, limited to what the caller can see,
  plus a count of matches the caller cannot see ("2 more held by other reps").
- Only a warning in the form; saving is never blocked.

Lead sources:
- `GET /api/lead-sources?includeArchived=` for ADMIN and SALES. `POST`, `PUT /{id}` (rename), `POST /{id}/archive`,
  `POST /{id}/restore` for ADMIN. Duplicate name (ignoring case) is 409 `LEAD_SOURCE_EXISTS`.

## API

| method + path | who | notes |
|---|---|---|
| GET `/api/leads?status=&q=&sourceId=&repId=&page=&size=` | ADMIN, SALES | `q` matches name, company, phone, email, social; `repId` admin only; default excludes CONVERTED unless `status` asks |
| GET `/api/leads/counts` | ADMIN, SALES | count per status within the caller's scope |
| GET `/api/leads/{id}` | ADMIN, SALES | 404 when hidden |
| POST `/api/leads` | ADMIN, SALES | status starts NEW |
| PUT `/api/leads/{id}` | ADMIN, SALES | fields only, with `version`; not status, rep or conversion fields |
| POST `/api/leads/{id}/status` | ADMIN, SALES | `{status, lostReason, version}` |
| POST `/api/leads/{id}/assign` | ADMIN | `{repId, version}` |
| POST `/api/leads/{id}/convert` | ADMIN, SALES | see Conversion |
| GET `/api/leads/duplicates` | ADMIN, SALES | see Duplicates |
| lead-source routes | see above | |

New error codes: `LEAD_CONVERTED`, `LEAD_LOST`, `LEAD_LOST_REASON`, `LEAD_CONTACT_REQUIRED`,
`LEAD_SOURCE_EXISTS`, `LEAD_SOURCE_ARCHIVED`. All new routes go in SecurityMatrixTest; the OpenAPI snapshot is refreshed.
`/api/leads` joins the IdempotencyFilter paths.

Audit (`AuditAction`): LEAD_CREATED, LEAD_UPDATED (changed fields), LEAD_STATUS_CHANGED (from → to, reason),
LEAD_ASSIGNED, LEAD_CONVERTED (customer id, new or linked), LEAD_SOURCE_ADDED, LEAD_SOURCE_RENAMED,
LEAD_SOURCE_ARCHIVED, LEAD_SOURCE_RESTORED. Step logging and redaction come from the existing logging work;
phone, email and contact name are not logged at INFO.

## UI (crm-dashboard-ui)

- Nav: **Leads** in the sales group, for ADMIN and SALES.
- `/leads`: status tabs with counts (Open = New + Contacted + Qualified, then each status, Lost, Converted),
  search, source filter, rep filter for admin, **New lead** button. Rows show name, company, source, rep,
  status, last updated.
- **Lead form** (drawer or dialog, as customers do): the fields above; source is a pick-list of active sources;
  under phone/email, the duplicate warning ("Matches customer Acme Trading", linked) after the field loses focus.
- `/leads/:id`: details, notes, status buttons (Lost asks for a reason), admin **Reassign**, and
  **Convert to customer**. Convert opens the existing customer form prefilled (display name ← company or
  contact name, contact name, phone, email, social, address, source; type and payment terms chosen by the
  user) with a **Link an existing customer instead** option using the customer picker. On success it goes to
  the new customer. A converted lead shows "Converted to <customer>" and no actions.
- **Settings → Lead sources** (admin): list with archived toggle, add, rename, archive, restore.
- Customer form: Source becomes a pick-list of active sources (current value shown even if archived).
  Customer page: "Converted from lead" link when set.

## Testing

- Backend ITs: scopes per role (incl. warehouse 403 and hidden-lead 404); create assigns the rep; contact-required
  rule; each status transition incl. Lost reason and reopen; converted is final; convert new and linked;
  convert from Lost; double convert with the same key and with a new key gives one customer; linking a hidden
  or archived customer; duplicates with hidden matches counted not named; source CRUD and name clash;
  migration backfill of existing customer sources (case-folded, seeded-name match).
- SecurityMatrixTest rows, ModularityTest (leads → customers only), OpenAPI snapshot.
- UI unit tests: list tabs and counts, form validation and duplicate warning, status actions, convert dialog
  both modes, lead sources settings page, customer form source pick-list.
- e2e `leads.spec.ts`: create lead → mark Qualified → convert to a new customer → customer page shows the
  source and "Converted from lead".

## Out of scope

Follow-ups on leads (follow-ups stay on customers), public web form, CSV import of leads (F-110), lead
dashboards and funnel charts (F-172), opportunities (F-130), automatic lead assignment, merging duplicates.
