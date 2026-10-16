package service

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"os"
	"regexp"
	"strconv"
	"strings"
	"time"

	jwt "github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	"golang.org/x/crypto/bcrypt"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

var (
	ErrAppCredentials   = errors.New("账号或密码错误")
	ErrAppDisabled      = errors.New("账号不可用或已停用")
	ErrAppUnverified    = errors.New("请先完成邮箱验证")
	ErrAppSession       = errors.New("设备会话已失效，请重新登录")
	ErrAppNoChanges     = errors.New("没有可更新的资料")
	ErrAppUsername      = errors.New("用户名需为6-18位字母或数字，且不能使用保留词")
	ErrAppUsernameOnce  = errors.New("用户名只能修改一次")
	ErrAppUsernameTaken = errors.New("用户名已被使用")
	ErrAppNicknameTaken = errors.New("昵称已被使用")
	ErrAppAvatar        = errors.New("头像不存在或不属于当前账号")
)

var appUsernamePattern = regexp.MustCompile(`^[A-Za-z0-9]{6,18}$`)
var appUsernameBlacklist = map[string]bool{"admin": true, "administrator": true, "root": true, "system": true, "official": true, "support": true}

type appUser struct {
	ID          uint
	UUID        string
	Username    string
	Password    string
	NickName    string
	HeaderImg   string
	AuthorityID uint
	Email       string
	Enable      int
}

func (appUser) TableName() string { return "sys_users" }

type appAccountSecurity struct {
	UserID             uint `gorm:"primaryKey"`
	EmailVerified      bool
	AppUsername        *string
	UsernameChangedAt  *time.Time
	NicknameNormalized *string
}

func (appAccountSecurity) TableName() string { return "cat_account_securities" }

type appDeviceSession struct {
	ID               string
	UserID           uint
	Name             string
	TokenHash        string
	AccessTokenHash  string
	RefreshTokenHash string
	FamilyID         string
	CreatedAt        time.Time
	LastSeenAt       time.Time
	RevokedAt        *time.Time
}

func (appDeviceSession) TableName() string { return "cat_device_sessions" }

type AppAccount struct {
	ID                string `json:"id"`
	Email             string `json:"email"`
	EmailVerified     bool   `json:"emailVerified"`
	DisplayName       string `json:"displayName"`
	Nickname          string `json:"nickname"`
	AvatarURL         string `json:"avatarUrl"`
	Username          string `json:"username"`
	UsernameEditable  bool   `json:"usernameEditable"`
	UsernameTemporary bool   `json:"usernameTemporary"`
}

type AppTokens struct {
	AccessToken     string     `json:"accessToken"`
	RefreshToken    string     `json:"refreshToken"`
	AccessExpiresAt int64      `json:"accessExpiresAt"`
	Account         AppAccount `json:"account"`
	DeviceID        string     `json:"deviceId"`
}

type AppDevice struct {
	ID         string `json:"id"`
	Name       string `json:"name"`
	CreatedAt  int64  `json:"createdAt"`
	LastSeenAt int64  `json:"lastSeenAt"`
	Current    bool   `json:"current"`
}

type AppSecurityPolicy struct {
	CaptchaOpen   int
	CaptchaLength int
	CaptchaWidth  int
	CaptchaHeight int
	EmailReady    bool
}

type UpdateAppAccount struct {
	Nickname  string
	AvatarURL string
	Username  string
}

func LoadAppSecurityPolicy(db *gorm.DB) (AppSecurityPolicy, error) {
	policy := AppSecurityPolicy{CaptchaLength: 6, CaptchaWidth: 240, CaptchaHeight: 80}
	var config struct {
		CaptchaOpen int
		KeyLong     int
		ImgWidth    int
		ImgHeight   int
	}
	if err := db.Table("sys_security_config").Take(&config).Error; err != nil && !errors.Is(err, gorm.ErrRecordNotFound) {
		return policy, err
	}
	policy.CaptchaOpen = config.CaptchaOpen
	policy.CaptchaLength = bounded(config.KeyLong, 6, 4, 8)
	policy.CaptchaWidth = bounded(config.ImgWidth, 240, 80, 640)
	policy.CaptchaHeight = bounded(config.ImgHeight, 80, 32, 320)
	var email struct {
		Enabled            bool
		Host               string
		From               string
		PasswordEnv        string
		PasswordCiphertext string
	}
	if err := db.Table("cat_email_configs").Take(&email).Error; err == nil {
		policy.EmailReady = email.Enabled && strings.TrimSpace(email.Host) != "" && strings.TrimSpace(email.From) != "" && (strings.TrimSpace(email.PasswordEnv) != "" || strings.TrimSpace(email.PasswordCiphertext) != "")
	} else if !errors.Is(err, gorm.ErrRecordNotFound) {
		return policy, err
	}
	return policy, nil
}

