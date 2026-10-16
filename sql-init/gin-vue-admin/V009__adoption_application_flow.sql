-- Complete adoption application state, timeline and query indexes.
SET @schema_name = DATABASE();

SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='cat_applies' AND column_name='contact_summary'), 'SELECT 1', 'ALTER TABLE `cat_applies` ADD COLUMN `contact_summary` varchar(200) DEFAULT NULL AFTER `message`');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='cat_applies' AND column_name='updated_at'), 'SELECT 1', 'ALTER TABLE `cat_applies` ADD COLUMN `updated_at` datetime(3) DEFAULT NULL AFTER `created_at`');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='cat_applies' AND column_name='decided_at'), 'SELECT 1', 'ALTER TABLE `cat_applies` ADD COLUMN `decided_at` datetime(3) DEFAULT NULL AFTER `updated_at`');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

UPDATE `cat_applies` SET `status`='approved' WHERE `status`='completed';
UPDATE `cat_applies` SET `updated_at`=`created_at` WHERE `updated_at` IS NULL;
UPDATE `cat_applies` SET `decided_at`=COALESCE(`updated_at`,`created_at`) WHERE `status` IN ('approved','rejected','withdrawn') AND `decided_at` IS NULL;
INSERT INTO `cat_adoption_events` (`apply_id`,`actor_id`,`status`,`note`,`media_urls`,`media_ids`,`created_at`)
SELECT a.`id`,a.`applicant_id`,a.`status`,'历史申请状态迁移','', '[]',a.`created_at`
FROM `cat_applies` a LEFT JOIN `cat_adoption_events` e ON e.`apply_id`=a.`id`
WHERE e.`id` IS NULL;
UPDATE `cat_public_cats` c JOIN `cat_applies` a ON a.`cat_id`=c.`id` AND a.`status`='approved'
SET c.`status`='adopted',c.`cat_status`='已领养',c.`archive_reason`=NULL,c.`status_reason`='历史领养申请已通过',c.`status_date`=DATEDIFF(DATE(a.`created_at`),'1970-01-01')
WHERE c.`status`='adoptable';

SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=@schema_name AND table_name='cat_applies' AND index_name='idx_cat_apply_cat_status'), 'SELECT 1', 'ALTER TABLE `cat_applies` ADD INDEX `idx_cat_apply_cat_status` (`cat_id`,`status`)');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=@schema_name AND table_name='cat_applies' AND index_name='idx_cat_apply_applicant_status'), 'SELECT 1', 'ALTER TABLE `cat_applies` ADD INDEX `idx_cat_apply_applicant_status` (`applicant_id`,`status`)');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=@schema_name AND table_name='cat_applies' AND index_name='idx_cat_apply_owner_status'), 'SELECT 1', 'ALTER TABLE `cat_applies` ADD INDEX `idx_cat_apply_owner_status` (`owner_id`,`status`)');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;
