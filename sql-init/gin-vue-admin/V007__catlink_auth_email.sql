CREATE TABLE IF NOT EXISTS `cat_account_securities` (
  `user_id` bigint unsigned NOT NULL,
  `email_verified` tinyint(1) NOT NULL DEFAULT 0,
  `verified_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) DEFAULT NULL,
  `updated_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  KEY `idx_cat_account_securities_email_verified` (`email_verified`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `cat_auth_codes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `email` varchar(191) NOT NULL,
  `purpose` varchar(32) NOT NULL,
  `code_hash` varchar(100) NOT NULL,
  `attempts` bigint NOT NULL DEFAULT 0,
  `expires_at` datetime(3) NOT NULL,
  `consumed_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cat_auth_code_lookup` (`email`,`purpose`),
  KEY `idx_cat_auth_codes_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `cat_email_configs` (
  `id` bigint unsigned NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 0,
  `host` varchar(255) DEFAULT NULL,
  `port` bigint NOT NULL DEFAULT 465,
  `from` varchar(191) DEFAULT NULL,
  `username` varchar(191) DEFAULT NULL,
  `password_env` varchar(120) DEFAULT NULL,
  `nickname` varchar(80) DEFAULT NULL,
  `tls_mode` varchar(20) NOT NULL DEFAULT 'implicit',
  `login_otp_required` tinyint(1) NOT NULL DEFAULT 0,
  `updated_by` bigint unsigned DEFAULT NULL,
  `created_at` datetime(3) DEFAULT NULL,
  `updated_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cat_email_configs_updated_by` (`updated_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `cat_account_securities` (`user_id`,`email_verified`,`verified_at`,`created_at`,`updated_at`)
SELECT `id`,1,NOW(3),NOW(3),NOW(3) FROM `sys_users` WHERE `authority_id`=9528
ON DUPLICATE KEY UPDATE `user_id`=`user_id`;
