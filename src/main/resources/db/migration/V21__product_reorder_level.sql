-- Low-stock threshold: warn when a product's available stock (all warehouses) is at or below it. Null = not tracked.
ALTER TABLE product ADD COLUMN reorder_level numeric(12, 3) CHECK (reorder_level >= 0);
