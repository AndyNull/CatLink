package catlink

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"mime"
	"net/http"
	"os"
	pathpkg "path"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	"github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	gvamedia "github.com/flipped-aurora/gin-vue-admin/server/model/media"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	"github.com/flipped-aurora/gin-vue-admin/server/utils/upload"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

var mediaCategories = map[string]bool{"cat": true, "contract": true, "avatar": true, "post": true, "misc": true}

const (
	maxMediaAccountBytes       int64 = 2 << 30
	maxMediaHourlyBytes        int64 = 256 << 20
	maxMediaHourlyUploads            = 120
	maxMediaRequestBytes       int64 = 16 << 20
	maxMediaHourlyRequestBytes int64 = 512 << 20
)

type clientMediaError string

func (e clientMediaError) Error() string { return string(e) }

func detectMedia(data []byte, ext string) (string, bool) {
	if len(data) < 4 {
		return "", false
	}
	ext = strings.ToLower(strings.TrimPrefix(ext, "."))
	if data[0] == 0xff && data[1] == 0xd8 && data[2] == 0xff {
		return "jpg", ext == "jpg" || ext == "jpeg"
	}
	if len(data) >= 8 && string(data[:8]) == "\x89PNG\r\n\x1a\n" {
		return "png", ext == "png"
	}
	if len(data) >= 12 && string(data[:4]) == "RIFF" && string(data[8:12]) == "WEBP" {
		return "webp", ext == "webp"
	}
	if string(data[:4]) == "%PDF" {
		return "pdf", ext == "pdf"
	}
	return "", false
}

func mediaWindowStart(now time.Time) time.Time {
	return now.Truncate(time.Hour)
}

func reserveMediaUploadAttempt(uid uint, requestBytes int64) error {
	if requestBytes <= 0 || requestBytes > maxMediaRequestBytes {
		requestBytes = maxMediaRequestBytes
	}
	return global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		var user system.SysUser
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Select("id").First(&user, uid).Error; err != nil {
			return err
		}
		windowStart := mediaWindowStart(time.Now())
		var window catlink.CatMediaUploadWindow
		err := tx.Where("owner_id = ? AND window_start = ?", uid, windowStart).First(&window).Error
		if errors.Is(err, gorm.ErrRecordNotFound) {
			window = catlink.CatMediaUploadWindow{OwnerID: uid, WindowStart: windowStart}
		} else if err != nil {
			return err
		}
		if window.AttemptCount >= maxMediaHourlyUploads || window.RequestBytes > maxMediaHourlyRequestBytes-requestBytes {
			return clientMediaError("上传过于频繁，请稍后再试")
		}
		window.AttemptCount++
		window.RequestBytes += requestBytes
		window.UpdatedAt = time.Now()
		if window.ID == 0 {
			return tx.Create(&window).Error
		}
		return tx.Model(&window).Updates(map[string]any{
			"attempt_count": window.AttemptCount,
			"request_bytes": window.RequestBytes,
			"updated_at":    window.UpdatedAt,
		}).Error
	})
}

func removeMediaFile(path string) error {
	err := os.Remove(path)
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	return err
}

func syncMediaCatalog(tx *gorm.DB, row catlink.CatMedia) error {
	if !tx.Migrator().HasTable(&gvamedia.FileUploadAndDownload{}) {
		return nil
	}
	name := filepath.Base(row.StorageKey)
	catalog := gvamedia.FileUploadAndDownload{
		Name: name, Url: row.URL, Tag: "CatLink/" + row.Category, Key: row.StorageKey,
		Size: row.Size, Mime: mime.TypeByExtension(filepath.Ext(name)), UserID: row.OwnerID,
		Source: "catlink-app", BizCategory: row.Category, StorageProvider: mediaProvider(row),
	}
	return tx.Where("`key` = ?", row.StorageKey).Assign(catalog).FirstOrCreate(&catalog).Error
}

func deleteMediaCatalog(tx *gorm.DB, row catlink.CatMedia) error {
	if !tx.Migrator().HasTable(&gvamedia.FileUploadAndDownload{}) {
		return nil
	}
	return tx.Unscoped().Where("`key` = ?", row.StorageKey).Delete(&gvamedia.FileUploadAndDownload{}).Error
}

