-- =============================================================================
-- CRM dummy data (MySQL)
--
-- Covers every table except `users`.
-- Explicit IDs are used so foreign keys are deterministic; run top to bottom.
--
-- NOTE: `customers.status` is NOT NULL in the database but no longer exists on
-- the Customer entity (leftover from ddl-auto=update). It is populated here
-- because the insert would otherwise fail. The other drifted columns
-- (billing_address, contact_name, phone, tax_id) are nullable and left NULL,
-- which is what the current entity would write.
--
-- Quotation numbers follow QuotationNumberGenerator's `QTN-<year>-<0000>`
-- format, and the highest ID holds the highest number, so the next generated
-- number continues cleanly at QTN-2026-0011.
-- =============================================================================

-- Optional reset, in FK-safe order. Uncomment only if you want the existing
-- rows in these tables destroyed first.
-- DELETE FROM quotation_item_details;
-- DELETE FROM quotation_products;
-- DELETE FROM customer_quotation;
-- DELETE FROM quotation_item;
-- DELETE FROM product;
-- DELETE FROM quotation;
-- DELETE FROM customers;

START TRANSACTION;

-- -----------------------------------------------------------------------------
-- customers
-- -----------------------------------------------------------------------------
INSERT INTO customers (id, created_at, updated_at, first_name, last_name, email, phone_number, address, preferred_shipping_method) VALUES
(1, '2026-01-12 09:14:22.000000', '2026-02-03 10:02:11.000000', 'Maria',    'Santos',      'maria.santos@brewhaven.ph',          '+63 917 555 0101', '12 Rizal Ave, Makati City, Metro Manila 1200',      'LBC Express'),
(2, '2026-01-19 14:31:07.000000', '2026-02-18 08:45:59.000000', 'Juan',     'Dela Cruz',   'juan.delacruz@cafelumina.ph',        '+63 918 555 0142', '88 Session Rd, Baguio City, Benguet 2600',          'J&T Express'),
(3, '2026-02-02 11:05:48.000000', '2026-07-02 16:20:33.000000', 'Angelica', 'Reyes',       'angelica.reyes@thedailygrind.ph',    '+63 920 555 0177', '5F One Global Place, BGC, Taguig 1634',             'Grab Express'),
(4, '2026-03-08 10:22:15.000000', '2026-04-14 09:11:04.000000', 'Miguel',   'Torres',      'miguel.torres@bukoandbean.ph',       '+63 921 555 0198', '24 Magsaysay Blvd, Naga City, Camarines Sur 4400',  '2GO Freight'),
-- no preferred shipping method on file (the column is optional)
(5, '2026-03-25 15:47:39.000000', '2026-05-05 13:30:26.000000', 'Katrina',  'Villanueva',  'katrina.villanueva@sipnorth.ph',     '+63 922 555 0203', 'Unit 7, Ayala Center, Cebu City 6000',              NULL),
(6, '2026-04-17 08:56:12.000000', '2026-06-11 11:41:50.000000', 'Rafael',   'Bautista',    'rafael.bautista@morningmugs.ph',     NULL,               '3 Bonifacio St, Iloilo City 5000',                  'Pickup at Warehouse'),
(7, '2025-11-06 13:18:44.000000', '2026-07-21 09:05:17.000000', 'Liza',     'Domingo',     'liza.domingo@steamandsteep.ph',      '+63 927 555 0266', '17 Quezon Ave, Lucena City, Quezon 4301',           'Flash Express'),
(8, '2025-12-14 16:02:53.000000', '2026-08-10 14:55:08.000000', 'Paolo',    'Cruz',        'paolo.cruz@cupsandcrates.ph',        '+63 928 555 0281', NULL,                                                'Company Courier');

