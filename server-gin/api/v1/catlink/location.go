package catlink

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/middleware"
	"github.com/gin-gonic/gin"
)

type nearbyArea struct {
	City        string `json:"city"`
	District    string `json:"district"`
	Attribution string `json:"attribution,omitempty"`
}

// IPLocation 使用可信反向代理 GeoIP 头或部署方配置的 HTTPS 服务进行城市级粗略定位。
func (a *CatCommunityApi) IPLocation(c *gin.Context) {
	if trustedIPGeoHeaders(c) {
		city := strings.TrimSpace(c.GetHeader("X-Geo-City"))
		district := strings.TrimSpace(c.GetHeader("X-Geo-District"))
		if city != "" && len([]rune(city)) <= 80 && len([]rune(district)) <= 80 {
			ok(c, nearbyArea{City: city, District: district, Attribution: "IP 粗略定位"})
			return
		}
	}

	ip := net.ParseIP(c.ClientIP())
	if ip == nil || ip.IsLoopback() || ip.IsPrivate() || ip.IsUnspecified() {
		fail(c, "当前网络无法进行 IP 定位")
		return
	}
	key := ip.String()
	ipGeocoder.Lock()
	area, found := cachedArea(ipGeocoder.Cache, key, time.Now())
	ipGeocoder.Unlock()
	if found {
		ok(c, area)
		return
	}
	area, err := requestIPArea(c.Request.Context(), key)
	if err != nil {
		fail(c, "暂时无法通过 IP 识别所在城市")
		return
	}
	ipGeocoder.Lock()
	storeCachedArea(ipGeocoder.Cache, key, area, time.Now())
	ipGeocoder.Unlock()
	ok(c, area)
}

func trustedIPGeoHeaders(c *gin.Context) bool {
	if !strings.EqualFold(strings.TrimSpace(os.Getenv("CATLINK_TRUST_IP_GEO_HEADERS")), "true") {
		return false
	}
	forwarded := strings.Split(c.GetHeader("X-Forwarded-For"), ",")
	return len(forwarded) > 0 && middleware.IsTrustedProxyAddress(forwarded[len(forwarded)-1])
}

func requestIPArea(ctx context.Context, ip string) (nearbyArea, error) {
	endpoint := strings.TrimSpace(os.Getenv("CATLINK_IP_GEO_ENDPOINT"))
	if endpoint == "" || !strings.Contains(endpoint, "{ip}") {
		return nearbyArea{}, fmt.Errorf("IP geocoder endpoint is not configured")
	}
	parsed, err := url.Parse(strings.ReplaceAll(endpoint, "{ip}", url.PathEscape(ip)))
	if err != nil || parsed.Scheme != "https" || parsed.Host == "" || parsed.User != nil {
		return nearbyArea{}, fmt.Errorf("invalid IP geocoder endpoint")
	}
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, parsed.String(), nil)
	if err != nil {
		return nearbyArea{}, err
	}
	request.Header.Set("Accept", "application/json")
	response, err := (&http.Client{
		Timeout:       5 * time.Second,
		CheckRedirect: rejectGeocoderRedirect,
	}).Do(request)
	if err != nil {
		return nearbyArea{}, err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return nearbyArea{}, fmt.Errorf("IP geocoder status %d", response.StatusCode)
	}
	var payload struct {
		City       string `json:"city"`
		District   string `json:"district"`
		Region     string `json:"region"`
		RegionName string `json:"regionName"`
	}
	if err := json.NewDecoder(io.LimitReader(response.Body, 64<<10)).Decode(&payload); err != nil {
		return nearbyArea{}, err
	}
	city := strings.TrimSpace(payload.City)
	district := firstValue(map[string]string{"district": payload.District, "region": payload.RegionName, "region2": payload.Region}, "district", "region", "region2")
	if city == "" || len([]rune(city)) > 80 || len([]rune(district)) > 80 {
		return nearbyArea{}, fmt.Errorf("incomplete IP geocoder result")
	}
	return nearbyArea{City: city, District: district, Attribution: "IP 粗略定位"}, nil
}

type cachedNearbyArea struct {
	Area      nearbyArea
	ExpiresAt time.Time
}

const (
	locationCacheTTL        = 24 * time.Hour
	locationCacheMaxEntries = 4096
)

var ipGeocoder = struct {
	sync.Mutex
	Cache map[string]cachedNearbyArea
}{Cache: make(map[string]cachedNearbyArea)}

var geocoder = struct {
	sync.Mutex
	NextRequest time.Time
	Cache       map[string]cachedNearbyArea
}{Cache: make(map[string]cachedNearbyArea)}

func cachedArea(cache map[string]cachedNearbyArea, key string, now time.Time) (nearbyArea, bool) {
	cached, found := cache[key]
	if !found {
		return nearbyArea{}, false
	}
	if !now.Before(cached.ExpiresAt) {
		delete(cache, key)
		return nearbyArea{}, false
	}
	return cached.Area, true
}

func storeCachedArea(cache map[string]cachedNearbyArea, key string, area nearbyArea, now time.Time) {
	if len(cache) >= locationCacheMaxEntries {
		for cachedKey, cached := range cache {
			if !now.Before(cached.ExpiresAt) {
				delete(cache, cachedKey)
			}
		}
	}
	for len(cache) >= locationCacheMaxEntries {
		for cachedKey := range cache {
			delete(cache, cachedKey)
			break
		}
	}
	cache[key] = cachedNearbyArea{Area: area, ExpiresAt: now.Add(locationCacheTTL)}
}

