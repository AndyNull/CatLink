package middleware

import (
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"go.uber.org/zap"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	"github.com/flipped-aurora/gin-vue-admin/server/utils/gva_cache"
	"github.com/gin-gonic/gin"
)

func init() {
	// 测试环境注入 no-op 日志，避免 GVA_LOG nil panic
	global.GVA_LOG = zap.NewNop()
}

func TestCatLinkAuthIdentityUsesCredentialHashAndPreservesBody(t *testing.T) {
	recorder := httptest.NewRecorder()
	c, _ := gin.CreateTestContext(recorder)
	payload := `{"email":"Admin@Example.com","password":"secret"}`
	c.Request = httptest.NewRequest(http.MethodPost, "/login", strings.NewReader(payload))

	identity := catLinkAuthIdentity(c)
	if identity == "anonymous" || identity == "invalid-body" || strings.Contains(identity, "admin") {
		t.Fatalf("unexpected identity: %q", identity)
	}
	body, err := io.ReadAll(c.Request.Body)
	if err != nil || string(body) != payload {
		t.Fatalf("request body was not preserved: %q, err=%v", body, err)
	}
}

func TestOperationBodyRedaction(t *testing.T) {
	body := []byte(`{"email":"admin@example.com","password":"secret","code":"111111","captchaId":"challenge","captcha":"1234","nested":{"refreshToken":"token","safe":"value"}}`)
	redacted := redactOperationBody(body)
	if strings.Contains(redacted, "secret") || strings.Contains(redacted, "token\"") || strings.Contains(redacted, "111111") || strings.Contains(redacted, "challenge") || strings.Contains(redacted, "1234") {
		t.Fatalf("sensitive values remained in operation body: %s", redacted)
	}
	if !strings.Contains(redacted, "[REDACTED]") || !strings.Contains(redacted, "admin@example.com") || !strings.Contains(redacted, "value") {
		t.Fatalf("unexpected redacted body: %s", redacted)
	}
}

func TestDefaultCheckOrMark_LimitWithMemoryCache(t *testing.T) {
	// 注入内存后端，模拟无 Redis 也能限流
	global.GVA_CACHE = gva_cache.NewMemoryCache(time.Minute)
	key := "GVA_Limit_test_ip"
	global.GVA_CACHE.Delete(key)

	limit := 3
	// 前 3 次应放行
	for i := 1; i <= limit; i++ {
		if err := DefaultCheckOrMark(key, 60, limit); err != nil {
			t.Fatalf("第 %d 次不应被限流: %v", i, err)
		}
	}
	// 第 4 次应被限流
	if err := DefaultCheckOrMark(key, 60, limit); err == nil {
		t.Fatalf("第 4 次应被限流")
	}
	global.GVA_CACHE.Delete(key)
}

func TestDefaultCheckOrMark_FailOpenWhenCacheNil(t *testing.T) {
	global.GVA_CACHE = nil
	if err := DefaultCheckOrMark("any", 60, 1); err != nil {
		t.Fatalf("GVA_CACHE 为 nil 时应 fail-open 放行: %v", err)
	}
}

func TestCacheCheckOrMark(t *testing.T) {
	global.GVA_CACHE = gva_cache.NewMemoryCache(time.Hour)
	key := "GVA_Limit_test_1.2.3.4"
	// limit=2 前两次放行 第三次拒绝
	if err := CacheCheckOrMark(key, 60, 2); err != nil {
		t.Fatalf("1st should pass, got %v", err)
	}
	if err := CacheCheckOrMark(key, 60, 2); err != nil {
		t.Fatalf("2nd should pass, got %v", err)
	}
	if err := CacheCheckOrMark(key, 60, 2); err == nil {
		t.Fatalf("3rd should be limited, got nil")
	}
}
