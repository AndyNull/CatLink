package catlink

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"math"
	"net/http"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	"github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	"github.com/flipped-aurora/gin-vue-admin/server/utils/sse"
	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

func roundedCoordinate(value float64) float64 { return math.Round(value*100) / 100 }

func validSafetyKind(kind string) bool { return kind == "block" || kind == "mute" }

func suppressedTargets(uid uint, targetIDs []uint) map[uint]struct{} {
	result := make(map[uint]struct{})
	if uid == 0 || len(targetIDs) == 0 {
		return result
	}
	var rows []catlink.CatSafetyRelation
	global.GVA_DB.Where("user_id = ? AND target_id IN ? AND kind IN ?", uid, targetIDs, []string{"block", "mute"}).Find(&rows)
	for _, row := range rows {
		result[row.TargetID] = struct{}{}
	}
	return result
}

func conversationSuppressed(uid, other uint) bool {
	var count int64
	global.GVA_DB.Model(&catlink.CatSafetyRelation{}).Where(
		"(user_id = ? AND target_id = ? AND kind IN ?) OR (user_id = ? AND target_id = ? AND kind = ?)",
		uid, other, []string{"block", "mute"}, other, uid, "block",
	).Count(&count)
	return count > 0
}

var adoptionStatusLabels = map[string]string{
	"pending": "待审核", "contact": "沟通中", "home_visit": "家访中",
	"approved": "已通过", "rejected": "已拒绝", "withdrawn": "已撤回",
}

var activeAdoptionStatuses = []string{"pending", "contact", "home_visit"}

type adoptionError string

func (e adoptionError) Error() string { return string(e) }

func adoptionTransitionAllowed(current, next string, actorID uint, application catlink.CatApply, administrative bool) bool {
	if _, valid := adoptionStatusLabels[next]; !valid {
		return false
	}
	active := current == "pending" || current == "contact" || current == "home_visit"
	if current == next {
		return active && (administrative || actorID == application.ApplicantID || actorID == application.OwnerID)
	}
	if !active {
		return false
	}
	if next == "withdrawn" {
		return !administrative && actorID == application.ApplicantID
	}
	if !administrative && actorID != application.OwnerID {
		return false
	}
	allowed := map[string]map[string]bool{
		"pending":    {"contact": true, "home_visit": true, "approved": true, "rejected": true},
		"contact":    {"home_visit": true, "approved": true, "rejected": true},
		"home_visit": {"approved": true, "rejected": true},
	}
	return allowed[current][next]
}

func queueCatNotification(tx *gorm.DB, row catlink.CatNotification) (catlink.CatNotification, error) {
	if row.CreatedAt.IsZero() {
		row.CreatedAt = time.Now()
	}
	if err := tx.Create(&row).Error; err != nil {
		return row, err
	}
	err := tx.Create(&catlink.CatNotificationOutbox{NotificationID: row.ID, Provider: "sse", Status: "pending", CreatedAt: row.CreatedAt, UpdatedAt: row.CreatedAt}).Error
	return row, err
}

func claimNotificationOutbox(id uint, now time.Time) bool {
	result := global.GVA_DB.Model(&catlink.CatNotificationOutbox{}).
		Where("id = ? AND ((status = ? AND (next_attempt_at IS NULL OR next_attempt_at <= ?)) OR (status = ? AND updated_at < ?))",
			id, "pending", now, "processing", now.Add(-5*time.Minute)).
		Updates(map[string]any{"status": "processing", "updated_at": now})
	return result.Error == nil && result.RowsAffected == 1
}

func markNotificationOutboxSent(id uint, attempts int) {
	global.GVA_DB.Model(&catlink.CatNotificationOutbox{}).
		Where("id = ? AND status = ?", id, "processing").
		Updates(map[string]any{"status": "sent", "attempts": attempts + 1, "next_attempt_at": nil, "last_error": "", "updated_at": time.Now()})
}

func publishCatNotification(row catlink.CatNotification) {
	if !sse.Default().Online(row.ToUser) {
		return
	}
	var outbox catlink.CatNotificationOutbox
	if global.GVA_DB.Where("notification_id = ? AND provider = ?", row.ID, "sse").First(&outbox).Error != nil || !claimNotificationOutbox(outbox.ID, time.Now()) {
		return
	}
	payload, _ := json.Marshal(gin.H{"id": row.ID, "kind": row.Type, "title": row.Title})
	sse.Default().PublishToUsers([]uint{row.ToUser}, sse.Event{Name: "catlink_notification", Data: string(payload)})
	markNotificationOutboxSent(outbox.ID, outbox.Attempts)
}

