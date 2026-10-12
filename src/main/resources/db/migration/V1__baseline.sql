-- conventions: bigint identity ids, timestamptz, text+CHECK enums, archived_at for master data
CREATE TABLE warehouse (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code text NOT NULL, name text NOT NULL, address text,
  is_default boolean NOT NULL DEFAULT false,
  archived_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0);
CREATE UNIQUE INDEX warehouse_code_uq ON warehouse (lower(code)) WHERE archived_at IS NULL;
CREATE UNIQUE INDEX warehouse_one_default ON warehouse (is_default) WHERE is_default AND archived_at IS NULL;

CREATE TABLE app_user (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  email text NOT NULL, password_hash text, first_name text NOT NULL, last_name text NOT NULL, phone text,
  role text NOT NULL CHECK (role IN ('ADMIN','SALES','WAREHOUSE')),
  totp_secret_enc text, totp_enabled_at timestamptz, totp_last_step bigint,
  failed_logins int NOT NULL DEFAULT 0, locked_until timestamptz,
  invite_email_status text CHECK (invite_email_status IN ('PENDING','SENT','FAILED')),
  archived_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0);
CREATE UNIQUE INDEX app_user_email_uq ON app_user (email) WHERE archived_at IS NULL;

CREATE TABLE user_warehouse (
  user_id bigint NOT NULL REFERENCES app_user(id), warehouse_id bigint NOT NULL REFERENCES warehouse(id),
  PRIMARY KEY (user_id, warehouse_id));

CREATE TABLE recovery_code (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id bigint NOT NULL REFERENCES app_user(id), code_hash text NOT NULL, used_at timestamptz);

CREATE TABLE one_time_token (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  purpose text NOT NULL CHECK (purpose IN ('INVITE','PASSWORD_RESET','PORTAL_LOGIN','QUOTE_RESPONSE','PAYMENT_PROOF')),
  subject_type text NOT NULL, subject_id bigint NOT NULL, token_hash text NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL, used_at timestamptz, created_by bigint REFERENCES app_user(id),
  created_at timestamptz NOT NULL DEFAULT now());

CREATE TABLE app_setting (
  key text PRIMARY KEY, value jsonb NOT NULL,
  updated_by bigint REFERENCES app_user(id), updated_at timestamptz NOT NULL DEFAULT now());
INSERT INTO app_setting (key, value) VALUES
  ('approval.discountPercentMax', '15'), ('approval.writeOffQtyMax', '10'), ('pos.quickAddAssignment', '"SELF"');

CREATE TABLE audit_event (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  at timestamptz NOT NULL DEFAULT now(),
  actor_type text NOT NULL CHECK (actor_type IN ('USER','CONTACT','SYSTEM')),
  actor_id bigint, actor_name text, action text NOT NULL,
  entity_type text, entity_id bigint, summary text NOT NULL, changes jsonb,
  ip text, request_id text);
CREATE INDEX audit_entity_idx ON audit_event (entity_type, entity_id, at DESC);
CREATE INDEX audit_actor_idx ON audit_event (actor_type, actor_id, at DESC);
CREATE INDEX audit_at_idx ON audit_event (at DESC);

CREATE TABLE approval_request (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  type text NOT NULL CHECK (type IN ('DISCOUNT','BELOW_COST','CREDIT_LIMIT','STOCK_WRITE_OFF')),
  subject_type text NOT NULL, subject_id bigint NOT NULL, subject_version bigint NOT NULL,
  requested_value jsonb NOT NULL, reason text NOT NULL,
  status text NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
  requested_by bigint NOT NULL REFERENCES app_user(id), requested_at timestamptz NOT NULL DEFAULT now(),
  decided_by bigint REFERENCES app_user(id), decided_at timestamptz, decision_note text,
  version bigint NOT NULL DEFAULT 0);
CREATE INDEX approval_subject_idx ON approval_request (subject_type, subject_id, type, subject_version);
CREATE UNIQUE INDEX approval_one_pending ON approval_request (type, subject_type, subject_id, subject_version)
  WHERE status = 'PENDING';

CREATE TABLE customer (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  type text NOT NULL CHECK (type IN ('BUSINESS','INDIVIDUAL')),
  display_name text NOT NULL, business_name text, tin text, address text, phone text, email text,
  credit_limit numeric(19,2) CHECK (credit_limit IS NULL OR credit_limit >= 0),
  payment_terms text NOT NULL DEFAULT 'DUE_ON_RECEIPT'
    CHECK (payment_terms IN ('DUE_ON_RECEIPT','NET_15','NET_30','NET_60')),
  assigned_rep_id bigint REFERENCES app_user(id), source text, notes text,
  archived_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0);
CREATE INDEX customer_rep_idx ON customer (assigned_rep_id);
CREATE INDEX customer_name_idx ON customer (lower(display_name));

CREATE TABLE customer_contact (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  customer_id bigint NOT NULL REFERENCES customer(id), name text NOT NULL, email text NOT NULL, phone text,
  portal_enabled boolean NOT NULL DEFAULT true,
  archived_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0);
CREATE UNIQUE INDEX contact_email_uq ON customer_contact (email) WHERE archived_at IS NULL;

CREATE TABLE notification (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  recipient_user_id bigint NOT NULL REFERENCES app_user(id),
  type text NOT NULL, entity_type text, entity_id bigint, title text NOT NULL, body text,
  read_at timestamptz, created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX notification_recipient_idx ON notification (recipient_user_id, read_at, created_at DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO crm_app;
REVOKE UPDATE, DELETE, TRUNCATE ON audit_event FROM crm_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO crm_app;
ALTER DEFAULT PRIVILEGES FOR ROLE crm_owner IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO crm_app;
ALTER DEFAULT PRIVILEGES FOR ROLE crm_owner IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO crm_app;
