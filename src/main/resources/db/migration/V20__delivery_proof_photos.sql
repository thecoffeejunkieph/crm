-- Proof of delivery can be several photos; keep the existing single photo as the first.
ALTER TABLE delivery_order ADD COLUMN proof_keys text[] NOT NULL DEFAULT '{}';
UPDATE delivery_order SET proof_keys = ARRAY[proof_key] WHERE proof_key IS NOT NULL;
ALTER TABLE delivery_order DROP COLUMN proof_key;
