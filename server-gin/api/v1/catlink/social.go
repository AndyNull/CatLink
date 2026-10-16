package catlink

import (
	"strconv"
	"strings"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	"github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

func (a *CatAuthApi) UserPublic(c *gin.Context) {
	id, _ := strconv.Atoi(c.Param("accountId"))
	var user system.SysUser
	if global.GVA_DB.First(&user, id).Error != nil {
		fail(c, "用户不存在")
		return
	}
	var catRows []catlink.CatPublicCat
	global.GVA_DB.Where("owner_id=? AND state=?", id, "published").Order("updated_at desc").Limit(50).Find(&catRows)
	var postRows []catlink.CatPost
	global.GVA_DB.Where("author_id=? AND locked=?", id, false).Order("created_at desc").Limit(50).Find(&postRows)
	cats := make([]gin.H, 0, len(catRows))
	for _, row := range catRows {
		cats = append(cats, publicCatView(row))
	}
	posts := make([]gin.H, 0, len(postRows))
	for _, row := range postRows {
		posts = append(posts, postView(row))
	}
	ok(c, gin.H{
		"accountId": strconv.Itoa(id), "nickname": user.NickName,
		"publicCats": len(cats), "posts": len(posts),
		"cats": cats, "postItems": posts,
	})
}

func (a *CatCommunityApi) ApplyAdoption(c *gin.Context) {
	uid := currentUserId(c)
	var body struct {
		PublicCatID    string `json:"publicCatId"`
		Message        string `json:"message"`
		ContactSummary string `json:"contactSummary"`
	}
	if c.ShouldBindJSON(&body) != nil {
		fail(c, "领养申请参数无效")
		return
	}
	body.PublicCatID = strings.TrimSpace(body.PublicCatID)
	body.Message = strings.TrimSpace(body.Message)
	body.ContactSummary = strings.TrimSpace(body.ContactSummary)
	if body.PublicCatID == "" || len([]rune(body.Message)) > 2000 || body.ContactSummary == "" || len([]rune(body.ContactSummary)) > 200 {
		fail(c, "请填写有效的联系方式，申请说明不能超过 2000 字")
		return
	}
	cat, err := publicCatByRef(body.PublicCatID, 0, true)
	if err != nil {
		fail(c, "公开猫档案不存在")
		return
	}
	var row catlink.CatApply
	var notification catlink.CatNotification
	err = global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		var locked catlink.CatPublicCat
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).First(&locked, cat.ID).Error; err != nil {
			return adoptionError("公开猫档案不存在")
		}
		if locked.OwnerID == uid {
			return adoptionError("不能申请自己发布的猫咪")
		}
		if locked.State != "published" || locked.Status != catStatusAdoptable {
			return adoptionError("该猫咪当前不可申请领养")
		}
		var count int64
		if err := tx.Model(&catlink.CatApply{}).Where("cat_id = ? AND applicant_id = ? AND status IN ?", locked.ID, uid, activeAdoptionStatuses).Count(&count).Error; err != nil {
			return err
		}
		if count > 0 {
			return adoptionError("已有进行中的领养申请")
		}
		now := time.Now()
		row = catlink.CatApply{CatID: locked.ID, ApplicantID: uid, OwnerID: locked.OwnerID, Message: body.Message, ContactSummary: body.ContactSummary, Status: "pending", CreatedAt: now, UpdatedAt: now}
		if err := tx.Create(&row).Error; err != nil {
			return err
		}
		if err := tx.Create(&catlink.CatAdoptionEvent{ApplyID: row.ID, ActorID: uid, Status: "pending", Note: "提交领养申请", CreatedAt: now}).Error; err != nil {
			return err
		}
		var queueErr error
		notification, queueErr = queueCatNotification(tx, catlink.CatNotification{ToUser: locked.OwnerID, Type: "adoption", Title: "收到新的领养申请", Body: "有人申请领养 " + locked.Nickname, Link: "/adoption/" + strconv.Itoa(int(row.ID)), CreatedAt: now})
		return queueErr
	})
	if err != nil {
		failAdoption(c, err, "提交领养申请失败")
		return
	}
	publishCatNotification(notification)
	ok(c, gin.H{"id": row.ID})
}

func (a *CatCommunityApi) SubmitCommunityReport(c *gin.Context) { a.SubmitReportCompat(c) }
func (a *CatCommunityApi) SubmitReportCompat(c *gin.Context)    { (&CatReportApi{}).Submit(c) }

func (a *CatCommunityApi) FollowUser(c *gin.Context) {
	uid := currentUserId(c)
	target64, _ := strconv.ParseUint(c.Param("accountId"), 10, 64)
	target := uint(target64)
	if target == 0 || target == uid {
		fail(c, "无效用户")
		return
	}
	var row catlink.CatUserFollow
	global.GVA_DB.Where("user_id=? AND target_id=?", uid, target).First(&row)
	if row.ID > 0 {
		global.GVA_DB.Delete(&row)
		ok(c, gin.H{"followed": false})
		return
	}
	global.GVA_DB.Create(&catlink.CatUserFollow{UserID: uid, TargetID: target, CreatedAt: time.Now()})
	ok(c, gin.H{"followed": true})
}

