package service

import (
	"testing"

	"github.com/glebarez/sqlite"
	"github.com/stretchr/testify/require"
	"golang.org/x/crypto/bcrypt"
	"gorm.io/gorm"
)

func appAuthDB(t *testing.T) *gorm.DB {
	t.Helper()
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	require.NoError(t, db.Exec(`CREATE TABLE sys_users (id integer primary key, uuid text, username text, password text, nick_name text, header_img text, authority_id integer, email text, enable integer, deleted_at datetime)`).Error)
	require.NoError(t, db.AutoMigrate(new(appAccountSecurity), new(appDeviceSession)))
	require.NoError(t, db.Exec(`CREATE TABLE cat_media (id integer primary key, owner_id integer, category text, url text)`).Error)
	hash, err := bcrypt.GenerateFromPassword([]byte("Password1!"), bcrypt.MinCost)
	require.NoError(t, err)
	require.NoError(t, db.Exec(`INSERT INTO sys_users VALUES(7,'00000000-0000-0000-0000-000000000007','dev@example.com',?,'猫友','','9528','dev@example.com',1,NULL)`, string(hash)).Error)
	require.NoError(t, db.Create(&appAccountSecurity{UserID: 7, EmailVerified: true}).Error)
	return db
}

func TestUpdateAccountAppEnforcesOwnershipAndOneTimeUsername(t *testing.T) {
	db := appAuthDB(t)
	require.NoError(t, db.Exec(`INSERT INTO sys_users (id,uuid,username,password,nick_name,header_img,authority_id,email,enable,deleted_at) VALUES (8,'00000000-0000-0000-0000-000000000008','other@example.com','x','其他','','9528','other@example.com',1,NULL)`).Error)
	require.NoError(t, db.Exec(`INSERT INTO cat_media (owner_id,category,url) VALUES (7,'avatar','/uploads/avatar-7.jpg')`).Error)

	account, err := UpdateAccountApp(db, 7, UpdateAppAccount{Nickname: "新猫友", AvatarURL: "/uploads/avatar-7.jpg", Username: "MaoYou2026"})
	require.NoError(t, err)
	require.Equal(t, "新猫友", account.Nickname)
	require.Equal(t, "MaoYou2026", account.Username)
	require.False(t, account.UsernameEditable)

	_, err = UpdateAccountApp(db, 7, UpdateAppAccount{Username: "MaoYou2027"})
	require.ErrorIs(t, err, ErrAppUsernameOnce)
	_, err = UpdateAccountApp(db, 8, UpdateAppAccount{Nickname: "新猫友"})
	require.ErrorIs(t, err, ErrAppNicknameTaken)
	_, err = UpdateAccountApp(db, 7, UpdateAppAccount{AvatarURL: "/uploads/foreign.jpg"})
	require.ErrorIs(t, err, ErrAppAvatar)
}

func TestAppLoginRefreshReplayAndLogout(t *testing.T) {
	t.Setenv("CATLINK_JWT_SIGNING_KEY", "test-signing-key-with-at-least-32-characters")
	db := appAuthDB(t)

	login, err := LoginApp(db, "DEV@example.com", "Password1!", "device A")
	require.NoError(t, err)
	require.Equal(t, "7", login.Account.ID)
	require.NotEmpty(t, login.AccessToken)
	require.NotEqual(t, login.AccessToken, login.RefreshToken)

	refreshed, err := RefreshApp(db, login.RefreshToken)
	require.NoError(t, err)
	require.NotEqual(t, login.RefreshToken, refreshed.RefreshToken)

	_, err = RefreshApp(db, login.RefreshToken)
	require.ErrorIs(t, err, ErrAppSession)
	var successor appDeviceSession
	require.NoError(t, db.Where("id = ?", refreshed.DeviceID).Take(&successor).Error)
	require.NotNil(t, successor.RevokedAt)

	second, err := LoginApp(db, "dev@example.com", "Password1!", "device B")
	require.NoError(t, err)
	require.NoError(t, LogoutApp(db, 7, second.DeviceID, second.DeviceID, second.AccessToken))
	var loggedOut appDeviceSession
	require.NoError(t, db.Where("id = ?", second.DeviceID).Take(&loggedOut).Error)
	require.NotNil(t, loggedOut.RevokedAt)
}

func TestAppLoginRejectsPasswordAndUnverifiedAccount(t *testing.T) {
	t.Setenv("CATLINK_JWT_SIGNING_KEY", "test-signing-key-with-at-least-32-characters")
	db := appAuthDB(t)
	_, err := LoginApp(db, "dev@example.com", "wrong", "device")
	require.ErrorIs(t, err, ErrAppCredentials)
	require.NoError(t, db.Model(&appAccountSecurity{}).Where("user_id = ?", 7).Update("email_verified", false).Error)
	_, err = LoginApp(db, "dev@example.com", "Password1!", "device")
	require.ErrorIs(t, err, ErrAppUnverified)
}
