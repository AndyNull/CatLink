package catlink

import (
	"bytes"
	"encoding/base64"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	catmodel "github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	mediamodel "github.com/flipped-aurora/gin-vue-admin/server/model/media"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	"github.com/gin-gonic/gin"
	"github.com/glebarez/sqlite"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

func TestUploadMediaAcceptsStreamingMultipart(t *testing.T) {
	oldDB, oldConfig := global.GVA_DB, global.GVA_CONFIG
	t.Cleanup(func() { global.GVA_DB, global.GVA_CONFIG = oldDB, oldConfig })
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, db.AutoMigrate(&catmodel.CatMedia{}, &catmodel.CatMediaUploadWindow{}, &system.SysUser{}))
	require.NoError(t, db.Create(&system.SysUser{GVA_MODEL: global.GVA_MODEL{ID: 3}, Username: "multipart-owner"}).Error)
	global.GVA_DB = db
	global.GVA_CONFIG.Local.Path = "/uploads"
	global.GVA_CONFIG.Local.StorePath = t.TempDir()

	png := append([]byte("\x89PNG\r\n\x1a\n"), make([]byte, 1024)...)
	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)
	require.NoError(t, writer.WriteField("category", "post"))
	require.NoError(t, writer.WriteField("temporary", "true"))
	part, err := writer.CreateFormFile("file", "rescue.png")
	require.NoError(t, err)
	_, err = part.Write(png)
	require.NoError(t, err)
	require.NoError(t, writer.Close())

	recorder := httptest.NewRecorder()
	ctx, _ := gin.CreateTestContext(recorder)
	ctx.Request = httptest.NewRequest(http.MethodPost, "/api/v1/media/upload", body)
	ctx.Request.Header.Set("Content-Type", writer.FormDataContentType())
	ctx.Set("catlink_uid", uint(3))
	CatApiGroup.UploadMedia(ctx)
	require.Contains(t, recorder.Body.String(), `"code":0`)

	var row catmodel.CatMedia
	require.NoError(t, db.First(&row).Error)
	require.Equal(t, "post", row.Category)
	require.True(t, row.Temporary)
	require.EqualValues(t, len(png), row.Size)
	stored := filepath.Join(global.GVA_CONFIG.Local.StorePath, filepath.FromSlash(row.StorageKey))
	require.Equal(t, png, mustReadFile(t, stored))
}

func mustReadFile(t *testing.T, path string) []byte {
	t.Helper()
	data, err := os.ReadFile(path)
	require.NoError(t, err)
	return data
}

func TestContractMediaIsPrivateAndOwnerDownloadIsAuthorized(t *testing.T) {
	oldDB, oldConfig := global.GVA_DB, global.GVA_CONFIG
	t.Cleanup(func() { global.GVA_DB, global.GVA_CONFIG = oldDB, oldConfig })
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, db.AutoMigrate(&catmodel.CatMedia{}, &catmodel.CatMediaUploadWindow{}, &system.SysUser{}))
	require.NoError(t, db.Create(&system.SysUser{GVA_MODEL: global.GVA_MODEL{ID: 3}, Username: "contract-owner"}).Error)
	global.GVA_DB = db
	storeRoot := t.TempDir()
	global.GVA_CONFIG.Local.Path = "/uploads"
	global.GVA_CONFIG.Local.StorePath = storeRoot
	pdf := []byte("%PDF-private-contract")
	row, err := saveMedia(3, "contract", "contract.pdf", base64.StdEncoding.EncodeToString(pdf), false)
	require.NoError(t, err)
	require.Equal(t, "/api/v1/media/1", row.URL)
	require.True(t, strings.HasPrefix(row.StorageKey, ".catlink-private/catlink/contract/"))
	_, err = os.Stat(filepath.Join(storeRoot, filepath.FromSlash(row.StorageKey)))
	require.NoError(t, err)

	ownerRecorder := httptest.NewRecorder()
	ownerContext, _ := gin.CreateTestContext(ownerRecorder)
	ownerContext.Request = httptest.NewRequest(http.MethodGet, row.URL, nil)
	ownerContext.Params = gin.Params{{Key: "id", Value: "1"}}
	ownerContext.Set("catlink_uid", uint(3))
	CatApiGroup.DownloadPrivateMedia(ownerContext)
	require.Equal(t, http.StatusOK, ownerRecorder.Code)
	require.Equal(t, pdf, ownerRecorder.Body.Bytes())
	require.Equal(t, "private, no-store", ownerRecorder.Header().Get("Cache-Control"))

	otherRecorder := httptest.NewRecorder()
	otherContext, _ := gin.CreateTestContext(otherRecorder)
	otherContext.Request = httptest.NewRequest(http.MethodGet, row.URL, nil)
	otherContext.Params = gin.Params{{Key: "id", Value: "1"}}
	otherContext.Set("catlink_uid", uint(4))
	CatApiGroup.DownloadPrivateMedia(otherContext)
	require.Contains(t, otherRecorder.Body.String(), "无权访问")
}

