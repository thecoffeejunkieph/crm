# Per-Category Sales Targets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An admin can set a monthly net-sales target per product category for each sales rep. The rep sees a progress bar for each one and gets 50/80/100% milestone notifications. Commission is unchanged.

**Architecture:** A new `sales_category_target` table lives in the `insights` module next to `sales_target`. CRUD goes in the existing `TargetService`/`TargetController`, so the cache-eviction, rep-check and audit code is reused. Category net sales is one new `InsightsQueries` method: the existing `CREDIT` split per line with `LINE_NET`, grouped by `product.category_id`. The frontend adds an expandable per-rep editor on the targets page and a card on the sales dashboard.

**Tech Stack:** Spring Boot, JPA plus `NamedParameterJdbcTemplate`, Flyway, Testcontainers ITs (backend `crm/crm`). React, TanStack Query, Vitest and Testing Library (frontend `../crm-dashboard-ui`).

**Spec:** `docs/superpowers/specs/2026-11-23-category-targets-design.md`

## Global Constraints

- Backend Gradle from Claude's shell: `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/Users/Public/gtmp ./gradlew --no-daemon test --tests '<pattern>'`. If `build/` is locked by a running dev server, add `-I` with an init script setting `allprojects { layout.buildDirectory = file('C:/Users/Public/gbuild') }`.
- Next migration number is `V29` (latest is `V28__customer_self_service.sql`).
- Category net sales must use `InsightsQueries.CREDIT`'s numerator `PAID` times `LINE_NET / o.total`, so that summed over all lines it equals `CREDIT`.
- Money is `numeric(19,2)`, so request amounts need `@Digits(integer = 17, fraction = 2)`. Results are scaled with `InsightsQueries.money(...)`.
- Notification entity type stays `"SALES_TARGET"`, so `NotificationBell` links need no change.
- Audit action stays `AuditAction.SALES_TARGET_SET`. The entity type is `"SALES_CATEGORY_TARGET"`.
- Month label format in audit text: `TargetService.MONTH` (`"MMM yyyy"`). Month name in notifications: `TextStyle.FULL`, English.
- After the backend API change: `./gradlew --no-daemon test --tests '*OpenApiSnapshotTest' -DupdateSnapshot=true`, then `npm run gen:api` in `../crm-dashboard-ui`.
- `ModularityTest` already fails on the baseline. Don't treat that as a regression.
- The workspace has unrelated uncommitted edits (`docker-compose.prod.yml`, `application.properties`, `graphify-out/*`). Stage only the files each task names.

## Review Focus

1. **Order with an uncategorized line:** only the categorized lines count toward category targets, the uncategorized line's share goes nowhere, and the overall net sales is unchanged. Test: Task 2 `uncategorizedLineCountsTowardNoCategory`.
2. **Refund paid in the month:** it reduces each category in the same proportion as the overall net sales. Test: Task 2 `refundReducesCategoriesProRata`.
3. **Category archived after its target was set:** the dashboard still shows its progress, the admin list still shows it, and it can be cleared but not changed. Tests: Task 1 `archivedCategoryWithTargetStaysListedAndClearable` and Task 2 `archivedCategoryStillShowsProgress`.
4. **A target saved while the dashboard is cached (60s Redis):** the rep sees the new category bar on the next load. Test: Task 2 `categoryTargetWriteShowsOnNextLoad`.
5. **A milestone crossed on a category when the rep has no overall target:** the category notification is still sent, because the early return must not skip it. Test: Task 3 `categoryMilestoneWithoutOverallTarget`.

---

### Task 1: Category targets table and admin CRUD API

