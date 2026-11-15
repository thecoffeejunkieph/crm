# Attachments (M-08) — design

Status: approved in chat 2026-11-05. Tracker item M-08 (blueprint F-146), backlog after the roadmap.

## Goal

Keep the paper that belongs to a customer, order or invoice with it: a customer's PO, a signed contract, a permit, a
delivery photo, a price sheet. Anyone who can open the record finds its files there.

## Decisions (from the user)

- Types: documents and images — PDF, JPEG, PNG, WebP, DOCX, XLSX, CSV.
- Access follows the record. The uploader or an admin may remove a file; removing archives it. Warehouse users see an
  order's files on its deliveries. Nothing is shown in the customer portal.

## Data — Flyway `V13__attachments.sql`

`attachment`
| column | type | rule |
|---|---|---|
| id | bigint identity | |
| subject_type | text not null | `CUSTOMER`, `ORDER`, `INVOICE` (check constraint) |
| subject_id | bigint not null | no FK (polymorphic); checked in service |
| object_key | text not null unique | `attachments/{type}/{subjectId}/{uuid}.{ext}` |
| file_name | varchar(255) not null | original name, cleaned: path parts and control chars removed |
| content_type | text not null | detected type, never the client's claim |
| size_bytes | bigint not null | ≤ 20 MB |
| note | varchar(200) null | |
| uploaded_by | bigint not null → app_user | |
| uploaded_at | timestamptz not null | |
| archived_at | timestamptz null | |
| archived_by | bigint null → app_user | |

Index: `(subject_type, subject_id) where archived_at is null`.

## Rules

- **Types** (checked by file signature in `StorageService`, extending its magic-byte check): PDF `%PDF-`; JPEG
  `FF D8 FF`; PNG `89 50 4E 47`; WebP `RIFF....WEBP`; DOCX/XLSX = ZIP `50 4B 03 04` whose entries contain
  `word/document.xml` / `xl/workbook.xml` (anything else in a zip is rejected, including `.docm`/`.xlsm` macros);
  CSV = valid UTF-8 text with no NUL bytes and a `.csv` name. Otherwise 422 `ATTACHMENT_TYPE`.
- **Size:** each file ≤ 20 MB (`ATTACHMENT_TOO_LARGE`, 422); at most 10 files per request (400). Spring multipart
  limits set to 20 MB per file, 200 MB per request.
- **Who sees and adds**, by subject:
  - CUSTOMER: visible through `Customers.salesProfileVisible` (rep's own; admin all). Warehouse: none.
  - ORDER: visible if the order's customer is visible; WAREHOUSE also when a delivery order for that sales order is in
    one of the user's warehouses (read only).
  - INVOICE: as its order.
  - Not visible → 404 (never reveals existence).
- **Add** requires visibility and a writable subject: not WAREHOUSE; customer not archived; order not CANCELLED;
  invoice not VOID. Otherwise 422 `ATTACHMENT_READ_ONLY`.
- **Remove** (`archive`): the uploader or an ADMIN, on a visible attachment; others 403 is avoided — they get 404 if
  they cannot see it, 422 `ATTACHMENT_NOT_YOURS` if they can see but did not upload. Archiving is final in the UI;
  the object stays in MinIO.
- **Download:** `GET /api/attachments/{id}/download` checks visibility (archived files are still downloadable by
  id for audit) and answers 302 to a presigned URL valid 10 minutes, with
  `response-content-disposition=attachment; filename*=UTF-8''{file_name}`.
- **Audit:** `ATTACHMENT_ADDED` / `ATTACHMENT_REMOVED`, recorded on the subject's entity type and id
  (`CUSTOMER`/`ORDER`/`INVOICE`) so they appear in that record's Activity, summary `Attached "PO-123.pdf"`.
- **Upload is all-or-nothing per request:** every file is validated before any is stored; if storing one fails, the
  ones stored in that request are deleted and nothing is saved.

## API — `/api/attachments`

- `GET ?subjectType=&subjectId=` → `List<AttachmentResponse>` (not archived, newest first).
  `AttachmentResponse {id, subjectType, subjectId, fileName, contentType, sizeBytes, note, uploadedBy, uploadedByName,
  uploadedAt, canRemove}`.
- `GET ?orderId=` for the order page: the order's own files plus its invoices' files, each with its subject.
- `POST` multipart: `subjectType`, `subjectId`, `note?`, `files[]` (1–10) → 201 `List<AttachmentResponse>`.
  Honours `Idempotency-Key`.
- `POST /{id}/archive` → 204.
- `GET /{id}/download` → 302.
- `GET /{id}/thumbnail` → 302 to the same presigned URL, images only (UI shows it at 48 px; no resizing).
- URL rules: all routes STAFF (`ADMIN`, `SALES`, `WAREHOUSE`); finer checks in the service. `SecurityMatrixTest` rows
  and the OpenAPI snapshot updated.

## Screens (crm-dashboard-ui)

- `features/attachments/FilesSection` in the rail of the customer page, the order workspace (order + invoice files,
  invoice files tagged with the invoice number) and, read-only, the delivery order page.
- Add: button opens the file picker (`multiple`, `accept` = the allowed types); drop zone on desktop; optional note
  applied to the batch; per-file progress (axios `onUploadProgress`); a rejected type or size shows the file name and
  reason before anything uploads.
- Row: type icon or 48 px thumbnail, file name (download link), size, uploader and date, note, Remove (when
  `canRemove`) behind a confirm "Remove {name}? It disappears for everyone."
- Empty: "No files yet."

## Testing

- Integration (MinIO + Postgres Testcontainers): add/list/download per subject; rep scoping (other rep's customer
  404); warehouse sees order files through a delivery in their warehouse and not otherwise, and cannot add;
  `.exe` renamed `.pdf` → 422; `.docm` zip → 422; 20 MB + 1 byte → 422; 11 files → 400; archived customer /
  cancelled order / void invoice → 422 read only; remove by uploader and admin OK, by another rep 422; archived file
  gone from list but downloadable; audit rows on the subject; all-or-nothing when the 2nd file fails.
- `StorageService` signature unit tests for each type.
- Vitest: section list, picker validation messages, progress, remove confirm, read-only mode.
- e2e `attachments.spec.ts`: attach a PDF to a customer, see it listed, download returns 200 `application/pdf`,
  remove it.

## Out of scope

Virus scanning, versions, previews beyond image thumbnails, files in the customer portal, a global file list,
attachments on other records (products, deliveries, transfers).
