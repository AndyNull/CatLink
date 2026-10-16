package service

import (
	"encoding/json"
	"strings"
	"time"

	"gorm.io/gorm"
)

type Page struct {
	Items interface{} `json:"items"`
	Total int64       `json:"total"`
}

type VaultCat struct {
	RecordID        string         `json:"recordId" gorm:"column:record_id"`
	OwnerID         uint           `json:"ownerId" gorm:"column:owner_id"`
	OwnerName       string         `json:"ownerName" gorm:"column:owner_name"`
	OwnerUsername   string         `json:"ownerUsername" gorm:"column:owner_username"`
	Revision        int64          `json:"revision"`
	ServerRevision  int64          `json:"serverRevision" gorm:"column:server_revision"`
	UpdatedAt       time.Time      `json:"updatedAt" gorm:"column:updated_at"`
	CoverURL        string         `json:"coverUrl" gorm:"column:cover_url"`
	MediaCount      int64          `json:"mediaCount" gorm:"column:media_count"`
	PublicID        uint           `json:"publicId" gorm:"column:public_id"`
	PublicState     string         `json:"publicState" gorm:"column:public_state"`
	Payload         string         `json:"-"`
	Envelope        string         `json:"-" gorm:"column:server_wrapped_dek"`
	Profile         map[string]any `json:"profile" gorm:"-"`
	ProfileReadable bool           `json:"profileReadable" gorm:"-"`
}

func Stats(db *gorm.DB) (map[string]int64, error) {
	now := time.Now()
	today := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, now.Location())
	var row struct {
		Users                    int64
		PrivateCats              int64
		PublicCats               int64
		Posts                    int64
		Comments                 int64
		Media                    int64
		Notifications            int64
		ReportsPending           int64
		LockedPosts              int64
		StorageMigrationsRunning int64
		UnreadNotifications      int64
		TodayUsers               int64
		TodayPosts               int64
		TodayComments            int64
		TodayMedia               int64
	}
	err := db.Raw(`SELECT
		(SELECT COUNT(*) FROM sys_users WHERE deleted_at IS NULL) AS users,
		(SELECT COUNT(*) FROM cat_vaults WHERE entity_type = 'cat' AND tombstone = ?) AS private_cats,
		(SELECT COUNT(*) FROM cat_public_cats WHERE state = 'published') AS public_cats,
		(SELECT COUNT(*) FROM cat_posts) AS posts,
		(SELECT COUNT(*) FROM cat_comments) AS comments,
		(SELECT COUNT(*) FROM cat_media WHERE temporary = ?) AS media,
		(SELECT COUNT(*) FROM cat_notifications) AS notifications,
		(SELECT COUNT(*) FROM cat_reports WHERE status = 'pending') AS reports_pending,
		(SELECT COUNT(*) FROM cat_posts WHERE locked = ?) AS locked_posts,
		(SELECT COUNT(*) FROM cat_storage_migration_jobs WHERE status IN ('queued', 'running')) AS storage_migrations_running,
		(SELECT COUNT(*) FROM cat_notifications WHERE read_flag = ?) AS unread_notifications,
		(SELECT COUNT(*) FROM sys_users WHERE created_at >= ? AND deleted_at IS NULL) AS today_users,
		(SELECT COUNT(*) FROM cat_posts WHERE created_at >= ?) AS today_posts,
		(SELECT COUNT(*) FROM cat_comments WHERE created_at >= ?) AS today_comments,
		(SELECT COUNT(*) FROM cat_media WHERE created_at >= ? AND temporary = ?) AS today_media`,
		false, false, true, false, today, today, today, today, false,
	).Scan(&row).Error
	if err != nil {
		return nil, err
	}
	return map[string]int64{
		"users": row.Users, "privateCats": row.PrivateCats, "publicCats": row.PublicCats,
		"posts": row.Posts, "comments": row.Comments, "media": row.Media,
		"notifications": row.Notifications, "reportsPending": row.ReportsPending,
		"lockedPosts": row.LockedPosts, "storageMigrationsRunning": row.StorageMigrationsRunning,
		"unreadNotifications": row.UnreadNotifications, "todayUsers": row.TodayUsers,
		"todayPosts": row.TodayPosts, "todayComments": row.TodayComments, "todayMedia": row.TodayMedia,
	}, nil
}