func TestAdminDeletePostQueuesOwnedMediaForCleanup(t *testing.T) {
	db := integrityTestDB(t, &catmodel.CatPost{}, &catmodel.CatComment{}, &catmodel.CatCommentLike{}, &catmodel.CatLike{}, &catmodel.CatBookmark{}, &catmodel.CatMedia{})
	media := catmodel.CatMedia{OwnerID: 7, Category: "post", URL: "/uploads/post.webp", Temporary: false}
	require.NoError(t, db.Create(&media).Error)
	post := catmodel.CatPost{AuthorID: 7, Title: "待删除", MediaIDs: mustJSON([]uint{media.ID}), ImageURL: mustJSON([]string{media.URL})}
	require.NoError(t, db.Create(&post).Error)

	envelope := invokeJSON(t, http.MethodDelete, "/api/v1/moderation/posts/1", "", 0, func(c *gin.Context) {
		c.Params = gin.Params{{Key: "id", Value: "1"}}
		CatApiGroup.DeletePost(c)
	})
	require.Equal(t, float64(0), envelope["code"])
	require.Error(t, db.First(&catmodel.CatPost{}, post.ID).Error)
	var queued catmodel.CatMedia
	require.NoError(t, db.First(&queued, media.ID).Error)
	require.True(t, queued.Temporary)
	require.NotNil(t, queued.ExpiresAt)
}

func TestCreatePostConfirmsTemporaryMedia(t *testing.T) {
	oldDB, oldConfig := global.GVA_DB, global.GVA_CONFIG
	t.Cleanup(func() { global.GVA_DB, global.GVA_CONFIG = oldDB, oldConfig })
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, db.AutoMigrate(&catmodel.CatMedia{}, &catmodel.CatMediaUploadWindow{}, &catmodel.CatPost{}, &system.SysUser{}))
	require.NoError(t, db.Create(&system.SysUser{GVA_MODEL: global.GVA_MODEL{ID: 3}, Username: "post-owner"}).Error)
	global.GVA_DB = db
	global.GVA_CONFIG.Local.Path = "/uploads"
	global.GVA_CONFIG.Local.StorePath = t.TempDir()
	png := append([]byte("\x89PNG\r\n\x1a\n"), make([]byte, 32)...)
	media, err := saveMedia(3, "misc", "photo.png", base64.StdEncoding.EncodeToString(png), true)
	require.NoError(t, err)
	require.True(t, media.Temporary)
	require.NotNil(t, media.ExpiresAt)

	clientID := "aa834ae0-8a7b-4a44-a981-b78c4df896af"
	post := catmodel.CatPost{AuthorID: 3, ClientID: &clientID, Title: "救助记录", Content: "正文", Topic: "rescue", ImageURL: media.URL}
	require.NoError(t, createPostWithLimits(3, &post))
	retry := catmodel.CatPost{AuthorID: 3, ClientID: &clientID, Title: "救助记录", Content: "正文", Topic: "rescue", ImageURL: media.URL}
	require.NoError(t, createPostWithLimits(3, &retry))
	require.Equal(t, post.ID, retry.ID)

	var stored catmodel.CatMedia
	require.NoError(t, db.First(&stored, media.ID).Error)
	require.False(t, stored.Temporary)
	require.Nil(t, stored.ExpiresAt)
	require.NotZero(t, post.ID)
}