type appTokenClaims struct {
	UUID            string
	ID              uint
	Username        string
	NickName        string
	AuthorityId     uint
	UserType        string
	BufferTime      int64
	MustChangePwd   bool  `json:"mustChangePwd"`
	CredentialEpoch int64 `json:"credentialEpoch,omitempty"`
	jwt.RegisteredClaims
}

func LoginApp(db *gorm.DB, email, password, deviceName string) (AppTokens, error) {
	var user appUser
	if err := db.Where("username = ? AND deleted_at IS NULL", strings.ToLower(strings.TrimSpace(email))).Take(&user).Error; err != nil {
		return AppTokens{}, ErrAppCredentials
	}
	if user.Enable != 1 {
		return AppTokens{}, ErrAppDisabled
	}
	if bcrypt.CompareHashAndPassword([]byte(user.Password), []byte(password)) != nil {
		return AppTokens{}, ErrAppCredentials
	}
	account, err := appAccountView(db, user)
	if err != nil {
		return AppTokens{}, err
	}
	if !account.EmailVerified {
		return AppTokens{}, ErrAppUnverified
	}
	return issueAppTokens(db, user, account, deviceName, "")
}

func RefreshApp(db *gorm.DB, refreshToken string) (AppTokens, error) {
	oldHash := hashToken(refreshToken)
	var session appDeviceSession
	if err := db.Where("refresh_token_hash = ?", oldHash).Take(&session).Error; err != nil {
		return AppTokens{}, ErrAppSession
	}
	familyID := session.FamilyID
	if familyID == "" {
		familyID = session.ID
	}
	if session.RevokedAt != nil {
		revokeFamily(db, session.UserID, familyID)
		return AppTokens{}, ErrAppSession
	}
	var user appUser
	if err := db.Where("id = ? AND enable = ? AND deleted_at IS NULL", session.UserID, 1).Take(&user).Error; err != nil {
		return AppTokens{}, ErrAppDisabled
	}
	account, err := appAccountView(db, user)
	if err != nil {
		return AppTokens{}, err
	}
	return rotateAppTokens(db, user, account, session, oldHash, familyID)
}

func AccountApp(db *gorm.DB, accountID uint) (AppAccount, error) {
	var user appUser
	if err := db.Where("id = ? AND enable = ? AND deleted_at IS NULL", accountID, 1).Take(&user).Error; err != nil {
		return AppAccount{}, ErrAppDisabled
	}
	return appAccountView(db, user)
}

