-- Runs once on first container start. Override passwords with: psql -v owner_pw=... -v app_pw=...
\if :{?owner_pw} \else \set owner_pw 'crm_owner' \endif
\if :{?app_pw} \else \set app_pw 'crm_app' \endif

CREATE ROLE crm_owner LOGIN PASSWORD :'owner_pw';
CREATE ROLE crm_app LOGIN PASSWORD :'app_pw';
CREATE DATABASE crm OWNER crm_owner;
\connect crm
-- crm_app owns nothing; it only gets DML on what crm_owner creates.
GRANT USAGE ON SCHEMA public TO crm_app;
ALTER DEFAULT PRIVILEGES FOR ROLE crm_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO crm_app;
ALTER DEFAULT PRIVILEGES FOR ROLE crm_owner IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO crm_app;
