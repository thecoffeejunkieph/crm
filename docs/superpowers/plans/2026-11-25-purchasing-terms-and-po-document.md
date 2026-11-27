# Purchasing Terms and PO Document Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Per-supplier downpayment terms that block sending until paid, a PO document (PDF and PNG) in the house PDF style, automatic and manual emailing to the supplier, and Edit from the suppliers list.

**Architecture:** All backend work stays in the `purchasing` module, except a PNG method on `shared.PdfRenderer` and one `AuditAction` constant. A downpayment is an ordinary `supplier_payment` row with a nullable `purchase_order_id`, so the credit math does not change. The document is a Thymeleaf template printed by the existing Playwright renderer; mail follows `SalesMailer`'s after-commit pattern.

**Tech Stack:** Spring Boot, Spring Modulith, JPA/JdbcTemplate, Flyway, Thymeleaf, Playwright, Testcontainers; React, TanStack Query, openapi-typescript, Vitest.

**Spec:** `docs/superpowers/specs/2026-11-25-purchasing-terms-and-po-document-design.md`

## Global Constraints

- **Repos:** backend `crm/crm` on `phase/major_refactor_ui`; frontend `crm-dashboard-ui` on `phase/major_refactor`.
- **Gradle in Claude's shell:** `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/Users/Public/gtmp ./gradlew --no-daemon ...`.
- **Don't touch:** uncommitted changes that were already present (`insights/*`, `application.properties`, `openapi.json`, `GRAPH_REPORT.md`). Stage only your own files. Before regenerating `openapi.json`, run `git diff openapi.json` and keep the existing hunk.
- **Migration:** `V31__purchasing_terms.sql`.
- **Downpayment column:** `downpayment_percent numeric(5,2) not null default 0 check (downpayment_percent between 0 and 100)`.
- **Required downpayment:** `percent / 100 × PO total`, `setScale(2, HALF_UP)`.
- **Send refusal:** 409 `INVALID_STATE` with the exact text `"<number> needs a downpayment of ₱<required> before sending; ₱<paid> recorded."`. Amounts use `%,.2f`.
- **Resend:** allowed only in `SENT` and `PARTIALLY_RECEIVED`.
- **Statement label for a linked payment:** `"Downpayment – <PO number>"` (en dash).
- **OpenAPI names:** schema and operationId names must be unique (springdoc uses simple class names and controller method names). Use `PoResendResponse`, `getPurchaseOrderPdf`, `getPurchaseOrderPng` and `resendPurchaseOrder`.
- **graphify + Obsidian:** orient with `graphify query` before reading raw source. After each repo's last task, run `graphify update .` and refresh `graphify-out/obsidian` in that repo.

## Review Focus

- **Linked payment deleted after the PO was sent:** the PO stays SENT. The requirement is only checked at send time, so nothing re-locks. Covered in Task 2.
- **A downpayment of 0% or a supplier with no terms:** send behaves exactly as before, and `downpaymentRequired` is 0. Covered in Task 2.
- **Payment recorded against a CANCELLED PO or another supplier's PO:** refused with 400. Covered in Task 1.
- **Supplier with a null or blank email:** auto-email on send is silently skipped, and resend returns an empty `to`. Covered in Task 4.
- **PO with long product names or many lines:** the PNG must be full-page, not clipped to the viewport. Covered in Task 3, where the test asserts the PNG height exceeds 1123 px for 40 lines.

---

### Task 1: Downpayment percent and PO-linked payments

**Files:**
- Create: `src/main/resources/db/migration/V31__purchasing_terms.sql`
- Modify: `purchasing/Supplier.java`, `SupplierRequest.java`, `SupplierResponse.java`, `SupplierPayment.java`, `SupplierPaymentRequest.java`, `SupplierPaymentResponse.java`, `SupplierService.java` (create/update/payment/statement)
- Test: `src/test/java/ph/thecoffeejunkie/crm/purchasing/SuppliersIT.java`, `SupplierCreditIT.java`

**Interfaces:**
- Produces:
  - `SupplierRequest.downpaymentPercent` (`@DecimalMin("0") @DecimalMax("100") @Digits(integer=3, fraction=2) BigDecimal`; null means 0).
  - `SupplierResponse.downpaymentPercent` (`BigDecimal`, never null).
  - `SupplierPaymentRequest.purchaseOrderId` (`Long`, nullable).
  - `SupplierPaymentResponse` gains `Long purchaseOrderId, String purchaseOrderNumber`.
  - `SupplierPaymentRepository.sumByPurchaseOrderId(long poId): BigDecimal` (coalesced to 0).

