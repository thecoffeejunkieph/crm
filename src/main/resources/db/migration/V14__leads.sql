-- Leads and the shared lead-source list (spec 2026-11-05-leads-design).
CREATE TABLE lead_source (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name varchar(60) NOT NULL CHECK (char_length(name) BETWEEN 1 AND 60),
  archived_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0);
CREATE UNIQUE INDEX lead_source_name_uq ON lead_source (lower(name));

INSERT INTO lead_source (name) VALUES ('Walk-in'), ('Referral'), ('Facebook'), ('Website'), ('Phone call'),
  ('Trade show');

CREATE TABLE lead (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  contact_name varchar(120) NOT NULL CHECK (char_length(contact_name) BETWEEN 1 AND 120),
  company varchar(160),
  phone varchar(40),
  email varchar(254),
  social_media varchar(1000),
  address text,
  interest text CHECK (interest IS NULL OR char_length(interest) <= 2000),
  notes text CHECK (notes IS NULL OR char_length(notes) <= 4000),
  source_id bigint REFERENCES lead_source(id),
  assigned_rep_id bigint REFERENCES app_user(id),
  status varchar(20) NOT NULL DEFAULT 'NEW'
    CHECK (status IN ('NEW','CONTACTED','QUALIFIED','LOST','CONVERTED')),
  lost_reason varchar(500),
  customer_id bigint REFERENCES customer(id),
  converted_at timestamptz,
  converted_by bigint REFERENCES app_user(id),
  created_by bigint NOT NULL REFERENCES app_user(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0,
  CHECK (status <> 'LOST' OR lost_reason IS NOT NULL),
  CHECK (phone IS NOT NULL OR email IS NOT NULL OR social_media IS NOT NULL));
CREATE INDEX lead_rep_status_idx ON lead (assigned_rep_id, status);
CREATE INDEX lead_status_idx ON lead (status);
CREATE INDEX lead_email_idx ON lead (lower(email));
CREATE INDEX lead_phone_idx ON lead (phone);
CREATE INDEX lead_customer_idx ON lead (customer_id);

ALTER TABLE customer ADD COLUMN source_id bigint REFERENCES lead_source(id);
ALTER TABLE customer ADD COLUMN converted_from_lead_id bigint REFERENCES lead(id);

-- One source per distinct spelling (ignoring case and padding, cut to the 60-character name limit), named as the
-- oldest customer wrote it. The key is the cut name, so two long sources that differ only after character 60 share it.
INSERT INTO lead_source (name)
SELECT DISTINCT ON (lower(left(trim(source), 60))) left(trim(source), 60)
FROM customer
WHERE trim(coalesce(source, '')) <> ''
  AND NOT EXISTS (SELECT 1 FROM lead_source s WHERE lower(s.name) = lower(left(trim(customer.source), 60)))
ORDER BY lower(left(trim(source), 60)), id;

UPDATE customer SET source_id = (SELECT s.id FROM lead_source s WHERE lower(s.name) = lower(left(trim(customer.source), 60)))
WHERE trim(coalesce(source, '')) <> '';

ALTER TABLE customer DROP COLUMN source;
