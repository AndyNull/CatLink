package catlink

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	catmodel "github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	"github.com/flipped-aurora/gin-vue-admin/server/utils"
	"github.com/gin-gonic/gin"
	"github.com/glebarez/sqlite"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

func TestLogoutWithRotatedAccessRevokesSuccessorFamily(t *testing.T) {
	oldDB, oldConfig := global.GVA_DB, global.GVA_CONFIG
	t.Cleanup(func() { global.GVA_DB, global.GVA_CONFIG = oldDB, oldConfig })
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, db.AutoMigrate(&system.SysUser{}, &catmodel.CatDeviceSession{}, &catmodel.CatAccountSecurity{}, &catmodel.CatAuthCode{}, &catmodel.CatEmailConfig{}))
	require.NoError(t, db.Create(&catmodel.CatEmailConfig{ID: 1, LoginOTPRequired: false}).Error)
	global.GVA_DB = db
	global.GVA_CONFIG.JWT.SigningKey = "test-signing-key-with-at-least-32-characters"
	global.GVA_CONFIG.JWT.ExpiresTime = "7d"
	global.GVA_CONFIG.JWT.BufferTime = "1d"
	global.GVA_CONFIG.JWT.Issuer = "CatLink-Test"
	setupAppCaptcha(t, appCaptchaTTL)

	user := system.SysUser{Username: "logout-race@example.com", Password: utils.BcryptHash("password123"), NickName: "logout", AuthorityId: 9528, Enable: 1}
	require.NoError(t, db.Create(&user).Error)

	gin.SetMode(gin.TestMode)
	router := gin.New()
	auth := &CatAuthApi{}
	router.POST("/login", auth.Login)
	router.POST("/refresh", auth.Refresh)
	router.POST("/logout", LogoutBearer(), auth.Logout)
	router.GET("/account", AuthBearer(), auth.Account)

	login := httptest.NewRecorder()
	captchaID, captcha := seedAppCaptcha(t)
	router.ServeHTTP(login, httptest.NewRequest(http.MethodPost, "/login", captchaLoginBody(t, user.Username, "password123", captchaID, captcha)))
	var original authResponse
	require.NoError(t, json.Unmarshal(login.Body.Bytes(), &original))
	require.Equal(t, 0, original.Code, login.Body.String())

	refreshBody, err := json.Marshal(map[string]string{"refreshToken": original.Data.RefreshToken})
	require.NoError(t, err)
	refresh := httptest.NewRecorder()
	router.ServeHTTP(refresh, httptest.NewRequest(http.MethodPost, "/refresh", bytes.NewReader(refreshBody)))
	var successor authResponse
	require.NoError(t, json.Unmarshal(refresh.Body.Bytes(), &successor))
	require.Equal(t, 0, successor.Code, refresh.Body.String())

	logoutRequest := httptest.NewRequest(http.MethodPost, "/logout", nil)
	logoutRequest.Header.Set("Authorization", "Bearer "+original.Data.AccessToken)
	logout := httptest.NewRecorder()
	router.ServeHTTP(logout, logoutRequest)
	var logoutEnvelope struct {
		Code int `json:"code"`
	}
	require.NoError(t, json.Unmarshal(logout.Body.Bytes(), &logoutEnvelope))
	require.Equal(t, 0, logoutEnvelope.Code, logout.Body.String())

	for _, access := range []string{original.Data.AccessToken, successor.Data.AccessToken} {
		request := httptest.NewRequest(http.MethodGet, "/account", nil)
		request.Header.Set("Authorization", "Bearer "+access)
		recorder := httptest.NewRecorder()
		router.ServeHTTP(recorder, request)
		var envelope struct {
			Code int `json:"code"`
		}
		require.NoError(t, json.Unmarshal(recorder.Body.Bytes(), &envelope))
		require.Equal(t, 7, envelope.Code, recorder.Body.String())
	}

	successorRefreshBody, err := json.Marshal(map[string]string{"refreshToken": successor.Data.RefreshToken})
	require.NoError(t, err)
	successorRefresh := httptest.NewRecorder()
	router.ServeHTTP(successorRefresh, httptest.NewRequest(http.MethodPost, "/refresh", bytes.NewReader(successorRefreshBody)))
	var refreshEnvelope struct {
		Code int `json:"code"`
	}
	require.NoError(t, json.Unmarshal(successorRefresh.Body.Bytes(), &refreshEnvelope))
	require.Equal(t, 7, refreshEnvelope.Code, successorRefresh.Body.String())

	var active int64
	require.NoError(t, db.Model(&catmodel.CatDeviceSession{}).Where("user_id = ? AND revoked_at IS NULL", user.ID).Count(&active).Error)
	require.Zero(t, active)
}
