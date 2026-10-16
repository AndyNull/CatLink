package gva_cache

import (
	"sync"
	"time"

	"github.com/songzhibin97/gkit/cache/local_cache"
)

// memoryCache 包装 gkit local_cache，保持无 Redis 时的语义。
type memoryCache struct {
	c             local_cache.Cache
	defaultExpire time.Duration
	mu            sync.Mutex
}

// NewMemoryCache 创建内存后端，defaultExpire 为 SetDefault 的默认过期时间。
func NewMemoryCache(defaultExpire time.Duration) Cache {
	return &memoryCache{
		c: local_cache.NewCache(
			local_cache.SetDefaultExpire(defaultExpire),
			local_cache.SetCapture(func(string, interface{}) {}), // 禁用默认 stdout 打印
		),
		defaultExpire: defaultExpire,
	}
}

func (m *memoryCache) Get(key string) (any, bool) {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.c.Get(key)
}

func (m *memoryCache) Take(key string) (any, bool) {
	m.mu.Lock()
	defer m.mu.Unlock()
	value, ok := m.c.Get(key)
	if ok {
		m.c.Delete(key)
	}
	return value, ok
}

func (m *memoryCache) Set(key string, value any, ttl time.Duration) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.c.Set(key, value, ttl)
}

func (m *memoryCache) SetDefault(key string, value any) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.c.SetDefault(key, value)
}

// increment 内部统一逻辑：key 不存在则按 int64(n) 建立计数（带 ttl），存在则 IncrementInt64。
func (m *memoryCache) increment(key string, n int64, ttl time.Duration) (int64, error) {
	if _, ok := m.c.Get(key); !ok {
		m.c.Set(key, n, ttl)
		return n, nil
	}
	return m.c.IncrementInt64(key, n)
}

func (m *memoryCache) Increment(key string, n int64) (int64, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.increment(key, n, m.defaultExpire)
}

func (m *memoryCache) IncrementWithExpire(key string, n int64, ttl time.Duration) (int64, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.increment(key, n, ttl)
}

func (m *memoryCache) Exists(key string) bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	_, ok := m.c.Get(key)
	return ok
}

func (m *memoryCache) Delete(key string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.c.Delete(key)
}
