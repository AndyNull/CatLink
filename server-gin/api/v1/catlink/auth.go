package catlink

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"strconv"
	"strings"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	catmodel "github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system/request"
	"github.com/flipped-aurora/gin-vue-admin/server/utils"
	"github.com/gin-gonic/gin"
	jwt "github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	"gorm.io/gorm"
)

type CatAuthApi struct{}

func catJWT() *utils.JWT { return utils.NewJWT() }

const catLinkAppIssuer = "CatLink-App"

func markCatLinkAppClaims(claims *request.CustomClaims) {
	claims.Issuer = catLinkAppIssuer
	claims.Audience = []string{catLinkAppIssuer}
	claims.ExpiresAt = jwt.NewNumericDate(time.Now().Add(15 * time.Minute))
}

func isCatLinkAppClaims(claims *request.CustomClaims) bool {
	if claims == nil || claims.Issuer != catLinkAppIssuer {
		return false
	}
	for _, audience := range claims.Audience {
		if audience == catLinkAppIssuer {
			return true
		}
	}
	return false
}

func tokenHash(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}

func validCatLinkPassword(password string) bool {
	if len(password) < 12 || len(password) > 128 {
		return false
	}
	var hasLetter, hasDigit bool
	for _, char := range password {
		hasLetter = hasLetter || (char >= 'A' && char <= 'Z') || (char >= 'a' && char <= 'z')
		hasDigit = hasDigit || (char >= '0' && char <= '9')
	}
	return hasLetter && hasDigit
}

func accountEmailVerified(userID uint) bool {
	var security catmodel.CatAccountSecurity
	err := global.GVA_DB.First(&security, "user_id = ?", userID).Error
	if err == nil {
		return security.EmailVerified
	}
	if !errors.Is(err, gorm.ErrRecordNotFound) {
		return false
	}
	// 迁移前已存在的账号保持可用；新注册账号会显式创建未验证记录。
	now := time.Now()
	security = catmodel.CatAccountSecurity{UserID: userID, EmailVerified: true, VerifiedAt: &now}
	_ = global.GVA_DB.Create(&security).Error
	return true
}

func accountView(user system.SysUser) gin.H {
	return gin.H{
		"id": strconv.FormatUint(uint64(user.ID), 10), "email": user.Username,
		"emailVerified": accountEmailVerified(user.ID), "displayName": user.NickName, "nickname": user.NickName,
		"avatarUrl": strings.TrimSpace(user.HeaderImg),
	}
}

func newRefreshToken() string {
	return uuid.NewString() + uuid.NewString()
}

func tokenView(user system.SysUser, accessToken, refreshToken, deviceID string, expiresAt int64) gin.H {
	return gin.H{
		"accessToken": accessToken, "refreshToken": refreshToken, "accessExpiresAt": expiresAt,
		"account": accountView(user), "deviceId": deviceID,
	}
}

func deviceSession(userID uint, name, accessToken, refreshToken string, now time.Time) catmodel.CatDeviceSession {
	refreshHash := tokenHash(refreshToken)
	return catmodel.CatDeviceSession{
		ID: refreshHash[:24], UserID: userID, Name: name,
		TokenHash: refreshHash, AccessTokenHash: tokenHash(accessToken),
		RefreshTokenHash: refreshHash, FamilyID: uuid.NewString(), CreatedAt: now, LastSeenAt: now,
	}
}

func issueAppTokens(user system.SysUser, deviceName string) (gin.H, error) {
	base := request.BaseClaims{ID: user.ID, Username: user.Username, NickName: user.NickName, AuthorityId: user.AuthorityId}
	claims := catJWT().CreateClaims(base)
	markCatLinkAppClaims(&claims)
	claims.RegisteredClaims.ID = uuid.NewString()
	token, err := catJWT().CreateToken(claims)
	if err != nil {
		return nil, err
	}
	deviceName = strings.TrimSpace(deviceName)
	if deviceName == "" {
		deviceName = "有猫腻 App"
	}
	refreshToken := newRefreshToken()
	session := deviceSession(user.ID, deviceName, token, refreshToken, time.Now())
	if err := global.GVA_DB.Create(&session).Error; err != nil {
		return nil, err
	}
	return tokenView(user, token, refreshToken, session.ID, claims.ExpiresAt.UnixMilli()), nil
}

func ok(c *gin.Context, data any) {
	c.JSON(200, gin.H{"code": 0, "msg": "ok", "data": data})
}
func fail(c *gin.Context, msg string) {
	c.JSON(200, gin.H{"code": 7, "msg": msg, "data": nil})
}

func failDB(c *gin.Context, message string) {
	// 详细数据库错误由 AccessLog 记录，客户端只收到稳定且不泄露 schema 的业务提示。
	fail(c, message)
}

// Health 健康检查
func (a *CatAuthApi) Health(c *gin.Context) { ok(c, "ok") }

// 从上下文取当前账号 ID（AuthBearer 中间件写入）
func currentUserId(c *gin.Context) uint {
	if v, exists := c.Get("catlink_uid"); exists {
		if id, ok2 := v.(uint); ok2 {
			return id
		}
	}
	return 0
}

