package catlink

import (
	"strings"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	catmodel "github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system/request"
	"github.com/flipped-aurora/gin-vue-admin/server/utils"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

func bearerToken(c *gin.Context) string {
	h := c.GetHeader("Authorization")
	if !strings.HasPrefix(h, "Bearer ") {
		return ""
	}
	return strings.TrimSpace(strings.TrimPrefix(h, "Bearer "))
}

// Refresh 刷新并轮换 App token；已撤销设备不能刷新。
func (a *CatAuthApi) Refresh(c *gin.Context) {
	var body struct {
		RefreshToken string `json:"refreshToken"`
	}
	_ = c.ShouldBindJSON(&body)
	oldHash := tokenHash(body.RefreshToken)
	var session catmodel.CatDeviceSession
	if err := global.GVA_DB.Where("refresh_token_hash = ?", oldHash).First(&session).Error; err != nil {
		fail(c, "设备会话已失效，请重新登录")
		return
	}
	familyID := session.FamilyID
	if familyID == "" {
		familyID = session.ID
	}
	if session.RevokedAt != nil {
		now := time.Now()
		global.GVA_DB.Model(&catmodel.CatDeviceSession{}).
			Where("user_id = ? AND family_id = ? AND revoked_at IS NULL", session.UserID, familyID).
			Update("revoked_at", &now)
		fail(c, "设备会话已失效，请重新登录")
		return
	}
	var user system.SysUser
	if err := global.GVA_DB.First(&user, session.UserID).Error; err != nil || user.Enable == 2 {
		fail(c, "账号不可用")
		return
	}
	newClaims := catJWT().CreateClaims(request.BaseClaims{ID: user.ID, Username: user.Username, NickName: user.NickName, AuthorityId: user.AuthorityId})
	markCatLinkAppClaims(&newClaims)
	newClaims.RegisteredClaims.ID = uuid.NewString()
	newToken, err := catJWT().CreateToken(newClaims)
	if err != nil {
		fail(c, "刷新失败")
		return
	}
	now := time.Now()
	newRefreshToken := newRefreshToken()
	newID := tokenHash(newRefreshToken)[:24]
	newSession := deviceSession(user.ID, session.Name, newToken, newRefreshToken, now)
	newSession.ID = newID
	newSession.FamilyID = familyID
	result := global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		claim := tx.Model(&catmodel.CatDeviceSession{}).
			Where("id = ? AND user_id = ? AND refresh_token_hash = ? AND revoked_at IS NULL", session.ID, user.ID, oldHash).
			Update("revoked_at", &now)
		if claim.Error != nil {
			return claim.Error
		}
		if claim.RowsAffected != 1 {
			return gorm.ErrRecordNotFound
		}
		return tx.Create(&newSession).Error
	})
	if result != nil {
		global.GVA_DB.Model(&catmodel.CatDeviceSession{}).
			Where("user_id = ? AND family_id = ? AND revoked_at IS NULL", user.ID, familyID).
			Update("revoked_at", &now)
		fail(c, "设备会话已失效，请重新登录")
		return
	}
	ok(c, tokenView(user, newToken, newRefreshToken, newID, newClaims.ExpiresAt.UnixMilli()))
}

func (a *CatAuthApi) Logout(c *gin.Context) {
	familyValue, familyOK := c.Get("catlink_family_id")
	sessionValue, sessionOK := c.Get("catlink_session_id")
	familyID, _ := familyValue.(string)
	sessionID, _ := sessionValue.(string)
	if !familyOK || !sessionOK || familyID == "" || sessionID == "" {
		fail(c, "设备会话已失效，请重新登录")
		return
	}
	now := time.Now()
	result := global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		var locked catmodel.CatDeviceSession
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).
			Where("id = ? AND user_id = ? AND access_token_hash = ?", sessionID, currentUserId(c), tokenHash(bearerToken(c))).
			First(&locked).Error; err != nil {
			return err
		}
		res := tx.Model(&catmodel.CatDeviceSession{}).
			Where("user_id = ? AND (family_id = ? OR id = ?)", currentUserId(c), familyID, sessionID).
			Update("revoked_at", &now)
		if res.Error != nil {
			return res.Error
		}
		if res.RowsAffected == 0 {
			return gorm.ErrRecordNotFound
		}
		return nil
	})
	if result != nil {
		fail(c, "退出登录失败，请稍后重试")
		return
	}
	ok(c, gin.H{"ok": true})
}

func (a *CatAuthApi) Account(c *gin.Context) {
	var user system.SysUser
	if err := global.GVA_DB.First(&user, currentUserId(c)).Error; err != nil {
		fail(c, "用户不存在")
		return
	}
	ok(c, accountView(user))
}

func (a *CatAuthApi) UpdateAccount(c *gin.Context) {
	var body struct {
		Nickname  string `json:"nickname"`
		AvatarURL string `json:"avatarUrl"`
	}
	_ = c.ShouldBindJSON(&body)
	nick := strings.TrimSpace(body.Nickname)
	avatarURL := strings.TrimSpace(body.AvatarURL)
	if nick == "" && avatarURL == "" {
		fail(c, "没有可更新的资料")
		return
	}
	if nick != "" && len([]rune(nick)) > 30 {
		fail(c, "昵称需为1-30个字符")
		return
	}
	updates := map[string]any{}
	if nick != "" {
		updates["nick_name"] = nick
	}
	if avatarURL != "" {
		var count int64
		if err := global.GVA_DB.Model(&catmodel.CatMedia{}).
			Where("owner_id = ? AND category = ? AND url = ?", currentUserId(c), "avatar", avatarURL).Count(&count).Error; err != nil || count == 0 {
			fail(c, "头像不存在或不属于当前账号")
			return
		}
		updates["header_img"] = avatarURL
	}
	if err := global.GVA_DB.Model(&system.SysUser{}).Where("id = ?", currentUserId(c)).Updates(updates).Error; err != nil {
		failDB(c, "资料更新失败，请稍后重试")
		return
	}
	var user system.SysUser
	global.GVA_DB.First(&user, currentUserId(c))
	ok(c, accountView(user))
}

