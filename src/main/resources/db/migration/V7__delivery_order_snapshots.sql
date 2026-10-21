-- Delivery orders are scoped to the rep (SALES reads its own) and show the order lines without reading sales.
-- No rows exist yet, so the NOT NULL columns need no backfill.
ALTER TABLE delivery_order ADD COLUMN order_number text NOT NULL,
  ADD COLUMN rep_user_id bigint NOT NULL REFERENCES app_user(id);
CREATE INDEX delivery_order_rep_idx ON delivery_order (rep_user_id);
ALTER TABLE delivery_order_line ADD COLUMN description text NOT NULL, ADD COLUMN unit text NOT NULL;
