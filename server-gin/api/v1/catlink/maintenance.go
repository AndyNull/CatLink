package catlink

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	catlinkModel "github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/flipped-aurora/gin-vue-admin/server/utils/sse"
	"gorm.io/gorm"
)

func CleanupNotificationHistory(ctx context.Context) error {
	now := time.Now()
	return global.GVA_DB.WithContext(ctx).
		Where("(read_flag = ? AND created_at < ?) OR created_at < ?", true, now.AddDate(0, -6, 0), now.AddDate(-1, 0, 0)).
		Delete(&catlinkModel.CatNotification{}).Error
}

func CleanupMediaUploadWindows(ctx context.Context) error {
	return global.GVA_DB.WithContext(ctx).
		Where("window_start < ?", time.Now().Add(-2*time.Hour)).
		Delete(&catlinkModel.CatMediaUploadWindow{}).Error
}

func CleanupExpiredAuthCodes(ctx context.Context) error {
	before := time.Now().Add(-24 * time.Hour)
	return global.GVA_DB.WithContext(ctx).
		Where("expires_at < ? OR (consumed_at IS NOT NULL AND consumed_at < ?)", before, before).
		Delete(&catlinkModel.CatAuthCode{}).Error
}

// CleanupRetentionPolicies applies configured retention to notifications, outbox and soft-deleted media.
func CleanupRetentionPolicies(ctx context.Context) error {
	if err := CleanupExpiredAuthCodes(ctx); err != nil {
		return fmt.Errorf("清理邮箱验证码失败: %w", err)
	}
	var policies []catlinkModel.CatRetentionPolicy
	if err := global.GVA_DB.WithContext(ctx).Where("enabled = ?", true).Find(&policies).Error; err != nil {
		return err
	}
	for _, policy := range policies {
		if policy.Days < 1 {
			continue
		}
		before := time.Now().Add(-time.Duration(policy.Days) * 24 * time.Hour)
		var err error
		switch policy.Kind {
		case "notifications":
			err = global.GVA_DB.WithContext(ctx).Where("created_at < ?", before).Delete(&catlinkModel.CatNotification{}).Error
		case "notification_outbox":
			err = global.GVA_DB.WithContext(ctx).Where("created_at < ?", before).Delete(&catlinkModel.CatNotificationOutbox{}).Error
		case "temporary_media":
			err = cleanupTemporaryMediaBefore(ctx, before)
		}
		if err != nil {
			return fmt.Errorf("清理 %s 失败: %w", policy.Kind, err)
		}
	}
	return nil
}

func cleanupTemporaryMediaBefore(ctx context.Context, before time.Time) error {
	for {
		var rows []catlinkModel.CatMedia
		if err := global.GVA_DB.WithContext(ctx).
			Where("temporary = ? AND created_at < ?", true, before).
			Order("id asc").Limit(100).Find(&rows).Error; err != nil {
			return err
		}
		if len(rows) == 0 {
			return nil
		}
		for _, row := range rows {
			if err := deleteMediaObject(ctx, row); err != nil {
				return fmt.Errorf("删除临时媒体对象 %d 失败: %w", row.ID, err)
			}
			if err := global.GVA_DB.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
				if err := deleteMediaCatalog(tx, row); err != nil {
					return err
				}
				return tx.Delete(&catlinkModel.CatMedia{}, row.ID).Error
			}); err != nil {
				return err
			}
		}
	}
}

// ProcessNotificationOutbox retries durable notification events and marks successful SSE delivery.
func ProcessNotificationOutbox(ctx context.Context) error {
	var rows []catlinkModel.CatNotificationOutbox
	now := time.Now()
	if err := global.GVA_DB.WithContext(ctx).
		Where("(status = ? AND (next_attempt_at IS NULL OR next_attempt_at <= ?)) OR (status = ? AND updated_at < ?)",
			"pending", now, "processing", now.Add(-5*time.Minute)).
		Order("id asc").Limit(100).Find(&rows).Error; err != nil {
		return err
	}
	for _, row := range rows {
		if !claimNotificationOutbox(row.ID, time.Now()) {
			continue
		}
		var notification catlinkModel.CatNotification
		if err := global.GVA_DB.WithContext(ctx).First(&notification, row.NotificationID).Error; err != nil {
			if retryErr := markOutboxRetry(ctx, &row, err.Error()); retryErr != nil {
				return retryErr
			}
			continue
		}
		if row.Provider != "sse" {
			if err := markOutboxRetry(ctx, &row, "推送供应商未配置"); err != nil {
				return err
			}
			continue
		}
		if !sse.Default().Online(notification.ToUser) {
			if err := global.GVA_DB.WithContext(ctx).Model(&catlinkModel.CatNotificationOutbox{}).
				Where("id = ? AND status = ?", row.ID, "processing").
				Updates(map[string]any{"status": "pending", "next_attempt_at": time.Now().Add(time.Minute), "last_error": "用户当前不在线", "updated_at": time.Now()}).Error; err != nil {
				return err
			}
			continue
		}
		payload, _ := json.Marshal(map[string]any{"id": notification.ID, "kind": notification.Type, "title": notification.Title})
		sse.Default().PublishToUsers([]uint{notification.ToUser}, sse.Event{Name: "catlink_notification", Data: string(payload)})
		markNotificationOutboxSent(row.ID, row.Attempts)
	}
	return nil
}

func markOutboxRetry(ctx context.Context, row *catlinkModel.CatNotificationOutbox, message string) error {
	attempts := row.Attempts + 1
	updates := map[string]any{"status": "pending", "attempts": attempts, "last_error": message, "updated_at": time.Now()}
	if attempts >= 8 {
		updates["status"] = "dead"
		updates["next_attempt_at"] = nil
	} else {
		delay := time.Minute * time.Duration(1<<min(attempts-1, 6))
		updates["next_attempt_at"] = time.Now().Add(delay)
	}
	return global.GVA_DB.WithContext(ctx).Model(&catlinkModel.CatNotificationOutbox{}).
		Where("id = ? AND status = ?", row.ID, "processing").Updates(updates).Error
}
