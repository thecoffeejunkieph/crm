-- Deals / opportunities on customers (spec 2026-11-06-deals-design).
CREATE TABLE deal (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  customer_id bigint NOT NULL REFERENCES customer(id),
  rep_id bigint REFERENCES app_user(id),
  title varchar(160) NOT NULL CHECK (char_length(title) BETWEEN 1 AND 160),
  stage varchar(20) NOT NULL DEFAULT 'DISCOVERY'
    CHECK (stage IN ('DISCOVERY','PROPOSAL','NEGOTIATION','WON','LOST')),
  value numeric(14,2) CHECK (value IS NULL OR value >= 0),
  expected_close date,
  lost_reason varchar(500),
  notes text CHECK (notes IS NULL OR char_length(notes) <= 4000),
  won_at timestamptz,
  won_order_id bigint REFERENCES sales_order(id),
  created_by bigint NOT NULL REFERENCES app_user(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0,
  CHECK (stage <> 'LOST' OR lost_reason IS NOT NULL));
CREATE INDEX deal_rep_stage_idx ON deal (rep_id, stage);
CREATE INDEX deal_customer_idx ON deal (customer_id);
CREATE INDEX deal_stage_idx ON deal (stage);

CREATE TABLE deal_order (
  deal_id bigint NOT NULL REFERENCES deal(id),
  order_id bigint NOT NULL REFERENCES sales_order(id),
  linked_at timestamptz NOT NULL DEFAULT now(),
  linked_by bigint NOT NULL REFERENCES app_user(id),
  PRIMARY KEY (deal_id, order_id));
CREATE UNIQUE INDEX deal_order_order_uq ON deal_order (order_id);