func closeActiveAdoptionsForCat(tx *gorm.DB, catRow catlink.CatPublicCat, actorID uint, nextStatus string, now time.Time) ([]catlink.CatNotification, error) {
	if nextStatus == catStatusAdoptable {
		return nil, nil
	}
	var rows []catlink.CatApply
	if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Where("cat_id = ? AND status IN ?", catRow.ID, activeAdoptionStatuses).Find(&rows).Error; err != nil {
		return nil, err
	}
	notifications := make([]catlink.CatNotification, 0, len(rows))
	for _, row := range rows {
		if err := tx.Model(&row).Updates(map[string]any{"status": "rejected", "updated_at": now, "decided_at": now}).Error; err != nil {
			return nil, err
		}
		note := "猫咪状态已变更为" + catStatusLabels[nextStatus]
		if err := tx.Create(&catlink.CatAdoptionEvent{ApplyID: row.ID, ActorID: actorID, Status: "rejected", Note: note, CreatedAt: now}).Error; err != nil {
			return nil, err
		}
		queued, err := queueCatNotification(tx, catlink.CatNotification{
			ToUser: row.ApplicantID, Type: "adoption", Title: "领养申请已关闭",
			Body: catRow.Nickname + " 已变更为" + catStatusLabels[nextStatus], Link: "/adoption/" + strconv.Itoa(int(row.ID)), CreatedAt: now,
		})
		if err != nil {
			return nil, err
		}
		notifications = append(notifications, queued)
	}
	return notifications, nil
}

func createCatNotification(row catlink.CatNotification) error {
	var queued catlink.CatNotification
	err := global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		var queueErr error
		queued, queueErr = queueCatNotification(tx, row)
		return queueErr
	})
	if err == nil {
		publishCatNotification(queued)
	}
	return err
}

type adoptionTransitionResult struct {
	Application   catlink.CatApply
	Notifications []catlink.CatNotification
}

func adoptionTransitionNotification(application catlink.CatApply, actorID uint, administrative bool, previousStatus, status, catName string) catlink.CatNotification {
	target := application.ApplicantID
	if !administrative && actorID == application.ApplicantID {
		target = application.OwnerID
	}
	title := "领养申请状态更新"
	if status == previousStatus {
		title = "领养申请有新补充"
	}
	return catlink.CatNotification{
		ToUser: target, Type: "adoption", Title: title,
		Body: catName + " 的领养申请已更新为" + adoptionStatusLabels[status],
		Link: "/adoption/" + strconv.Itoa(int(application.ID)), CreatedAt: time.Now(),
	}
}

func approveAdoption(tx *gorm.DB, application catlink.CatApply, actorID uint, now time.Time) ([]catlink.CatNotification, error) {
	var catRow catlink.CatPublicCat
	if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).First(&catRow, application.CatID).Error; err != nil {
		return nil, adoptionError("猫咪档案不存在")
	}
	if catRow.OwnerID != application.OwnerID || catRow.Status != catStatusAdoptable {
		return nil, adoptionError("猫咪当前不是待领养状态")
	}
	statusDay := calendarEpochDay(now)
	if err := tx.Model(&catRow).Updates(map[string]any{
		"status": catStatusAdopted, "cat_status": catStatusLabels[catStatusAdopted], "archive_reason": "",
		"status_reason": "领养申请已通过", "status_date": statusDay, "updated_at": now,
	}).Error; err != nil {
		return nil, err
	}
	if err := tx.Create(&catlink.CatTimelineEvent{CatID: catRow.ID, OwnerID: catRow.OwnerID, Kind: "adoption", Body: "领养申请已通过，猫咪状态更新为已领养", Public: true, CreatedAt: now}).Error; err != nil {
		return nil, err
	}
	return rejectCompetingAdoptions(tx, application, actorID, catRow.Nickname, now)
}

func rejectCompetingAdoptions(tx *gorm.DB, approved catlink.CatApply, actorID uint, catName string, now time.Time) ([]catlink.CatNotification, error) {
	var rows []catlink.CatApply
	if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Where("cat_id = ? AND id <> ? AND status IN ?", approved.CatID, approved.ID, activeAdoptionStatuses).Find(&rows).Error; err != nil {
		return nil, err
	}
	notifications := make([]catlink.CatNotification, 0, len(rows))
	for _, row := range rows {
		if err := tx.Model(&row).Updates(map[string]any{"status": "rejected", "updated_at": now, "decided_at": now}).Error; err != nil {
			return nil, err
		}
		if err := tx.Create(&catlink.CatAdoptionEvent{ApplyID: row.ID, ActorID: actorID, Status: "rejected", Note: "该猫咪已有申请通过", CreatedAt: now}).Error; err != nil {
			return nil, err
		}
		queued, err := queueCatNotification(tx, catlink.CatNotification{ToUser: row.ApplicantID, Type: "adoption", Title: "领养申请已关闭", Body: catName + " 已通过其他领养申请", Link: "/adoption/" + strconv.Itoa(int(row.ID)), CreatedAt: now})
		if err != nil {
			return nil, err
		}
		notifications = append(notifications, queued)
	}
	return notifications, nil
}

