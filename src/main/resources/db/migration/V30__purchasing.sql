CREATE TABLE supplier (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code text NOT NULL UNIQUE,
  name text NOT NULL,
  contact_name text, phone text, email text, address text,
  payment_terms_days integer CHECK (payment_terms_days >= 0),
  lead_time_days integer CHECK (lead_time_days >= 0),
  credit_limit numeric(14,2) CHECK (credit_limit >= 0),
  opening_balance numeric(14,2) NOT NULL DEFAULT 0 CHECK (opening_balance >= 0),
  notes text,
  archived_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0);

ALTER TABLE product ADD COLUMN preferred_supplier_id bigint REFERENCES supplier(id);
ALTER TABLE product ADD COLUMN reorder_target numeric(12,3) CHECK (reorder_target >= 0);

CREATE TABLE purchase_order (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  number text NOT NULL UNIQUE,
  supplier_id bigint NOT NULL REFERENCES supplier(id),
  warehouse_id bigint NOT NULL REFERENCES warehouse(id),
  status text NOT NULL CHECK (status IN ('DRAFT','PENDING_APPROVAL','SENT','PARTIALLY_RECEIVED','RECEIVED','CANCELLED')),
  expected_on date,
  notes text,
  rejection_reason text,
  approval_request_id bigint REFERENCES approval_request(id),
  sent_at timestamptz,
  closed_short boolean NOT NULL DEFAULT false,
  created_by bigint REFERENCES app_user(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0);
CREATE INDEX purchase_order_supplier_status_idx ON purchase_order (supplier_id, status);

CREATE TABLE purchase_order_line (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  po_id bigint NOT NULL REFERENCES purchase_order(id) ON DELETE CASCADE,
  product_id bigint NOT NULL REFERENCES product(id),
  qty numeric(12,3) NOT NULL CHECK (qty > 0),
  unit_cost numeric(14,2) NOT NULL CHECK (unit_cost >= 0),
  received_qty numeric(12,3) NOT NULL DEFAULT 0 CHECK (received_qty >= 0),
  CHECK (received_qty <= qty),
  UNIQUE (po_id, product_id));
CREATE INDEX purchase_order_line_po_idx ON purchase_order_line (po_id);
CREATE INDEX purchase_order_line_product_idx ON purchase_order_line (product_id);

ALTER TABLE stock_receipt ADD COLUMN purchase_order_id bigint REFERENCES purchase_order(id);
CREATE INDEX stock_receipt_po_idx ON stock_receipt (purchase_order_id);

CREATE TABLE supplier_payment (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  supplier_id bigint NOT NULL REFERENCES supplier(id),
  amount numeric(14,2) NOT NULL CHECK (amount > 0),
  paid_on date NOT NULL,
  method text, reference text, notes text,
  created_by bigint REFERENCES app_user(id),
  created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX supplier_payment_supplier_idx ON supplier_payment (supplier_id, paid_on);

CREATE TABLE reorder_reminder_state (
  id smallint PRIMARY KEY CHECK (id = 1),
  product_ids bigint[] NOT NULL,
  sent_at timestamptz NOT NULL);

ALTER TABLE approval_request DROP CONSTRAINT approval_request_type_check;
ALTER TABLE approval_request ADD CONSTRAINT approval_request_type_check
  CHECK (type IN ('DISCOUNT','BELOW_COST','CREDIT_LIMIT','STOCK_WRITE_OFF','INVOICE_VOID','REFUND','PO_OVER_CREDIT'));

INSERT INTO document_counter VALUES ('PURCHASE_ORDER', 1);
