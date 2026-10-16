package catlink

import (
	"errors"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	"github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	"github.com/flipped-aurora/gin-vue-admin/server/utils"
	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

// CatAdminOnly 限制 CatLink 管理 API 只允许 GVA 超级管理员访问。
func CatAdminOnly() gin.HandlerFunc {
	return func(c *gin.Context) {
		uid := utils.GetUserID(c)
		var user system.SysUser
		if uid == 0 || global.GVA_DB == nil || global.GVA_DB.Select("id", "enable", "authority_id").First(&user, uid).Error != nil || user.Enable != 1 || user.AuthorityId != CatAdminAuthority {
			c.JSON(200, gin.H{"code": 7, "data": nil, "msg": "无 CatLink 管理权限"})
			c.Abort()
			return
		}
		c.Next()
	}
}

type CatAdminApi struct{}

func pageParams(c *gin.Context) (int, int, string) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", c.DefaultQuery("pageNum", "1")))
	size, _ := strconv.Atoi(c.DefaultQuery("pageSize", "20"))
	if page < 1 {
		page = 1
	}
	if size < 1 {
		size = 20
	}
	if size > 200 {
		size = 200
	}
	return page, size, strings.TrimSpace(c.Query("keyword"))
}

func (a *CatAdminApi) Stats(c *gin.Context) {
	counts := gin.H{}
	models := []struct {
		name  string
		model any
	}{
		{"users", &system.SysUser{}}, {"cats", &catlink.CatPublicCat{}}, {"posts", &catlink.CatPost{}},
		{"reports", &catlink.CatReport{}}, {"quotes", &catlink.CatEncouragement{}}, {"ads", &catlink.CatAd{}},
		{"media", &catlink.CatMedia{}}, {"adoptions", &catlink.CatApply{}},
	}
	for _, item := range models {
		var n int64
		global.GVA_DB.Model(item.model).Count(&n)
		counts[item.name] = n
	}
	var pending int64
	global.GVA_DB.Model(&catlink.CatReport{}).Where("status = ?", "pending").Count(&pending)
	counts["pendingReports"] = pending
	statusCounts := gin.H{}
	for status := range catStatusLabels {
		var n int64
		global.GVA_DB.Model(&catlink.CatPublicCat{}).Where("status = ?", status).Count(&n)
		statusCounts[status] = n
	}
	counts["catStatuses"] = statusCounts
	ok(c, counts)
}

func (a *CatAdminApi) Posts(c *gin.Context) {
	page, size, keyword := pageParams(c)
	q := global.GVA_DB.Model(&catlink.CatPost{})
	if keyword != "" {
		q = q.Where("title LIKE ? OR content LIKE ?", "%"+keyword+"%", "%"+keyword+"%")
	}
	var total int64
	q.Count(&total)
	var rows []catlink.CatPost
	q.Order("created_at desc").Offset((page - 1) * size).Limit(size).Find(&rows)
	ok(c, gin.H{"items": rows, "total": total})
}

func (a *CatAdminApi) LockPost(c *gin.Context) {
	id, _ := strconv.Atoi(c.Param("id"))
	var body struct {
		Locked bool `json:"locked"`
	}
	_ = c.ShouldBindJSON(&body)
	if err := global.GVA_DB.Model(&catlink.CatPost{}).Where("id = ?", id).Update("locked", body.Locked).Error; err != nil {
		fail(c, "更新帖子状态失败")
		return
	}
	ok(c, gin.H{"id": id, "locked": body.Locked})
}

