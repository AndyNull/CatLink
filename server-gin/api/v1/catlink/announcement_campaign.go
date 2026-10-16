package catlink

import (
	"context"
	"encoding/json"
	"strconv"
	"strings"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	catmodel "github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	"github.com/flipped-aurora/gin-vue-admin/server/utils"
	"github.com/flipped-aurora/gin-vue-admin/server/utils/datascope"
	"github.com/flipped-aurora/gin-vue-admin/server/utils/sse"
	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

var announcementSegments = map[string]bool{"all": true, "district": true, "cat_status": true, "active_days": true}

func (a *CatAdminApi) createAnnouncementCampaign(c *gin.Context) {
	db := global.GVA_DB.WithContext(datascope.WithSystem(c.Request.Context()))
	var body struct {
		Title        string `json:"title"`
		Body         string `json:"body"`
		Link         string `json:"link"`
		SegmentType  string `json:"segmentType"`
		SegmentValue string `json:"segmentValue"`
		ScheduledAt  string `json:"scheduledAt"`
	}
	if c.ShouldBindJSON(&body) != nil {
		fail(c, "公告参数无效")
		return
	}
	body.Title, body.Body, body.Link = strings.TrimSpace(body.Title), strings.TrimSpace(body.Body), strings.TrimSpace(body.Link)
	if body.Title == "" || body.Body == "" || len([]rune(body.Title)) > 120 || len([]rune(body.Body)) > 10000 {
		fail(c, "公告标题或内容无效")
		return
	}
	validAppLink := strings.HasPrefix(body.Link, "/") && !strings.HasPrefix(body.Link, "//") && len(body.Link) <= 300
	if body.Link != "" && safeHTTPSURL(body.Link) == "" && !validAppLink {
		fail(c, "公告链接必须是 App 内路径或 HTTPS 地址")
		return
	}
	if body.SegmentType == "" {
		body.SegmentType = "all"
	}
	if !announcementSegments[body.SegmentType] || (body.SegmentType != "all" && strings.TrimSpace(body.SegmentValue) == "") {
		fail(c, "公告分群无效")
		return
	}
	if body.SegmentType == "cat_status" {
		status, _, valid := normalizeCatStatus(body.SegmentValue)
		if !valid {
			fail(c, "公告猫咪状态分群无效")
			return
		}
		body.SegmentValue = status
	}
	scheduledAt := time.Now()
	if body.ScheduledAt != "" {
		parsed, err := time.Parse(time.RFC3339, body.ScheduledAt)
		if err != nil {
			fail(c, "定时发送时间无效")
			return
		}
		scheduledAt = parsed
	}
	row := catmodel.CatAnnouncementCampaign{
		Title: body.Title, Body: body.Body, Link: body.Link,
		SegmentType: body.SegmentType, SegmentValue: strings.TrimSpace(body.SegmentValue),
		Status: "scheduled", ScheduledAt: scheduledAt, CreatedBy: utils.GetUserID(c),
		CreatedAt: time.Now(), UpdatedAt: time.Now(),
	}
	if err := db.Create(&row).Error; err != nil {
		fail(c, "公告创建失败")
		return
	}
	if !scheduledAt.After(time.Now()) {
		if err := deliverAnnouncement(c.Request.Context(), row.ID); err != nil {
			fail(c, "公告投递失败")
			return
		}
		db.First(&row, row.ID)
	}
	ok(c, gin.H{"id": row.ID, "sent": row.AudienceCount, "status": row.Status})
}

func announcementAudience(db *gorm.DB, row catmodel.CatAnnouncementCampaign) ([]uint, error) {
	query := db.Model(&system.SysUser{}).Select("id")
	switch row.SegmentType {
	case "district":
		owners := db.Model(&catmodel.CatPublicCat{}).Select("owner_id").Where("district_code = ?", row.SegmentValue)
		authors := db.Model(&catmodel.CatPost{}).Select("author_id").Where("district_code = ?", row.SegmentValue)
		query = query.Where("id IN (?) OR id IN (?)", owners, authors)
	case "cat_status":
		query = query.Where("id IN (?)", db.Model(&catmodel.CatPublicCat{}).Select("owner_id").Where("status = ?", row.SegmentValue))
	case "active_days":
		days, err := strconv.Atoi(row.SegmentValue)
		if err != nil || days < 1 || days > 365 {
			return nil, gorm.ErrInvalidValue
		}
		query = query.Where("id IN (?)", db.Model(&catmodel.CatDeviceSession{}).Select("user_id").Where("last_seen_at >= ? AND revoked_at IS NULL", time.Now().Add(-time.Duration(days)*24*time.Hour)))
	}
	var ids []uint
	return ids, query.Order("id asc").Pluck("id", &ids).Error
}

func deliverAnnouncement(ctx context.Context, id uint) error {
	ctx = datascope.WithSystem(ctx)
	now := time.Now()
	claim := global.GVA_DB.WithContext(ctx).Model(&catmodel.CatAnnouncementCampaign{}).
		Where("id = ? AND status = ? AND scheduled_at <= ?", id, "scheduled", now).
		Updates(map[string]any{"status": "sending", "updated_at": now})
	if claim.Error != nil || claim.RowsAffected == 0 {
		return claim.Error
	}
	var campaign catmodel.CatAnnouncementCampaign
	if err := global.GVA_DB.WithContext(ctx).First(&campaign, id).Error; err != nil {
		return err
	}
	ids, err := announcementAudience(global.GVA_DB.WithContext(ctx), campaign)
	if err != nil {
		global.GVA_DB.WithContext(ctx).Model(&campaign).Updates(map[string]any{"status": "failed", "updated_at": time.Now()})
		return err
	}
	delivered := 0
	err = global.GVA_DB.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		notifications := make([]catmodel.CatNotification, 0, len(ids))
		for _, uid := range ids {
			notifications = append(notifications, catmodel.CatNotification{CampaignID: campaign.ID, ToUser: uid, Type: "announcement", Title: campaign.Title, Body: campaign.Body, Link: campaign.Link, CreatedAt: now})
		}
		if len(notifications) == 0 {
			return nil
		}
		if err := tx.CreateInBatches(&notifications, 200).Error; err != nil {
			return err
		}
		outbox := make([]catmodel.CatNotificationOutbox, 0, len(notifications))
		for _, notification := range notifications {
			status := "pending"
			if sse.Default().Online(notification.ToUser) {
				payload, _ := json.Marshal(gin.H{"id": notification.ID, "kind": "announcement", "title": notification.Title})
				sse.Default().PublishToUsers([]uint{notification.ToUser}, sse.Event{Name: "catlink_notification", Data: string(payload)})
				status = "sent"
				delivered++
			}
			outbox = append(outbox, catmodel.CatNotificationOutbox{NotificationID: notification.ID, Provider: "sse", Status: status, Attempts: 1, CreatedAt: now, UpdatedAt: now})
		}
		return tx.CreateInBatches(&outbox, 200).Error
	})
	if err != nil {
		global.GVA_DB.WithContext(ctx).Model(&campaign).Updates(map[string]any{"status": "failed", "updated_at": time.Now()})
		return err
	}
	return global.GVA_DB.WithContext(ctx).Model(&campaign).Updates(map[string]any{"status": "sent", "audience_count": len(ids), "delivered_count": delivered, "sent_at": now, "updated_at": now}).Error
}