func transitionAdoption(tx *gorm.DB, id, actorID uint, administrative bool, status, note, mediaURLs, mediaIDs string) (adoptionTransitionResult, error) {
	var application catlink.CatApply
	if err := tx.Select("id", "cat_id").First(&application, id).Error; err != nil {
		return adoptionTransitionResult{}, adoptionError("领养申请不存在")
	}
	var catLock catlink.CatPublicCat
	if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Select("id").First(&catLock, application.CatID).Error; err != nil {
		return adoptionTransitionResult{}, adoptionError("猫咪档案不存在")
	}
	if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).First(&application, id).Error; err != nil {
		return adoptionTransitionResult{}, adoptionError("领养申请不存在")
	}
	if !adoptionTransitionAllowed(application.Status, status, actorID, application, administrative) {
		return adoptionTransitionResult{}, adoptionError("当前状态不允许执行该操作")
	}
	if status == application.Status && strings.TrimSpace(note) == "" && mediaURLs == "" {
		return adoptionTransitionResult{}, adoptionError("补充说明或材料不能为空")
	}
	previousStatus := application.Status
	now := time.Now()
	updates := map[string]any{"status": status, "updated_at": now}
	if status == "approved" || status == "rejected" || status == "withdrawn" {
		updates["decided_at"] = now
	}
	if err := tx.Model(&application).Updates(updates).Error; err != nil {
		return adoptionTransitionResult{}, err
	}
	if err := tx.Create(&catlink.CatAdoptionEvent{ApplyID: application.ID, ActorID: actorID, Status: status, Note: strings.TrimSpace(note), MediaURLs: mediaURLs, MediaIDs: mediaIDs, CreatedAt: now}).Error; err != nil {
		return adoptionTransitionResult{}, err
	}
	var catRow catlink.CatPublicCat
	if err := tx.Select("id", "nickname").First(&catRow, application.CatID).Error; err != nil {
		return adoptionTransitionResult{}, adoptionError("猫咪档案不存在")
	}
	notifications := []catlink.CatNotification{}
	if status == "approved" {
		competing, err := approveAdoption(tx, application, actorID, now)
		if err != nil {
			return adoptionTransitionResult{}, err
		}
		notifications = append(notifications, competing...)
	}
	queued, err := queueCatNotification(tx, adoptionTransitionNotification(application, actorID, administrative, previousStatus, status, catRow.Nickname))
	if err != nil {
		return adoptionTransitionResult{}, err
	}
	notifications = append(notifications, queued)
	application.Status, application.UpdatedAt = status, now
	return adoptionTransitionResult{Application: application, Notifications: notifications}, nil
}

func failAdoption(c *gin.Context, err error, fallback string) {
	var business adoptionError
	if errors.As(err, &business) {
		fail(c, business.Error())
		return
	}
	failDB(c, fallback)
}

func adoptionApplicationViews(rows []catlink.CatApply, viewerID uint, administrative bool) []gin.H {
	catIDs, userIDs := make([]uint, 0, len(rows)), make([]uint, 0, len(rows)*2)
	for _, row := range rows {
		catIDs = append(catIDs, row.CatID)
		userIDs = append(userIDs, row.ApplicantID, row.OwnerID)
	}
	var cats []catlink.CatPublicCat
	var users []system.SysUser
	global.GVA_DB.Select("id", "nickname").Where("id IN ?", catIDs).Find(&cats)
	global.GVA_DB.Select("id", "nick_name").Where("id IN ?", userIDs).Find(&users)
	catNames, userNames := map[uint]string{}, map[uint]string{}
	for _, row := range cats {
		catNames[row.ID] = row.Nickname
	}
	for _, row := range users {
		userNames[row.ID] = row.NickName
	}
	timelines := make(map[uint][]gin.H, len(rows))
	if len(rows) > 0 {
		applicationIDs := make([]uint, 0, len(rows))
		for _, row := range rows {
			applicationIDs = append(applicationIDs, row.ID)
		}
		var events []catlink.CatAdoptionEvent
		global.GVA_DB.Where("apply_id IN ?", applicationIDs).Order("created_at asc, id asc").Find(&events)
		for index := range events {
			events[index].MediaURLs = strings.Join(mediaURLsByIDs(events[index].MediaIDs, postMediaURLs(events[index].MediaURLs)), ",")
		}
		eventViews := adoptionEventViews(events)
		for index, event := range events {
			timelines[event.ApplyID] = append(timelines[event.ApplyID], eventViews[index])
		}
	}
	views := make([]gin.H, 0, len(rows))
	for _, row := range rows {
		role := "applicant"
		if administrative {
			role = "admin"
		} else if viewerID == row.OwnerID {
			role = "owner"
		}
		var decidedAt any
		if row.DecidedAt != nil {
			decidedAt = row.DecidedAt.UnixMilli()
		}
		timeline := timelines[row.ID]
		if timeline == nil {
			timeline = []gin.H{}
		}
		views = append(views, gin.H{
			"id": strconv.FormatUint(uint64(row.ID), 10), "catId": strconv.FormatUint(uint64(row.CatID), 10), "catNickname": catNames[row.CatID],
			"applicantId": strconv.FormatUint(uint64(row.ApplicantID), 10), "applicantName": userNames[row.ApplicantID],
			"ownerId": strconv.FormatUint(uint64(row.OwnerID), 10), "ownerName": userNames[row.OwnerID], "role": role,
			"message": row.Message, "contactSummary": row.ContactSummary, "status": row.Status,
			"createdAt": row.CreatedAt.UnixMilli(), "updatedAt": row.UpdatedAt.UnixMilli(), "decidedAt": decidedAt, "timeline": timeline,
		})
	}
	return views
}