func TestCreatePostUsesOrderedMediaIDsAndIsIdempotent(t *testing.T) {
	oldDB, oldConfig := global.GVA_DB, global.GVA_CONFIG
	t.Cleanup(func() { global.GVA_DB, global.GVA_CONFIG = oldDB, oldConfig })
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, db.AutoMigrate(
		&catmodel.CatMedia{}, &catmodel.CatMediaUploadWindow{}, &catmodel.CatPost{},
		&mediamodel.FileUploadAndDownload{}, &system.SysUser{},
	))
	require.NoError(t, db.Create(&system.SysUser{GVA_MODEL: global.GVA_MODEL{ID: 3}, Username: "post-owner"}).Error)
	global.GVA_DB = db
	global.GVA_CONFIG.Local.Path = "/uploads"
	global.GVA_CONFIG.Local.StorePath = t.TempDir()
	png := append([]byte("\x89PNG\r\n\x1a\n"), make([]byte, 32)...)
	one, err := saveMedia(3, "post", "one.png", base64.StdEncoding.EncodeToString(png), true)
	require.NoError(t, err)
	two, err := saveMedia(3, "post", "two.png", base64.StdEncoding.EncodeToString(png), true)
	require.NoError(t, err)
	body := `{"id":"7c86a45b-81a1-4891-93d5-cdf24541fe41","title":"救助记录","body":"正文","topic":"rescue","mediaIds":["2","1"]}`

	first := invokeJSON(t, http.MethodPost, "/api/v1/community/posts", body, 3, CatApiGroup.CreatePost)
	require.Equal(t, float64(0), first["code"])
	second := invokeJSON(t, http.MethodPost, "/api/v1/community/posts", body, 3, CatApiGroup.CreatePost)
	require.Equal(t, float64(0), second["code"])
	conflict := invokeJSON(t, http.MethodPost, "/api/v1/community/posts", strings.Replace(body, "救助记录", "另一条动态", 1), 3, CatApiGroup.CreatePost)
	require.Equal(t, float64(7), conflict["code"])
	require.Equal(t, "发布请求标识已被其他内容使用", conflict["msg"])

	var posts []catmodel.CatPost
	require.NoError(t, db.Find(&posts).Error)
	require.Len(t, posts, 1)
	require.Equal(t, []uint{two.ID, one.ID}, jsonUintList(posts[0].MediaIDs))
	require.Equal(t, two.URL+","+one.URL, posts[0].ImageURL)
	require.Equal(t, []string{two.URL, one.URL}, mediaURLsByIDs(posts[0].MediaIDs, nil))
	var stored []catmodel.CatMedia
	require.NoError(t, db.Order("id asc").Find(&stored).Error)
	for _, media := range stored {
		require.Equal(t, "post", media.Category)
		require.False(t, media.Temporary)
		require.Nil(t, media.ExpiresAt)
	}
	var catalog []mediamodel.FileUploadAndDownload
	require.NoError(t, db.Order("id asc").Find(&catalog).Error)
	require.Len(t, catalog, 2)
	for _, media := range catalog {
		require.Equal(t, "post", media.BizCategory)
		require.Equal(t, "CatLink/post", media.Tag)
	}
}

