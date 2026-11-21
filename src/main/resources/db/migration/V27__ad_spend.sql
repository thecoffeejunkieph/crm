-- Monthly ad spend per lead source (spec 2026-11-22-ad-performance-design).
CREATE TABLE ad_spend (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  source_id bigint NOT NULL REFERENCES lead_source(id),
  month date NOT NULL CHECK (extract(day FROM month) = 1),
  amount numeric(19,2) NOT NULL CHECK (amount >= 0),
  version bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by bigint NOT NULL REFERENCES app_user(id),
  UNIQUE (source_id, month));