func adoptionEventViews(rows []catlink.CatAdoptionEvent) []gin.H {
	actorIDs := make([]uint, 0, len(rows))
	for _, row := range rows {
		if row.ActorID > 0 {
			actorIDs = append(actorIDs, row.ActorID)
		}
	}
	var users []system.SysUser
	global.GVA_DB.Select("id", "nick_name").Where("id IN ?", actorIDs).Find(&users)
	names := map[uint]string{}
	for _, user := range users {
		names[user.ID] = user.NickName
	}
	views := make([]gin.H, 0, len(rows))
	for _, row := range rows {
		views = append(views, gin.H{
			"id": strconv.FormatUint(uint64(row.ID), 10), "applyId": strconv.FormatUint(uint64(row.ApplyID), 10),
			"actorId": strconv.FormatUint(uint64(row.ActorID), 10), "actorName": names[row.ActorID],
			"status": row.Status, "note": row.Note, "mediaUrls": row.MediaURLs, "createdAt": row.CreatedAt.UnixMilli(),
		})
	}
	return views
}

func migrateAdoptionApplications() error {
	return global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		if err := tx.Model(&catlink.CatApply{}).Where("status = ?", "completed").Updates(map[string]any{"status": "approved"}).Error; err != nil {
			return err
		}
		var rows []catlink.CatApply
		if err := tx.Find(&rows).Error; err != nil {
			return err
		}
		for _, row := range rows {
			updates := map[string]any{}
			if row.UpdatedAt.IsZero() {
				updates["updated_at"] = row.CreatedAt
			}
			if row.DecidedAt == nil && (row.Status == "approved" || row.Status == "rejected" || row.Status == "withdrawn") {
				updates["decided_at"] = row.CreatedAt
			}
			if len(updates) > 0 {
				if err := tx.Model(&row).Updates(updates).Error; err != nil {
					return err
				}
			}
			var count int64
			if err := tx.Model(&catlink.CatAdoptionEvent{}).Where("apply_id = ?", row.ID).Count(&count).Error; err != nil {
				return err
			}
			if count == 0 {
				if err := tx.Create(&catlink.CatAdoptionEvent{ApplyID: row.ID, ActorID: row.ApplicantID, Status: row.Status, Note: "历史申请状态迁移", CreatedAt: row.CreatedAt}).Error; err != nil {
					return err
				}
			}
			if row.Status == "approved" {
				statusDay := calendarEpochDay(row.CreatedAt)
				if err := tx.Model(&catlink.CatPublicCat{}).Where("id = ? AND status = ?", row.CatID, catStatusAdoptable).Updates(map[string]any{"status": catStatusAdopted, "cat_status": catStatusLabels[catStatusAdopted], "status_reason": "历史领养申请已通过", "status_date": statusDay}).Error; err != nil {
					return err
				}
			}
		}
		return nil
	})
}

func (a *CatAuthApi) NotificationStream(c *gin.Context) {
	sse.Default().Stream(c, currentUserId(c), 30*time.Second)
}

// ToggleSafety toggles a block/mute relation. Block takes precedence in feed/message filtering.
func (a *CatCommunityApi) ToggleSafety(c *gin.Context) {
	uid := currentUserId(c)
	target, err := strconv.ParseUint(c.Param("accountId"), 10, 64)
	if err != nil || target == 0 || uint(target) == uid || !validSafetyKind(c.Param("kind")) {
		fail(c, "无效的屏蔽对象")
		return
	}
	var row catlink.CatSafetyRelation
	query := global.GVA_DB.Where("user_id = ? AND target_id = ? AND kind = ?", uid, uint(target), c.Param("kind"))
	if query.First(&row).Error == nil {
		global.GVA_DB.Delete(&row)
		ok(c, gin.H{"active": false, "kind": c.Param("kind")})
		return
	}
	if global.GVA_DB.Create(&catlink.CatSafetyRelation{UserID: uid, TargetID: uint(target), Kind: c.Param("kind"), CreatedAt: time.Now()}).Error != nil {
		fail(c, "设置屏蔽关系失败")
		return
	}
	ok(c, gin.H{"active": true, "kind": c.Param("kind")})
}

func (a *CatCommunityApi) SafetyList(c *gin.Context) {
	uid := currentUserId(c)
	var rows []catlink.CatSafetyRelation
	global.GVA_DB.Where("user_id = ?", uid).Order("created_at desc").Find(&rows)
	ok(c, gin.H{"items": rows})
}

