-- POS is gone. Past counter sales stay as ordinary fulfilled orders.
ALTER TABLE sales_order DROP COLUMN channel;
DELETE FROM app_setting WHERE key IN ('pos.warehouseId', 'pos.quickAddAssignment');