func (a *CatAdminApi) DeletePost(c *gin.Context) {
	id, err := strconv.ParseUint(strings.TrimSpace(c.Param("id")), 10, 64)
	if err != nil || id == 0 {
		fail(c, "帖子不存在")
		return
	}
	err = global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		var post catlink.CatPost
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).First(&post, uint(id)).Error; err != nil {
			return err
		}
		var commentIDs []uint
		if err := tx.Model(&catlink.CatComment{}).Where("post_id = ?", id).Pluck("id", &commentIDs).Error; err != nil {
			return err
		}
		if len(commentIDs) > 0 {
			if err := tx.Where("comment_id IN ?", commentIDs).Delete(&catlink.CatCommentLike{}).Error; err != nil {
				return err
			}
		}
		if err := tx.Where("post_id = ?", id).Delete(&catlink.CatComment{}).Error; err != nil {
			return err
		}
		if err := tx.Where("post_id = ?", id).Delete(&catlink.CatLike{}).Error; err != nil {
			return err
		}
		if err := tx.Where("post_id = ?", id).Delete(&catlink.CatBookmark{}).Error; err != nil {
			return err
		}
		if err := queuePostMediaCleanup(tx, post); err != nil {
			return err
		}
		return tx.Delete(&catlink.CatPost{}, uint(id)).Error
	})
	if err != nil {
		fail(c, "删除帖子失败")
		return
	}
	ok(c, gin.H{"id": id})
}

func queuePostMediaCleanup(tx *gorm.DB, post catlink.CatPost) error {
	ids := jsonUintList(post.MediaIDs)
	urls := postMediaURLs(post.ImageURL)
	if len(ids) == 0 && len(urls) == 0 {
		return nil
	}
	query := tx.Model(&catlink.CatMedia{}).Where("owner_id = ? AND category = ?", post.AuthorID, "post")
	switch {
	case len(ids) > 0 && len(urls) > 0:
		query = query.Where("id IN ? OR url IN ?", ids, urls)
	case len(ids) > 0:
		query = query.Where("id IN ?", ids)
	default:
		query = query.Where("url IN ?", urls)
	}
	now := time.Now()
	return query.Updates(map[string]any{"temporary": true, "expires_at": &now}).Error
}

func (a *CatAdminApi) Cats(c *gin.Context) {
	page, size, keyword := pageParams(c)
	q := global.GVA_DB.Model(&catlink.CatPublicCat{})
	if raw := strings.TrimSpace(c.Query("catStatus")); raw != "" {
		status, inferred, valid := normalizeCatStatus(raw)
		if !valid {
			fail(c, "猫咪状态筛选无效")
			return
		}
		q = q.Where("status = ?", status)
		if inferred != "" {
			q = q.Where("archive_reason = ?", inferred)
		}
	}
	if keyword != "" {
		q = q.Where("nickname LIKE ? OR story LIKE ? OR location_name LIKE ?", "%"+keyword+"%", "%"+keyword+"%", "%"+keyword+"%")
	}
	var total int64
	q.Count(&total)
	var rows []catlink.CatPublicCat
	q.Order("updated_at desc").Offset((page - 1) * size).Limit(size).Find(&rows)
	ok(c, gin.H{"items": rows, "total": total})
}

func (a *CatAdminApi) SetCatStatus(c *gin.Context) {
	id, _ := strconv.Atoi(c.Param("id"))
	var body struct {
		Status        string `json:"status"`
		ArchiveReason string `json:"archiveReason"`
		StatusReason  string `json:"statusReason"`
	}
	if c.ShouldBindJSON(&body) != nil {
		fail(c, "猫咪状态参数无效")
		return
	}
	status, archiveReason, err := validateCatStatusInput(body.Status, body.ArchiveReason)
	if err != nil {
		fail(c, err.Error())
		return
	}
	var row catlink.CatPublicCat
	var notifications []catlink.CatNotification
	err = global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).First(&row, id).Error; err != nil {
			return err
		}
		from, _, valid := normalizeCatStatus(row.Status)
		if !valid || !canChangeCatStatus(from, status, row.ArchiveReason) {
			return catStatusError("当前状态不允许变更为" + catStatusLabels[status])
		}
		now := time.Now()
		statusDay := calendarEpochDay(now)
		if err := tx.Model(&row).Updates(map[string]any{
			"status": status, "cat_status": catStatusLabels[status], "archive_reason": archiveReason,
			"status_reason": strings.TrimSpace(body.StatusReason), "status_date": statusDay, "updated_at": now,
		}).Error; err != nil {
			return err
		}
		var closeErr error
		notifications, closeErr = closeActiveAdoptionsForCat(tx, row, utils.GetUserID(c), status, now)
		return closeErr
	})
	if err != nil {
		var statusErr catStatusError
		if errors.As(err, &statusErr) {
			fail(c, statusErr.Error())
		} else {
			fail(c, "猫咪档案不存在或状态更新失败")
		}
		return
	}
	for _, notification := range notifications {
		publishCatNotification(notification)
	}
	if err := global.GVA_DB.First(&row, id).Error; err != nil {
		fail(c, "猫咪状态更新失败")
		return
	}
	ok(c, publicCatView(row))
}

