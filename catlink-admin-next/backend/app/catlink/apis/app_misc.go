package apis

import (
	"context"
	"crypto/sha256"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"math/rand"
	"net"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"

	"go-admin/app/catlink/service"

	"github.com/gin-gonic/gin"
	"github.com/go-admin-team/go-admin-core/v2/sdk/pkg"
)

func AppLocation(c *gin.Context) {
	provider := service.LocationProviderConfig{}
	if db, dbErr := pkg.GetOrm(c); dbErr == nil {
		provider, _ = service.LoadLocationProviderConfig(db)
	}
	var area locationArea
	var err error
	if strings.HasSuffix(c.FullPath(), "/reverse") {
		area, err = reverseLocationWithProvider(c.Request.Context(), c.Query("lat"), c.Query("lng"), provider)
	} else {
		area, err = ipLocation(c, provider)
	}
	if err != nil {
		appFailure(c, "暂时无法定位所在城市，请手动选择")
		return
	}
	appSuccess(c, area)
}

type locationArea struct {
	City        string   `json:"city"`
	District    string   `json:"district"`
	Attribution string   `json:"attribution,omitempty"`
	Province    string   `json:"province,omitempty"`
	Street      string   `json:"street,omitempty"`
	Community   string   `json:"community,omitempty"`
	Latitude    *float64 `json:"latitude,omitempty"`
	Longitude   *float64 `json:"longitude,omitempty"`
}

type locationSuggestion struct {
	ID      string `json:"id,omitempty"`
	Label   string `json:"label"`
	Address string `json:"address,omitempty"`
	locationArea
}

type locationSuggestionPage struct {
	Items    []locationSuggestion `json:"items"`
	Total    int                  `json:"total"`
	Page     int                  `json:"page"`
	PageSize int                  `json:"pageSize"`
	HasMore  bool                 `json:"hasMore"`
}

func AppLocationSearch(c *gin.Context) {
	keyword := strings.TrimSpace(c.Query("q"))
	if len([]rune(keyword)) < 2 || len([]rune(keyword)) > 80 {
		appFailure(c, "请输入至少两个字的地点")
		return
	}
	city := strings.TrimSpace(c.Query("city"))
	if len([]rune(city)) > 80 {
		appFailure(c, "城市名称无效")
		return
	}
	district := strings.TrimSpace(c.Query("district"))
	if len([]rune(district)) > 80 {
		appFailure(c, "区县名称无效")
		return
	}
	cityCode, districtCode := strings.TrimSpace(c.Query("cityCode")), strings.TrimSpace(c.Query("districtCode"))
	if !validRegionCode(cityCode) || !validRegionCode(districtCode) {
		appFailure(c, "地区代码无效")
		return
	}
	page := locationQueryInt(c.Query("page"), 1, 1, 100)
	pageSize := locationQueryInt(c.Query("pageSize"), 20, 1, 20)
	provider := service.LocationProviderConfig{}
	if db, dbErr := pkg.GetOrm(c); dbErr == nil {
		if loaded, loadErr := service.LoadLocationProviderConfig(db); loadErr == nil {
			provider = loaded
		}
	}
	kind := locationProviderKind(provider)
	cacheKey := locationSearchCacheKey(kind, provider.SearchURL, keyword, city, district, cityCode, districtCode, strconv.Itoa(page), strconv.Itoa(pageSize))
	if result, ok := loadLocationSearchCache(cacheKey); ok {
		appSuccess(c, result)
		return
	}
	parsed, err := locationSearchPageURL(provider, kind, keyword, city, district, cityCode, districtCode, page, pageSize)
	if err != nil {
		appFailure(c, locationProviderError(err))
		return
	}
	ctx, cancel := context.WithTimeout(c.Request.Context(), 7*time.Second)
	defer cancel()
	result, err := requestLocationSuggestionPage(ctx, parsed.String(), kind, page, pageSize, city)
	if err != nil {
		appFailure(c, locationProviderError(err))
		return
	}
	storeLocationSearchCache(cacheKey, result)
	appSuccess(c, result)
}

func locationQueryInt(raw string, fallback, minimum, maximum int) int {
	value, err := strconv.Atoi(strings.TrimSpace(raw))
	if err != nil || value < minimum {
		return fallback
	}
	if value > maximum {
		return maximum
	}
	return value
}

func validRegionCode(value string) bool {
	if value == "" {
		return true
	}
	if len(value) != 2 && len(value) != 4 && len(value) != 6 {
		return false
	}
	for _, char := range value {
		if char < '0' || char > '9' {
			return false
		}
	}
	return true
}

type cachedLocation struct {
	Area      locationArea
	ExpiresAt time.Time
}

var locationCache = struct {
	sync.Mutex
	Items map[string]cachedLocation
}{Items: map[string]cachedLocation{}}

type cachedLocationSuggestions struct {
	Result    locationSuggestionPage
	ExpiresAt time.Time
}

var locationSearchCache = struct {
	sync.Mutex
	Items map[string]cachedLocationSuggestions
}{Items: map[string]cachedLocationSuggestions{}}

func locationSearchCacheKey(kind, endpoint, keyword, city string, qualifiers ...string) string {
	parts := append([]string{kind, endpoint, keyword, city}, qualifiers...)
	for index := range parts {
		parts[index] = strings.TrimSpace(parts[index])
	}
	digest := sha256.Sum256([]byte(strings.Join(parts, "\x00")))
	return fmt.Sprintf("%x", digest[:])
}

