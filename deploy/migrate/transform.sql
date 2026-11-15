-- Legacy `legacy.*` -> new schema. Runs inside migrate.py's transaction against an empty target.
-- One legacy invoice, or a quotation that never became one, is one sales_order. Legacy documents keep their numbers.
-- Decided 2026-11-09: legacy invoices never recorded VAT, so the whole amount is VAT-exempt with VAT 0, and new SI-
-- invoice serials continue after the highest legacy serial.

CREATE FUNCTION pg_temp.ts(t timestamp) RETURNS timestamptz LANGUAGE sql STABLE AS $$ SELECT t AT TIME ZONE current_setting('etl.tz') $$;
-- Noon in Manila, so a reprint shows exactly the legacy document date.
CREATE FUNCTION pg_temp.day(d date) RETURNS timestamptz LANGUAGE sql IMMUTABLE AS $$ SELECT (d + time '12:00') AT TIME ZONE 'Asia/Manila' $$;
CREATE FUNCTION pg_temp.key(p text) RETURNS text LANGUAGE sql IMMUTABLE AS $$ SELECT nullif(regexp_replace(trim(p), '^/?(static/)?', ''), '') $$;
CREATE FUNCTION pg_temp.label(terms text) RETURNS text LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE terms WHEN 'NET_15' THEN 'Net 15' WHEN 'NET_30' THEN 'Net 30' WHEN 'NET_60' THEN 'Net 60' ELSE 'Due on Receipt' END $$;

-- Users. Bcrypt hashes carry over, so passwords keep working; admins are made to enrol 2FA at first sign-in.
INSERT INTO app_user (email, password_hash, first_name, last_name, phone, role, archived_at, avatar_key)
SELECT lower(trim(email)), password, coalesce(nullif(trim(first_name), ''), split_part(email, '@', 1)),
       coalesce(nullif(trim(last_name), ''), '-'), nullif(trim(cellphone_number), ''),
       CASE WHEN roles ~* 'ADMIN' THEN 'ADMIN' WHEN roles ~* 'WAREHOUSE' THEN 'WAREHOUSE' ELSE 'SALES' END,
       CASE WHEN is_active IS FALSE THEN now() END, pg_temp.key(picture_path)
FROM legacy.users
ON CONFLICT (email) WHERE archived_at IS NULL DO NOTHING;

CREATE FUNCTION pg_temp.sys() RETURNS bigint LANGUAGE sql STABLE AS $$
  SELECT id FROM app_user WHERE role = 'ADMIN' ORDER BY archived_at NULLS FIRST, id LIMIT 1 $$;
CREATE FUNCTION pg_temp.uid(mail text) RETURNS bigint LANGUAGE sql STABLE AS $$
  SELECT id FROM app_user WHERE email = lower(trim(mail)) ORDER BY archived_at NULLS FIRST LIMIT 1 $$;

INSERT INTO warehouse (id, code, name, address, is_default, archived_at, created_at, updated_at) OVERRIDING SYSTEM VALUE
SELECT id, coalesce(nullif(trim(code), ''), 'WH-' || id), name, address,
       id = (SELECT id FROM legacy.warehouse ORDER BY default_warehouse DESC, active DESC, id LIMIT 1),
       CASE WHEN NOT active THEN updated_at END, pg_temp.ts(created_at), pg_temp.ts(updated_at)
FROM legacy.warehouse;

-- The old system had no warehouse scoping, so warehouse staff keep access to every warehouse.
INSERT INTO user_warehouse (user_id, warehouse_id)
SELECT u.id, w.id FROM app_user u CROSS JOIN warehouse w WHERE u.role = 'WAREHOUSE';

