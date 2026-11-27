-- A rep's monthly target for one product category; no commission.
CREATE TABLE sales_category_target (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  rep_id bigint NOT NULL REFERENCES app_user(id),
  month date NOT NULL CHECK (extract(day FROM month) = 1),
  category_id bigint NOT NULL REFERENCES product_category(id),
  target_amount numeric(19,2) NOT NULL CHECK (target_amount > 0),
  version bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by bigint NOT NULL REFERENCES app_user(id),
  UNIQUE (rep_id, month, category_id));