-- -----------------------------------------------------------------------------
-- quotation
-- total_amount = SUM(quotation_item.total) + shipping_charges
-- -----------------------------------------------------------------------------
INSERT INTO quotation (id, created_at, updated_at, quotation_number, customer_id, status, quote_date, expiry_date, shipping_charges, total_amount, notes, terms_and_conditions) VALUES
(1,  '2026-02-03 10:02:11.000000', '2026-02-06 09:20:44.000000', 'QTN-2026-0001', 1, 'ACCEPTED', '2026-02-03 00:00:00.000000', '2026-03-05 00:00:00.000000',  350.00, 12040.00, 'Repeat order for the Makati branch.',           'Payment within 30 days. Prices valid until expiry date.'),
(2,  '2026-02-18 08:45:59.000000', '2026-02-21 15:12:30.000000', 'QTN-2026-0002', 2, 'ACCEPTED', '2026-02-18 00:00:00.000000', '2026-03-20 00:00:00.000000',  500.00, 19730.00, 'Cold-chain not required. Deliver before 10 AM.', 'Payment within 30 days. Prices valid until expiry date.'),
(3,  '2026-03-09 09:33:18.000000', '2026-03-09 09:33:18.000000', 'QTN-2026-0003', 3, 'SENT',     '2026-03-09 00:00:00.000000', '2026-04-08 00:00:00.000000',    0.00, 14700.00, 'Free delivery within BGC.',                     'Payment on delivery. Prices valid until expiry date.'),
(4,  '2026-03-27 14:07:52.000000', '2026-04-02 11:48:09.000000', 'QTN-2026-0004', 1, 'REJECTED', '2026-03-27 00:00:00.000000', '2026-04-26 00:00:00.000000',  350.00,  7610.00, 'Client deferred the cold brew line for now.',   'Payment within 30 days. Prices valid until expiry date.'),
(5,  '2026-04-14 09:11:04.000000', '2026-04-18 16:39:21.000000', 'QTN-2026-0005', 4, 'ACCEPTED', '2026-04-14 00:00:00.000000', '2026-05-14 00:00:00.000000', 1200.00, 24317.50, 'Bulk order, provincial freight applies.',       'Payment 50% down, 50% on delivery.'),
(6,  '2026-05-05 13:30:26.000000', '2026-06-05 00:15:00.000000', 'QTN-2026-0006', 5, 'EXPIRED',  '2026-05-05 00:00:00.000000', '2026-06-04 00:00:00.000000',  275.00,  4317.00, 'No response after two follow-ups.',             'Payment within 15 days. Prices valid until expiry date.'),
(7,  '2026-06-11 11:41:50.000000', '2026-06-11 11:41:50.000000', 'QTN-2026-0007', 6, 'DRAFT',    '2026-06-11 00:00:00.000000', '2026-07-11 00:00:00.000000',    0.00,  8180.00, 'Customer picks up at the Iloilo warehouse.',    NULL),
(8,  '2026-07-02 16:20:33.000000', '2026-07-04 10:27:45.000000', 'QTN-2026-0008', 3, 'SENT',     '2026-07-02 00:00:00.000000', '2026-08-01 00:00:00.000000',  450.00, 19705.00, 'Second outlet opening in Ortigas.',             'Payment within 30 days. Prices valid until expiry date.'),
(9,  '2026-07-21 09:05:17.000000', '2026-08-21 00:15:00.000000', 'QTN-2026-0009', 7, 'EXPIRED',  '2026-07-21 00:00:00.000000', '2026-08-20 00:00:00.000000',  400.00, 10478.75, 'Account archived before approval.',             'Payment within 15 days. Prices valid until expiry date.'),
(10, '2026-08-10 14:55:08.000000', '2026-08-10 14:55:08.000000', 'QTN-2026-0010', 8, 'DRAFT',    '2026-08-10 00:00:00.000000', '2026-09-09 00:00:00.000000',  600.00, 17130.00, 'Awaiting confirmation of the bean volume.',     'Payment within 30 days. Prices valid until expiry date.');

