package apis

import (
	"errors"
	"regexp"
	"strconv"
	"strings"
	"time"

	"go-admin/app/catlink/service"

	"github.com/gin-gonic/gin"
	"github.com/go-admin-team/go-admin-core/v2/captcha"
	"github.com/go-admin-team/go-admin-core/v2/sdk"
	"github.com/go-admin-team/go-admin-core/v2/sdk/pkg"
	"github.com/mojocn/base64Captcha"
)

var appCaptchaID = regexp.MustCompile(`^[A-Za-z0-9_-]{10,128}$`)

func AppAuthPolicy(c *gin.Context) {
	db, err := pkg.GetOrm(c)
	if err != nil {
		appFailure(c, "安全策略暂不可用")
		return
	}
	policy, err := service.LoadAppSecurityPolicy(db)
	if err != nil {
		appFailure(c, "安全策略暂不可用")
		return
	}
	appSuccess(c, gin.H{
		"loginOtpRequired": false, "captchaRequired": captchaRequired(c, policy.CaptchaOpen),
		"captchaLength": policy.CaptchaLength, "captchaWidth": policy.CaptchaWidth,
		"captchaHeight": policy.CaptchaHeight, "captchaOpen": policy.CaptchaOpen, "emailReady": policy.EmailReady,
	})
}

func AppCaptcha(c *gin.Context) {
	db, err := pkg.GetOrm(c)
	if err != nil {
		appFailure(c, "验证码服务暂不可用")
		return
	}
	policy, err := service.LoadAppSecurityPolicy(db)
	if err != nil {
		appFailure(c, "验证码服务暂不可用")
		return
	}
	var request struct {
		CaptchaID string `json:"captchaId"`
	}
	if c.ShouldBindJSON(&request) != nil {
		appFailure(c, "请求格式错误")
		return
	}
	previous := strings.TrimSpace(request.CaptchaID)
	if previous != "" {
		if !appCaptchaID.MatchString(previous) {
			appFailure(c, "请求格式错误")
			return
		}
		base64Captcha.DefaultMemStore.Get(previous, true)
	}
	driver := base64Captcha.NewDriverDigit(policy.CaptchaHeight, policy.CaptchaWidth, policy.CaptchaLength, 0.7, 80)
	id, image, _, err := base64Captcha.NewCaptcha(driver, base64Captcha.DefaultMemStore).Generate()
	if err != nil {
		appFailure(c, "验证码服务暂不可用")
		return
	}
	appSuccess(c, gin.H{"captchaId": id, "imageBase64": image, "length": policy.CaptchaLength, "expiresInSeconds": 180})
}

func AppLogin(c *gin.Context) {
	var request struct {
		Email, Password, CaptchaID, Captcha, DeviceName string
	}
	if c.ShouldBindJSON(&request) != nil || strings.TrimSpace(request.Email) == "" || request.Password == "" {
		appFailure(c, "邮箱和密码不能为空")
		return
	}
	db, err := pkg.GetOrm(c)
	if err != nil {
		appFailure(c, "登录服务暂不可用")
		return
	}
	policy, err := service.LoadAppSecurityPolicy(db)
	if err != nil {
		appFailure(c, "登录服务暂不可用")
		return
	}
	if captchaRequired(c, policy.CaptchaOpen) && (!appCaptchaID.MatchString(strings.TrimSpace(request.CaptchaID)) || !captcha.Verify(request.CaptchaID, strings.TrimSpace(request.Captcha), true)) {
		appFailure(c, "图形验证码错误或已过期，请刷新后重试")
		return
	}
	tokens, err := service.LoginApp(db, request.Email, request.Password, request.DeviceName)
	if err != nil {
		if errors.Is(err, service.ErrAppCredentials) {
			recordLoginFailure(c)
		}
		appFailure(c, err.Error())
		return
	}
	clearLoginFailures(c)
	appSuccess(c, tokens)
}

func AppRefresh(c *gin.Context) {
	var request struct {
		RefreshToken string `json:"refreshToken"`
	}
	if c.ShouldBindJSON(&request) != nil || strings.TrimSpace(request.RefreshToken) == "" {
		appFailure(c, "设备会话已失效，请重新登录")
		return
	}
	db, err := pkg.GetOrm(c)
	if err != nil {
		appFailure(c, "刷新服务暂不可用")
		return
	}
	tokens, err := service.RefreshApp(db, request.RefreshToken)
	if err != nil {
		appFailure(c, err.Error())
		return
	}
	appSuccess(c, tokens)
}

func AppLogout(c *gin.Context) {
	db, err := pkg.GetOrm(c)
	if err != nil {
		appFailure(c, "退出登录失败，请稍后重试")
		return
	}
	err = service.LogoutApp(db, c.GetUint("catlink_uid"), c.GetString("catlink_session_id"), c.GetString("catlink_family_id"), c.GetString("catlink_access_token"))
	if err != nil {
		appFailure(c, "退出登录失败，请稍后重试")
		return
	}
	appSuccess(c, gin.H{"ok": true})
}

func AppAccount(c *gin.Context) {
	db, err := pkg.GetOrm(c)
	if err != nil {
		appFailure(c, "账号服务暂不可用")
		return
	}
	account, err := service.AccountApp(db, c.GetUint("catlink_uid"))
	if err != nil {
		appFailure(c, err.Error())
		return
	}
	appSuccess(c, account)
}