func TestCreatePostRejectsPermanentOrWrongCategoryMedia(t *testing.T) {
	oldDB := global.GVA_DB
	t.Cleanup(func() { global.GVA_DB = oldDB })
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, db.AutoMigrate(&catmodel.CatMedia{}, &catmodel.CatPost{}, &system.SysUser{}))
	require.NoError(t, db.Create(&system.SysUser{GVA_MODEL: global.GVA_MODEL{ID: 3}, Username: "post-owner"}).Error)
	require.NoError(t, db.Create(&catmodel.CatMedia{OwnerID: 3, Category: "cat", URL: "/cat-photo", Temporary: true}).Error)
	require.NoError(t, db.Create(&catmodel.CatMedia{OwnerID: 3, Category: "post", URL: "/used-post-photo", Temporary: false}).Error)
	global.GVA_DB = db

	wrongCategory := invokeJSON(t, http.MethodPost, "/api/v1/community/posts", `{"title":"记录","body":"正文","imageUrl":"/cat-photo"}`, 3, CatApiGroup.CreatePost)
	require.Equal(t, float64(7), wrongCategory["code"])
	permanent := invokeJSON(t, http.MethodPost, "/api/v1/community/posts", `{"title":"记录","body":"正文","mediaIds":["2"]}`, 3, CatApiGroup.CreatePost)
	require.Equal(t, float64(7), permanent["code"])
	require.Equal(t, "帖子图片已被使用，请重新选择", permanent["msg"])
}

func TestCreatePostRejectsForeignMediaIDWithoutPromotingOwnedMedia(t *testing.T) {
	oldDB, oldConfig := global.GVA_DB, global.GVA_CONFIG
	t.Cleanup(func() { global.GVA_DB, global.GVA_CONFIG = oldDB, oldConfig })
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, db.AutoMigrate(&catmodel.CatMedia{}, &catmodel.CatPost{}, &system.SysUser{}))
	require.NoError(t, db.Create(&system.SysUser{GVA_MODEL: global.GVA_MODEL{ID: 3}, Username: "post-owner"}).Error)
	global.GVA_DB = db
	expires := time.Now().Add(time.Hour)
	require.NoError(t, db.Create(&catmodel.CatMedia{OwnerID: 4, Category: "post", URL: "/foreign", Temporary: true, ExpiresAt: &expires}).Error)

	result := invokeJSON(t, http.MethodPost, "/api/v1/community/posts",
		`{"id":"1b5af742-feb4-4bee-b21e-b81736ba2106","title":"救助记录","body":"正文","topic":"rescue","mediaIds":["1"]}`,
		3, CatApiGroup.CreatePost)

	require.Equal(t, float64(7), result["code"])
	var postCount int64
	require.NoError(t, db.Model(&catmodel.CatPost{}).Count(&postCount).Error)
	require.Zero(t, postCount)
	var media catmodel.CatMedia
	require.NoError(t, db.First(&media, 1).Error)
	require.True(t, media.Temporary)
}

func TestCreatePostRejectsInvalidMediaIDLists(t *testing.T) {
	tests := []struct {
		name string
		ids  string
		msg  string
	}{
		{name: "duplicate", ids: `["1","1"]`, msg: "帖子图片不能重复"},
		{name: "over limit", ids: `["1","2","3","4","5"]`, msg: "帖子最多上传 4 张图片"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := invokeJSON(t, http.MethodPost, "/api/v1/community/posts",
				`{"id":"41929f9f-5843-4870-9106-f4d81e337a44","title":"救助记录","body":"正文","topic":"rescue","mediaIds":`+tt.ids+`}`,
				3, CatApiGroup.CreatePost)

			require.Equal(t, float64(7), result["code"])
			require.Equal(t, tt.msg, result["msg"])
		})
	}
}

func TestLegacyContractMediaMovesOutOfPublicDirectory(t *testing.T) {
	oldDB := global.GVA_DB
	t.Cleanup(func() { global.GVA_DB = oldDB })
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, db.AutoMigrate(&catmodel.CatMedia{}))
	global.GVA_DB = db
	storeRoot := t.TempDir()
	oldKey := "catlink/contract/2026/08/legacy.pdf"
	oldFull := filepath.Join(storeRoot, filepath.FromSlash(oldKey))
	require.NoError(t, os.MkdirAll(filepath.Dir(oldFull), 0o750))
	require.NoError(t, os.WriteFile(oldFull, []byte("%PDF-legacy"), 0o640))
	require.NoError(t, db.Create(&catmodel.CatMedia{OwnerID: 3, Category: "contract", URL: "/uploads/" + oldKey, Size: 11}).Error)

	require.NoError(t, migrateLegacyContractMedia(storeRoot, "/uploads"))
	var row catmodel.CatMedia
	require.NoError(t, db.First(&row).Error)
	require.Equal(t, "/api/v1/media/1", row.URL)
	require.Equal(t, ".catlink-private/"+oldKey, row.StorageKey)
	_, err = os.Stat(oldFull)
	require.ErrorIs(t, err, os.ErrNotExist)
	_, err = os.Stat(filepath.Join(storeRoot, filepath.FromSlash(row.StorageKey)))
	require.NoError(t, err)
}