func syncCatLinkMediaCatalog() error {
	return syncCatLinkMediaCatalogContext(context.Background())
}

func SyncMediaCatalog(ctx context.Context) error {
	if global.GVA_DB == nil {
		return errors.New("database is not initialized")
	}
	return syncCatLinkMediaCatalogContext(ctx)
}

func syncCatLinkMediaCatalogContext(ctx context.Context) error {
	db := global.GVA_DB.WithContext(ctx)
	if !db.Migrator().HasTable(&gvamedia.FileUploadAndDownload{}) {
		return nil
	}
	if err := db.Unscoped().Where("(`key` IS NULL OR `key` = '') AND tag LIKE ?", "CatLink/%").Delete(&gvamedia.FileUploadAndDownload{}).Error; err != nil {
		return err
	}
	var rows []catlink.CatMedia
	return db.FindInBatches(&rows, 100, func(tx *gorm.DB, _ int) error {
		for _, row := range rows {
			if err := syncMediaCatalog(tx, row); err != nil {
				return err
			}
		}
		return nil
	}).Error
}

func migrateLegacyPublicMedia(urlRoot string) error {
	prefix := "/" + strings.Trim(urlRoot, "/\\") + "/"
	var rows []catlink.CatMedia
	if err := global.GVA_DB.Where("category <> ? AND (storage_key IS NULL OR storage_key = '')", "contract").Find(&rows).Error; err != nil {
		return err
	}
	for _, row := range rows {
		key := strings.TrimPrefix(strings.TrimSpace(row.URL), prefix)
		key = strings.TrimPrefix(pathpkg.Clean("/"+strings.ReplaceAll(key, "\\", "/")), "/")
		if !strings.HasPrefix(key, "catlink/"+row.Category+"/") {
			return fmt.Errorf("legacy media %d has invalid public path", row.ID)
		}
		if err := global.GVA_DB.Model(&catlink.CatMedia{}).Where("id = ?", row.ID).Update("storage_key", key).Error; err != nil {
			return err
		}
	}
	return nil
}

func mediaStoragePath(storeRoot, storageKey string) (string, error) {
	key := filepath.ToSlash(strings.TrimSpace(storageKey))
	if !strings.HasPrefix(key, "catlink/") && !strings.HasPrefix(key, ".catlink-private/catlink/") {
		return "", errors.New("invalid CatLink media storage key")
	}
	full := filepath.Join(storeRoot, filepath.FromSlash(key))
	rel, err := filepath.Rel(storeRoot, full)
	if err != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
		return "", errors.New("CatLink media path escapes storage root")
	}
	return full, nil
}

func cleanupExpiredTemporaryMedia() error {
	return CleanupExpiredTemporaryMedia(context.Background())
}

func CleanupExpiredTemporaryMedia(ctx context.Context) error {
	for {
		var rows []catlink.CatMedia
		if err := global.GVA_DB.WithContext(ctx).Where("temporary = ? AND expires_at IS NOT NULL AND expires_at <= ?", true, time.Now()).
			Order("id asc").Limit(100).Find(&rows).Error; err != nil {
			return err
		}
		if len(rows) == 0 {
			return nil
		}
		for _, row := range rows {
			if err := deleteMediaObject(ctx, row); err != nil {
				return err
			}
			if err := global.GVA_DB.Transaction(func(tx *gorm.DB) error {
				if err := deleteMediaCatalog(tx, row); err != nil {
					return err
				}
				return tx.Delete(&catlink.CatMedia{}, row.ID).Error
			}); err != nil {
				return err
			}
		}
	}
}

