package middleware

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/config"
	"github.com/flipped-aurora/gin-vue-admin/server/global"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	"github.com/flipped-aurora/gin-vue-admin/server/utils"
	"github.com/flipped-aurora/gin-vue-admin/server/utils/gva_cache"
	"github.com/gin-gonic/gin"
	"github.com/glebarez/sqlite"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

func TestJWTAuthRejectsOldCredentialEpoch(t *testing.T) {
	oldDB, oldConfig, oldCache := global.GVA_DB, global.GVA_CONFIG, global.GVA_CACHE
	t.Cleanup(func() { global.GVA_DB, global.GVA_CONFIG, global.GVA_CACHE = oldDB, oldConfig, oldCache })
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, db.AutoMigrate(&system.SysUser{}))
	global.GVA_DB = db
	global.GVA_CACHE = gva_cache.NewMemoryCache(time.Hour)
	global.GVA_CONFIG.JWT = config.JWT{SigningKey: "jwt-auth-test-signing-key-at-least-32-characters", ExpiresTime: "1h", BufferTime: "5m", Issuer: "JWTAuth-Test"}

	passwordTime := time.Now().Add(-time.Hour).Truncate(time.Microsecond)
	user := system.SysUser{Username: "admin@example.com", NickName: "admin", AuthorityId: 888, Enable: 1, PasswordUpdatedAt: &passwordTime}
	require.NoError(t, db.Create(&user).Error)
	oldToken, _, err := utils.LoginTokenWithExpire(&user, false)
	require.NoError(t, err)

	gin.SetMode(gin.TestMode)
	router := gin.New()
	router.Use(JWTAuth())
	router.GET("/protected", func(c *gin.Context) { c.Status(http.StatusNoContent) })

	request := func(token string) *httptest.ResponseRecorder {
		recorder := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodGet, "/protected", nil)
		req.Header.Set("x-token", token)
		router.ServeHTTP(recorder, req)
		return recorder
	}
	require.Equal(t, http.StatusNoContent, request(oldToken).Code)

	newPasswordTime := time.Now().Truncate(time.Microsecond)
	require.NoError(t, db.Model(&system.SysUser{}).Where("id = ?", user.ID).Update("password_updated_at", newPasswordTime).Error)
	require.Equal(t, http.StatusUnauthorized, request(oldToken).Code)

	user.PasswordUpdatedAt = &newPasswordTime
	newToken, _, err := utils.LoginTokenWithExpire(&user, false)
	require.NoError(t, err)
	require.Equal(t, http.StatusNoContent, request(newToken).Code)
}
