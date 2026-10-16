package catlink

import (
	"encoding/json"
	"errors"
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

func publicCatView(row catlink.CatPublicCat) gin.H {
	photos := mediaURLsByIDs(row.PhotoMediaIDs, jsonStringList(row.PhotoURLs))
	cover := row.CoverURL
	if row.CoverMediaID > 0 {
		cover = mediaURLsByIDs(mustJSON([]uint{row.CoverMediaID}), []string{cover})[0]
	}
	return gin.H{
		"id": strconv.FormatUint(uint64(row.ID), 10), "nickname": row.Nickname, "alias": row.Alias, "cityCode": row.CityCode,
		"districtCode": row.DistrictCode, "story": row.Story, "gender": row.Gender, "ageBand": row.AgeBand,
		"coat": row.Coat, "personalityTags": row.Personality, "healthSummary": row.HealthSummary, "statusCode": row.Status,
		"weight": row.Weight, "dietRestrictions": row.Diet, "contactWithHumans": row.HumanContact,
		"contactWithCats": row.CatContact, "catStatus": row.CatStatus, "statusReason": row.StatusReason,
		"archiveReason":       row.ArchiveReason,
		"rescuedDateEpochDay": row.RescuedDate, "statusDateEpochDay": row.StatusDate,
		"coverUrl": cover, "coverMediaId": row.CoverMediaID, "photoMediaIds": jsonUintList(row.PhotoMediaIDs), "ownerAccountId": strconv.FormatUint(uint64(row.OwnerID), 10),
		"photoUrls":   photos,
		"followCount": row.FollowCount, "locationName": row.LocationName, "updatedAt": row.UpdatedAt.UnixMilli(),
	}
}

func canonicalPostCatRef(row catlink.CatPost) string {
	ref := strings.TrimSpace(row.CatRef)
	if ref == "" {
		return ""
	}
	var publicCat catlink.CatPublicCat
	q := global.GVA_DB.Select("id").Where("owner_id = ? AND state = ?", row.AuthorID, "published")
	if id, err := strconv.ParseUint(ref, 10, 64); err == nil && id > 0 {
		q = q.Where("id = ?", uint(id))
	} else {
		q = q.Where("local_id = ?", ref)
	}
	if q.First(&publicCat).Error == nil {
		return strconv.FormatUint(uint64(publicCat.ID), 10)
	}
	return ""
}

func nullablePostCatRef(row catlink.CatPost) any {
	if ref := canonicalPostCatRef(row); ref != "" {
		return ref
	}
	return nil
}

func postView(row catlink.CatPost) gin.H {
	var user system.SysUser
	global.GVA_DB.Select("id,nick_name").First(&user, row.AuthorID)
	return gin.H{
		"id": strconv.FormatUint(uint64(row.ID), 10), "topic": row.Topic, "authorName": user.NickName,
		"title": row.Title, "body": row.Content, "authorAccountId": strconv.FormatUint(uint64(row.AuthorID), 10),
		"cityCode": row.CityCode, "districtCode": row.DistrictCode, "mediaUrls": strings.Join(mediaURLsByIDs(row.MediaIDs, postMediaURLs(row.ImageURL)), ","), "mediaIds": jsonUintList(row.MediaIDs), "catRef": nullablePostCatRef(row),
		"liked": false, "likeCount": row.LikeCount, "commentCount": row.CommentCount,
		"createdAt": row.CreatedAt.UnixMilli(),
	}
}

func jsonStringList(value string) []string {
	var items []string
	_ = json.Unmarshal([]byte(value), &items)
	return items
}

func jsonUintList(value string) []uint {
	var items []uint
	_ = json.Unmarshal([]byte(value), &items)
	return items
}

func mustJSON(value any) string {
	data, _ := json.Marshal(value)
	return string(data)
}

func mediaURLsByIDs(value string, fallback []string) []string {
	ids := jsonUintList(value)
	if len(ids) == 0 {
		return fallback
	}
	var rows []catlink.CatMedia
	global.GVA_DB.Where("id IN ?", ids).Find(&rows)
	byID := make(map[uint]string, len(rows))
	for _, row := range rows {
		byID[row.ID] = row.URL
	}
	urls := make([]string, 0, len(ids))
	for _, id := range ids {
		if url := byID[id]; url != "" {
			urls = append(urls, url)
		}
	}
	if len(urls) == 0 {
		return fallback
	}
	return urls
}

func commentView(row catlink.CatComment, user system.SysUser, replyToName string, liked bool, replyCount int) gin.H {
	authorName := strings.TrimSpace(user.NickName)
	if authorName == "" {
		authorName = strings.TrimSpace(user.Username)
	}
	if authorName == "" {
		authorName = "CatLink 用户"
	}
	var replyToID any
	if row.ReplyToID > 0 {
		replyToID = strconv.FormatUint(uint64(row.ReplyToID), 10)
	}
	return gin.H{
		"id": strconv.FormatUint(uint64(row.ID), 10), "postId": strconv.FormatUint(uint64(row.PostID), 10),
		"authorAccountId": strconv.FormatUint(uint64(row.AuthorID), 10), "authorName": authorName,
		"authorAvatar": strings.TrimSpace(user.HeaderImg), "body": row.Body, "replyToId": replyToID,
		"replyToName": replyToName, "liked": liked, "likeCount": row.LikeCount,
		"replyCount": replyCount, "createdAt": row.CreatedAt.UnixMilli(),
	}
}

func publicPostByID(value string) (catlink.CatPost, error) {
	var row catlink.CatPost
	id, err := strconv.ParseUint(strings.TrimSpace(value), 10, 64)
	if err != nil || id == 0 {
		return row, gorm.ErrRecordNotFound
	}
	return row, global.GVA_DB.Where("id = ? AND locked = ?", uint(id), false).First(&row).Error
}

func (a *CatCommunityApi) DetailPost(c *gin.Context) {
	row, err := publicPostByID(c.Param("postId"))
	if err != nil {
		fail(c, "帖子不存在")
		return
	}
	data := postView(row)
	if token := bearerToken(c); token != "" {
		if claims, err := catJWT().ParseTokenFor(token, catLinkAppIssuer, catLinkAppIssuer); err == nil {
			var n int64
			global.GVA_DB.Model(&catlink.CatLike{}).Where("user_id=? AND post_id=?", claims.BaseClaims.ID, row.ID).Count(&n)
			data["liked"] = n > 0
			data["isOwner"] = claims.BaseClaims.ID == row.AuthorID
		}
	}
	ok(c, data)
}

func (a *CatCommunityApi) ToggleBookmark(c *gin.Context) {
	uid := currentUserId(c)
	post, err := publicPostByID(c.Param("postId"))
	if err != nil {
		fail(c, "帖子不存在")
		return
	}
	bookmarked := false
	err = global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		var locked catlink.CatPost
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Select("id").Where("id = ? AND locked = ?", post.ID, false).First(&locked).Error; err != nil {
			return err
		}
		var row catlink.CatBookmark
		err := tx.Where("user_id = ? AND post_id = ?", uid, post.ID).First(&row).Error
		switch {
		case err == nil:
			return tx.Delete(&row).Error
		case errors.Is(err, gorm.ErrRecordNotFound):
			bookmarked = true
			return tx.Create(&catlink.CatBookmark{UserID: uid, PostID: post.ID}).Error
		default:
			return err
		}
	})
	if err != nil {
		failDB(c, "收藏操作失败，请稍后重试")
		return
	}
	ok(c, gin.H{"bookmarked": bookmarked})
}

