package catlink

import (
	"fmt"
	"math/rand"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	"github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

func dedupeInteractionTable(table, targetColumn string, parentModel any, countColumn string) error {
	if !global.GVA_DB.Migrator().HasTable(table) {
		return nil
	}
	type duplicateGroup struct {
		UserID   uint `gorm:"column:user_id"`
		TargetID uint `gorm:"column:target_id"`
		KeepID   uint `gorm:"column:keep_id"`
	}
	for {
		var groups []duplicateGroup
		selectSQL := fmt.Sprintf("user_id, %s AS target_id, MIN(id) AS keep_id", targetColumn)
		if err := global.GVA_DB.Table(table).Select(selectSQL).
			Group("user_id, " + targetColumn).Having("COUNT(*) > 1").Limit(100).Scan(&groups).Error; err != nil {
			return err
		}
		if len(groups) == 0 {
			break
		}
		for _, group := range groups {
			deleteSQL := fmt.Sprintf("DELETE FROM %s WHERE user_id = ? AND %s = ? AND id <> ?", table, targetColumn)
			if err := global.GVA_DB.Exec(deleteSQL, group.UserID, group.TargetID, group.KeepID).Error; err != nil {
				return err
			}
			if parentModel != nil && countColumn != "" {
				var count int64
				if err := global.GVA_DB.Table(table).Where(targetColumn+" = ?", group.TargetID).Count(&count).Error; err != nil {
					return err
				}
				if err := global.GVA_DB.Model(parentModel).Where("id = ?", group.TargetID).UpdateColumn(countColumn, count).Error; err != nil {
					return err
				}
			}
		}
	}
	return nil
}

// AutoMigrateCatTables 注册 CatLink 业务表（幂等）
func AutoMigrateCatTables() {
	if global.GVA_DB == nil {
		return
	}
	if global.GVA_CONFIG.System.DisableAutoMigrate {
		if err := cleanupExpiredTemporaryMedia(); err != nil {
			panic(fmt.Errorf("catlink temporary media cleanup failed: %w", err))
		}
		if err := migrateLegacyContractMedia(global.GVA_CONFIG.Local.StorePath, global.GVA_CONFIG.Local.Path); err != nil {
			panic(fmt.Errorf("catlink contract media migration failed: %w", err))
		}
		if err := reconcileCatLinkMediaFiles(global.GVA_CONFIG.Local.StorePath, global.GVA_CONFIG.Local.Path); err != nil {
			panic(fmt.Errorf("catlink media reconciliation failed: %w", err))
		}
		if err := syncCatLinkMediaCatalog(); err != nil {
			panic(fmt.Errorf("catlink media catalog sync failed: %w", err))
		}
		return
	}
	// 旧库先只补 server_revision 列，再清理重复实体；完整 AutoMigrate 随后创建唯一索引。
	if global.GVA_DB.Migrator().HasTable(&catlink.CatVault{}) {
		if !global.GVA_DB.Migrator().HasColumn(&catlink.CatVault{}, "ServerRevision") {
			if err := global.GVA_DB.Migrator().AddColumn(&catlink.CatVault{}, "ServerRevision"); err != nil {
				panic(fmt.Errorf("catlink vault server revision migration failed: %w", err))
			}
		}
		var lastDuplicateID uint
		for {
			var duplicateIDs []uint
			if err := global.GVA_DB.Raw(`
				SELECT candidate.id
				FROM cat_vaults AS candidate
				WHERE candidate.id > ? AND EXISTS (
					SELECT 1 FROM cat_vaults AS keeper
					WHERE keeper.account_id = candidate.account_id
					  AND keeper.entity_type = candidate.entity_type
					  AND keeper.entity_id = candidate.entity_id
					  AND (keeper.revision > candidate.revision OR (keeper.revision = candidate.revision AND keeper.id > candidate.id))
				)
				ORDER BY candidate.id ASC LIMIT 100`, lastDuplicateID).Scan(&duplicateIDs).Error; err != nil {
				panic(fmt.Errorf("catlink vault duplicate scan failed: %w", err))
			}
			if len(duplicateIDs) == 0 {
				break
			}
			lastDuplicateID = duplicateIDs[len(duplicateIDs)-1]
			if err := global.GVA_DB.Where("id IN ?", duplicateIDs).Delete(&catlink.CatVault{}).Error; err != nil {
				panic(fmt.Errorf("catlink vault duplicate cleanup failed: %w", err))
			}
		}
	}
	if err := dedupeInteractionTable("cat_likes", "post_id", &catlink.CatPost{}, "like_count"); err != nil {
		panic(fmt.Errorf("catlink like dedupe failed: %w", err))
	}
	if err := dedupeInteractionTable("cat_bookmarks", "post_id", nil, ""); err != nil {
		panic(fmt.Errorf("catlink bookmark dedupe failed: %w", err))
	}
	if err := dedupeInteractionTable("cat_follows", "cat_id", &catlink.CatPublicCat{}, "follow_count"); err != nil {
		panic(fmt.Errorf("catlink follow dedupe failed: %w", err))
	}
	if err := global.GVA_DB.AutoMigrate(
		&catlink.CatDeviceSession{}, &catlink.CatAccountSecurity{}, &catlink.CatAuthCode{}, &catlink.CatEmailConfig{}, &catlink.CatShare{}, &catlink.CatUserFollow{},
		&catlink.CatEncouragement{}, &catlink.CatEncouragementAIConfig{}, &catlink.CatPublicCat{}, &catlink.CatPost{},
		&catlink.CatComment{}, &catlink.CatCommentLike{}, &catlink.CatLike{}, &catlink.CatBookmark{},
		&catlink.CatFollow{}, &catlink.CatReport{}, &catlink.CatAd{},
		&catlink.CatModerationAction{}, &catlink.CatModerationAppeal{},
		&catlink.CatVersion{}, &catlink.CatNotification{}, &catlink.CatAnnouncementCampaign{}, &catlink.CatApply{},
		&catlink.CatConversation{}, &catlink.CatMessage{}, &catlink.CatMediaUploadWindow{}, &catlink.CatMedia{},
		&catlink.CatVault{}, &catlink.CatVaultMeta{},
		&catlink.CatSafetyRelation{}, &catlink.CatTimelineEvent{}, &catlink.CatAdoptionEvent{},
		&catlink.CatMissingCase{}, &catlink.CatMissingSighting{}, &catlink.CatRescueTask{},
		&catlink.CatIdentityBadge{}, &catlink.CatPushDevice{}, &catlink.CatNotificationOutbox{},
		&catlink.CatStorageMigrationJob{}, &catlink.CatRetentionPolicy{},
	); err != nil {
		panic(fmt.Errorf("catlink table migration failed: %w", err))
	}
	if err := migrateCatStatuses(); err != nil {
		panic(fmt.Errorf("catlink status migration failed: %w", err))
	}
	if err := migrateAdoptionApplications(); err != nil {
		panic(fmt.Errorf("catlink adoption migration failed: %w", err))
	}
	for kind, days := range map[string]int{"notifications": 180, "notification_outbox": 30, "temporary_media": 2} {
		policy := catlink.CatRetentionPolicy{Kind: kind, Days: days, Enabled: true, UpdatedAt: time.Now()}
		if err := global.GVA_DB.Where("kind = ?", kind).FirstOrCreate(&policy).Error; err != nil {
			panic(fmt.Errorf("catlink retention seed failed: %w", err))
		}
		if global.GVA_DB.Migrator().HasTable(&system.SysTimedTask{}) {
			for _, timed := range []system.SysTimedTask{
				{Name: "CleanCatLinkRetentionPolicies", Description: "清理过期验证码，并按保留策略清理通知、媒体和推送 outbox", Spec: "@daily", ExecutorType: system.TimedTaskExecutorMethod, MethodName: "CleanCatLinkRetentionPolicies", Enabled: true},
				{Name: "ProcessCatLinkNotificationOutbox", Description: "重试并投递 CatLink 通知 outbox", Spec: "@every 1m", ExecutorType: system.TimedTaskExecutorMethod, MethodName: "ProcessCatLinkNotificationOutbox", Enabled: true},
				{Name: "DeliverCatLinkAnnouncements", Description: "投递到期的 CatLink 分群公告", Spec: "@every 1m", ExecutorType: system.TimedTaskExecutorMethod, MethodName: "DeliverCatLinkAnnouncements", Enabled: true},
			} {
				if err := global.GVA_DB.Where("name = ?", timed.Name).Attrs(timed).FirstOrCreate(&system.SysTimedTask{}).Error; err != nil {
					panic(fmt.Errorf("catlink timed task seed failed: %w", err))
				}
			}
		}
	}
	if err := dedupeInteractionTable("cat_comment_likes", "comment_id", &catlink.CatComment{}, "like_count"); err != nil {
		panic(fmt.Errorf("catlink comment like dedupe failed: %w", err))
	}
	if err := migrateLegacyPublicMedia(global.GVA_CONFIG.Local.Path); err != nil {
		panic(fmt.Errorf("catlink public media migration failed: %w", err))
	}
	if err := cleanupExpiredTemporaryMedia(); err != nil {
		panic(fmt.Errorf("catlink temporary media cleanup failed: %w", err))
	}
	if err := migrateLegacyContractMedia(global.GVA_CONFIG.Local.StorePath, global.GVA_CONFIG.Local.Path); err != nil {
		panic(fmt.Errorf("catlink contract media migration failed: %w", err))
	}
	if err := reconcileCatLinkMediaFiles(global.GVA_CONFIG.Local.StorePath, global.GVA_CONFIG.Local.Path); err != nil {
		panic(fmt.Errorf("catlink media reconciliation failed: %w", err))
	}
	if err := syncCatLinkMediaCatalog(); err != nil {
		panic(fmt.Errorf("catlink media catalog sync failed: %w", err))
	}
	// 清理旧版本可能写入的非标准 payload，避免 JSON escaping 放大和不可解密数据下行。
	var invalidBatch []catlink.CatVault
	if err := global.GVA_DB.Select("id", "tombstone", "payload").FindInBatches(&invalidBatch, 100, func(tx *gorm.DB, _ int) error {
		invalidIDs := make([]uint, 0, len(invalidBatch))
		for _, row := range invalidBatch {
			if (row.Tombstone && row.Payload != "") || (!row.Tombstone && !validVaultCiphertext(row.Payload)) {
				invalidIDs = append(invalidIDs, row.ID)
			}
		}
		if len(invalidIDs) > 0 {
			return tx.Where("id IN ?", invalidIDs).Delete(&catlink.CatVault{}).Error
		}
		return nil
	}).Error; err != nil {
		panic(fmt.Errorf("catlink vault invalid payload cleanup failed: %w", err))
	}

	// 旧版 access/refresh 共用 JWT 的会话不再升级，统一撤销并要求重新登录。
	global.GVA_DB.Model(&catlink.CatDeviceSession{}).
		Where("(access_token_hash IS NULL OR access_token_hash = '' OR refresh_token_hash IS NULL OR refresh_token_hash = '') AND token_hash <> '' AND revoked_at IS NULL").
		UpdateColumn("revoked_at", time.Now())

	// 旧 Vault 记录按稳定主键游标分批回填独立服务端游标，并初始化账号配额计数。
	var lastAccountID uint
	for {
		var accountIDs []uint
		if err := global.GVA_DB.Model(&catlink.CatVault{}).Where("account_id > ?", lastAccountID).
			Distinct("account_id").Order("account_id asc").Limit(100).Pluck("account_id", &accountIDs).Error; err != nil {
			panic(fmt.Errorf("catlink vault account migration failed: %w", err))
		}
		if len(accountIDs) == 0 {
			break
		}
		lastAccountID = accountIDs[len(accountIDs)-1]
		for _, accountID := range accountIDs {
			var maxRevision int64
			var lastRowID uint
			for {
				var rows []catlink.CatVault
				if err := global.GVA_DB.Select("id", "server_revision").Where("account_id = ? AND id > ?", accountID, lastRowID).
					Order("id asc").Limit(100).Find(&rows).Error; err != nil {
					panic(fmt.Errorf("catlink vault cursor migration failed: %w", err))
				}
				if len(rows) == 0 {
					break
				}
				lastRowID = rows[len(rows)-1].ID
				for index := range rows {
					serverRevision := rows[index].ServerRevision
					if serverRevision <= maxRevision {
						serverRevision = maxRevision + 1
						if err := global.GVA_DB.Model(&catlink.CatVault{}).Where("id = ?", rows[index].ID).Update("server_revision", serverRevision).Error; err != nil {
							panic(fmt.Errorf("catlink vault cursor backfill failed: %w", err))
						}
					}
					maxRevision = serverRevision
				}
			}

			var quota struct {
				RecordCount  int64
				PayloadBytes int64
			}
			if err := global.GVA_DB.Model(&catlink.CatVault{}).Where("account_id = ?", accountID).
				Select("COUNT(*) AS record_count, COALESCE(SUM(LENGTH(payload)), 0) AS payload_bytes").Scan(&quota).Error; err != nil {
				panic(fmt.Errorf("catlink vault quota migration failed: %w", err))
			}
			var meta catlink.CatVaultMeta
			if err := global.GVA_DB.Where("account_id = ?", accountID).FirstOrCreate(&meta, catlink.CatVaultMeta{AccountID: accountID}).Error; err != nil {
				panic(fmt.Errorf("catlink vault meta migration failed: %w", err))
			}
			if meta.Revision > maxRevision {
				maxRevision = meta.Revision
			}
			if err := global.GVA_DB.Model(&meta).Updates(map[string]any{
				"revision": maxRevision, "record_count": quota.RecordCount, "payload_bytes": quota.PayloadBytes,
			}).Error; err != nil {
				panic(fmt.Errorf("catlink vault meta update failed: %w", err))
			}
		}
	}
}

// ---------- 一言 ----------
type CatEncouragementApi struct{}

var fallbackQuotes = []string{
	"每一个生命都值得被温柔以待。",
	"救助一只猫，世界就多一分温暖。",
	"它们不会说话，但会用一生陪伴。",
	"今天也有猫咪在等你回家。",
	"爱心不分大小，善意永不缺席。",
}

func (a *CatEncouragementApi) Random(c *gin.Context) {
	if quote, err := generatedEncouragement(c.Request.Context(), false); err == nil && quote != "" {
		ok(c, gin.H{"quote": quote, "source": "ai"})
		return
	}
	var qs []catlink.CatEncouragement
	global.GVA_DB.Where("enabled = ?", true).Order("RAND()").Limit(1).Find(&qs)
	if len(qs) > 0 {
		ok(c, gin.H{"quote": qs[0].Quote, "source": qs[0].Source})
		return
	}
	ok(c, gin.H{"quote": fallbackQuotes[rand.Intn(len(fallbackQuotes))], "source": "有猫腻"})
}

// ---------- 公开猫 ----------
type CatPublicCatApi struct{}

func (a *CatPublicCatApi) List(c *gin.Context) {
	city := c.Query("city")
	district := c.Query("district")
	status := c.Query("status")
	if status == "" {
		status = "adoptable"
	}
	q := global.GVA_DB.Model(&catlink.CatPublicCat{}).Where("state = ?", "published")
	if city != "" {
		q = q.Where("city_code = ?", city)
	}
	if district != "" {
		q = q.Where("district_code = ?", district)
	}
	if status != "all" {
		canonical, inferred, valid := normalizeCatStatus(status)
		if !valid {
			fail(c, "猫咪状态筛选无效")
			return
		}
		q = q.Where("status = ?", canonical)
		if inferred != "" {
			q = q.Where("archive_reason = ?", inferred)
		}
	}
	if keyword := strings.TrimSpace(c.Query("keyword")); keyword != "" {
		q = q.Where("nickname LIKE ? OR alias LIKE ? OR story LIKE ?", "%"+keyword+"%", "%"+keyword+"%", "%"+keyword+"%")
	}
	if gender := strings.TrimSpace(c.Query("gender")); gender != "" {
		q = q.Where("gender = ?", gender)
	}
	if age := strings.TrimSpace(c.Query("age")); age != "" {
		q = q.Where("age_band = ?", age)
	}
	if sterilized := c.Query("sterilized"); sterilized == "true" {
		q = q.Where("health_summary LIKE ? AND health_summary NOT LIKE ?", "%绝育%", "%未绝育%")
	} else if sterilized == "false" {
		q = q.Where("health_summary LIKE ?", "%未绝育%")
	}
	if days, err := strconv.Atoi(c.Query("updatedDays")); err == nil && days > 0 && days <= 3650 {
		q = q.Where("updated_at >= ?", time.Now().Add(-time.Duration(days)*24*time.Hour))
	}
	if before, id, ok := parseCursor(c.Query("before")); ok {
		q = q.Where("updated_at < ? OR (updated_at = ? AND id < ?)", before, before, id)
	}
	limit := queryLimit(c, 24, 60)
	var items []catlink.CatPublicCat
	q.Order("updated_at desc, id desc").Limit(limit).Find(&items)
	views := make([]gin.H, 0, len(items))
	for _, item := range items {
		views = append(views, publicCatView(item))
	}
	var next any
	if len(items) == limit {
		last := items[len(items)-1]
		next = fmt.Sprintf("%d:%d", last.UpdatedAt.UnixMilli(), last.ID)
	}
	ok(c, gin.H{"items": views, "nextCursor": next})
}

func (a *CatPublicCatApi) Detail(c *gin.Context) {
	pc, err := publicCatByRef(c.Param("id"), 0, true)
	if err != nil {
		fail(c, "档案不存在")
		return
	}
	ok(c, publicCatView(pc))
}

// ---------- 社区 ----------
type CatCommunityApi struct{}

func communityFeedQuery(topic, city, district string) *gorm.DB {
	if topic == "" {
		topic = "all"
	}
	q := global.GVA_DB.Model(&catlink.CatPost{}).Where("locked = ?", false)
	if topic != "all" {
		q = q.Where("topic = ?", topic)
	}
	if city = strings.TrimSpace(city); city != "" {
		q = q.Where("city_code = ?", city)
	}
	if district = strings.TrimSpace(district); district != "" {
		q = q.Where("district_code = ?", district)
	}
	return q
}

func queryLimit(c *gin.Context, fallback, maximum int) int {
	limit, err := strconv.Atoi(c.Query("limit"))
	if err != nil || limit < 1 {
		return fallback
	}
	return min(limit, maximum)
}

func parseCursor(value string) (time.Time, uint, bool) {
	parts := strings.Split(strings.TrimSpace(value), ":")
	if len(parts) != 2 {
		return time.Time{}, 0, false
	}
	millis, errTime := strconv.ParseInt(parts[0], 10, 64)
	id, errID := strconv.ParseUint(parts[1], 10, 64)
	return time.UnixMilli(millis), uint(id), errTime == nil && errID == nil && id > 0
}

func respondCommunityFeed(c *gin.Context, q *gorm.DB, uid uint) {
	if keyword := strings.TrimSpace(c.Query("keyword")); keyword != "" {
		q = q.Where("title LIKE ? OR content LIKE ?", "%"+keyword+"%", "%"+keyword+"%")
	}
	if days, err := strconv.Atoi(c.Query("updatedDays")); err == nil && days > 0 && days <= 3650 {
		q = q.Where("created_at >= ?", time.Now().Add(-time.Duration(days)*24*time.Hour))
	}
	if before, id, ok := parseCursor(c.Query("before")); ok {
		q = q.Where("created_at < ? OR (created_at = ? AND id < ?)", before, before, id)
	}
	limit := queryLimit(c, 24, 60)
	var posts []catlink.CatPost
	if err := q.Order("created_at desc, id desc").Limit(limit).Find(&posts).Error; err != nil {
		failDB(c, "社区内容加载失败，请稍后重试")
		return
	}
	hasNext := len(posts) == limit
	var cursorPost catlink.CatPost
	if hasNext {
		cursorPost = posts[len(posts)-1]
	}
	if uid > 0 && len(posts) > 0 {
		authorIDs := make([]uint, 0, len(posts))
		for _, post := range posts {
			authorIDs = append(authorIDs, post.AuthorID)
		}
		suppressed := suppressedTargets(uid, authorIDs)
		filtered := posts[:0]
		for _, post := range posts {
			if _, hidden := suppressed[post.AuthorID]; !hidden {
				filtered = append(filtered, post)
			}
		}
		posts = filtered
	}
	likedPostIDs := make(map[uint]struct{})
	if uid > 0 && len(posts) > 0 {
		postIDs := make([]uint, 0, len(posts))
		for _, post := range posts {
			postIDs = append(postIDs, post.ID)
		}
		var ids []uint
		if err := global.GVA_DB.Model(&catlink.CatLike{}).Where("user_id = ? AND post_id IN ?", uid, postIDs).Pluck("post_id", &ids).Error; err != nil {
			failDB(c, "社区互动状态加载失败，请稍后重试")
			return
		}
		for _, id := range ids {
			likedPostIDs[id] = struct{}{}
		}
	}
	items := make([]gin.H, 0, len(posts))
	for _, post := range posts {
		view := postView(post)
		_, liked := likedPostIDs[post.ID]
		view["liked"] = liked
		items = append(items, view)
	}
	var next any
	if hasNext {
		next = fmt.Sprintf("%d:%d", cursorPost.CreatedAt.UnixMilli(), cursorPost.ID)
	}
	ok(c, gin.H{"items": items, "nextCursor": next})
}

func (a *CatCommunityApi) Feed(c *gin.Context) {
	uid := uint(0)
	if bearerToken(c) != "" {
		if !validateAppBearer(c, false) {
			return
		}
		uid = currentUserId(c)
	}
	respondCommunityFeed(c, communityFeedQuery(c.Query("topic"), c.Query("city"), c.Query("district")), uid)
}

func (a *CatCommunityApi) FollowingFeed(c *gin.Context) {
	uid := currentUserId(c)
	followedAuthors := global.GVA_DB.Model(&catlink.CatUserFollow{}).
		Select("target_id").Where("user_id = ?", uid)
	respondCommunityFeed(c, communityFeedQuery(c.Query("topic"), "", "").Where("author_id IN (?)", followedAuthors), uid)
}

// ---------- 广告 ----------
type CatAdApi struct{}

func (a *CatAdApi) Config(c *gin.Context) {
	slot := c.Query("slot")
	if slot == "" {
		slot = "launch"
	}
	now := time.Now()
	var ads []catlink.CatAd
	global.GVA_DB.Where("slot = ? AND enabled = ? AND (on_time IS NULL OR on_time <= ?) AND (off_time IS NULL OR off_time >= ?)",
		slot, true, now, now).Order("created_at desc").Limit(1).Find(&ads)
	views := make([]gin.H, 0, len(ads))
	for _, ad := range ads {
		var onTime, offTime *int64
		if ad.OnTime != nil {
			v := ad.OnTime.UnixMilli()
			onTime = &v
		}
		if ad.OffTime != nil {
			v := ad.OffTime.UnixMilli()
			offTime = &v
		}
		heightMode := ad.HeightMode
		if heightMode != "fixed" && heightMode != "ratio" {
			heightMode = "ratio"
		}
		heightValue := ad.HeightValue
		if heightValue <= 0 {
			heightValue = 42.857
		}
		linkURL := ""
		if parsed, err := url.Parse(strings.TrimSpace(ad.LinkURL)); err == nil && parsed.IsAbs() && (parsed.Scheme == "http" || parsed.Scheme == "https") && parsed.Host != "" {
			linkURL = parsed.String()
		}
		views = append(views, gin.H{
			"id": strconv.FormatUint(uint64(ad.ID), 10), "slot": ad.Slot, "name": ad.Name,
			"imageUrl": ad.ImageURL, "linkUrl": linkURL, "onTime": onTime, "offTime": offTime,
			"heightMode": heightMode, "heightValue": heightValue,
		})
	}
	ok(c, views)
}

// ---------- 版本 ----------
type CatVersionApi struct{}

func safeHTTPSURL(value string) string {
	parsed, err := url.Parse(strings.TrimSpace(value))
	if err != nil || !parsed.IsAbs() || parsed.Scheme != "https" || parsed.Host == "" {
		return ""
	}
	return parsed.String()
}

func (a *CatVersionApi) Check(c *gin.Context) {
	cur, _ := strconv.Atoi(c.DefaultQuery("current", "0"))
	var v catlink.CatVersion
	global.GVA_DB.Order("version_code desc").First(&v)
	hasUpdate := v.ID > 0 && cur < v.VersionCode
	latest := gin.H(nil)
	if v.ID > 0 {
		latest = gin.H{"versionName": v.VersionName, "versionCode": v.VersionCode, "updateContent": v.Content, "downloadUrl": safeHTTPSURL(v.DownloadURL), "forced": 0}
	}
	ok(c, gin.H{"hasUpdate": hasUpdate, "latest": latest, "current": cur})
}
