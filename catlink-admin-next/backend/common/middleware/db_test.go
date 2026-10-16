package middleware

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
)

func TestWithContextDbUnknownTenantReturnsServiceUnavailable(t *testing.T) {
	gin.SetMode(gin.TestMode)
	engine := gin.New()
	engine.Use(WithContextDb)
	engine.GET("/readyz", func(c *gin.Context) { c.Status(http.StatusOK) })
	recorder := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodGet, "http://unknown-tenant.invalid/readyz", nil)
	engine.ServeHTTP(recorder, request)
	if recorder.Code != http.StatusServiceUnavailable {
		t.Fatalf("status = %d, want %d", recorder.Code, http.StatusServiceUnavailable)
	}
}

func TestWithContextDbLivenessProbesSkipDatabase(t *testing.T) {
	gin.SetMode(gin.TestMode)
	engine := gin.New()
	engine.Use(WithContextDb)
	for _, path := range []string{"/healthz", "/api/v1/health"} {
		path := path
		engine.GET(path, func(c *gin.Context) {
			if _, exists := c.Get("db"); exists {
				t.Errorf("%s unexpectedly received a database", path)
			}
			c.Status(http.StatusOK)
		})
	}
	for _, path := range []string{"/healthz", "/api/v1/health"} {
		recorder := httptest.NewRecorder()
		request := httptest.NewRequest(http.MethodGet, "http://unknown-tenant.invalid"+path, nil)
		engine.ServeHTTP(recorder, request)
		if recorder.Code != http.StatusOK {
			t.Fatalf("%s status = %d, want %d", path, recorder.Code, http.StatusOK)
		}
	}
}