-- -----------------------------------------------------------------------------
-- product
-- unit is the Unit enum ordinal: 0=KG, 1=L, 2=BOX, 3=ML, 4=PCS, 5=ROLL
-- -----------------------------------------------------------------------------
INSERT INTO product (id, created_at, updated_at, product_name, description, unit, price, quotation_id) VALUES
(1,  '2026-02-03 10:02:11.000000', '2026-02-03 10:02:11.000000', 'Arabica Beans - Benguet Medium Roast',  'Single-origin medium roast, 1kg vacuum pack', 0,  780.00, 1),
(2,  '2026-02-03 10:02:11.000000', '2026-02-03 10:02:11.000000', 'Robusta Beans - Cavite Dark Roast',     'Bold-bodied dark roast, 1kg vacuum pack',    0,  620.00, 1),
(3,  '2026-02-03 10:02:11.000000', '2026-02-03 10:02:11.000000', 'Vanilla Syrup 750ml',                   'Pump-ready flavor syrup',                    1,  295.00, 1),
(4,  '2026-02-18 08:45:59.000000', '2026-02-18 08:45:59.000000', 'Arabica Beans - Sagada Light Roast',    'Floral light roast, 1kg vacuum pack',        0,  850.00, 2),
(5,  '2026-02-18 08:45:59.000000', '2026-02-18 08:45:59.000000', 'Paper Cups 12oz (50s)',                 'Double-wall hot cups',                       2,  480.00, 2),
(6,  '2026-02-18 08:45:59.000000', '2026-02-18 08:45:59.000000', 'Cup Lids 12oz (50s)',                   'Sip-through dome lids',                      2,  210.00, 2),
(7,  '2026-03-09 09:33:18.000000', '2026-03-09 09:33:18.000000', 'Espresso Blend - House',                '70/30 Arabica-Robusta, 1kg vacuum pack',     0,  700.00, 3),
(8,  '2026-03-09 09:33:18.000000', '2026-03-09 09:33:18.000000', 'Caramel Syrup 750ml',                   'Pump-ready flavor syrup',                    1,  295.00, 3),
(9,  '2026-03-09 09:33:18.000000', '2026-03-09 09:33:18.000000', 'Barista Milk 1L',                       'Steaming-grade fresh milk',                  1,  145.00, 3),
(10, '2026-03-27 14:07:52.000000', '2026-03-27 14:07:52.000000', 'Cold Brew Concentrate 1L',              'Ready for 1:4 dilution',                     1,  520.00, 4),
(11, '2026-03-27 14:07:52.000000', '2026-03-27 14:07:52.000000', 'Filter Papers V60 (100s)',              'Unbleached cone filters',                    2,  340.00, 4),
(12, '2026-04-14 09:11:04.000000', '2026-04-14 09:11:04.000000', 'Arabica Beans - Kalinga Medium Roast',  'Chocolate-forward medium roast, 1kg',        0,  810.00, 5),
(13, '2026-04-14 09:11:04.000000', '2026-04-14 09:11:04.000000', 'Takeout Bags (100s)',                   'Kraft paper carry bags',                     2,  390.00, 5),
(14, '2026-04-14 09:11:04.000000', '2026-04-14 09:11:04.000000', 'Thermal Receipt Roll 57mm',             'Bond paper roll, 50m',                       5,   55.00, 5),
(15, '2026-05-05 13:30:26.000000', '2026-05-05 13:30:26.000000', 'Hazelnut Syrup 750ml',                  'Pump-ready flavor syrup',                    1,  295.00, 6),
(16, '2026-05-05 13:30:26.000000', '2026-05-05 13:30:26.000000', 'Ceramic Cup & Saucer Set',              '180ml cappuccino set',                       4,  265.00, 6),
(17, '2026-06-11 11:41:50.000000', '2026-06-11 11:41:50.000000', 'Decaf Arabica - Swiss Water Process',   'Caffeine-free, 1kg vacuum pack',             0,  980.00, 7),
(18, '2026-06-11 11:41:50.000000', '2026-06-11 11:41:50.000000', 'Chocolate Powder 1kg',                  'Dutch-processed cocoa',                      0,  460.00, 7),
(19, '2026-07-02 16:20:33.000000', '2026-07-02 16:20:33.000000', 'Espresso Blend - Signature Dark',       'Low-acid dark blend, 1kg vacuum pack',       0,  735.00, 8),
(20, '2026-07-02 16:20:33.000000', '2026-07-02 16:20:33.000000', 'Oat Milk 1L',                           'Barista edition, steams well',               1,  210.00, 8),
(21, '2026-07-02 16:20:33.000000', '2026-07-02 16:20:33.000000', 'Cup Sleeves 12oz (100s)',               'Corrugated kraft sleeves',                   2,  320.00, 8),
(22, '2026-07-21 09:05:17.000000', '2026-07-21 09:05:17.000000', 'Matcha Powder 500g',                    'Culinary grade, stone ground',               0,  890.00, 9),
(23, '2026-07-21 09:05:17.000000', '2026-07-21 09:05:17.000000', 'Iced Cups 16oz (50s)',                  'Clear PET cold cups',                        2,  395.00, 9),
(24, '2026-08-10 14:55:08.000000', '2026-08-10 14:55:08.000000', 'Arabica Beans - Mt. Apo Medium-Dark',   'Nutty medium-dark roast, 1kg',               0,  795.00, 10),
(25, '2026-08-10 14:55:08.000000', '2026-08-10 14:55:08.000000', 'Salted Caramel Syrup 750ml',            'Pump-ready flavor syrup',                    1,  310.00, 10),
(26, '2026-08-10 14:55:08.000000', '2026-08-10 14:55:08.000000', 'Stirrers Wooden (1000s)',               'FSC-certified birch stirrers',               2,  180.00, 10);

