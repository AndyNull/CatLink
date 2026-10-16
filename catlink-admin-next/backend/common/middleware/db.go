package middleware

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/go-admin-team/go-admin-core/v2/sdk"
)

func WithContextDb(c *gin.Context) {
	if c.Request.URL.Path == "/healthz" || c.Request.URL.Path == "/api/v1/health" {
		c.Next()
		return
	}
	db := sdk.Runtime.GetDbByTenant(c.Request.Host)
	if db == nil {
		c.AbortWithStatusJSON(http.StatusServiceUnavailable, gin.H{"status": "not_ready"})
		return
	}
	c.Set("db", db.WithContext(c))
	c.Next()
}