type publicCatInput struct {
	ID              string   `json:"id"`
	Nickname        string   `json:"nickname"`
	Alias           string   `json:"alias"`
	Story           string   `json:"story"`
	Gender          string   `json:"gender"`
	AgeBand         string   `json:"ageBand"`
	Coat            string   `json:"coat"`
	PersonalityTags string   `json:"personalityTags"`
	HealthSummary   string   `json:"healthSummary"`
	Weight          string   `json:"weight"`
	Diet            string   `json:"dietRestrictions"`
	HumanContact    string   `json:"contactWithHumans"`
	CatContact      string   `json:"contactWithCats"`
	CatStatus       string   `json:"catStatus"`
	StatusReason    string   `json:"statusReason"`
	ArchiveReason   string   `json:"archiveReason"`
	RescuedDate     int64    `json:"rescuedDateEpochDay"`
	StatusDate      *int64   `json:"statusDateEpochDay"`
	StatusCode      string   `json:"statusCode"`
	CityCode        string   `json:"cityCode"`
	DistrictCode    string   `json:"districtCode"`
	CoverURL        string   `json:"coverUrl"`
	PhotoURLs       []string `json:"photoUrls"`
}

func publicCatByRef(ref string, ownerID uint, publishedOnly bool) (catlink.CatPublicCat, error) {
	var row catlink.CatPublicCat
	q := global.GVA_DB.Model(&catlink.CatPublicCat{})
	if id, err := strconv.ParseUint(strings.TrimSpace(ref), 10, 64); err == nil && id > 0 {
		q = q.Where("id = ?", uint(id))
	} else {
		q = q.Where("local_id = ?", strings.TrimSpace(ref))
	}
	if ownerID > 0 {
		q = q.Where("owner_id = ?", ownerID)
	}
	if publishedOnly {
		q = q.Where("state = ?", "published")
	}
	return row, q.First(&row).Error
}

