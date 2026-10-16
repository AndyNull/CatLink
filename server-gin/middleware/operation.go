package middleware

import (
	"encoding/json"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/utils"
	"github.com/flipped-aurora/gin-vue-admin/server/utils/logger"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	"github.com/gin-gonic/gin"
)

func redactOperationBody(body []byte) string {
	var value any
	if err := json.Unmarshal(body, &value); err != nil {
		return "[非 JSON 请求体]"
	}
	var redact func(any) any
	redact = func(current any) any {
		switch typed := current.(type) {
		case map[string]any:
			clean := make(map[string]any, len(typed))
			for key, child := range typed {
				lower := strings.ToLower(key)
				if strings.Contains(lower, "password") || strings.Contains(lower, "token") || strings.Contains(lower, "secret") || strings.Contains(lower, "credential") || strings.Contains(lower, "captcha") || lower == "code" || lower == "contactsummary" {
					clean[key] = "[REDACTED]"
				} else {
					clean[key] = redact(child)
				}
			}
			return clean
		case []any:
			clean := make([]any, len(typed))
			for index, child := range typed {
				clean[index] = redact(child)
			}
			return clean
		default:
			return current
		}
	}
	clean, err := json.Marshal(redact(value))
	if err != nil {
		return "[请求体脱敏失败]"
	}
	return string(clean)
}

func OperationRecord() gin.HandlerFunc {
	return func(c *gin.Context) {
		var body []byte
		var userId int
		if c.Request.Method != http.MethodGet {
			// 请求体已由 AccessLog 中间件统一读取并缓存，避免重复读取 Body
			body = []byte(c.GetString(ctxReqBodyKey))
		} else {
			query := c.Request.URL.RawQuery
			query, _ = url.QueryUnescape(query)
			split := strings.Split(query, "&")
			m := make(map[string]string)
			for _, v := range split {
				kv := strings.Split(v, "=")
				if len(kv) == 2 {
					m[kv[0]] = kv[1]
				}
			}
			body, _ = json.Marshal(&m)
		}
		userId = int(utils.GetUserID(c))
		record := system.SysOperationRecord{
			Ip:        c.ClientIP(),
			Method:    c.Request.Method,
			Path:      c.Request.URL.Path,
			Agent:     c.Request.UserAgent(),
			Body:      "",
			UserID:    userId,
			RequestID: logger.FromCtx(c.Request.Context()).GetRequestID(),
			TraceID:   logger.FromCtx(c.Request.Context()).GetTraceID(),
			DeviceID:  logger.FromCtx(c.Request.Context()).GetDeviceID(),
		}
		record.ObjectType, record.ObjectID = operationObject(c.Request.URL.Path)

		// 上传文件时候 中间件日志进行裁断操作
		if strings.Contains(c.GetHeader("Content-Type"), "multipart/form-data") {
			record.Body = "[文件]"
		} else {
			redacted := redactOperationBody(body)
			if len(redacted) > logger.AccessLogMaxBytes() {
				record.Body = "[超出记录长度]"
			} else {
				record.Body = redacted
			}
		}

		now := time.Now()
		if err := global.GVA_DB.Create(&record).Error; err != nil {
			logger.WithCtx(c.Request.Context()).Mod("http").Err(err).Error("create operation record error")
			c.JSON(http.StatusServiceUnavailable, gin.H{"code": 7, "data": nil, "msg": "审计服务暂不可用"})
			c.Abort()
			return
		}

		c.Next()

		record.ErrorMessage = c.Errors.ByType(gin.ErrorTypePrivate).String()
		record.Status = c.Writer.Status()
		record.LatencyMs = time.Since(now).Milliseconds()
		// 管理响应可能包含账号信息；审计记录只保留状态摘要，避免持久化敏感数据。
		record.Resp = "[response omitted]"

		if strings.Contains(c.Writer.Header().Get("Pragma"), "public") ||
			strings.Contains(c.Writer.Header().Get("Expires"), "0") ||
			strings.Contains(c.Writer.Header().Get("Cache-Control"), "must-revalidate, post-check=0, pre-check=0") ||
			strings.Contains(c.Writer.Header().Get("Content-Type"), "application/force-download") ||
			strings.Contains(c.Writer.Header().Get("Content-Type"), "application/octet-stream") ||
			strings.Contains(c.Writer.Header().Get("Content-Type"), "application/vnd.ms-excel") ||
			strings.Contains(c.Writer.Header().Get("Content-Type"), "application/download") ||
			strings.Contains(c.Writer.Header().Get("Content-Disposition"), "attachment") ||
			strings.Contains(c.Writer.Header().Get("Content-Transfer-Encoding"), "binary") {
			if len(record.Resp) > logger.AccessLogMaxBytes() {
				// 下载类响应体超长时截断的是 Resp(此处原误写为 Body)
				record.Resp = "[超出记录长度]"
			}
		}
		if err := global.GVA_DB.Model(&record).Select("status", "latency_ms", "error_message", "resp").Updates(&record).Error; err != nil {
			logger.WithCtx(c.Request.Context()).Mod("http").Err(err).Error("update operation record error")
		}
	}
}

func operationObject(path string) (string, string) {
	parts := strings.Split(strings.Trim(path, "/"), "/")
	for index, part := range parts {
		kind := map[string]string{
			"posts": "post", "comments": "comment", "cats": "cat", "users": "user",
			"media": "media", "conversations": "conversation", "reports": "report",
			"adoption-applications": "adoption", "missing-cases": "missing_case",
			"rescue-tasks": "rescue_task", "migrate": "storage_migration",
		}[part]
		if kind != "" && index+1 < len(parts) {
			id := parts[index+1]
			if id != "start" && id != "upload" && id != "following" {
				return kind, id
			}
		}
	}
	return "", ""
}

func BusinessOperationRecord() gin.HandlerFunc {
	record := OperationRecord()
	return func(c *gin.Context) {
		if c.Request.Method == http.MethodGet {
			c.Next()
			return
		}
		record(c)
	}
}
