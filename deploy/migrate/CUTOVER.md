# Production cutover (P5-2)

Moves production from the old MySQL system to the new one. The old MySQL database and its upload volume are only read,
so until step 8 the way back is simply to start the old API again. Run this in a quiet window; expect about 30 minutes.

Replace `<old-mysql>` with the old database container's name, and `<new-network>` with the new stack's Docker network.

## Before the window

- [ ] Dry-run against a recent production copy (steps 3 to 6) and read the "Review by hand" list.
- [ ] Build and test the new Docker image. PDF generation needs its Chromium.
- [ ] Check with the accountant: old invoices migrate as VAT-exempt with VAT 0, and new SI- serials continue after the
      highest legacy serial.
- [ ] Set `.env`: the new stack's secrets, plus `LEGACY_DB_HOST`, `LEGACY_DB_USER`, `LEGACY_DB_PASSWORD`,
      `LEGACY_DB_NAME`. `LEGACY_TZ` is `UTC` unless the old API ran with another timezone.
- [ ] Leave `BOOTSTRAP_ADMIN_EMAIL` unset. Legacy admins carry over with their passwords.
- [ ] Tell users about the window. Admins will have to enrol 2FA at their first sign-in.

## Steps

1. **Freeze the old system** so nothing changes during the copy: stop the old API and keep its MySQL running.

2. **Back up.** Keep this file off the server as well.

   ```bash
   docker exec <old-mysql> sh -c 'exec mysqldump --single-transaction -uroot -p"$MYSQL_ROOT_PASSWORD" crm_db' > legacy-$(date +%F).sql
   ```

3. **Start the new infrastructure and create the schema.** Starting the API once runs Flyway. Stop it again so nobody
   writes before the data is in.

   ```bash
   docker compose up -d new-crm-postgres new-crm-redis new-crm-minio new-crm-api
   docker compose stop api
   ```

4. **Copy uploads into the bucket**, then confirm nothing differs. `mc diff` should print nothing.

   ```bash
   docker compose --profile migrate run --rm new-crm-storage-migrate
   docker compose --profile migrate run --rm --entrypoint sh new-crm-storage-migrate -c 'mc diff /old-storage crm/$MINIO_BUCKET'
   ```

5. **Let the migration reach the old database.**

   ```bash
   docker network connect <new-network> <old-mysql>
   ```

6. **Dry run.** Everything happens in a transaction and is rolled back. Stop here if any check says `MISMATCH`.

   ```bash
   docker compose --profile migrate run --rm new-crm-data-migrate | tee etl-dry-run.txt
   ```

7. **Commit.** It commits only if every check passes again.

   ```bash
   docker compose --profile migrate run --rm -e ETL_COMMIT=1 new-crm-data-migrate | tee etl-commit.txt
   ```

   If uploads are genuinely lost, the `files:` check blocks the commit. To accept that, add
   `-e ETL_ALLOW_MISSING_FILES=1` after checking the list against what you expected.

8. **Go live.** Start the API and frontend, then point users at the new site.

   ```bash
   docker compose up -d
   ```

9. **Smoke test** before announcing:
   - Sign in as an admin and enrol 2FA.
   - Open an old invoice and check number, date, customer and total against the original.
   - View a proof of payment and a delivery proof.
   - Compare the Dashboard totals with `etl-commit.txt`.
   - Issue one new test invoice only if you are prepared to keep or void that serial.

## Rollback

- **Before step 8:** run `docker compose down`, start the old API. Nothing has changed in the old system.
- **After step 8:** the same, but anything entered in the new system since go-live is lost, so decide quickly.

## Afterwards

Keep the old MySQL volume and backup until the first full month closes cleanly. Then remove the `storage-migrate` and
`data-migrate` services, the `api_storage` volume and this folder.
