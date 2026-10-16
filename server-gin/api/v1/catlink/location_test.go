package catlink

import (
	"net/http"
	"net/http/httptest"
	"strconv"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/require"
)

func TestAreaFromGeocoderUsesDistrictLevelChineseLocation(t *testing.T) {
	area := areaFromGeocoder(
		map[string]string{"city": "武侯区", "state": "四川省", "suburb": "浆洗街街道"},
		"浆洗街街道, 武侯区, 成都市, 四川省, 中国",
	)

	require.Equal(t, "成都市", area.City)
	require.Equal(t, "武侯区", area.District)
}

func TestIPLocationRejectsGeoHeadersFromUntrustedProxy(t *testing.T) {
	t.Setenv("CATLINK_TRUST_IP_GEO_HEADERS", "true")
	t.Setenv("CATLINK_TRUSTED_PROXY_CIDRS", "10.0.0.0/8")
	t.Setenv("CATLINK_IP_GEO_ENDPOINT", "")
	gin.SetMode(gin.TestMode)
	router := gin.New()
	router.GET("/location", (&CatCommunityApi{}).IPLocation)
	request := httptest.NewRequest(http.MethodGet, "/location", nil)
	request.RemoteAddr = "198.51.100.2:3000"
	request.Header.Set("X-Forwarded-For", "203.0.113.8, 198.51.100.2")
	request.Header.Set("X-Geo-City", "伪造城市")
	request.Header.Set("X-Geo-District", "伪造区县")
	response := httptest.NewRecorder()
	router.ServeHTTP(response, request)
	require.NotContains(t, response.Body.String(), "伪造城市")
}

func TestLocationCacheIsBoundedAndExpires(t *testing.T) {
	now := time.Unix(1_700_000_000, 0)
	cache := make(map[string]cachedNearbyArea)
	for index := 0; index < locationCacheMaxEntries+100; index++ {
		storeCachedArea(cache, strconv.Itoa(index), nearbyArea{City: "成都市"}, now)
	}
	require.Len(t, cache, locationCacheMaxEntries)

	cache["expired"] = cachedNearbyArea{Area: nearbyArea{City: "旧城市"}, ExpiresAt: now.Add(-time.Second)}
	_, found := cachedArea(cache, "expired", now)
	require.False(t, found)
}

func TestGeocoderRedirectsAreRejected(t *testing.T) {
	require.ErrorIs(t, rejectGeocoderRedirect(nil, nil), http.ErrUseLastResponse)
}
