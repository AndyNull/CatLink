package apis

import (
	"net/http"
	"net/url"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/go-admin-team/go-admin-core/v2/sdk/pkg"
	"go-admin/app/catlink/service"
)

func LocationProvider(c *gin.Context) {
	db, err := pkg.GetOrm(c)
	if err != nil {
		respond(c, nil, err)
		return
	}
	config, err := service.LoadLocationProviderConfig(db)
	if err != nil {
		respond(c, nil, err)
		return
	}
	if config.Provider == "" {
		config.Provider = "osm"
	}
	if endpoint, endpointErr := providerEndpoint(config, locationProviderKind(config), "search"); endpointErr == nil {
		config.SearchURL = endpoint
	}
	if config.SearchURL == "" {
		switch config.Provider {
		case "amap":
			config.SearchURL = "https://restapi.amap.com/v5/place/text"
		case "baidu":
			config.SearchURL = "https://api.map.baidu.com/place/v3/region"
		default:
			config.SearchURL = "https://nominatim.openstreetmap.org/search"
		}
	}
	if config.ReverseURL == "" {
		switch config.Provider {
		case "amap":
			config.ReverseURL = "https://restapi.amap.com/v3/geocode/regeo"
		case "baidu":
			config.ReverseURL = "https://api.map.baidu.com/reverse_geocoding/v3/"
		default:
			config.ReverseURL = "https://nominatim.openstreetmap.org/reverse"
		}
	}
	apiKeyConfigured := strings.TrimSpace(config.APIKey) != ""
	amapKeyConfigured := strings.TrimSpace(config.AmapKey) != ""
	baiduAkConfigured := strings.TrimSpace(config.BaiduAK) != ""
	configured := apiKeyConfigured || amapKeyConfigured || baiduAkConfigured
	config.APIKey, config.AmapKey, config.BaiduAK = "", "", ""
	respond(c, gin.H{
		"provider": config.Provider, "searchUrl": config.SearchURL, "reverseUrl": config.ReverseURL,
		"ipUrl": config.IPURL, "trustProxyHeaders": config.TrustProxyHeaders, "apiKeyConfigured": configured,
		"amapKeyConfigured": amapKeyConfigured || apiKeyConfigured, "baiduAkConfigured": baiduAkConfigured || apiKeyConfigured,
	}, nil)
}

func SaveLocationProvider(c *gin.Context) {
	var config service.LocationProviderConfig
	if c.ShouldBindJSON(&config) != nil || !validLocationProvider(config.Provider) || !validLocationEndpoint(config.SearchURL, false) || !validLocationEndpoint(config.ReverseURL, false) || !validLocationEndpoint(config.IPURL, true) {
		c.JSON(http.StatusBadRequest, gin.H{"code": 400, "msg": "定位服务地址必须是 HTTPS"})
		return
	}
	db, err := pkg.GetOrm(c)
	if err == nil {
		err = service.SaveLocationProviderConfig(db, config)
	}
	config.APIKey, config.AmapKey, config.BaiduAK = "", "", ""
	respond(c, config, err)
}

func validLocationProvider(value string) bool {
	value = strings.ToLower(strings.TrimSpace(value))
	return value == "" || value == "amap" || value == "baidu" || value == "osm" || value == "custom"
}

func validLocationEndpoint(raw string, ipTemplate bool) bool {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return true
	}
	parsed, err := url.Parse(raw)
	return err == nil && parsed.Scheme == "https" && parsed.Host != "" && parsed.User == nil && (!ipTemplate || strings.Contains(raw, "{ip}"))
}
