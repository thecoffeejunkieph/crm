-- Not failures: judgement calls the migration made, for someone to look over after a dry run.
SELECT line FROM (
  SELECT 1 o, count(*) || ' user(s) with an unrecognised role became SALES: ' || string_agg(email, ', ') line FROM legacy.users WHERE roles !~* 'ADMIN|SALES|WAREHOUSE' HAVING count(*) > 0
  UNION ALL SELECT 2, count(*) || ' customer(s) share an email with an earlier customer and got no portal contact: ' || string_agg(id::text, ', ')
    FROM (SELECT id, row_number() OVER (PARTITION BY lower(trim(email)) ORDER BY id) rn FROM legacy.customers WHERE trim(email) <> '') c WHERE rn > 1 HAVING count(*) > 0
  UNION ALL SELECT 3, count(*) || ' product(s) with unit ROLL became PC' FROM legacy.product WHERE unit = 5 HAVING count(*) > 0
  UNION ALL SELECT 4, count(*) || ' product picture(s) have no place in the new model; the files stay in the bucket' FROM legacy.product WHERE trim(picture_path) <> '' HAVING count(*) > 0
  UNION ALL SELECT 5, count(*) || ' invoice(s) reached their total with a generated payment, because the legacy system only marked them PAID'
    FROM payment WHERE reference LIKE 'Migrated from legacy: balance%' AND status = 'VERIFIED' HAVING count(*) > 0
  UNION ALL SELECT 6, count(*) || ' payment(s) await verification' FROM payment WHERE status = 'PENDING' HAVING count(*) > 0
  UNION ALL SELECT 7, count(*) || ' file(s) are missing on disk: ' || left(string_agg(key, ', '), 400) FROM legacy.file_stat WHERE NOT present HAVING count(*) > 0
  UNION ALL SELECT 8, count(*) || ' opening stock adjustment(s) were added so the ledger matches on hand' FROM stock_movement WHERE reason = 'Opening balance from legacy system' HAVING count(*) > 0
  UNION ALL SELECT 10, count(*) || ' paid invoice(s) had no delivery record and were marked FULFILLED' FROM sales_order so WHERE status = 'FULFILLED' AND NOT EXISTS (SELECT 1 FROM delivery_order d WHERE d.sales_order_id = so.id) AND NOT EXISTS (SELECT 1 FROM legacy.stock_movement m JOIN legacy.invoice i ON i.id = m.reference_id AND m.reference_type = 'INVOICE' JOIN invoice n ON n.number = i.invoice_number WHERE n.order_id = so.id AND m.type = 'DELIVERY') HAVING count(*) > 0
  UNION ALL SELECT 11, count(*) || ' open order line(s) had nothing reserved in legacy and are short until stock is allocated' FROM stock_reservation WHERE short_qty > 0 HAVING count(*) > 0
  UNION ALL SELECT 9, count(*) || ' accepted quotation(s) had no invoice and stay QUOTED' FROM sales_order WHERE status = 'QUOTED' AND customer_accepted_at IS NOT NULL HAVING count(*) > 0
) r ORDER BY o;