// DeliverScheduledAnnouncements dispatches campaigns claimed by their due time.
func DeliverScheduledAnnouncements(ctx context.Context) error {
	ctx = datascope.WithSystem(ctx)
	global.GVA_DB.WithContext(ctx).Model(&catmodel.CatAnnouncementCampaign{}).
		Where("status = ? AND updated_at < ?", "sending", time.Now().Add(-5*time.Minute)).
		Updates(map[string]any{"status": "scheduled", "updated_at": time.Now()})
	var ids []uint
	if err := global.GVA_DB.WithContext(ctx).Model(&catmodel.CatAnnouncementCampaign{}).
		Where("status = ? AND scheduled_at <= ?", "scheduled", time.Now()).Order("id asc").Limit(20).Pluck("id", &ids).Error; err != nil {
		return err
	}
	for _, id := range ids {
		if err := deliverAnnouncement(ctx, id); err != nil {
			return err
		}
	}
	return nil
}

func (a *CatAdminApi) AnnouncementCampaigns(c *gin.Context) {
	db := global.GVA_DB.WithContext(datascope.WithSystem(c.Request.Context()))
	var rows []catmodel.CatAnnouncementCampaign
	db.Order("created_at desc").Limit(100).Find(&rows)
	for index := range rows {
		var readCount, deliveredCount int64
		db.Model(&catmodel.CatNotification{}).Where("campaign_id = ? AND read_flag = ?", rows[index].ID, true).Count(&readCount)
		db.Model(&catmodel.CatNotificationOutbox{}).
			Where("notification_id IN (?) AND status = ?", db.Model(&catmodel.CatNotification{}).Select("id").Where("campaign_id = ?", rows[index].ID), "sent").Count(&deliveredCount)
		rows[index].ReadCount = int(readCount)
		rows[index].DeliveredCount = int(deliveredCount)
	}
	ok(c, gin.H{"items": rows})
}

func (a *CatAdminApi) WithdrawAnnouncement(c *gin.Context) {
	db := global.GVA_DB.WithContext(datascope.WithSystem(c.Request.Context()))
	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil || id == 0 {
		fail(c, "公告不存在")
		return
	}
	now := time.Now()
	err = db.Transaction(func(tx *gorm.DB) error {
		var row catmodel.CatAnnouncementCampaign
		if err := tx.Where("id = ? AND status IN ?", uint(id), []string{"scheduled", "sent", "failed"}).First(&row).Error; err != nil {
			return err
		}
		notificationIDs := tx.Model(&catmodel.CatNotification{}).Select("id").Where("campaign_id = ?", row.ID)
		if err := tx.Where("notification_id IN (?)", notificationIDs).Delete(&catmodel.CatNotificationOutbox{}).Error; err != nil {
			return err
		}
		if err := tx.Where("campaign_id = ?", row.ID).Delete(&catmodel.CatNotification{}).Error; err != nil {
			return err
		}
		return tx.Model(&row).Updates(map[string]any{"status": "withdrawn", "withdrawn_at": now, "updated_at": now}).Error
	})
	if err != nil {
		fail(c, "公告不存在或无法撤回")
		return
	}
	ok(c, gin.H{"id": id, "status": "withdrawn"})
}