INSERT INTO product (id, sku, name, description, unit, price, cost, tax_class, archived_at, created_at, updated_at) OVERRIDING SYSTEM VALUE
SELECT id, 'LEGACY-' || id, product_name, description,
       (ARRAY['KG', 'L', 'BOX', 'ML', 'PC', 'PC'])[coalesce(unit, 4) + 1], -- legacy enum ordinal; ROLL has no new equivalent
       price, cost, 'VATABLE', CASE WHEN NOT active THEN pg_temp.ts(updated_at) END, pg_temp.ts(created_at), pg_temp.ts(updated_at)
FROM legacy.product;

INSERT INTO customer (id, type, display_name, business_name, tin, address, phone, email, payment_terms, assigned_rep_id, notes,
                      archived_at, created_at, updated_at) OVERRIDING SYSTEM VALUE
SELECT c.id, coalesce(c.customer_type, 'INDIVIDUAL'),
       coalesce(nullif(trim(c.business_name), ''), trim(c.first_name || ' ' || c.last_name)),
       nullif(trim(c.business_name), ''), nullif(trim(c.tin), ''), c.address, nullif(trim(c.phone_number), ''), nullif(trim(c.email), ''),
       coalesce((SELECT q.payment_terms FROM legacy.quotation q WHERE q.customer_id = c.id AND q.payment_terms IS NOT NULL ORDER BY q.id DESC LIMIT 1), 'DUE_ON_RECEIPT'),
       pg_temp.uid(c.assigned_rep_email),
       nullif(concat_ws(E'\n', 'Source: ' || nullif(trim(c.source), ''), 'Preferred shipping: ' || nullif(trim(c.preferred_shipping_method), ''),
                        'Business type: ' || c.business_type), ''),
       CASE WHEN NOT c.active THEN pg_temp.ts(c.updated_at) END, pg_temp.ts(c.created_at), pg_temp.ts(c.updated_at)
FROM legacy.customers c;

-- A contact email is unique across the portal, so a repeated email goes to the earliest customer only (see review.sql).
INSERT INTO customer_contact (customer_id, name, email, phone, created_at, updated_at)
SELECT id, trim(first_name || ' ' || last_name), lower(trim(email)), nullif(trim(phone_number), ''), pg_temp.ts(created_at), pg_temp.ts(updated_at)
FROM (SELECT *, row_number() OVER (PARTITION BY lower(trim(email)) ORDER BY id) rn FROM legacy.customers WHERE trim(email) <> '') c
WHERE rn = 1;

-- Orders
CREATE TEMP TABLE o ON COMMIT DROP AS
WITH inv AS (SELECT *, row_number() OVER (PARTITION BY quotation_id ORDER BY id) rn FROM legacy.invoice),
     src AS (SELECT i.id invoice_id, CASE WHEN i.rn = 1 THEN i.quotation_id END quotation_id FROM inv i
             UNION ALL
             SELECT NULL, q.id FROM legacy.quotation q WHERE NOT EXISTS (SELECT 1 FROM legacy.invoice i WHERE i.quotation_id = q.id))
SELECT row_number() OVER (ORDER BY coalesce(q.created_at, i.created_at), s.invoice_id)::bigint id, s.invoice_id, s.quotation_id,
       coalesce(q.quotation_number, i.invoice_number, 'LEGACY-Q-' || q.id) number,
       coalesce(i.customer_id, q.customer_id) customer_id, coalesce(i.sales_rep_email, q.sales_rep_email) rep_email,
       coalesce(i.payment_terms, q.payment_terms, 'DUE_ON_RECEIPT') terms, coalesce(i.total_amount, q.total_amount, 0) total,
       coalesce(i.shipping_charges, q.shipping_charges, 0) shipping, coalesce(i.notes, q.notes) notes,
       coalesce(i.terms_and_conditions, q.terms_and_conditions) t_and_c, q.expiry_date, q.quote_date, q.status quote_status,
       i.status invoice_status, i.invoice_date, i.due_date, i.paid_at, i.created_at invoice_created_at,
       coalesce(q.created_at, i.created_at) created_at, coalesce(i.updated_at, q.updated_at) updated_at,
       d.id do_id, d.status do_status, d.created_at do_created_at, d.delivered_at,
       -- Older invoices were delivered without a delivery order; only the stock ledger or the invoice itself says so.
       coalesce(i.delivered_at, (SELECT max(m.created_at) FROM legacy.stock_movement m WHERE m.type = 'DELIVERY' AND m.reference_type = 'INVOICE' AND m.reference_id = s.invoice_id)) invoice_delivered_at,
       coalesce((SELECT m.warehouse_id FROM legacy.stock_movement m
                 WHERE (m.reference_type = 'INVOICE' AND m.reference_id = s.invoice_id) OR (m.reference_type = 'QUOTATION' AND m.reference_id = s.quotation_id)
                 ORDER BY m.id DESC LIMIT 1), (SELECT id FROM warehouse WHERE is_default)) warehouse_id,
       coalesce((SELECT sum(CASE m.type WHEN 'RESERVE' THEN m.quantity ELSE -m.quantity END) FROM legacy.stock_movement m
                 WHERE m.reference_type = 'QUOTATION' AND m.reference_id = s.quotation_id AND m.type IN ('RESERVE', 'RELEASE')), 0) net_reserved
