-- Retail POS: walk-in sales stored as PAID invoices that deduct stock immediately.
ALTER TABLE invoice ADD COLUMN channel enum('B2B','POS') NOT NULL DEFAULT 'B2B';
ALTER TABLE invoice_payment ADD COLUMN amount_tendered decimal(19,2) DEFAULT NULL;
ALTER TABLE invoice_item ADD COLUMN list_price decimal(19,2) DEFAULT NULL;
ALTER TABLE stock_movement MODIFY COLUMN type enum('DELIVERY','RECEIPT','RELEASE','RESERVE','SALE') NOT NULL;