func loadLocationSearchCache(key string) (locationSuggestionPage, bool) {
	now := time.Now()
	locationSearchCache.Lock()
	defer locationSearchCache.Unlock()
	entry, ok := locationSearchCache.Items[key]
	if !ok || !now.Before(entry.ExpiresAt) {
		delete(locationSearchCache.Items, key)
		return locationSuggestionPage{}, false
	}
	result := entry.Result
	result.Items = append([]locationSuggestion(nil), entry.Result.Items...)
	return result, true
}

func storeLocationSearchCache(key string, result locationSuggestionPage) {
	now := time.Now()
	locationSearchCache.Lock()
	defer locationSearchCache.Unlock()
	if len(locationSearchCache.Items) >= 256 {
		for cachedKey, entry := range locationSearchCache.Items {
			if !now.Before(entry.ExpiresAt) || len(locationSearchCache.Items) >= 256 {
				delete(locationSearchCache.Items, cachedKey)
			}
			if len(locationSearchCache.Items) < 256 {
				break
			}
		}
	}
	result.Items = append([]locationSuggestion(nil), result.Items...)
	locationSearchCache.Items[key] = cachedLocationSuggestions{Result: result, ExpiresAt: now.Add(5 * time.Minute)}
}

func locationProviderError(err error) string {
	message := strings.ToLower(err.Error())
	switch {
	case strings.Contains(message, "key 未配置"), strings.Contains(message, "ak 未配置"):
		return "地图服务未配置，请在服务端定位设置中填写对应 Key"
	case strings.Contains(message, "invalid_user_key"), strings.Contains(message, "ak参数不存在"), strings.Contains(message, "权限"), strings.Contains(message, "配额"):
		return "地图服务授权失败，请检查 Key、接口权限和配额"
	case strings.Contains(message, "context deadline"), strings.Contains(message, "timeout"):
		return "地图搜索超时，请稍后重试"
	default:
		return "地图搜索暂不可用，请检查服务端地图配置"
	}
}

func ipLocation(c *gin.Context, provider service.LocationProviderConfig) (locationArea, error) {
	trustHeaders := provider.TrustProxyHeaders || strings.EqualFold(os.Getenv("CATLINK_TRUST_IP_GEO_HEADERS"), "true")
	if trustHeaders && trustedProxyRequest(c.Request.RemoteAddr) {
		area := locationArea{City: strings.TrimSpace(c.GetHeader("X-Geo-City")), District: strings.TrimSpace(c.GetHeader("X-Geo-District")), Attribution: "IP 粗略定位"}
		if validArea(area) {
			return area, nil
		}
	}
	ip := net.ParseIP(c.ClientIP())
	if ip == nil || ip.IsLoopback() || ip.IsPrivate() || ip.IsUnspecified() {
		return locationArea{}, errors.New("IP 无法定位")
	}
	endpointIdentity := strings.TrimSpace(provider.IPURL)
	if endpointIdentity == "" {
		endpointIdentity = strings.TrimSpace(os.Getenv("CATLINK_IP_GEO_ENDPOINT"))
	}
	cacheKey := locationSearchCacheKey(locationProviderKind(provider), endpointIdentity, ip.String(), "")
	return cachedLocationRequest(cacheKey, func() (locationArea, error) {
		endpoint := strings.TrimSpace(provider.IPURL)
		if endpoint == "" {
			endpoint = strings.TrimSpace(os.Getenv("CATLINK_IP_GEO_ENDPOINT"))
		}
		if endpoint == "" || !strings.Contains(endpoint, "{ip}") {
			return locationArea{}, errors.New("IP 定位服务未配置")
		}
		return requestLocation(c.Request.Context(), strings.ReplaceAll(endpoint, "{ip}", url.PathEscape(ip.String())), "IP 粗略定位")
	})
}

const (
	locationHTTPTimeout   = 6 * time.Second
	locationCacheTTL      = 24 * time.Hour
	locationCacheMaxItems = 4096
)

// reverseLocation keeps the old internal signature for compatibility.
func reverseLocation(ctx context.Context, latitude, longitude, configuredEndpoint string) (locationArea, error) {
	return reverseLocationWithProvider(ctx, latitude, longitude, service.LocationProviderConfig{ReverseURL: configuredEndpoint})
}

func reverseLocationWithProvider(ctx context.Context, latitude, longitude string, provider service.LocationProviderConfig) (locationArea, error) {
	lat, latErr := strconv.ParseFloat(latitude, 64)
	lng, lngErr := strconv.ParseFloat(longitude, 64)
	if latErr != nil || lngErr != nil || lat < -90 || lat > 90 || lng < -180 || lng > 180 {
		return locationArea{}, errors.New("坐标无效")
	}
	kind := locationProviderKind(provider)
	key := locationSearchCacheKey(kind, provider.ReverseURL, fmt.Sprintf("%.3f", lat), fmt.Sprintf("%.3f", lng))
	return cachedLocationRequest(key, func() (locationArea, error) {
		endpoint, err := locationReverseURL(provider, kind, lat, lng)
		if err != nil {
			return locationArea{}, err
		}
		if kind == "baidu" {
			return requestBaiduReverse(ctx, endpoint.String(), lat, lng)
		}
		attribution := "位置数据 © OpenStreetMap 贡献者"
		if kind == "amap" {
			attribution = "高德地图"
		}
		return requestLocation(ctx, endpoint.String(), attribution)
	})
}

