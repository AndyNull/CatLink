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

type authResponse struct {
	Code int `json:"code"`
	Data struct {
		AccessToken  string `json:"accessToken"`
		RefreshToken string `json:"refreshToken"`
	} `json:"data"`
}

func TestRefreshTokenIsDistinctAndSingleUse(t *testing.T) {
	oldDB, oldConfig := global.GVA_DB, global.GVA_CONFIG
	t.Cleanup(func() {
		global.GVA_DB, global.GVA_CONFIG = oldDB, oldConfig
	})

	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, db.AutoMigrate(&system.SysUser{}, &catmodel.CatDeviceSession{}, &catmodel.CatAccountSecurity{}, &catmodel.CatAuthCode{}, &catmodel.CatEmailConfig{}))
	require.NoError(t, db.Create(&catmodel.CatEmailConfig{ID: 1, LoginOTPRequired: false}).Error)
	global.GVA_DB = db
	global.GVA_CONFIG.JWT.SigningKey = ""
	unsignedJWT := catJWT()
	global.GVA_CONFIG.JWT.SigningKey = "test-signing-key-with-at-least-32-characters"
	global.GVA_CONFIG.JWT.ExpiresTime = "7d"
	global.GVA_CONFIG.JWT.BufferTime = "1d"
	global.GVA_CONFIG.JWT.Issuer = "CatLink-Test"
	setupAppCaptcha(t, appCaptchaTTL)

	user := system.SysUser{Username: "refresh@example.com", Password: utils.BcryptHash("password123"), NickName: "refresh", AuthorityId: 9528, Enable: 1}
	require.NoError(t, db.Create(&user).Error)

	gin.SetMode(gin.TestMode)
	router := gin.New()
	auth := &CatAuthApi{}
	router.POST("/login", auth.Login)
	router.POST("/refresh", auth.Refresh)

	captchaID, captcha := seedAppCaptcha(t)
	login := httptest.NewRecorder()
	router.ServeHTTP(login, httptest.NewRequest(http.MethodPost, "/login", captchaLoginBody(t, user.Username, "password123", captchaID, captcha)))
	var loginResponse authResponse
	require.NoError(t, json.Unmarshal(login.Body.Bytes(), &loginResponse))
	require.Equal(t, 0, loginResponse.Code)
	require.NotEmpty(t, loginResponse.Data.AccessToken)
	require.NotEmpty(t, loginResponse.Data.RefreshToken)
	require.NotEqual(t, loginResponse.Data.AccessToken, loginResponse.Data.RefreshToken)
	_, err = unsignedJWT.ParseTokenFor(loginResponse.Data.AccessToken, catLinkAppIssuer, catLinkAppIssuer)
	require.Error(t, err, "CatLink JWT must use the signing key loaded after package initialization")
	_, err = catJWT().ParseTokenFor(loginResponse.Data.RefreshToken, catLinkAppIssuer, catLinkAppIssuer)
	require.Error(t, err, "opaque refresh token must not be accepted as an access JWT")

	refreshBody, err := json.Marshal(map[string]string{"refreshToken": loginResponse.Data.RefreshToken})
	require.NoError(t, err)
	first := httptest.NewRecorder()
	router.ServeHTTP(first, httptest.NewRequest(http.MethodPost, "/refresh", bytes.NewReader(refreshBody)))
	var firstResponse authResponse
	require.NoError(t, json.Unmarshal(first.Body.Bytes(), &firstResponse))
	require.Equal(t, 0, firstResponse.Code)
	require.NotEqual(t, loginResponse.Data.RefreshToken, firstResponse.Data.RefreshToken)

	replay := httptest.NewRecorder()
	router.ServeHTTP(replay, httptest.NewRequest(http.MethodPost, "/refresh", bytes.NewReader(refreshBody)))
	var replayResponse authResponse
	require.NoError(t, json.Unmarshal(replay.Body.Bytes(), &replayResponse))
	require.Equal(t, 7, replayResponse.Code)

	stolenSuccessorBody, err := json.Marshal(map[string]string{"refreshToken": firstResponse.Data.RefreshToken})
	require.NoError(t, err)
	stolenSuccessor := httptest.NewRecorder()
	router.ServeHTTP(stolenSuccessor, httptest.NewRequest(http.MethodPost, "/refresh", bytes.NewReader(stolenSuccessorBody)))
	var stolenSuccessorResponse authResponse
	require.NoError(t, json.Unmarshal(stolenSuccessor.Body.Bytes(), &stolenSuccessorResponse))
	require.Equal(t, 7, stolenSuccessorResponse.Code, "replaying an ancestor must revoke the entire token family")
	var activeSessions int64
	require.NoError(t, db.Model(&catmodel.CatDeviceSession{}).Where("user_id = ? AND revoked_at IS NULL", user.ID).Count(&activeSessions).Error)
	require.Zero(t, activeSessions)
}