func (a *CatPublicCatApi) Timeline(c *gin.Context) {
	catID, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil || catID == 0 {
		fail(c, "猫咪档案不存在")
		return
	}
	var rows []catlink.CatTimelineEvent
	q := global.GVA_DB.Where("cat_id = ? AND public = ?", uint(catID), true)
	if before := strings.TrimSpace(c.Query("before")); before != "" {
		if ts, parseErr := strconv.ParseInt(before, 10, 64); parseErr == nil {
			q = q.Where("created_at < ?", time.UnixMilli(ts))
		}
	}
	q.Order("created_at desc, id desc").Limit(40).Find(&rows)
	for index := range rows {
		rows[index].MediaURLs = strings.Join(mediaURLsByIDs(rows[index].MediaIDs, postMediaURLs(rows[index].MediaURLs)), ",")
	}
	ok(c, gin.H{"items": rows})
}

func (a *CatPublicCatApi) AddTimeline(c *gin.Context) {
	uid := currentUserId(c)
	catID, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil || catID == 0 {
		fail(c, "猫咪档案不存在")
		return
	}
	var catRow catlink.CatPublicCat
	if global.GVA_DB.Where("id = ? AND owner_id = ?", uint(catID), uid).First(&catRow).Error != nil {
		fail(c, "只能维护自己发布的猫咪动态")
		return
	}
	var body struct {
		Kind      string `json:"kind"`
		Body      string `json:"body"`
		MediaURLs string `json:"mediaUrls"`
		Public    *bool  `json:"public"`
	}
	if c.ShouldBindJSON(&body) != nil || strings.TrimSpace(body.Body) == "" || len([]rune(body.Body)) > 2000 {
		fail(c, "动态内容需为 1-2000 个字符")
		return
	}
	public := true
	if body.Public != nil {
		public = *body.Public
	}
	mediaURLs := postMediaURLs(body.MediaURLs)
	mediaIDs, err := mediaIDsForURLs(global.GVA_DB, uid, mediaURLs)
	if err != nil || len(mediaIDs) != len(mediaURLs) {
		fail(c, "动态照片不存在或不属于当前账号")
		return
	}
	row := catlink.CatTimelineEvent{CatID: uint(catID), OwnerID: uid, Kind: strings.TrimSpace(body.Kind), Body: strings.TrimSpace(body.Body), MediaURLs: strings.Join(mediaURLs, ","), MediaIDs: mustJSON(mediaIDs), Public: public, CreatedAt: time.Now()}
	if global.GVA_DB.Create(&row).Error != nil {
		fail(c, "发布动态失败")
		return
	}
	ok(c, row)
}

func (a *CatCommunityApi) AdoptionTimeline(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("applicationId"), 10, 64)
	if err != nil || id == 0 {
		fail(c, "领养申请不存在")
		return
	}
	uid := currentUserId(c)
	var application catlink.CatApply
	if global.GVA_DB.Where("id = ? AND (applicant_id = ? OR owner_id = ?)", uint(id), uid, uid).First(&application).Error != nil {
		fail(c, "无权查看该申请")
		return
	}
	var rows []catlink.CatAdoptionEvent
	global.GVA_DB.Where("apply_id = ?", application.ID).Order("created_at asc, id asc").Find(&rows)
	for index := range rows {
		rows[index].MediaURLs = strings.Join(mediaURLsByIDs(rows[index].MediaIDs, postMediaURLs(rows[index].MediaURLs)), ",")
	}
	ok(c, gin.H{"items": adoptionEventViews(rows), "status": application.Status})
}

func (a *CatCommunityApi) AdoptionApplications(c *gin.Context) {
	uid := currentUserId(c)
	var rows []catlink.CatApply
	global.GVA_DB.Where("applicant_id = ? OR owner_id = ?", uid, uid).Order("created_at desc").Limit(100).Find(&rows)
	ok(c, gin.H{"items": adoptionApplicationViews(rows, uid, false)})
}

func (a *CatCommunityApi) AddAdoptionEvent(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("applicationId"), 10, 64)
	if err != nil || id == 0 {
		fail(c, "领养申请不存在")
		return
	}
	uid := currentUserId(c)
	var body struct {
		Status    string `json:"status"`
		Note      string `json:"note"`
		MediaURLs string `json:"mediaUrls"`
	}
	if c.ShouldBindJSON(&body) != nil || strings.TrimSpace(body.Status) == "" || len([]rune(body.Note)) > 2000 {
		fail(c, "领养状态或说明无效")
		return
	}
	if _, allowed := adoptionStatusLabels[body.Status]; !allowed {
		fail(c, "领养状态无效")
		return
	}
	mediaURLs := postMediaURLs(body.MediaURLs)
	mediaIDs, err := mediaIDsForURLs(global.GVA_DB, uid, mediaURLs)
	if err != nil {
		fail(c, "补充材料无效")
		return
	}
	if len(mediaIDs) > 0 {
		var privateMedia int64
		if global.GVA_DB.Model(&catlink.CatMedia{}).Where("id IN ? AND category = ?", mediaIDs, "contract").Count(&privateMedia).Error != nil {
			failDB(c, "补充材料校验失败，请稍后重试")
			return
		}
		if privateMedia > 0 {
			fail(c, "私有合同不能作为共享材料，请填写可访问的 HTTPS 地址")
			return
		}
	}
	if len(mediaIDs) != len(mediaURLs) {
		for _, value := range mediaURLs {
			if safeHTTPSURL(value) == "" {
				fail(c, "补充材料必须是本人上传媒体或 HTTPS 地址")
				return
			}
		}
	}
	var result adoptionTransitionResult
	err = global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		var transitionErr error
		result, transitionErr = transitionAdoption(tx, uint(id), uid, false, body.Status, body.Note, strings.Join(mediaURLs, ","), mustJSON(mediaIDs))
		return transitionErr
	})
	if err != nil {
		failAdoption(c, err, "领养状态更新失败")
		return
	}
	for _, notification := range result.Notifications {
		publishCatNotification(notification)
	}
	ok(c, gin.H{"status": result.Application.Status})
}

