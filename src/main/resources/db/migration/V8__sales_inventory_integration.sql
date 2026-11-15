-- OrderService now sets ship_from_warehouse_id itself (the default warehouse on create), so V5's stopgap goes.
DROP TRIGGER sales_order_ship_from ON sales_order;
DROP FUNCTION sales_order_default_ship_from();

-- Order lines keep their identity on edit, and inventory frees a removed line's reservation itself. A cascade
-- would delete the reservation without lowering stock_level.reserved, so these become plain cross-module ids.
ALTER TABLE stock_reservation DROP CONSTRAINT stock_reservation_order_line_id_fkey;
ALTER TABLE delivery_order_line DROP CONSTRAINT delivery_order_line_order_line_id_fkey;
