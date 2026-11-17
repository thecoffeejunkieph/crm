-- Admin-managed product categories. Archived ones stay on products but can't be newly picked.
CREATE TABLE product_category (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name varchar(100) NOT NULL CHECK (char_length(name) BETWEEN 1 AND 100),
  archived_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0);
CREATE UNIQUE INDEX product_category_name_uq ON product_category (lower(name));

ALTER TABLE product ADD COLUMN category_id bigint REFERENCES product_category(id);
CREATE INDEX product_category_id_idx ON product (category_id);
