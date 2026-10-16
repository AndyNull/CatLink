package catlink

import (
	"context"
	"net/http"
	"strconv"
	"testing"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	catmodel "github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/require"
)

func TestConversationBlockIsBidirectionalAndStopsMessageCreation(t *testing.T) {
	db := integrityTestDB(t, &catmodel.CatSafetyRelation{}, &catmodel.CatConversation{}, &catmodel.CatMessage{}, &catmodel.CatNotification{}, &catmodel.CatNotificationOutbox{})
	conversation := catmodel.CatConversation{UserA: 7, UserB: 8, UpdatedAt: time.Now()}
	require.NoError(t, db.Create(&conversation).Error)
	require.NoError(t, db.Create(&catmodel.CatSafetyRelation{UserID: 8, TargetID: 7, Kind: "block", CreatedAt: time.Now()}).Error)
	require.True(t, conversationSuppressed(7, 8))

	envelope := invokeJSON(t, http.MethodPost, "/api/v1/conversations/1/messages", `{"body":"不应发送"}`, 7, func(c *gin.Context) {
		c.Params = gin.Params{{Key: "conversationId", Value: "1"}}
		CatApiGroup.SendMessage(c)
	})
	require.Equal(t, float64(7), envelope["code"])
	var messages, notifications int64
	require.NoError(t, db.Model(&catmodel.CatMessage{}).Count(&messages).Error)
	require.NoError(t, db.Model(&catmodel.CatNotification{}).Count(&notifications).Error)
	require.Zero(t, messages)
	require.Zero(t, notifications)
}

func TestAdoptionTimelineRejectsInvalidTerminalTransition(t *testing.T) {
	db := integrityTestDB(t, &catmodel.CatPublicCat{}, &catmodel.CatApply{}, &catmodel.CatAdoptionEvent{}, &catmodel.CatTimelineEvent{}, &catmodel.CatNotification{}, &catmodel.CatNotificationOutbox{})
	require.NoError(t, db.Create(&catmodel.CatPublicCat{OwnerID: 2, Nickname: "小猫", Status: catStatusAdoptable, State: "published"}).Error)
	application := catmodel.CatApply{CatID: 1, ApplicantID: 1, OwnerID: 2, Status: "pending", CreatedAt: time.Now(), UpdatedAt: time.Now()}
	require.NoError(t, db.Create(&application).Error)

	contact := invokeJSON(t, http.MethodPost, "/api/v1/adoption-applications/1/timeline", `{"status":"contact"}`, 2, func(c *gin.Context) {
		c.Params = gin.Params{{Key: "applicationId", Value: "1"}}
		CatApiGroup.AddAdoptionEvent(c)
	})
	require.Equal(t, float64(0), contact["code"])
	approved := invokeJSON(t, http.MethodPost, "/api/v1/adoption-applications/1/timeline", `{"status":"approved"}`, 2, func(c *gin.Context) {
		c.Params = gin.Params{{Key: "applicationId", Value: "1"}}
		CatApiGroup.AddAdoptionEvent(c)
	})
	require.Equal(t, float64(0), approved["code"])
	rejected := invokeJSON(t, http.MethodPost, "/api/v1/adoption-applications/1/timeline", `{"status":"rejected"}`, 2, func(c *gin.Context) {
		c.Params = gin.Params{{Key: "applicationId", Value: "1"}}
		CatApiGroup.AddAdoptionEvent(c)
	})
	require.Equal(t, float64(7), rejected["code"])
	var count int64
	require.NoError(t, db.Model(&catmodel.CatAdoptionEvent{}).Count(&count).Error)
	require.EqualValues(t, 2, count)
}

func TestAdoptionApplicationsOnlyReturnsParticipantRows(t *testing.T) {
	db := integrityTestDB(t, &catmodel.CatApply{}, &catmodel.CatAdoptionEvent{}, &catmodel.CatPublicCat{}, &system.SysUser{})
	require.NoError(t, db.Create(&catmodel.CatApply{ApplicantID: 7, OwnerID: 8, Status: "pending", CreatedAt: time.Now()}).Error)
	require.NoError(t, db.Create(&catmodel.CatApply{ApplicantID: 9, OwnerID: 10, Status: "pending", CreatedAt: time.Now()}).Error)
	require.NoError(t, db.Create(&catmodel.CatAdoptionEvent{ApplyID: 1, ActorID: 7, Status: "pending", Note: "已提交", CreatedAt: time.Now()}).Error)

	envelope := invokeJSON(t, http.MethodGet, "/api/v1/adoption-applications", "", 7, CatApiGroup.AdoptionApplications)
	require.Equal(t, float64(0), envelope["code"])
	items := envelope["data"].(map[string]any)["items"].([]any)
	require.Len(t, items, 1)
	require.Len(t, items[0].(map[string]any)["timeline"].([]any), 1)
}

func TestNotificationOutboxClaimIsExclusiveAndOfflineDoesNotExhaustRetries(t *testing.T) {
	db := integrityTestDB(t, &catmodel.CatNotification{}, &catmodel.CatNotificationOutbox{})
	notification := catmodel.CatNotification{ToUser: 424242, Type: "adoption", Title: "状态更新", CreatedAt: time.Now()}
	require.NoError(t, db.Create(&notification).Error)
	outbox := catmodel.CatNotificationOutbox{NotificationID: notification.ID, Provider: "sse", Status: "pending", CreatedAt: time.Now(), UpdatedAt: time.Now()}
	require.NoError(t, db.Create(&outbox).Error)

	now := time.Now()
	require.True(t, claimNotificationOutbox(outbox.ID, now))
	require.False(t, claimNotificationOutbox(outbox.ID, now))
	require.NoError(t, db.Model(&outbox).Updates(map[string]any{"status": "pending", "updated_at": now}).Error)
	require.NoError(t, ProcessNotificationOutbox(context.Background()))
	require.NoError(t, db.First(&outbox, outbox.ID).Error)
	require.Equal(t, "pending", outbox.Status)
	require.Zero(t, outbox.Attempts)
	require.NotNil(t, outbox.NextAttemptAt)
}

