package catlink

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"regexp"
	"testing"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	catmodel "github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	"github.com/flipped-aurora/gin-vue-admin/server/utils"
	"github.com/gin-gonic/gin"
	"github.com/glebarez/sqlite"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

func setupAuthEmailDB(t *testing.T) *gorm.DB {
	t.Helper()
	oldDB, oldConfig := global.GVA_DB, global.GVA_CONFIG
	t.Cleanup(func() { global.GVA_DB, global.GVA_CONFIG = oldDB, oldConfig })
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, db.AutoMigrate(
		&system.SysUser{}, &catmodel.CatDeviceSession{}, &catmodel.CatAccountSecurity{},
		&catmodel.CatAuthCode{}, &catmodel.CatEmailConfig{},
	))
	global.GVA_DB = db
	global.GVA_CONFIG.JWT.SigningKey = "test-signing-key-with-at-least-32-characters"
	global.GVA_CONFIG.JWT.ExpiresTime = "7d"
	global.GVA_CONFIG.JWT.BufferTime = "1d"
	global.GVA_CONFIG.JWT.Issuer = "CatLink-Test"
	return db
}

func TestEmailAuthCodeSingleUseAndAttemptLimit(t *testing.T) {
	db := setupAuthEmailDB(t)
	code, err := issueAuthCode("otp@example.com", authCodeVerifyEmail)
	require.NoError(t, err)
	wrong := "000000"
	if code == wrong {
		wrong = "999999"
	}
	require.ErrorContains(t, consumeAuthCode("otp@example.com", authCodeVerifyEmail, wrong), "验证码")
	var record catmodel.CatAuthCode
	require.NoError(t, db.Order("id DESC").First(&record).Error)
	require.Equal(t, 1, record.Attempts)
	require.NoError(t, consumeAuthCode("otp@example.com", authCodeVerifyEmail, code))
	require.Error(t, consumeAuthCode("otp@example.com", authCodeVerifyEmail, code))
}

func TestLoginUsesCaptchaWhenLegacyEmailOTPIsConfigured(t *testing.T) {
	db := setupAuthEmailDB(t)
	setupAppCaptcha(t, appCaptchaTTL)
	gin.SetMode(gin.TestMode)
	user := system.SysUser{Username: "login-otp@example.com", Password: utils.BcryptHash("password123"), NickName: "otp", AuthorityId: 9528, Enable: 1}
	require.NoError(t, db.Create(&user).Error)
	now := time.Now()
	require.NoError(t, db.Create(&catmodel.CatAccountSecurity{UserID: user.ID, EmailVerified: true, VerifiedAt: &now}).Error)
	require.NoError(t, db.Create(&catmodel.CatEmailConfig{ID: 1, LoginOTPRequired: true, Port: 465, TLSMode: "implicit"}).Error)

	router := gin.New()
	router.POST("/login", (&CatAuthApi{}).Login)
	without := httptest.NewRecorder()
	router.ServeHTTP(without, httptest.NewRequest(http.MethodPost, "/login", bytes.NewBufferString(`{"email":"login-otp@example.com","password":"password123","deviceName":"test"}`)))
	var denied authResponse
	require.NoError(t, json.Unmarshal(without.Body.Bytes(), &denied))
	require.Equal(t, 7, denied.Code)

	with := httptest.NewRecorder()
	captchaID, captcha := seedAppCaptcha(t)
	router.ServeHTTP(with, httptest.NewRequest(http.MethodPost, "/login", captchaLoginBody(t, user.Username, "password123", captchaID, captcha)))
	var accepted authResponse
	require.NoError(t, json.Unmarshal(with.Body.Bytes(), &accepted))
	require.Equal(t, 0, accepted.Code, with.Body.String())
	require.NotEmpty(t, accepted.Data.AccessToken)
}

func TestRegisterVerifyAndLogin(t *testing.T) {
	db := setupAuthEmailDB(t)
	setupAppCaptcha(t, appCaptchaTTL)
	gin.SetMode(gin.TestMode)
	outbox := t.TempDir()
	t.Setenv("CATLINK_MAIL_OUTBOX_DIR", outbox)
	router := gin.New()
	auth := &CatAuthApi{}
	router.POST("/register", auth.Register)
	router.POST("/verify", auth.VerifyEmail)
	router.POST("/login", auth.Login)

	register := httptest.NewRecorder()
	router.ServeHTTP(register, httptest.NewRequest(http.MethodPost, "/register", bytes.NewBufferString(`{"email":"flow@example.com","password":"password1234"}`)))
	var registered authResponse
	require.NoError(t, json.Unmarshal(register.Body.Bytes(), &registered))
	require.Equal(t, 0, registered.Code, register.Body.String())

	files, err := os.ReadDir(outbox)
	require.NoError(t, err)
	require.Len(t, files, 1)
	message, err := os.ReadFile(filepath.Join(outbox, files[0].Name()))
	require.NoError(t, err)
	match := regexp.MustCompile(`>([0-9]{6})<`).FindSubmatch(message)
	require.Len(t, match, 2)

	verify := httptest.NewRecorder()
	verifyBody, err := json.Marshal(map[string]string{"email": "flow@example.com", "code": string(match[1])})
	require.NoError(t, err)
	router.ServeHTTP(verify, httptest.NewRequest(http.MethodPost, "/verify", bytes.NewReader(verifyBody)))
	var verified authResponse
	require.NoError(t, json.Unmarshal(verify.Body.Bytes(), &verified))
	require.Equal(t, 0, verified.Code, verify.Body.String())
	require.NotEmpty(t, verified.Data.AccessToken)

	require.NoError(t, db.Create(&catmodel.CatEmailConfig{ID: 1, LoginOTPRequired: true}).Error)
	captchaID, captcha := seedAppCaptcha(t)
	login := httptest.NewRecorder()
	router.ServeHTTP(login, httptest.NewRequest(http.MethodPost, "/login", captchaLoginBody(t, "flow@example.com", "password1234", captchaID, captcha)))
	var loggedIn authResponse
	require.NoError(t, json.Unmarshal(login.Body.Bytes(), &loggedIn))
	require.Equal(t, 0, loggedIn.Code, login.Body.String())
}

