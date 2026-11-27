-- Customer self-service: billing recipients, delivery addresses, order ship-to (spec 2026-11-22-customer-self-service-design).
ALTER TABLE customer_contact ADD COLUMN receives_invoices boolean NOT NULL DEFAULT true;
UPDATE customer_contact SET receives_invoices = portal_enabled;

CREATE TABLE customer_address (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  customer_id bigint NOT NULL REFERENCES customer(id),
  label text NOT NULL,
  address text NOT NULL,
  is_default boolean NOT NULL DEFAULT false,
  archived_at timestamptz,
  version bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX customer_address_customer ON customer_address (customer_id);
CREATE UNIQUE INDEX customer_address_one_default ON customer_address (customer_id)
  WHERE is_default AND archived_at IS NULL;

ALTER TABLE sales_order ADD COLUMN ship_to text;