**Files:**
- Create: `src/main/resources/db/migration/V29__sales_category_target.sql`
- Create: `src/main/java/ph/thecoffeejunkie/crm/insights/SalesCategoryTarget.java`
- Create: `src/main/java/ph/thecoffeejunkie/crm/insights/SalesCategoryTargetRepository.java`
- Create: `src/main/java/ph/thecoffeejunkie/crm/insights/CategoryTargetRow.java`
- Create: `src/main/java/ph/thecoffeejunkie/crm/insights/CategoryTargetRequest.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/insights/InsightsQueries.java` (category lookups)
- Modify: `src/main/java/ph/thecoffeejunkie/crm/insights/TargetService.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/insights/TargetController.java`
- Modify: `src/test/java/ph/thecoffeejunkie/crm/insights/InsightsFixture.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/insights/CategoryTargetsIT.java`

**Interfaces:**
- Produces:
  - Table `sales_category_target`, with the DDL copied verbatim from the spec's Schema section.
  - `class SalesCategoryTarget` (JPA, `@Table(name = "sales_category_target")`) with package-private fields shaped like `SalesTarget`: `Long id; long repId; LocalDate month; long categoryId; BigDecimal targetAmount; Instant createdAt, updatedAt; long updatedBy; @Version long version`.
  - `interface SalesCategoryTargetRepository extends JpaRepository<SalesCategoryTarget, Long>` with `List<SalesCategoryTarget> findByRepIdAndMonth(long repId, LocalDate month)`, `Optional<SalesCategoryTarget> findByRepIdAndMonthAndCategoryId(long repId, LocalDate month, long categoryId)` and `List<SalesCategoryTarget> findByMonth(LocalDate month)`.
  - `record CategoryTargetRow(long categoryId, String categoryName, boolean archived, BigDecimal targetAmount, Long version) implements Serializable`
  - `record CategoryTargetRequest(@NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal targetAmount, Long version)`
  - `InsightsQueries.record Category(long id, String name, boolean archived)`
  - `InsightsQueries.List<Category> categories(Collection<Long> alsoInclude)`: active categories plus `alsoInclude`, sorted by `lower(name)`.
  - `InsightsQueries.Optional<Category> category(long id)`
  - `TargetService.List<CategoryTargetRow> categoryTargets(long repId, YearMonth m)`: rows for every active category plus every category targeted that month. Read-only, with no rep check, so past reps still list.
  - `TargetService.CategoryTargetRow setCategoryTarget(long repId, YearMonth m, long categoryId, CategoryTargetRequest req)`
  - `TargetService.void clearCategoryTarget(long repId, YearMonth m, long categoryId, long version)`
  - Endpoints, all under the class-level `hasRole('ADMIN')`:
    - `GET /api/insights/targets/{repId}/{month}/categories`
    - `PUT .../categories/{categoryId}`
    - `DELETE .../categories/{categoryId}?version=` returning 204
  - Every endpoint parses the month with `InsightsMonth.parseForPlanning`.
  - Fixture helpers: `long category(String name)` (name prefixed with `tag`), `void archiveCategory(long id)` and `void categorize(long productId, long categoryId)`. `cleanUp()` also deletes `sales_category_target` rows for tagged users (`rep_id` or `updated_by`) before `app_user`, and deletes `product_category` rows whose name starts with `tag` after `product`.