func (a *CatAdminApi) SetCatState(c *gin.Context) {
	id, _ := strconv.Atoi(c.Param("id"))
	var body struct {
		State string `json:"state"`
	}
	_ = c.ShouldBindJSON(&body)
	allowed := map[string]bool{"published": true, "paused": true, "archived": true}
	if !allowed[body.State] {
		fail(c, "无效状态")
		return
	}
	if err := global.GVA_DB.Model(&catlink.CatPublicCat{}).Where("id = ?", id).Update("state", body.State).Error; err != nil {
		fail(c, "更新猫咪状态失败")
		return
	}
	ok(c, gin.H{"id": id, "state": body.State})
}

func (a *CatAdminApi) Reports(c *gin.Context) {
	page, size, keyword := pageParams(c)
	q := global.GVA_DB.Model(&catlink.CatReport{})
	if s := c.Query("status"); s != "" {
		q = q.Where("status = ?", s)
	}
	if keyword != "" {
		q = q.Where("reason LIKE ? OR target_id LIKE ?", "%"+keyword+"%", "%"+keyword+"%")
	}
	var total int64
	q.Count(&total)
	var rows []catlink.CatReport
	q.Order("created_at desc").Offset((page - 1) * size).Limit(size).Find(&rows)
	ok(c, gin.H{"items": rows, "total": total})
}

func (a *CatAdminApi) ResolveReport(c *gin.Context) {
	id, _ := strconv.Atoi(c.Param("id"))
	var body struct {
		Status string `json:"status"`
		Action string `json:"action"`
		Note   string `json:"note"`
	}
	_ = c.ShouldBindJSON(&body)
	if body.Status != "resolved" && body.Status != "rejected" {
		fail(c, "状态必须为 resolved 或 rejected")
		return
	}
	var report catlink.CatReport
	if err := global.GVA_DB.First(&report, id).Error; err != nil {
		fail(c, "举报不存在")
		return
	}
	err := global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		if err := tx.Model(&report).Update("status", body.Status).Error; err != nil {
			return err
		}
		if body.Status != "resolved" {
			return nil
		}
		action := strings.TrimSpace(body.Action)
		if action == "" {
			action = "warning"
		}
		if !map[string]bool{"warning": true, "hide": true, "lock": true, "suspend": true}[action] {
			return errors.New("处罚动作无效")
		}
		return tx.Create(&catlink.CatModerationAction{ReportID: report.ID, TargetType: report.TargetType, TargetID: report.TargetID, Action: action, Reason: strings.TrimSpace(body.Note), OperatorID: utils.GetUserID(c), Status: "active", CreatedAt: time.Now()}).Error
	})
	if err != nil {
		fail(c, "更新举报状态失败")
		return
	}
	if err := createCatNotification(catlink.CatNotification{ToUser: report.Reporter, Type: "report", Title: "举报处理结果", Body: "你的举报已处理：" + body.Status}); err != nil {
		fail(c, "发送举报处理通知失败")
		return
	}
	ok(c, gin.H{"id": id, "status": body.Status})
}

func (a *CatAdminApi) ModerationAppeals(c *gin.Context) {
	page, size, _ := pageParams(c)
	q := global.GVA_DB.Model(&catlink.CatModerationAppeal{})
	if status := strings.TrimSpace(c.Query("status")); status != "" {
		q = q.Where("status = ?", status)
	}
	var total int64
	q.Count(&total)
	var rows []catlink.CatModerationAppeal
	q.Order("created_at desc").Offset((page - 1) * size).Limit(size).Find(&rows)
	ok(c, gin.H{"items": rows, "total": total})
}