func locationProviderKind(provider service.LocationProviderConfig) string {
	kind := strings.ToLower(strings.TrimSpace(provider.Provider))
	if kind == "amap" || kind == "baidu" || kind == "osm" || kind == "custom" {
		return kind
	}
	joined := strings.ToLower(provider.SearchURL + " " + provider.ReverseURL)
	if strings.Contains(joined, "restapi.amap.com") {
		return "amap"
	}
	if strings.Contains(joined, "api.map.baidu.com") {
		return "baidu"
	}
	return "osm"
}

func locationProviderKey(provider service.LocationProviderConfig, kind string) string {
	key := strings.TrimSpace(provider.APIKey)
	if kind == "amap" {
		if strings.TrimSpace(provider.AmapKey) != "" {
			key = strings.TrimSpace(provider.AmapKey)
		}
		if key == "" {
			key = strings.TrimSpace(os.Getenv("CATLINK_AMAP_KEY"))
		}
	} else if kind == "baidu" {
		if strings.TrimSpace(provider.BaiduAK) != "" {
			key = strings.TrimSpace(provider.BaiduAK)
		}
		if key == "" {
			key = strings.TrimSpace(os.Getenv("CATLINK_BAIDU_AK"))
		}
	}
	return key
}

func providerEndpoint(provider service.LocationProviderConfig, kind, operation string) (string, error) {
	endpoint := strings.TrimSpace(provider.SearchURL)
	if operation == "reverse" {
		endpoint = strings.TrimSpace(provider.ReverseURL)
	}
	if endpoint == "" {
		switch kind {
		case "amap":
			if operation == "reverse" {
				endpoint = "https://restapi.amap.com/v3/geocode/regeo"
			} else {
				endpoint = "https://restapi.amap.com/v5/place/text"
			}
		case "baidu":
			if operation == "reverse" {
				endpoint = "https://api.map.baidu.com/reverse_geocoding/v3/"
			} else {
				endpoint = "https://api.map.baidu.com/place/v3/region"
			}
		case "osm":
			if operation == "reverse" {
				endpoint = strings.TrimSpace(os.Getenv("CATLINK_GEOCODER_URL"))
				if endpoint == "" {
					endpoint = "https://nominatim.openstreetmap.org/reverse"
				}
			} else {
				endpoint = strings.TrimSpace(os.Getenv("CATLINK_GEOCODER_SEARCH_URL"))
				if endpoint == "" {
					endpoint = "https://nominatim.openstreetmap.org/search"
				}
			}
		default:
			return "", errors.New("定位服务地址未配置")
		}
	}
	parsed, err := secureLocationURL(endpoint)
	if err != nil {
		return "", err
	}
	if officialHost := map[string]string{
		"amap":  "restapi.amap.com",
		"baidu": "api.map.baidu.com",
		"osm":   "nominatim.openstreetmap.org",
	}[kind]; officialHost != "" && (!strings.EqualFold(parsed.Hostname(), officialHost) || parsed.Port() != "" && parsed.Port() != "443") {
		return "", errors.New("内置地图服务必须使用官方地址")
	}
	if kind == "amap" && strings.EqualFold(parsed.Hostname(), "restapi.amap.com") {
		if operation == "reverse" {
			parsed.Path = "/v3/geocode/regeo"
		} else {
			parsed.Path = "/v5/place/text"
		}
	}
	if kind == "baidu" && strings.EqualFold(parsed.Hostname(), "api.map.baidu.com") {
		if operation == "reverse" {
			parsed.Path = "/reverse_geocoding/v3/"
		} else {
			parsed.Path = "/place/v3/region"
		}
	}
	return parsed.String(), nil
}

func locationSearchURL(provider service.LocationProviderConfig, kind, keyword, city string, district ...string) (*url.URL, error) {
	selectedDistrict := ""
	if len(district) > 0 {
		selectedDistrict = strings.TrimSpace(district[0])
	}
	return locationSearchPageURL(provider, kind, keyword, city, selectedDistrict, "", "", 1, 20)
}

func locationSearchPageURL(provider service.LocationProviderConfig, kind, keyword, city, district, cityCode, districtCode string, page, pageSize int) (*url.URL, error) {
	endpoint, err := providerEndpoint(provider, kind, "search")
	if err != nil {
		return nil, err
	}
	parsed, err := secureLocationURL(endpoint)
	if err != nil {
		return nil, err
	}
	query := parsed.Query()
	switch kind {
	case "amap":
		region := firstLocation(amapRegionCode(districtCode), amapRegionCode(cityCode), city)
		if district != "" && districtCode == "" {
			keyword = strings.TrimSpace(keyword + " " + district)
		}
		query.Set("keywords", keyword)
		query.Set("output", "json")
		if strings.HasPrefix(parsed.Path, "/v5/") {
			query.Set("page_size", strconv.Itoa(pageSize))
			query.Set("page_num", strconv.Itoa(page))
			if region != "" {
				query.Set("region", region)
				query.Set("city_limit", "true")
			}
		} else {
			query.Set("offset", strconv.Itoa(pageSize))
			query.Set("page", strconv.Itoa(page))
			if region != "" {
				query.Set("city", region)
				query.Set("citylimit", "true")
			}
		}
		if key := locationProviderKey(provider, kind); key != "" {
			query.Set("key", key)
		}
		if query.Get("key") == "" {
			return nil, errors.New("高德地图 Key 未配置")
		}
	case "baidu":
		query.Set("query", strings.TrimSpace(strings.Join([]string{keyword, district}, " ")))
		query.Set("output", "json")
		query.Set("page_num", strconv.Itoa(page-1))
		query.Set("page_size", strconv.Itoa(pageSize))
		query.Set("ret_coordtype", "gcj02ll")
		region := firstLocation(city, district)
		if region != "" {
			query.Set("region", region)
			query.Set("region_limit", "true")
		} else {
			// 百度 Suggestion API requires region; 全国 keeps the fallback
			// search valid without silently narrowing it to an arbitrary city.
			query.Set("region", "全国")
			query.Set("region_limit", "false")
		}
		if key := locationProviderKey(provider, kind); key != "" {
			query.Set("ak", key)
		}
		if query.Get("ak") == "" {
			return nil, errors.New("百度地图 AK 未配置")
		}
	default:
		q := strings.TrimSpace(strings.Join([]string{city, district, keyword}, " "))
		query.Set("q", q)
		query.Set("format", "jsonv2")
		query.Set("addressdetails", "1")
		query.Set("limit", strconv.Itoa(pageSize))
		query.Set("offset", strconv.Itoa((page-1)*pageSize))
	}
	parsed.RawQuery = query.Encode()
	return parsed, nil
}

