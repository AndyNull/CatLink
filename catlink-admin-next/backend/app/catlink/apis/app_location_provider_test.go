package apis

import (
	"context"
	"errors"
	"net/url"
	"os"
	"testing"
	"time"

	"go-admin/app/catlink/service"
)

func TestLiveLocationProviders(t *testing.T) {
	if os.Getenv("CATLINK_LIVE_MAP_TEST") != "1" {
		t.Skip("set CATLINK_LIVE_MAP_TEST=1 to run provider integration tests")
	}
	tests := []struct {
		name     string
		provider service.LocationProviderConfig
		cityCode string
		distCode string
	}{
		{"amap", service.LocationProviderConfig{Provider: "amap", AmapKey: os.Getenv("CATLINK_AMAP_KEY")}, "110100", "110101"},
		{"baidu", service.LocationProviderConfig{Provider: "baidu", BaiduAK: os.Getenv("CATLINK_BAIDU_AK")}, "110100", "110101"},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if locationProviderKey(test.provider, test.name) == "" {
				t.Skip("provider key is not configured")
			}
			all := make([]locationSuggestion, 0, 100)
			for page := 1; page <= 12; page++ {
				target, err := locationSearchPageURL(test.provider, test.name, "天安门", "北京市", "东城区", test.cityCode, test.distCode, page, 20)
				if err != nil {
					t.Fatal(err)
				}
				ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
				result, err := requestLocationSuggestionPage(ctx, target.String(), test.name, page, 20, "北京市")
				cancel()
				if err != nil {
					t.Fatal(err)
				}
				all = append(all, result.Items...)
				if !result.HasMore {
					break
				}
			}
			all = dedupeLocationSuggestions(all)
			t.Logf("provider=%s uniqueResults=%d", test.name, len(all))
			if len(all) < 20 {
				t.Fatalf("provider returned too few Tiananmen results: %d", len(all))
			}
			for _, item := range all {
				if item.Label == "" || item.Address == "" || item.District != "东城区" {
					t.Fatalf("incomplete or out-of-district result: %#v", item)
				}
			}
		})
	}
}

func TestLocationSearchURLUsesProviderSpecificContracts(t *testing.T) {
	t.Run("amap", func(t *testing.T) {
		provider := service.LocationProviderConfig{
			Provider:  "amap",
			AmapKey:   "amap-test-key",
			SearchURL: "https://restapi.amap.com/v5/place/text",
		}
		got, err := locationSearchPageURL(provider, "amap", "白猫", "成都市", "青羊区", "510100", "510105", 2, 20)
		if err != nil {
			t.Fatal(err)
		}
		query := got.Query()
		if got.Path != "/v5/place/text" || query.Get("keywords") != "白猫" || query.Get("region") != "510105" || query.Get("city_limit") != "true" || query.Get("page_num") != "2" || query.Get("page_size") != "20" || query.Get("key") != "amap-test-key" {
			t.Fatalf("unexpected amap request: %s", got.String())
		}
		if query.Get("query") != "" || query.Get("ak") != "" {
			t.Fatalf("amap request contains baidu parameters: %s", got.String())
		}
	})

	t.Run("baidu", func(t *testing.T) {
		provider := service.LocationProviderConfig{
			Provider:   "baidu",
			BaiduAK:    "baidu-test-ak",
			SearchURL:  "https://api.map.baidu.com/place/v3/suggestion",
			ReverseURL: "https://api.map.baidu.com/reverse_geocoding/v3/",
		}
		got, err := locationSearchPageURL(provider, "baidu", "白猫", "成都市", "青羊区", "510100", "510105", 2, 20)
		if err != nil {
			t.Fatal(err)
		}
		query := got.Query()
		if got.Path != "/place/v3/region" || query.Get("query") != "白猫 青羊区" || query.Get("region") != "成都市" || query.Get("region_limit") != "true" || query.Get("page_num") != "1" || query.Get("page_size") != "20" || query.Get("ak") != "baidu-test-ak" {
			t.Fatalf("unexpected baidu request: %s", got.String())
		}
		if query.Get("keywords") != "" || query.Get("key") != "" {
			t.Fatalf("baidu request contains amap parameters: %s", got.String())
		}
	})
}

