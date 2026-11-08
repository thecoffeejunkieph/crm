"""One-time MySQL -> Postgres migration (P5-1). See deploy/migrate/CUTOVER.md.

Everything runs in one Postgres transaction: copy MySQL into a temporary `legacy` schema, run transform.sql, run
reconcile.sql, drop `legacy`. It commits only when ETL_COMMIT=1 and every check passes; otherwise it rolls back.
"""
import mimetypes
import os
import pathlib
import sys

import psycopg
import pymysql

HERE = pathlib.Path(__file__).parent
SKIP = {"flyway_schema_history"}
TYPES = {"bigint": "bigint", "int": "bigint", "tinyint": "bigint", "smallint": "bigint", "decimal": "numeric",
         "datetime": "timestamp", "timestamp": "timestamp", "date": "date", "bit": "boolean"}
PROOF_COLUMNS = [("delivery_order_proof_of_delivery", "file_path"), ("delivery_order_proof_of_pickup", "file_path"),
                 ("invoice", "proof_of_payment_path"), ("invoice", "proof_of_delivery_path"),
                 ("invoice_payment", "proof_of_payment_path"), ("delivery_order", "proof_of_delivery_path"),
                 ("delivery_order", "proof_of_pickup_path"), ("users", "picture_path")]


def env(name, default=None):
    value = os.environ.get(name) or default  # compose passes unset variables as ""
    if value is None:
        sys.exit(f"{name} is required")
    return value


def copy_legacy(my, pg):
    with my.cursor() as c:
        c.execute("select table_name from information_schema.tables where table_schema = database() and table_type = 'BASE TABLE'")
        tables = [r[0] for r in c.fetchall() if r[0] not in SKIP]
        for t in tables:
            c.execute("select column_name, data_type from information_schema.columns where table_schema = database() "
                      "and table_name = %s order by ordinal_position", (t,))
            cols = c.fetchall()
            ddl = ", ".join(f'"{n}" {TYPES.get(k, "text")}' for n, k in cols)
            pg.execute(f'create table legacy."{t}" ({ddl})')
            c.execute("select " + ", ".join(f"`{n}`" for n, _ in cols) + f" from `{t}`")
            rows = [tuple(bool(int.from_bytes(v, "big")) if k == "bit" and v is not None else v
                          for v, (_, k) in zip(r, cols)) for r in c.fetchall()]
            if rows:
                marks = ", ".join(["%s"] * len(cols))
                pg.cursor().executemany(f'insert into legacy."{t}" values ({marks})', rows)


def stat_files(pg, root):
    """Records which referenced uploads exist on disk, so proofs can become attachments and missing ones are reported."""
    pg.execute("create table legacy.file_stat (key text primary key, present boolean not null, size_bytes bigint, content_type text)")
    keys = set()
    for table, col in PROOF_COLUMNS:
        for (p,) in pg.execute(f'''select distinct regexp_replace(trim("{col}"), '^/?(static/)?', '') from legacy."{table}" where trim("{col}") <> \'\''''):
            keys.add(p)
    for key in keys:
        f = root / key
        present = f.is_file() and f.stat().st_size > 0
        pg.execute("insert into legacy.file_stat values (%s, %s, %s, %s)",
                   (key, present, f.stat().st_size if present else None, mimetypes.guess_type(key)[0] or "application/octet-stream"))


def main():
    commit = env("ETL_COMMIT", "0") == "1"
    my = pymysql.connect(host=env("LEGACY_DB_HOST"), port=int(env("LEGACY_DB_PORT", "3306")), user=env("LEGACY_DB_USER"),
                         password=env("LEGACY_DB_PASSWORD"), database=env("LEGACY_DB_NAME", "crm_db"), charset="utf8mb4")
    with psycopg.connect(host=env("PG_HOST"), port=env("PG_PORT", "5432"), dbname=env("PG_DB", "crm"), user=env("PG_USER"),
                         password=env("PG_PASSWORD")) as pg:
        for table in ("customer", "product", "sales_order", "invoice"):
            n = pg.execute(f"select count(*) from {table}").fetchone()[0]
            if n:
                sys.exit(f"Refusing to run: target table {table} already has {n} rows.")
        pg.execute("select set_config('etl.tz', %s, true)", (env("LEGACY_TZ", "UTC"),))
        pg.execute("create schema legacy")
        copy_legacy(my, pg)
        stat_files(pg, pathlib.Path(env("STORAGE_DIR", "/old-storage")))
        pg.execute((HERE / "transform.sql").read_text(encoding="utf-8"))
        cur = pg.cursor()
        cur.execute((HERE / "reconcile.sql").read_text(encoding="utf-8"))
        while cur.description is None and cur.nextset():
            pass
        checks = cur.fetchall()
        allow_missing = env("ETL_ALLOW_MISSING_FILES", "0") == "1"  # a lost upload should be a decision, not a default
        failed = [c for c in checks if str(c[1]) != str(c[2]) and not (allow_missing and c[0].startswith("files:"))]
        width = max(len(c[0]) for c in checks)
        print(f"{'check':<{width}}  {'legacy':>16}  {'migrated':>16}  result")
        for name, want, got in checks:
            print(f"{name:<{width}}  {str(want):>16}  {str(got):>16}  {'ok' if str(want) == str(got) else 'MISMATCH'}")
        print("\nReview by hand:")
        for line, in pg.execute(open(HERE / "review.sql", encoding="utf-8").read()).fetchall():
            print(" -", line)
        pg.execute("drop schema legacy cascade")
        if failed or not commit:
            pg.rollback()
            print(f"\nROLLED BACK: {len(failed)} check(s) failed." if failed else "\nDRY RUN: all checks passed, rolled back. Set ETL_COMMIT=1 to commit.")
            return 1 if failed else 0
        pg.commit()
        print("\nCOMMITTED: all checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