func (a *CatCommunityApi) ReverseLocation(c *gin.Context) {
	lat, latErr := strconv.ParseFloat(c.Query("lat"), 64)
	lng, lngErr := strconv.ParseFloat(c.Query("lng"), 64)
	if latErr != nil || lngErr != nil || lat < -90 || lat > 90 || lng < -180 || lng > 180 {
		fail(c, "定位坐标无效")
		return
	}

	area, err := resolveNearbyArea(c.Request.Context(), lat, lng)
	if err != nil {
		fail(c, "暂时无法识别当前位置")
		return
	}
	ok(c, area)
}

func resolveNearbyArea(ctx context.Context, lat, lng float64) (nearbyArea, error) {
	key := fmt.Sprintf("%.3f,%.3f", lat, lng)
	geocoder.Lock()
	if cached, found := cachedArea(geocoder.Cache, key, time.Now()); found {
		geocoder.Unlock()
		return cached, nil
	}
	scheduledAt := time.Now()
	if geocoder.NextRequest.After(scheduledAt) {
		scheduledAt = geocoder.NextRequest
	}
	geocoder.NextRequest = scheduledAt.Add(time.Second)
	geocoder.Unlock()
	wait := time.Until(scheduledAt)
	if wait > 0 {
		timer := time.NewTimer(wait)
		defer timer.Stop()
		select {
		case <-ctx.Done():
			return nearbyArea{}, ctx.Err()
		case <-timer.C:
		}
	}

	area, err := requestNearbyArea(ctx, lat, lng)
	if err != nil {
		return nearbyArea{}, err
	}
	geocoder.Lock()
	storeCachedArea(geocoder.Cache, key, area, time.Now())
	geocoder.Unlock()
	return area, nil
}

func requestNearbyArea(ctx context.Context, lat, lng float64) (nearbyArea, error) {
	endpoint := strings.TrimSpace(os.Getenv("CATLINK_GEOCODER_URL"))
	if endpoint == "" {
		endpoint = "https://nominatim.openstreetmap.org/reverse"
	}
	parsed, err := url.Parse(endpoint)
	if err != nil || parsed.Scheme != "https" || parsed.Host == "" || parsed.User != nil {
		return nearbyArea{}, fmt.Errorf("invalid geocoder endpoint")
	}
	query := parsed.Query()
	query.Set("format", "jsonv2")
	query.Set("lat", strconv.FormatFloat(lat, 'f', 6, 64))
	query.Set("lon", strconv.FormatFloat(lng, 'f', 6, 64))
	query.Set("zoom", "12")
	query.Set("addressdetails", "1")
	query.Set("layer", "address")
	parsed.RawQuery = query.Encode()

	request, err := http.NewRequestWithContext(ctx, http.MethodGet, parsed.String(), nil)
	if err != nil {
		return nearbyArea{}, err
	}
	request.Header.Set("User-Agent", "CatLink/1.0 (+https://catlink.app)")
	request.Header.Set("Accept-Language", "zh-CN")
	response, err := (&http.Client{
		Timeout:       8 * time.Second,
		CheckRedirect: rejectGeocoderRedirect,
	}).Do(request)
	if err != nil {
		return nearbyArea{}, err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return nearbyArea{}, fmt.Errorf("geocoder status %d", response.StatusCode)
	}
	var payload struct {
		DisplayName string            `json:"display_name"`
		Address     map[string]string `json:"address"`
	}
	if err := json.NewDecoder(io.LimitReader(response.Body, 64<<10)).Decode(&payload); err != nil {
		return nearbyArea{}, err
	}
	area := areaFromGeocoder(payload.Address, payload.DisplayName)
	if area.City == "" || area.District == "" {
		return nearbyArea{}, fmt.Errorf("incomplete geocoder result")
	}
	area.Attribution = "位置数据 © OpenStreetMap 贡献者"
	return area, nil
}

func rejectGeocoderRedirect(*http.Request, []*http.Request) error {
	return http.ErrUseLastResponse
}

func areaFromGeocoder(address map[string]string, displayName string) nearbyArea {
	area := nearbyArea{
		City:     firstValue(address, "municipality", "city", "town", "county"),
		District: firstValue(address, "city_district", "district", "county"),
	}
	for _, raw := range strings.Split(displayName, ",") {
		part := strings.TrimSpace(raw)
		if strings.HasSuffix(part, "市") && (area.City == "" || strings.HasSuffix(area.City, "区")) {
			area.City = part
		}
		if area.District == "" && (strings.HasSuffix(part, "区") || strings.HasSuffix(part, "县") || strings.HasSuffix(part, "旗")) {
			area.District = part
		}
	}
	if area.District == "" {
		area.District = firstValue(address, "suburb")
	}
	if area.District == area.City {
		area.District = ""
	}
	return area
}

func firstValue(values map[string]string, keys ...string) string {
	for _, key := range keys {
		if value := strings.TrimSpace(values[key]); value != "" {
			return value
		}
	}
	return ""
}
