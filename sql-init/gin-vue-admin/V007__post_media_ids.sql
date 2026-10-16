-- Stable ordered post media references and idempotent App publishing.
SET @schema_name = DATABASE();

SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='cat_posts' AND column_name='media_ids'), 'SELECT 1', 'ALTER TABLE `cat_posts` ADD COLUMN `media_ids` text AFTER `image_url`');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='cat_posts' AND column_name='client_id'), 'SELECT 1', 'ALTER TABLE `cat_posts` ADD COLUMN `client_id` varchar(36) DEFAULT NULL AFTER `author_id`');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=@schema_name AND table_name='cat_posts' AND index_name='idx_cat_post_author_client'), 'SELECT 1', 'ALTER TABLE `cat_posts` ADD UNIQUE INDEX `idx_cat_post_author_client` (`author_id`,`client_id`)');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;
