package gva_cache

import (
	"context"
	"time"

	"github.com/redis/go-redis/v9"
	"go.uber.org/zap"
)

// redisCache 包装 go-redis，使缓存语义在多实例间共享。
type redisCache struct {
	client redis.UniversalClient
}

const (
	redisTakeScript                = `local value = redis.call('GET', KEYS[1]); if value then redis.call('DEL', KEYS[1]); end; return value`
	redisIncrementWithExpireScript = `local value = redis.call('INCRBY', KEYS[1], ARGV[1]); if value == tonumber(ARGV[1]) then redis.call('PEXPIRE', KEYS[1], ARGV[2]); end; return value`
)

// NewRedisCache 创建 Redis 后端。
func NewRedisCache(client redis.UniversalClient) Cache {
	return &redisCache{client: client}
}

func (r *redisCache) Get(key string) (any, bool) {
	val, err := r.client.Get(context.Background(), key).Result()
	if err != nil {
		// redis.Nil（不存在）与其他错误统一按「未命中」处理
		return nil, false
	}
	return val, true
}

func (r *redisCache) Take(key string) (any, bool) {
	value, err := r.client.Eval(context.Background(), redisTakeScript, []string{key}).Result()
	if err != nil || value == nil {
		return nil, false
	}
	return value, true
}

func (r *redisCache) Set(key string, value any, ttl time.Duration) {
	if err := r.client.Set(context.Background(), key, value, ttl).Err(); err != nil {
		zap.L().Warn("gva_cache: redis set 失败", zap.String("key", key), zap.Error(err))
	}
}

func (r *redisCache) SetDefault(key string, value any) {
	// 无过期
	if err := r.client.Set(context.Background(), key, value, 0).Err(); err != nil {
		zap.L().Warn("gva_cache: redis setDefault 失败", zap.String("key", key), zap.Error(err))
	}
}

func (r *redisCache) Increment(key string, n int64) (int64, error) {
	return r.client.IncrBy(context.Background(), key, n).Result()
}

func (r *redisCache) IncrementWithExpire(key string, n int64, ttl time.Duration) (int64, error) {
	return r.client.Eval(
		context.Background(), redisIncrementWithExpireScript, []string{key}, n, ttl.Milliseconds(),
	).Int64()
}

func (r *redisCache) Exists(key string) bool {
	cnt, err := r.client.Exists(context.Background(), key).Result()
	if err != nil {
		return false
	}
	return cnt > 0
}

func (r *redisCache) Delete(key string) {
	if err := r.client.Del(context.Background(), key).Err(); err != nil {
		zap.L().Warn("gva_cache: redis delete 失败", zap.String("key", key), zap.Error(err))
	}
}
