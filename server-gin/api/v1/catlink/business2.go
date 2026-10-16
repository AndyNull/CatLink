package catlink

import (
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	"github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	gvamedia "github.com/flipped-aurora/gin-vue-admin/server/model/media"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	"github.com/gin-gonic/gin"
)

func postMediaURLs(value string) []string {
	parts := strings.FieldsFunc(value, func(r rune) bool { return r == ',' || r == '|' })
	seen := make(map[string]struct{}, len(parts))
	urls := make([]string, 0, len(parts))
	for _, part := range parts {
		url := strings.TrimSpace(part)
		if url == "" {
			continue
		}
		if _, exists := seen[url]; exists {
			continue
		}
		seen[url] = struct{}{}
		urls = append(urls, url)
	}
	return urls
}

func samePostRequest(existing, requested catlink.CatPost) bool {
	if existing.Title != requested.Title || existing.Content != requested.Content || existing.Topic != requested.Topic ||
		existing.CatRef != requested.CatRef || existing.CityCode != requested.CityCode || existing.DistrictCode != requested.DistrictCode {
		return false
	}
	existingIDs, requestedIDs := jsonUintList(existing.MediaIDs), jsonUintList(requested.MediaIDs)
	if len(requestedIDs) > 0 {
		return mustJSON(existingIDs) == mustJSON(requestedIDs)
	}
	return strings.Join(postMediaURLs(existing.ImageURL), "|") == strings.Join(postMediaURLs(requested.ImageURL), "|")
}

func createPostWithLimits(uid uint, post *catlink.CatPost) error {
	return global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		var user system.SysUser
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Select("id").First(&user, uid).Error; err != nil {
			return err
		}
		if post.ClientID != nil {
			var existing catlink.CatPost
			if err := tx.Where("author_id = ? AND client_id = ?", uid, *post.ClientID).First(&existing).Error; err == nil {
				if !samePostRequest(existing, *post) {
					return clientMediaError("发布请求标识已被其他内容使用")
				}
				*post = existing
				return nil
			} else if !errors.Is(err, gorm.ErrRecordNotFound) {
				return err
			}
		}
		var recentMinute, recentDay int64
		if err := tx.Model(&catlink.CatPost{}).Where("author_id = ? AND created_at > ?", uid, time.Now().Add(-time.Minute)).Count(&recentMinute).Error; err != nil {
			return err
		}
		if err := tx.Model(&catlink.CatPost{}).Where("author_id = ? AND created_at > ?", uid, time.Now().Add(-24*time.Hour)).Count(&recentDay).Error; err != nil {
			return err
		}
		if recentMinute >= 10 || recentDay >= 200 {
			return clientMediaError("发布过于频繁，请稍后再试")
		}
		mediaIDs := jsonUintList(post.MediaIDs)
		mediaURLs := postMediaURLs(post.ImageURL)
		if len(mediaIDs) > 0 || len(mediaURLs) > 0 {
			var media []catlink.CatMedia
			if len(mediaIDs) > 0 {
				if err := tx.Where("owner_id = ? AND id IN ? AND category = ?", uid, mediaIDs, "post").Find(&media).Error; err != nil {
					return err
				}
				if len(media) != len(mediaIDs) {
					return clientMediaError("帖子图片不存在或不属于当前账号")
				}
				byID := make(map[uint]catlink.CatMedia, len(media))
				for _, item := range media {
					byID[item.ID] = item
				}
				media = media[:0]
				mediaURLs = mediaURLs[:0]
				for _, id := range mediaIDs {
					item, exists := byID[id]
					if !exists {
						return clientMediaError("帖子图片不存在或不属于当前账号")
					}
					media = append(media, item)
					mediaURLs = append(mediaURLs, item.URL)
				}
			} else {
				if err := tx.Where("owner_id = ? AND url IN ? AND category IN ?", uid, mediaURLs, []string{"post", "misc"}).Find(&media).Error; err != nil {
					return err
				}
				if len(media) != len(mediaURLs) {
					return clientMediaError("帖子图片不存在或不属于当前账号")
				}
				byURL := make(map[string]catlink.CatMedia, len(media))
				for _, item := range media {
					byURL[item.URL] = item
				}
				media = media[:0]
				mediaIDs = mediaIDs[:0]
				for _, mediaURL := range mediaURLs {
					item := byURL[mediaURL]
					media = append(media, item)
					mediaIDs = append(mediaIDs, item.ID)
				}
			}
			post.ImageURL = strings.Join(mediaURLs, ",")
			post.MediaIDs = mustJSON(mediaIDs)
			for _, item := range media {
				if !item.Temporary {
					return clientMediaError("帖子图片已被使用，请重新选择")
				}
				if item.Temporary && item.ExpiresAt != nil && item.ExpiresAt.Before(time.Now()) {
					return clientMediaError("帖子图片已过期，请重新上传")
				}
			}
			if err := tx.Model(&catlink.CatMedia{}).Where("owner_id = ? AND id IN ?", uid, mediaIDs).
				Updates(map[string]any{"temporary": false, "expires_at": nil, "category": "post"}).Error; err != nil {
				return err
			}
			keys := make([]string, 0, len(media))
			for _, item := range media {
				keys = append(keys, item.StorageKey)
			}
			if tx.Migrator().HasTable(&gvamedia.FileUploadAndDownload{}) {
				if err := tx.Model(&gvamedia.FileUploadAndDownload{}).Where("`key` IN ?", keys).
					Updates(map[string]any{"biz_category": "post", "tag": "CatLink/post"}).Error; err != nil {
					return err
				}
			}
		}
		return tx.Create(post).Error
	})
}