func (a *CatCommunityApi) CreateMissingCase(c *gin.Context) {
	uid := currentUserId(c)
	var body struct {
		CatID       string  `json:"catId"`
		Latitude    float64 `json:"latitude"`
		Longitude   float64 `json:"longitude"`
		RadiusKM    float64 `json:"radiusKm"`
		Description string  `json:"description"`
	}
	if c.ShouldBindJSON(&body) != nil || body.Latitude < -90 || body.Latitude > 90 || body.Longitude < -180 || body.Longitude > 180 || len([]rune(body.Description)) > 2000 {
		fail(c, "失踪事件参数无效")
		return
	}
	catID, err := strconv.ParseUint(body.CatID, 10, 64)
	if err != nil || catID == 0 {
		fail(c, "猫咪档案不存在")
		return
	}
	var catRow catlink.CatPublicCat
	if global.GVA_DB.Where("id = ? AND owner_id = ?", uint(catID), uid).First(&catRow).Error != nil {
		fail(c, "只能为自己的猫咪发起失踪协作")
		return
	}
	radius := body.RadiusKM
	if radius < 1 || radius > 50 {
		radius = 5
	}
	row := catlink.CatMissingCase{CatID: uint(catID), OwnerID: uid, Latitude: roundedCoordinate(body.Latitude), Longitude: roundedCoordinate(body.Longitude), RadiusKM: radius, Description: strings.TrimSpace(body.Description), Status: "open", CreatedAt: time.Now()}
	if global.GVA_DB.Create(&row).Error != nil {
		fail(c, "创建失踪协作失败")
		return
	}
	ok(c, row)
}

func (a *CatCommunityApi) ListMissingCases(c *gin.Context) {
	var rows []catlink.CatMissingCase
	global.GVA_DB.Where("status = ?", "open").Order("created_at desc").Limit(100).Find(&rows)
	ok(c, gin.H{"items": rows})
}

func (a *CatCommunityApi) AddMissingSighting(c *gin.Context) {
	uid := currentUserId(c)
	caseID, err := strconv.ParseUint(c.Param("caseId"), 10, 64)
	if err != nil || caseID == 0 {
		fail(c, "失踪事件不存在")
		return
	}
	var body struct {
		Latitude  float64 `json:"latitude"`
		Longitude float64 `json:"longitude"`
		Note      string  `json:"note"`
	}
	if c.ShouldBindJSON(&body) != nil || body.Latitude < -90 || body.Latitude > 90 || body.Longitude < -180 || body.Longitude > 180 || len([]rune(body.Note)) > 1000 {
		fail(c, "目击线索参数无效")
		return
	}
	var target catlink.CatMissingCase
	if global.GVA_DB.Where("id = ? AND status = ?", uint(caseID), "open").First(&target).Error != nil {
		fail(c, "失踪事件不存在或已结束")
		return
	}
	row := catlink.CatMissingSighting{CaseID: target.ID, ReporterID: uid, Latitude: roundedCoordinate(body.Latitude), Longitude: roundedCoordinate(body.Longitude), Note: strings.TrimSpace(body.Note), CreatedAt: time.Now()}
	if global.GVA_DB.Create(&row).Error != nil {
		fail(c, "提交目击线索失败")
		return
	}
	ok(c, gin.H{"id": row.ID})
}

func (a *CatCommunityApi) CloseMissingCase(c *gin.Context) {
	uid := currentUserId(c)
	id, _ := strconv.ParseUint(c.Param("caseId"), 10, 64)
	now := time.Now()
	result := global.GVA_DB.Model(&catlink.CatMissingCase{}).Where("id = ? AND owner_id = ? AND status = ?", uint(id), uid, "open").Updates(map[string]any{"status": "closed", "closed_at": now})
	if result.Error != nil || result.RowsAffected == 0 {
		fail(c, "失踪事件不存在或无权关闭")
		return
	}
	ok(c, gin.H{"status": "closed"})
}