- [ ] **Step 1: Write the failing tests** in `CategoryTargetsIT` (`@IntegrationTest`, seeded like `TargetsIT`: an admin, `rep` a SALES user, categories `coffee` and `tea`). Use a helper `putCat(rep, m, catId, amount, version)` that builds the JSON `{"targetAmount":…,"version":…}`.
  - `listShowsActiveCategoriesWithNulls`: GET for `now` returns 200, and the `coffee` row has `categoryName == f.tag + "coffee"`, `targetAmount == null`, `version == null` and `archived == false`. An archived category without a target is absent.
  - `putCreatesThenUpdatesWithVersion`: a PUT of `30000.00` with a null version returns 200 with `targetAmount == 30000.00` and `version == 0`. A PUT of `35000.00` with version 0 returns 200 with `version == 1`. The GET then shows `35000.00`.
  - `staleOrMissingVersionIs409`: on an existing row, a PUT with a null version returns 409 and a PUT with version 7 returns 409. With no row, a PUT with version 0 returns 409. Each body has `code == "STALE_VERSION"`.
  - `zeroOrNegativeAmountIs400`: `0` returns 400 and `-1` returns 400.
  - `unknownCategoryIs404`: a PUT to category id `Long.MAX_VALUE` returns 404.
  - `archivedCategoryIs422`: `f.archiveCategory(tea)`, then a PUT returns 422.
  - `nonRepIs422`: a PUT for the admin's own id returns 422 with `code == "INSIGHTS_NOT_A_REP"`.
  - `archivedCategoryWithTargetStaysListedAndClearable`: set the target, archive the category, then the GET still lists it with `archived == true` and its amount. A PUT returns 422, `DELETE ?version=0` returns 204, and after that the row is absent from the GET.
  - `deleteStaleIs409AndMissingIs404`: a DELETE with the wrong version returns 409. A DELETE with no row returns 404.
  - `salesUserIs403`: a GET as `rep` with `Role.SALES` returns 403.
  - `writeIsAudited`: after a PUT, `select summary from audit_log where entity_type = 'SALES_CATEGORY_TARGET' order by id desc limit 1` equals `"Set " + <rep display name> + "'s " + f.tag + "coffee target for " + <MMM yyyy> + " to 30000.00"`. Check `AuditLog.record`'s column name in `AuditLog.java` before writing this query.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew --no-daemon test --tests '*CategoryTargetsIT'` (with the `JAVA_TOOL_OPTIONS` from Global Constraints)
Expected: FAIL. The migration and endpoints are missing, so the requests return 404 or 500.

- [ ] **Step 3: Implement the migration, entity, repository, records and queries**, with the signatures from Interfaces. `categories(...)`: `select id, name, archived_at is not null archived from product_category where archived_at is null or id in (:ids) order by lower(name)`. Guard an empty `ids` the way the existing queries in the file do.

- [ ] **Step 4: Implement the `TargetService` methods.**
  - `setCategoryTarget` validates in this order:
    1. The rep must be active SALES, else `INSIGHTS_NOT_A_REP`.
    2. `q.category(id)` must exist, else `DomainException(ErrorCode.NOT_FOUND, "That category doesn't exist.")`.
    3. The category must not be archived, else `DomainException(ErrorCode.VALIDATION_FAILED, "That category is archived.")`.
    4. Check the version exactly as `set` does.
  - After `saveAndFlush`, call `evictDashboardsAfterCommit()` and audit with the spec's text.
  - `clearCategoryTarget`:
    - No row: `NOT_FOUND`, `"That target doesn't exist."`.
    - Version mismatch: `STALE_VERSION`.
    - Otherwise delete the row, evict the cache and audit `"Cleared " + name + "'s " + category + " target for " + month`.
  - Inject `InsightsQueries` and `SalesCategoryTargetRepository` through the constructor.

- [ ] **Step 5: Add the three controller methods.** The DELETE has `@ResponseStatus(HttpStatus.NO_CONTENT)` and `@RequestParam long version`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew --no-daemon test --tests '*CategoryTargetsIT' --tests '*TargetsIT'`
Expected: PASS (`TargetsIT` unchanged).

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/db/migration/V29__sales_category_target.sql src/main/java/ph/thecoffeejunkie/crm/insights/{SalesCategoryTarget,SalesCategoryTargetRepository,CategoryTargetRow,CategoryTargetRequest,InsightsQueries,TargetService,TargetController}.java src/test/java/ph/thecoffeejunkie/crm/insights/{InsightsFixture,CategoryTargetsIT}.java
git commit -m "feat(insights): per-category sales targets API"
```

### Task 2: Category progress on the rep dashboard (backend)

**Files:**
- Modify: `src/main/java/ph/thecoffeejunkie/crm/insights/InsightsQueries.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/insights/SalesInsights.java`
- Modify: `src/main/java/ph/thecoffeejunkie/crm/insights/InsightsService.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/insights/SalesInsightsIT.java`