func (a *CatAuthApi) Devices(c *gin.Context) {
	uid := currentUserId(c)
	currentHash := tokenHash(bearerToken(c))
	var rows []catmodel.CatDeviceSession
	global.GVA_DB.Where("user_id = ? AND revoked_at IS NULL", uid).Order("last_seen_at desc").Find(&rows)
	items := make([]gin.H, 0, len(rows))
	for _, s := range rows {
		items = append(items, gin.H{"id": s.ID, "name": s.Name, "createdAt": s.CreatedAt.UnixMilli(), "lastSeenAt": s.LastSeenAt.UnixMilli(), "current": s.AccessTokenHash == currentHash})
	}
	ok(c, gin.H{"devices": items})
}

func (a *CatAuthApi) RevokeDevice(c *gin.Context) {
	uid := currentUserId(c)
	id := c.Param("deviceId")
	var target catmodel.CatDeviceSession
	if err := global.GVA_DB.Where("id = ? AND user_id = ?", id, uid).First(&target).Error; err != nil {
		fail(c, "设备不存在")
		return
	}
	familyID := target.FamilyID
	if familyID == "" {
		familyID = target.ID
	}
	now := time.Now()
	res := global.GVA_DB.Model(&catmodel.CatDeviceSession{}).
		Where("user_id = ? AND (family_id = ? OR id = ?)", uid, familyID, target.ID).
		Update("revoked_at", &now)
	if res.Error != nil || res.RowsAffected == 0 {
		fail(c, "设备不存在")
		return
	}
	ok(c, gin.H{"ok": true})
}

func (a *CatAuthApi) VerifyEmail(c *gin.Context) {
	var body struct {
		Email string `json:"email"`
		Code  string `json:"code"`
		Token string `json:"token"`
	}
	if c.ShouldBindJSON(&body) != nil {
		fail(c, "请求格式错误")
		return
	}
	if body.Code == "" {
		body.Code = body.Token
	}
	email := strings.ToLower(strings.TrimSpace(body.Email))
	if err := consumeAuthCode(email, authCodeVerifyEmail, body.Code); err != nil {
		fail(c, err.Error())
		return
	}
	var user system.SysUser
	if err := global.GVA_DB.Where("username = ?", email).First(&user).Error; err != nil {
		fail(c, "账号不存在")
		return
	}
	now := time.Now()
	if err := global.GVA_DB.Model(&catmodel.CatAccountSecurity{}).Where("user_id = ?", user.ID).
		Updates(map[string]any{"email_verified": true, "verified_at": &now}).Error; err != nil {
		failDB(c, "邮箱验证失败")
		return
	}
	view, err := issueAppTokens(user, c.GetHeader("X-Device-Name"))
	if err != nil {
		fail(c, "邮箱已验证，请重新登录")
		return
	}
	ok(c, view)
}

func (a *CatAuthApi) ResendEmail(c *gin.Context) {
	var body struct {
		Email string `json:"email"`
	}
	_ = c.ShouldBindJSON(&body)
	email := strings.ToLower(strings.TrimSpace(body.Email))
	var user system.SysUser
	if global.GVA_DB.Where("username = ?", email).First(&user).Error == nil && !accountEmailVerified(user.ID) {
		if code, err := issueAuthCode(email, authCodeVerifyEmail); err == nil {
			_ = sendAuthCode(email, authCodeVerifyEmail, code)
		}
	}
	ok(c, gin.H{"accepted": true})
}

func (a *CatAuthApi) RequestPasswordReset(c *gin.Context) {
	var body struct {
		Email string `json:"email"`
	}
	_ = c.ShouldBindJSON(&body)
	email := strings.ToLower(strings.TrimSpace(body.Email))
	var user system.SysUser
	if global.GVA_DB.Where("username = ?", email).First(&user).Error == nil {
		if code, err := issueAuthCode(email, authCodePasswordReset); err == nil {
			_ = sendAuthCode(email, authCodePasswordReset, code)
		}
	}
	ok(c, gin.H{"accepted": true})
}

func (a *CatAuthApi) ConfirmPasswordReset(c *gin.Context) {
	var body struct {
		Email       string `json:"email"`
		Code        string `json:"code"`
		Token       string `json:"token"`
		NewPassword string `json:"newPassword"`
	}
	if c.ShouldBindJSON(&body) != nil || !validCatLinkPassword(body.NewPassword) {
		fail(c, "验证码或新密码格式无效")
		return
	}
	if body.Code == "" {
		body.Code = body.Token
	}
	email := strings.ToLower(strings.TrimSpace(body.Email))
	if err := consumeAuthCode(email, authCodePasswordReset, body.Code); err != nil {
		fail(c, err.Error())
		return
	}
	var user system.SysUser
	if err := global.GVA_DB.Where("username = ?", email).First(&user).Error; err != nil {
		fail(c, "账号不存在")
		return
	}
	now := time.Now()
	if err := global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		if err := tx.Model(&user).Updates(map[string]any{"password": utils.BcryptHash(body.NewPassword), "password_updated_at": &now}).Error; err != nil {
			return err
		}
		return tx.Model(&catmodel.CatDeviceSession{}).Where("user_id = ? AND revoked_at IS NULL", user.ID).Update("revoked_at", &now).Error
	}); err != nil {
		failDB(c, "密码重置失败")
		return
	}
	ok(c, gin.H{"reset": true})
}