func TestRescueTaskCannotReopenAfterCompletion(t *testing.T) {
	db := integrityTestDB(t, &catmodel.CatRescueTask{})
	task := catmodel.CatRescueTask{OwnerID: 7, AssigneeID: 8, Status: "open", CreatedAt: time.Now()}
	require.NoError(t, db.Create(&task).Error)

	done := invokeJSON(t, http.MethodPut, "/api/v1/rescue-tasks/1", `{"status":"done"}`, 8, func(c *gin.Context) {
		c.Params = gin.Params{{Key: "taskId", Value: "1"}}
		CatApiGroup.UpdateRescueTask(c)
	})
	require.Equal(t, float64(0), done["code"])
	reopen := invokeJSON(t, http.MethodPut, "/api/v1/rescue-tasks/1", `{"status":"open"}`, 8, func(c *gin.Context) {
		c.Params = gin.Params{{Key: "taskId", Value: "1"}}
		CatApiGroup.UpdateRescueTask(c)
	})
	require.Equal(t, float64(7), reopen["code"])
}

func TestMissingCaseCoordinatesAreRoundedAndTimelineOwnerOnly(t *testing.T) {
	db := integrityTestDB(t, &catmodel.CatPublicCat{}, &catmodel.CatMissingCase{}, &catmodel.CatTimelineEvent{})
	require.NoError(t, db.Create(&catmodel.CatPublicCat{OwnerID: 7, Nickname: "小猫", State: "published"}).Error)
	created := invokeJSON(t, http.MethodPost, "/api/v1/missing-cases", `{"catId":"1","latitude":30.123456,"longitude":120.654321,"radiusKm":3,"description":"走失"}`, 7, CatApiGroup.CreateMissingCase)
	require.Equal(t, float64(0), created["code"])
	var missing catmodel.CatMissingCase
	require.NoError(t, db.First(&missing).Error)
	require.Equal(t, 30.12, missing.Latitude)
	require.Equal(t, 120.65, missing.Longitude)

	denied := invokeJSON(t, http.MethodPost, "/api/v1/public/cats/1/timeline", `{"body":"他人的动态"}`, 8, func(c *gin.Context) {
		c.Params = gin.Params{{Key: "id", Value: "1"}}
		CatApiGroup.AddTimeline(c)
	})
	require.Equal(t, float64(7), denied["code"])
}

func TestAnnouncementCampaignSegmentsSchedulesAndWithdraws(t *testing.T) {
	db := integrityTestDB(t, &system.SysUser{}, &catmodel.CatPublicCat{}, &catmodel.CatPost{}, &catmodel.CatNotification{}, &catmodel.CatNotificationOutbox{}, &catmodel.CatAnnouncementCampaign{})
	for _, id := range []uint{7, 8, 9} {
		require.NoError(t, db.Create(&system.SysUser{GVA_MODEL: global.GVA_MODEL{ID: id}, Username: "user" + strconv.Itoa(int(id)), Enable: 1}).Error)
	}
	require.NoError(t, db.Create(&catmodel.CatPublicCat{OwnerID: 7, LocalID: "cat-7", DistrictCode: "武侯区", State: "published"}).Error)
	require.NoError(t, db.Create(&catmodel.CatPost{AuthorID: 8, DistrictCode: "武侯区"}).Error)
	campaign := catmodel.CatAnnouncementCampaign{Title: "分群公告", Body: "正文", SegmentType: "district", SegmentValue: "武侯区", Status: "scheduled", ScheduledAt: time.Now().Add(-time.Minute), CreatedAt: time.Now(), UpdatedAt: time.Now()}
	require.NoError(t, db.Create(&campaign).Error)
	require.NoError(t, DeliverScheduledAnnouncements(context.Background()))

	var delivered catmodel.CatAnnouncementCampaign
	require.NoError(t, db.First(&delivered, campaign.ID).Error)
	require.Equal(t, "sent", delivered.Status)
	require.Equal(t, 2, delivered.AudienceCount)
	var notificationCount int64
	require.NoError(t, db.Model(&catmodel.CatNotification{}).Where("campaign_id = ?", campaign.ID).Count(&notificationCount).Error)
	require.EqualValues(t, 2, notificationCount)

	envelope := invokeJSON(t, http.MethodPut, "/api/v1/moderation/notifications/campaigns/1/withdraw", "", 1, func(c *gin.Context) {
		c.Params = gin.Params{{Key: "id", Value: strconv.Itoa(int(campaign.ID))}}
		CatApiGroup.WithdrawAnnouncement(c)
	})
	require.Equal(t, float64(0), envelope["code"])
	require.NoError(t, db.Model(&catmodel.CatNotification{}).Where("campaign_id = ?", campaign.ID).Count(&notificationCount).Error)
	require.Zero(t, notificationCount)

	future := catmodel.CatAnnouncementCampaign{Title: "定时公告", Body: "稍后", SegmentType: "all", Status: "scheduled", ScheduledAt: time.Now().Add(time.Hour), CreatedAt: time.Now(), UpdatedAt: time.Now()}
	require.NoError(t, db.Create(&future).Error)
	require.NoError(t, DeliverScheduledAnnouncements(context.Background()))
	require.NoError(t, db.First(&future, future.ID).Error)
	require.Equal(t, "scheduled", future.Status)
}
