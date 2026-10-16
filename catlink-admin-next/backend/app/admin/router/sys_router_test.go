package router

import (
	"testing"

	"github.com/gin-gonic/gin"
)

func TestProbeRoutesKeepReadyAlias(t *testing.T) {
	gin.SetMode(gin.TestMode)
	engine := gin.New()
	registerProbeRoutes(engine.Group(""))
	routes := map[string]bool{}
	for _, route := range engine.Routes() {
		routes[route.Method+" "+route.Path] = true
	}
	for _, path := range []string{"/healthz", "/readyz", "/api/ready"} {
		if !routes["GET "+path] {
			t.Fatalf("missing GET %s", path)
		}
	}
}