func confirmPublicCatMedia(tx *gorm.DB, ownerID uint, coverURL string, photoURLs []string) error {
	urls := postMediaURLs(strings.Join(append(photoURLs, coverURL), ","))
	if len(urls) == 0 {
		return nil
	}
	var media []catlink.CatMedia
	if err := tx.Where("owner_id = ? AND category = ? AND url IN ?", ownerID, "cat", urls).Find(&media).Error; err != nil {
		return err
	}
	if len(media) != len(urls) {
		return clientMediaError("公开相册包含无效或无权使用的照片")
	}
	for _, item := range media {
		if item.Temporary && item.ExpiresAt != nil && item.ExpiresAt.Before(time.Now()) {
			return clientMediaError("公开相册照片已过期，请重新选择")
		}
	}
	return tx.Model(&catlink.CatMedia{}).
		Where("owner_id = ? AND category = ? AND url IN ?", ownerID, "cat", urls).
		Updates(map[string]any{"temporary": false, "expires_at": nil}).Error
}

func mediaIDsForURLs(tx *gorm.DB, ownerID uint, urls []string) ([]uint, error) {
	urls = postMediaURLs(strings.Join(urls, ","))
	if len(urls) == 0 {
		return nil, nil
	}
	var media []catlink.CatMedia
	if err := tx.Where("owner_id = ? AND url IN ?", ownerID, urls).Find(&media).Error; err != nil {
		return nil, err
	}
	byURL := make(map[string]uint, len(media))
	for _, item := range media {
		byURL[item.URL] = item.ID
	}
	ids := make([]uint, 0, len(media))
	for _, url := range urls {
		if id := byURL[url]; id > 0 {
			ids = append(ids, id)
		}
	}
	return ids, nil
}

