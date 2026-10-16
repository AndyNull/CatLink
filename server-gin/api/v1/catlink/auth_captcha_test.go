package catlink

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	"github.com/flipped-aurora/gin-vue-admin/server/internal/testutil"
	"github.com/flipped-aurora/gin-vue-admin/server/middleware"
	catmodel "github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	"github.com/flipped-aurora/gin-vue-admin/server/utils"
	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/require"
)

type captchaResponse struct {
	Code int `json:"code"`
	Data struct {
		CaptchaID       string `json:"captchaId"`
		ImageBase64     string `json:"imageBase64"`
		Length          int    `json:"length"`
		ExpiresInSecond int    `json:"expiresInSeconds"`
	} `json:"data"`
}

func setupAppCaptcha(t *testing.T, expiration time.Duration) {
	t.Helper()
	testutil.InitMemoryCache(t, time.Minute)
	previous := appCaptchaStore
	appCaptchaStore = newAppCaptchaStore(expiration)
	t.Cleanup(func() { appCaptchaStore = previous })
}

func requestCaptcha(t *testing.T, router http.Handler, previousID string) captchaResponse {
	t.Helper()
	body, err := json.Marshal(map[string]string{"captchaId": previousID})
	require.NoError(t, err)
	response := httptest.NewRecorder()
	router.ServeHTTP(response, httptest.NewRequest(http.MethodPost, "/captcha", bytes.NewReader(body)))
	require.Equal(t, http.StatusOK, response.Code)
	var envelope captchaResponse
	require.NoError(t, json.Unmarshal(response.Body.Bytes(), &envelope))
	require.Equal(t, 0, envelope.Code, response.Body.String())
	return envelope
}

func captchaLoginBody(t *testing.T, email, password, captchaID, captcha string) *bytes.Reader {
	t.Helper()
	body, err := json.Marshal(map[string]string{
		"email": email, "password": password, "captchaId": captchaID,
		"captcha": captcha, "deviceName": "test",
	})
	require.NoError(t, err)
	return bytes.NewReader(body)
}

func seedAppCaptcha(t *testing.T) (string, string) {
	t.Helper()
	const id, answer = "TestCaptchaID", "1234"
	require.NoError(t, appCaptchaStore.Set(id, answer))
	return id, answer
}

func createVerifiedCaptchaUser(t *testing.T, email string) system.SysUser {
	t.Helper()
	user := system.SysUser{Username: email, Password: utils.BcryptHash("password123"), NickName: "captcha", AuthorityId: 9528, Enable: 1}
	require.NoError(t, global.GVA_DB.Create(&user).Error)
	now := time.Now()
	require.NoError(t, global.GVA_DB.Create(&catmodel.CatAccountSecurity{UserID: user.ID, EmailVerified: true, VerifiedAt: &now}).Error)
	return user
}

func TestAppCaptchaLoginSuccessAndSingleUse(t *testing.T) {
	setupAuthEmailDB(t)
	setupAppCaptcha(t, appCaptchaTTL)
	gin.SetMode(gin.TestMode)
	user := createVerifiedCaptchaUser(t, "captcha-login@example.com")

	router := gin.New()
	auth := &CatAuthApi{}
	router.POST("/captcha", auth.Captcha)
	router.POST("/login", auth.Login)
	challenge := requestCaptcha(t, router, "")
	require.NotEmpty(t, challenge.Data.ImageBase64)
	require.Equal(t, appCaptchaLength, challenge.Data.Length)
	require.Equal(t, int(appCaptchaTTL/time.Second), challenge.Data.ExpiresInSecond)
	answer := appCaptchaStore.Get(challenge.Data.CaptchaID, false)
	require.NotEmpty(t, answer)

	accepted := httptest.NewRecorder()
	router.ServeHTTP(accepted, httptest.NewRequest(http.MethodPost, "/login", captchaLoginBody(t, user.Username, "password123", challenge.Data.CaptchaID, answer)))
	var loggedIn authResponse
	require.NoError(t, json.Unmarshal(accepted.Body.Bytes(), &loggedIn))
	require.Equal(t, 0, loggedIn.Code, accepted.Body.String())
	require.NotEmpty(t, loggedIn.Data.AccessToken)

	replay := httptest.NewRecorder()
	router.ServeHTTP(replay, httptest.NewRequest(http.MethodPost, "/login", captchaLoginBody(t, user.Username, "password123", challenge.Data.CaptchaID, answer)))
	var denied authResponse
	require.NoError(t, json.Unmarshal(replay.Body.Bytes(), &denied))
	require.Equal(t, 7, denied.Code, replay.Body.String())
}