// ---------- 社区写操作 ----------
func (a *CatCommunityApi) CreatePost(c *gin.Context) {
	uid := currentUserId(c)
	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, 64<<10)
	var body struct {
		ID           string   `json:"id"`
		Title        string   `json:"title"`
		Content      string   `json:"content"`
		Body         string   `json:"body"`
		Topic        string   `json:"topic"`
		CatRef       string   `json:"catRef"`
		ImageURL     string   `json:"imageUrl"`
		MediaURLs    string   `json:"mediaUrls"`
		MediaIDs     []string `json:"mediaIds"`
		CityCode     string   `json:"cityCode"`
		DistrictCode string   `json:"districtCode"`
	}
	if err := c.ShouldBindJSON(&body); err != nil {
		fail(c, "请求格式错误或内容过大")
		return
	}
	clientID := strings.TrimSpace(body.ID)
	var clientIDValue *string
	if clientID != "" {
		if _, err := uuid.Parse(clientID); err != nil {
			fail(c, "发布请求标识无效")
			return
		}
		clientIDValue = &clientID
	}
	title := strings.TrimSpace(body.Title)
	content := strings.TrimSpace(body.Content)
	if content == "" {
		content = strings.TrimSpace(body.Body)
	}
	if title == "" || content == "" {
		fail(c, "标题和内容不能为空")
		return
	}
	if len([]rune(title)) > 200 || len([]rune(content)) > 10_000 {
		fail(c, "标题不超过 200 字，正文不超过 10000 字")
		return
	}
	topic := strings.TrimSpace(body.Topic)
	if topic == "" {
		topic = "all"
	}
	if topic != "all" && topic != "rescue" && topic != "seeking_adoption" && topic != "general" {
		fail(c, "帖子话题无效")
		return
	}
	imageURL := strings.TrimSpace(body.ImageURL)
	if imageURL == "" {
		imageURL = strings.TrimSpace(body.MediaURLs)
	}
	mediaIDs := make([]uint, 0, len(body.MediaIDs))
	seenMediaIDs := make(map[uint]struct{}, len(body.MediaIDs))
	for _, rawID := range body.MediaIDs {
		id, err := strconv.ParseUint(strings.TrimSpace(rawID), 10, 64)
		if err != nil || id == 0 {
			fail(c, "帖子图片标识无效")
			return
		}
		mediaID := uint(id)
		if _, exists := seenMediaIDs[mediaID]; exists {
			fail(c, "帖子图片不能重复")
			return
		}
		seenMediaIDs[mediaID] = struct{}{}
		mediaIDs = append(mediaIDs, mediaID)
	}
	mediaURLs := strings.FieldsFunc(imageURL, func(r rune) bool { return r == ',' || r == '|' })
	if len(mediaIDs) > 4 || (len(mediaIDs) == 0 && len(mediaURLs) > 4) {
		fail(c, "帖子最多上传 4 张图片")
		return
	}
	for _, mediaURL := range mediaURLs {
		if len(strings.TrimSpace(mediaURL)) > 500 {
			fail(c, "单张图片地址过长")
			return
		}
	}
	if len(imageURL) > 2200 {
		fail(c, "帖子图片信息过长")
		return
	}
	city := strings.TrimSpace(body.CityCode)
	district := strings.TrimSpace(body.DistrictCode)
	if len([]rune(city)) > 80 || len([]rune(district)) > 80 {
		fail(c, "地区信息过长")
		return
	}
	if district != "" && city == "" {
		fail(c, "选择区县前请先选择城市")
		return
	}
	catRef := strings.TrimSpace(body.CatRef)
	if len(catRef) > 100 {
		fail(c, "关联猫咪标识过长")
		return
	}
	if catRef != "" {
		publicCat, err := publicCatByRef(catRef, uid, true)
		if err != nil {
			fail(c, "关联猫咪不存在、未公开或不属于当前账号")
			return
		}
		catRef = strconv.FormatUint(uint64(publicCat.ID), 10)
	}
	p := catlink.CatPost{
		AuthorID: uid, ClientID: clientIDValue, Title: title, Content: content, Topic: topic,
		CatRef: catRef, ImageURL: imageURL, MediaIDs: mustJSON(mediaIDs),
		CityCode: city, DistrictCode: district,
	}
	if err := createPostWithLimits(uid, &p); err != nil {
		var clientErr clientMediaError
		if errors.As(err, &clientErr) {
			fail(c, clientErr.Error())
		} else {
			failDB(c, "发布失败，请稍后重试")
		}
		return
	}
	ok(c, gin.H{"id": p.ID})
}