func TestReconcileRemovesOnlyStaleUntrackedCatLinkFiles(t *testing.T) {
	oldDB := global.GVA_DB
	t.Cleanup(func() { global.GVA_DB = oldDB })
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, db.AutoMigrate(&catmodel.CatMedia{}))
	global.GVA_DB = db
	storeRoot := t.TempDir()
	dir := filepath.Join(storeRoot, "catlink", "misc", "2026", "08")
	require.NoError(t, os.MkdirAll(dir, 0o750))
	tracked := filepath.Join(dir, "tracked.png")
	orphan := filepath.Join(dir, "orphan.png")
	temp := filepath.Join(dir, ".upload-stale.tmp")
	for _, path := range []string{tracked, orphan, temp} {
		require.NoError(t, os.WriteFile(path, []byte("x"), 0o640))
		require.NoError(t, os.Chtimes(path, time.Now().Add(-2*time.Hour), time.Now().Add(-2*time.Hour)))
	}
	require.NoError(t, db.Create(&catmodel.CatMedia{OwnerID: 3, URL: "/uploads/catlink/misc/2026/08/tracked.png", Size: 1}).Error)

	require.NoError(t, reconcileCatLinkMediaFiles(storeRoot, "/uploads"))
	_, err = os.Stat(tracked)
	require.NoError(t, err)
	_, err = os.Stat(orphan)
	require.ErrorIs(t, err, os.ErrNotExist)
	_, err = os.Stat(temp)
	require.ErrorIs(t, err, os.ErrNotExist)
}

func TestSaveMediaRejectsAccountAndHourlyQuotaWithoutWritingFile(t *testing.T) {
	oldDB, oldConfig := global.GVA_DB, global.GVA_CONFIG
	t.Cleanup(func() { global.GVA_DB, global.GVA_CONFIG = oldDB, oldConfig })
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, db.AutoMigrate(&catmodel.CatMedia{}, &catmodel.CatMediaUploadWindow{}, &system.SysUser{}))
	require.NoError(t, db.Create(&system.SysUser{GVA_MODEL: global.GVA_MODEL{ID: 3}, Username: "quota-user"}).Error)
	global.GVA_DB = db
	storePath := t.TempDir()
	global.GVA_CONFIG.Local.Path = "/uploads"
	global.GVA_CONFIG.Local.StorePath = storePath
	png := append([]byte("\x89PNG\r\n\x1a\n"), make([]byte, 32)...)
	encoded := base64.StdEncoding.EncodeToString(png)

	require.NoError(t, db.Create(&catmodel.CatMedia{OwnerID: 3, URL: "/old", Size: maxMediaAccountBytes}).Error)
	_, err = saveMedia(3, "cat", "photo.png", encoded, false)
	var clientErr clientMediaError
	require.ErrorAs(t, err, &clientErr)
	entries, readErr := os.ReadDir(storePath)
	require.NoError(t, readErr)
	require.Empty(t, entries)

	require.NoError(t, db.Where("owner_id = ?", 3).Delete(&catmodel.CatMedia{}).Error)
	rows := make([]catmodel.CatMedia, maxMediaHourlyUploads)
	for i := range rows {
		rows[i] = catmodel.CatMedia{OwnerID: 3, URL: "/hourly", Size: 1, CreatedAt: time.Now()}
	}
	require.NoError(t, db.Create(&rows).Error)
	_, err = saveMedia(3, "cat", "photo.png", encoded, false)
	require.ErrorAs(t, err, &clientErr)
}

