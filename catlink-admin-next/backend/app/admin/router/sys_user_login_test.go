package router

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/glebarez/sqlite"
	jwt "github.com/go-admin-team/go-admin-core/v2/jwtauth"
	"github.com/go-admin-team/go-admin-core/v2/logger"
	"github.com/go-admin-team/go-admin-core/v2/sdk/config"
	"github.com/go-admin-team/go-admin-core/v2/sdk/pkg"
	"go-admin/common/middleware/handler"
	"gorm.io/gorm"
)

func TestGetInfoLoadsCurrentAdminWithDataPermissionEnabled(t *testing.T) {
	gin.SetMode(gin.TestMode)
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	if err != nil {
		t.Fatal(err)
	}
	for _, statement := range []string{
		`CREATE TABLE sys_users (id integer primary key, username text, header_img text, dept_id integer, password_updated_at datetime, enable integer, deleted_at datetime)`,
		`CREATE TABLE ga_admin_memberships (sys_user_id integer, primary_role_id integer, enabled boolean)`,
		`INSERT INTO sys_users(id,username,header_img,dept_id,enable,deleted_at) VALUES(7,'admin','',1,1,NULL)`,
		`INSERT INTO ga_admin_memberships(sys_user_id,primary_role_id,enabled) VALUES(7,1,1)`,
	} {
		if err := db.Exec(statement).Error; err != nil {
			t.Fatal(err)
		}
	}

	previous := config.ApplicationConfig.EnableDP
	config.ApplicationConfig.EnableDP = true
	t.Cleanup(func() { config.ApplicationConfig.EnableDP = previous })

	auth := newGetInfoTestAuth(t)
	engine := gin.New()
	engine.Use(func(c *gin.Context) {
		c.Set("db", db)
		c.Set(pkg.LoggerKey, logger.NewHelper(logger.DefaultLogger))
		c.Next()
	})
	registerSysUserRouter(engine.Group("/admin-api"), auth)

	token, _, err := auth.TokenGenerator(jwt.MapClaims{
		"identity":         7,
		"roleid":           1,
		"rolekey":          "admin",
		"nice":             "admin",
		"datascope":        "1",
		"deptid":           1,
		"credential_epoch": "0",
		"iss":              "CatLink-Admin",
		"aud":              "CatLink-Admin-Web",
	})
	if err != nil {
		t.Fatal(err)
	}
	req := httptest.NewRequest(http.MethodGet, "/admin-api/getinfo", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	recorder := httptest.NewRecorder()
	engine.ServeHTTP(recorder, req)

	var response struct {
		Code int `json:"code"`
		Data struct {
			UserID int `json:"userId"`
		} `json:"data"`
	}
	if err := json.Unmarshal(recorder.Body.Bytes(), &response); err != nil {
		t.Fatal(err)
	}
	if recorder.Code != http.StatusOK || response.Code != http.StatusOK || response.Data.UserID != 7 {
		t.Fatalf("getinfo returned status=%d body=%s", recorder.Code, recorder.Body.String())
	}

	anonymous := httptest.NewRecorder()
	engine.ServeHTTP(anonymous, httptest.NewRequest(http.MethodGet, "/admin-api/getinfo", nil))
	if anonymous.Code != http.StatusUnauthorized {
		t.Fatalf("anonymous getinfo returned status=%d body=%s", anonymous.Code, anonymous.Body.String())
	}
}

func newGetInfoTestAuth(t *testing.T) *jwt.GinJWTMiddleware {
	t.Helper()
	auth, err := jwt.New(&jwt.GinJWTMiddleware{
		Key:             []byte("getinfo-data-permission-test-key"),
		Timeout:         time.Hour,
		IdentityHandler: handler.IdentityHandler,
		Authorizator:    handler.Authorizator,
		PayloadFunc: func(data interface{}) jwt.MapClaims {
			return data.(jwt.MapClaims)
		},
		Unauthorized: func(c *gin.Context, code int, message string) {
			c.JSON(code, gin.H{"code": code, "msg": message})
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	return auth
}