func (a *CatCommunityApi) LikePost(c *gin.Context) {
	uid := currentUserId(c)
	post, err := publicPostByID(c.Param("postId"))
	if err != nil {
		fail(c, "帖子不存在")
		return
	}
	liked := false
	err = global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		var locked catlink.CatPost
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Select("id").Where("id = ? AND locked = ?", post.ID, false).First(&locked).Error; err != nil {
			return err
		}
		var like catlink.CatLike
		err := tx.Where("user_id = ? AND post_id = ?", uid, post.ID).First(&like).Error
		switch {
		case err == nil:
			if err := tx.Delete(&like).Error; err != nil {
				return err
			}
			return tx.Model(&catlink.CatPost{}).Where("id = ?", post.ID).
				Update("like_count", gorm.Expr("CASE WHEN like_count > 0 THEN like_count - 1 ELSE 0 END")).Error
		case errors.Is(err, gorm.ErrRecordNotFound):
			if err := tx.Create(&catlink.CatLike{UserID: uid, PostID: post.ID}).Error; err != nil {
				return err
			}
			liked = true
			return tx.Model(&catlink.CatPost{}).Where("id = ?", post.ID).
				Update("like_count", gorm.Expr("like_count + 1")).Error
		default:
			return err
		}
	})
	if err != nil {
		failDB(c, "点赞操作失败，请稍后重试")
		return
	}
	ok(c, gin.H{"liked": liked})
}

func (a *CatCommunityApi) AddComment(c *gin.Context) {
	uid := currentUserId(c)
	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, 8<<10)
	post, err := publicPostByID(c.Param("postId"))
	if err != nil {
		fail(c, "帖子不存在")
		return
	}
	var body struct {
		Body      string `json:"body"`
		ReplyToID string `json:"replyToId"`
	}
	if err := c.ShouldBindJSON(&body); err != nil {
		fail(c, "请求格式错误或内容过大")
		return
	}
	commentBody := strings.TrimSpace(body.Body)
	if commentBody == "" {
		fail(c, "评论不能为空")
		return
	}
	if len([]rune(commentBody)) > 1000 {
		fail(c, "评论不超过 1000 字")
		return
	}
	var replyTo catlink.CatComment
	if ref := strings.TrimSpace(body.ReplyToID); ref != "" {
		id, parseErr := strconv.ParseUint(ref, 10, 64)
		if parseErr != nil || id == 0 || global.GVA_DB.Where("id = ? AND post_id = ?", uint(id), post.ID).First(&replyTo).Error != nil {
			fail(c, "回复的评论不存在")
			return
		}
	}
	cm := catlink.CatComment{PostID: post.ID, AuthorID: uid, Body: commentBody, ReplyToID: replyTo.ID}
	err = global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		var user system.SysUser
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Select("id").First(&user, uid).Error; err != nil {
			return err
		}
		var recentMinute, recentDay int64
		if err := tx.Model(&catlink.CatComment{}).Where("author_id = ? AND created_at > ?", uid, time.Now().Add(-time.Minute)).Count(&recentMinute).Error; err != nil {
			return err
		}
		if err := tx.Model(&catlink.CatComment{}).Where("author_id = ? AND created_at > ?", uid, time.Now().Add(-24*time.Hour)).Count(&recentDay).Error; err != nil {
			return err
		}
		if recentMinute >= 20 || recentDay >= 500 {
			return clientMediaError("评论过于频繁，请稍后再试")
		}
		if err := tx.Create(&cm).Error; err != nil {
			return err
		}
		result := tx.Model(&catlink.CatPost{}).Where("id = ? AND locked = ?", post.ID, false).
			Update("comment_count", gorm.Expr("comment_count + 1"))
		if result.Error != nil {
			return result.Error
		}
		if result.RowsAffected != 1 {
			return gorm.ErrRecordNotFound
		}
		return nil
	})
	if err != nil {
		var clientErr clientMediaError
		if errors.As(err, &clientErr) {
			fail(c, clientErr.Error())
		} else {
			failDB(c, "评论失败，请稍后重试")
		}
		return
	}
	notifyCommentRecipients(post, cm, replyTo)
	ok(c, gin.H{"id": cm.ID})
}