func amapRegionCode(value string) string {
	value = strings.TrimSpace(value)
	if len(value) == 2 {
		return value + "0000"
	}
	if len(value) == 4 {
		return value + "00"
	}
	return value
}

func locationReverseURL(provider service.LocationProviderConfig, kind string, lat, lng float64) (*url.URL, error) {
	endpoint, err := providerEndpoint(provider, kind, "reverse")
	if err != nil {
		return nil, err
	}
	parsed, err := secureLocationURL(endpoint)
	if err != nil {
		return nil, err
	}
	query := parsed.Query()
	switch kind {
	case "amap":
		query.Set("location", fmt.Sprintf("%f,%f", lng, lat))
		query.Set("output", "json")
		query.Set("extensions", "all")
		if key := locationProviderKey(provider, kind); key != "" {
			query.Set("key", key)
		}
		if query.Get("key") == "" {
			return nil, errors.New("高德地图 Key 未配置")
		}
	case "baidu":
		query.Set("location", fmt.Sprintf("%f,%f", lat, lng))
		query.Set("coordtype", "wgs84ll")
		query.Set("ret_coordtype", "gcj02ll")
		query.Set("output", "json")
		query.Set("extensions_poi", "0")
		if key := locationProviderKey(provider, kind); key != "" {
			query.Set("ak", key)
		}
		if query.Get("ak") == "" {
			return nil, errors.New("百度地图 AK 未配置")
		}
	default:
		query.Set("format", "jsonv2")
		query.Set("lat", strconv.FormatFloat(lat, 'f', 6, 64))
		query.Set("lon", strconv.FormatFloat(lng, 'f', 6, 64))
		query.Set("zoom", "18")
		query.Set("addressdetails", "1")
	}
	parsed.RawQuery = query.Encode()
	return parsed, nil
}

func requestLocation(ctx context.Context, target, attribution string) (locationArea, error) {
	parsed, err := secureLocationURL(target)
	if err != nil {
		return locationArea{}, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, parsed.String(), nil)
	if err != nil {
		return locationArea{}, err
	}
	req.Header.Set("User-Agent", "CatLink/1.0 (+https://api.example.com)")
	req.Header.Set("Accept-Language", "zh-CN")
	res, err := (&http.Client{Timeout: locationHTTPTimeout, CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}).Do(req)
	if err != nil {
		return locationArea{}, err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return locationArea{}, fmt.Errorf("定位服务状态 %d", res.StatusCode)
	}
	var payload struct {
		City, District, Region, RegionName, DisplayName string
		Lat, Lon                                        string
		Latitude, Longitude                             float64
		Address                                         map[string]string
		Status                                          string `json:"status"`
		Regeocode                                       struct {
			AddressComponent struct {
				Province, District, Township string
				Neighborhood                 json.RawMessage
				City                         json.RawMessage
				StreetNumber                 struct {
					Street string `json:"street"`
				} `json:"streetNumber"`
			} `json:"addressComponent"`
			Location string `json:"location"`
		} `json:"regeocode"`
	}
	body, readErr := io.ReadAll(io.LimitReader(res.Body, 64<<10))
	if readErr != nil || json.Unmarshal(body, &payload) != nil {
		return locationArea{}, errors.New("定位响应无效")
	}
	if strings.EqualFold(attribution, "高德地图") && payload.Status != "" && payload.Status != "1" {
		return locationArea{}, fmt.Errorf("高德地图响应无效")
	}
	if payload.Regeocode.AddressComponent.Province != "" {
		component := payload.Regeocode.AddressComponent
		city := stringFromJSON(component.City)
		payload.Address = map[string]string{"state": component.Province, "city": city, "district": component.District, "town": component.Township, "neighbourhood": amapText(component.Neighborhood), "road": component.StreetNumber.Street}
		parts := strings.SplitN(payload.Regeocode.Location, ",", 2)
		if len(parts) == 2 {
			payload.Lon, payload.Lat = parts[0], parts[1]
		}
	}
	area := locationArea{
		City:        firstLocation(payload.City, payload.Address["municipality"], payload.Address["city"], payload.Address["town"], payload.Address["county"]),
		District:    firstLocation(payload.District, payload.RegionName, payload.Region, payload.Address["city_district"], payload.Address["district"], payload.Address["county"], payload.Address["suburb"]),
		Province:    firstLocation(payload.Address["state"], payload.Address["province"]),
		Street:      firstLocation(payload.Address["town"], payload.Address["village"], payload.Address["road"], payload.Address["pedestrian"]),
		Community:   firstLocation(payload.Address["neighbourhood"], payload.Address["residential"], payload.Address["quarter"], payload.Address["suburb"]),
		Attribution: attribution,
	}
	lat, latErr := strconv.ParseFloat(payload.Lat, 64)
	lng, lngErr := strconv.ParseFloat(payload.Lon, 64)
	if latErr != nil && payload.Latitude != 0 {
		lat, latErr = payload.Latitude, nil
	}
	if lngErr != nil && payload.Longitude != 0 {
		lng, lngErr = payload.Longitude, nil
	}
	if latErr == nil && lngErr == nil {
		lat, lng = roundedCoordinate(lat), roundedCoordinate(lng)
		area.Latitude, area.Longitude = &lat, &lng
	}
	if area.City == area.District {
		area.District = ""
	}
	if !validArea(area) {
		return locationArea{}, errors.New("定位信息不完整")
	}
	return area, nil
}

