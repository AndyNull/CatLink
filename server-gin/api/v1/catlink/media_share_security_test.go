package catlink

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	catmodel "github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	"github.com/gin-gonic/gin"
	"github.com/glebarez/sqlite"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

func TestInvalidUploadAttemptsAreCountedBeforeBodyParsing(t *testing.T) {
	oldDB := global.GVA_DB
	t.Cleanup(func() { global.GVA_DB = oldDB })
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, db.AutoMigrate(&catmodel.CatMedia{}, &catmodel.CatMediaUploadWindow{}, &system.SysUser{}))
	require.NoError(t, db.Create(&system.SysUser{GVA_MODEL: global.GVA_MODEL{ID: 3}, Username: "attempt-user"}).Error)
	global.GVA_DB = db

	for i := 0; i < maxMediaHourlyUploads; i++ {
		recorder := httptest.NewRecorder()
		context, _ := gin.CreateTestContext(recorder)
		context.Request = httptest.NewRequest(http.MethodPost, "/api/v1/media/upload", strings.NewReader("not-json"))
		context.Request.Header.Set("Content-Type", "application/json")
		context.Set("catlink_uid", uint(3))
		CatApiGroup.UploadMedia(context)
		require.Contains(t, recorder.Body.String(), "请求格式错误")
	}

	recorder := httptest.NewRecorder()
	context, _ := gin.CreateTestContext(recorder)
	context.Request = httptest.NewRequest(http.MethodPost, "/api/v1/media/upload", strings.NewReader("not-json"))
	context.Request.Header.Set("Content-Type", "application/json")
	context.Set("catlink_uid", uint(3))
	CatApiGroup.UploadMedia(context)
	require.Contains(t, recorder.Body.String(), "上传过于频繁")
	var window catmodel.CatMediaUploadWindow
	require.NoError(t, db.First(&window).Error)
	require.Equal(t, maxMediaHourlyUploads, window.AttemptCount)
}

func TestUploadMediaDoesNotExposeStorageErrors(t *testing.T) {
	oldDB, oldConfig := global.GVA_DB, global.GVA_CONFIG
	t.Cleanup(func() {
		global.GVA_DB, global.GVA_CONFIG = oldDB, oldConfig
	})
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, db.AutoMigrate(&catmodel.CatMedia{}, &catmodel.CatMediaUploadWindow{}, &system.SysUser{}))
	require.NoError(t, db.Create(&system.SysUser{GVA_MODEL: global.GVA_MODEL{ID: 3}, Username: "media-user"}).Error)
	global.GVA_DB = db
	blockedStore := filepath.Join(t.TempDir(), "not-a-directory")
	require.NoError(t, os.WriteFile(blockedStore, []byte("blocked"), 0o600))
	global.GVA_CONFIG.Local.Path = "/uploads"
	global.GVA_CONFIG.Local.StorePath = blockedStore

	png := append([]byte("\x89PNG\r\n\x1a\n"), make([]byte, 32)...)
	body, err := json.Marshal(struct {
		Category string `json:"category"`
		Filename string `json:"filename"`
		Base64   string `json:"base64"`
	}{
		Category: "cat",
		Filename: "photo.png",
		Base64:   base64.StdEncoding.EncodeToString(png),
	})
	require.NoError(t, err)
	recorder := httptest.NewRecorder()
	context, _ := gin.CreateTestContext(recorder)
	context.Request = httptest.NewRequest(http.MethodPost, "/api/v1/media/upload", bytes.NewReader(body))
	context.Request.Header.Set("Content-Type", "application/json")
	context.Set("catlink_uid", uint(3))

	CatApiGroup.UploadMedia(context)

	require.Equal(t, http.StatusOK, recorder.Code)
	require.Contains(t, recorder.Body.String(), "上传失败，请稍后重试")
	require.NotContains(t, recorder.Body.String(), filepath.ToSlash(blockedStore))
	require.NotContains(t, recorder.Body.String(), "not a directory")
	var rows int64
	require.NoError(t, db.Model(&catmodel.CatMedia{}).Count(&rows).Error)
	require.Zero(t, rows)
}
