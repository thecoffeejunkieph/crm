-- Uploads moved from local disk (served at /static/**) to a MinIO bucket. Columns now hold the
-- object key, not a URL. Existing files are copied into the bucket under their old relative path
-- (see the storage-migrate service in docker-compose.yml), so stripping the "/static/" prefix
-- turns each stored path into the matching key.
UPDATE users SET picture_path = SUBSTRING(picture_path, 9) WHERE picture_path LIKE '/static/%';
UPDATE product SET picture_path = SUBSTRING(picture_path, 9) WHERE picture_path LIKE '/static/%';
UPDATE invoice SET proof_of_payment_path = SUBSTRING(proof_of_payment_path, 9) WHERE proof_of_payment_path LIKE '/static/%';
UPDATE invoice_payment SET proof_of_payment_path = SUBSTRING(proof_of_payment_path, 9) WHERE proof_of_payment_path LIKE '/static/%';
UPDATE delivery_order_proof_of_pickup SET file_path = SUBSTRING(file_path, 9) WHERE file_path LIKE '/static/%';
UPDATE delivery_order_proof_of_delivery SET file_path = SUBSTRING(file_path, 9) WHERE file_path LIKE '/static/%';