func migrateLegacyContractMedia(storeRoot, urlRoot string) error {
	if strings.TrimSpace(storeRoot) == "" {
		return nil
	}
	var lastID uint
	for {
		var rows []catlink.CatMedia
		if err := global.GVA_DB.Where("id > ? AND category = ? AND (storage_key IS NULL OR storage_key = '' OR storage_key NOT LIKE ?)", lastID, "contract", ".catlink-private/%").
			Order("id asc").Limit(100).Find(&rows).Error; err != nil {
			return err
		}
		if len(rows) == 0 {
			return nil
		}
		for _, row := range rows {
			lastID = row.ID
			prefix := "/" + strings.Trim(urlRoot, "/\\") + "/"
			oldKey := strings.TrimPrefix(strings.TrimSpace(row.URL), prefix)
			cleanOldKey := strings.TrimPrefix(pathpkg.Clean("/"+strings.ReplaceAll(oldKey, "\\", "/")), "/")
			if cleanOldKey != oldKey || !strings.HasPrefix(cleanOldKey, "catlink/contract/") {
				return fmt.Errorf("legacy contract media %d has invalid public path", row.ID)
			}
			oldFull, err := mediaStoragePath(storeRoot, cleanOldKey)
			if err != nil {
				return err
			}
			contractRoot := filepath.Join(storeRoot, "catlink", "contract")
			oldRel, err := filepath.Rel(contractRoot, oldFull)
			if err != nil || oldRel == ".." || strings.HasPrefix(oldRel, ".."+string(filepath.Separator)) {
				return fmt.Errorf("legacy contract media %d escapes contract root", row.ID)
			}
			newKey := filepath.ToSlash(filepath.Join(".catlink-private", filepath.FromSlash(cleanOldKey)))
			newFull, err := mediaStoragePath(storeRoot, newKey)
			if err != nil {
				return err
			}
			if err := os.MkdirAll(filepath.Dir(newFull), 0750); err != nil {
				return err
			}
			if _, err := os.Stat(newFull); errors.Is(err, os.ErrNotExist) {
				if err := os.Rename(oldFull, newFull); err != nil {
					return err
				}
			} else if err != nil {
				return err
			}
			newURL := "/api/v1/media/" + strconv.FormatUint(uint64(row.ID), 10)
			if err := global.GVA_DB.Model(&catlink.CatMedia{}).Where("id = ?", row.ID).
				Updates(map[string]any{"storage_key": newKey, "url": newURL}).Error; err != nil {
				return err
			}
		}
	}
}

func reconcileMediaRoot(storeRoot, urlRoot, storagePrefix string) error {
	if strings.TrimSpace(storeRoot) == "" {
		return nil
	}
	mediaRoot := filepath.Join(storeRoot, filepath.FromSlash(storagePrefix), "catlink")
	cutoff := time.Now().Add(-time.Hour)
	return filepath.WalkDir(mediaRoot, func(path string, entry os.DirEntry, err error) error {
		if err != nil {
			if errors.Is(err, os.ErrNotExist) {
				return nil
			}
			return err
		}
		if entry.IsDir() {
			return nil
		}
		info, err := entry.Info()
		if err != nil {
			return err
		}
		if info.ModTime().After(cutoff) {
			return nil
		}
		if strings.HasPrefix(entry.Name(), ".upload-") && strings.HasSuffix(entry.Name(), ".tmp") {
			return removeMediaFile(path)
		}
		rel, err := filepath.Rel(storeRoot, path)
		if err != nil || strings.HasPrefix(rel, "..") {
			return fmt.Errorf("invalid CatLink media path: %s", path)
		}
		storageKey := filepath.ToSlash(rel)
		publicURL := "/" + strings.Trim(urlRoot, "/\\") + "/" + storageKey
		var count int64
		q := global.GVA_DB.Model(&catlink.CatMedia{}).Where("storage_key = ?", storageKey)
		if storagePrefix == "" {
			q = q.Or("(storage_key IS NULL OR storage_key = '') AND url = ?", publicURL)
		}
		if err := q.Count(&count).Error; err != nil {
			return err
		}
		if count == 0 {
			return removeMediaFile(path)
		}
		return nil
	})
}

func reconcileCatLinkMediaFiles(storeRoot, urlRoot string) error {
	return errors.Join(
		reconcileMediaRoot(storeRoot, urlRoot, ""),
		reconcileMediaRoot(storeRoot, urlRoot, ".catlink-private"),
	)
}

