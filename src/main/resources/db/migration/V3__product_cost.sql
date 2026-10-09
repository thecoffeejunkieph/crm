-- Unit cost per product, used for gross profit on the dashboard. Nullable: existing products
-- have no known cost until someone fills it in.
ALTER TABLE product ADD COLUMN cost decimal(38,2) DEFAULT NULL AFTER price;