func TestAppCaptchaWrongAnswerIsConsumed(t *testing.T) {
	setupAuthEmailDB(t)
	setupAppCaptcha(t, appCaptchaTTL)
	gin.SetMode(gin.TestMode)
	user := createVerifiedCaptchaUser(t, "captcha-wrong@example.com")
	router := gin.New()
	auth := &CatAuthApi{}
	router.POST("/captcha", auth.Captcha)
	router.POST("/login", auth.Login)
	challenge := requestCaptcha(t, router, "")
	answer := appCaptchaStore.Get(challenge.Data.CaptchaID, false)
	wrong := "0000"
	if answer == wrong {
		wrong = "9999"
	}

	for _, candidate := range []string{wrong, answer} {
		response := httptest.NewRecorder()
		router.ServeHTTP(response, httptest.NewRequest(http.MethodPost, "/login", captchaLoginBody(t, user.Username, "password123", challenge.Data.CaptchaID, candidate)))
		var denied authResponse
		require.NoError(t, json.Unmarshal(response.Body.Bytes(), &denied))
		require.Equal(t, 7, denied.Code, response.Body.String())
	}
}

func TestAppCaptchaExpires(t *testing.T) {
	setupAuthEmailDB(t)
	setupAppCaptcha(t, 15*time.Millisecond)
	gin.SetMode(gin.TestMode)
	user := createVerifiedCaptchaUser(t, "captcha-expired@example.com")
	router := gin.New()
	auth := &CatAuthApi{}
	router.POST("/captcha", auth.Captcha)
	router.POST("/login", auth.Login)
	challenge := requestCaptcha(t, router, "")
	answer := appCaptchaStore.Get(challenge.Data.CaptchaID, false)
	require.Eventually(t, func() bool {
		return appCaptchaStore.Get(challenge.Data.CaptchaID, false) == ""
	}, time.Second, 5*time.Millisecond)

	response := httptest.NewRecorder()
	router.ServeHTTP(response, httptest.NewRequest(http.MethodPost, "/login", captchaLoginBody(t, user.Username, "password123", challenge.Data.CaptchaID, answer)))
	var denied authResponse
	require.NoError(t, json.Unmarshal(response.Body.Bytes(), &denied))
	require.Equal(t, 7, denied.Code, response.Body.String())
}

func TestAppCaptchaRefreshInvalidatesPreviousChallenge(t *testing.T) {
	setupAppCaptcha(t, appCaptchaTTL)
	gin.SetMode(gin.TestMode)
	router := gin.New()
	router.POST("/captcha", (&CatAuthApi{}).Captcha)
	first := requestCaptcha(t, router, "")
	second := requestCaptcha(t, router, first.Data.CaptchaID)

	require.NotEqual(t, first.Data.CaptchaID, second.Data.CaptchaID)
	require.Empty(t, appCaptchaStore.Get(first.Data.CaptchaID, false))
	require.NotEmpty(t, appCaptchaStore.Get(second.Data.CaptchaID, false))
}

func TestAppCaptchaEndpointIsRateLimited(t *testing.T) {
	setupAppCaptcha(t, appCaptchaTTL)
	gin.SetMode(gin.TestMode)
	router := gin.New()
	router.POST("/captcha", middleware.CatLinkAuthLimit(), (&CatAuthApi{}).Captcha)
	for range 30 {
		response := httptest.NewRecorder()
		router.ServeHTTP(response, httptest.NewRequest(http.MethodPost, "/captcha", bytes.NewBufferString(`{}`)))
		require.Equal(t, http.StatusOK, response.Code, response.Body.String())
	}
	response := httptest.NewRecorder()
	router.ServeHTTP(response, httptest.NewRequest(http.MethodPost, "/captcha", bytes.NewBufferString(`{}`)))
	require.Equal(t, http.StatusTooManyRequests, response.Code, response.Body.String())
	require.Contains(t, response.Body.String(), "请求过于频繁")

	otherIPRequest := httptest.NewRequest(http.MethodPost, "/captcha", bytes.NewBufferString(`{}`))
	otherIPRequest.RemoteAddr = "198.51.100.2:4321"
	otherIP := httptest.NewRecorder()
	router.ServeHTTP(otherIP, otherIPRequest)
	require.Equal(t, http.StatusOK, otherIP.Code, otherIP.Body.String())
}