-- -----------------------------------------------------------------------------
-- quotation_item
-- discount is a percentage; total = quantity * price * (100 - discount) / 100
-- -----------------------------------------------------------------------------
INSERT INTO quotation_item (id, created_at, updated_at, quotation_id, product_id, quantity, price, discount, total) VALUES
(1,  '2026-02-03 10:02:11.000000', '2026-02-03 10:02:11.000000', 1,  1,  10,  780.00,  5,  7410.00),
(2,  '2026-02-03 10:02:11.000000', '2026-02-03 10:02:11.000000', 1,  2,   5,  620.00,  0,  3100.00),
(3,  '2026-02-03 10:02:11.000000', '2026-02-03 10:02:11.000000', 1,  3,   4,  295.00,  0,  1180.00),
(4,  '2026-02-18 08:45:59.000000', '2026-02-18 08:45:59.000000', 2,  4,   8,  850.00, 10,  6120.00),
(5,  '2026-02-18 08:45:59.000000', '2026-02-18 08:45:59.000000', 2,  5,  20,  480.00,  5,  9120.00),
(6,  '2026-02-18 08:45:59.000000', '2026-02-18 08:45:59.000000', 2,  6,  20,  210.00,  5,  3990.00),
(7,  '2026-03-09 09:33:18.000000', '2026-03-09 09:33:18.000000', 3,  7,  15,  700.00, 10,  9450.00),
(8,  '2026-03-09 09:33:18.000000', '2026-03-09 09:33:18.000000', 3,  8,   6,  295.00,  0,  1770.00),
(9,  '2026-03-09 09:33:18.000000', '2026-03-09 09:33:18.000000', 3,  9,  24,  145.00,  0,  3480.00),
(10, '2026-03-27 14:07:52.000000', '2026-03-27 14:07:52.000000', 4, 10,  12,  520.00,  0,  6240.00),
(11, '2026-03-27 14:07:52.000000', '2026-03-27 14:07:52.000000', 4, 11,   3,  340.00,  0,  1020.00),
(12, '2026-04-14 09:11:04.000000', '2026-04-14 09:11:04.000000', 5, 12,  25,  810.00, 15, 17212.50),
(13, '2026-04-14 09:11:04.000000', '2026-04-14 09:11:04.000000', 5, 13,  10,  390.00,  5,  3705.00),
(14, '2026-04-14 09:11:04.000000', '2026-04-14 09:11:04.000000', 5, 14,  40,   55.00,  0,  2200.00),
(15, '2026-05-05 13:30:26.000000', '2026-05-05 13:30:26.000000', 6, 15,   4,  295.00,  0,  1180.00),
(16, '2026-05-05 13:30:26.000000', '2026-05-05 13:30:26.000000', 6, 16,  12,  265.00, 10,  2862.00),
(17, '2026-06-11 11:41:50.000000', '2026-06-11 11:41:50.000000', 7, 17,   6,  980.00,  0,  5880.00),
(18, '2026-06-11 11:41:50.000000', '2026-06-11 11:41:50.000000', 7, 18,   5,  460.00,  0,  2300.00),
(19, '2026-07-02 16:20:33.000000', '2026-07-02 16:20:33.000000', 8, 19,  18,  735.00, 10, 11907.00),
(20, '2026-07-02 16:20:33.000000', '2026-07-02 16:20:33.000000', 8, 20,  24,  210.00,  5,  4788.00),
(21, '2026-07-02 16:20:33.000000', '2026-07-02 16:20:33.000000', 8, 21,   8,  320.00,  0,  2560.00),
(22, '2026-07-21 09:05:17.000000', '2026-07-21 09:05:17.000000', 9, 22,   5,  890.00,  0,  4450.00),
(23, '2026-07-21 09:05:17.000000', '2026-07-21 09:05:17.000000', 9, 23,  15,  395.00,  5,  5628.75),
(24, '2026-08-10 14:55:08.000000', '2026-08-10 14:55:08.000000', 10, 24, 20,  795.00, 10, 14310.00),
(25, '2026-08-10 14:55:08.000000', '2026-08-10 14:55:08.000000', 10, 25,  6,  310.00,  0,  1860.00),
(26, '2026-08-10 14:55:08.000000', '2026-08-10 14:55:08.000000', 10, 26,  2,  180.00,  0,   360.00);

