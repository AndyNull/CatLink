package catlink

import (
	"crypto/rand"
	"crypto/tls"
	"errors"
	"fmt"
	"html"
	"math/big"
	netmail "net/mail"
	"net/smtp"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	catmodel "github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/flipped-aurora/gin-vue-admin/server/utils"
	"github.com/gin-gonic/gin"
	mail "github.com/jordan-wright/email"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

const (
	authCodeVerifyEmail   = "verify_email"
	authCodePasswordReset = "password_reset"
	authCodeTTL           = 10 * time.Minute
	authCodeMaxAttempts   = 5
)

func defaultEmailConfig() catmodel.CatEmailConfig {
	return catmodel.CatEmailConfig{ID: 1, Port: 465, TLSMode: "implicit", Nickname: "有猫腻"}
}

func loadEmailConfig() (catmodel.CatEmailConfig, error) {
	config := defaultEmailConfig()
	err := global.GVA_DB.First(&config, 1).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return config, nil
	}
	return config, err
}

func emailConfigReady(config catmodel.CatEmailConfig) bool {
	if !config.Enabled || config.Host == "" || config.Port == 0 || config.From == "" || config.PasswordEnv == "" {
		return false
	}
	return strings.TrimSpace(os.Getenv(config.PasswordEnv)) != ""
}

func validCatLinkEmail(value string) bool {
	address, err := netmail.ParseAddress(value)
	return err == nil && strings.EqualFold(address.Address, value) && len(value) <= 191
}

func localMailOutboxEnabled() bool {
	return strings.TrimSpace(os.Getenv("CATLINK_MAIL_OUTBOX_DIR")) != "" || gin.Mode() != gin.ReleaseMode
}

func issueAuthCode(emailAddress, purpose string) (string, error) {
	value, err := rand.Int(rand.Reader, big.NewInt(1_000_000))
	if err != nil {
		return "", err
	}
	code := fmt.Sprintf("%06d", value.Int64())
	now := time.Now()
	if err := global.GVA_DB.Model(&catmodel.CatAuthCode{}).
		Where("email = ? AND purpose = ? AND consumed_at IS NULL", emailAddress, purpose).
		Update("consumed_at", &now).Error; err != nil {
		return "", err
	}
	record := catmodel.CatAuthCode{
		Email: emailAddress, Purpose: purpose, CodeHash: utils.BcryptHash(code),
		ExpiresAt: now.Add(authCodeTTL), CreatedAt: now,
	}
	if err := global.GVA_DB.Create(&record).Error; err != nil {
		return "", err
	}
	return code, nil
}

func consumeAuthCode(emailAddress, purpose, code string) error {
	emailAddress = strings.ToLower(strings.TrimSpace(emailAddress))
	code = strings.TrimSpace(code)
	if emailAddress == "" || len(code) != 6 {
		return errors.New("验证码无效")
	}
	var validationErr error
	err := global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		var record catmodel.CatAuthCode
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).
			Where("email = ? AND purpose = ? AND consumed_at IS NULL AND expires_at > ?", emailAddress, purpose, time.Now()).
			Order("id DESC").First(&record).Error; err != nil {
			return errors.New("验证码无效或已过期")
		}
		if record.Attempts >= authCodeMaxAttempts {
			return errors.New("验证码尝试次数过多，请重新获取")
		}
		if !utils.BcryptCheck(code, record.CodeHash) {
			if err := tx.Model(&record).UpdateColumn("attempts", gorm.Expr("attempts + 1")).Error; err != nil {
				return err
			}
			validationErr = errors.New("验证码错误")
			return nil
		}
		now := time.Now()
		return tx.Model(&record).Update("consumed_at", &now).Error
	})
	if err != nil {
		return err
	}
	return validationErr
}

func sendAuthCode(emailAddress, purpose, code string) error {
	config, err := loadEmailConfig()
	if err != nil {
		return err
	}
	subject := map[string]string{
		authCodeVerifyEmail:   "验证有猫腻邮箱",
		authCodePasswordReset: "有猫腻密码重置验证码",
	}[purpose]
	body := fmt.Sprintf(`<div style="font-family:sans-serif;line-height:1.8"><p>你的验证码是：</p><p style="font-size:28px;font-weight:700;letter-spacing:6px">%s</p><p>10 分钟内有效，请勿转发。</p></div>`, html.EscapeString(code))
	if emailConfigReady(config) {
		return sendSMTPMail(config, emailAddress, subject, body)
	}
	return writeMailOutbox(emailAddress, subject, body)
}

func sendSMTPMail(config catmodel.CatEmailConfig, recipient, subject, body string) error {
	password := os.Getenv(config.PasswordEnv)
	username := config.Username
	if username == "" {
		username = config.From
	}
	auth := smtp.PlainAuth("", username, password, config.Host)
	message := mail.NewEmail()
	message.From = fmt.Sprintf("%s <%s>", config.Nickname, config.From)
	message.To = []string{recipient}
	message.Subject = subject
	message.HTML = []byte(body)
	address := fmt.Sprintf("%s:%d", config.Host, config.Port)
	if config.TLSMode == "implicit" {
		return message.SendWithTLS(address, auth, &tls.Config{ServerName: config.Host, MinVersion: tls.VersionTLS12})
	}
	return message.Send(address, auth)
}