FROM src s
LEFT JOIN legacy.invoice i ON i.id = s.invoice_id
LEFT JOIN legacy.quotation q ON q.id = s.quotation_id
LEFT JOIN LATERAL (SELECT * FROM legacy.delivery_order x WHERE x.invoice_id = s.invoice_id ORDER BY x.id DESC LIMIT 1) d ON true;

ALTER TABLE o ADD COLUMN status text, ADD COLUMN void boolean;
UPDATE o SET void = coalesce(invoice_status ~* 'VOID|CANCEL', false);
UPDATE o SET status = CASE
    WHEN void THEN 'CANCELLED'
    WHEN do_status = 'DELIVERED' OR (do_id IS NULL AND invoice_delivered_at IS NOT NULL) THEN 'FULFILLED'
    WHEN do_id IS NOT NULL THEN 'RELEASED'
    -- Paid before stock tracking existed: nothing is reserved and no delivery was recorded, so there is nothing left to ship.
    WHEN invoice_status = 'PAID' AND net_reserved = 0 THEN 'FULFILLED'
    WHEN invoice_id IS NOT NULL THEN 'CONFIRMED'
    WHEN quote_status ~* 'DRAFT' THEN 'DRAFT'
    WHEN quote_status ~* 'REJECT|DECLIN|CANCEL' THEN 'CANCELLED'
    WHEN quote_status ~* 'ACCEPT' AND net_reserved > 0 THEN 'CONFIRMED'
    ELSE 'QUOTED' END;

-- Lines: items hang off the *_item_details join tables or, on older rows, a direct foreign key.
CREATE TEMP TABLE l ON COMMIT DROP AS
WITH items AS (
    SELECT o.id order_id, x.id, x.product_id, x.quantity qty, x.price, coalesce(x.total, x.price * x.quantity) total
    FROM o JOIN legacy.invoice_item x ON x.id IN (SELECT invoice_item_id FROM legacy.invoice_item_details d WHERE d.invoice_id = o.invoice_id) OR x.invoice_id = o.invoice_id
    UNION
    SELECT o.id, x.id, x.product_id, x.quantity, x.price, coalesce(x.total, x.price * x.quantity)
    FROM o JOIN legacy.quotation_item x ON o.invoice_id IS NULL
         AND (x.id IN (SELECT quotation_item_id FROM legacy.quotation_item_details d WHERE d.quotation_id = o.quotation_id) OR x.quotation_id = o.quotation_id)),
n AS (SELECT i.*, row_number() OVER w rn, count(*) OVER (PARTITION BY order_id) cnt, sum(total) OVER (PARTITION BY order_id) lines_sum
      FROM items i WHERE qty > 0 AND product_id IS NOT NULL WINDOW w AS (PARTITION BY order_id ORDER BY id))