func VerifyMediaFiles(ctx context.Context) error {
	var rows []catlink.CatMedia
	if err := global.GVA_DB.WithContext(ctx).Select("id", "storage_key", "storage_provider").Where("storage_key <> ''").Find(&rows).Error; err != nil {
		return err
	}
	missing := make([]uint, 0)
	for _, row := range rows {
		store := upload.NewOssByType(mediaProvider(row))
		exists, err := store.Exists(ctx, row.StorageKey)
		if err != nil {
			missing = append(missing, row.ID)
			continue
		}
		if !exists {
			missing = append(missing, row.ID)
		}
	}
	if len(missing) > 0 {
		return fmt.Errorf("media files missing for record ids: %v", missing)
	}
	return nil
}

func mediaProvider(row catlink.CatMedia) string {
	if strings.TrimSpace(row.StorageProvider) == "" {
		return "local"
	}
	return row.StorageProvider
}

func deleteMediaObject(ctx context.Context, row catlink.CatMedia) error {
	err := upload.NewOssByType(mediaProvider(row)).DeleteFile(ctx, row.StorageKey)
	if err != nil && strings.Contains(err.Error(), "不存在") {
		return nil
	}
	return err
}

func persistMediaWithQuota(ctx context.Context, uid uint, row *catlink.CatMedia, data []byte) error {
	return persistMediaReaderWithQuota(ctx, uid, row, bytes.NewReader(data), int64(len(data)))
}

func persistMediaReaderWithQuota(ctx context.Context, uid uint, row *catlink.CatMedia, reader io.ReadSeeker, size int64) error {
	store, err := upload.NewManagedOss(row.StorageProvider)
	if err != nil {
		return err
	}
	desiredTemporary := row.Temporary
	desiredExpiresAt := row.ExpiresAt
	pendingExpiry := time.Now().Add(time.Hour)
	row.Temporary = true
	row.ExpiresAt = &pendingExpiry
	row.URL = ""
	if err := global.GVA_DB.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		var user system.SysUser
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Select("id").First(&user, uid).Error; err != nil {
			return err
		}
		var accountBytes, hourlyBytes int64
		var hourlyUploads int64
		if err := tx.Model(&catlink.CatMedia{}).Where("owner_id = ?", uid).Select("COALESCE(SUM(size), 0)").Scan(&accountBytes).Error; err != nil {
			return err
		}
		hourAgo := time.Now().Add(-time.Hour)
		if err := tx.Model(&catlink.CatMedia{}).Where("owner_id = ? AND created_at > ?", uid, hourAgo).
			Select("COALESCE(SUM(size), 0)").Scan(&hourlyBytes).Error; err != nil {
			return err
		}
		if err := tx.Model(&catlink.CatMedia{}).Where("owner_id = ? AND created_at > ?", uid, hourAgo).Count(&hourlyUploads).Error; err != nil {
			return err
		}
		incoming := size
		if accountBytes > maxMediaAccountBytes-incoming {
			return clientMediaError("账号媒体空间已满，请清理后再上传")
		}
		if hourlyUploads >= maxMediaHourlyUploads || hourlyBytes > maxMediaHourlyBytes-incoming {
			return clientMediaError("上传过于频繁，请稍后再试")
		}
		return tx.Create(row).Error
	}); err != nil {
		return err
	}
	if _, err := reader.Seek(0, io.SeekStart); err != nil {
		global.GVA_DB.WithContext(ctx).Unscoped().Delete(&catlink.CatMedia{}, row.ID)
		return err
	}
	url, err := store.PutObject(ctx, row.StorageKey, reader, size, mime.TypeByExtension(filepath.Ext(row.StorageKey)))
	if err != nil {
		return errors.Join(err, global.GVA_DB.WithContext(ctx).Unscoped().Delete(&catlink.CatMedia{}, row.ID).Error)
	}
	if row.Category == "contract" {
		url = "/api/v1/media/" + strconv.FormatUint(uint64(row.ID), 10)
	}
	row.URL = url
	row.Temporary = desiredTemporary
	row.ExpiresAt = desiredExpiresAt
	err = global.GVA_DB.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		if err := tx.Model(&catlink.CatMedia{}).Where("id = ?", row.ID).Updates(map[string]any{
			"url": row.URL, "temporary": row.Temporary, "expires_at": row.ExpiresAt,
		}).Error; err != nil {
			return err
		}
		return syncMediaCatalog(tx, *row)
	})
	if err != nil {
		return errors.Join(err, store.DeleteFile(ctx, row.StorageKey), global.GVA_DB.WithContext(ctx).Unscoped().Delete(&catlink.CatMedia{}, row.ID).Error)
	}
	return nil
}

