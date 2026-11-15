-- Follow-ups on customers (spec 2026-11-05-follow-ups-design).
CREATE TABLE follow_up (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  customer_id bigint NOT NULL REFERENCES customer(id),
  order_id bigint REFERENCES sales_order(id),
  assignee_id bigint NOT NULL REFERENCES app_user(id),
  title varchar(200) NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
  note text CHECK (note IS NULL OR char_length(note) <= 2000),
  due_date date NOT NULL,
  done_at timestamptz,
  done_by bigint REFERENCES app_user(id),
  created_by bigint NOT NULL REFERENCES app_user(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0);

CREATE INDEX follow_up_open_idx ON follow_up (assignee_id, due_date) WHERE done_at IS NULL;
CREATE INDEX follow_up_customer_idx ON follow_up (customer_id);
CREATE INDEX follow_up_order_idx ON follow_up (order_id);
