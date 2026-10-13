#!/bin/sh
# Runs once on first start of an empty data dir. The entrypoint has already created the crm_owner role and the
# crm database from POSTGRES_USER / POSTGRES_PASSWORD / POSTGRES_DB; this adds the least-privilege app role.
# No default password: a missing DB_APP_PASSWORD aborts initialisation.
# If it aborts, `docker compose down -v` before retrying: the data dir is already initialised and init will not rerun.
set -eu
: "${DB_APP_PASSWORD:?DB_APP_PASSWORD must be set}"

psql -v ON_ERROR_STOP=1 -v app_pw="$DB_APP_PASSWORD" -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<'SQL'
CREATE ROLE crm_app LOGIN PASSWORD :'app_pw';
-- crm_app owns nothing; it only gets DML on what crm_owner creates (V1 revokes UPDATE/DELETE on audit_event).
GRANT USAGE ON SCHEMA public TO crm_app;
ALTER DEFAULT PRIVILEGES FOR ROLE crm_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO crm_app;
ALTER DEFAULT PRIVILEGES FOR ROLE crm_owner IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO crm_app;
SQL
