-- Files on customers, orders and invoices (spec 2026-11-05-attachments-design). Removing archives the row;
-- the object stays in MinIO.
CREATE TABLE attachment (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  subject_type text NOT NULL CHECK (subject_type IN ('CUSTOMER', 'ORDER', 'INVOICE')),
  subject_id bigint NOT NULL,
  object_key text NOT NULL UNIQUE,
  file_name varchar(255) NOT NULL,
  content_type text NOT NULL,
  size_bytes bigint NOT NULL CHECK (size_bytes > 0),
  note varchar(200),
  uploaded_by bigint NOT NULL REFERENCES app_user(id),
  uploaded_at timestamptz NOT NULL DEFAULT now(),
  archived_at timestamptz,
  archived_by bigint REFERENCES app_user(id));

CREATE INDEX attachment_subject_idx ON attachment (subject_type, subject_id) WHERE archived_at IS NULL;
