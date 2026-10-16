package logger

import (
	"net/http"
	"strings"
	"testing"
)

func TestSanitizeHeaders(t *testing.T) {
	h := http.Header{}
	h.Set("Authorization", "Bearer secret")
	h.Set("X-Token", "tok")
	h.Set("Accept", "application/json")
	got := SanitizeHeaders(h)
	if got["Authorization"] != maskValue || got["X-Token"] != maskValue {
		t.Fatalf("sensitive headers not masked: %+v", got)
	}
	if got["Accept"] != "application/json" {
		t.Fatalf("normal header changed: %+v", got)
	}
}

func TestSanitizeBodyMasksAuthenticationCodesAndCaptcha(t *testing.T) {
	raw := `{"email":"user@example.com","password":"Password123","code":"111111","captchaId":"challenge-id","captcha":"1234","accessToken":"access-secret-value","refreshToken":"refresh-secret-value","contactSummary":"微信 cat-home"}`
	got := SanitizeBody("application/json", raw)
	for _, secret := range []string{"Password123", "111111", "challenge-id", "1234", "access-secret-value", "refresh-secret-value", "微信 cat-home"} {
		if strings.Contains(got, secret) {
			t.Fatalf("secret %q remained in %s", secret, got)
		}
	}
	if !strings.Contains(got, "user@example.com") {
		t.Fatalf("non-secret identity should remain useful: %s", got)
	}
}

func TestTruncate(t *testing.T) {
	if Truncate("short", 100) != "short" {
		t.Fatal("short should be unchanged")
	}
	long := strings.Repeat("a", 2000)
	if Truncate(long, 1024) != truncatedMark {
		t.Fatal("long should be replaced with mark")
	}
}
