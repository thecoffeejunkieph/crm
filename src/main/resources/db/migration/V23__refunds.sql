-- Refunds: money-only and goods-return refunds against a sales order, plus the RETURN stock movement.
ALTER TABLE stock_movement DROP CONSTRAINT stock_movement_type_check;
ALTER TABLE stock_movement ADD CONSTRAINT stock_movement_type_check
  CHECK (type IN ('RECEIPT','ADJUSTMENT','TRANSFER_OUT','TRANSFER_IN','DISPATCH','RETURN'));
ALTER TABLE stock_adjustment DROP CONSTRAINT stock_adjustment_source_check;
ALTER TABLE stock_adjustment ADD CONSTRAINT stock_adjustment_source_check
  CHECK (source IN ('MANUAL','SHORT_PICK','TRANSIT_LOSS','RETURN_DAMAGED'));
ALTER TABLE approval_request DROP CONSTRAINT approval_request_type_check;
ALTER TABLE approval_request ADD CONSTRAINT approval_request_type_check
  CHECK (type IN ('DISCOUNT','BELOW_COST','CREDIT_LIMIT','STOCK_WRITE_OFF','INVOICE_VOID','REFUND'));

ALTER TABLE sales_order ADD COLUMN refunded_amount numeric(19,2) NOT NULL DEFAULT 0;

CREATE TABLE refund (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  order_id bigint NOT NULL REFERENCES sales_order(id),
  kind text NOT NULL CHECK (kind IN ('MONEY_ONLY','GOODS_RETURN')),
  amount numeric(19,2) NOT NULL CHECK (amount > 0),
  reason text NOT NULL,
  status text NOT NULL CHECK (status IN ('REQUESTED','APPROVED','PAID','REJECTED','CANCELLED')),
  approval_request_id bigint REFERENCES approval_request(id),
  method text CHECK (method IN ('CASH','BANK_TRANSFER','CHECK','GCASH','CARD','OTHER')),
  reference text, proof_key text, paid_on date,
  paid_by bigint REFERENCES app_user(id), paid_at timestamptz,
  credit_memo_no text UNIQUE,
  -- Frozen at payout: the memo always cites this invoice and (money-only) this tax class.
  invoice_id bigint REFERENCES invoice(id),
  memo_tax_class text CHECK (memo_tax_class IN ('VATABLE','VAT_EXEMPT','ZERO_RATED')),
  requested_by bigint NOT NULL REFERENCES app_user(id),
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0);
CREATE INDEX refund_order_idx ON refund (order_id);

CREATE TABLE refund_line (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  refund_id bigint NOT NULL REFERENCES refund(id) ON DELETE CASCADE,
  order_line_id bigint NOT NULL REFERENCES order_line(id),
  warehouse_id bigint NOT NULL REFERENCES warehouse(id),
  qty numeric(12,3) NOT NULL CHECK (qty > 0),
  disposition text NOT NULL CHECK (disposition IN ('RESTOCK','DAMAGED')),
  amount numeric(19,2) NOT NULL CHECK (amount >= 0));
CREATE INDEX refund_line_refund_idx ON refund_line (refund_id);

INSERT INTO document_counter VALUES ('CREDIT_MEMO', 1);

-- Cash in (verified payments) and cash out (paid refunds) as one stream.
CREATE VIEW money_movement AS
  SELECT p.id AS id, 'PAYMENT'::text AS kind, p.order_id, p.verified_at AS at,
         p.cash_amount AS cash_amount, p.withholding_amount AS withholding_amount
    FROM payment p WHERE p.status = 'VERIFIED'
  UNION ALL
  SELECT -r.id, 'REFUND', r.order_id, r.paid_at, -r.amount, 0::numeric(19,2)
    FROM refund r WHERE r.status = 'PAID';