**Interfaces:**
- Consumes: `TargetService.categoryTargets(long, YearMonth)` and fixture `category`/`categorize`/`archiveCategory` (Task 1).
- Produces:
  - `InsightsQueries.Map<Long, BigDecimal> netSalesByCategory(long repId, YearMonth m, Long excludePaymentId)`: keys are category ids, with uncategorized lines dropped and values `money(...)`-scaled. A null `excludePaymentId` excludes nothing.
  - `SalesInsights.CategoryProgress(long categoryId, String name, BigDecimal target, BigDecimal netSales, Integer progressPct, BigDecimal remaining, boolean hit) implements Serializable`
  - A new last component on `SalesInsights`, `List<CategoryProgress> categories`: only categories with a target, in the order `categoryTargets` returns, with zeros for categories with no sales.

- [ ] **Step 1: Write the failing tests** in `SalesInsightsIT`. Seed with fixture `order` total `1000.00` (no VAT or shipping, so `LINE_NET == line_total`), lines `coffeeProduct 600.00` and `teaProduct 400.00`, and a VERIFIED payment of `500.00` this month. Set category targets by direct insert into `sales_category_target`, the way the existing `target(...)` helper does.
  - `categoryProgressSplitsPaymentByLine`: with targets coffee `600.00` and tea `1000.00`, `$.categories[0]` is coffee with `netSales 300.00`, `progressPct 50`, `remaining 300.00` and `hit false`. `$.categories[1]` is tea with `200.00` and `20`. `$.netSales == 500.00` is unchanged.
  - `categoriesEmptyWithoutCategoryTargets`: `$.categories` has length 0.
  - `untargetedCategoryOmitted`: with only a coffee target, `$.categories.length() == 1`.
  - `uncategorizedLineCountsTowardNoCategory`: the tea product is left uncategorized. With a coffee target `300.00`, coffee is `300.00`, `progressPct 100` and `hit true`, and `$.netSales == 500.00`.
  - `refundReducesCategoriesProRata`: add `f.refund(order, "100.00", <this month>)`. Coffee is `240.00` and tea is `160.00`.
  - `archivedCategoryStillShowsProgress`: set a coffee target, `f.archiveCategory(coffee)`, and coffee is still listed at `300.00`.
  - `categoryTargetWriteShowsOnNextLoad`: GET the dashboard (warming the cache), PUT a coffee target as admin through the Task 1 endpoint, and GET again. `$.categories.length() == 1`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew --no-daemon test --tests '*SalesInsightsIT'`
Expected: FAIL with `No value at JSON path "$.categories"`.

- [ ] **Step 3: Implement `netSalesByCategory`:**

```sql
select pr.category_id, sum((PAID) * LINE_NET / o.total) net from money_movement p
join sales_order o on o.id = p.order_id join order_line l on l.order_id = o.id join product pr on pr.id = l.product_id
where o.total > 0 and pr.category_id is not null and p.at >= :start and p.at < :end [and p.id <> :exclude] + rep(repId, p)
group by pr.category_id
```

Use the existing `PAID`, `LINE_NET` and `rep(...)` fragments.

- [ ] **Step 4: Add `CategoryProgress` and the `categories` component.** In `InsightsService.sales`, build the list from `targets.categoryTargets(userId, m)` filtered to `targetAmount() != null`. Use `Commission.progressPct`, `Commission.remaining` and `Commission.hit` with `netSalesByCategory(...).getOrDefault(id, Commission.ZERO)`. Update every other `new SalesInsights(...)` call site that the compiler flags.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew --no-daemon test --tests '*SalesInsightsIT' --tests '*AdminInsightsIT'`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ph/thecoffeejunkie/crm/insights/{InsightsQueries,SalesInsights,InsightsService}.java src/test/java/ph/thecoffeejunkie/crm/insights/SalesInsightsIT.java
git commit -m "feat(insights): category target progress on the sales dashboard"
```

### Task 3: Category milestone notifications

**Files:**
- Modify: `src/main/java/ph/thecoffeejunkie/crm/insights/TargetMilestones.java`
- Test: `src/test/java/ph/thecoffeejunkie/crm/insights/TargetMilestonesIT.java`

**Interfaces:**
- Consumes: `TargetService.categoryTargets` (Task 1) and `InsightsQueries.netSalesByCategory` (Task 2).

- [ ] **Step 1: Write the failing tests** in `TargetMilestonesIT`. The existing `order` is `100000.00`. Give it one line, a `coffee` product with `line_total 100000.00`, so that coffee net equals order net. Add a `catTarget(String amount)` insert helper, and delete from `sales_category_target` in `cleanUp`.
  - `categoryCrossingSendsCategoryTitle`: with a coffee target `1000.00`, already `400.00`, verify `450.00`. `titles()` contains exactly `"You're at 80% of your " + f.tag + "coffee target for November"`, and the body is `"850.00 of 1000.00"`.
  - `categoryHitTitle`: already `950.00`, verify `50.00`. The title is `f.tag + "coffee target hit for November"`.
  - `categoryMilestoneWithoutOverallTarget`: as in the first test with no `target(...)`, exactly one title arrives.
  - `overallAndCategoryBothCross`: with an overall target `1000.00` and a coffee target `2000.00`, already `400.00`, verify `1200.00`. `titles()` contains exactly `"Target hit for November"` and then `"You're at 80% of your " + f.tag + "coffee target for November"`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew --no-daemon test --tests '*TargetMilestonesIT'`