func (a *CatCommunityApi) Comments(c *gin.Context) {
	post, err := publicPostByID(c.Param("postId"))
	if err != nil {
		fail(c, "帖子不存在")
		return
	}
	uid := uint(0)
	if bearerToken(c) != "" {
		if !validateAppBearer(c, false) {
			return
		}
		uid = currentUserId(c)
	}
	var list []catlink.CatComment
	if err := global.GVA_DB.Where("post_id = ?", post.ID).Order("created_at asc").Limit(300).Find(&list).Error; err != nil {
		failDB(c, "评论加载失败，请稍后重试")
		return
	}
	if uid > 0 && len(list) > 0 {
		authorIDs := make([]uint, 0, len(list))
		for _, item := range list {
			authorIDs = append(authorIDs, item.AuthorID)
		}
		suppressed := suppressedTargets(uid, authorIDs)
		filtered := list[:0]
		for _, item := range list {
			if _, hidden := suppressed[item.AuthorID]; !hidden {
				filtered = append(filtered, item)
			}
		}
		list = filtered
	}
	commentByID := make(map[uint]catlink.CatComment, len(list))
	replyCounts := make(map[uint]int)
	authorIDs := make([]uint, 0, len(list))
	for _, item := range list {
		commentByID[item.ID] = item
		authorIDs = append(authorIDs, item.AuthorID)
		if item.ReplyToID > 0 {
			replyCounts[item.ReplyToID]++
		}
	}
	var users []system.SysUser
	if len(authorIDs) > 0 {
		global.GVA_DB.Select("id", "username", "nick_name", "header_img").Where("id IN ?", authorIDs).Find(&users)
	}
	userByID := make(map[uint]system.SysUser, len(users))
	for _, user := range users {
		userByID[user.ID] = user
	}
	likedCommentIDs := make(map[uint]struct{})
	if uid > 0 && len(list) > 0 {
		var ids []uint
		global.GVA_DB.Model(&catlink.CatCommentLike{}).Where("user_id = ?", uid).Pluck("comment_id", &ids)
		for _, id := range ids {
			likedCommentIDs[id] = struct{}{}
		}
	}
	comments := make([]gin.H, 0, len(list))
	for _, item := range list {
		replyToName := ""
		if parent, found := commentByID[item.ReplyToID]; found {
			replyToName = strings.TrimSpace(userByID[parent.AuthorID].NickName)
			if replyToName == "" {
				replyToName = strings.TrimSpace(userByID[parent.AuthorID].Username)
			}
		}
		_, liked := likedCommentIDs[item.ID]
		comments = append(comments, commentView(item, userByID[item.AuthorID], replyToName, liked, replyCounts[item.ID]))
	}
	ok(c, gin.H{"comments": comments})
}

