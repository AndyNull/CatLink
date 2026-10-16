package catlink

import (
	"encoding/json"
	"net/http"
	"strings"
	"testing"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	catmodel "github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	"github.com/stretchr/testify/require"
)

func TestAccountExportIncludesOwnedDomainsWithoutSecretsOrForeignRows(t *testing.T) {
	db := integrityTestDB(t,
		&system.SysUser{}, &catmodel.CatAccountSecurity{}, &catmodel.CatDeviceSession{},
		&catmodel.CatShare{}, &catmodel.CatPublicCat{}, &catmodel.CatPost{}, &catmodel.CatComment{},
		&catmodel.CatCommentLike{}, &catmodel.CatLike{}, &catmodel.CatBookmark{}, &catmodel.CatFollow{},
		&catmodel.CatUserFollow{}, &catmodel.CatReport{}, &catmodel.CatModerationAppeal{},
		&catmodel.CatNotification{}, &catmodel.CatApply{}, &catmodel.CatAdoptionEvent{},
		&catmodel.CatConversation{}, &catmodel.CatMessage{}, &catmodel.CatMedia{},
		&catmodel.CatVault{}, &catmodel.CatVaultMeta{}, &catmodel.CatSafetyRelation{},
		&catmodel.CatTimelineEvent{}, &catmodel.CatMissingCase{}, &catmodel.CatMissingSighting{},
		&catmodel.CatRescueTask{}, &catmodel.CatIdentityBadge{}, &catmodel.CatPushDevice{},
	)
	require.NoError(t, db.Create(&system.SysUser{GVA_MODEL: global.GVA_MODEL{ID: 7}, Username: "owner@example.com", Password: "password-secret", NickName: "本人"}).Error)
	require.NoError(t, db.Create(&system.SysUser{GVA_MODEL: global.GVA_MODEL{ID: 8}, Username: "other@example.com", Password: "other-secret", NickName: "他人"}).Error)
	require.NoError(t, db.Create(&catmodel.CatAccountSecurity{UserID: 7, EmailVerified: true}).Error)
	require.NoError(t, db.Create(&catmodel.CatDeviceSession{ID: "own-device", UserID: 7, AccessTokenHash: "access-hash-secret", RefreshTokenHash: "refresh-hash-secret"}).Error)
	require.NoError(t, db.Create(&catmodel.CatPost{AuthorID: 7, Title: "本人帖子"}).Error)
	require.NoError(t, db.Create(&catmodel.CatPost{AuthorID: 8, Title: "他人帖子"}).Error)
	require.NoError(t, db.Create(&catmodel.CatComment{PostID: 2, AuthorID: 7, Body: "本人评论"}).Error)
	require.NoError(t, db.Create(&catmodel.CatComment{PostID: 1, AuthorID: 8, Body: "他人评论"}).Error)
	require.NoError(t, db.Create(&catmodel.CatMedia{OwnerID: 7, Category: "cat", URL: "/owned-media", StorageKey: "storage-key-secret"}).Error)
	require.NoError(t, db.Create(&catmodel.CatMedia{OwnerID: 8, Category: "cat", URL: "/foreign-media"}).Error)
	require.NoError(t, db.Create(&catmodel.CatVault{AccountID: 7, EntityType: "cat", EntityID: "local-1", Payload: "owned-ciphertext"}).Error)
	require.NoError(t, db.Create(&catmodel.CatVault{AccountID: 8, EntityType: "cat", EntityID: "local-2", Payload: "foreign-ciphertext"}).Error)
	conversation := catmodel.CatConversation{UserA: 7, UserB: 8, LastMessage: "参与者消息"}
	require.NoError(t, db.Create(&conversation).Error)
	require.NoError(t, db.Create(&catmodel.CatMessage{ConvID: conversation.ID, SenderID: 8, Body: "参与者消息"}).Error)

	envelope := invokeJSON(t, http.MethodGet, "/api/v1/account/export", "", 7, CatApiGroup.ExportAccount)
	require.Equal(t, float64(0), envelope["code"])
	encoded, err := json.Marshal(envelope["data"])
	require.NoError(t, err)
	text := string(encoded)
	for _, expected := range []string{"owner@example.com", "本人帖子", "本人评论", "/owned-media", "owned-ciphertext", "参与者消息"} {
		require.Contains(t, text, expected)
	}
	for _, forbidden := range []string{"password-secret", "access-hash-secret", "refresh-hash-secret", "storage-key-secret", "他人帖子", "他人评论", "/foreign-media", "foreign-ciphertext"} {
		require.False(t, strings.Contains(text, forbidden), forbidden)
	}
}
