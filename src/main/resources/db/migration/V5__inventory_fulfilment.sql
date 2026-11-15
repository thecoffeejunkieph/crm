-- #3 inventory and fulfilment. Conventions as V1. Quantities numeric(12,3).

CREATE TABLE stock_level (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product_id bigint NOT NULL REFERENCES product(id), warehouse_id bigint NOT NULL REFERENCES warehouse(id),
  on_hand numeric(12,3) NOT NULL DEFAULT 0, reserved numeric(12,3) NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0,
  UNIQUE (product_id, warehouse_id),
  CHECK (reserved >= 0 AND on_hand >= reserved));

CREATE TABLE stock_movement (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product_id bigint NOT NULL REFERENCES product(id), warehouse_id bigint NOT NULL REFERENCES warehouse(id),
  type text NOT NULL CHECK (type IN ('RECEIPT','ADJUSTMENT','TRANSFER_OUT','TRANSFER_IN','DISPATCH')),
  qty numeric(12,3) NOT NULL, ref_type text, ref_id bigint, reason text,
  created_by bigint REFERENCES app_user(id), created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX stock_movement_pw_idx ON stock_movement (product_id, warehouse_id, created_at);

CREATE TABLE stock_receipt (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  number text NOT NULL UNIQUE, warehouse_id bigint NOT NULL REFERENCES warehouse(id),
  supplier_name text, reference text, received_on date NOT NULL, notes text,
  created_by bigint REFERENCES app_user(id), created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE stock_receipt_line (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  receipt_id bigint NOT NULL REFERENCES stock_receipt(id) ON DELETE CASCADE,
  product_id bigint NOT NULL REFERENCES product(id), qty numeric(12,3) NOT NULL CHECK (qty > 0));

CREATE TABLE stock_adjustment (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  number text NOT NULL UNIQUE, warehouse_id bigint NOT NULL REFERENCES warehouse(id),
  product_id bigint NOT NULL REFERENCES product(id),
  mode text NOT NULL CHECK (mode IN ('COUNTED','DELTA')),
  counted_qty numeric(12,3), delta numeric(12,3),
  reason text NOT NULL,
  source text NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('MANUAL','SHORT_PICK','TRANSIT_LOSS')),
  status text NOT NULL CHECK (status IN ('POSTED','PENDING_APPROVAL','REJECTED')),
  approval_request_id bigint REFERENCES approval_request(id),
  created_by bigint REFERENCES app_user(id), created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0);

-- sales: ship-from warehouse and delivery method.
ALTER TABLE sales_order ADD COLUMN ship_from_warehouse_id bigint REFERENCES warehouse(id);
ALTER TABLE sales_order ADD COLUMN delivery_method text NOT NULL DEFAULT 'DELIVERY'
  CHECK (delivery_method IN ('DELIVERY','PICKUP'));
-- A database with orders but no default warehouse would fail the backfill; create one in that case.
INSERT INTO warehouse (code, name, is_default)
  SELECT 'MAIN', 'Main warehouse', true
  WHERE EXISTS (SELECT 1 FROM sales_order)
    AND NOT EXISTS (SELECT 1 FROM warehouse WHERE is_default AND archived_at IS NULL);
UPDATE sales_order SET ship_from_warehouse_id = (SELECT id FROM warehouse WHERE is_default AND archived_at IS NULL);
ALTER TABLE sales_order ALTER COLUMN ship_from_warehouse_id SET NOT NULL;
-- Temporary: Task 7 (sales integration) sets ship_from_warehouse_id in OrderService and drops this trigger in V6.
-- New orders default to the default warehouse unless the caller names one.
CREATE FUNCTION sales_order_default_ship_from() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.ship_from_warehouse_id := (SELECT id FROM warehouse WHERE is_default AND archived_at IS NULL);
  RETURN NEW;
END $$;
CREATE TRIGGER sales_order_ship_from BEFORE INSERT ON sales_order
  FOR EACH ROW WHEN (NEW.ship_from_warehouse_id IS NULL) EXECUTE FUNCTION sales_order_default_ship_from();

CREATE TABLE stock_transfer (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  number text NOT NULL UNIQUE,
  from_warehouse_id bigint NOT NULL REFERENCES warehouse(id),
  to_warehouse_id bigint NOT NULL REFERENCES warehouse(id),
  status text NOT NULL CHECK (status IN ('DRAFT','DISPATCHED','RECEIVED','CANCELLED')),
  sales_order_id bigint REFERENCES sales_order(id),
  dispatched_at timestamptz, dispatched_by bigint REFERENCES app_user(id),
  received_at timestamptz, received_by bigint REFERENCES app_user(id),
  created_by bigint REFERENCES app_user(id), created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0,
  CHECK (from_warehouse_id <> to_warehouse_id));
CREATE INDEX stock_transfer_status_idx ON stock_transfer (status);
CREATE TABLE stock_transfer_line (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  transfer_id bigint NOT NULL REFERENCES stock_transfer(id) ON DELETE CASCADE,
  product_id bigint NOT NULL REFERENCES product(id), qty numeric(12,3) NOT NULL CHECK (qty > 0),
  received_qty numeric(12,3) CHECK (received_qty IS NULL OR received_qty >= 0));

CREATE TABLE stock_reservation (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  order_line_id bigint NOT NULL UNIQUE REFERENCES order_line(id) ON DELETE CASCADE,
  sales_order_id bigint NOT NULL REFERENCES sales_order(id),
  order_number text NOT NULL, rep_user_id bigint REFERENCES app_user(id),
  product_id bigint NOT NULL REFERENCES product(id), warehouse_id bigint NOT NULL REFERENCES warehouse(id),
  reserved_qty numeric(12,3) NOT NULL DEFAULT 0 CHECK (reserved_qty >= 0),
  short_qty numeric(12,3) NOT NULL DEFAULT 0 CHECK (short_qty >= 0),
  confirmed_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0,
  CHECK (reserved_qty + short_qty > 0));
CREATE INDEX stock_reservation_pw_idx ON stock_reservation (product_id, warehouse_id);
CREATE INDEX stock_reservation_order_idx ON stock_reservation (sales_order_id);

CREATE TABLE delivery_order (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  number text NOT NULL UNIQUE, sales_order_id bigint NOT NULL REFERENCES sales_order(id),
  warehouse_id bigint NOT NULL REFERENCES warehouse(id),
  method text NOT NULL CHECK (method IN ('DELIVERY','PICKUP')),
  status text NOT NULL CHECK (status IN ('PICKING','DISPATCHED','DELIVERED','CANCELLED')),
  dispatched_at timestamptz, dispatched_by bigint REFERENCES app_user(id),
  delivered_at timestamptz, delivered_by bigint REFERENCES app_user(id),
  proof_key text, receiver_name text, customer_name text NOT NULL, ship_to text,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0);
CREATE UNIQUE INDEX delivery_order_one_live ON delivery_order (sales_order_id) WHERE status <> 'CANCELLED';
CREATE INDEX delivery_order_wh_status_idx ON delivery_order (warehouse_id, status);
CREATE TABLE delivery_order_line (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  delivery_order_id bigint NOT NULL REFERENCES delivery_order(id) ON DELETE CASCADE,
  order_line_id bigint NOT NULL REFERENCES order_line(id),
  product_id bigint NOT NULL REFERENCES product(id),
  qty numeric(12,3) NOT NULL CHECK (qty > 0),
  picked_qty numeric(12,3) CHECK (picked_qty IS NULL OR picked_qty >= 0));

INSERT INTO document_counter VALUES ('STOCK_RECEIPT', 1), ('STOCK_ADJUSTMENT', 1), ('STOCK_TRANSFER', 1),
  ('DELIVERY_ORDER', 1);

DELETE FROM app_setting WHERE key = 'approval.writeOffQtyMax';
INSERT INTO app_setting (key, value) VALUES ('approval.writeOffValueMax', '5000');

-- V1's default privileges cover the new tables; the movement ledger is append-only like audit_event.
REVOKE UPDATE, DELETE, TRUNCATE ON stock_movement FROM crm_app;
