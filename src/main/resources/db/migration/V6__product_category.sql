-- Product categories, picked from a list on the product form and used to group sold quantities
-- on the inventory dashboard. The column collation makes names unique case-insensitively.
CREATE TABLE category (
  id bigint NOT NULL AUTO_INCREMENT,
  created_at datetime(6) NOT NULL,
  updated_at datetime(6) NOT NULL,
  name varchar(100) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_category_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE product
  ADD COLUMN category_id bigint DEFAULT NULL AFTER description,
  ADD CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id);
