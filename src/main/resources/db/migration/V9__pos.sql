ALTER TABLE sales_order ADD COLUMN channel text NOT NULL DEFAULT 'B2B' CHECK (channel IN ('B2B','POS'));
INSERT INTO app_setting (key, value) VALUES ('pos.warehouseId', '0');
