package initialize

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	"github.com/gin-gonic/gin"
	"github.com/glebarez/sqlite"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

func TestDependencyReadyChecksDatabase(t *testing.T) {
	originalDB := global.GVA_DB
	originalUseRedis := global.GVA_CONFIG.System.UseRedis
	t.Cleanup(func() {
		global.GVA_DB = originalDB
		global.GVA_CONFIG.System.UseRedis = originalUseRedis
	})
	gin.SetMode(gin.TestMode)
	global.GVA_CONFIG.System.UseRedis = false

	router := gin.New()
	router.GET("/ready", dependencyReady)

	global.GVA_DB = nil
	unavailable := httptest.NewRecorder()
	router.ServeHTTP(unavailable, httptest.NewRequest(http.MethodGet, "/ready", nil))
	require.Equal(t, http.StatusServiceUnavailable, unavailable.Code)

	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)
	global.GVA_DB = db
	ready := httptest.NewRecorder()
	router.ServeHTTP(ready, httptest.NewRequest(http.MethodGet, "/ready", nil))
	require.Equal(t, http.StatusOK, ready.Code)
}