func TestBuiltInProviderKeyCannotBeSentToCustomHost(t *testing.T) {
	for _, test := range []struct {
		name     string
		provider service.LocationProviderConfig
	}{
		{"amap", service.LocationProviderConfig{Provider: "amap", AmapKey: "secret-amap-key", SearchURL: "https://attacker.example/search"}},
		{"baidu", service.LocationProviderConfig{Provider: "baidu", BaiduAK: "secret-baidu-ak", SearchURL: "https://attacker.example/search"}},
	} {
		t.Run(test.name, func(t *testing.T) {
			if _, err := locationSearchURL(test.provider, test.name, "天安门", "北京市"); err == nil {
				t.Fatal("non-official provider endpoint was accepted")
			}
		})
	}
}

func TestCustomProviderDoesNotAttachBuiltInKeys(t *testing.T) {
	provider := service.LocationProviderConfig{
		Provider:  "custom",
		APIKey:    "legacy-secret",
		AmapKey:   "amap-secret",
		BaiduAK:   "baidu-secret",
		SearchURL: "https://maps.example.test/search",
	}
	got, err := locationSearchURL(provider, "custom", "天安门", "北京市")
	if err != nil {
		t.Fatal(err)
	}
	if got.Query().Get("key") != "" || got.Query().Get("ak") != "" {
		t.Fatalf("built-in provider key leaked to custom endpoint: %s", got.String())
	}
}

func TestBaiduSearchURLProvidesNationwideRegionWhenCityMissing(t *testing.T) {
	provider := service.LocationProviderConfig{Provider: "baidu", BaiduAK: "baidu-test-ak"}
	got, err := locationSearchURL(provider, "baidu", "白猫", "")
	if err != nil {
		t.Fatal(err)
	}
	query := got.Query()
	if query.Get("region") != "全国" || query.Get("region_limit") != "false" {
		t.Fatalf("baidu request must specify a non-restrictive region without city: %s", got.String())
	}
}

func TestLocationSearchURLNarrowsOptionalDistrict(t *testing.T) {
	amap, err := locationSearchURL(service.LocationProviderConfig{
		Provider: "amap", AmapKey: "amap-test-key", SearchURL: "https://restapi.amap.com/v3/place/text",
	}, "amap", "小区", "成都市", "青羊区")
	if err != nil {
		t.Fatal(err)
	}
	if got := amap.Query().Get("keywords"); got != "小区 青羊区" || amap.Path != "/v5/place/text" || amap.Query().Get("region") != "成都市" {
		t.Fatalf("amap district was not included in keyword scope: %s", amap.String())
	}

	baidu, err := locationSearchURL(service.LocationProviderConfig{
		Provider: "baidu", BaiduAK: "baidu-test-ak", SearchURL: "https://api.map.baidu.com/place/v3/suggestion",
	}, "baidu", "小区", "成都市", "青羊区")
	if err != nil {
		t.Fatal(err)
	}
	if got := baidu.Query().Get("query"); got != "小区 青羊区" || baidu.Query().Get("region") != "成都市" {
		t.Fatalf("baidu district was not included in query scope: %s", baidu.String())
	}
}

func TestLocationSearchCacheKeySeparatesDistrict(t *testing.T) {
	if locationSearchCacheKey("amap", "endpoint", "小区", "成都市", "青羊区") == locationSearchCacheKey("amap", "endpoint", "小区", "成都市", "武侯区") {
		t.Fatal("district must be part of the location search cache key")
	}
}

func TestAmapRegionCodeExpandsCatalogCodes(t *testing.T) {
	if amapRegionCode("11") != "110000" || amapRegionCode("5101") != "510100" || amapRegionCode("510105") != "510105" {
		t.Fatal("region catalog codes were not normalized to six-digit adcodes")
	}
	if !validRegionCode("11") || !validRegionCode("5101") || !validRegionCode("510105") || validRegionCode("51a1") {
		t.Fatal("region code validation does not match the catalog")
	}
}