func TestEmailConfigReadyRequiresNonEmptySecret(t *testing.T) {
	config := catmodel.CatEmailConfig{
		Enabled: true, Host: "smtp.example.com", Port: 465, From: "mailer@example.com",
		PasswordEnv: "CATLINK_SMTP_TEST_PASSWORD",
	}
	t.Setenv(config.PasswordEnv, "")
	require.False(t, emailConfigReady(config))
	t.Setenv(config.PasswordEnv, "secret")
	require.True(t, emailConfigReady(config))
}

func TestExpiredAuthCodesAreCleaned(t *testing.T) {
	db := setupAuthEmailDB(t)
	now := time.Now()
	records := []catmodel.CatAuthCode{
		{Email: "old@example.com", Purpose: authCodeVerifyEmail, CodeHash: "hash", ExpiresAt: now.Add(-48 * time.Hour), CreatedAt: now.Add(-49 * time.Hour)},
		{Email: "current@example.com", Purpose: authCodePasswordReset, CodeHash: "hash", ExpiresAt: now.Add(time.Minute), CreatedAt: now},
	}
	require.NoError(t, db.Create(&records).Error)
	require.NoError(t, CleanupExpiredAuthCodes(context.Background()))
	var count int64
	require.NoError(t, db.Model(&catmodel.CatAuthCode{}).Count(&count).Error)
	require.Equal(t, int64(1), count)
}

func TestPasswordResetRevokesSessions(t *testing.T) {
	db := setupAuthEmailDB(t)
	user := system.SysUser{Username: "reset@example.com", Password: utils.BcryptHash("password123"), NickName: "reset", AuthorityId: 9528, Enable: 1}
	require.NoError(t, db.Create(&user).Error)
	require.NoError(t, db.Create(&catmodel.CatDeviceSession{ID: "session", UserID: user.ID, CreatedAt: time.Now(), LastSeenAt: time.Now()}).Error)
	code, err := issueAuthCode(user.Username, authCodePasswordReset)
	require.NoError(t, err)

	router := gin.New()
	router.POST("/reset", (&CatAuthApi{}).ConfirmPasswordReset)
	body, err := json.Marshal(map[string]string{"email": user.Username, "code": code, "newPassword": "newPassword123"})
	require.NoError(t, err)
	response := httptest.NewRecorder()
	router.ServeHTTP(response, httptest.NewRequest(http.MethodPost, "/reset", bytes.NewReader(body)))
	var envelope struct {
		Code int `json:"code"`
	}
	require.NoError(t, json.Unmarshal(response.Body.Bytes(), &envelope))
	require.Equal(t, 0, envelope.Code, response.Body.String())
	require.NoError(t, db.First(&user, user.ID).Error)
	require.True(t, utils.BcryptCheck("newPassword123", user.Password))
	var active int64
	require.NoError(t, db.Model(&catmodel.CatDeviceSession{}).Where("user_id = ? AND revoked_at IS NULL", user.ID).Count(&active).Error)
	require.Zero(t, active)
}

func TestIPLocationUsesTrustedProxyHeaders(t *testing.T) {
	t.Setenv("CATLINK_TRUST_IP_GEO_HEADERS", "true")
	t.Setenv("CATLINK_TRUSTED_PROXY_CIDRS", "10.0.0.0/8")
	gin.SetMode(gin.TestMode)
	router := gin.New()
	router.GET("/location", (&CatCommunityApi{}).IPLocation)
	request := httptest.NewRequest(http.MethodGet, "/location", nil)
	request.Header.Set("X-Geo-City", "成都市")
	request.Header.Set("X-Geo-District", "武侯区")
	request.Header.Set("X-Forwarded-For", "203.0.113.8, 10.0.0.2")
	response := httptest.NewRecorder()
	router.ServeHTTP(response, request)
	require.Contains(t, response.Body.String(), "成都市")
	require.Contains(t, response.Body.String(), "IP 粗略定位")
}
