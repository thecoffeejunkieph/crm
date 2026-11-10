ALTER TABLE sales_order
  ADD COLUMN discount_type text NOT NULL DEFAULT 'PERCENT' CHECK (discount_type IN ('PERCENT','AMOUNT')),
  ADD COLUMN discount_value numeric(19,2) NOT NULL DEFAULT 0 CHECK (discount_value >= 0),
  ADD COLUMN discount_amount numeric(19,2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
  ADD COLUMN withholding_pct numeric(5,2) NOT NULL DEFAULT 0 CHECK (withholding_pct BETWEEN 0 AND 100);

-- line_total less the line's share of the order discount; NULL means no order discount.
ALTER TABLE order_line ADD COLUMN net_total numeric(19,2);

ALTER TABLE invoice
  ADD COLUMN discount_amount numeric(19,2) NOT NULL DEFAULT 0,
  ADD COLUMN withholding_pct numeric(5,2) NOT NULL DEFAULT 0,
  ADD COLUMN withholding_amount numeric(19,2) NOT NULL DEFAULT 0;

-- Existing business orders keep the withholding they were already being suggested.
UPDATE sales_order o
   SET withholding_pct = coalesce((SELECT (value #>> '{}')::numeric FROM app_setting WHERE key = 'sales.withholdingSuggestPercent'), 0)
  FROM customer c
 WHERE c.id = o.customer_id AND c.type = 'BUSINESS';

INSERT INTO app_setting (key, value) VALUES ('invoice.bankDetails', '""');