func TestLocationReverseURLUsesProviderCoordinateOrder(t *testing.T) {
	provider := service.LocationProviderConfig{Provider: "baidu", BaiduAK: "baidu-test-ak"}
	got, err := locationReverseURL(provider, "baidu", 30.660123, 104.050987)
	if err != nil {
		t.Fatal(err)
	}
	query := got.Query()
	if query.Get("location") != "30.660123,104.050987" || query.Get("coordtype") != "wgs84ll" || query.Get("ak") != "baidu-test-ak" {
		t.Fatalf("unexpected baidu reverse request: %s", got.String())
	}

	amap, err := locationReverseURL(service.LocationProviderConfig{Provider: "amap", AmapKey: "amap-test-key"}, "amap", 30.660123, 104.050987)
	if err != nil {
		t.Fatal(err)
	}
	if amap.Query().Get("location") != "104.050987,30.660123" {
		t.Fatalf("amap must use longitude,latitude: %s", amap.String())
	}
}

func TestParseAmapSuggestions(t *testing.T) {
	body := []byte(`{"status":"1","info":"OK","count":"100","pois":[{"id":"poi-1","name":"浣花小区","address":"草堂路1号","pname":"四川省","cityname":"成都市","adname":"青羊区","townname":"草堂街道","business_area":[],"location":"104.050987,30.660123"}],"geocodes":[{"formatted_address":"四川省成都市青羊区草堂街道","province":"四川省","city":"成都市","district":"青羊区","township":{"name":"草堂街道"},"neighborhood":{"name":"浣花社区"},"location":"104.050987,30.660123"}]}`)
	items, err := parseAmapSuggestions(body, "成都市")
	if err != nil {
		t.Fatal(err)
	}
	if len(items) != 2 {
		t.Fatalf("expected two amap results, got %d: %#v", len(items), items)
	}
	if items[0].City != "成都市" || items[0].District != "青羊区" || items[0].Street != "草堂街道" || items[0].Latitude == nil || *items[0].Latitude != 30.66 || *items[0].Longitude != 104.051 {
		t.Fatalf("unexpected amap POI mapping: %#v", items[0])
	}
	if items[1].Street != "草堂街道" || items[1].Community != "浣花社区" {
		t.Fatalf("unexpected amap geocode mapping: %#v", items[1])
	}
	if items[0].Label != "浣花小区" || items[0].Address != "四川省成都市青羊区草堂路1号" {
		t.Fatalf("name and full address were not separated: %#v", items[0])
	}
}

func TestProviderPaginationMetadata(t *testing.T) {
	amap, err := parseAmapSuggestionPage([]byte(`{"status":"1","count":"100","pois":[{"id":"1","name":"天安门","address":[],"pname":"北京市","cityname":[],"adname":"东城区","business_area":[],"location":"116.397,39.908"}]}`), "北京市", 2, 20, false)
	if err != nil || amap.Total != 100 || !amap.HasMore || amap.Page != 2 || len(amap.Items) != 1 {
		t.Fatalf("unexpected amap page: %#v %v", amap, err)
	}
	baidu, err := parseBaiduSuggestionPage([]byte(`{"status":0,"total":55,"results":[{"uid":"1","name":"天安门","province":"北京市","city":"北京市","area":"东城区","address":"东长安街"}]}`), "北京市东城区", 3, 20)
	if err != nil || baidu.Total != 55 || baidu.HasMore || baidu.Page != 3 || len(baidu.Items) != 1 {
		t.Fatalf("unexpected baidu page: %#v %v", baidu, err)
	}
}

func TestParseBaiduSuggestions(t *testing.T) {
	body := []byte(`{"status":0,"message":"ok","result":[{"name":"浣花小区","province":"四川省","city":"成都市","district":"青羊区","town":"草堂街道","address":"草堂路1号","location":{"lat":30.660123,"lng":104.050987}}]}`)
	items, err := parseBaiduSuggestions(body, "成都市")
	if err != nil {
		t.Fatal(err)
	}
	if len(items) != 1 {
		t.Fatalf("expected one baidu result, got %d: %#v", len(items), items)
	}
	item := items[0]
	if item.City != "成都市" || item.District != "青羊区" || item.Street != "草堂街道" || item.Latitude == nil || *item.Latitude != 30.66 || *item.Longitude != 104.051 {
		t.Fatalf("unexpected baidu mapping: %#v", item)
	}
}

