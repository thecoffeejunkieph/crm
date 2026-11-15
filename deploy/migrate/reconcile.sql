-- check, legacy value, migrated value. Any row where the two differ blocks the commit.
WITH inv AS (
    SELECT i.invoice_number number, i.total_amount FROM legacy.invoice i
), items AS (
    SELECT x.id FROM legacy.invoice_item x
    WHERE x.quantity > 0 AND x.product_id IS NOT NULL
      AND (x.invoice_id IS NOT NULL OR x.id IN (SELECT invoice_item_id FROM legacy.invoice_item_details))
)
SELECT check_name, trim_scale(legacy)::text, trim_scale(migrated)::text FROM (VALUES
  ('rows: users', (SELECT count(*) FROM legacy.users)::numeric, (SELECT count(*) FROM app_user u WHERE u.email IN (SELECT lower(trim(email)) FROM legacy.users))::numeric),
  ('rows: customers', (SELECT count(*) FROM legacy.customers), (SELECT count(*) FROM customer)),
  ('rows: products', (SELECT count(*) FROM legacy.product), (SELECT count(*) FROM product)),
  ('rows: warehouses', (SELECT count(*) FROM legacy.warehouse), (SELECT count(*) FROM warehouse)),
  ('rows: invoices', (SELECT count(*) FROM legacy.invoice), (SELECT count(*) FROM invoice)),
  ('rows: orders (invoices + uninvoiced quotations)',
     (SELECT count(*) FROM legacy.invoice) + (SELECT count(*) FROM legacy.quotation q WHERE NOT EXISTS (SELECT 1 FROM legacy.invoice i WHERE i.quotation_id = q.id)),
     (SELECT count(*) FROM sales_order)),
  ('rows: invoice lines', (SELECT count(*) FROM items), (SELECT count(*) FROM order_line ol JOIN invoice i ON i.order_id = ol.order_id)),
  ('rows: delivery orders', (SELECT count(*) FROM legacy.delivery_order), (SELECT count(*) FROM delivery_order)),
  ('rows: customer activity', (SELECT count(*) FROM legacy.customer_activity), (SELECT count(*) FROM follow_up)),
  ('money: invoice total', (SELECT coalesce(sum(total_amount), 0) FROM legacy.invoice), (SELECT coalesce(sum(total), 0) FROM invoice)),
  ('invoices whose total differs', 0, (SELECT count(*) FROM inv JOIN invoice n ON n.number = inv.number WHERE n.total <> inv.total_amount)),
  ('invoices with a VAT amount', 0, (SELECT count(*) FROM invoice WHERE vat <> 0 OR vatable_sales <> 0)),
  ('money: paid invoices settled', (SELECT coalesce(sum(total_amount), 0) FROM legacy.invoice WHERE status = 'PAID'),
     (SELECT coalesce(sum(so.settled_amount), 0) FROM sales_order so JOIN invoice i ON i.order_id = so.id JOIN legacy.invoice li ON li.invoice_number = i.number WHERE li.status = 'PAID')),
  ('money: recorded payments preserved', (SELECT coalesce(sum(amount), 0) FROM legacy.invoice_payment WHERE amount > 0), (SELECT coalesce(sum(cash_amount), 0) FROM payment WHERE reference = 'Migrated from legacy')),
  ('orders: lines + shipping differ from total', 0, (SELECT count(*) FROM sales_order so WHERE EXISTS (SELECT 1 FROM order_line ol WHERE ol.order_id = so.id)
     AND so.total <> so.shipping_fee + (SELECT sum(line_total) FROM order_line ol WHERE ol.order_id = so.id))),
  ('orders: settled above total', 0, (SELECT count(*) FROM sales_order WHERE settled_amount > total)),
  ('stock: total on hand', (SELECT coalesce(sum(quantity_on_hand), 0) FROM legacy.inventory_item), (SELECT coalesce(sum(on_hand), 0) FROM stock_level)),
  ('stock: levels whose on hand differs', 0, (SELECT count(*) FROM legacy.inventory_item x JOIN stock_level s ON s.product_id = x.product_id AND s.warehouse_id = x.warehouse_id WHERE s.on_hand <> x.quantity_on_hand)),
  ('stock: levels whose reserved differs', 0, (SELECT count(*) FROM legacy.inventory_item x JOIN stock_level s ON s.product_id = x.product_id AND s.warehouse_id = x.warehouse_id WHERE s.reserved <> x.quantity_reserved)),
  ('stock: ledger differs from on hand', 0, (SELECT count(*) FROM stock_level s WHERE s.on_hand <> (SELECT coalesce(sum(m.qty), 0) FROM stock_movement m WHERE m.product_id = s.product_id AND m.warehouse_id = s.warehouse_id))),
  ('files: referenced but missing on disk', 0, (SELECT count(*) FROM legacy.file_stat WHERE NOT present)),
  ('invoice serial: next after legacy', (SELECT coalesce(max(substring(invoice_number FROM '(\d+)$')::bigint), 0) + 1 FROM legacy.invoice), (SELECT next_value FROM document_counter WHERE name = 'INVOICE'))
) AS t(check_name, legacy, migrated);
