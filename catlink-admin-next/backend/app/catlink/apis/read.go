package apis

import (
	"errors"
	"io"
	"mime"
	"net/http"
	"path/filepath"
	"strconv"
	"strings"

	adminstorage "go-admin/app/bricks/storage"
	"go-admin/app/catlink/service"
	"go-admin/common/actions"

	"github.com/gin-gonic/gin"
	"github.com/go-admin-team/go-admin-core/v2/sdk/pkg"
)

func adminDataPermission(c *gin.Context) *actions.DataPermission {
	return actions.GetPermissionFromContext(c)
}

func page(c *gin.Context) (int, int, string) {
	pageValue := c.Query("pageIndex")
	if pageValue == "" {
		pageValue = c.DefaultQuery("page", "1")
	}
	page, _ := strconv.Atoi(pageValue)
	size, _ := strconv.Atoi(c.DefaultQuery("pageSize", "20"))
	if page < 1 {
		page = 1
	}
	if size < 1 {
		size = 20
	}
	if size > 200 {
		size = 200
	}
	return page, size, strings.TrimSpace(c.Query("keyword"))
}

func respond(c *gin.Context, data interface{}, err error) {
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"code": 500, "msg": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"code": 200, "data": data})
}

func Stats(c *gin.Context) {
	db, err := pkg.GetOrm(c)
	if err != nil {
		respond(c, nil, err)
		return
	}
	db = db.WithContext(c.Request.Context())
	data, err := service.Stats(db, adminDataPermission(c))
	respond(c, data, err)
}

func VaultCats(c *gin.Context) {
	db, err := pkg.GetOrm(c)
	if err != nil {
		respond(c, nil, err)
		return
	}
	db = db.WithContext(c.Request.Context())
	p, size, keyword := page(c)
	data, err := service.VaultCats(db, p, size, keyword, adminDataPermission(c))
	respond(c, data, err)
}

func VaultCatDetail(c *gin.Context) {
	ownerID, err := strconv.ParseUint(c.Param("ownerId"), 10, 64)
	if err != nil || ownerID == 0 || strings.TrimSpace(c.Param("recordId")) == "" {
		c.JSON(http.StatusBadRequest, gin.H{"code": 400, "msg": "档案参数无效"})
		return
	}
	db, err := pkg.GetOrm(c)
	if err != nil {
		respond(c, nil, err)
		return
	}
	db = db.WithContext(c.Request.Context())
	data, err := service.VaultCatDetail(db, uint(ownerID), c.Param("recordId"), adminDataPermission(c))
	respond(c, data, err)
}

func VaultBusinessRecords(c *gin.Context) {
	db, err := pkg.GetOrm(c)
	if err != nil {
		respond(c, nil, err)
		return
	}
	db = db.WithContext(c.Request.Context())
	p, size, keyword := page(c)
	data, err := service.VaultBusinessRecords(db, c.Param("entityType"), p, size, keyword, adminDataPermission(c))
	var limitErr *service.VaultSearchLimitError
	if errors.As(err, &limitErr) {
		c.JSON(http.StatusBadRequest, gin.H{"code": 400, "msg": limitErr.Error()})
		return
	}
	respond(c, data, err)
}

func VaultBusinessRecordDetail(c *gin.Context) {
	ownerID, err := strconv.ParseUint(c.Param("ownerId"), 10, 64)
	if err != nil || ownerID == 0 || strings.TrimSpace(c.Param("recordId")) == "" {
		c.JSON(http.StatusBadRequest, gin.H{"code": 400, "msg": "领养记录参数无效"})
		return
	}
	db, err := pkg.GetOrm(c)
	if err != nil {
		respond(c, nil, err)
		return
	}
	db = db.WithContext(c.Request.Context())
	data, err := service.VaultBusinessRecordDetail(db, c.Param("entityType"), uint(ownerID), c.Param("recordId"), adminDataPermission(c))
	respond(c, data, err)
}

