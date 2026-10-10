-- Customers can now upload several proof-of-payment files per invoice, so the single path
-- column moves into a collection table (same shape as delivery_order_proof_of_delivery).
CREATE TABLE invoice_proof_of_payment (
  invoice_id bigint NOT NULL,
  file_path varchar(255) DEFAULT NULL,
  KEY fk_invoice_proof_of_payment_invoice (invoice_id),
  CONSTRAINT fk_invoice_proof_of_payment_invoice FOREIGN KEY (invoice_id) REFERENCES invoice (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO invoice_proof_of_payment (invoice_id, file_path)
SELECT id, proof_of_payment_path FROM invoice WHERE proof_of_payment_path IS NOT NULL AND proof_of_payment_path <> '';

ALTER TABLE invoice DROP COLUMN proof_of_payment_path;
