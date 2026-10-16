/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_ads` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `slot` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'launch/community',
  `name` varchar(120) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `image_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `link_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `fee` varchar(60) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `on_time` datetime(3) DEFAULT NULL,
  `duration` varchar(120) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `off_time` datetime(3) DEFAULT NULL,
  `contact` varchar(120) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `height_mode` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'ratio' COMMENT 'fixed/ratio',
  `height_value` decimal(8,3) DEFAULT '42.857' COMMENT 'fixed=px,ratio=宽度百分比',
  `enabled` tinyint(1) DEFAULT '0',
  `created_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_applies` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `cat_id` bigint unsigned DEFAULT NULL,
  `applicant_id` bigint unsigned DEFAULT NULL,
  `owner_id` bigint unsigned DEFAULT NULL,
  `message` text COLLATE utf8mb4_general_ci,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending',
  `created_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cat_applies_cat_id` (`cat_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_bookmarks` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned DEFAULT NULL,
  `post_id` bigint unsigned DEFAULT NULL,
  `created_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_cat_bookmark_user_post` (`user_id`,`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_comments` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `post_id` bigint unsigned DEFAULT NULL,
  `author_id` bigint unsigned DEFAULT NULL,
  `body` text COLLATE utf8mb4_general_ci,
  `reply_to_id` bigint unsigned DEFAULT NULL,
  `like_count` bigint DEFAULT '0',
  `created_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cat_comments_post_id` (`post_id`),
  KEY `idx_cat_comments_reply_to_id` (`reply_to_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_comment_likes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned DEFAULT NULL,
  `comment_id` bigint unsigned DEFAULT NULL,
  `created_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_comment_like_user_comment` (`user_id`,`comment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_conversations` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_a` bigint unsigned DEFAULT NULL,
  `user_b` bigint unsigned DEFAULT NULL,
  `last_message` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `updated_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cat_conversations_user_a` (`user_a`),
  KEY `idx_cat_conversations_user_b` (`user_b`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_device_sessions` (
  `id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `user_id` bigint unsigned DEFAULT NULL,
  `name` varchar(120) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `token_hash` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `access_token_hash` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `refresh_token_hash` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `family_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime(3) DEFAULT NULL,
  `last_seen_at` datetime(3) DEFAULT NULL,
  `revoked_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cat_device_sessions_user_id` (`user_id`),
  KEY `idx_cat_device_sessions_token_hash` (`token_hash`),
  KEY `idx_cat_device_sessions_access_token_hash` (`access_token_hash`),
  KEY `idx_cat_device_sessions_refresh_token_hash` (`refresh_token_hash`),
  KEY `idx_cat_device_sessions_family_id` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_encouragements` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `quote` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '一句话',
  `source` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源',
  `enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `created_at` datetime(3) DEFAULT NULL,
  `deleted_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cat_encouragements_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_follows` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned DEFAULT NULL,
  `cat_id` bigint unsigned DEFAULT NULL,
  `created_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_cat_follow_user_cat` (`user_id`,`cat_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_likes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned DEFAULT NULL,
  `post_id` bigint unsigned DEFAULT NULL,
  `created_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_cat_like_user_post` (`user_id`,`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_media_upload_windows` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `owner_id` bigint unsigned DEFAULT NULL,
  `window_start` datetime(3) DEFAULT NULL,
  `attempt_count` bigint DEFAULT '0',
  `request_bytes` bigint DEFAULT '0',
  `updated_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_cat_media_upload_window` (`owner_id`,`window_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_media` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `owner_id` bigint unsigned DEFAULT NULL,
  `category` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'cat/contract/avatar/post/misc',
  `url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `storage_key` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `storage_provider` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'local',
  `temporary` tinyint(1) DEFAULT '0',
  `expires_at` datetime(3) DEFAULT NULL,
  `size` bigint DEFAULT NULL,
  `created_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cat_media_owner_id` (`owner_id`),
  KEY `idx_cat_media_url` (`url`),
  KEY `idx_cat_media_storage_key` (`storage_key`),
  KEY `idx_cat_media_storage_provider` (`storage_provider`),
  KEY `idx_cat_media_temporary` (`temporary`),
  KEY `idx_cat_media_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_messages` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `conv_id` bigint unsigned DEFAULT NULL,
  `sender_id` bigint unsigned DEFAULT NULL,
  `body` text COLLATE utf8mb4_general_ci,
  `read_flag` tinyint(1) DEFAULT '0',
  `created_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cat_messages_conv_id` (`conv_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_notifications` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `to_user` bigint unsigned DEFAULT NULL,
  `type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'announcement/remind/private/report',
  `title` varchar(120) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `body` text COLLATE utf8mb4_general_ci,
  `link` varchar(300) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `read_flag` tinyint(1) DEFAULT '0',
  `created_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cat_notifications_to_user` (`to_user`),
  KEY `idx_cat_notifications_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_posts` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `author_id` bigint unsigned DEFAULT NULL,
  `client_id` varchar(36) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `title` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `content` text COLLATE utf8mb4_general_ci,
  `topic` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'all' COMMENT 'all/help/adopt/exchange',
  `cat_ref` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `image_url` longtext COLLATE utf8mb4_general_ci,
  `media_ids` text COLLATE utf8mb4_general_ci,
  `city_code` varchar(80) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `district_code` varchar(80) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `like_count` bigint DEFAULT '0',
  `comment_count` bigint DEFAULT '0',
  `locked` tinyint(1) DEFAULT '0' COMMENT '锁定(管理)',
  `created_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cat_posts_author_id` (`author_id`),
  UNIQUE KEY `idx_cat_post_author_client` (`author_id`,`client_id`),
  KEY `idx_cat_posts_city_code` (`city_code`),
  KEY `idx_cat_posts_district_code` (`district_code`),
  KEY `idx_cat_posts_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_public_cats` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `owner_id` bigint unsigned DEFAULT NULL COMMENT '创建人',
  `nickname` varchar(80) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '公开昵称',
  `alias` varchar(80) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `story` text COLLATE utf8mb4_general_ci COMMENT '公开故事',
  `gender` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `age_band` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `coat` varchar(60) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `city_code` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `district_code` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'adoptable' COMMENT 'rescue/treating/adoptable/adopted/archived',
  `state` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'published' COMMENT 'published/paused/archived',
  `cover_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `location_name` varchar(120) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '地区文字',
  `follow_count` bigint DEFAULT '0',
  `created_at` datetime(3) DEFAULT NULL,
  `updated_at` datetime(3) DEFAULT NULL,
  `adopted_by_name` varchar(80) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `local_id` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'App本地档案ID',
  `personality` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `health_summary` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `weight` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `diet` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `human_contact` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cat_contact` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cat_status` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `archive_reason` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'lost/deceased/other',
  `legacy_status` varchar(80) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '升级前无法识别的状态',
  `rescued_date` bigint NOT NULL DEFAULT '0',
  `status_date` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_owner_local_cat` (`owner_id`,`local_id`),
  KEY `idx_cat_public_cats_owner_id` (`owner_id`),
  KEY `idx_cat_public_cats_status` (`status`),
  KEY `idx_cat_public_cats_archive_reason` (`archive_reason`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_reports` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `reporter` bigint unsigned DEFAULT NULL,
  `target_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'post/comment/user/cat',
  `target_id` varchar(191) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending',
  `created_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_shares` (
  `code` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `owner_id` bigint unsigned DEFAULT NULL,
  `cat_id` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `nickname` varchar(80) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `story` text COLLATE utf8mb4_general_ci,
  `gender` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `age_band` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `coat` varchar(60) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `tags` varchar(300) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `health` varchar(300) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `archive_reason` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cover_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `photo_urls` text COLLATE utf8mb4_general_ci,
  `created_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`code`),
  KEY `idx_cat_shares_owner_id` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_user_follows` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned DEFAULT NULL,
  `target_id` bigint unsigned DEFAULT NULL,
  `created_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_user_follow` (`user_id`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_vault_meta` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `account_id` bigint unsigned DEFAULT NULL,
  `revision` bigint DEFAULT '0',
  `updated_at` datetime(3) DEFAULT NULL,
  `key_version` bigint DEFAULT '1',
  `record_count` bigint DEFAULT '0',
  `payload_bytes` bigint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_cat_vault_meta_account_id` (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_vaults` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `account_id` bigint unsigned DEFAULT NULL,
  `entity_type` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `entity_id` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `revision` bigint DEFAULT '1',
  `server_revision` bigint DEFAULT '0',
  `tombstone` tinyint(1) DEFAULT '0',
  `payload` longtext COLLATE utf8mb4_general_ci,
  `updated_at` datetime(3) DEFAULT NULL,
  `key_version` bigint DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_cat_vault_entity` (`account_id`,`entity_type`,`entity_id`),
  KEY `idx_cat_vaults_account_id` (`account_id`),
  KEY `idx_cat_vaults_server_revision` (`server_revision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `cat_versions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `version_code` bigint DEFAULT NULL,
  `version_name` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `content` text COLLATE utf8mb4_general_ci,
  `download_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