func (a *CatCommunityApi) LikeComment(c *gin.Context) {
	uid := currentUserId(c)
	id, err := strconv.ParseUint(strings.TrimSpace(c.Param("commentId")), 10, 64)
	if err != nil || id == 0 {
		fail(c, "评论不存在")
		return
	}
	var comment catlink.CatComment
	if global.GVA_DB.First(&comment, uint(id)).Error != nil {
		fail(c, "评论不存在")
		return
	}
	if _, err := publicPostByID(strconv.FormatUint(uint64(comment.PostID), 10)); err != nil {
		fail(c, "评论不存在")
		return
	}
	liked := false
	err = global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).First(&comment, comment.ID).Error; err != nil {
			return err
		}
		var row catlink.CatCommentLike
		err := tx.Where("user_id = ? AND comment_id = ?", uid, comment.ID).First(&row).Error
		switch {
		case err == nil:
			if err := tx.Delete(&row).Error; err != nil {
				return err
			}
			return tx.Model(&comment).Update("like_count", gorm.Expr("CASE WHEN like_count > 0 THEN like_count - 1 ELSE 0 END")).Error
		case errors.Is(err, gorm.ErrRecordNotFound):
			if err := tx.Create(&catlink.CatCommentLike{UserID: uid, CommentID: comment.ID}).Error; err != nil {
				return err
			}
			liked = true
			return tx.Model(&comment).Update("like_count", gorm.Expr("like_count + 1")).Error
		default:
			return err
		}
	})
	if err != nil {
		failDB(c, "评论点赞失败，请稍后重试")
		return
	}
	global.GVA_DB.Select("like_count").First(&comment, comment.ID)
	if liked && comment.AuthorID != uid {
		createCatNotification(catlink.CatNotification{ToUser: comment.AuthorID, Type: "remind", Title: "你的评论收到点赞", Link: "/post/" + strconv.Itoa(int(comment.PostID))})
	}
	ok(c, gin.H{"liked": liked, "likeCount": comment.LikeCount})
}

func notifyCommentRecipients(post catlink.CatPost, comment, replyTo catlink.CatComment) {
	notified := map[uint]bool{comment.AuthorID: true}
	if replyTo.ID > 0 && !notified[replyTo.AuthorID] {
		createCatNotification(catlink.CatNotification{ToUser: replyTo.AuthorID, Type: "remind", Title: "你的评论有新回复", Body: comment.Body, Link: "/post/" + strconv.Itoa(int(post.ID))})
		notified[replyTo.AuthorID] = true
	}
	if !notified[post.AuthorID] {
		createCatNotification(catlink.CatNotification{ToUser: post.AuthorID, Type: "remind", Title: "你的帖子有新评论", Body: comment.Body, Link: "/post/" + strconv.Itoa(int(post.ID))})
	}
}

func notifyPostOwner(postID uint, by uint, title string) {
	var p catlink.CatPost
	if err := global.GVA_DB.First(&p, postID).Error; err == nil && p.AuthorID != by {
		createCatNotification(catlink.CatNotification{ToUser: p.AuthorID, Type: "remind", Title: title,
			Body: "帖子《" + p.Title + "》有了新动态", Link: "/post/" + strconv.Itoa(int(postID))})
	}
}

// ---------- 关注 ----------
func (a *CatPublicCatApi) Follow(c *gin.Context) {
	uid := currentUserId(c)
	publicCat, err := publicCatByRef(c.Param("id"), 0, true)
	if err != nil {
		fail(c, "公开猫档案不存在")
		return
	}
	followed := false
	err = global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		var locked catlink.CatPublicCat
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Select("id").Where("id = ? AND state = ?", publicCat.ID, "published").First(&locked).Error; err != nil {
			return err
		}
		var follow catlink.CatFollow
		err := tx.Where("user_id = ? AND cat_id = ?", uid, publicCat.ID).First(&follow).Error
		switch {
		case err == nil:
			if err := tx.Delete(&follow).Error; err != nil {
				return err
			}
			return tx.Model(&catlink.CatPublicCat{}).Where("id = ?", publicCat.ID).
				Update("follow_count", gorm.Expr("CASE WHEN follow_count > 0 THEN follow_count - 1 ELSE 0 END")).Error
		case errors.Is(err, gorm.ErrRecordNotFound):
			if err := tx.Create(&catlink.CatFollow{UserID: uid, CatID: publicCat.ID}).Error; err != nil {
				return err
			}
			followed = true
			return tx.Model(&catlink.CatPublicCat{}).Where("id = ?", publicCat.ID).
				Update("follow_count", gorm.Expr("follow_count + 1")).Error
		default:
			return err
		}
	})
	if err != nil {
		failDB(c, "关注操作失败，请稍后重试")
		return
	}
	ok(c, gin.H{"followed": followed})
}