func (a *CatAdminApi) ResolveModerationAppeal(c *gin.Context) {
	id, _ := strconv.ParseUint(c.Param("id"), 10, 64)
	var body struct {
		Status string `json:"status"`
		Result string `json:"result"`
	}
	if c.ShouldBindJSON(&body) != nil || (body.Status != "approved" && body.Status != "rejected") || len([]rune(body.Result)) > 500 {
		fail(c, "申诉处理参数无效")
		return
	}
	var appeal catlink.CatModerationAppeal
	if global.GVA_DB.First(&appeal, uint(id)).Error != nil || appeal.Status != "pending" {
		fail(c, "申诉不存在或已处理")
		return
	}
	err := global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		if err := tx.Model(&appeal).Updates(map[string]any{"status": body.Status, "result": strings.TrimSpace(body.Result), "updated_at": time.Now()}).Error; err != nil {
			return err
		}
		if body.Status == "approved" {
			return tx.Model(&catlink.CatModerationAction{}).Where("id = ?", appeal.ActionID).Update("status", "revoked").Error
		}
		return nil
	})
	if err != nil {
		fail(c, "申诉处理失败")
		return
	}
	ok(c, gin.H{"status": body.Status})
}

func (a *CatAdminApi) Quotes(c *gin.Context) {
	var rows []catlink.CatEncouragement
	global.GVA_DB.Order("id desc").Find(&rows)
	ok(c, gin.H{"items": rows})
}
func (a *CatAdminApi) SaveQuote(c *gin.Context) {
	var body struct {
		Quote   string `json:"quote"`
		Source  string `json:"source"`
		Enabled *bool  `json:"enabled"`
	}
	if err := c.ShouldBindJSON(&body); err != nil {
		fail(c, "请求格式错误")
		return
	}
	quote := strings.TrimSpace(body.Quote)
	if quote == "" || len([]rune(quote)) > 500 || len([]rune(body.Source)) > 100 {
		fail(c, "语句需为 1-500 个字符，来源不超过 100 个字符")
		return
	}
	enabled := true
	if body.Enabled != nil {
		enabled = *body.Enabled
	}
	source := strings.TrimSpace(body.Source)
	if source == "" {
		source = "有猫腻"
	}
	row := catlink.CatEncouragement{Quote: quote, Source: source, Enabled: enabled}
	if err := global.GVA_DB.Create(&row).Error; err != nil {
		fail(c, "新增语句失败")
		return
	}
	ok(c, row)
}
func (a *CatAdminApi) UpdateQuote(c *gin.Context) {
	id, err := strconv.Atoi(c.Param("id"))
	if err != nil || id < 1 {
		fail(c, "语句 ID 无效")
		return
	}
	var body struct {
		Quote   string `json:"quote"`
		Source  string `json:"source"`
		Enabled *bool  `json:"enabled"`
	}
	if err := c.ShouldBindJSON(&body); err != nil {
		fail(c, "请求格式错误")
		return
	}
	quote, source := strings.TrimSpace(body.Quote), strings.TrimSpace(body.Source)
	if quote == "" || len([]rune(quote)) > 500 || len([]rune(source)) > 100 {
		fail(c, "语句需为 1-500 个字符，来源不超过 100 个字符")
		return
	}
	updates := map[string]any{"quote": quote, "source": source}
	if body.Enabled != nil {
		updates["enabled"] = *body.Enabled
	}
	result := global.GVA_DB.Model(&catlink.CatEncouragement{}).Where("id = ?", id).Updates(updates)
	if result.Error != nil {
		fail(c, "更新语句失败")
		return
	}
	if result.RowsAffected != 1 {
		fail(c, "语句不存在")
		return
	}
	ok(c, gin.H{"id": id})
}
func (a *CatAdminApi) DeleteQuote(c *gin.Context) {
	id, _ := strconv.Atoi(c.Param("id"))
	global.GVA_DB.Delete(&catlink.CatEncouragement{}, id)
	ok(c, gin.H{"id": id})
}