func saveMedia(uid uint, category, filename, encoded string, temporary bool) (catlink.CatMedia, error) {
	return saveMediaContext(context.Background(), uid, category, filename, encoded, temporary)
}

func saveMediaContext(ctx context.Context, uid uint, category, filename, encoded string, temporary bool) (catlink.CatMedia, error) {
	if !mediaCategories[category] {
		category = "misc"
	}
	if len(encoded) > 15*1024*1024 {
		return catlink.CatMedia{}, clientMediaError("文件过大")
	}
	data, err := base64.StdEncoding.DecodeString(strings.TrimSpace(encoded))
	if err != nil {
		return catlink.CatMedia{}, clientMediaError("base64 无效")
	}
	return saveMediaReaderContext(ctx, uid, category, filename, bytes.NewReader(data), int64(len(data)), temporary)
}

func saveMediaReaderContext(ctx context.Context, uid uint, category, filename string, reader io.ReadSeeker, size int64, temporary bool) (catlink.CatMedia, error) {
	if !mediaCategories[category] {
		category = "misc"
	}
	if size <= 0 || size > 10*1024*1024 {
		return catlink.CatMedia{}, clientMediaError("文件超过 10MB")
	}
	header := make([]byte, min(size, 512))
	if _, err := io.ReadFull(reader, header); err != nil {
		return catlink.CatMedia{}, clientMediaError("文件内容无效")
	}
	if _, err := reader.Seek(0, io.SeekStart); err != nil {
		return catlink.CatMedia{}, clientMediaError("文件内容无效")
	}
	ext := filepath.Ext(filename)
	realExt, ok := detectMedia(header, ext)
	if !ok {
		return catlink.CatMedia{}, clientMediaError("仅支持 JPEG/PNG/WebP/PDF，且扩展名需匹配内容")
	}
	rel := filepath.ToSlash(filepath.Join("catlink", category, time.Now().Format("2006/01"), uuid.NewString()+"."+realExt))
	storageKey := rel
	if category == "contract" {
		storageKey = filepath.ToSlash(filepath.Join(".catlink-private", rel))
	}
	if temporary && category != "post" && category != "misc" && category != "cat" {
		return catlink.CatMedia{}, clientMediaError("仅社区帖子和猫咪相册可使用临时上传")
	}
	var expiresAt *time.Time
	if temporary {
		expires := time.Now().Add(24 * time.Hour)
		expiresAt = &expires
	}
	row := catlink.CatMedia{
		OwnerID: uid, Category: category, StorageKey: storageKey, StorageProvider: global.GVA_CONFIG.System.OssType,
		Temporary: temporary, ExpiresAt: expiresAt, Size: size, CreatedAt: time.Now(),
	}
	if err := persistMediaReaderWithQuota(ctx, uid, &row, reader, size); err != nil {
		return catlink.CatMedia{}, err
	}
	return row, nil
}