func (a *CatPublicCatApi) Publish(c *gin.Context) {
	uid := currentUserId(c)
	var body publicCatInput
	_ = c.ShouldBindJSON(&body)
	if body.Nickname == "" {
		fail(c, "猫咪昵称不能为空")
		return
	}
	status, archiveReason, err := validateCatStatusInput(body.StatusCode, body.ArchiveReason)
	if err != nil {
		fail(c, err.Error())
		return
	}
	localID := strings.TrimSpace(body.ID)
	row := catlink.CatPublicCat{
		OwnerID: uid, LocalID: localID, Nickname: body.Nickname, Alias: body.Alias, Story: body.Story,
		Gender: body.Gender, AgeBand: body.AgeBand, Coat: body.Coat,
		Personality: body.PersonalityTags, HealthSummary: body.HealthSummary,
		Weight: body.Weight, Diet: body.Diet, HumanContact: body.HumanContact, CatContact: body.CatContact,
		CatStatus: catStatusLabels[status], StatusReason: body.StatusReason, ArchiveReason: archiveReason, RescuedDate: body.RescuedDate, StatusDate: body.StatusDate,
		CityCode: body.CityCode, DistrictCode: body.DistrictCode, Status: status,
		State: "published", CoverURL: body.CoverURL, CreatedAt: time.Now(), UpdatedAt: time.Now(),
	}
	photos, err := json.Marshal(body.PhotoURLs)
	if err != nil {
		fail(c, "公开相册参数无效")
		return
	}
	row.PhotoURLs = string(photos)
	var notifications []catlink.CatNotification
	err = global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		if err := confirmPublicCatMedia(tx, uid, body.CoverURL, body.PhotoURLs); err != nil {
			return err
		}
		photoIDs, err := mediaIDsForURLs(tx, uid, body.PhotoURLs)
		if err != nil {
			return err
		}
		coverIDs, err := mediaIDsForURLs(tx, uid, []string{body.CoverURL})
		if err != nil {
			return err
		}
		row.PhotoMediaIDs = mustJSON(photoIDs)
		if len(coverIDs) > 0 {
			row.CoverMediaID = coverIDs[0]
		}
		if localID != "" {
			var existing catlink.CatPublicCat
			if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Where("owner_id = ? AND local_id = ?", uid, localID).First(&existing).Error; err == nil {
				from, _, known := normalizeCatStatus(existing.Status)
				if !known || !canChangeCatStatus(from, status, existing.ArchiveReason) {
					return catStatusError("当前状态不允许变更为" + catStatusLabels[status])
				}
				row.ID = existing.ID
				row.CreatedAt = existing.CreatedAt
				if err := tx.Model(&existing).Updates(&row).Error; err != nil {
					return err
				}
				var closeErr error
				notifications, closeErr = closeActiveAdoptionsForCat(tx, row, uid, status, row.UpdatedAt)
				if closeErr != nil {
					return closeErr
				}
				return tx.First(&row, existing.ID).Error
			} else if !errors.Is(err, gorm.ErrRecordNotFound) {
				return err
			}
		}
		return tx.Create(&row).Error
	})
	if err != nil {
		var clientErr clientMediaError
		var statusErr catStatusError
		if errors.As(err, &clientErr) {
			fail(c, clientErr.Error())
		} else if errors.As(err, &statusErr) {
			fail(c, statusErr.Error())
		} else {
			failDB(c, "发布失败，请稍后重试")
		}
		return
	}
	for _, notification := range notifications {
		publishCatNotification(notification)
	}
	ok(c, publicCatView(row))
}