- [ ] **Step 1: Write the failing tests**
  - `SuppliersIT.downpaymentPercentRoundTrips`: create with `downpaymentPercent: 50`, GET returns `50.00`; update to `101` gives 400.
  - `SupplierCreditIT.paymentLinkedToPoShowsOnStatement`: record `{amount: 500, purchaseOrderId: po}`. The response has `purchaseOrderNumber == po.number`, and the statement entry description is `"Downpayment – " + number`.
  - `SupplierCreditIT.paymentLinkRejectsOtherSupplierAndCancelledPo`: both give 400.
- [ ] **Step 2: Run the tests and confirm they fail:** `./gradlew test --tests '*SuppliersIT' --tests '*SupplierCreditIT'`. They should fail on unknown fields or missing columns.
- [ ] **Step 3: Implement.**
  - Migration: add the supplier column, plus `supplier_payment.purchase_order_id bigint references purchase_order(id)` and an index on it.
  - Map both fields.
  - Validation in `SupplierService.recordPayment`: the PO must exist, `po.supplierId == supplierId`, and the status must not be CANCELLED. Otherwise throw the module's existing 400 `invalid("purchaseOrderId", …)`.
  - Statement: build the label for a linked payment row from a join to `purchase_order.number`.
- [ ] **Step 4: Run the tests and confirm they pass** (same command).
- [ ] **Step 5: Commit** with message `feat(purchasing): supplier downpayment percent and PO-linked payments`.

### Task 2: Send requires the downpayment

**Files:**
- Modify: `purchasing/PurchaseOrderService.java` (`send`, `toResponses`), `PoResponse.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/purchasing/SendPurchaseOrderIT.java`

**Interfaces:**
- Consumes: `SupplierPaymentRepository.sumByPurchaseOrderId` (from Task 1).
- Produces: `PoResponse` gains `BigDecimal downpaymentRequired, BigDecimal downpaymentPaid`, placed after `total`.

- [ ] **Step 1: Write the failing tests**
  - `sendRefusedUntilDownpaymentPaid`: supplier at 50%, limit 2,500,000, PO total 1,000.00.
    - Send gives 409, and the message contains `"needs a downpayment of ₱500.00"`.
    - Record 500 linked to the PO; send gives 200 with `status == SENT`.
    - The supplier's `credit.available == 2_499_500.00` (limit − (−500) − 1000 = limit − 500).
  - `zeroPercentSendsAsBefore`: with no term, `downpaymentRequired == 0` and send gives SENT.
  - `deletingDownpaymentAfterSendKeepsSent`: delete the linked payment and the PO stays SENT.
  - `responseShowsRequiredAndPaid`: a 33.33% term on a total of 100.01 gives `downpaymentRequired == 33.33`.
- [ ] **Step 2: Run the tests and confirm they fail:** `./gradlew test --tests '*SendPurchaseOrderIT'`.
- [ ] **Step 3: Implement.**
  - In `send`, after the archived-supplier check and before `check(po)`: compute required with the Global Constraints rule. If paid < required, throw `DomainException(ErrorCode.INVALID_STATE, …)` with the exact text from Global Constraints.
  - `toResponses` fills both fields, batching the supplier percentages and payment sums per page rather than per row.
- [ ] **Step 4: Run the tests and confirm they pass**, plus `*PurchaseOrdersIT` and `*ReceivePurchaseOrderIT`. Those existing tests construct `PoResponse` usage only via JSON, so they should still pass.
- [ ] **Step 5: Commit** with message `feat(purchasing): block send until downpayment recorded`.

### Task 3: PO document as PDF and PNG

**Files:**
- Modify: `shared/PdfRenderer.java`
- Create: `purchasing/PurchaseOrderDocument.java`, `src/main/resources/templates/pdf/purchase-order.html`
- Modify: `purchasing/PurchaseOrderController.java`, `SecurityMatrixTest.java`, `tailwind/` build output (`templates/pdf/tailwind.css`)
- Test: `src/test/java/ph/thecoffeejunkie/crm/purchasing/PurchaseOrderDocumentIT.java`

**Interfaces:**
- Produces:
  - `PdfRenderer.renderPng(String template, Map<String,Object> vars): byte[]`.
  - `PurchaseOrderDocument.pdf(long poId): byte[]` and `png(long poId): byte[]`, both reading in their own read-only transaction.
  - `PurchaseOrderService.documentFor(long id)` checks read access the same way `get(id)` does (WAREHOUSE needs `canAct` on the PO warehouse).
  - Endpoints `GET /api/purchase-orders/{id}.pdf` (`getPurchaseOrderPdf`) and `GET /api/purchase-orders/{id}.png` (`getPurchaseOrderPng`). Both take `@RequestParam(defaultValue="false") boolean download`. Content-Disposition is `attachment` when that is true and `inline` otherwise, with filename `<number>.pdf` or `<number>.png`.

