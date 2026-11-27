ALTER TABLE supplier ADD COLUMN downpayment_percent numeric(5,2) NOT NULL DEFAULT 0
  CHECK (downpayment_percent BETWEEN 0 AND 100);

ALTER TABLE supplier_payment ADD COLUMN purchase_order_id bigint REFERENCES purchase_order(id);
CREATE INDEX idx_supplier_payment_po ON supplier_payment(purchase_order_id);
