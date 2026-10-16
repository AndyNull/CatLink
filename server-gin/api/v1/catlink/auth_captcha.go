package catlink

import (
	"regexp"
	"strings"
	"time"

	appcaptcha "github.com/flipped-aurora/gin-vue-admin/server/utils/captcha"
	"github.com/gin-gonic/gin"
	"github.com/mojocn/base64Captcha"
)

const (
	appCaptchaTTL    = 3 * time.Minute
	appCaptchaLength = 4
)

var (
	appCaptchaIDPattern = regexp.MustCompile(`^[A-Za-z0-9_-]{10,128}$`)
	appCaptchaStore     = newAppCaptchaStore(appCaptchaTTL)
)

func newAppCaptchaStore(expiration time.Duration) *appcaptcha.CacheStore {
	return &appcaptcha.CacheStore{Expiration: expiration, PreKey: "CATLINK_APP_CAPTCHA_"}
}

func consumeAppCaptcha(id, answer string) bool {
	id = strings.TrimSpace(id)
	answer = strings.TrimSpace(answer)
	return appCaptchaIDPattern.MatchString(id) && len(answer) == appCaptchaLength && appCaptchaStore.Verify(id, answer, true)
}

// Captcha 签发 App 登录专用的一次性图形验证码；captchaId 用于刷新并作废旧挑战。
func (a *CatAuthApi) Captcha(c *gin.Context) {
	var body struct {
		CaptchaID string `json:"captchaId"`
	}
	if c.ShouldBindJSON(&body) != nil {
		fail(c, "请求格式错误")
		return
	}
	previousID := strings.TrimSpace(body.CaptchaID)
	if previousID != "" {
		if !appCaptchaIDPattern.MatchString(previousID) {
			fail(c, "请求格式错误")
			return
		}
		appCaptchaStore.Get(previousID, true)
	}
	driver := base64Captcha.NewDriverDigit(44, 120, appCaptchaLength, 0.7, 80)
	id, image, _, err := base64Captcha.NewCaptcha(driver, appCaptchaStore).Generate()
	if err != nil {
		fail(c, "验证码服务暂不可用")
		return
	}
	ok(c, gin.H{
		"captchaId": id, "imageBase64": image, "length": appCaptchaLength,
		"expiresInSeconds": int(appCaptchaTTL / time.Second),
	})
}