- [ ] **Step 1: Write the failing tests in `PurchaseOrderDocumentIT`**
  - `pdfStartsWithMagic`: the body starts with `%PDF`.
  - `pdfContainsNumberSupplierAndTerms`: extract text with pdfbox. It contains the PO number, the supplier name and `"50% downpayment"`.
  - `pngIsFullPage`: the body starts with `\x89PNG`. For a 40-line PO, `ImageIO.read(...).getHeight() > 1123`.
  - `downloadSetsAttachment`: `?download=true` gives a header starting with `attachment`.
  - `salesCannotRead`: SALES gets 403. A WAREHOUSE user without access to that warehouse gets 404 (the same behaviour as `get`).
- [ ] **Step 2: Run the tests and confirm they fail:** `./gradlew test --tests '*PurchaseOrderDocumentIT'`.
- [ ] **Step 3: Implement.**
  - **`renderPng`:** share the template-processing code with `render`. Inside the same `synchronized` browser block, set viewport `794×1123`, then `page.screenshot(new Page.ScreenshotOptions().setFullPage(true))`.
  - **Template:** copy `quote.html`'s structure. Use `fragments/parts.html` `letterhead('PURCHASE ORDER', number)` and the `company` fragment, and `QuoteDocument.company(settings)` logic (duplicate the three-setting map; it is package-private in sales).
    - Blocks: supplier block, deliver-to warehouse name, expected date, lines table, total.
    - Terms box text:
      - percent > 0: `"<pct>% downpayment ₱<required> (paid ₱<paid>)"`.
      - payment-terms days set: `"balance on <n>-day credit"`.
      - Join with ` · ` and omit the box when both are empty.
    - A `DRAFT` tag when the status is DRAFT; notes at the end.
  - Run `npm run build` in `tailwind/`.
  - Add both endpoints to `SecurityMatrixTest` as `AW`.
- [ ] **Step 4: Run the tests and confirm they pass**, plus `*SecurityMatrixTest`.
- [ ] **Step 5: Commit** with message `feat(purchasing): PO document as PDF and PNG`.

### Task 4: Email the PO to the supplier on send, plus resend

