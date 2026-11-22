# Customer self-service profile — design (2026-11-22)

Both repos: `crm/crm` (API, migration V28) and `crm-dashboard-ui` (portal Company page, staff views). Tracker item:
O-04 (Smoother operations).

## Goal

A customer keeps their own contacts, delivery addresses and billing recipients current from the portal, instead of
asking a rep to edit the customer record.

## Decisions

| Question | Decision |
|---|---|
| Who receives invoices | New `customer_contact.receives_invoices` (default true). Existing rows copy `portal_enabled`, so today's recipients don't change. A billing contact need not have portal access (an accountant who never signs in) |
| Which mails use it | Billing mails: invoice sent and overdue reminder go to active contacts with `receives_invoices`. Order status mails (confirmed, payment, dispatched, delivered) stay with portal contacts |
| Portal contacts | Any portal contact can list, add, edit and archive their company's contacts: name, email, phone, portal access, receives invoices. Same email uniqueness and session revoke as staff edits. Audited with the contact as actor |
| Self-protection | A contact can't archive themself, turn off their own portal access or change their own email (they would lock themself out). Name, phone and receives-invoices are fine |
| Delivery addresses | New `customer_address` (label, address, default flag, archive). At most one default per customer (partial unique index). The first address becomes default; archiving the default leaves none |
| Where an address is used | New `sales_order.ship_to` text, a snapshot. The portal quote request can pick an address (`addressId`). When a delivery order is created its ship-to is: the order's `ship_to`, else the customer's default delivery address, else the customer address (today's behaviour) |
| Staff | Customer page lists delivery addresses (read-only) and shows which contacts get invoices; the contact drawer gets a "Receives invoices" checkbox. The order page shows Ship to when set |

## API (portal, CUSTOMER only, scoped to the contact's company)

| Method | Path | Body |
|---|---|---|
| GET | `/api/portal/contacts` | — |
| POST | `/api/portal/contacts` | `ContactRequest` (+ `receivesInvoices`) |
| PUT | `/api/portal/contacts/{id}` | `ContactRequest` with `version` |
| POST | `/api/portal/contacts/{id}/archive` | — |
| GET | `/api/portal/addresses` | — |
| POST | `/api/portal/addresses` | `{label, address, isDefault}` |
| PUT | `/api/portal/addresses/{id}` | `{label, address, isDefault, version}` |
| POST | `/api/portal/addresses/{id}/archive` | — |

Staff: `GET /api/customers/{id}/addresses`. `POST /api/portal/quote-requests` takes optional `addressId`.

## Out of scope

Staff editing addresses or an order's ship-to, per-mail-type recipient settings, editing the company's own name, TIN
or billing address from the portal (those stay with staff because they print on BIR invoices).