Expected: FAIL. No category titles are sent.

- [ ] **Step 3: Restructure `on(PaymentVerified)`.** Keep the `repId == null` return. Move the overall-target logic into `private void overall(...)`, which returns early on its own. Then add `private void categories(...)`: for each `categoryTargets` row with a target, call `crossed(progressPct(before), progressPct(after))` using `netSalesByCategory(rep, m, paymentId)` for before and `netSalesByCategory(rep, m, null)` for after. Each query runs once per event. Send with the spec's titles, entity type `"SALES_TARGET"` and body `after.toPlainString() + " of " + target.toPlainString()`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew --no-daemon test --tests '*TargetMilestonesIT'`
Expected: PASS, the existing tests included.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ph/thecoffeejunkie/crm/insights/TargetMilestones.java src/test/java/ph/thecoffeejunkie/crm/insights/TargetMilestonesIT.java
git commit -m "feat(insights): milestone notifications for category targets"
```

### Task 4: Copy from last month includes category targets

**Files:**
- Modify: `src/main/java/ph/thecoffeejunkie/crm/insights/TargetService.java` (`copy`)
- Modify: `src/main/java/ph/thecoffeejunkie/crm/insights/TargetController.java` (`copy` response)
- Test: `src/test/java/ph/thecoffeejunkie/crm/insights/CategoryTargetsIT.java`

**Interfaces:**
- Produces:
  - `record CopyResult(int copied, int categoriesCopied)`, nested in `TargetService`, returned by `TargetService.copy(YearMonth, YearMonth)`.
  - The controller returns `Map.of("copied", r.copied(), "categoriesCopied", r.categoriesCopied())`.

- [ ] **Step 1: Write the failing test** `copyAlsoCopiesCategoryTargets`:
  - In `prev`, `rep` has coffee `500.00`, tea `700.00` and `archivedCat` `100.00` (archived after insert). `inactiveRep` has coffee `900.00`, then that user is deactivated the way `TargetsIT`'s copy test does it.
  - In `now`, `rep` has tea `50.00`.
  - `POST /api/insights/targets/copy?from=prev&to=now` returns 200 with `$.categoriesCopied == 1`.
  - In `now`, `rep` has coffee `500.00`, and tea is still `50.00`. There's no row for `archivedCat` and no row for `inactiveRep`.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew --no-daemon test --tests '*CategoryTargetsIT.copyAlsoCopiesCategoryTargets'`
Expected: FAIL with `No value at JSON path "$.categoriesCopied"`.