func writeMailOutbox(recipient, subject, body string) error {
	dir := strings.TrimSpace(os.Getenv("CATLINK_MAIL_OUTBOX_DIR"))
	if dir == "" && gin.Mode() != gin.ReleaseMode {
		dir = "local-data/mail-outbox"
	}
	if dir == "" {
		return errors.New("SMTP 尚未配置")
	}
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return err
	}
	name := fmt.Sprintf("%d-%s-%s.html", time.Now().UnixNano(), tokenHash(recipient)[:10], strings.ReplaceAll(subject, " ", "-"))
	return os.WriteFile(filepath.Join(dir, name), []byte(body), 0o600)
}

func emailConfigView(config catmodel.CatEmailConfig) gin.H {
	return gin.H{
		"enabled": config.Enabled, "host": config.Host, "port": config.Port,
		"from": config.From, "username": config.Username, "passwordEnv": config.PasswordEnv,
		"nickname": config.Nickname, "tlsMode": config.TLSMode,
		"ready": emailConfigReady(config),
		"updatedAt": config.UpdatedAt,
	}
}

func (a *CatAuthApi) AuthPolicy(c *gin.Context) {
	config, _ := loadEmailConfig()
	ok(c, gin.H{
		"loginOtpRequired": false, "captchaRequired": true,
		"emailReady": emailConfigReady(config) || localMailOutboxEnabled(),
	})
}

func (a *CatAdminApi) EmailConfig(c *gin.Context) {
	config, err := loadEmailConfig()
	if err != nil {
		failDB(c, "读取邮件配置失败")
		return
	}
	ok(c, emailConfigView(config))
}

func (a *CatAdminApi) SaveEmailConfig(c *gin.Context) {
	var body struct {
		Enabled     bool   `json:"enabled"`
		Host        string `json:"host"`
		Port        int    `json:"port"`
		From        string `json:"from"`
		Username    string `json:"username"`
		PasswordEnv string `json:"passwordEnv"`
		Nickname    string `json:"nickname"`
		TLSMode     string `json:"tlsMode"`
	}
	if c.ShouldBindJSON(&body) != nil {
		fail(c, "请求格式错误")
		return
	}
	config := catmodel.CatEmailConfig{
		ID: 1, Enabled: body.Enabled, Host: strings.TrimSpace(body.Host), Port: body.Port,
		From: strings.ToLower(strings.TrimSpace(body.From)), Username: strings.TrimSpace(body.Username),
		PasswordEnv: strings.ToUpper(strings.TrimSpace(body.PasswordEnv)), Nickname: strings.TrimSpace(body.Nickname),
		TLSMode: strings.TrimSpace(body.TLSMode), LoginOTPRequired: false, UpdatedBy: utils.GetUserID(c),
	}
	if config.Nickname == "" {
		config.Nickname = "有猫腻"
	}
	if config.TLSMode == "" {
		config.TLSMode = "implicit"
	}
	if config.Port < 1 || config.Port > 65535 || (config.TLSMode != "implicit" && config.TLSMode != "starttls") {
		fail(c, "SMTP 端口或 TLS 模式无效")
		return
	}
	_, fromErr := netmail.ParseAddress(config.From)
	hostInvalid := config.Host == "" || strings.ContainsAny(config.Host, " /\\\r\n\t")
	if strings.ContainsAny(config.Nickname, "\r\n") || (config.Enabled && (hostInvalid || fromErr != nil || !envNamePattern.MatchString(config.PasswordEnv))) {
		fail(c, "启用前请完整填写 SMTP 地址、发件邮箱和密钥环境变量名")
		return
	}
	if err := global.GVA_DB.Save(&config).Error; err != nil {
		failDB(c, "保存邮件配置失败")
		return
	}
	ok(c, emailConfigView(config))
}

func (a *CatAdminApi) TestEmailConfig(c *gin.Context) {
	var body struct {
		Recipient string `json:"recipient"`
	}
	if c.ShouldBindJSON(&body) != nil {
		fail(c, "请输入测试收件邮箱")
		return
	}
	if _, err := netmail.ParseAddress(strings.TrimSpace(body.Recipient)); err != nil {
		fail(c, "请输入测试收件邮箱")
		return
	}
	config, err := loadEmailConfig()
	if err != nil || !emailConfigReady(config) {
		fail(c, "SMTP 配置尚未就绪")
		return
	}
	if err := sendSMTPMail(config, strings.ToLower(strings.TrimSpace(body.Recipient)), "有猫腻邮件服务测试", "<p>邮件服务配置可用。</p>"); err != nil {
		fail(c, "测试邮件发送失败")
		return
	}
	ok(c, gin.H{"sent": true})
}
