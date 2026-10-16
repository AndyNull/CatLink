package service

import (
	"testing"
	"time"

	"github.com/glebarez/sqlite"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

func TestAppNotificationsStayInsideCurrentAccount(t *testing.T) {
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, db.AutoMigrate(new(notificationRow)))
	now := time.Now()
	require.NoError(t, db.Create(&notificationRow{ID: 1, ToUser: 7, Type: "announcement", Title: "公告", CreatedAt: now}).Error)
	require.NoError(t, db.Create(&notificationRow{ID: 2, ToUser: 8, Type: "private", Title: "私聊", CreatedAt: now.Add(time.Second)}).Error)

	items, err := AppNotifications(db, 7)
	require.NoError(t, err)
	require.Len(t, items, 1)
	require.Equal(t, "1", items[0].ID)
	require.NoError(t, MarkAppNotificationsRead(db, 7, "2"))

	var foreign notificationRow
	require.NoError(t, db.First(&foreign, 2).Error)
	require.False(t, foreign.ReadFlag)
	require.NoError(t, MarkAppNotificationsRead(db, 7, "1"))
	var own notificationRow
	require.NoError(t, db.First(&own, 1).Error)
	require.True(t, own.ReadFlag)
}

func TestMarkAppNotificationsReadRejectsInvalidID(t *testing.T) {
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, db.AutoMigrate(new(notificationRow)))
	require.ErrorIs(t, MarkAppNotificationsRead(db, 7, "invalid"), ErrNotificationID)
}