func normalizeAd(row *catlink.CatAd) error {
	row.Name = strings.TrimSpace(row.Name)
	row.Slot = strings.TrimSpace(row.Slot)
	row.ImageURL = strings.TrimSpace(row.ImageURL)
	row.LinkURL = strings.TrimSpace(row.LinkURL)
	if row.Name == "" || row.Slot == "" {
		return errors.New("广告名称和位置不能为空")
	}
	if row.Slot != "launch" && row.Slot != "community" {
		return errors.New("广告位置仅支持 launch 或 community")
	}
	if len(row.ImageURL) > 500 || len(row.LinkURL) > 500 {
		return errors.New("广告图片或链接地址过长")
	}
	if row.ImageURL != "" {
		parsed, err := url.Parse(row.ImageURL)
		if err != nil || (parsed.IsAbs() && parsed.Scheme != "http" && parsed.Scheme != "https") || (!parsed.IsAbs() && !strings.HasPrefix(row.ImageURL, "/uploads/")) {
			return errors.New("广告图片仅支持 http(s) 或 /uploads/ 地址")
		}
	}
	if row.LinkURL != "" {
		parsed, err := url.Parse(row.LinkURL)
		if err != nil || !parsed.IsAbs() || (parsed.Scheme != "http" && parsed.Scheme != "https") || parsed.Host == "" {
			return errors.New("广告跳转仅支持完整 http(s) 地址")
		}
	}
	if row.HeightMode == "" {
		row.HeightMode = "ratio"
	}
	switch row.HeightMode {
	case "fixed":
		if row.HeightValue < 80 || row.HeightValue > 1600 {
			return errors.New("固定高度需为 80-1600px")
		}
	case "ratio":
		if row.HeightValue == 0 {
			row.HeightValue = 42.857
		}
		if row.HeightValue < 15 || row.HeightValue > 120 {
			return errors.New("高度百分比需为 15%-120%")
		}
	default:
		return errors.New("高度模式仅支持 fixed 或 ratio")
	}
	return nil
}

func adUpdates(row catlink.CatAd) map[string]any {
	return map[string]any{
		"slot": row.Slot, "name": row.Name, "image_url": row.ImageURL, "link_url": row.LinkURL,
		"fee": row.Fee, "on_time": row.OnTime, "duration": row.Duration, "off_time": row.OffTime,
		"contact": row.Contact, "height_mode": row.HeightMode, "height_value": row.HeightValue, "enabled": row.Enabled,
	}
}

func (a *CatAdminApi) Ads(c *gin.Context) {
	var rows []catlink.CatAd
	global.GVA_DB.Order("id desc").Find(&rows)
	ok(c, gin.H{"items": rows})
}
func (a *CatAdminApi) SaveAd(c *gin.Context) {
	var row catlink.CatAd
	if err := c.ShouldBindJSON(&row); err != nil {
		fail(c, "请求格式错误")
		return
	}
	if err := normalizeAd(&row); err != nil {
		fail(c, err.Error())
		return
	}
	if err := global.GVA_DB.Create(&row).Error; err != nil {
		failDB(c, "新增广告失败，请稍后重试")
		return
	}
	ok(c, row)
}
func (a *CatAdminApi) UpdateAd(c *gin.Context) {
	id, err := strconv.Atoi(c.Param("id"))
	if err != nil || id < 1 {
		fail(c, "广告 ID 无效")
		return
	}
	var row catlink.CatAd
	if err := c.ShouldBindJSON(&row); err != nil {
		fail(c, "请求格式错误")
		return
	}
	if err := normalizeAd(&row); err != nil {
		fail(c, err.Error())
		return
	}
	var existing catlink.CatAd
	if err := global.GVA_DB.Select("id").First(&existing, id).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			fail(c, "广告不存在")
		} else {
			failDB(c, "查询广告失败，请稍后重试")
		}
		return
	}
	if err := global.GVA_DB.Model(&existing).Updates(adUpdates(row)).Error; err != nil {
		failDB(c, "更新广告失败，请稍后重试")
		return
	}
	row.ID = uint(id)
	ok(c, row)
}
func (a *CatAdminApi) DeleteAd(c *gin.Context) {
	id, _ := strconv.Atoi(c.Param("id"))
	global.GVA_DB.Delete(&catlink.CatAd{}, id)
	ok(c, gin.H{"id": id})
}

