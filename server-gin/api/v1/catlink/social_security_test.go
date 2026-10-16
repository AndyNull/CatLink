package catlink

import (
	"net/http"
	"testing"
	"time"

	catmodel "github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/require"
)

func TestPrivateMessageRateLimitAndAtomicNotification(t *testing.T) {
	db := integrityTestDB(t,
		&catmodel.CatConversation{}, &catmodel.CatMessage{}, &catmodel.CatSafetyRelation{},
		&catmodel.CatNotification{}, &catmodel.CatNotificationOutbox{},
	)
	conversation := catmodel.CatConversation{UserA: 7, UserB: 8, UpdatedAt: time.Now()}
	require.NoError(t, db.Create(&conversation).Error)
	recent := make([]catmodel.CatMessage, 30)
	for index := range recent {
		recent[index] = catmodel.CatMessage{ConvID: conversation.ID, SenderID: 7, Body: "消息", CreatedAt: time.Now()}
	}
	require.NoError(t, db.Create(&recent).Error)

	invoke := func() map[string]any {
		return invokeJSON(t, http.MethodPost, "/api/v1/conversations/1/messages", `{"body":"新的消息"}`, 7, func(c *gin.Context) {
			c.Params = gin.Params{{Key: "conversationId", Value: "1"}}
			CatApiGroup.SendMessage(c)
		})
	}
	require.Equal(t, float64(7), invoke()["code"])
	var count int64
	require.NoError(t, db.Model(&catmodel.CatMessage{}).Count(&count).Error)
	require.Equal(t, int64(30), count)

	require.NoError(t, db.Model(&catmodel.CatMessage{}).Where("sender_id = ?", 7).Update("created_at", time.Now().Add(-2*time.Hour)).Error)
	require.Equal(t, float64(0), invoke()["code"])
	require.NoError(t, db.Model(&catmodel.CatMessage{}).Count(&count).Error)
	require.Equal(t, int64(31), count)
	require.NoError(t, db.Model(&catmodel.CatNotification{}).Count(&count).Error)
	require.Equal(t, int64(1), count)
	require.NoError(t, db.Model(&catmodel.CatNotificationOutbox{}).Count(&count).Error)
	require.Equal(t, int64(1), count)
}