func (a *CatPublicCatApi) UpdatePublic(c *gin.Context) {
	uid := currentUserId(c)
	row, err := publicCatByRef(c.Param("id"), uid, false)
	if err != nil {
		fail(c, "档案不存在或无权修改")
		return
	}
	var body publicCatInput
	_ = c.ShouldBindJSON(&body)
	status, archiveReason, err := validateCatStatusInput(body.StatusCode, body.ArchiveReason)
	if err != nil {
		fail(c, err.Error())
		return
	}
	updates := map[string]any{
		"nickname": body.Nickname, "alias": body.Alias, "story": body.Story, "gender": body.Gender,
		"age_band": body.AgeBand, "coat": body.Coat, "personality": body.PersonalityTags,
		"health_summary": body.HealthSummary, "weight": body.Weight, "diet": body.Diet,
		"human_contact": body.HumanContact, "cat_contact": body.CatContact, "cat_status": catStatusLabels[status],
		"status_reason": body.StatusReason, "archive_reason": archiveReason, "rescued_date": body.RescuedDate, "status_date": body.StatusDate,
		"city_code": body.CityCode, "district_code": body.DistrictCode, "status": status,
		"cover_url": body.CoverURL, "state": "published", "updated_at": time.Now(),
	}
	if photos, err := json.Marshal(body.PhotoURLs); err == nil {
		updates["photo_urls"] = string(photos)
	}
	var notifications []catlink.CatNotification
	err = global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		var locked catlink.CatPublicCat
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).First(&locked, row.ID).Error; err != nil {
			return err
		}
		from, _, known := normalizeCatStatus(locked.Status)
		if !known || !canChangeCatStatus(from, status, locked.ArchiveReason) {
			return catStatusError("当前状态不允许变更为" + catStatusLabels[status])
		}
		if err := confirmPublicCatMedia(tx, uid, body.CoverURL, body.PhotoURLs); err != nil {
			return err
		}
		photoIDs, err := mediaIDsForURLs(tx, uid, body.PhotoURLs)
		if err != nil {
			return err
		}
		coverIDs, err := mediaIDsForURLs(tx, uid, []string{body.CoverURL})
		if err != nil {
			return err
		}
		updates["photo_media_ids"] = mustJSON(photoIDs)
		updates["cover_media_id"] = uint(0)
		if len(coverIDs) > 0 {
			updates["cover_media_id"] = coverIDs[0]
		}
		if err := tx.Model(&row).Updates(updates).Error; err != nil {
			return err
		}
		updatedCat := locked
		updatedCat.Nickname = body.Nickname
		var closeErr error
		notifications, closeErr = closeActiveAdoptionsForCat(tx, updatedCat, uid, status, updates["updated_at"].(time.Time))
		if closeErr != nil {
			return closeErr
		}
		return tx.First(&row, row.ID).Error
	})
	if err != nil {
		var clientErr clientMediaError
		var statusErr catStatusError
		if errors.As(err, &clientErr) {
			fail(c, clientErr.Error())
		} else if errors.As(err, &statusErr) {
			fail(c, statusErr.Error())
		} else {
			failDB(c, "更新失败，请稍后重试")
		}
		return
	}
	for _, notification := range notifications {
		publishCatNotification(notification)
	}
	ok(c, publicCatView(row))
}
func (a *CatPublicCatApi) ArchivePublic(c *gin.Context) {
	uid := currentUserId(c)
	row, err := publicCatByRef(c.Param("id"), uid, false)
	if err != nil {
		fail(c, "档案不存在或无权修改")
		return
	}
	res := global.GVA_DB.Model(&row).Updates(map[string]any{"state": "archived", "updated_at": time.Now()})
	if res.RowsAffected == 0 {
		fail(c, "档案不存在或无权修改")
		return
	}
	ok(c, gin.H{"ok": true})
}
func (a *CatPublicCatApi) Followed(c *gin.Context) {
	uid := currentUserId(c)
	var cats []catlink.CatPublicCat
	global.GVA_DB.Table("cat_public_cats c").Select("c.*").Joins("JOIN cat_follows f ON f.cat_id=c.id").Where("f.user_id=? AND c.state=?", uid, "published").Order("f.created_at desc").Find(&cats)
	items := make([]gin.H, 0, len(cats))
	for _, row := range cats {
		items = append(items, publicCatView(row))
	}
	ok(c, gin.H{"cats": items})
}
func (a *CatPublicCatApi) Unfollow(c *gin.Context) {
	uid := currentUserId(c)
	publicCat, err := publicCatByRef(c.Param("id"), 0, true)
	if err != nil {
		fail(c, "公开猫档案不存在")
		return
	}
	err = global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		var locked catlink.CatPublicCat
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Select("id").Where("id = ?", publicCat.ID).First(&locked).Error; err != nil {
			return err
		}
		result := tx.Where("user_id = ? AND cat_id = ?", uid, publicCat.ID).Delete(&catlink.CatFollow{})
		if result.Error != nil || result.RowsAffected == 0 {
			return result.Error
		}
		return tx.Model(&catlink.CatPublicCat{}).Where("id = ?", publicCat.ID).
			Update("follow_count", gorm.Expr("CASE WHEN follow_count > 0 THEN follow_count - 1 ELSE 0 END")).Error
	})
	if err != nil {
		failDB(c, "取消关注失败，请稍后重试")
		return
	}
	ok(c, gin.H{"ok": true})
}