func requestLocationSuggestions(ctx context.Context, target string) ([]locationSuggestion, error) {
	kind := "custom"
	if parsed, err := secureLocationURL(target); err == nil {
		switch strings.ToLower(parsed.Hostname()) {
		case "restapi.amap.com":
			kind = "amap"
		case "api.map.baidu.com":
			kind = "baidu"
		}
	}
	return requestLocationSuggestionsWithProvider(ctx, target, kind)
}

func requestLocationSuggestionsWithProvider(ctx context.Context, target, kind string) ([]locationSuggestion, error) {
	result, err := requestLocationSuggestionPage(ctx, target, kind, 1, 20)
	return result.Items, err
}

func requestLocationSuggestionPage(ctx context.Context, target, kind string, page, pageSize int, fallbackCity ...string) (locationSuggestionPage, error) {
	parsed, err := secureLocationURL(target)
	if err != nil {
		return locationSuggestionPage{}, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, parsed.String(), nil)
	if err != nil {
		return locationSuggestionPage{}, err
	}
	req.Header.Set("User-Agent", "CatLink/1.0 (+https://api.example.com)")
	req.Header.Set("Accept-Language", "zh-CN")
	res, err := (&http.Client{Timeout: locationHTTPTimeout, CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}).Do(req)
	if err != nil {
		return locationSuggestionPage{}, err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return locationSuggestionPage{}, fmt.Errorf("地点搜索服务状态 %d", res.StatusCode)
	}
	body, err := io.ReadAll(io.LimitReader(res.Body, 256<<10))
	if err != nil {
		return locationSuggestionPage{}, err
	}
	var result locationSuggestionPage
	city := firstLocation(fallbackCity...)
	switch kind {
	case "amap":
		result, err = parseAmapSuggestionPage(body, firstLocation(city, parsed.Query().Get("region"), parsed.Query().Get("city")), page, pageSize, strings.HasPrefix(parsed.Path, "/v5/"))
	case "baidu":
		result, err = parseBaiduSuggestionPage(body, firstLocation(city, parsed.Query().Get("region")), page, pageSize)
	default:
		result, err = parseOSMSuggestionPage(body, page, pageSize)
	}
	return result, err
}

type amapPOI struct {
	ID           string          `json:"id"`
	Name         string          `json:"name"`
	Address      json.RawMessage `json:"address"`
	Province     json.RawMessage `json:"pname"`
	City         json.RawMessage `json:"cityname"`
	District     json.RawMessage `json:"adname"`
	Town         json.RawMessage `json:"townname"`
	BusinessArea json.RawMessage `json:"business_area"`
	Location     string          `json:"location"`
}

type amapSearchResponse struct {
	Status   string          `json:"status"`
	Info     string          `json:"info"`
	Count    json.RawMessage `json:"count"`
	Pois     []amapPOI       `json:"pois"`
	Geocodes []struct {
		FormattedAddress string          `json:"formatted_address"`
		Province         string          `json:"province"`
		City             string          `json:"city"`
		District         string          `json:"district"`
		Township         json.RawMessage `json:"township"`
		Neighborhood     json.RawMessage `json:"neighborhood"`
		Location         string          `json:"location"`
	} `json:"geocodes"`
}

type baiduPOI struct {
	UID      string `json:"uid"`
	Name     string `json:"name"`
	Province string `json:"province"`
	City     string `json:"city"`
	District string `json:"district"`
	Area     string `json:"area"`
	Town     string `json:"town"`
	Address  string `json:"address"`
	Location struct {
		Lat float64 `json:"lat"`
		Lng float64 `json:"lng"`
	} `json:"location"`
}

type baiduSearchResponse struct {
	Status      int        `json:"status"`
	Message     string     `json:"message"`
	Total       int        `json:"total"`
	Results     []baiduPOI `json:"results"`
	Suggestions []baiduPOI `json:"result"`
}

func parseAmapSuggestions(body []byte, city string) ([]locationSuggestion, error) {
	result, err := parseAmapSuggestionPage(body, city, 1, 20, false)
	return result.Items, err
}

