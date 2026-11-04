-- Dashboards (spec 2026-11-05): cost snapshot per line, monthly sales targets, read indexes.
ALTER TABLE order_line ADD COLUMN unit_cost numeric(19,2);

UPDATE order_line l SET unit_cost = p.cost
FROM product p, sales_order o
WHERE l.product_id = p.id AND l.order_id = o.id AND o.status IN ('CONFIRMED','RELEASED','FULFILLED');

CREATE TABLE sales_target (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  rep_id bigint NOT NULL REFERENCES app_user(id),
  month date NOT NULL CHECK (extract(day FROM month) = 1),
  target_amount numeric(19,2) NOT NULL CHECK (target_amount >= 0),
  commission_pct numeric(5,2) NOT NULL CHECK (commission_pct >= 0 AND commission_pct <= 100),
  version bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by bigint NOT NULL REFERENCES app_user(id),
  UNIQUE (rep_id, month));

CREATE INDEX sales_order_rep_idx ON sales_order (rep_id);
CREATE INDEX payment_status_verified_idx ON payment (status, verified_at);