func UpdateAccountApp(db *gorm.DB, accountID uint, input UpdateAppAccount) (AppAccount, error) {
	nickname := strings.TrimSpace(input.Nickname)
	avatar := strings.TrimSpace(input.AvatarURL)
	username := strings.TrimSpace(input.Username)
	if nickname == "" && avatar == "" && username == "" {
		return AppAccount{}, ErrAppNoChanges
	}
	if nickname != "" && len([]rune(nickname)) > 30 {
		return AppAccount{}, errors.New("昵称需为1-30个字符")
	}
	if username != "" && (!appUsernamePattern.MatchString(username) || appUsernameBlacklist[strings.ToLower(username)]) {
		return AppAccount{}, ErrAppUsername
	}
	err := db.Transaction(func(tx *gorm.DB) error {
		var user appUser
		if err := tx.Where("id = ? AND enable = ? AND deleted_at IS NULL", accountID, 1).Take(&user).Error; err != nil {
			return ErrAppDisabled
		}
		var security appAccountSecurity
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Where("user_id = ?", accountID).Take(&security).Error; err != nil {
			if !errors.Is(err, gorm.ErrRecordNotFound) {
				return err
			}
			security = appAccountSecurity{UserID: accountID}
			if err := tx.Create(&security).Error; err != nil {
				return err
			}
		}
		updates := map[string]any{}
		if nickname != "" {
			var count int64
			if err := tx.Model(&appUser{}).Where("id <> ? AND nick_name = ?", accountID, nickname).Count(&count).Error; err != nil {
				return err
			}
			if count > 0 {
				return ErrAppNicknameTaken
			}
			if err := tx.Model(&appAccountSecurity{}).Where("user_id <> ? AND nickname_normalized = ?", accountID, normalizeAppNickname(nickname)).Count(&count).Error; err != nil {
				return err
			}
			if count > 0 {
				return ErrAppNicknameTaken
			}
			updates["nick_name"] = nickname
		}
		if avatar != "" {
			var count int64
			if err := tx.Table("cat_media").Where("owner_id = ? AND category = ? AND url = ?", accountID, "avatar", avatar).Count(&count).Error; err != nil {
				return err
			}
			if count == 0 {
				return ErrAppAvatar
			}
			updates["header_img"] = avatar
		}
		securityUpdates := map[string]any{}
		if nickname != "" {
			securityUpdates["nickname_normalized"] = normalizeAppNickname(nickname)
		}
		if username != "" {
			if security.UsernameChangedAt != nil {
				return ErrAppUsernameOnce
			}
			var count int64
			if err := tx.Model(&appAccountSecurity{}).Where("app_username = ? AND user_id <> ?", username, accountID).Count(&count).Error; err != nil {
				return err
			}
			if count > 0 {
				return ErrAppUsernameTaken
			}
			now := time.Now()
			securityUpdates["app_username"] = username
			securityUpdates["username_changed_at"] = &now
		}
		if len(updates) > 0 {
			if err := tx.Model(&appUser{}).Where("id = ?", accountID).Updates(updates).Error; err != nil {
				return err
			}
		}
		if len(securityUpdates) > 0 {
			if err := tx.Model(&security).Updates(securityUpdates).Error; err != nil {
				return err
			}
		}
		return nil
	})
	if err != nil {
		return AppAccount{}, err
	}
	return AccountApp(db, accountID)
}

func normalizeAppNickname(value string) string {
	return strings.ToLower(strings.TrimSpace(value))
}

func LogoutApp(db *gorm.DB, accountID uint, sessionID, familyID, accessToken string) error {
	now := time.Now()
	return db.Transaction(func(tx *gorm.DB) error {
		var session appDeviceSession
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Where("id = ? AND user_id = ? AND access_token_hash = ?", sessionID, accountID, hashToken(accessToken)).Take(&session).Error; err != nil {
			return ErrAppSession
		}
		result := tx.Model(&appDeviceSession{}).Where("user_id = ? AND (family_id = ? OR id = ?)", accountID, familyID, sessionID).Update("revoked_at", &now)
		if result.Error != nil || result.RowsAffected == 0 {
			return ErrAppSession
		}
		return nil
	})
}

func AppDevices(db *gorm.DB, accountID uint, accessToken string) ([]AppDevice, error) {
	var rows []appDeviceSession
	if err := db.Where("user_id = ? AND revoked_at IS NULL", accountID).Order("last_seen_at DESC").Find(&rows).Error; err != nil {
		return nil, err
	}
	currentHash := hashToken(accessToken)
	items := make([]AppDevice, len(rows))
	for i, row := range rows {
		items[i] = AppDevice{ID: row.ID, Name: row.Name, CreatedAt: row.CreatedAt.UnixMilli(), LastSeenAt: row.LastSeenAt.UnixMilli(), Current: row.AccessTokenHash == currentHash}
	}
	return items, nil
}

func RevokeAppDevice(db *gorm.DB, accountID uint, deviceID string) error {
	var target appDeviceSession
	if err := db.Where("id = ? AND user_id = ?", deviceID, accountID).Take(&target).Error; err != nil {
		return ErrAppSession
	}
	familyID := target.FamilyID
	if familyID == "" {
		familyID = target.ID
	}
	return revokeFamily(db, accountID, familyID)
}

func issueAppTokens(db *gorm.DB, user appUser, account AppAccount, deviceName, familyID string) (AppTokens, error) {
	accessToken, expiresAt, err := createAppAccessToken(user)
	if err != nil {
		return AppTokens{}, err
	}
	refreshToken := uuid.NewString() + uuid.NewString()
	refreshHash := hashToken(refreshToken)
	if strings.TrimSpace(deviceName) == "" {
		deviceName = "有猫腻 App"
	}
	if familyID == "" {
		familyID = uuid.NewString()
	}
	now := time.Now()
	session := appDeviceSession{ID: refreshHash[:24], UserID: user.ID, Name: strings.TrimSpace(deviceName), TokenHash: refreshHash, AccessTokenHash: hashToken(accessToken), RefreshTokenHash: refreshHash, FamilyID: familyID, CreatedAt: now, LastSeenAt: now}
	if err := db.Create(&session).Error; err != nil {
		return AppTokens{}, err
	}
	return AppTokens{AccessToken: accessToken, RefreshToken: refreshToken, AccessExpiresAt: expiresAt, Account: account, DeviceID: session.ID}, nil
}