- [ ] **Step 3: Extend `copy`.** After the overall loop, copy each `catRepo.findByMonth(from)` row whose (rep, category) pair is missing in `to`, whose rep is active SALES and whose category is not archived (`q.category(id)`). Extend the audit summary to `"Copied N targets and K category targets from … to …"`. Update `TargetsIT` if it asserts the old summary or response shape.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew --no-daemon test --tests '*CategoryTargetsIT' --tests '*TargetsIT'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ph/thecoffeejunkie/crm/insights/{TargetService,TargetController}.java src/test/java/ph/thecoffeejunkie/crm/insights/{CategoryTargetsIT,TargetsIT}.java
git commit -m "feat(insights): copy category targets from last month"
```

### Task 5: Admin editor for category targets (frontend)

**Files:**
- Modify: `openapi.json` (snapshot regen)
- Modify (frontend repo `../crm-dashboard-ui`): `src/api/schema.d.ts` (generated), `src/features/insights/api.ts`, `src/features/insights/SalesTargetsPage.tsx`, `src/features/insights/SalesTargetsPage.test.tsx`
- Create: `../crm-dashboard-ui/src/features/insights/CategoryTargets.tsx`
- Test: `../crm-dashboard-ui/src/features/insights/CategoryTargets.test.tsx`

**Interfaces:**
- Consumes: the Task 1 endpoints and the Task 4 `{copied, categoriesCopied}`.
- Produces in `api.ts`:
  - `type CategoryTargetRow`, taken from the generated schema like `TargetRow`.
  - `fetchCategoryTargets(repId: number, month: string): Promise<CategoryTargetRow[]>`
  - `saveCategoryTarget(repId: number, month: string, categoryId: number, body: {targetAmount: number; version?: number}): Promise<CategoryTargetRow>`
  - `clearCategoryTarget(repId: number, month: string, categoryId: number, version: number): Promise<void>`
  - `insightsKeys.categoryTargets(repId, month)`, under the `["insights", ...]` prefix so that `reload`'s invalidation covers it.
  - `copyTargets` now returns `{copied: number; categoriesCopied: number}`.
- Produces: `export default function CategoryTargets({repId, repName, month}: {repId: number; repName: string; month: string})`

- [ ] **Step 1: Regenerate the contract.** Run `./gradlew --no-daemon test --tests '*OpenApiSnapshotTest' -DupdateSnapshot=true` in `crm/crm`, then `npm run gen:api` in `../crm-dashboard-ui`. Expected: `schema.d.ts` contains `"/api/insights/targets/{repId}/{month}/categories/{categoryId}"`.

- [ ] **Step 2: Write the failing tests.** Mock `./api` as `SalesTargetsPage.test.tsx` does, with the rows coffee (`targetAmount 30000`, `version 2`) and tea (null).
  - `CategoryTargets.test.tsx`:
    - "lists every category, empty when unset": the inputs labelled `Coffee target for Sam Reyes` have the value `30000`, and `Tea target for Sam Reyes` has `""`.
    - "saves with the row's version": type `35000` into Coffee and click Save in that row. `saveCategoryTarget` is called with `(7, "2026-11", <coffeeId>, {targetAmount: 35000, version: 2})`.
    - "clears a set target": a Clear button shows only on the coffee row. Clicking it calls `clearCategoryTarget(7, "2026-11", <coffeeId>, 2)`.
    - "blocks zero or empty": Save is disabled for `""` and for `0`.
    - "reloads on 409": a rejected save with status 409 shows `role=status` with the text `Someone else changed this target. Reloaded.`
    - "marks archived categories": a row with `archived: true` shows `(archived)`, and its input is disabled.
  - `SalesTargetsPage.test.tsx`:
    - "expands a rep's category targets": clicking the `Categories for Sam Reyes` button sets `aria-expanded` to `true` and calls `fetchCategoryTargets(7, month)`.
    - Update "copies last month's targets": the mock returns `{copied: 2, categoriesCopied: 5}`, and the status reads `Copied 2 targets and 5 category targets`.

- [ ] **Step 3: Run them to verify they fail**

Run: `npx vitest run src/features/insights/CategoryTargets.test.tsx src/features/insights/SalesTargetsPage.test.tsx` (in `../crm-dashboard-ui`)
Expected: FAIL, because `./CategoryTargets` can't be resolved.

- [ ] **Step 4: Implement `CategoryTargets.tsx`.** Copy `SalesTargetsPage`'s `Row` pattern: `MoneyInput`, `Button`, `useMutation`, 409 handling and `toastError`. Use a small table with the columns Category, Target (₱) and an actions cell. The note shows above the table. Then modify `SalesTargetsPage`:
  - Add a `Categories` toggle button (`aria-expanded`, `aria-label="Categories for {name}"`) in each `Row`'s actions cell.
  - When the toggle is open, render a following `<tr><td colSpan={4}><CategoryTargets …/></td></tr>`. That makes `Row` return a fragment.
  - Change the copy note to `Copied {copied} target(s) and {categoriesCopied} category target(s)`, keeping the existing singular/plural rule.

- [ ] **Step 5: Run the tests, typecheck and lint**

Run: `npx vitest run src/features/insights && npx tsc -b && npm run lint` (in `../crm-dashboard-ui`)
Expected: PASS, with no type or lint errors.

- [ ] **Step 6: Commit both repos**

```bash
git add openapi.json && git commit -m "chore(api): snapshot for category targets"
cd ../../crm-dashboard-ui && git add src/api/schema.d.ts src/features/insights/{api.ts,CategoryTargets.tsx,CategoryTargets.test.tsx,SalesTargetsPage.tsx,SalesTargetsPage.test.tsx}
git commit -m "feat(insights): edit per-category targets per rep"
```

### Task 6: Category progress card on the sales dashboard (frontend)

**Files:**
- Create: `../crm-dashboard-ui/src/features/insights/CategoryTargetsCard.tsx`
- Modify: `../crm-dashboard-ui/src/features/insights/SalesDashboard.tsx` (render it below `<TargetCard/>`)
- Test: `../crm-dashboard-ui/src/features/insights/CategoryTargetsCard.test.tsx`

**Interfaces:**
- Consumes: `SalesInsights["categories"]` from the regenerated schema (Task 5, Step 1).
- Produces: `export default function CategoryTargetsCard({categories}: {categories: SalesInsights["categories"]})`, which returns `null` when the list is empty.

- [ ] **Step 1: Write the failing tests** in `CategoryTargetsCard.test.tsx`, with the rows coffee (`target 600`, `netSales 300`, `progressPct 50`, `remaining 300`, `hit false`) and tea (`target 100`, `netSales 150`, `progressPct 150`, `remaining 0`, `hit true`).
  - "renders a bar per category": `getByRole("progressbar", {name: "Coffee net sales against target"})` has `aria-valuenow="50"`, and the text `₱300.00 of ₱600.00` and `₱300.00 to go` is shown.
  - "caps the bar and marks a hit": the tea bar has `aria-valuenow="100"`, the fill style width is `100%`, and the `Hit` badge and the text `150%` are shown.
  - "renders nothing without category targets": `container` is empty for `categories={[]}`.

- [ ] **Step 2: Run them to verify they fail**

Run: `npx vitest run src/features/insights/CategoryTargetsCard.test.tsx`
Expected: FAIL, because the module can't be resolved.

- [ ] **Step 3: Implement the card.** It's a `section aria-label="Category targets"` with a heading `Category targets` and one row per category. Reuse `TargetCard`'s progressbar markup and `formatCurrency`. Render it in `SalesDashboard` right after `<TargetCard data={data}/>` as `<CategoryTargetsCard categories={data.categories}/>`.

- [ ] **Step 4: Run the tests, typecheck and lint**

Run: `npx vitest run src/features/insights && npx tsc -b && npm run lint`
Expected: PASS

- [ ] **Step 5: Commit, then refresh the graphs**

```bash
git add src/features/insights/{CategoryTargetsCard.tsx,CategoryTargetsCard.test.tsx,SalesDashboard.tsx}
git commit -m "feat(insights): category target progress card"
```

Then run `graphify update .` in both repos.
