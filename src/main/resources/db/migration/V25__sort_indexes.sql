CREATE INDEX IF NOT EXISTS delivery_order_line_delivery_order_idx ON delivery_order_line (delivery_order_id);
CREATE INDEX IF NOT EXISTS stock_transfer_line_transfer_idx ON stock_transfer_line (transfer_id);