// MediaContent streams a catalog object through the configured provider. This
// keeps private/local media out of guessed filesystem URLs while allowing the
// admin console to preview the exact same object used by the app.
func MediaContent(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil || id == 0 {
		c.JSON(http.StatusBadRequest, gin.H{"code": 400, "msg": "媒体参数无效"})
		return
	}
	db, err := pkg.GetOrm(c)
	if err != nil {
		respond(c, nil, err)
		return
	}
	db = db.WithContext(c.Request.Context())
	var row struct {
		StorageKey      string `gorm:"column:storage_key"`
		StorageProvider string `gorm:"column:storage_provider"`
		URL             string `gorm:"column:url"`
		Name            string `gorm:"column:original_name"`
	}
	query := db.Table("cat_media").Select("storage_key,storage_provider,url,original_name").Where("id = ? AND temporary = ?", id, false)
	if err := service.ScopeAdminResource(query, db, adminDataPermission(c), "cat_media", "").Take(&row).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"code": 404, "msg": "媒体文件不存在"})
		return
	}
	if strings.TrimSpace(row.StorageKey) == "" {
		c.JSON(http.StatusNotFound, gin.H{"code": 404, "msg": "媒体存储路径为空"})
		return
	}
	provider, err := adminstorage.ProviderFor(c.Request.Context(), db, row.StorageProvider)
	if err != nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{"code": 503, "msg": "存储方案不可用"})
		return
	}
	reader, info, err := provider.Open(c.Request.Context(), row.StorageKey)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"code": 404, "msg": "媒体文件不存在或存储不可用"})
		return
	}
	defer reader.Close()
	contentType := mime.TypeByExtension(strings.ToLower(filepath.Ext(row.Name)))
	if contentType == "" {
		contentType = info.ContentType
	}
	if contentType != "" {
		c.Header("Content-Type", contentType)
	}
	if info.Size > 0 {
		c.Header("Content-Length", strconv.FormatInt(info.Size, 10))
	}
	c.Header("Cache-Control", "private, max-age=300")
	_, _ = io.Copy(c.Writer, reader)
}

func tableList(table, order string, columns ...string) gin.HandlerFunc {
	return func(c *gin.Context) {
		db, err := pkg.GetOrm(c)
		if err != nil {
			respond(c, nil, err)
			return
		}
		db = db.WithContext(c.Request.Context())
		p, size, keyword := page(c)
		data, err := service.ListTable(db, table, order, p, size, keyword, adminDataPermission(c), columns...)
		respond(c, data, err)
	}
}

var Posts = tableList("cat_posts", "created_at DESC, id DESC", "title", "content")
var Comments = tableList("cat_comments", "created_at DESC, id DESC", "body")
var Announcements = tableList("cat_announcement_campaigns", "created_at DESC, id DESC", "title", "body", "status")

func PublicCats(c *gin.Context) {
	db, err := pkg.GetOrm(c)
	if err != nil {
		respond(c, nil, err)
		return
	}
	db = db.WithContext(c.Request.Context())
	p, size, keyword := page(c)
	staleDays, _ := strconv.Atoi(c.Query("staleDays"))
	data, err := service.PublicCats(db, p, size, keyword, staleDays, adminDataPermission(c))
	respond(c, data, err)
}

func ContentDetail(table string) gin.HandlerFunc {
	return func(c *gin.Context) {
		id, err := strconv.ParseUint(c.Param("id"), 10, 64)
		if err != nil || id == 0 {
			c.JSON(http.StatusBadRequest, gin.H{"code": 400, "msg": "参数无效"})
			return
		}
		db, err := pkg.GetOrm(c)
		if err != nil || db == nil {
			c.JSON(http.StatusServiceUnavailable, gin.H{"code": 503, "msg": "数据库连接不可用"})
			return
		}
		db = db.WithContext(c.Request.Context())
		var data map[string]any
		data, err = service.TableDetail(db, table, uint(id), adminDataPermission(c))
		if err == nil {
			respond(c, data, nil)
			return
		}
		c.JSON(http.StatusNotFound, gin.H{"code": 404, "msg": "数据不存在"})
	}
}

func Media(c *gin.Context) {
	db, err := pkg.GetOrm(c)
	if err != nil {
		respond(c, nil, err)
		return
	}
	db = db.WithContext(c.Request.Context())
	p, size, keyword := page(c)
	data, err := service.MediaList(db, p, size, keyword, strings.TrimSpace(c.Query("category")), strings.TrimSpace(c.Query("entityType")), strings.TrimSpace(c.Query("storageProvider")), adminDataPermission(c))
	respond(c, data, err)
}

func OperationLogs(c *gin.Context) {
	source := strings.ToLower(strings.TrimSpace(c.Query("source")))
	method := strings.ToUpper(strings.TrimSpace(c.Query("method")))
	status := strings.ToLower(strings.TrimSpace(c.Query("status")))
	if (source != "" && source != "app" && source != "admin") ||
		(method != "" && method != http.MethodGet && method != http.MethodPost && method != http.MethodPut && method != http.MethodPatch && method != http.MethodDelete) ||
		(status != "" && status != "success" && status != "failed") {
		c.JSON(http.StatusBadRequest, gin.H{"code": 400, "msg": "日志筛选参数无效"})
		return
	}
	db, err := pkg.GetOrm(c)
	if err != nil {
		respond(c, nil, err)
		return
	}
	db = db.WithContext(c.Request.Context())
	p, size, keyword := page(c)
	data, err := service.BusinessAuditList(db, p, size, service.BusinessAuditFilter{Keyword: keyword, Source: source, Method: method, Status: status}, adminDataPermission(c))
	respond(c, data, err)
}
