-- #2 sales lifecycle: catalog, orders, invoices, payments. Conventions as V1.
ALTER TABLE customer ADD COLUMN tax_treatment text NOT NULL DEFAULT 'REGULAR'
  CHECK (tax_treatment IN ('REGULAR','ZERO_RATED','VAT_EXEMPT'));

ALTER TABLE approval_request DROP CONSTRAINT approval_request_type_check;
ALTER TABLE approval_request ADD CONSTRAINT approval_request_type_check
  CHECK (type IN ('DISCOUNT','BELOW_COST','CREDIT_LIMIT','STOCK_WRITE_OFF','INVOICE_VOID'));

CREATE TABLE product (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  sku text NOT NULL, name text NOT NULL, description text,
  unit text NOT NULL CHECK (unit IN ('PC','BOX','PACK','KG','G','L','ML','M')),
  price numeric(19,2) NOT NULL CHECK (price >= 0),
  cost numeric(19,2) CHECK (cost IS NULL OR cost >= 0),
  tax_class text NOT NULL CHECK (tax_class IN ('VATABLE','VAT_EXEMPT','ZERO_RATED')),
  archived_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0);
CREATE UNIQUE INDEX product_sku_uq ON product (lower(sku)) WHERE archived_at IS NULL;
CREATE INDEX product_name_idx ON product (lower(name));

CREATE SEQUENCE sales_order_number_seq;

CREATE TABLE sales_order (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  number text NOT NULL UNIQUE,
  customer_id bigint NOT NULL REFERENCES customer(id),
  rep_id bigint REFERENCES app_user(id),
  status text NOT NULL CHECK (status IN ('DRAFT','QUOTED','CONFIRMED','RELEASED','FULFILLED','CANCELLED')),
  revision int NOT NULL DEFAULT 0,
  payment_terms text NOT NULL CHECK (payment_terms IN ('DUE_ON_RECEIPT','NET_15','NET_30','NET_60')),
  quote_valid_until date,
  shipping_fee numeric(19,2) NOT NULL DEFAULT 0 CHECK (shipping_fee >= 0),
  notes text, terms text,
  vatable_sales numeric(19,2) NOT NULL DEFAULT 0, vat numeric(19,2) NOT NULL DEFAULT 0,
  vat_exempt_sales numeric(19,2) NOT NULL DEFAULT 0, zero_rated_sales numeric(19,2) NOT NULL DEFAULT 0,
  total numeric(19,2) NOT NULL DEFAULT 0,
  settled_amount numeric(19,2) NOT NULL DEFAULT 0,
  due_date date,
  quoted_at timestamptz, customer_accepted_at timestamptz, confirmed_at timestamptz, released_at timestamptz,
  fulfilled_at timestamptz, cancelled_at timestamptz, cancel_reason text,
  created_by bigint NOT NULL REFERENCES app_user(id),
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0);
CREATE INDEX sales_order_customer_idx ON sales_order (customer_id);
CREATE INDEX sales_order_status_idx ON sales_order (status);

CREATE TABLE order_line (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  order_id bigint NOT NULL REFERENCES sales_order(id) ON DELETE CASCADE,
  position int NOT NULL,
  product_id bigint NOT NULL REFERENCES product(id),
  description text NOT NULL,
  unit text NOT NULL CHECK (unit IN ('PC','BOX','PACK','KG','G','L','ML','M')),
  qty numeric(12,3) NOT NULL CHECK (qty > 0),
  unit_price numeric(19,2) NOT NULL CHECK (unit_price >= 0),
  discount_pct numeric(5,2) NOT NULL DEFAULT 0 CHECK (discount_pct BETWEEN 0 AND 100),
  tax_class text NOT NULL CHECK (tax_class IN ('VATABLE','VAT_EXEMPT','ZERO_RATED')),
  line_total numeric(19,2) NOT NULL);
CREATE INDEX order_line_order_idx ON order_line (order_id);

CREATE TABLE order_revision (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  order_id bigint NOT NULL REFERENCES sales_order(id),
  revision int NOT NULL, snapshot jsonb NOT NULL, reason text NOT NULL,
  created_by bigint NOT NULL REFERENCES app_user(id), created_at timestamptz NOT NULL DEFAULT now());
CREATE UNIQUE INDEX order_revision_uq ON order_revision (order_id, revision);

CREATE TABLE document_counter (name text PRIMARY KEY, next_value bigint NOT NULL);
INSERT INTO document_counter VALUES ('INVOICE', 1);

CREATE TABLE invoice (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  number text NOT NULL UNIQUE,
  order_id bigint NOT NULL REFERENCES sales_order(id),
  order_revision int NOT NULL,
  status text NOT NULL CHECK (status IN ('ISSUED','VOID')),
  issued_at timestamptz NOT NULL, due_date date NOT NULL,
  customer_snapshot jsonb NOT NULL, lines_snapshot jsonb NOT NULL,
  vatable_sales numeric(19,2) NOT NULL, vat numeric(19,2) NOT NULL,
  vat_exempt_sales numeric(19,2) NOT NULL, zero_rated_sales numeric(19,2) NOT NULL,
  shipping_fee numeric(19,2) NOT NULL, total numeric(19,2) NOT NULL,
  void_reason text, voided_by bigint REFERENCES app_user(id), voided_at timestamptz,
  replaced_by_id bigint REFERENCES invoice(id),
  issued_by bigint NOT NULL REFERENCES app_user(id));
CREATE INDEX invoice_order_idx ON invoice (order_id);
CREATE UNIQUE INDEX invoice_one_active ON invoice (order_id) WHERE status = 'ISSUED';

CREATE TABLE payment (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  order_id bigint NOT NULL REFERENCES sales_order(id),
  status text NOT NULL CHECK (status IN ('PENDING','VERIFIED','REJECTED')),
  method text NOT NULL CHECK (method IN ('CASH','BANK_TRANSFER','CHECK','GCASH','CARD','OTHER')),
  cash_amount numeric(19,2) NOT NULL CHECK (cash_amount >= 0),
  withholding_amount numeric(19,2) NOT NULL DEFAULT 0 CHECK (withholding_amount >= 0),
  received_on date NOT NULL, reference text,
  proof_key text, form2307_key text,
  submitted_by_type text NOT NULL CHECK (submitted_by_type IN ('USER','CONTACT')),
  submitted_by_id bigint NOT NULL,
  verified_by bigint REFERENCES app_user(id), verified_at timestamptz, reject_reason text,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0,
  CHECK (cash_amount + withholding_amount > 0));
CREATE INDEX payment_order_idx ON payment (order_id);

INSERT INTO app_setting (key, value) VALUES
  ('sales.quoteValidityDays', '15'), ('sales.withholdingSuggestPercent', '1'),
  ('company.name', '""'), ('company.tin', '""'), ('company.address', '""'), ('invoice.footerText', '""');
