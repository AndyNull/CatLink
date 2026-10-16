package middleware

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	"github.com/flipped-aurora/gin-vue-admin/server/model/common/response"
	"github.com/flipped-aurora/gin-vue-admin/server/service"
	"github.com/flipped-aurora/gin-vue-admin/server/utils/logger"
	"github.com/gin-gonic/gin"
)

type LimitConfig struct {
	// GenerationKey 根据业务生成key 下面CheckOrMark查询生成
	GenerationKey func(c *gin.Context) string
	// 检查函数,用户可修改具体逻辑,更加灵活
	CheckOrMark func(key string, expire int, limit int) error
	// Expire key 过期时间
	Expire int
	// Limit 周期时间
	Limit int
}

func (l LimitConfig) LimitWithTime() gin.HandlerFunc {
	return func(c *gin.Context) {
		if err := l.CheckOrMark(l.GenerationKey(c), l.Expire, l.Limit); err != nil {
			c.JSON(http.StatusOK, gin.H{"code": response.ERROR, "msg": err.Error()})
			c.Abort()
			return
		} else {
			c.Next()
		}
	}
}

// DefaultGenerationKey 默认生成key
func DefaultGenerationKey(c *gin.Context) string {
	return "GVA_Limit" + c.ClientIP()
}

func DefaultCheckOrMark(key string, expire int, limit int) (err error) {
	// 无缓存句柄（极端启动期）时 fail-open，避免误伤
	if global.GVA_CACHE == nil {
		return nil
	}
	if err = SetLimitWithTime(key, limit, time.Duration(expire)*time.Second); err != nil {
		logger.Bg().Mod("system").Err(err).Error("limit")
	}
	return err
}

func DefaultLimit() gin.HandlerFunc {
	return LimitConfig{
		GenerationKey: DefaultGenerationKey,
		CheckOrMark:   DefaultCheckOrMark,
		Expire:        global.GVA_CONFIG.System.LimitTimeIP,
		Limit:         global.GVA_CONFIG.System.LimitCountIP,
	}.LimitWithTime()
}

// SetLimitWithTime 设置访问次数：窗口内计数到达 limit 即拒绝。
func SetLimitWithTime(key string, limit int, expiration time.Duration) error {
	count, err := global.GVA_CACHE.IncrementWithExpire(key, 1, expiration)
	if err != nil {
		// 运行时缓存异常：记录日志并 fail-open 放行
		logger.Bg().Mod("system").Err(err).Error("limit increment")
		return nil
	}
	if count > int64(limit) {
		return errors.New("请求太过频繁，请稍后再试")
	}
	return nil
}

// CacheCheckOrMark 基于 GVA_CACHE 的限流计数 超限返回错误 cache 异常 fail-open
func CacheCheckOrMark(key string, expire int, limit int) error {
	if global.GVA_CACHE == nil {
		return nil
	}
	n, err := global.GVA_CACHE.IncrementWithExpire(key, 1, time.Duration(expire)*time.Second)
	if err != nil {
		logger.Bg().Mod("system").Err(err).Error("limit")
		return nil // fail-open
	}
	if int(n) > limit {
		return errors.New("请求太过频繁，请稍后再试")
	}
	return nil
}

func catLinkAuthIdentity(c *gin.Context) string {
	const maxAuthBody = 64 << 10
	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxAuthBody)
	body, err := io.ReadAll(c.Request.Body)
	if err != nil {
		return "ip:" + catLinkIdentityDigest(c.ClientIP())
	}
	c.Request.Body = io.NopCloser(bytes.NewReader(body))
	var fields map[string]any
	if json.Unmarshal(body, &fields) != nil {
		return "ip:" + catLinkIdentityDigest(c.ClientIP())
	}
	for _, key := range []string{"email", "username", "refreshToken"} {
		if value, ok := fields[key].(string); ok && strings.TrimSpace(value) != "" {
			return catLinkIdentityDigest(value)
		}
	}
	return "ip:" + catLinkIdentityDigest(c.ClientIP())
}

func catLinkIdentityDigest(value string) string {
	digest := sha256.Sum256([]byte(strings.ToLower(strings.TrimSpace(value))))
	return hex.EncodeToString(digest[:16])
}

// CatLinkAuthLimit 对 App 登录、注册、刷新和密码端点按 IP + 账号/credential 限流。
// 缓存不可用时拒绝请求，避免启动期或 Redis 故障演变为无限制认证尝试。
func CatLinkAuthLimit() gin.HandlerFunc {
	return func(c *gin.Context) {
		if global.GVA_CACHE == nil {
			c.JSON(http.StatusServiceUnavailable, gin.H{"code": response.ERROR, "msg": "认证服务暂不可用"})
			c.Abort()
			return
		}
		identity := catLinkAuthIdentity(c)
		keys := []string{"CatLink_AuthLimit:ip:" + c.ClientIP(), "CatLink_AuthLimit:identity:" + identity}
		for _, key := range keys {
			count, err := global.GVA_CACHE.IncrementWithExpire(key, 1, time.Minute)
			if err != nil {
				logger.Bg().Mod("system").Err(err).Error("catlink auth limit")
				c.JSON(http.StatusServiceUnavailable, gin.H{"code": response.ERROR, "msg": "认证服务暂不可用"})
				c.Abort()
				return
			}
			if count > 30 {
				c.JSON(http.StatusTooManyRequests, gin.H{"code": response.ERROR, "msg": "请求过于频繁，请稍后再试"})
				c.Abort()
				return
			}
		}
		c.Next()
	}
}

func CatLinkLocationLimit() gin.HandlerFunc {
	return LimitConfig{
		GenerationKey: func(c *gin.Context) string { return "CatLink_LocationLimit:" + c.ClientIP() },
		CheckOrMark:   CacheCheckOrMark,
		Expire:        60,
		Limit:         60,
	}.LimitWithTime()
}

// SecurityLimit 按安全配置对登录/敏感接口限流 未开启则放行
func SecurityLimit() gin.HandlerFunc {
	return func(c *gin.Context) {
		enable, window, count := service.ServiceGroupApp.SystemServiceGroup.SecurityConfigService.CurrentLimit(c.Request.Context())
		if !enable {
			c.Next()
			return
		}
		key := "GVA_SecLimit" + c.ClientIP() + c.FullPath()
		if err := CacheCheckOrMark(key, window, count); err != nil {
			c.JSON(http.StatusOK, gin.H{"code": response.ERROR, "msg": err.Error()})
			c.Abort()
			return
		}
		c.Next()
	}
}