SELECT n.*, round(CASE WHEN lines_sum > 0 THEN n.total * (o.total - o.shipping) / lines_sum ELSE n.total END, 2) line_total
FROM n JOIN o ON o.id = n.order_id;

-- Rounding and order-level discounts are spread over the lines; the last line takes the cents so lines + shipping = total.
UPDATE l SET line_total = l.line_total + (o.total - o.shipping - s.lines)
FROM o, (SELECT order_id, sum(line_total) lines FROM l GROUP BY 1) s
WHERE o.id = l.order_id AND s.order_id = l.order_id AND l.rn = l.cnt AND l.lines_sum > 0;

INSERT INTO sales_order (id, number, customer_id, rep_id, status, payment_terms, quote_valid_until, shipping_fee, notes, terms,
                         vatable_sales, vat, vat_exempt_sales, zero_rated_sales, total, settled_amount, due_date, quoted_at, customer_accepted_at,
                         confirmed_at, released_at, fulfilled_at, cancelled_at, cancel_reason, created_by, created_at, updated_at, ship_from_warehouse_id)
OVERRIDING SYSTEM VALUE
SELECT id, number, customer_id, pg_temp.uid(rep_email), status, terms, expiry_date, shipping, notes, t_and_c,
       0, 0, total, 0, total, 0, due_date, pg_temp.day(quote_date),
       CASE WHEN quote_status ~* 'ACCEPT' THEN pg_temp.ts(updated_at) END,
       CASE WHEN status IN ('CONFIRMED', 'RELEASED', 'FULFILLED') THEN pg_temp.ts(coalesce(invoice_created_at, updated_at)) END,
       CASE WHEN status IN ('RELEASED', 'FULFILLED') THEN pg_temp.ts(coalesce(do_created_at, invoice_delivered_at, paid_at)) END,
       CASE WHEN status = 'FULFILLED' THEN pg_temp.ts(coalesce(delivered_at, invoice_delivered_at, paid_at)) END,
       CASE WHEN status = 'CANCELLED' THEN pg_temp.ts(updated_at) END,
       CASE WHEN status = 'CANCELLED' THEN 'Legacy status: ' || coalesce(invoice_status, quote_status) END,
       coalesce(pg_temp.uid(rep_email), pg_temp.sys()), pg_temp.ts(created_at), pg_temp.ts(updated_at), warehouse_id
FROM o;

INSERT INTO order_line (order_id, position, product_id, description, unit, qty, unit_price, discount_pct, tax_class, line_total, unit_cost)
SELECT l.order_id, l.rn, l.product_id, p.name, p.unit, l.qty, coalesce(l.price, 0),
       CASE WHEN l.price * l.qty > 0 THEN greatest(0, least(100, round(100 * (1 - l.line_total / (l.price * l.qty)), 2))) ELSE 0 END,
       'VAT_EXEMPT', l.line_total, p.cost
FROM l JOIN product p ON p.id = l.product_id;

INSERT INTO invoice (number, order_id, order_revision, status, issued_at, due_date, customer_snapshot, lines_snapshot,
                     vatable_sales, vat, vat_exempt_sales, zero_rated_sales, shipping_fee, total, void_reason, voided_by, voided_at, issued_by)
SELECT coalesce(o.number_inv, 'LEGACY-I-' || o.invoice_id), o.id, 0, CASE WHEN o.void THEN 'VOID' ELSE 'ISSUED' END,
       pg_temp.day(coalesce(o.invoice_date, o.invoice_created_at::date)), coalesce(o.due_date, o.invoice_date, o.invoice_created_at::date),
       jsonb_build_object('name', coalesce(c.business_name, c.display_name), 'tin', c.tin, 'address', c.address, 'paymentTerms', pg_temp.label(o.terms)),
       coalesce((SELECT jsonb_agg(jsonb_build_object('productId', ol.product_id, 'description', ol.description, 'unit', ol.unit, 'qty', ol.qty,
                                                     'unitPrice', ol.unit_price, 'discountPct', ol.discount_pct, 'taxClass', ol.tax_class, 'lineTotal', ol.line_total) ORDER BY ol.position)
                 FROM order_line ol WHERE ol.order_id = o.id), '[]'::jsonb),
       0, 0, o.total, 0, o.shipping, o.total,
       CASE WHEN o.void THEN 'Legacy status: ' || o.invoice_status END, CASE WHEN o.void THEN pg_temp.sys() END, CASE WHEN o.void THEN pg_temp.ts(o.updated_at) END,
       coalesce(pg_temp.uid(o.rep_email), pg_temp.sys())