func (a *CatCommunityApi) CreateRescueTask(c *gin.Context) {
	uid := currentUserId(c)
	var body struct {
		CatID      string     `json:"catId"`
		AssigneeID uint       `json:"assigneeId"`
		Kind       string     `json:"kind"`
		Note       string     `json:"note"`
		DueAt      *time.Time `json:"dueAt"`
	}
	if c.ShouldBindJSON(&body) != nil || strings.TrimSpace(body.Kind) == "" || len([]rune(body.Note)) > 1000 {
		fail(c, "救助任务参数无效")
		return
	}
	catID, err := strconv.ParseUint(body.CatID, 10, 64)
	if err != nil || catID == 0 {
		fail(c, "猫咪档案不存在")
		return
	}
	var catRow catlink.CatPublicCat
	if global.GVA_DB.Where("id = ? AND owner_id = ?", uint(catID), uid).First(&catRow).Error != nil {
		fail(c, "只能为自己的猫咪创建任务")
		return
	}
	row := catlink.CatRescueTask{CatID: uint(catID), OwnerID: uid, AssigneeID: body.AssigneeID, Kind: strings.TrimSpace(body.Kind), Note: strings.TrimSpace(body.Note), DueAt: body.DueAt, Status: "open", CreatedAt: time.Now()}
	if global.GVA_DB.Create(&row).Error != nil {
		fail(c, "创建救助任务失败")
		return
	}
	ok(c, row)
}

func (a *CatCommunityApi) ListRescueTasks(c *gin.Context) {
	uid := currentUserId(c)
	var rows []catlink.CatRescueTask
	global.GVA_DB.Where("owner_id = ? OR assignee_id = ?", uid, uid).Order("created_at desc").Limit(200).Find(&rows)
	ok(c, gin.H{"items": rows})
}

func (a *CatCommunityApi) UpdateRescueTask(c *gin.Context) {
	uid := currentUserId(c)
	id, _ := strconv.ParseUint(c.Param("taskId"), 10, 64)
	var body struct {
		Status string `json:"status"`
	}
	if c.ShouldBindJSON(&body) != nil || (body.Status != "open" && body.Status != "done" && body.Status != "cancelled") {
		fail(c, "任务状态无效")
		return
	}
	var task catlink.CatRescueTask
	if global.GVA_DB.Where("id = ? AND (owner_id = ? OR assignee_id = ?)", uint(id), uid, uid).First(&task).Error != nil {
		fail(c, "任务不存在或无权操作")
		return
	}
	if task.Status != "open" && task.Status != body.Status {
		fail(c, "已结束任务不能再次变更")
		return
	}
	updates := map[string]any{"status": body.Status}
	if body.Status == "done" {
		now := time.Now()
		updates["completed_at"] = &now
	}
	result := global.GVA_DB.Model(&catlink.CatRescueTask{}).Where("id = ? AND status = ?", task.ID, task.Status).Updates(updates)
	if result.Error != nil || result.RowsAffected == 0 {
		fail(c, "任务不存在或无权操作")
		return
	}
	ok(c, gin.H{"status": body.Status})
}

func (a *CatAuthApi) RequestIdentityBadge(c *gin.Context) {
	uid := currentUserId(c)
	var body struct {
		Kind        string `json:"kind"`
		DisplayName string `json:"displayName"`
	}
	if c.ShouldBindJSON(&body) != nil || strings.TrimSpace(body.Kind) == "" || len([]rune(body.DisplayName)) > 80 {
		fail(c, "认证信息无效")
		return
	}
	var row catlink.CatIdentityBadge
	global.GVA_DB.Where("user_id = ?", uid).Assign(map[string]any{"kind": strings.TrimSpace(body.Kind), "display_name": strings.TrimSpace(body.DisplayName), "status": "pending", "updated_at": time.Now()}).FirstOrCreate(&row, catlink.CatIdentityBadge{UserID: uid, CreatedAt: time.Now()})
	ok(c, row)
}

func (a *CatAuthApi) RegisterPushDevice(c *gin.Context) {
	uid := currentUserId(c)
	var body struct {
		Provider string `json:"provider"`
		Token    string `json:"token"`
	}
	if c.ShouldBindJSON(&body) != nil || strings.TrimSpace(body.Provider) == "" || len(body.Token) < 16 || len(body.Token) > 4096 {
		fail(c, "推送设备参数无效")
		return
	}
	hash := sha256.Sum256([]byte(strings.TrimSpace(body.Token)))
	var row catlink.CatPushDevice
	global.GVA_DB.Where("token_hash = ?", hex.EncodeToString(hash[:])).Assign(map[string]any{"user_id": uid, "provider": strings.TrimSpace(body.Provider), "enabled": true, "updated_at": time.Now()}).FirstOrCreate(&row, catlink.CatPushDevice{UserID: uid, Provider: strings.TrimSpace(body.Provider), TokenHash: hex.EncodeToString(hash[:]), CreatedAt: time.Now(), UpdatedAt: time.Now()})
	ok(c, gin.H{"id": row.ID, "enabled": true})
}

func (a *CatAuthApi) DisablePushDevice(c *gin.Context) {
	uid := currentUserId(c)
	hash := sha256.Sum256([]byte(strings.TrimSpace(c.Param("token"))))
	result := global.GVA_DB.Model(&catlink.CatPushDevice{}).Where("user_id = ? AND token_hash = ?", uid, hex.EncodeToString(hash[:])).Update("enabled", false)
	if result.Error != nil || result.RowsAffected == 0 {
		fail(c, "推送设备不存在")
		return
	}
	ok(c, gin.H{"enabled": false})
}

