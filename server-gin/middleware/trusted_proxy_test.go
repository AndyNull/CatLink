package middleware

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/require"
)

func TestConfigureTrustedProxiesRejectsUntrustedForwardedIP(t *testing.T) {
	t.Setenv(trustedProxyEnv, "10.20.0.0/16")
	gin.SetMode(gin.TestMode)
	router := gin.New()
	require.NoError(t, ConfigureTrustedProxies(router))
	router.GET("/ip", func(c *gin.Context) { c.String(http.StatusOK, c.ClientIP()) })

	trusted := httptest.NewRequest(http.MethodGet, "/ip", nil)
	trusted.RemoteAddr = "10.20.0.4:3000"
	trusted.Header.Set("X-Forwarded-For", "203.0.113.9")
	trustedResponse := httptest.NewRecorder()
	router.ServeHTTP(trustedResponse, trusted)
	require.Equal(t, "203.0.113.9", trustedResponse.Body.String())

	untrusted := httptest.NewRequest(http.MethodGet, "/ip", nil)
	untrusted.RemoteAddr = "198.51.100.2:3000"
	untrusted.Header.Set("X-Forwarded-For", "203.0.113.10")
	untrustedResponse := httptest.NewRecorder()
	router.ServeHTTP(untrustedResponse, untrusted)
	require.Equal(t, "198.51.100.2", untrustedResponse.Body.String())
}

func TestConfigureTrustedProxiesRejectsInvalidCIDR(t *testing.T) {
	t.Setenv(trustedProxyEnv, "not-a-network")
	require.Error(t, ConfigureTrustedProxies(gin.New()))
}

func TestIsTrustedProxyAddressSupportsExactIPv4(t *testing.T) {
	t.Setenv(trustedProxyEnv, "10.20.30.40")
	require.True(t, IsTrustedProxyAddress("10.20.30.40:8080"))
	require.False(t, IsTrustedProxyAddress("10.20.30.41:8080"))
}