func rotateAppTokens(db *gorm.DB, user appUser, account AppAccount, old appDeviceSession, oldHash, familyID string) (AppTokens, error) {
	accessToken, expiresAt, err := createAppAccessToken(user)
	if err != nil {
		return AppTokens{}, err
	}
	refreshToken := uuid.NewString() + uuid.NewString()
	refreshHash := hashToken(refreshToken)
	now := time.Now()
	next := appDeviceSession{ID: refreshHash[:24], UserID: user.ID, Name: old.Name, TokenHash: refreshHash, AccessTokenHash: hashToken(accessToken), RefreshTokenHash: refreshHash, FamilyID: familyID, CreatedAt: now, LastSeenAt: now}
	err = db.Transaction(func(tx *gorm.DB) error {
		claim := tx.Model(&appDeviceSession{}).Where("id = ? AND user_id = ? AND refresh_token_hash = ? AND revoked_at IS NULL", old.ID, user.ID, oldHash).Update("revoked_at", &now)
		if claim.Error != nil || claim.RowsAffected != 1 {
			return ErrAppSession
		}
		return tx.Create(&next).Error
	})
	if err != nil {
		revokeFamily(db, user.ID, familyID)
		return AppTokens{}, ErrAppSession
	}
	return AppTokens{AccessToken: accessToken, RefreshToken: refreshToken, AccessExpiresAt: expiresAt, Account: account, DeviceID: next.ID}, nil
}

func createAppAccessToken(user appUser) (string, int64, error) {
	key := strings.TrimSpace(os.Getenv("CATLINK_JWT_SIGNING_KEY"))
	if len(key) < 32 {
		return "", 0, errors.New("CATLINK_JWT_SIGNING_KEY 未配置")
	}
	now := time.Now()
	expires := now.Add(15 * time.Minute)
	claims := appTokenClaims{UUID: user.UUID, ID: user.ID, Username: user.Username, NickName: user.NickName, AuthorityId: user.AuthorityID, RegisteredClaims: jwt.RegisteredClaims{
		Issuer: "CatLink-App", Audience: jwt.ClaimStrings{"CatLink-App"}, ID: uuid.NewString(), IssuedAt: jwt.NewNumericDate(now), NotBefore: jwt.NewNumericDate(now.Add(-time.Second)), ExpiresAt: jwt.NewNumericDate(expires),
	}}
	value, err := jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString([]byte(key))
	return value, expires.UnixMilli(), err
}

func appAccountView(db *gorm.DB, user appUser) (AppAccount, error) {
	var security appAccountSecurity
	err := db.Where("user_id = ?", user.ID).Take(&security).Error
	verified := true
	if err == nil {
		verified = security.EmailVerified
	} else if !errors.Is(err, gorm.ErrRecordNotFound) {
		return AppAccount{}, err
	}
	username := ""
	if security.AppUsername != nil {
		username = *security.AppUsername
	}
	avatar := strings.TrimSpace(user.HeaderImg)
	if strings.Contains(strings.ToLower(avatar), "qmplusimg.henrongyi.top/") {
		avatar = ""
	}
	email := user.Email
	if email == "" {
		email = user.Username
	}
	return AppAccount{ID: fmtUint(user.ID), Email: email, EmailVerified: verified, DisplayName: user.NickName, Nickname: user.NickName, AvatarURL: avatar, Username: username, UsernameEditable: security.UsernameChangedAt == nil, UsernameTemporary: security.UsernameChangedAt == nil}, nil
}

func revokeFamily(db *gorm.DB, accountID uint, familyID string) error {
	now := time.Now()
	return db.Model(&appDeviceSession{}).Where("user_id = ? AND (family_id = ? OR id = ?) AND revoked_at IS NULL", accountID, familyID, familyID).Update("revoked_at", &now).Error
}

func hashToken(value string) string {
	sum := sha256.Sum256([]byte(value))
	return hex.EncodeToString(sum[:])
}

func fmtUint(value uint) string {
	return strconv.FormatUint(uint64(value), 10)
}

func bounded(value, fallback, minimum, maximum int) int {
	if value < minimum || value > maximum {
		return fallback
	}
	return value
}
