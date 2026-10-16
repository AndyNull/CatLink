-- Five canonical cat statuses; missing/deceased are archive reasons, not main statuses.
SET @schema_name = DATABASE();

SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='cat_public_cats' AND column_name='archive_reason'), 'SELECT 1', 'ALTER TABLE `cat_public_cats` ADD COLUMN `archive_reason` varchar(20) DEFAULT NULL COMMENT ''lost/deceased/other'' AFTER `status_reason`');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='cat_shares' AND column_name='archive_reason'), 'SELECT 1', 'ALTER TABLE `cat_shares` ADD COLUMN `archive_reason` varchar(20) DEFAULT NULL AFTER `status`');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='cat_public_cats' AND column_name='legacy_status'), 'SELECT 1', 'ALTER TABLE `cat_public_cats` ADD COLUMN `legacy_status` varchar(80) DEFAULT NULL COMMENT ''升级前无法识别的状态'' AFTER `archive_reason`');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

UPDATE `cat_public_cats`
SET `legacy_status` = `status`
WHERE UPPER(COALESCE(`status`, '')) NOT IN ('RESCUE','RESCUING','INTAKE','TREATING','RECOVERING','ADOPTABLE','READY_FOR_ADOPTION','MATCHING','RETURNED','ADOPTED','FOLLOW_UP','ARCHIVED','END','DECEASED','LOST','LOST_CONTACT')
  AND COALESCE(`legacy_status`, '') = '';
UPDATE `cat_public_cats` SET `archive_reason`='deceased' WHERE UPPER(`status`)='DECEASED';
UPDATE `cat_public_cats` SET `archive_reason`='lost' WHERE UPPER(`status`) IN ('LOST','LOST_CONTACT');
UPDATE `cat_public_cats` SET `status`='rescue', `cat_status`='救助中', `archive_reason`=NULL WHERE UPPER(`status`) IN ('RESCUE','RESCUING','INTAKE');
UPDATE `cat_public_cats` SET `status`='treating', `cat_status`='治疗中', `archive_reason`=NULL WHERE UPPER(`status`) IN ('TREATING','RECOVERING');
UPDATE `cat_public_cats` SET `status`='adoptable', `cat_status`='待领养', `archive_reason`=NULL WHERE UPPER(`status`) IN ('ADOPTABLE','READY_FOR_ADOPTION','MATCHING','RETURNED');
UPDATE `cat_public_cats` SET `status`='adopted', `cat_status`='已领养', `archive_reason`=NULL WHERE UPPER(`status`) IN ('ADOPTED','FOLLOW_UP');
UPDATE `cat_public_cats` SET `status`='archived', `cat_status`='已归档', `archive_reason`=COALESCE(NULLIF(`archive_reason`,''),'other') WHERE UPPER(`status`) IN ('ARCHIVED','END','DECEASED','LOST','LOST_CONTACT');
UPDATE `cat_public_cats` SET `status`='rescue', `cat_status`='救助中', `archive_reason`=NULL WHERE `status` NOT IN ('rescue','treating','adoptable','adopted','archived') OR `status` IS NULL;
UPDATE `cat_shares` SET `archive_reason`='deceased', `status`='archived' WHERE UPPER(`status`)='DECEASED';
UPDATE `cat_shares` SET `archive_reason`='lost', `status`='archived' WHERE UPPER(`status`) IN ('LOST','LOST_CONTACT');
UPDATE `cat_shares` SET `status`='rescue', `archive_reason`=NULL WHERE UPPER(`status`) IN ('RESCUE','RESCUING','INTAKE');
UPDATE `cat_shares` SET `status`='treating', `archive_reason`=NULL WHERE UPPER(`status`) IN ('TREATING','RECOVERING');
UPDATE `cat_shares` SET `status`='adoptable', `archive_reason`=NULL WHERE UPPER(`status`) IN ('ADOPTABLE','READY_FOR_ADOPTION','MATCHING','RETURNED');
UPDATE `cat_shares` SET `status`='adopted', `archive_reason`=NULL WHERE UPPER(`status`) IN ('ADOPTED','FOLLOW_UP');
UPDATE `cat_shares` SET `status`='archived', `archive_reason`=COALESCE(NULLIF(`archive_reason`,''),'other') WHERE UPPER(`status`) IN ('ARCHIVED','END');
UPDATE `cat_shares` SET `status`='rescue', `archive_reason`=NULL WHERE `status` NOT IN ('rescue','treating','adoptable','adopted','archived') OR `status` IS NULL;

SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=@schema_name AND table_name='cat_public_cats' AND index_name='idx_cat_public_cats_status'), 'SELECT 1', 'ALTER TABLE `cat_public_cats` ADD INDEX `idx_cat_public_cats_status` (`status`)');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=@schema_name AND table_name='cat_public_cats' AND index_name='idx_cat_public_cats_archive_reason'), 'SELECT 1', 'ALTER TABLE `cat_public_cats` ADD INDEX `idx_cat_public_cats_archive_reason` (`archive_reason`)');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;