func AppUpdateAccount(c *gin.Context) {
	var request struct {
		Nickname  string `json:"nickname"`
		AvatarURL string `json:"avatarUrl"`
		Username  string `json:"username"`
	}
	if c.ShouldBindJSON(&request) != nil {
		appFailure(c, "请求格式错误")
		return
	}
	db, err := pkg.GetOrm(c)
	if err != nil {
		appFailure(c, "账号服务暂不可用")
		return
	}
	account, err := service.UpdateAccountApp(db, c.GetUint("catlink_uid"), service.UpdateAppAccount{Nickname: request.Nickname, AvatarURL: request.AvatarURL, Username: request.Username})
	if err != nil {
		appFailure(c, err.Error())
		return
	}
	appSuccess(c, account)
}

func AppUserInfo(c *gin.Context) {
	db, err := pkg.GetOrm(c)
	if err != nil {
		appFailure(c, "账号服务暂不可用")
		return
	}
	account, err := service.AccountApp(db, c.GetUint("catlink_uid"))
	if err != nil {
		appFailure(c, err.Error())
		return
	}
	appSuccess(c, gin.H{"account": account})
}

func AppDeviceList(c *gin.Context) {
	db, err := pkg.GetOrm(c)
	if err != nil {
		appFailure(c, "设备服务暂不可用")
		return
	}
	items, err := service.AppDevices(db, c.GetUint("catlink_uid"), c.GetString("catlink_access_token"))
	if err != nil {
		appFailure(c, "读取设备失败")
		return
	}
	appSuccess(c, gin.H{"devices": items})
}

func AppDeviceRevoke(c *gin.Context) {
	db, err := pkg.GetOrm(c)
	if err != nil {
		appFailure(c, "设备服务暂不可用")
		return
	}
	if err := service.RevokeAppDevice(db, c.GetUint("catlink_uid"), strings.TrimSpace(c.Param("deviceId"))); err != nil {
		appFailure(c, "设备不存在")
		return
	}
	appSuccess(c, gin.H{"ok": true})
}

func AppVaultEnable(c *gin.Context) {
	var request struct {
		KeyVersion int `json:"keyVersion"`
	}
	_ = c.ShouldBindJSON(&request)
	db, err := pkg.GetOrm(c)
	if err != nil {
		appFailure(c, "档案同步服务暂不可用")
		return
	}
	cursor, keyVersion, err := service.EnableVault(db, c.GetUint("catlink_uid"), request.KeyVersion)
	if err != nil {
		appFailure(c, "档案同步服务暂不可用")
		return
	}
	appSuccess(c, gin.H{"enabled": true, "cursor": cursor, "keyVersion": keyVersion})
}

func AppVaultOpenSession(c *gin.Context) {
	var request struct {
		Password         string `json:"password"`
		RecoveryDEK      string `json:"recoveryDek"`
		LocalRecordCount int    `json:"localRecordCount"`
	}
	if c.ShouldBindJSON(&request) != nil {
		appFailure(c, "登录密码无效")
		return
	}
	db, err := pkg.GetOrm(c)
	if err != nil {
		appFailure(c, "账号档案密钥处理失败")
		return
	}
	result, err := service.OpenVaultSession(db, c.GetUint("catlink_uid"), request.Password, request.RecoveryDEK, request.LocalRecordCount)
	if err != nil {
		appFailure(c, vaultKeyMessage(err))
		return
	}
	appSuccess(c, result)
}

func AppVaultCurrentSession(c *gin.Context) {
	db, err := pkg.GetOrm(c)
	if err != nil {
		appFailure(c, "账号档案密钥处理失败")
		return
	}
	result, err := service.CurrentVaultSessionKey(db, c.GetUint("catlink_uid"))
	if err != nil {
		appFailure(c, vaultKeyMessage(err))
		return
	}
	appSuccess(c, result)
}

func AppVaultRecoveryKey(c *gin.Context) {
	var request struct {
		RecoveryDEK string `json:"recoveryDek"`
	}
	if c.ShouldBindJSON(&request) != nil {
		appFailure(c, "恢复密钥参数无效")
		return
	}
	db, err := pkg.GetOrm(c)
	if err != nil {
		appFailure(c, "恢复密钥保存失败")
		return
	}
	if err := service.SaveVaultRecoveryKey(db, c.GetUint("catlink_uid"), request.RecoveryDEK); err != nil {
		appFailure(c, vaultKeyMessage(err))
		return
	}
	appSuccess(c, gin.H{"configured": true})
}

func vaultKeyMessage(err error) string {
	for _, known := range []error{service.ErrVaultPassword, service.ErrVaultKeyUnavailable, service.ErrVaultKeyRecovery, service.ErrVaultRecoveryMismatch, service.ErrVaultKeyActive} {
		if errors.Is(err, known) {
			return known.Error()
		}
	}
	return "账号档案密钥处理失败"
}

func captchaRequired(c *gin.Context, threshold int) bool {
	if threshold <= 0 {
		return true
	}
	value, err := sdk.Runtime.GetCacheAdapter().Get(loginFailureKey(c))
	if err != nil {
		return false
	}
	count, _ := strconv.Atoi(value)
	return count >= threshold
}

func recordLoginFailure(c *gin.Context) {
	cache := sdk.Runtime.GetCacheAdapter()
	_ = cache.Increase(loginFailureKey(c))
	_ = cache.Expire(loginFailureKey(c), time.Hour)
}

func clearLoginFailures(c *gin.Context)     { _ = sdk.Runtime.GetCacheAdapter().Del(loginFailureKey(c)) }
func loginFailureKey(c *gin.Context) string { return "CATLINK_APP_LOGIN_FAILURE_" + c.ClientIP() }
func appSuccess(c *gin.Context, data any)   { c.JSON(200, gin.H{"code": 0, "msg": "ok", "data": data}) }
