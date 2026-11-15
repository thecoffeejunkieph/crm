-- The free-text bank box becomes a list of accounts, each with an optional QR image. "Bank - Name - Number" splits cleanly.
INSERT INTO app_setting (key, value)
SELECT 'invoice.bankAccounts', CASE
         WHEN t = '' THEN '[]'::jsonb
         WHEN t ~ '^[^\n]+ - [^\n]+ - [^\n]+$' THEN jsonb_build_array(jsonb_build_object(
              'bankName', split_part(t, ' - ', 1), 'accountName', split_part(t, ' - ', 2),
              'accountNumber', split_part(t, ' - ', 3), 'qr', ''))
         ELSE jsonb_build_array(jsonb_build_object('bankName', t, 'accountName', '', 'accountNumber', '', 'qr', ''))
       END
  FROM (SELECT value #>> '{}' AS t FROM app_setting WHERE key = 'invoice.bankDetails') old;

DELETE FROM app_setting WHERE key = 'invoice.bankDetails';

INSERT INTO app_setting (key, value) VALUES ('invoice.terms', '""');