// Login email/密码登录 -> 返回 App 兼容契约（accessToken/refreshToken/account）
func (a *CatAuthApi) Login(c *gin.Context) {
	var body struct {
		Email      string `json:"email"`
		Password   string `json:"password"`
		CaptchaID  string `json:"captchaId"`
		Captcha    string `json:"captcha"`
		DeviceName string `json:"deviceName"`
	}
	if c.ShouldBindJSON(&body) != nil {
		fail(c, "请求格式错误")
		return
	}
	email := strings.ToLower(strings.TrimSpace(body.Email))
	if email == "" || body.Password == "" {
		fail(c, "邮箱和密码不能为空")
		return
	}
	if !consumeAppCaptcha(body.CaptchaID, body.Captcha) {
		fail(c, "图形验证码错误或已过期，请刷新后重试")
		return
	}
	var user system.SysUser
	if err := global.GVA_DB.Where("username = ?", email).First(&user).Error; err != nil {
		fail(c, "账号或密码错误")
		return
	}
	if user.Enable == 2 {
		fail(c, "账号已停用，请联系管理员")
		return
	}
	if !utils.BcryptCheck(body.Password, user.Password) {
		fail(c, "账号或密码错误")
		return
	}
	if !accountEmailVerified(user.ID) {
		fail(c, "请先完成邮箱验证")
		return
	}
	view, err := issueAppTokens(user, body.DeviceName)
	if err != nil {
		fail(c, "登录失败，请稍后重试")
		return
	}
	ok(c, view)
}

// Register email 注册
func (a *CatAuthApi) Register(c *gin.Context) {
	var body struct {
		Email    string `json:"email"`
		Password string `json:"password"`
		Category string `json:"category"`
	}
	_ = c.ShouldBindJSON(&body)
	email := strings.ToLower(strings.TrimSpace(body.Email))
	if !validCatLinkPassword(body.Password) || !validCatLinkEmail(email) {
		fail(c, "邮箱格式不正确，或密码未满足12-128位且包含字母和数字的要求")
		return
	}
	mailConfig, err := loadEmailConfig()
	if err != nil || (!emailConfigReady(mailConfig) && !localMailOutboxEnabled()) {
		fail(c, "邮件服务暂不可用，请联系管理员")
		return
	}
	var cnt int64
	global.GVA_DB.Model(&system.SysUser{}).Where("username = ?", email).Count(&cnt)
	if cnt > 0 {
		fail(c, "该邮箱已注册")
		return
	}
	nick := email[0:strings.Index(email, "@")]
	user := system.SysUser{Username: email, Password: utils.BcryptHash(body.Password), NickName: nick, AuthorityId: 9528, Email: email, Enable: 1}
	if err := global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		if err := tx.Create(&user).Error; err != nil {
			return err
		}
		return tx.Create(&catmodel.CatAccountSecurity{UserID: user.ID, EmailVerified: false}).Error
	}); err != nil {
		fail(c, "注册失败，请稍后重试")
		return
	}
	code, err := issueAuthCode(email, authCodeVerifyEmail)
	if err == nil {
		err = sendAuthCode(email, authCodeVerifyEmail, code)
	}
	ok(c, gin.H{"id": user.ID, "verificationSent": err == nil})
}

// GetUserInfo Bearer 用户信息
func (a *CatAuthApi) GetUserInfo(c *gin.Context) {
	id := currentUserId(c)
	var user system.SysUser
	if err := global.GVA_DB.First(&user, id).Error; err != nil {
		fail(c, "用户不存在")
		return
	}
	ok(c, gin.H{
		"account": gin.H{
			"id": user.ID, "email": user.Username, "nickname": user.NickName,
			"authorityId": user.AuthorityId, "phone": user.Phone,
		},
	})
}

// validateAppBearer 验证 App JWT 与账号状态；allowRevokedSession 仅供 logout 定位刚轮换的旧 session。
func validateAppBearer(c *gin.Context, allowRevokedSession bool) bool {
	h := c.GetHeader("Authorization")
	if !strings.HasPrefix(h, "Bearer ") {
		fail(c, "认证失败，请先登录")
		c.Abort()
		return false
	}
	tokenStr := strings.TrimSpace(h[len("Bearer "):])
	claims, err := catJWT().ParseTokenFor(tokenStr, catLinkAppIssuer, catLinkAppIssuer)
	if err != nil || !isCatLinkAppClaims(claims) {
		fail(c, "登录状态已失效，请重新登录")
		c.Abort()
		return false
	}
	var user system.SysUser
	if err := global.GVA_DB.Select("id", "enable").First(&user, claims.BaseClaims.ID).Error; err != nil || user.Enable != 1 {
		fail(c, "账号不可用或已停用")
		c.Abort()
		return false
	}
	var session catmodel.CatDeviceSession
	q := global.GVA_DB.Where("user_id = ? AND access_token_hash = ?", claims.BaseClaims.ID, tokenHash(tokenStr))
	if !allowRevokedSession {
		q = q.Where("revoked_at IS NULL")
	}
	if err := q.First(&session).Error; err != nil {
		fail(c, "设备会话已失效，请重新登录")
		c.Abort()
		return false
	}
	if !allowRevokedSession {
		global.GVA_DB.Model(&session).Update("last_seen_at", time.Now())
	}
	familyID := session.FamilyID
	if familyID == "" {
		familyID = session.ID
	}
	c.Set("catlink_uid", claims.BaseClaims.ID)
	c.Set("claims", claims)
	c.Set("catlink_session_id", session.ID)
	c.Set("catlink_family_id", familyID)
	return true
}

// AuthBearer 中间件：严格要求 access session 当前未撤销。
func AuthBearer() gin.HandlerFunc {
	return func(c *gin.Context) {
		if validateAppBearer(c, false) {
			c.Next()
		}
	}
}

// LogoutBearer 仅用于 logout：允许由 refresh rotation 刚撤销的旧 access session 定位并撤销整个 family。
func LogoutBearer() gin.HandlerFunc {
	return func(c *gin.Context) {
		if validateAppBearer(c, true) {
			c.Next()
		}
	}
}