func parseAmapSuggestionPage(body []byte, city string, page, pageSize int, isV5 bool) (locationSuggestionPage, error) {
	var payload amapSearchResponse
	if err := json.Unmarshal(body, &payload); err != nil || payload.Status != "1" {
		if payload.Info != "" {
			return locationSuggestionPage{}, fmt.Errorf("高德地图: %s", payload.Info)
		}
		return locationSuggestionPage{}, errors.New("高德地图响应无效")
	}
	items := make([]locationSuggestion, 0, len(payload.Pois)+len(payload.Geocodes))
	for _, poi := range payload.Pois {
		lat, lng := parseLonLat(poi.Location)
		resolvedCity := firstLocation(stringFromJSON(poi.City), city)
		province := amapText(poi.Province)
		district := amapText(poi.District)
		address := amapText(poi.Address)
		fullAddress := locationFullAddress(province, resolvedCity, district, address)
		label := strings.TrimSpace(poi.Name)
		area := locationArea{Province: province, City: resolvedCity, District: district, Street: firstLocation(amapText(poi.Town), address), Community: firstLocation(poi.Name, amapText(poi.BusinessArea)), Attribution: "高德地图"}
		if lat != nil && lng != nil {
			area.Latitude, area.Longitude = lat, lng
		}
		if area.City != "" && label != "" {
			items = append(items, locationSuggestion{ID: poi.ID, Label: label, Address: fullAddress, locationArea: area})
		}
	}
	for _, row := range payload.Geocodes {
		lat, lng := parseLonLat(row.Location)
		area := locationArea{Province: row.Province, City: firstLocation(row.City, city), District: row.District, Street: amapText(row.Township), Community: amapText(row.Neighborhood), Attribution: "高德地图", Latitude: lat, Longitude: lng}
		if area.City != "" {
			items = append(items, locationSuggestion{Label: row.FormattedAddress, Address: row.FormattedAddress, locationArea: area})
		}
	}
	items = dedupeLocationSuggestions(items)
	total := rawJSONInt(payload.Count)
	hasMore := page*pageSize < total
	if isV5 {
		total, hasMore = -1, len(payload.Pois) >= pageSize
	}
	return locationSuggestionPage{Items: items, Total: total, Page: page, PageSize: pageSize, HasMore: hasMore}, nil
}

func parseBaiduSuggestions(body []byte, city string) ([]locationSuggestion, error) {
	result, err := parseBaiduSuggestionPage(body, city, 1, 20)
	return result.Items, err
}

func parseBaiduSuggestionPage(body []byte, city string, page, pageSize int) (locationSuggestionPage, error) {
	var payload baiduSearchResponse
	if err := json.Unmarshal(body, &payload); err != nil || payload.Status != 0 {
		if payload.Message != "" {
			return locationSuggestionPage{}, fmt.Errorf("百度地图: %s", payload.Message)
		}
		return locationSuggestionPage{}, errors.New("百度地图响应无效")
	}
	rows := payload.Results
	if len(rows) == 0 {
		rows = payload.Suggestions
	}
	items := make([]locationSuggestion, 0, len(rows))
	for _, row := range rows {
		resolvedCity := firstLocation(row.City, city)
		label := strings.TrimSpace(row.Name)
		fullAddress := locationFullAddress(row.Province, resolvedCity, firstLocation(row.District, row.Area), row.Address)
		area := locationArea{Province: row.Province, City: resolvedCity, District: firstLocation(row.District, row.Area), Street: row.Town, Community: row.Name, Attribution: "百度地图"}
		if row.Location.Lat >= -90 && row.Location.Lat <= 90 && row.Location.Lng >= -180 && row.Location.Lng <= 180 && (row.Location.Lat != 0 || row.Location.Lng != 0) {
			lat, lng := roundedCoordinate(row.Location.Lat), roundedCoordinate(row.Location.Lng)
			area.Latitude, area.Longitude = &lat, &lng
		}
		if area.City != "" && label != "" {
			items = append(items, locationSuggestion{ID: row.UID, Label: label, Address: fullAddress, locationArea: area})
		}
	}
	items = dedupeLocationSuggestions(items)
	return locationSuggestionPage{Items: items, Total: payload.Total, Page: page, PageSize: pageSize, HasMore: page*pageSize < payload.Total}, nil
}

func parseOSMSuggestions(body []byte) ([]locationSuggestion, error) {
	result, err := parseOSMSuggestionPage(body, 1, 20)
	return result.Items, err
}

func parseOSMSuggestionPage(body []byte, page, pageSize int) (locationSuggestionPage, error) {
	var rows []struct {
		PlaceID     json.RawMessage   `json:"place_id"`
		DisplayName string            `json:"display_name"`
		Lat         string            `json:"lat"`
		Lon         string            `json:"lon"`
		Address     map[string]string `json:"address"`
	}
	if err := json.Unmarshal(body, &rows); err != nil {
		return locationSuggestionPage{}, errors.New("地点搜索响应无效")
	}
	items := make([]locationSuggestion, 0, len(rows))
	for _, row := range rows {
		lat, latErr := strconv.ParseFloat(row.Lat, 64)
		lng, lngErr := strconv.ParseFloat(row.Lon, 64)
		area := locationArea{Province: firstLocation(row.Address["state"], row.Address["province"]), City: firstLocation(row.Address["municipality"], row.Address["city"], row.Address["town"], row.Address["county"]), District: firstLocation(row.Address["city_district"], row.Address["district"], row.Address["county"], row.Address["suburb"]), Street: firstLocation(row.Address["town"], row.Address["village"], row.Address["road"], row.Address["pedestrian"]), Community: firstLocation(row.Address["neighbourhood"], row.Address["residential"], row.Address["quarter"], row.Address["suburb"]), Attribution: "位置数据 © OpenStreetMap 贡献者"}
		if latErr == nil && lngErr == nil {
			lat, lng = roundedCoordinate(lat), roundedCoordinate(lng)
			area.Latitude, area.Longitude = &lat, &lng
		}
		if area.City != "" {
			items = append(items, locationSuggestion{ID: rawJSONText(row.PlaceID), Label: firstLocation(row.Address["amenity"], row.Address["building"], row.Address["road"], row.DisplayName), Address: row.DisplayName, locationArea: area})
		}
	}
	items = dedupeLocationSuggestions(items)
	return locationSuggestionPage{Items: items, Total: -1, Page: page, PageSize: pageSize, HasMore: len(rows) >= pageSize}, nil
}

