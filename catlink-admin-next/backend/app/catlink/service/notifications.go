package service

import (
	"errors"
	"strconv"
	"strings"
	"time"

	"gorm.io/gorm"
)

var ErrNotificationID = errors.New("通知标识无效")

type notificationRow struct {
	ID        uint
	ToUser    uint
	Type      string
	Title     string
	Body      string
	Link      string
	ReadFlag  bool
	CreatedAt time.Time
}

func (notificationRow) TableName() string { return "cat_notifications" }

type AppNotification struct {
	ID        string `json:"id"`
	Kind      string `json:"kind"`
	Title     string `json:"title"`
	Body      string `json:"body"`
	RefType   string `json:"refType"`
	RefID     string `json:"refId"`
	Read      bool   `json:"read"`
	IsRead    bool   `json:"isRead"`
	CreatedAt int64  `json:"createdAt"`
}

func AppNotifications(db *gorm.DB, accountID uint) ([]AppNotification, error) {
	var rows []notificationRow
	if err := db.Where("to_user = ?", accountID).Order("created_at DESC, id DESC").Limit(60).Find(&rows).Error; err != nil {
		return nil, err
	}
	items := make([]AppNotification, len(rows))
	for i, row := range rows {
		items[i] = AppNotification{ID: strconv.FormatUint(uint64(row.ID), 10), Kind: row.Type, Title: row.Title, Body: row.Body, RefType: "link", RefID: row.Link, Read: row.ReadFlag, IsRead: row.ReadFlag, CreatedAt: row.CreatedAt.UnixMilli()}
	}
	return items, nil
}

func MarkAppNotificationsRead(db *gorm.DB, accountID uint, rawID string) error {
	query := db.Model(&notificationRow{}).Where("to_user = ? AND read_flag = ?", accountID, false)
	if id := strings.TrimSpace(rawID); id != "" {
		value, err := strconv.ParseUint(id, 10, 64)
		if err != nil || value == 0 {
			return ErrNotificationID
		}
		query = query.Where("id = ?", value)
	}
	return query.Update("read_flag", true).Error
}