FROM (SELECT o.*, i.invoice_number number_inv FROM o JOIN legacy.invoice i ON i.id = o.invoice_id) o
JOIN customer c ON c.id = o.customer_id;

-- Payments: recorded rows first, then whatever a PAID or awaiting-verification invoice still needs to reach its total.
INSERT INTO payment (order_id, status, method, cash_amount, received_on, reference, proof_key, submitted_by_type, submitted_by_id, verified_by, verified_at, created_at, updated_at)
SELECT o.id, 'VERIFIED', CASE p.method WHEN 'CREDIT_CARD' THEN 'CARD' ELSE coalesce(p.method, 'OTHER') END, p.amount,
       coalesce(p.recorded_at, p.created_at)::date, 'Migrated from legacy', pg_temp.key(p.proof_of_payment_path), 'USER',
       coalesce(pg_temp.uid(o.rep_email), pg_temp.sys()), pg_temp.sys(), pg_temp.ts(coalesce(p.recorded_at, p.created_at)), pg_temp.ts(p.created_at), pg_temp.ts(p.updated_at)
FROM legacy.invoice_payment p JOIN o ON o.invoice_id = p.invoice_id WHERE p.amount > 0 AND NOT o.void;

INSERT INTO payment (order_id, status, method, cash_amount, received_on, reference, proof_key, submitted_by_type, submitted_by_id, verified_by, verified_at, created_at, updated_at)
SELECT o.id, CASE WHEN i.status = 'PAID' THEN 'VERIFIED' ELSE 'PENDING' END, 'OTHER', o.total - s.paid,
       coalesce(i.paid_at, i.updated_at)::date, 'Migrated from legacy: balance marked ' || i.status, pg_temp.key(i.proof_of_payment_path), 'USER',
       coalesce(pg_temp.uid(o.rep_email), pg_temp.sys()), CASE WHEN i.status = 'PAID' THEN pg_temp.sys() END,
       CASE WHEN i.status = 'PAID' THEN pg_temp.ts(coalesce(i.paid_at, i.updated_at)) END, pg_temp.ts(coalesce(i.paid_at, i.updated_at)), pg_temp.ts(i.updated_at)
FROM o JOIN legacy.invoice i ON i.id = o.invoice_id
CROSS JOIN LATERAL (SELECT coalesce(sum(p.amount), 0) paid FROM legacy.invoice_payment p WHERE p.invoice_id = i.id AND p.amount > 0) s
WHERE i.status IN ('PAID', 'FOR_PAYMENT_VERIFICATION') AND NOT o.void AND o.total > s.paid;

UPDATE sales_order so SET settled_amount = v.sum
FROM (SELECT order_id, sum(cash_amount + withholding_amount) sum FROM payment WHERE status = 'VERIFIED' GROUP BY 1) v WHERE v.order_id = so.id;

-- Delivery orders
INSERT INTO delivery_order (number, sales_order_id, warehouse_id, method, status, dispatched_at, dispatched_by, delivered_at, delivered_by, proof_key,
                            customer_name, ship_to, created_at, updated_at, order_number, rep_user_id)