**Files:**
- Create: `purchasing/PurchasingMailer.java`, `src/main/resources/templates/mail/po-sent.html`, `purchasing/PoResendResponse.java`
- Modify: `audit/AuditAction.java` (`PO_SENT`), `PurchaseOrderService.java` (`send`, `decided`, new `resend`), `PurchaseOrderController.java`, `SecurityMatrixTest.java`, `openapi.json`
- Test: `PurchaseOrderDocumentIT.java` (annotate the mail tests' class with `@MailIntegrationTest`, or put them in `PurchaseOrderMailIT` if the context differs)

**Interfaces:**
- Consumes: `PurchaseOrderDocument.pdf(long)` from Task 3.
- Produces:
  - `PurchasingMailer.poAfterCommit(List<String> to, String number, String supplierName, Supplier<byte[]> pdf)`. Subject: `"Purchase order <number> from <company>"`. Template: `po-sent`. Attachment: `<number>.pdf`.
  - `record PoResendResponse(List<String> to)`.
  - `POST /api/purchase-orders/{id}/resend` (`resendPurchaseOrder`), ADMIN.

- [ ] **Step 1: Write the failing tests**
  - `sendEmailsSupplier`: supplier email `s@x.ph`. After send, `JavaMailSender.send` is invoked with one message to `s@x.ph`, carrying a PDF attachment. Await as the existing mail ITs do.
  - `approvalEmailsSupplier`: an over-limit PO is approved and the email is sent.
  - `resendDraftIs409`.
  - `resendWithoutEmailReturnsEmpty`: `to == []` and no mail is sent.
  - `resendAudits`: an audit row has action `PO_SENT` and the text `"Sent PO <number> to s@x.ph"`.
- [ ] **Step 2: Run the tests and confirm they fail.**
- [ ] **Step 3: Implement.**
  - `PurchasingMailer` mirrors `SalesMailer.invoiceAfterCommit`.
  - `po-sent.html` mirrors `invoice-sent.html`.
  - A private `emailSupplier(PurchaseOrder)` is called from `send` (SENT branch), `decided` (approved branch) and `resend`. It returns `[]` for a null or blank email and audits otherwise.
  - Add the resend row to `SecurityMatrixTest` as `A`.
- [ ] **Step 4: Run the tests and confirm they pass.** Then run `./gradlew test --tests '*OpenApiSnapshotTest' -DupdateSnapshot=true`, check that `git diff openapi.json` keeps the existing unrelated hunk, and run the full purchasing package plus `*SecurityMatrixTest` and `*OpenApiSnapshotTest`.
- [ ] **Step 5: Commit** your files and `openapi.json`. If the unrelated hunk can't be separated, leave `openapi.json` unstaged and report it. Commit message: `feat(purchasing): email PO to supplier on send and resend`.
- [ ] **Step 6:** In the backend, run `graphify update .` and refresh the Obsidian export.

### Task 5: Frontend supplier edit, downpayment field and linked payments

**Files:**
- Modify: `src/api/schema.d.ts` (generated), `src/features/purchasing/SuppliersPage.tsx`, `SupplierDrawer.tsx`, `PaymentDialog.tsx`, `SupplierPage.tsx` (payments list shows the PO number), `api.ts` if types need re-exporting
- Test: `SuppliersPage.test.tsx`, `SupplierPage.test.tsx`

**Interfaces:**
- Consumes: the Task 1 and Task 4 API.
- Produces: `PaymentDialog` props `{supplierId: number; purchaseOrderId?: number; defaultAmount?: number; title?: string; onClose(): void}`. With a `purchaseOrderId`, the request carries it and the title defaults to "Record downpayment".

- [ ] **Step 1:** Run `OPENAPI_PATH=../crm/crm/openapi.json npm run gen:api`. The path must be relative.
- [ ] **Step 2: Write the failing tests**
  - `SuppliersPage`: clicking a row's "Edit" opens the drawer with that supplier's name filled in.
  - `SupplierDrawer`: submitting with Downpayment % `50` sends `downpaymentPercent: 50`; `120` shows a validation error.
- [ ] **Step 3: Run the tests and confirm they fail:** `npx vitest run src/features/purchasing`.
- [ ] **Step 4: Implement.**
  - Edit action on each row, placed like other list row actions in the app.
  - Drawer field labelled "Downpayment %" with the hint "Paid before the PO is sent".
  - `PaymentDialog` props as above.
  - Supplier payments list shows "Downpayment – PO-…" when linked.
- [ ] **Step 5: Run the tests and confirm they pass**, plus `npx tsc -b`.
- [ ] **Step 6: Commit** with message `feat(purchasing): edit suppliers from list, downpayment terms`.

### Task 6: Frontend PO page document actions and downpayment gate

**Files:**
- Modify: `src/features/purchasing/api.ts`, `PurchaseOrderPage.tsx`, `CreditCheckPanel.tsx`
- Test: `PurchaseOrderPage.test.tsx`

**Interfaces:**
- Consumes: `PaymentDialog` from Task 5, and `apiUrl` from `features/sales/api.ts` (or its shared source).
- Produces, in `api.ts`:
  - `poPdfUrl(id, download=false)` and `poPngUrl(id, download=false)`.
  - `resendPurchaseOrder(id): Promise<PoResendResponse>`.

- [ ] **Step 1: Write the failing tests**
  - `sendDisabledUntilDownpayment`: with `downpaymentRequired: 500, downpaymentPaid: 0`, Send is disabled and "Record downpayment" is shown. With paid 500, Send is enabled.
  - `documentLinks`: View has an href ending `.pdf`; Download PDF ends `.pdf?download=true`; Download image ends `.png?download=true`.
  - `resendToastNoEmail`: resend returning `{to: []}` shows "This supplier has no email, so nothing was sent."
  - `resendHiddenOnDraft`.
- [ ] **Step 2: Run the tests and confirm they fail.**
- [ ] **Step 3: Implement.**
  - View, Download PDF and Download image are plain links (the browser carries the cookie), visible for every status.
  - Resend is shown for ADMIN on SENT and PARTIALLY_RECEIVED. Success toast: `"<number> emailed to <to.join(", ")>."`.
  - "Record downpayment" opens `PaymentDialog` with `purchaseOrderId`, `defaultAmount = required − paid` and the success invalidating PO and supplier queries.
  - `CreditCheckPanel` gets a line `"Downpayment ₱X required, ₱Y recorded"` when required > 0.
- [ ] **Step 4: Run the tests and confirm they pass**, then the full `npx vitest run` and `npx tsc -b`.
- [ ] **Step 5: Commit** with message `feat(purchasing): PO document actions and downpayment gate`.
- [ ] **Step 6:** In the frontend, run `graphify update .` and refresh the Obsidian export.