// ---------- 举报 ----------
type CatReportApi struct{}

func (a *CatReportApi) Submit(c *gin.Context) {
	uid := currentUserId(c)
	var body struct {
		TargetType string `json:"targetType"`
		TargetID   string `json:"targetId"`
		Reason     string `json:"reason"`
	}
	_ = c.ShouldBindJSON(&body)
	if body.TargetID == "" {
		fail(c, "缺少举报对象")
		return
	}
	var cnt int64
	global.GVA_DB.Model(&catlink.CatReport{}).Where("reporter = ? AND created_at > ?", uid, time.Now().Add(-time.Minute)).Count(&cnt)
	if cnt >= 10 {
		fail(c, "举报过于频繁，请稍后再试")
		return
	}
	if err := global.GVA_DB.Create(&catlink.CatReport{Reporter: uid, TargetType: body.TargetType, TargetID: body.TargetID, Reason: body.Reason}).Error; err != nil {
		fail(c, "提交失败")
		return
	}
	ok(c, gin.H{"id": uid, "msg": "已收到你的举报，我们会尽快处理"})
}

// ---------- 通知 ----------
func (a *CatAuthApi) Notifications(c *gin.Context) {
	uid := currentUserId(c)
	var list []catlink.CatNotification
	global.GVA_DB.Where("to_user = ?", uid).Order("created_at desc").Limit(60).Find(&list)
	items := make([]gin.H, 0, len(list))
	for _, n := range list {
		items = append(items, gin.H{"id": strconv.FormatUint(uint64(n.ID), 10), "kind": n.Type, "title": n.Title, "body": n.Body, "refType": "link", "refId": n.Link, "read": n.ReadFlag, "isRead": n.ReadFlag, "createdAt": n.CreatedAt.UnixMilli()})
	}
	ok(c, gin.H{"items": items})
}

func (a *CatAuthApi) MarkNotifRead(c *gin.Context) {
	uid := currentUserId(c)
	var body struct {
		ID string `json:"id"`
	}
	_ = c.ShouldBindJSON(&body)
	query := global.GVA_DB.Model(&catlink.CatNotification{}).Where("to_user = ? AND read_flag = ?", uid, false)
	if body.ID != "" {
		id, err := strconv.ParseUint(body.ID, 10, 64)
		if err != nil || id == 0 {
			fail(c, "通知标识无效")
			return
		}
		query = query.Where("id = ?", uint(id))
	}
	result := query.Update("read_flag", true)
	if result.Error != nil {
		fail(c, "通知状态更新失败，请稍后重试")
		return
	}
	ok(c, gin.H{"ok": true})
}

// ---------- 分享 ----------
type CatShareApi struct{}

func (a *CatShareApi) CreateShare(c *gin.Context) {
	uid := currentUserId(c)
	var body struct {
		TargetType string `json:"targetType"`
		TargetID   string `json:"targetId"`
	}
	_ = c.ShouldBindJSON(&body)
	sum := sha256.Sum256([]byte(strconv.FormatUint(uint64(uid), 10) + "." + body.TargetType + "." + body.TargetID + "." + strconv.FormatInt(time.Now().UnixNano(), 10)))
	code := hex.EncodeToString(sum[:])[:10]
	createCatNotification(catlink.CatNotification{ToUser: uid, Type: "share", Title: "分享已生成", Body: code, Link: "/" + body.TargetType + "/" + body.TargetID})
	ok(c, gin.H{"code": code, "deepLink": "catlink://share/" + code})
}

var (
	errVaultConflictBudget = errors.New("vault conflict response exceeds budget")
	errVaultAccountQuota   = errors.New("vault account quota exceeded")
)

func validVaultCiphertext(value string) bool {
	parts := strings.Split(value, ":")
	if len(parts) != 2 || parts[0] == "" || parts[1] == "" {
		return false
	}
	decoder := base64.RawStdEncoding.Strict()
	iv, err := decoder.DecodeString(parts[0])
	if err != nil || len(iv) != 12 || base64.RawStdEncoding.EncodeToString(iv) != parts[0] {
		return false
	}
	ciphertext, err := decoder.DecodeString(parts[1])
	return err == nil && len(ciphertext) >= 16 && base64.RawStdEncoding.EncodeToString(ciphertext) == parts[1]
}