func (a *CatAdminApi) Versions(c *gin.Context) {
	var rows []catlink.CatVersion
	global.GVA_DB.Order("version_code desc").Find(&rows)
	ok(c, gin.H{"items": rows})
}
func (a *CatAdminApi) SaveVersion(c *gin.Context) {
	var row catlink.CatVersion
	if err := c.ShouldBindJSON(&row); err != nil {
		fail(c, "请求格式错误")
		return
	}
	row.VersionName = strings.TrimSpace(row.VersionName)
	row.DownloadURL = strings.TrimSpace(row.DownloadURL)
	if row.VersionCode < 1 || row.VersionName == "" {
		fail(c, "版本号和版本名不能为空")
		return
	}
	if len(row.VersionName) > 30 || len(row.DownloadURL) > 500 || safeHTTPSURL(row.DownloadURL) == "" {
		fail(c, "版本名过长或下载地址不是有效的 HTTPS URL")
		return
	}
	if err := global.GVA_DB.Create(&row).Error; err != nil {
		failDB(c, "保存版本失败，请稍后重试")
		return
	}
	ok(c, row)
}
func (a *CatAdminApi) DeleteVersion(c *gin.Context) {
	id, _ := strconv.Atoi(c.Param("id"))
	global.GVA_DB.Delete(&catlink.CatVersion{}, id)
	ok(c, gin.H{"id": id})
}

func (a *CatAdminApi) Adoptions(c *gin.Context) {
	page, size, keyword := pageParams(c)
	q := global.GVA_DB.Model(&catlink.CatApply{}).
		Joins("LEFT JOIN cat_public_cats adoption_cat ON adoption_cat.id = cat_applies.cat_id").
		Joins("LEFT JOIN sys_users adoption_applicant ON adoption_applicant.id = cat_applies.applicant_id").
		Joins("LEFT JOIN sys_users adoption_owner ON adoption_owner.id = cat_applies.owner_id")
	if s := strings.TrimSpace(c.Query("status")); s != "" {
		if _, valid := adoptionStatusLabels[s]; !valid {
			fail(c, "领养申请状态筛选无效")
			return
		}
		q = q.Where("cat_applies.status = ?", s)
	}
	if keyword != "" {
		like := "%" + keyword + "%"
		q = q.Where("adoption_cat.nickname LIKE ? OR adoption_applicant.nick_name LIKE ? OR adoption_owner.nick_name LIKE ?", like, like, like)
	}
	var total int64
	q.Count(&total)
	var rows []catlink.CatApply
	q.Select("cat_applies.*").Order("cat_applies.created_at desc").Offset((page - 1) * size).Limit(size).Find(&rows)
	ok(c, gin.H{"items": adoptionApplicationViews(rows, 0, true), "total": total})
}
func (a *CatAdminApi) SetAdoptionStatus(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil || id == 0 {
		fail(c, "领养申请不存在")
		return
	}
	var body struct {
		Status string `json:"status"`
		Note   string `json:"note"`
	}
	if c.ShouldBindJSON(&body) != nil || len([]rune(body.Note)) > 2000 {
		fail(c, "领养申请状态参数无效")
		return
	}
	var result adoptionTransitionResult
	err = global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		var transitionErr error
		result, transitionErr = transitionAdoption(tx, uint(id), utils.GetUserID(c), true, body.Status, body.Note, "", "[]")
		return transitionErr
	})
	if err != nil {
		failAdoption(c, err, "领养申请状态更新失败")
		return
	}
	for _, notification := range result.Notifications {
		publishCatNotification(notification)
	}
	ok(c, gin.H{"id": strconv.FormatUint(id, 10), "status": result.Application.Status})
}

func (a *CatAdminApi) Broadcast(c *gin.Context) {
	a.createAnnouncementCampaign(c)
}
