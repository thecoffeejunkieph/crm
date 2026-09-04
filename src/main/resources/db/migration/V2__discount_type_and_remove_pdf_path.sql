-- Invoices/quotations/delivery orders are generated on demand and streamed straight to the
-- browser now - nothing is ever written to disk, so there's no path left to store.
ALTER TABLE invoice DROP COLUMN pdf_path;
ALTER TABLE quotation DROP COLUMN pdf_path;
ALTER TABLE delivery_order DROP COLUMN pdf_path;

-- A line-item discount can now be a percentage or a fixed peso amount instead of always
-- being treated as a fixed amount. Backfill existing rows to FIXED, since that was the only
-- behavior before this column existed.
ALTER TABLE invoice_item ADD COLUMN discount_type ENUM('PERCENT','FIXED') DEFAULT NULL AFTER discount;
ALTER TABLE quotation_item ADD COLUMN discount_type ENUM('PERCENT','FIXED') DEFAULT NULL AFTER discount;

UPDATE invoice_item SET discount_type = 'FIXED' WHERE discount IS NOT NULL AND discount_type IS NULL;
UPDATE quotation_item SET discount_type = 'FIXED' WHERE discount IS NOT NULL AND discount_type IS NULL;

-- Document-level discount (applied on top of any per-item discounts), same PERCENT/FIXED split.
ALTER TABLE invoice ADD COLUMN discount INT DEFAULT NULL AFTER shipping_charges;
ALTER TABLE invoice ADD COLUMN discount_type ENUM('PERCENT','FIXED') DEFAULT NULL AFTER discount;
ALTER TABLE quotation ADD COLUMN discount INT DEFAULT NULL AFTER shipping_charges;
ALTER TABLE quotation ADD COLUMN discount_type ENUM('PERCENT','FIXED') DEFAULT NULL AFTER discount;