func (a *CatCommunityApi) StartConversation(c *gin.Context) {
	uid := currentUserId(c)
	var body struct {
		UserID string `json:"userId"`
	}
	_ = c.ShouldBindJSON(&body)
	other64, _ := strconv.ParseUint(body.UserID, 10, 64)
	other := uint(other64)
	if other == 0 || other == uid {
		fail(c, "无效的聊天对象")
		return
	}
	var target system.SysUser
	if global.GVA_DB.First(&target, other).Error != nil {
		fail(c, "用户不存在")
		return
	}
	if conversationSuppressed(uid, other) {
		fail(c, "该用户已被屏蔽")
		return
	}
	aID, bID := uid, other
	if aID > bID {
		aID, bID = bID, aID
	}
	var row catlink.CatConversation
	global.GVA_DB.Where("user_a=? AND user_b=?", aID, bID).FirstOrCreate(&row, catlink.CatConversation{UserA: aID, UserB: bID, UpdatedAt: time.Now()})
	ok(c, gin.H{"id": strconv.FormatUint(uint64(row.ID), 10)})
}

func conversationFor(uid uint, id int) (catlink.CatConversation, bool) {
	var row catlink.CatConversation
	if global.GVA_DB.Where("id=? AND (user_a=? OR user_b=?)", id, uid, uid).First(&row).Error != nil {
		return row, false
	}
	return row, true
}

func (a *CatCommunityApi) ConversationList(c *gin.Context) {
	uid := currentUserId(c)
	var rows []catlink.CatConversation
	global.GVA_DB.Where("user_a=? OR user_b=?", uid, uid).Order("updated_at desc").Find(&rows)
	items := make([]gin.H, 0, len(rows))
	for _, row := range rows {
		other := row.UserA
		if other == uid {
			other = row.UserB
		}
		var user system.SysUser
		global.GVA_DB.Select("id,nick_name").First(&user, other)
		items = append(items, gin.H{"id": strconv.FormatUint(uint64(row.ID), 10), "otherId": strconv.FormatUint(uint64(other), 10), "otherName": user.NickName, "lastMessage": row.LastMessage, "lastAt": row.UpdatedAt.UnixMilli()})
	}
	ok(c, gin.H{"items": items})
}

func (a *CatCommunityApi) ConversationMessages(c *gin.Context) {
	uid := currentUserId(c)
	id, _ := strconv.Atoi(c.Param("conversationId"))
	if _, yes := conversationFor(uid, id); !yes {
		fail(c, "会话不存在")
		return
	}
	var rows []catlink.CatMessage
	global.GVA_DB.Where("conv_id=?", id).Order("created_at asc").Limit(500).Find(&rows)
	items := make([]gin.H, 0, len(rows))
	for _, m := range rows {
		items = append(items, gin.H{"id": strconv.FormatUint(uint64(m.ID), 10), "conversationId": strconv.Itoa(id), "senderId": strconv.FormatUint(uint64(m.SenderID), 10), "body": m.Body, "read": m.ReadFlag, "createdAt": m.CreatedAt.UnixMilli()})
	}
	global.GVA_DB.Model(&catlink.CatMessage{}).Where("conv_id=? AND sender_id<>?", id, uid).Update("read_flag", true)
	ok(c, gin.H{"items": items})
}

func (a *CatCommunityApi) SendMessage(c *gin.Context) {
	uid := currentUserId(c)
	id, _ := strconv.Atoi(c.Param("conversationId"))
	conv, yes := conversationFor(uid, id)
	if !yes {
		fail(c, "会话不存在")
		return
	}
	var body struct {
		Body string `json:"body"`
	}
	_ = c.ShouldBindJSON(&body)
	text := strings.TrimSpace(body.Body)
	if text == "" || len([]rune(text)) > 2000 {
		fail(c, "消息需为1-2000个字符")
		return
	}
	other := conv.UserA
	if other == uid {
		other = conv.UserB
	}
	if conversationSuppressed(uid, other) {
		fail(c, "该用户已被屏蔽")
		return
	}
	now := time.Now()
	m := catlink.CatMessage{ConvID: uint(id), SenderID: uid, Body: text, CreatedAt: now}
	var notification catlink.CatNotification
	err := global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		var recentMinute, recentDay int64
		if err := tx.Model(&catlink.CatMessage{}).Where("sender_id = ? AND created_at > ?", uid, now.Add(-time.Minute)).Count(&recentMinute).Error; err != nil {
			return err
		}
		if err := tx.Model(&catlink.CatMessage{}).Where("sender_id = ? AND created_at > ?", uid, now.Add(-24*time.Hour)).Count(&recentDay).Error; err != nil {
			return err
		}
		if recentMinute >= 30 || recentDay >= 1000 {
			return clientMediaError("消息发送过于频繁，请稍后再试")
		}
		if err := tx.Create(&m).Error; err != nil {
			return err
		}
		if err := tx.Model(&conv).Updates(map[string]any{"last_message": text, "updated_at": now}).Error; err != nil {
			return err
		}
		var err error
		notification, err = queueCatNotification(tx, catlink.CatNotification{ToUser: other, Type: "private", Title: "收到新消息", Body: text, Link: "/conversation/" + strconv.Itoa(id)})
		return err
	})
	if err != nil {
		if clientErr, ok := err.(clientMediaError); ok {
			fail(c, clientErr.Error())
		} else {
			failDB(c, "消息发送失败，请稍后重试")
		}
		return
	}
	publishCatNotification(notification)
	ok(c, gin.H{"id": strconv.FormatUint(uint64(m.ID), 10)})
}