// Metrics exposes stable counters for dashboards and Prometheus adapters.
func (a *CatAdminApi) Metrics(c *gin.Context) {
	counts := catlinkMetricCounters()
	jsonCounts := gin.H{}
	for key, value := range counts {
		jsonCounts[key] = value
	}
	ok(c, gin.H{"counters": jsonCounts, "generatedAt": time.Now().UnixMilli()})
}

func catlinkMetricCounters() map[string]int64 {
	counts := make(map[string]int64)
	for key, model := range map[string]any{
		"users": &system.SysUser{}, "posts": &catlink.CatPost{}, "cats": &catlink.CatPublicCat{},
		"media": &catlink.CatMedia{}, "notifications": &catlink.CatNotification{}, "outbox": &catlink.CatNotificationOutbox{},
		"migrationJobs": &catlink.CatStorageMigrationJob{}, "missingCases": &catlink.CatMissingCase{}, "rescueTasks": &catlink.CatRescueTask{},
	} {
		var count int64
		if global.GVA_DB.Model(model).Count(&count).Error == nil {
			counts[key] = count
		}
	}
	return counts
}

// MetricsPrometheus serves the same counters in the text exposition format.
func (a *CatAdminApi) MetricsPrometheus(c *gin.Context) {
	counts := catlinkMetricCounters()
	keys := make([]string, 0, len(counts))
	for key := range counts {
		keys = append(keys, key)
	}
	sort.Strings(keys)
	var body strings.Builder
	for _, key := range keys {
		body.WriteString("catlink_")
		body.WriteString(key)
		body.WriteByte(' ')
		body.WriteString(strconv.FormatInt(counts[key], 10))
		body.WriteByte('\n')
	}
	c.Data(http.StatusOK, "text/plain; version=0.0.4; charset=utf-8", []byte(body.String()))
}

func (a *CatAdminApi) RetentionPolicies(c *gin.Context) {
	var rows []catlink.CatRetentionPolicy
	global.GVA_DB.Order("kind asc").Find(&rows)
	ok(c, gin.H{"items": rows})
}

func (a *CatAdminApi) RetentionPreview(c *gin.Context) {
	var policies []catlink.CatRetentionPolicy
	global.GVA_DB.Where("enabled = ?", true).Find(&policies)
	counts := gin.H{}
	for _, policy := range policies {
		before := time.Now().Add(-time.Duration(policy.Days) * 24 * time.Hour)
		var count int64
		switch policy.Kind {
		case "notifications":
			global.GVA_DB.Model(&catlink.CatNotification{}).Where("created_at < ?", before).Count(&count)
		case "notification_outbox":
			global.GVA_DB.Model(&catlink.CatNotificationOutbox{}).Where("created_at < ?", before).Count(&count)
		case "temporary_media":
			global.GVA_DB.Model(&catlink.CatMedia{}).Where("temporary = ? AND created_at < ?", true, before).Count(&count)
		}
		counts[policy.Kind] = count
	}
	ok(c, gin.H{"counts": counts, "generatedAt": time.Now().UnixMilli()})
}

func (a *CatAdminApi) SaveRetentionPolicy(c *gin.Context) {
	var body struct {
		Kind    string `json:"kind"`
		Days    int    `json:"days"`
		Enabled *bool  `json:"enabled"`
	}
	if c.ShouldBindJSON(&body) != nil || strings.TrimSpace(body.Kind) == "" || body.Days < 1 || body.Days > 3650 {
		fail(c, "保留策略参数无效")
		return
	}
	enabled := true
	if body.Enabled != nil {
		enabled = *body.Enabled
	}
	var row catlink.CatRetentionPolicy
	global.GVA_DB.Where("kind = ?", strings.TrimSpace(body.Kind)).Assign(map[string]any{"days": body.Days, "enabled": enabled, "updated_at": time.Now()}).FirstOrCreate(&row, catlink.CatRetentionPolicy{Kind: strings.TrimSpace(body.Kind), Days: body.Days, Enabled: enabled, UpdatedAt: time.Now()})
	ok(c, row)
}

func (a *CatAdminApi) IdentityBadges(c *gin.Context) {
	var rows []catlink.CatIdentityBadge
	global.GVA_DB.Order("updated_at desc").Limit(200).Find(&rows)
	ok(c, gin.H{"items": rows})
}

func (a *CatAdminApi) ResolveIdentityBadge(c *gin.Context) {
	id, _ := strconv.ParseUint(c.Param("id"), 10, 64)
	var body struct {
		Status string `json:"status"`
	}
	if c.ShouldBindJSON(&body) != nil || (body.Status != "verified" && body.Status != "rejected" && body.Status != "pending") {
		fail(c, "认证状态无效")
		return
	}
	result := global.GVA_DB.Model(&catlink.CatIdentityBadge{}).Where("id = ?", uint(id)).Update("status", body.Status)
	if result.Error != nil || result.RowsAffected == 0 {
		fail(c, "认证记录不存在")
		return
	}
	ok(c, gin.H{"status": body.Status})
}