func TestSaveMediaSeparatesURLPrefixFromStorePath(t *testing.T) {
	oldDB, oldConfig := global.GVA_DB, global.GVA_CONFIG
	t.Cleanup(func() {
		global.GVA_DB, global.GVA_CONFIG = oldDB, oldConfig
	})
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, db.AutoMigrate(&catmodel.CatMedia{}, &catmodel.CatMediaUploadWindow{}, &system.SysUser{}))
	require.NoError(t, db.Create(&system.SysUser{GVA_MODEL: global.GVA_MODEL{ID: 3}, Username: "media-user"}).Error)
	global.GVA_DB = db
	storePath := t.TempDir()
	global.GVA_CONFIG.Local.Path = "/uploads"
	global.GVA_CONFIG.Local.StorePath = storePath

	png := []byte("\x89PNG\r\n\x1a\n")
	png = append(png, make([]byte, 32)...)
	row, err := saveMedia(3, "cat", "photo.png", base64.StdEncoding.EncodeToString(png), false)
	require.NoError(t, err)
	require.True(t, strings.HasPrefix(row.URL, "/uploads/catlink/cat/"), row.URL)
	require.NotContains(t, row.URL, filepath.ToSlash(storePath))
	rel := strings.TrimPrefix(row.URL, "/uploads/")
	_, err = os.Stat(filepath.Join(storePath, filepath.FromSlash(rel)))
	require.NoError(t, err)
}

func TestDeleteOwnedCatAlbumMediaProtectsPublishedReferences(t *testing.T) {
	oldDB, oldConfig := global.GVA_DB, global.GVA_CONFIG
	t.Cleanup(func() { global.GVA_DB, global.GVA_CONFIG = oldDB, oldConfig })
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, db.AutoMigrate(&catmodel.CatMedia{}, &catmodel.CatMediaUploadWindow{}, &catmodel.CatPublicCat{}, &system.SysUser{}))
	require.NoError(t, db.Create(&system.SysUser{GVA_MODEL: global.GVA_MODEL{ID: 3}, Username: "album-owner"}).Error)
	global.GVA_DB = db
	global.GVA_CONFIG.Local.Path = "/uploads"
	global.GVA_CONFIG.Local.StorePath = t.TempDir()
	png := append([]byte("\x89PNG\r\n\x1a\n"), make([]byte, 32)...)
	media, err := saveMedia(3, "cat", "album.png", base64.StdEncoding.EncodeToString(png), false)
	require.NoError(t, err)

	require.NoError(t, db.Create(&catmodel.CatPublicCat{
		OwnerID: 3, LocalID: "cat-1", Nickname: "小猫", State: "published",
		PhotoURLs: mustJSON([]string{media.URL}), PhotoMediaIDs: mustJSON([]uint{media.ID}),
	}).Error)
	recorder := httptest.NewRecorder()
	ctx, _ := gin.CreateTestContext(recorder)
	ctx.Request = httptest.NewRequest(http.MethodDelete, "/api/v1/media/1", nil)
	ctx.Params = gin.Params{{Key: "id", Value: "1"}}
	ctx.Set("catlink_uid", uint(3))
	CatApiGroup.DeleteTemporaryMedia(ctx)
	require.Contains(t, recorder.Body.String(), "仍被公开档案使用")
	var count int64
	require.NoError(t, db.Model(&catmodel.CatMedia{}).Where("id = ?", media.ID).Count(&count).Error)
	require.EqualValues(t, 1, count)

	require.NoError(t, db.Model(&catmodel.CatPublicCat{}).Where("owner_id = ?", 3).Update("state", "archived").Error)
	recorder = httptest.NewRecorder()
	ctx, _ = gin.CreateTestContext(recorder)
	ctx.Request = httptest.NewRequest(http.MethodDelete, "/api/v1/media/1", nil)
	ctx.Params = gin.Params{{Key: "id", Value: "1"}}
	ctx.Set("catlink_uid", uint(3))
	CatApiGroup.DeleteTemporaryMedia(ctx)
	require.Contains(t, recorder.Body.String(), "\"code\":0")
	require.NoError(t, db.Model(&catmodel.CatMedia{}).Where("id = ?", media.ID).Count(&count).Error)
	require.EqualValues(t, 0, count)
}
