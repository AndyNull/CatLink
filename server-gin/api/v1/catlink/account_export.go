package catlink

import (
	"strconv"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	catmodel "github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

type accountVaultExport struct {
	EntityType     string    `json:"entityType"`
	EntityID       string    `json:"entityId"`
	Revision       int64     `json:"revision"`
	ServerRevision int64     `json:"serverRevision"`
	KeyVersion     int       `json:"keyVersion"`
	Tombstone      bool      `json:"tombstone"`
	Payload        string    `json:"payload"`
	UpdatedAt      time.Time `json:"updatedAt"`
}

func accountExportData(tx *gorm.DB, uid uint) (gin.H, error) {
	load := func(dest any, query string, args ...any) error {
		return tx.Where(query, args...).Find(dest).Error
	}
	var user system.SysUser
	if err := tx.First(&user, uid).Error; err != nil {
		return nil, err
	}
	security := []catmodel.CatAccountSecurity{}
	devices := []catmodel.CatDeviceSession{}
	shares := []catmodel.CatShare{}
	publicCats := []catmodel.CatPublicCat{}
	posts := []catmodel.CatPost{}
	comments := []catmodel.CatComment{}
	commentLikes := []catmodel.CatCommentLike{}
	likes := []catmodel.CatLike{}
	bookmarks := []catmodel.CatBookmark{}
	catFollows := []catmodel.CatFollow{}
	userFollows := []catmodel.CatUserFollow{}
	reports := []catmodel.CatReport{}
	appeals := []catmodel.CatModerationAppeal{}
	notifications := []catmodel.CatNotification{}
	applications := []catmodel.CatApply{}
	conversations := []catmodel.CatConversation{}
	media := []catmodel.CatMedia{}
	vault := []accountVaultExport{}
	vaultMeta := []catmodel.CatVaultMeta{}
	safety := []catmodel.CatSafetyRelation{}
	timeline := []catmodel.CatTimelineEvent{}
	missingCases := []catmodel.CatMissingCase{}
	missingSightings := []catmodel.CatMissingSighting{}
	rescueTasks := []catmodel.CatRescueTask{}
	identities := []catmodel.CatIdentityBadge{}
	pushDevices := []catmodel.CatPushDevice{}
	queries := []error{
		load(&security, "user_id = ?", uid),
		load(&devices, "user_id = ?", uid),
		load(&shares, "owner_id = ?", uid),
		load(&publicCats, "owner_id = ?", uid),
		load(&posts, "author_id = ?", uid),
		load(&comments, "author_id = ?", uid),
		load(&commentLikes, "user_id = ?", uid),
		load(&likes, "user_id = ?", uid),
		load(&bookmarks, "user_id = ?", uid),
		load(&catFollows, "user_id = ?", uid),
		load(&userFollows, "user_id = ?", uid),
		load(&reports, "reporter = ?", uid),
		load(&appeals, "user_id = ?", uid),
		load(&notifications, "to_user = ?", uid),
		load(&applications, "applicant_id = ? OR owner_id = ?", uid, uid),
		load(&conversations, "user_a = ? OR user_b = ?", uid, uid),
		load(&media, "owner_id = ?", uid),
		tx.Model(&catmodel.CatVault{}).Select("entity_type, entity_id, revision, server_revision, key_version, tombstone, payload, updated_at").Where("account_id = ?", uid).Find(&vault).Error,
		load(&vaultMeta, "account_id = ?", uid),
		load(&safety, "user_id = ?", uid),
		load(&timeline, "owner_id = ?", uid),
		load(&missingCases, "owner_id = ?", uid),
		load(&missingSightings, "reporter_id = ?", uid),
		load(&rescueTasks, "owner_id = ? OR assignee_id = ?", uid, uid),
		load(&identities, "user_id = ?", uid),
		load(&pushDevices, "user_id = ?", uid),
	}
	for _, err := range queries {
		if err != nil {
			return nil, err
		}
	}

	applicationIDs := make([]uint, 0, len(applications))
	for _, row := range applications {
		applicationIDs = append(applicationIDs, row.ID)
	}
	adoptionEvents := []catmodel.CatAdoptionEvent{}
	if len(applicationIDs) > 0 {
		if err := load(&adoptionEvents, "apply_id IN ?", applicationIDs); err != nil {
			return nil, err
		}
	}
	conversationIDs := make([]uint, 0, len(conversations))
	for _, row := range conversations {
		conversationIDs = append(conversationIDs, row.ID)
	}
	messages := []catmodel.CatMessage{}
	if len(conversationIDs) > 0 {
		if err := load(&messages, "conv_id IN ?", conversationIDs); err != nil {
			return nil, err
		}
	}

	verified := false
	if len(security) > 0 {
		verified = security[0].EmailVerified
	}
	return gin.H{
		"format":        "youmaoni-server-account-data",
		"formatVersion": 1,
		"exportedAt":    time.Now().UTC().Format(time.RFC3339Nano),
		"account": gin.H{
			"accountId": strconv.FormatUint(uint64(user.ID), 10),
			"email":     user.Username, "nickname": user.NickName, "avatar": user.HeaderImg,
			"emailVerified": verified, "createdAt": user.CreatedAt,
		},
		"data": gin.H{
			"accountSecurity": security, "devices": devices, "shares": shares, "publicCats": publicCats,
			"posts": posts, "comments": comments, "commentLikes": commentLikes,
			"postLikes": likes, "bookmarks": bookmarks, "catFollows": catFollows,
			"userFollows": userFollows, "reports": reports, "moderationAppeals": appeals,
			"notifications": notifications, "adoptionApplications": applications,
			"adoptionEvents": adoptionEvents, "conversations": conversations, "messages": messages,
			"media": media, "encryptedVault": vault, "vaultMeta": vaultMeta,
			"safetyRelations": safety, "catTimeline": timeline, "missingCases": missingCases,
			"missingSightings": missingSightings, "rescueTasks": rescueTasks,
			"identityBadges": identities, "pushDevices": pushDevices,
		},
	}, nil
}

func (a *CatAuthApi) ExportAccount(c *gin.Context) {
	var data gin.H
	err := global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		var err error
		data, err = accountExportData(tx, currentUserId(c))
		return err
	})
	if err != nil {
		failDB(c, "导出服务端个人数据失败")
		return
	}
	ok(c, data)
}
