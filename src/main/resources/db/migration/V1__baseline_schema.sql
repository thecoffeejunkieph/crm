-- Baseline schema, captured from the schema Hibernate (ddl-auto=update) had already built.
-- Flyway takes over schema ownership from here; new changes go into new versioned migrations.
-- FK checks are disabled while creating tables so definition order (mysqldump alphabetical) does not matter.

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `customer_activity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `notes` varchar(2000) DEFAULT NULL,
  `type` enum('CALL','DEMO','EMAIL','FOLLOW_UP','MEETING','SENT_INVOICE','SENT_QUOTATION','SITE_VISIT') NOT NULL,
  `created_by_email` varchar(255) DEFAULT NULL,
  `customer_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKg1ta2m817x2q934lly5jaqenv` (`created_by_email`),
  KEY `FKdyksoexu86euux7t1cy57wrs` (`customer_id`),
  CONSTRAINT `FKdyksoexu86euux7t1cy57wrs` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`),
  CONSTRAINT `FKg1ta2m817x2q934lly5jaqenv` FOREIGN KEY (`created_by_email`) REFERENCES `users` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `customer_quotation` (
  `customer_id` bigint NOT NULL,
  `quotation_id` bigint NOT NULL,
  UNIQUE KEY `UKsnjotl03cpugxphk1dddr2g1h` (`quotation_id`),
  KEY `FKp9y7y7t7k0gg38j60adrj46q5` (`customer_id`),
  CONSTRAINT `FKb0qcoej035rp9yiiavu8d0ka7` FOREIGN KEY (`quotation_id`) REFERENCES `quotation` (`id`),
  CONSTRAINT `FKp9y7y7t7k0gg38j60adrj46q5` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `customers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `active` bit(1) NOT NULL DEFAULT b'1',
  `address` varchar(500) DEFAULT NULL,
  `business_name` varchar(255) DEFAULT NULL,
  `business_type` enum('COOPERATIVE','CORPORATION','OPC','PARTNERSHIP','SOLE_PROPRIETORSHIP') DEFAULT NULL,
  `tin` varchar(20) DEFAULT NULL,
  `customer_type` enum('BUSINESS','INDIVIDUAL') DEFAULT NULL,
  `email` varchar(255) NOT NULL,
  `first_name` varchar(100) NOT NULL,
  `last_name` varchar(100) NOT NULL,
  `phone_number` varchar(20) DEFAULT NULL,
  `preferred_shipping_method` varchar(100) DEFAULT NULL,
  `source` varchar(100) DEFAULT NULL,
  `assigned_rep_email` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKrfbvkrffamfql7cjmen8v976v` (`email`),
  KEY `FKortww4xi8prqigt5ao4wm35sv` (`assigned_rep_email`),
  CONSTRAINT `FKortww4xi8prqigt5ao4wm35sv` FOREIGN KEY (`assigned_rep_email`) REFERENCES `users` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `delivery_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `delivery_instructions` varchar(255) DEFAULT NULL,
  `picked_up_at` datetime(6) DEFAULT NULL,
  `proof_of_pickup_path` varchar(255) DEFAULT NULL,
  `status` enum('PENDING','PICKED_UP','READY_FOR_PICKUP','DELIVERED') DEFAULT NULL,
  `invoice_id` bigint DEFAULT NULL,
  `delivery_address` varchar(255) DEFAULT NULL,
  `delivery_order_number` varchar(255) DEFAULT NULL,
  `pdf_path` varchar(255) DEFAULT NULL,
  `delivered_at` datetime(6) DEFAULT NULL,
  `proof_of_delivery_path` varchar(255) DEFAULT NULL,
  `target_delivery_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK39l11b95h8iikwobk0vp96mel` (`delivery_order_number`),
  KEY `FK3jvi88y2te0qnxg9iuij1obp4` (`invoice_id`),
  CONSTRAINT `FK3jvi88y2te0qnxg9iuij1obp4` FOREIGN KEY (`invoice_id`) REFERENCES `invoice` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `delivery_order_proof_of_delivery` (
  `delivery_order_id` bigint NOT NULL,
  `file_path` varchar(255) DEFAULT NULL,
  KEY `FKrkwg46r3esh0kgotqspk64ba0` (`delivery_order_id`),
  CONSTRAINT `FKrkwg46r3esh0kgotqspk64ba0` FOREIGN KEY (`delivery_order_id`) REFERENCES `delivery_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `delivery_order_proof_of_pickup` (
  `delivery_order_id` bigint NOT NULL,
  `file_path` varchar(255) DEFAULT NULL,
  KEY `FK6ur6vkgd5og6g9ramv55veb5s` (`delivery_order_id`),
  CONSTRAINT `FK6ur6vkgd5og6g9ramv55veb5s` FOREIGN KEY (`delivery_order_id`) REFERENCES `delivery_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `inventory_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `quantity_on_hand` int NOT NULL,
  `quantity_reserved` int NOT NULL,
  `version` bigint DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `warehouse_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6ub9anq53i4q4jhemh72de1h9` (`product_id`,`warehouse_id`),
  KEY `FK19xsrqt167dam9vcd9n4v8957` (`warehouse_id`),
  CONSTRAINT `FK19xsrqt167dam9vcd9n4v8957` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouse` (`id`),
  CONSTRAINT `FKnlagkg4wldbng04fndb117wai` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `invoice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `due_date` date DEFAULT NULL,
  `invoice_date` date DEFAULT NULL,
  `invoice_number` varchar(255) DEFAULT NULL,
  `notes` varchar(255) DEFAULT NULL,
  `payment_terms` enum('DUE_ON_RECEIPT','NET_15','NET_30','NET_60') DEFAULT NULL,
  `pdf_path` varchar(255) DEFAULT NULL,
  `shipping_charges` decimal(38,2) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `terms_and_conditions` varchar(255) DEFAULT NULL,
  `total_amount` decimal(38,2) DEFAULT NULL,
  `customer_id` bigint DEFAULT NULL,
  `quotation_id` bigint DEFAULT NULL,
  `sales_rep_email` varchar(255) DEFAULT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `proof_of_payment_path` varchar(255) DEFAULT NULL,
  `delivered_at` datetime(6) DEFAULT NULL,
  `proof_of_delivery_path` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK3ed2a605iukty4i7qwldwx9bk` (`customer_id`),
  KEY `FKl9824irl1l9i7bslx5lxea21g` (`quotation_id`),
  KEY `FKlhqcd9y0bnjmhc2ni7x17k0uf` (`sales_rep_email`),
  CONSTRAINT `FK3ed2a605iukty4i7qwldwx9bk` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`),
  CONSTRAINT `FKl9824irl1l9i7bslx5lxea21g` FOREIGN KEY (`quotation_id`) REFERENCES `quotation` (`id`),
  CONSTRAINT `FKlhqcd9y0bnjmhc2ni7x17k0uf` FOREIGN KEY (`sales_rep_email`) REFERENCES `users` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `invoice_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `discount` int DEFAULT NULL,
  `price` decimal(38,2) DEFAULT NULL,
  `quantity` int DEFAULT NULL,
  `total` decimal(38,2) DEFAULT NULL,
  `invoice_id` bigint DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKbu6tmpd0mtgu9wrw5bj5uv09v` (`invoice_id`),
  KEY `FKdlrd6r1hiahn8botv6xhatjc2` (`product_id`),
  CONSTRAINT `FKbu6tmpd0mtgu9wrw5bj5uv09v` FOREIGN KEY (`invoice_id`) REFERENCES `invoice` (`id`),
  CONSTRAINT `FKdlrd6r1hiahn8botv6xhatjc2` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `invoice_item_details` (
  `invoice_id` bigint NOT NULL,
  `invoice_item_id` bigint NOT NULL,
  UNIQUE KEY `UKnh8plmmxpg7vajrv4px55rgu3` (`invoice_item_id`),
  KEY `FK6m70nla0nka1pfntm33ogg639` (`invoice_id`),
  CONSTRAINT `FK6m70nla0nka1pfntm33ogg639` FOREIGN KEY (`invoice_id`) REFERENCES `invoice` (`id`),
  CONSTRAINT `FKaunu2d70gtxcf7oqrjvbkbko5` FOREIGN KEY (`invoice_item_id`) REFERENCES `invoice_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `invoice_payment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `amount` decimal(38,2) DEFAULT NULL,
  `method` enum('BANK_TRANSFER','CASH','CREDIT_CARD','GCASH') DEFAULT NULL,
  `proof_of_payment_path` varchar(255) DEFAULT NULL,
  `recorded_at` datetime(6) DEFAULT NULL,
  `invoice_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKkopeu965ps1ljahtib8n8nub2` (`invoice_id`),
  CONSTRAINT `FKkopeu965ps1ljahtib8n8nub2` FOREIGN KEY (`invoice_id`) REFERENCES `invoice` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `active` bit(1) NOT NULL DEFAULT b'1',
  `description` varchar(255) DEFAULT NULL,
  `price` decimal(38,2) NOT NULL,
  `product_name` varchar(255) NOT NULL,
  `unit` tinyint DEFAULT NULL,
  `quotation_id` bigint DEFAULT NULL,
  `picture_path` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKedrq0110u9emeq6dpi32yqhty` (`quotation_id`),
  CONSTRAINT `FKedrq0110u9emeq6dpi32yqhty` FOREIGN KEY (`quotation_id`) REFERENCES `quotation` (`id`),
  CONSTRAINT `product_chk_1` CHECK ((`unit` between 0 and 5))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `quotation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `expiry_date` date DEFAULT NULL,
  `notes` varchar(255) DEFAULT NULL,
  `pdf_path` varchar(255) DEFAULT NULL,
  `quotation_number` varchar(255) DEFAULT NULL,
  `quote_date` date DEFAULT NULL,
  `shipping_charges` decimal(38,2) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `terms_and_conditions` varchar(255) DEFAULT NULL,
  `total_amount` decimal(38,2) DEFAULT NULL,
  `customer_id` bigint DEFAULT NULL,
  `sales_rep_email` varchar(255) DEFAULT NULL,
  `payment_terms` enum('DUE_ON_RECEIPT','NET_15','NET_30','NET_60') DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK485msvpjpli280mawl2s0xnwr` (`customer_id`),
  KEY `FKovytvodhjoy1a4d9253vddins` (`sales_rep_email`),
  CONSTRAINT `FK485msvpjpli280mawl2s0xnwr` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`),
  CONSTRAINT `FKovytvodhjoy1a4d9253vddins` FOREIGN KEY (`sales_rep_email`) REFERENCES `users` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `quotation_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `discount` int DEFAULT NULL,
  `price` decimal(38,2) DEFAULT NULL,
  `quantity` int DEFAULT NULL,
  `total` decimal(38,2) DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `quotation_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKj6wlqltxayhkhj8tx9f08gn1t` (`product_id`),
  KEY `FKdcp719ft4uhiigggr42o64o7g` (`quotation_id`),
  CONSTRAINT `FKdcp719ft4uhiigggr42o64o7g` FOREIGN KEY (`quotation_id`) REFERENCES `quotation` (`id`),
  CONSTRAINT `FKj6wlqltxayhkhj8tx9f08gn1t` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `quotation_item_details` (
  `quotation_id` bigint NOT NULL,
  `quotation_item_id` bigint NOT NULL,
  UNIQUE KEY `UKcw7pbs9k95vncaolv90mjlvsa` (`quotation_item_id`),
  KEY `FKqfgq8qihpu9cyx96dchmv7elu` (`quotation_id`),
  CONSTRAINT `FK2ybdcwrgmy0htiwhowxnm9fiy` FOREIGN KEY (`quotation_item_id`) REFERENCES `quotation_item` (`id`),
  CONSTRAINT `FKqfgq8qihpu9cyx96dchmv7elu` FOREIGN KEY (`quotation_id`) REFERENCES `quotation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `stock_movement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `notes` varchar(2000) DEFAULT NULL,
  `quantity` int NOT NULL,
  `quantity_on_hand_after` int NOT NULL,
  `quantity_reserved_after` int NOT NULL,
  `reference_id` bigint DEFAULT NULL,
  `reference_type` varchar(255) DEFAULT NULL,
  `type` enum('DELIVERY','RECEIPT','RELEASE','RESERVE') NOT NULL,
  `performed_by_email` varchar(255) DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `warehouse_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKhp5jxfxyb9wr75d5dywn02m9t` (`performed_by_email`),
  KEY `FKq63e7y5l2pnh2tt2lvxlquvbf` (`product_id`),
  KEY `FKmp40immc6bpap7qpthgr3ff2g` (`warehouse_id`),
  CONSTRAINT `FKhp5jxfxyb9wr75d5dywn02m9t` FOREIGN KEY (`performed_by_email`) REFERENCES `users` (`email`),
  CONSTRAINT `FKmp40immc6bpap7qpthgr3ff2g` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouse` (`id`),
  CONSTRAINT `FKq63e7y5l2pnh2tt2lvxlquvbf` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `users` (
  `email` varchar(255) NOT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `birthday` date DEFAULT NULL,
  `cellphone_number` varchar(255) DEFAULT NULL,
  `first_name` varchar(255) DEFAULT NULL,
  `last_name` varchar(255) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `qr_code_path` varchar(255) DEFAULT NULL,
  `reward_points` int NOT NULL,
  `roles` varchar(255) NOT NULL,
  `picture_path` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `warehouse` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `active` bit(1) NOT NULL DEFAULT b'1',
  `address` varchar(255) DEFAULT NULL,
  `code` varchar(255) DEFAULT NULL,
  `default_warehouse` bit(1) NOT NULL DEFAULT b'0',
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK9wk4ocyt0wv0hpffpr41aoweu` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