-- -----------------------------------------------------------------------------
-- customer_quotation (Customer.quotation join table)
-- quotation_id is UNIQUE here, so exactly one row per quotation.
-- -----------------------------------------------------------------------------
INSERT INTO customer_quotation (customer_id, quotation_id) VALUES
(1, 1), (2, 2), (3, 3), (1, 4), (4, 5), (5, 6), (6, 7), (3, 8), (7, 9), (8, 10);

-- -----------------------------------------------------------------------------
-- quotation_products
-- product_id is UNIQUE here, so exactly one row per product.
-- -----------------------------------------------------------------------------
INSERT INTO quotation_products (quotation_id, product_id) VALUES
(1, 1), (1, 2), (1, 3),
(2, 4), (2, 5), (2, 6),
(3, 7), (3, 8), (3, 9),
(4, 10), (4, 11),
(5, 12), (5, 13), (5, 14),
(6, 15), (6, 16),
(7, 17), (7, 18),
(8, 19), (8, 20), (8, 21),
(9, 22), (9, 23),
(10, 24), (10, 25), (10, 26);

-- -----------------------------------------------------------------------------
-- quotation_item_details (Quotation.quotationItems join table)
-- quotation_item_id is UNIQUE here, so exactly one row per item.
-- -----------------------------------------------------------------------------
INSERT INTO quotation_item_details (quotation_id, quotation_item_id) VALUES
(1, 1), (1, 2), (1, 3),
(2, 4), (2, 5), (2, 6),
(3, 7), (3, 8), (3, 9),
(4, 10), (4, 11),
(5, 12), (5, 13), (5, 14),
(6, 15), (6, 16),
(7, 17), (7, 18),
(8, 19), (8, 20), (8, 21),
(9, 22), (9, 23),
(10, 24), (10, 25), (10, 26);

COMMIT;

-- =============================================================================
-- Verification: every row should report 'OK'.
-- =============================================================================
-- Stored quotation totals match SUM(item totals) + shipping.
-- SELECT q.id,
--        q.quotation_number,
--        q.total_amount,
--        SUM(qi.total) + q.shipping_charges AS recomputed,
--        IF(q.total_amount = SUM(qi.total) + q.shipping_charges, 'OK', 'MISMATCH') AS result
-- FROM quotation q
--          JOIN quotation_item qi ON qi.quotation_id = q.id
-- GROUP BY q.id, q.quotation_number, q.total_amount, q.shipping_charges
-- ORDER BY q.id;

-- Line totals match quantity * price * (100 - discount) / 100.
-- SELECT id,
--        total,
--        ROUND(quantity * price * (100 - discount) / 100, 2) AS recomputed,
--        IF(total = ROUND(quantity * price * (100 - discount) / 100, 2), 'OK', 'MISMATCH') AS result
-- FROM quotation_item
-- ORDER BY id;
