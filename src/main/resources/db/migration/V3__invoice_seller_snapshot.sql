-- The seller block and footer (BIR permit text) are frozen at issue so a reprint is identical.
-- Null on invoices issued before this column existed; those render from the live settings.
ALTER TABLE invoice ADD COLUMN seller_snapshot jsonb;