// ---------- Vault ----------
func (a *CatAuthApi) VaultSync(c *gin.Context) {
	const maxVaultBody = 8 << 20
	const maxVaultRecords = 100
	const maxCiphertextBytes = 512 << 10
	const maxVaultResponseCiphertextBytes = 6 << 20
	const maxVaultAccountRecords int64 = 50_000
	const maxVaultAccountPayloadBytes int64 = 2 << 30
	allowedTypes := map[string]bool{"cat": true, "medical": true, "adopter": true, "case": true, "contract": true, "followup": true, "timeline": true, "todo": true, "casenote": true, "media": true}

	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxVaultBody)
	uid := currentUserId(c)
	var body struct {
		Cursor  int64 `json:"cursor"`
		Records []struct {
			RecordID   string `json:"recordId"`
			RecordType string `json:"recordType"`
			KeyVersion int    `json:"keyVersion"`
			Revision   int64  `json:"revision"`
			Ciphertext string `json:"ciphertext"`
			Deleted    bool   `json:"deleted"`
		} `json:"records"`
	}
	if err := c.ShouldBindJSON(&body); err != nil || body.Cursor < 0 || len(body.Records) > maxVaultRecords {
		fail(c, "同步请求格式错误或批次过大")
		return
	}
	for _, r := range body.Records {
		invalidCiphertext := (r.Deleted && r.Ciphertext != "") || (!r.Deleted && !validVaultCiphertext(r.Ciphertext))
		if !allowedTypes[r.RecordType] || len(r.RecordID) < 1 || len(r.RecordID) > 100 || r.Revision < 1 || r.KeyVersion < 1 || r.KeyVersion > 100 || len(r.Ciphertext) > maxCiphertextBytes || invalidCiphertext {
			fail(c, "同步记录字段无效")
			return
		}
	}

	acks := make([]gin.H, 0, len(body.Records))
	conflictRecords := make([]catlink.CatVault, 0)
	conflictIDs := make(map[uint]bool, len(body.Records))
	conflictBytes := 0
	var cursor int64
	err := global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		var meta catlink.CatVaultMeta
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Where("account_id = ?", uid).FirstOrCreate(&meta, catlink.CatVaultMeta{AccountID: uid, KeyVersion: 1}).Error; err != nil {
			return err
		}
		for _, r := range body.Records {
			var rec catlink.CatVault
			err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Where("account_id = ? AND entity_type = ? AND entity_id = ?", uid, r.RecordType, r.RecordID).First(&rec).Error
			status := "accepted"
			switch {
			case errors.Is(err, gorm.ErrRecordNotFound):
				newPayloadBytes := int64(len(r.Ciphertext))
				if meta.RecordCount+1 > maxVaultAccountRecords || meta.PayloadBytes+newPayloadBytes > maxVaultAccountPayloadBytes {
					return errVaultAccountQuota
				}
				meta.Revision++
				meta.RecordCount++
				meta.PayloadBytes += newPayloadBytes
				rec = catlink.CatVault{AccountID: uid, EntityType: r.RecordType, EntityID: r.RecordID, Revision: r.Revision, ServerRevision: meta.Revision, KeyVersion: r.KeyVersion, Tombstone: r.Deleted, Payload: r.Ciphertext}
				if err := tx.Create(&rec).Error; err != nil {
					return err
				}
			case err != nil:
				return err
			case rec.Revision == r.Revision && rec.KeyVersion == r.KeyVersion && rec.Tombstone == r.Deleted && rec.Payload == r.Ciphertext:
				status = "already-applied"
			case r.Revision <= rec.Revision:
				status = "conflict"
				if !conflictIDs[rec.ID] {
					conflictBytes += len(rec.Payload)
					if conflictBytes > maxVaultResponseCiphertextBytes {
						return errVaultConflictBudget
					}
					conflictRecords = append(conflictRecords, rec)
					conflictIDs[rec.ID] = true
				}
			default:
				payloadDelta := int64(len(r.Ciphertext) - len(rec.Payload))
				if meta.PayloadBytes+payloadDelta < 0 || meta.PayloadBytes+payloadDelta > maxVaultAccountPayloadBytes {
					return errVaultAccountQuota
				}
				meta.Revision++
				meta.PayloadBytes += payloadDelta
				result := tx.Model(&catlink.CatVault{}).Where("id = ? AND revision = ?", rec.ID, rec.Revision).Updates(map[string]any{
					"revision": r.Revision, "server_revision": meta.Revision, "key_version": r.KeyVersion,
					"tombstone": r.Deleted, "payload": r.Ciphertext, "updated_at": time.Now(),
				})
				if result.Error != nil {
					return result.Error
				}
				if result.RowsAffected != 1 {
					return errors.New("vault record changed concurrently")
				}
			}
			acks = append(acks, gin.H{"recordId": r.RecordID, "recordType": r.RecordType, "revision": r.Revision, "status": status})
		}
		if err := tx.Save(&meta).Error; err != nil {
			return err
		}
		cursor = meta.Revision
		return nil
	})
	if errors.Is(err, errVaultAccountQuota) {
		fail(c, "同步存储已达到账号安全上限，请清理不再需要的数据")
		return
	}
	if errors.Is(err, errVaultConflictBudget) {
		fail(c, "冲突记录超过下行安全上限，请减少同步批次")
		return
	}
	if err != nil {
		fail(c, "同步失败，请稍后重试")
		return
	}

	var changed []catlink.CatVault
	if err := global.GVA_DB.Where("account_id = ? AND server_revision > ?", uid, body.Cursor).Order("server_revision asc").Limit(100).Find(&changed).Error; err != nil {
		fail(c, "读取同步结果失败，请稍后重试")
		return
	}
	conflictBytes = 0
	conflictIDs = make(map[uint]bool, len(conflictRecords))
	for _, row := range conflictRecords {
		if !conflictIDs[row.ID] {
			conflictBytes += len(row.Payload)
			conflictIDs[row.ID] = true
		}
	}
	if conflictBytes > maxVaultResponseCiphertextBytes {
		fail(c, "冲突记录超过下行安全上限，请减少同步批次")
		return
	}
	bounded := changed[:0]
	responseBytes := conflictBytes
	for _, row := range changed {
		additionalBytes := 0
		if !conflictIDs[row.ID] {
			additionalBytes = len(row.Payload)
		}
		if additionalBytes > maxVaultResponseCiphertextBytes {
			fail(c, "单条同步记录超过下行安全上限")
			return
		}
		if responseBytes+additionalBytes > maxVaultResponseCiphertextBytes {
			break
		}
		bounded = append(bounded, row)
		responseBytes += additionalBytes
	}
	changed = bounded
	pullCursor := body.Cursor
	if len(changed) > 0 {
		pullCursor = changed[len(changed)-1].ServerRevision
	}
	seenChanged := make(map[uint]bool, len(changed))
	for _, row := range changed {
		seenChanged[row.ID] = true
	}
	for _, row := range conflictRecords {
		if !seenChanged[row.ID] {
			changed = append(changed, row)
			seenChanged[row.ID] = true
		}
	}
	items := make([]gin.H, 0, len(changed))
	for _, r := range changed {
		items = append(items, gin.H{"recordId": r.EntityID, "recordType": r.EntityType, "keyVersion": r.KeyVersion, "revision": r.Revision, "serverRevision": r.ServerRevision, "ciphertext": r.Payload, "deleted": r.Tombstone})
	}
	responseCursor := pullCursor
	if len(changed) == 0 && cursor < responseCursor {
		responseCursor = cursor
	}
	ok(c, gin.H{"cursor": responseCursor, "records": items, "acks": acks})
}

func (a *CatAuthApi) VaultEnable(c *gin.Context) {
	uid := currentUserId(c)
	var body struct {
		KeyVersion int `json:"keyVersion"`
	}
	_ = c.ShouldBindJSON(&body)
	if body.KeyVersion < 1 {
		body.KeyVersion = 1
	}
	var meta catlink.CatVaultMeta
	global.GVA_DB.Where("account_id = ?", uid).FirstOrCreate(&meta, catlink.CatVaultMeta{AccountID: uid, KeyVersion: body.KeyVersion})
	if body.KeyVersion > meta.KeyVersion {
		global.GVA_DB.Model(&meta).Update("key_version", body.KeyVersion)
		meta.KeyVersion = body.KeyVersion
	}
	ok(c, gin.H{"enabled": true, "cursor": meta.Revision, "keyVersion": meta.KeyVersion})
}