func (a *CatShareApi) UploadMedia(c *gin.Context) {
	uid := currentUserId(c)
	requestBytes := c.Request.ContentLength
	if err := reserveMediaUploadAttempt(uid, requestBytes); err != nil {
		var clientErr clientMediaError
		if errors.As(err, &clientErr) {
			fail(c, clientErr.Error())
		} else {
			failDB(c, "上传校验失败，请稍后重试")
		}
		return
	}
	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxMediaRequestBytes)
	var row catlink.CatMedia
	var err error
	if strings.HasPrefix(strings.ToLower(c.ContentType()), "multipart/form-data") {
		if err = c.Request.ParseMultipartForm(512 << 10); err != nil {
			fail(c, "请求格式错误或文件过大")
			return
		}
		if c.Request.MultipartForm != nil {
			defer c.Request.MultipartForm.RemoveAll()
		}
		header, formErr := c.FormFile("file")
		if formErr != nil {
			fail(c, "请选择需要上传的文件")
			return
		}
		file, openErr := header.Open()
		if openErr != nil {
			fail(c, "无法读取上传文件")
			return
		}
		defer file.Close()
		category := c.PostForm("category")
		if category == "" {
			category = "misc"
		}
		row, err = saveMediaReaderContext(
			c.Request.Context(), uid, category, filepath.Base(header.Filename), file, header.Size,
			strings.EqualFold(c.PostForm("temporary"), "true"),
		)
	} else {
		var body struct {
			Category   string `json:"category"`
			Filename   string `json:"filename"`
			DataBase64 string `json:"dataBase64"`
			Base64     string `json:"base64"`
			Extension  string `json:"extension"`
			Temporary  bool   `json:"temporary"`
		}
		if err = c.ShouldBindJSON(&body); err != nil {
			fail(c, "请求格式错误或文件过大")
			return
		}
		encoded := body.DataBase64
		if encoded == "" {
			encoded = body.Base64
		}
		name := body.Filename
		if name == "" {
			name = "share." + body.Extension
		}
		category := body.Category
		if category == "" {
			category = "misc"
		}
		row, err = saveMediaContext(c.Request.Context(), uid, category, name, encoded, body.Temporary)
	}
	if err != nil {
		var clientErr clientMediaError
		if errors.As(err, &clientErr) {
			fail(c, clientErr.Error())
		} else {
			failDB(c, "上传失败，请稍后重试")
		}
		return
	}
	ok(c, gin.H{"id": strconv.FormatUint(uint64(row.ID), 10), "url": row.URL})
}

func catMediaInPublishedUse(ownerID uint, media catlink.CatMedia) (bool, error) {
	var cats []catlink.CatPublicCat
	if err := global.GVA_DB.Select("cover_url", "photo_urls", "cover_media_id", "photo_media_ids").
		Where("owner_id = ? AND state = ?", ownerID, "published").Find(&cats).Error; err != nil {
		return false, err
	}
	for _, item := range cats {
		if item.CoverMediaID == media.ID || item.CoverURL == media.URL {
			return true, nil
		}
		var ids []uint
		_ = json.Unmarshal([]byte(item.PhotoMediaIDs), &ids)
		for _, id := range ids {
			if id == media.ID {
				return true, nil
			}
		}
		for _, url := range jsonStringList(item.PhotoURLs) {
			if url == media.URL {
				return true, nil
			}
		}
	}
	return false, nil
}

func (a *CatShareApi) DeleteTemporaryMedia(c *gin.Context) {
	uid := currentUserId(c)
	id, err := strconv.ParseUint(strings.TrimSpace(c.Param("id")), 10, 64)
	if err != nil || id == 0 {
		fail(c, "临时媒体不存在")
		return
	}
	var row catlink.CatMedia
	if err := global.GVA_DB.Where("id = ? AND owner_id = ?", uint(id), uid).First(&row).Error; err != nil {
		fail(c, "媒体不存在或无权删除")
		return
	}
	if !row.Temporary && row.Category != "cat" {
		fail(c, "该媒体不能由 App 删除")
		return
	}
	if !row.Temporary {
		used, err := catMediaInPublishedUse(uid, row)
		if err != nil {
			failDB(c, "媒体引用检查失败，请稍后重试")
			return
		}
		if used {
			fail(c, "照片仍被公开档案使用，请先取消公开")
			return
		}
	}
	if row.StorageKey == "" {
		fail(c, "媒体存储信息不完整")
		return
	}
	if err := deleteMediaObject(c.Request.Context(), row); err != nil {
		failDB(c, "删除临时媒体失败，请稍后重试")
		return
	}
	if err := global.GVA_DB.Transaction(func(tx *gorm.DB) error {
		if err := deleteMediaCatalog(tx, row); err != nil {
			return err
		}
		return tx.Delete(&catlink.CatMedia{}, row.ID).Error
	}); err != nil {
		failDB(c, "删除临时媒体失败，请稍后重试")
		return
	}
	ok(c, gin.H{"id": strconv.FormatUint(uint64(row.ID), 10)})
}