func requestBaiduReverse(ctx context.Context, target string, fallbackLat, fallbackLng float64) (locationArea, error) {
	parsed, err := secureLocationURL(target)
	if err != nil {
		return locationArea{}, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, parsed.String(), nil)
	if err != nil {
		return locationArea{}, err
	}
	req.Header.Set("User-Agent", "CatLink/1.0 (+https://api.example.com)")
	res, err := (&http.Client{Timeout: locationHTTPTimeout, CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}).Do(req)
	if err != nil {
		return locationArea{}, err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return locationArea{}, fmt.Errorf("百度地图服务状态 %d", res.StatusCode)
	}
	var payload struct {
		Status  int    `json:"status"`
		Message string `json:"message"`
		Result  struct {
			FormattedAddress string `json:"formatted_address"`
			Location         struct {
				Lat float64 `json:"lat"`
				Lng float64 `json:"lng"`
			} `json:"location"`
			AddressComponent struct {
				Province     string `json:"province"`
				City         string `json:"city"`
				District     string `json:"district"`
				Town         string `json:"town"`
				Street       string `json:"street"`
				StreetNumber string `json:"street_number"`
			} `json:"addressComponent"`
		} `json:"result"`
	}
	body, err := io.ReadAll(io.LimitReader(res.Body, 64<<10))
	if err != nil || json.Unmarshal(body, &payload) != nil || payload.Status != 0 {
		return locationArea{}, errors.New("百度地图反向解析响应无效")
	}
	component := payload.Result.AddressComponent
	area := locationArea{Province: component.Province, City: component.City, District: component.District, Street: firstLocation(component.Town, component.Street, component.StreetNumber), Community: payload.Result.FormattedAddress, Attribution: "百度地图"}
	lat, lng := payload.Result.Location.Lat, payload.Result.Location.Lng
	if lat == 0 && lng == 0 {
		lat, lng = fallbackLat, fallbackLng
	}
	lat, lng = roundedCoordinate(lat), roundedCoordinate(lng)
	area.Latitude, area.Longitude = &lat, &lng
	if area.City == area.District {
		area.District = ""
	}
	if !validArea(area) {
		return locationArea{}, errors.New("百度地图返回的地址不完整")
	}
	return area, nil
}

func parseLonLat(value string) (*float64, *float64) {
	parts := strings.SplitN(strings.TrimSpace(value), ",", 2)
	if len(parts) != 2 {
		return nil, nil
	}
	lng, lngErr := strconv.ParseFloat(parts[0], 64)
	lat, latErr := strconv.ParseFloat(parts[1], 64)
	if lngErr != nil || latErr != nil || lat < -90 || lat > 90 || lng < -180 || lng > 180 {
		return nil, nil
	}
	lng, lat = roundedCoordinate(lng), roundedCoordinate(lat)
	return &lat, &lng
}

func dedupeLocationSuggestions(items []locationSuggestion) []locationSuggestion {
	seen := make(map[string]struct{}, len(items))
	result := make([]locationSuggestion, 0, len(items))
	for _, item := range items {
		key := item.ID
		if key == "" {
			key = fmt.Sprintf("%s|%s|%v|%v", item.Label, item.Address, item.Latitude, item.Longitude)
		}
		if _, ok := seen[key]; ok {
			continue
		}
		seen[key] = struct{}{}
		result = append(result, item)
	}
	return result
}

func locationFullAddress(parts ...string) string {
	result := make([]string, 0, len(parts))
	for _, part := range parts {
		part = strings.TrimSpace(part)
		if part == "" || (len(result) > 0 && result[len(result)-1] == part) {
			continue
		}
		result = append(result, part)
	}
	return strings.Join(result, "")
}

func rawJSONInt(raw json.RawMessage) int {
	value, _ := strconv.Atoi(strings.Trim(string(raw), "\""))
	return value
}

func rawJSONText(raw json.RawMessage) string {
	if value := stringFromJSON(raw); value != "" {
		return value
	}
	return strings.TrimSpace(string(raw))
}

func roundedCoordinate(value float64) float64 {
	return math.Round(value*1000) / 1000
}

func secureLocationURL(raw string) (*url.URL, error) {
	parsed, err := url.Parse(raw)
	if err != nil || parsed.Scheme != "https" || parsed.Host == "" || parsed.User != nil {
		return nil, errors.New("定位服务地址无效")
	}
	return parsed, nil
}

func cachedLocationRequest(key string, load func() (locationArea, error)) (locationArea, error) {
	locationCache.Lock()
	item, ok := locationCache.Items[key]
	if ok && !time.Now().Before(item.ExpiresAt) {
		delete(locationCache.Items, key)
		ok = false
	}
	locationCache.Unlock()
	if ok {
		return item.Area, nil
	}
	area, err := load()
	if err == nil {
		locationCache.Lock()
		storeCachedLocation(key, area, time.Now())
		locationCache.Unlock()
	}
	return area, err
}

