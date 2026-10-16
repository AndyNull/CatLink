package apis

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

func TestContentDetailWithoutDatabaseReturnsServiceUnavailable(t *testing.T) {
	gin.SetMode(gin.TestMode)
	for _, setup := range []func(*gin.Context){
		func(*gin.Context) {},
		func(c *gin.Context) { c.Set("db", (*gorm.DB)(nil)) },
	} {
		recorder := httptest.NewRecorder()
		request := httptest.NewRequest(http.MethodGet, "/posts/1", nil)
		requestRouter := gin.New()
		requestRouter.Use(setup)
		requestRouter.GET("/posts/:id", ContentDetail("cat_posts"))
		requestRouter.ServeHTTP(recorder, request)
		if recorder.Code != http.StatusServiceUnavailable {
			t.Fatalf("status = %d, want %d", recorder.Code, http.StatusServiceUnavailable)
		}
	}
}
