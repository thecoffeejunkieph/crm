-- In-app notifications for staff. Rows outlive the live SSE push, so a user who was offline
-- still sees what happened; read_at NULL = unread.
CREATE TABLE `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `recipient_email` varchar(255) NOT NULL,
  `type` enum('QUOTATION_ACCEPTED','QUOTATION_REJECTED','QUOTATION_EXPIRED','PAYMENT_AWAITING_VERIFICATION','INVOICE_PAID','DELIVERY_ORDER_CREATED','DELIVERY_ORDER_DELIVERED') NOT NULL,
  `entity_id` bigint DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `body` varchar(1000) DEFAULT NULL,
  `read_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notification_recipient_read` (`recipient_email`, `read_at`),
  CONSTRAINT `fk_notification_recipient` FOREIGN KEY (`recipient_email`) REFERENCES `users` (`email`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