func TestParseBaiduPlaceResultsUsesAreaAndOmitsMissingCoordinates(t *testing.T) {
	body := []byte(`{"status":0,"message":"ok","results":[{"name":"青羊区","province":"四川省","city":"成都市","area":"青羊区","town":"","address":"成都市青羊区","location":{"lat":0,"lng":0}}]}`)
	items, err := parseBaiduSuggestions(body, "成都市")
	if err != nil {
		t.Fatal(err)
	}
	if len(items) != 1 || items[0].District != "青羊区" {
		t.Fatalf("area field was not mapped: %#v", items)
	}
	if items[0].Latitude != nil || items[0].Longitude != nil {
		t.Fatalf("zero coordinates must not be exposed: %#v", items[0])
	}
}

func TestLocationParsersRejectProviderErrors(t *testing.T) {
	if _, err := parseAmapSuggestions([]byte(`{"status":"0","info":"INVALID_USER_KEY"}`), "成都市"); err == nil {
		t.Fatal("amap error response was accepted")
	}
	if _, err := parseBaiduSuggestions([]byte(`{"status":401,"message":"AK有误"}`), "成都市"); err == nil {
		t.Fatal("baidu error response was accepted")
	}
}

func TestLocationProviderErrorsRemainActionable(t *testing.T) {
	tests := []struct {
		err  error
		want string
	}{
		{errors.New("高德地图 Key 未配置"), "地图服务未配置，请在服务端定位设置中填写对应 Key"},
		{errors.New("高德地图: INVALID_USER_KEY"), "地图服务授权失败，请检查 Key、接口权限和配额"},
		{context.DeadlineExceeded, "地图搜索超时，请稍后重试"},
	}
	for _, test := range tests {
		if got := locationProviderError(test.err); got != test.want {
			t.Fatalf("unexpected error message %q", got)
		}
	}
}

func TestLocationParsersAcceptEmptyResults(t *testing.T) {
	amap, err := parseAmapSuggestionPage([]byte(`{"status":"1","count":"0","pois":[]}`), "北京市", 1, 20, true)
	if err != nil || len(amap.Items) != 0 || amap.HasMore {
		t.Fatalf("unexpected amap empty result: %#v %v", amap, err)
	}
	baidu, err := parseBaiduSuggestionPage([]byte(`{"status":0,"total":0,"results":[]}`), "北京市", 1, 20)
	if err != nil || len(baidu.Items) != 0 || baidu.HasMore {
		t.Fatalf("unexpected baidu empty result: %#v %v", baidu, err)
	}
}

func TestLocationSearchCacheReturnsCopy(t *testing.T) {
	key := "location-cache-copy-test"
	locationSearchCache.Lock()
	delete(locationSearchCache.Items, key)
	locationSearchCache.Unlock()
	storeLocationSearchCache(key, locationSuggestionPage{Items: []locationSuggestion{{Label: "原始", Address: "地址"}}, Page: 1, PageSize: 20})
	page, ok := loadLocationSearchCache(key)
	if !ok || len(page.Items) != 1 {
		t.Fatalf("cache miss: %#v %v", page, ok)
	}
	page.Items[0].Label = "修改后的副本"
	again, ok := loadLocationSearchCache(key)
	if !ok || again.Items[0].Label != "原始" {
		t.Fatalf("cache entry was mutated through returned slice: %#v", again)
	}
}

func TestSecureLocationURLRejectsInsecureOrCredentialURLs(t *testing.T) {
	for _, raw := range []string{"http://example.test/search", "https://user:pass@example.test/search", "https://"} {
		if _, err := secureLocationURL(raw); err == nil {
			t.Fatalf("insecure URL accepted: %q", raw)
		}
	}
	if parsed, err := secureLocationURL("https://example.test/search?q=" + url.QueryEscape("白猫")); err != nil || parsed.Query().Get("q") != "白猫" {
		t.Fatalf("valid URL rejected: %v", err)
	}
}