func storeCachedLocation(key string, area locationArea, now time.Time) {
	if len(locationCache.Items) >= locationCacheMaxItems {
		for cachedKey, entry := range locationCache.Items {
			if !now.Before(entry.ExpiresAt) {
				delete(locationCache.Items, cachedKey)
			}
		}
	}
	for len(locationCache.Items) >= locationCacheMaxItems {
		for cachedKey := range locationCache.Items {
			delete(locationCache.Items, cachedKey)
			break
		}
	}
	locationCache.Items[key] = cachedLocation{Area: area, ExpiresAt: now.Add(locationCacheTTL)}
}

func trustedProxyRequest(remote string) bool {
	host, _, err := net.SplitHostPort(remote)
	if err != nil {
		host = remote
	}
	ip := net.ParseIP(host)
	return ip != nil && (ip.IsLoopback() || ip.IsPrivate())
}

func validArea(area locationArea) bool {
	return area.City != "" && len([]rune(area.City)) <= 80 && len([]rune(area.District)) <= 80
}

func firstLocation(values ...string) string {
	for _, value := range values {
		if value = strings.TrimSpace(value); value != "" {
			return value
		}
	}
	return ""
}

func amapText(raw json.RawMessage) string {
	if value := stringFromJSON(raw); value != "" {
		return value
	}
	var object struct {
		Name json.RawMessage `json:"name"`
	}
	if json.Unmarshal(raw, &object) == nil {
		return stringFromJSON(object.Name)
	}
	return ""
}

func stringFromJSON(raw json.RawMessage) string {
	var value string
	if json.Unmarshal(raw, &value) == nil {
		return value
	}
	var values []string
	if json.Unmarshal(raw, &values) == nil && len(values) > 0 {
		return values[0]
	}
	return ""
}

func AppAdminBrand(c *gin.Context) {
	name, logo := "有猫腻", "/api/v1/assets/default-avatar.png"
	if db, err := pkg.GetOrm(c); err == nil {
		var rows []struct{ ConfigKey, ConfigValue string }
		if db.Table("ga_config").Select("config_key,config_value").Where("config_key IN ?", []string{"sys_app_name", "sys_app_logo"}).Find(&rows).Error == nil {
			for _, row := range rows {
				if row.ConfigKey == "sys_app_name" && strings.TrimSpace(row.ConfigValue) != "" {
					name = row.ConfigValue
				}
				if row.ConfigKey == "sys_app_logo" && strings.TrimSpace(row.ConfigValue) != "" {
					logo = row.ConfigValue
				}
			}
		}
	}
	appSuccess(c, gin.H{"name": name, "logoUrl": logo})
}

var encouragementFallback = []string{
	"每一个生命都值得被温柔以待。",
	"救助一只猫，世界就多一分温暖。",
	"慢一点也没关系，温柔从来都有回声。",
	"今天的你和猫咪，都值得好好休息。",
}

func AppEncouragement(c *gin.Context) {
	db, err := pkg.GetOrm(c)
	if err != nil {
		appFailure(c, "温柔语句暂不可用")
		return
	}
	var count int64
	var quote string
	if db.Table("cat_encouragements").Where("enabled = ?", true).Count(&count).Error == nil && count > 0 {
		offset := rand.New(rand.NewSource(time.Now().UnixNano())).Intn(int(count))
		_ = db.Table("cat_encouragements").Where("enabled = ?", true).Offset(offset).Limit(1).Pluck("quote", &quote).Error
	}
	if strings.TrimSpace(quote) == "" {
		quote = encouragementFallback[rand.New(rand.NewSource(time.Now().UnixNano())).Intn(len(encouragementFallback))]
	}
	appSuccess(c, gin.H{"quote": quote})
}

func AppSubmitReport(c *gin.Context) {
	var body struct {
		TargetType string `json:"targetType"`
		TargetID   string `json:"targetId"`
		Reason     string `json:"reason"`
	}
	if c.ShouldBindJSON(&body) != nil {
		appFailure(c, "举报内容无效")
		return
	}
	body.TargetType, body.TargetID, body.Reason = strings.TrimSpace(body.TargetType), strings.TrimSpace(body.TargetID), strings.TrimSpace(body.Reason)
	if len([]rune(body.Reason)) < 5 || len([]rune(body.Reason)) > 500 || body.TargetID == "" {
		appFailure(c, "举报原因需为5-500个字符")
		return
	}
	allowed := map[string]string{"post": "cat_posts", "comment": "cat_comments", "user": "sys_users", "cat": "cat_public_cats"}
	table := allowed[body.TargetType]
	if table == "" {
		appFailure(c, "举报对象类型无效")
		return
	}
	db, err := pkg.GetOrm(c)
	if err != nil {
		appFailure(c, "举报服务暂不可用")
		return
	}
	var exists int64
	query := db.Table(table).Where("id = ?", body.TargetID)
	if body.TargetType == "user" {
		query = query.Where("authority_id = ? AND enable = ? AND deleted_at IS NULL", service.AppAuthorityID, 1)
	}
	if query.Count(&exists).Error != nil || exists != 1 {
		appFailure(c, "举报对象不存在")
		return
	}
	row := map[string]any{"reporter": c.GetUint("catlink_uid"), "target_type": body.TargetType, "target_id": body.TargetID, "reason": body.Reason, "status": "pending", "created_at": time.Now()}
	if err := db.Table("cat_reports").Create(row).Error; err != nil {
		appFailure(c, "举报提交失败")
		return
	}
	appSuccess(c, gin.H{"ok": true})
}
