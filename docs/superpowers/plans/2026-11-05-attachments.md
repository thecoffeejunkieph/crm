# Attachments (M-08) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Files (PDF, images, DOCX, XLSX, CSV) on customers, orders and invoices, visible to whoever can open the record, removable (archived) by the uploader or an admin, read-only for warehouse through deliveries.

**Architecture:** New Spring Modulith module `attachments` owning `attachment`; it stores bytes through `files.StorageService` (extended with Office/CSV detection, a key-based put and a download presign), and checks access through `Customers.salesProfileVisible` plus SQL reads of `sales_order`, `invoice` and `delivery_order` (no dependency on sales/fulfilment internals, like `insights` and `followups`). UI adds `features/attachments/FilesSection` used in three rails.

**Tech Stack:** Spring Boot 4 / Java 21, Postgres 17 + Flyway, MinIO (Testcontainers in ITs, as `StorageServiceIT`); React 19, TanStack Query 5, axios upload progress, Vitest, Playwright.

**Spec:** `docs/superpowers/specs/2026-11-05-attachments-design.md` (crm/crm). Runs **after** the logging plan (the new module gets logging from it automatically). Branch `redesign/attachments` in both repos, cut after logging is merged.

## Global Constraints

- Allowed types and signatures: PDF `%PDF-`; JPEG `FF D8 FF`; PNG `89 50 4E 47 0D 0A 1A 0A`; WebP `RIFF????WEBP`; DOCX = ZIP (`50 4B 03 04`) containing entry `word/document.xml` and **no** `word/vbaProject.bin`; XLSX = ZIP containing `xl/workbook.xml` and no `xl/vbaProject.bin`; CSV = name ends `.csv`, bytes decode as strict UTF-8 (BOM allowed), no NUL byte. Content types: `application/pdf`, `image/jpeg`, `image/png`, `image/webp`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `text/csv`.
- Limits: 20 MB per file (`ATTACHMENT_TOO_LARGE`, 422), 1–10 files per request (`VALIDATION_FAILED`, 400). Multipart: `spring.servlet.multipart.max-file-size=20MB`, `max-request-size=200MB` — set in `application-dev/-staging/-prod.properties` and the test config, **not** `application.properties` (user's local change there).
- Error codes (`shared/ErrorCode`): `ATTACHMENT_TYPE`, `ATTACHMENT_TOO_LARGE`, `ATTACHMENT_READ_ONLY`, `ATTACHMENT_NOT_YOURS` — all `UNPROCESSABLE_CONTENT`.
- Audit actions: `ATTACHMENT_ADDED`, `ATTACHMENT_REMOVED`, recorded with entity type = the subject's (`CUSTOMER`/`ORDER`/`INVOICE`) and entity id = subject id; summaries `Attached "{name}"` / `Removed "{name}"`.
- Object key: `attachments/{customer|order|invoice}/{subjectId}/{uuid}{ext}`.
- `file_name` cleaning: take the part after the last `/` or `\`, drop control chars, trim, cut to 255; empty → `file{ext}`.
- Presigned download: 10 minutes, `response-content-disposition: attachment; filename*=UTF-8''{percent-encoded name}`.
- Not visible → 404 everywhere (never reveal existence).
- Gradle via PowerShell + `JAVA_TOOL_OPTIONS` as in the logging plan; never commit the user's `application.properties` / `docker-compose.dev.yml`.

## Review Focus

1. A zip renamed `.docx` that is really a `.docm` (has `vbaProject.bin`) — rejected (Task 1).
2. A file name like `../../etc/passwd` or `"evil\r\n.pdf"` — stored cleaned, and the Content-Disposition is not header-injectable (Tasks 1 and 3).
3. A warehouse user guessing an attachment id on an order whose deliveries are in another warehouse — 404 (Task 2).
4. Upload where the 3rd of 3 files fails to store in MinIO — nothing saved, the first two objects deleted (Task 3).
5. An invoice that is VOID while its order is still open — the invoice's files are read-only but the order's are not (Task 2).

---

### Task 1: StorageService — detect, put, presign for download

**Files:** Modify `files/StorageService.java`; Test `src/test/java/ph/thecoffeejunkie/crm/files/StorageDetectTest.java`, extend `StorageServiceIT`

**Interfaces:**
- Produces: `public record Detected(String contentType, String ext)`; `public Optional<Detected> detect(byte[] bytes, String fileName)` covering the existing four types plus DOCX/XLSX/CSV (existing `store` keeps its own `allowedTypes` behaviour, now via `detect`).
- Produces: `public void put(String key, byte[] bytes, String contentType)` (throws `IllegalStateException` on failure, like `store`).
- Produces: `public String presignedDownloadUrl(String key, String fileName)` — 10 min, extra query param `response-content-disposition`.
- Produces: `public static String cleanFileName(String raw, String ext)` per Global Constraints.

- [ ] **Step 1: Tests** — one positive per type from small fixture byte arrays built in the test (minimal zips built with `java.util.zip.ZipOutputStream`); negatives: `MZ` exe named `.pdf`, zip with `word/document.xml` **and** `word/vbaProject.bin`, plain zip, CSV with a NUL, CSV with invalid UTF-8, `.txt` text file; `cleanFileName("../../a/b.pdf", ".pdf")` → `b.pdf`, `"x\r\n.pdf"` → `x.pdf`, `""` → `file.pdf`. IT: `put` then `presignedDownloadUrl` contains `response-content-disposition`, and fetching it returns the bytes.
- [ ] **Step 2:** run → FAIL. **Step 3:** implement. **Step 4:** run plus existing `StorageServiceIT` and proof-upload ITs → PASS.
- [ ] **Step 5:** commit `feat(files): office and csv detection, put, download links`.

### Task 2: Table, entity and access

**Files:** Create `db/migration/V13__attachments.sql`, `attachments/{package-info, Attachment, AttachmentRepository, SubjectType, AttachmentAccess}.java`; Test `attachments/AttachmentAccessIT.java`, `attachments/AttachmentsFixture.java`

**Interfaces:**
- Produces: `enum SubjectType { CUSTOMER, ORDER, INVOICE; String keySegment(); }` (`customer`/`order`/`invoice`).
- Produces: `AttachmentAccess` (`@Component`):
  - `boolean canSee(Scope s, SubjectType t, long id)`;
  - `void requireWritable(Scope s, SubjectType t, long id)` → throws `NOT_FOUND` if not visible, `ATTACHMENT_READ_ONLY` if WAREHOUSE, customer archived, order `CANCELLED`, invoice `VOID`;
  - `List<Long> invoiceIdsOfOrder(long orderId)`.
  Rules: CUSTOMER via `customers.salesProfileVisible(s, id)` (WAREHOUSE never); ORDER via its `customer_id` visible, or WAREHOUSE with a `delivery_order` row `sales_order_id = id and warehouse_id in s.warehouseIds()`; INVOICE via its `order_id` with the ORDER rule.
- V13 per spec table, check constraint on `subject_type`, partial index.

- [ ] **Step 1: Tests:** rep sees own customer / order / invoice, not another rep's (false); admin all; warehouse: order with delivery in its warehouse true, other warehouse false, customer false; `requireWritable`: warehouse → READ_ONLY, archived customer → READ_ONLY, cancelled order → READ_ONLY, void invoice → READ_ONLY while its order (not cancelled) stays writable; unknown ids → NOT_FOUND.
- [ ] **Step 2:** run → FAIL. **Step 3:** implement. **Step 4:** run + `ModularityTest` → PASS.
- [ ] **Step 5:** commit `feat(attachments): table and access rules`.

### Task 3: Upload, list, download, archive

**Files:** Create `attachments/{AttachmentService, AttachmentController, AttachmentResponse}.java`; Modify `shared/ErrorCode`, `audit/AuditAction`, `identity/SecurityConfig` (`/api/attachments/**` → `hasAnyRole(ADMIN, SALES, WAREHOUSE)`), `shared/IdempotencyFilter` (`/api/attachments`), profile property files (multipart limits); Test `attachments/AttachmentsIT.java`, `SecurityMatrixTest` rows, OpenAPI snapshot

**Interfaces:**
- Produces: `record AttachmentResponse(long id, SubjectType subjectType, long subjectId, String fileName, String contentType, long sizeBytes, String note, long uploadedBy, String uploadedByName, Instant uploadedAt, boolean canRemove)`.
- Produces endpoints:
  - `GET /api/attachments?subjectType&subjectId` → list (not archived, newest first); `GET /api/attachments?orderId` → order + its invoices' files.
  - `POST /api/attachments` multipart (`subjectType`, `subjectId`, `note?` ≤ 200, `files` 1–10) → 201 list.
  - `POST /api/attachments/{id}/archive` → 204.
  - `GET /api/attachments/{id}/download` → 302 `Location` presigned (archived still downloadable).
  - `GET /api/attachments/{id}/thumbnail` → 302 for image types, 404 otherwise.
- Upload algorithm: read all bytes, `detect` each (any miss → `ATTACHMENT_TYPE` naming the file), size check each, then `put` each in order; if a `put` throws, `deleteQuietly` the keys already put and rethrow; then save rows and audit — all in one transaction.
- `canRemove` = uploader or ADMIN, and subject writable.

- [ ] **Step 1: Tests:** upload 2 files to a customer as rep → 201, list shows both newest first with `uploadedByName`; download → 302 whose Location fetches the bytes with the original name in the disposition; `.exe`-as-pdf → 422 `ATTACHMENT_TYPE` and nothing stored (row count and MinIO prefix empty); 20 MB + 1 byte → 422 `ATTACHMENT_TOO_LARGE`; 11 files → 400; other rep's customer → 404; warehouse upload on its order → 422 READ_ONLY, warehouse list → 200; archive by uploader → 204 then gone from list, download still 302; archive by another rep who can see it → 422 `ATTACHMENT_NOT_YOURS`; admin archives anyone's; audit `ATTACHMENT_ADDED`/`REMOVED` on `CUSTOMER` entity; `orderId` listing includes an invoice's file tagged `INVOICE`; all-or-nothing: spy `StorageService.put` to throw on the 2nd call → 500/IllegalState, zero rows, first key deleted; header injection: name `"a\r\nX-Evil: 1.pdf"` → Location/disposition contains no CR/LF.
- [ ] **Step 2:** run → FAIL. **Step 3:** implement. **Step 4:** matrix rows for all 5 routes (STAFF), snapshot refresh, run + full suite → green.
- [ ] **Step 5:** commit `feat(attachments): upload, list, download, archive`.

### Task 4: UI — FilesSection

**Files (crm-dashboard-ui):** `npm run gen:api`; Create `src/features/attachments/{api.ts, FilesSection.tsx, FileRow.tsx, FilesSection.test.tsx}`

**Interfaces:**
- Produces in `api.ts`: `type Attachment = Filled<...AttachmentResponse> (note optional)`; `attachmentKeys.list(p)`; `fetchAttachments(p: {subjectType, subjectId} | {orderId})`; `uploadAttachments({subjectType, subjectId, note, files}, onProgress(fileIndex, pct))` — one multipart POST with `onUploadProgress` (overall %, reported to every file in the batch; **Ruling: one request per batch, not per file, keeps all-or-nothing; per-file bars show the batch %** — cost: bars move together); `archiveAttachment(id)`; `downloadUrl(id)` = `${API_BASE}/attachments/${id}/download`.
- Produces `FilesSection({subject: {type, id} | {orderId: number}, readOnly?: boolean, addTo?: {type, id}})`: `Section title="Files"`, Add (hidden when `readOnly`) → hidden `<input type="file" multiple accept=".pdf,.jpg,.jpeg,.png,.webp,.docx,.xlsx,.csv">`, plus a drop zone on `pointer: fine`; client-side precheck by extension and 20 MB/10 files with messages `"{name}: this type isn't allowed"`, `"{name}: over 20 MB"`, `"Choose up to 10 files"`; note field; upload with progress; rows via `FileRow` (icon by type or 48 px `<img src=thumbnail>` for images, name as download link, size in KB/MB, `{uploader} · {date}`, note, Remove → `ConfirmDialog` "Remove {name}? It disappears for everyone."). Empty: "No files yet." Invoice files on the order show `Invoice {number}` — **decision: the response has no invoice number; show "Invoice file" tag**.

- [ ] **Step 1: Tests:** lists rows and empty text; precheck rejects `x.exe` and a 21 MB file without calling upload; uploads two files with the note and shows progress; Remove asks then calls `archiveAttachment`; `readOnly` hides Add and Remove; image row renders a thumbnail img.
- [ ] **Step 2:** run → FAIL. **Step 3:** implement. **Step 4:** run + `npx tsc -b` → green.
- [ ] **Step 5:** commit `feat(attachments): files section`.

### Task 5: Place it, and e2e

**Files:** Modify `src/features/customers/CustomerPage.tsx` (rail, after Follow-ups: `<FilesSection subject={{type: "CUSTOMER", id}} readOnly={archived}/>`), `src/features/sales/OrderWorkspace.tsx` (rail: `<FilesSection subject={{orderId: id}} addTo={{type: "ORDER", id}} readOnly={o.status === "CANCELLED"}/>`), the delivery order page (`src/features/fulfilment/DeliveryOrderPage.tsx`: `<FilesSection subject={{orderId: d.salesOrderId}} readOnly/>` — check the field name in that page's data); Create `e2e/attachments.spec.ts`

- [ ] **Step 1:** extend `CustomerPage`/`OrderWorkspace` tests only if they assert the rail's sections list; otherwise rely on Task 4 tests.
- [ ] **Step 2: e2e:** admin signs in, creates a customer, Files → Add a small generated PDF (`Buffer.from("%PDF-1.4\n%…")` via `setInputFiles`), sees it listed; `request.get(downloadHref, {maxRedirects: 0})` → 302, following it → 200 `application/pdf`; Remove → confirm → "No files yet.". Run with the scratchpad harness on a fresh DB, plus the six existing specs.
- [ ] **Step 3:** whole UI suite + tsc green; commit `feat(attachments): files on customer, order and delivery pages`.