SELECT coalesce(d.delivery_order_number, 'LEGACY-D-' || d.id), o.id, o.warehouse_id, 'DELIVERY',
       CASE d.status WHEN 'DELIVERED' THEN 'DELIVERED' WHEN 'PICKED_UP' THEN 'DISPATCHED' ELSE 'PICKING' END,
       pg_temp.ts(CASE WHEN d.status IN ('DELIVERED', 'PICKED_UP') THEN coalesce(d.picked_up_at, d.delivered_at) END), CASE WHEN d.status IN ('DELIVERED', 'PICKED_UP') THEN pg_temp.sys() END,
       pg_temp.ts(CASE WHEN d.status = 'DELIVERED' THEN d.delivered_at END), CASE WHEN d.status = 'DELIVERED' THEN pg_temp.sys() END,
       coalesce((SELECT pg_temp.key(max(x.file_path)) FROM legacy.delivery_order_proof_of_delivery x WHERE x.delivery_order_id = d.id), pg_temp.key(d.proof_of_delivery_path)),
       c.display_name, d.delivery_address, pg_temp.ts(d.created_at), pg_temp.ts(d.updated_at), o.number, coalesce(pg_temp.uid(o.rep_email), pg_temp.sys())
FROM legacy.delivery_order d JOIN o ON o.invoice_id = d.invoice_id JOIN customer c ON c.id = o.customer_id;

INSERT INTO delivery_order_line (delivery_order_id, order_line_id, product_id, qty, picked_qty, description, unit)
SELECT d.id, ol.id, ol.product_id, ol.qty, CASE WHEN d.status IN ('DISPATCHED', 'DELIVERED') THEN ol.qty END, ol.description, ol.unit
FROM delivery_order d JOIN order_line ol ON ol.order_id = d.sales_order_id;

-- Every other proof has no single-file column in the new model, so it is kept as an order attachment.
INSERT INTO attachment (subject_type, subject_id, object_key, file_name, content_type, size_bytes, note, uploaded_by, uploaded_at)
SELECT 'ORDER', p.order_id, p.key, regexp_replace(p.key, '^.*/', ''), f.content_type, f.size_bytes, p.note, pg_temp.sys(), now()
FROM (SELECT DISTINCT ON (key) * FROM (
        SELECT o.id order_id, pg_temp.key(x.file_path) key, 'Legacy proof of delivery' note FROM legacy.delivery_order_proof_of_delivery x JOIN legacy.delivery_order d ON d.id = x.delivery_order_id JOIN o ON o.invoice_id = d.invoice_id
        UNION ALL SELECT o.id, pg_temp.key(x.file_path), 'Legacy proof of pickup' FROM legacy.delivery_order_proof_of_pickup x JOIN legacy.delivery_order d ON d.id = x.delivery_order_id JOIN o ON o.invoice_id = d.invoice_id
        UNION ALL SELECT o.id, pg_temp.key(d.proof_of_pickup_path), 'Legacy proof of pickup' FROM legacy.delivery_order d JOIN o ON o.invoice_id = d.invoice_id
        UNION ALL SELECT o.id, pg_temp.key(i.proof_of_payment_path), 'Legacy proof of payment' FROM legacy.invoice i JOIN o ON o.invoice_id = i.id
        UNION ALL SELECT o.id, pg_temp.key(i.proof_of_delivery_path), 'Legacy proof of delivery' FROM legacy.invoice i JOIN o ON o.invoice_id = i.id) a
      WHERE key IS NOT NULL ORDER BY key, note) p
JOIN legacy.file_stat f ON f.key = p.key AND f.present
WHERE p.key NOT IN (SELECT proof_key FROM delivery_order WHERE proof_key IS NOT NULL UNION SELECT proof_key FROM payment WHERE proof_key IS NOT NULL);

-- Stock
INSERT INTO stock_level (product_id, warehouse_id, on_hand, reserved, created_at, updated_at)
SELECT product_id, warehouse_id, quantity_on_hand, 0, pg_temp.ts(created_at), pg_temp.ts(updated_at) FROM legacy.inventory_item;