func (a *CatShareApi) DownloadPrivateMedia(c *gin.Context) {
	uid := currentUserId(c)
	id, err := strconv.ParseUint(strings.TrimSpace(c.Param("id")), 10, 64)
	if err != nil || id == 0 {
		fail(c, "媒体不存在")
		return
	}
	var row catlink.CatMedia
	if err := global.GVA_DB.Where("id = ? AND owner_id = ? AND category = ?", uint(id), uid, "contract").First(&row).Error; err != nil {
		fail(c, "媒体不存在或无权访问")
		return
	}
	storageKey := filepath.ToSlash(strings.TrimSpace(row.StorageKey))
	if !strings.HasPrefix(storageKey, ".catlink-private/catlink/contract/") {
		fail(c, "媒体尚未安全迁移")
		return
	}
	store, err := upload.NewManagedOss(mediaProvider(row))
	if err != nil {
		failDB(c, "媒体存储不可用")
		return
	}
	reader, err := store.GetObject(c.Request.Context(), storageKey)
	if err != nil {
		fail(c, "媒体文件不存在")
		return
	}
	defer reader.Close()
	c.Header("Cache-Control", "private, no-store")
	c.Header("X-Content-Type-Options", "nosniff")
	c.Header("Content-Type", mime.TypeByExtension(filepath.Ext(storageKey)))
	c.Header("Content-Disposition", `attachment; filename="contract`+filepath.Ext(storageKey)+`"`)
	_, _ = io.Copy(c.Writer, reader)
}

func (a *CatShareApi) CreateCatShare(c *gin.Context) {
	uid := currentUserId(c)
	var body struct {
		CatID, Nickname, Story, Gender, AgeBand, Coat, PersonalityTags, HealthSummary, StatusCode, ArchiveReason, CoverURL string
		PhotoURLs                                                                                                          []string `json:"photoUrls"`
	}
	_ = c.ShouldBindJSON(&body)
	if strings.TrimSpace(body.Nickname) == "" {
		fail(c, "猫咪昵称不能为空")
		return
	}
	status, archiveReason, err := validateCatStatusInput(body.StatusCode, body.ArchiveReason)
	if err != nil {
		fail(c, err.Error())
		return
	}
	code := strings.ReplaceAll(uuid.NewString(), "-", "")[:10]
	photos, _ := json.Marshal(body.PhotoURLs)
	row := catlink.CatShare{Code: code, OwnerID: uid, CatID: body.CatID, Nickname: body.Nickname, Story: body.Story, Gender: body.Gender, AgeBand: body.AgeBand, Coat: body.Coat, Tags: body.PersonalityTags, Health: body.HealthSummary, Status: status, ArchiveReason: archiveReason, CoverURL: body.CoverURL, PhotoURLs: string(photos), CreatedAt: time.Now()}
	if err := global.GVA_DB.Create(&row).Error; err != nil {
		fail(c, "生成分享失败")
		return
	}
	ok(c, gin.H{"code": code})
}

func (a *CatShareApi) SharedCat(c *gin.Context) {
	var row catlink.CatShare
	if global.GVA_DB.Where("code=?", c.Param("code")).First(&row).Error != nil {
		fail(c, "分享不存在或已失效")
		return
	}
	var photos []string
	_ = json.Unmarshal([]byte(row.PhotoURLs), &photos)
	ok(c, gin.H{"code": row.Code, "catId": row.CatID, "nickname": row.Nickname, "story": row.Story, "gender": row.Gender, "ageBand": row.AgeBand, "coat": row.Coat, "personalityTags": row.Tags, "healthSummary": row.Health, "statusCode": row.Status, "archiveReason": row.ArchiveReason, "coverUrl": row.CoverURL, "photoUrls": photos})
}