func VaultCats(db *gorm.DB, page, size int, keyword string) (Page, error) {
	query := db.Table("cat_vaults cv").
		Joins("LEFT JOIN sys_users u ON u.id = cv.account_id").
		Joins("LEFT JOIN cat_vault_meta vm ON vm.account_id = cv.account_id").
		Joins("LEFT JOIN cat_public_cats pc ON pc.owner_id = cv.account_id AND pc.local_id = cv.entity_id").
		Where("cv.entity_type = ? AND cv.tombstone = ?", "cat", false)
	if keyword != "" {
		like := "%" + keyword + "%"
		query = query.Where("cv.entity_id LIKE ? OR u.nick_name LIKE ? OR u.username LIKE ?", like, like, like)
	}
	var total int64
	if err := query.Count(&total).Error; err != nil {
		return Page{}, err
	}
	var rows []VaultCat
	err := query.Select("cv.entity_id AS record_id, cv.account_id AS owner_id, u.nick_name AS owner_name, u.username AS owner_username, cv.revision, cv.server_revision, cv.updated_at, cv.payload, vm.server_wrapped_dek, COALESCE(pc.id,0) AS public_id, COALESCE(pc.state,'') AS public_state, COALESCE((SELECT m.url FROM cat_media m WHERE m.owner_id=cv.account_id AND m.entity_type='cat' AND m.entity_id=cv.entity_id AND m.usage='cover' ORDER BY m.created_at DESC,m.id DESC LIMIT 1),'') AS cover_url, (SELECT COUNT(*) FROM cat_media m WHERE m.owner_id=cv.account_id AND m.entity_type='cat' AND m.entity_id=cv.entity_id) AS media_count").
		Order("cv.updated_at DESC, cv.id DESC").Offset((page - 1) * size).Limit(size).Scan(&rows).Error
	if err != nil {
		return Page{}, err
	}
	for i := range rows {
		dek, keyErr := accountDEK(rows[i].OwnerID, rows[i].Envelope)
		if keyErr != nil {
			continue
		}
		plain, decryptErr := decryptRecord(dek, "cat", rows[i].RecordID, rows[i].Payload)
		if decryptErr == nil && json.Unmarshal(plain, &rows[i].Profile) == nil {
			rows[i].ProfileReadable = true
		}
	}
	return Page{Items: rows, Total: total}, nil
}

func VaultCatDetail(db *gorm.DB, ownerID uint, recordID string) (map[string]any, error) {
	var envelope string
	if err := db.Table("cat_vault_meta").Where("account_id = ?", ownerID).Pluck("server_wrapped_dek", &envelope).Error; err != nil {
		return nil, err
	}
	dek, err := accountDEK(ownerID, envelope)
	if err != nil {
		return nil, err
	}
	var rows []struct {
		EntityType string
		EntityID   string
		Revision   int64
		Payload    string
	}
	if err := db.Table("cat_vaults").Where("account_id = ? AND tombstone = ?", ownerID, false).Order("server_revision").Find(&rows).Error; err != nil {
		return nil, err
	}
	records := make([]map[string]any, 0)
	for _, row := range rows {
		plain, decryptErr := decryptRecord(dek, row.EntityType, row.EntityID, row.Payload)
		if decryptErr != nil {
			continue
		}
		value := map[string]any{}
		if json.Unmarshal(plain, &value) != nil {
			continue
		}
		if row.EntityType == "cat" && row.EntityID == recordID || value["catId"] == recordID || value["relatedCatId"] == recordID {
			value["_type"], value["_id"], value["_revision"] = row.EntityType, row.EntityID, row.Revision
			records = append(records, value)
		}
	}
	var media []map[string]any
	if err := db.Table("cat_media").Select("id,url,original_name,entity_type,entity_id,usage,category,created_at").Where("owner_id = ? AND entity_type = ? AND entity_id = ?", ownerID, "cat", recordID).Order("created_at DESC").Find(&media).Error; err != nil {
		return nil, err
	}
	return map[string]any{"ownerId": ownerID, "recordId": recordID, "records": records, "media": media}, nil
}

func ListTable(db *gorm.DB, table, order string, page, size int, keyword string, columns ...string) (Page, error) {
	query := db.Table(table)
	if keyword != "" && len(columns) > 0 {
		like := "%" + strings.TrimSpace(keyword) + "%"
		condition := strings.Join(columns, " LIKE ? OR ") + " LIKE ?"
		args := make([]interface{}, len(columns))
		for i := range args {
			args[i] = like
		}
		query = query.Where(condition, args...)
	}
	var total int64
	if err := query.Count(&total).Error; err != nil {
		return Page{}, err
	}
	var rows []map[string]any
	err := query.Order(order).Offset((page - 1) * size).Limit(size).Find(&rows).Error
	return Page{Items: rows, Total: total}, err
}