INSERT INTO stock_reservation (order_line_id, sales_order_id, order_number, rep_user_id, product_id, warehouse_id, reserved_qty, short_qty, confirmed_at)
SELECT ol.id, so.id, so.number, so.rep_id, ol.product_id, so.ship_from_warehouse_id,
       CASE WHEN o.net_reserved > 0 THEN ol.qty ELSE 0 END, CASE WHEN o.net_reserved > 0 THEN 0 ELSE ol.qty END, so.confirmed_at
FROM sales_order so JOIN o ON o.id = so.id JOIN order_line ol ON ol.order_id = so.id
LEFT JOIN delivery_order d ON d.sales_order_id = so.id
WHERE so.status = 'CONFIRMED' OR (so.status = 'RELEASED' AND d.status = 'PICKING');

UPDATE stock_level s SET reserved = r.qty
FROM (SELECT product_id, warehouse_id, sum(reserved_qty) qty FROM stock_reservation GROUP BY 1, 2) r
WHERE r.product_id = s.product_id AND r.warehouse_id = s.warehouse_id;

-- The ledger must sum to on_hand: legacy receipts and deliveries, then an opening adjustment for whatever predates them.
INSERT INTO stock_movement (product_id, warehouse_id, type, qty, ref_type, ref_id, reason, created_by, created_at)
SELECT product_id, warehouse_id, CASE type WHEN 'RECEIPT' THEN 'RECEIPT' ELSE 'DISPATCH' END,
       CASE type WHEN 'RECEIPT' THEN quantity ELSE -abs(quantity) END, 'LEGACY_' || reference_type, reference_id, notes,
       coalesce(pg_temp.uid(performed_by_email), pg_temp.sys()), pg_temp.ts(created_at)
FROM legacy.stock_movement WHERE type IN ('RECEIPT', 'DELIVERY');

INSERT INTO stock_movement (product_id, warehouse_id, type, qty, reason, created_by, created_at)
SELECT s.product_id, s.warehouse_id, 'ADJUSTMENT', s.on_hand - m.qty, 'Opening balance from legacy system', pg_temp.sys(),
       coalesce(m.first_at, now()) - interval '1 second'
FROM stock_level s
CROSS JOIN LATERAL (SELECT coalesce(sum(x.qty), 0) qty, min(x.created_at) first_at FROM stock_movement x WHERE x.product_id = s.product_id AND x.warehouse_id = s.warehouse_id) m
WHERE s.on_hand <> m.qty;

-- Customer activity becomes completed follow-ups, which show on the customer page.
INSERT INTO follow_up (customer_id, assignee_id, title, note, due_date, done_at, done_by, created_by, created_at, updated_at)
SELECT a.customer_id, u.id, initcap(replace(a.type, '_', ' ')), left(a.notes, 2000), (pg_temp.ts(a.created_at) AT TIME ZONE 'Asia/Manila')::date, pg_temp.ts(a.created_at), u.id, u.id, pg_temp.ts(a.created_at), pg_temp.ts(a.updated_at)
FROM legacy.customer_activity a
JOIN customer c ON c.id = a.customer_id
CROSS JOIN LATERAL (SELECT coalesce(pg_temp.uid(a.created_by_email), c.assigned_rep_id, pg_temp.sys()) id) u;

UPDATE document_counter SET next_value = greatest(next_value, coalesce((SELECT max(substring(invoice_number FROM '(\d+)$')::bigint) + 1 FROM legacy.invoice), 1)) WHERE name = 'INVOICE';

SELECT setval(pg_get_serial_sequence('customer', 'id'), greatest((SELECT max(id) FROM customer), 1));
SELECT setval(pg_get_serial_sequence('product', 'id'), greatest((SELECT max(id) FROM product), 1));
SELECT setval(pg_get_serial_sequence('warehouse', 'id'), greatest((SELECT max(id) FROM warehouse), 1));
SELECT setval(pg_get_serial_sequence('sales_order', 'id'), greatest((SELECT max(id) FROM sales_order), 1));
