package catlink

import (
	"os"
	"strings"
	"time"

	gormadapter "github.com/casbin/gorm-adapter/v3"
	"github.com/flipped-aurora/gin-vue-admin/server/global"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	"github.com/flipped-aurora/gin-vue-admin/server/utils"
	"github.com/google/uuid"
	"gorm.io/gorm"
)

const (
	CatAdminAuthority uint = 888
	CatAppAuthority   uint = 9528
)

func catAdminApiDescription(path, method string) string {
	labels := map[string]string{
		"/v1/moderation/stats": "运营统计", "/v1/moderation/users": "用户管理",
		"/v1/moderation/posts": "社区帖子", "/v1/moderation/cats": "公开猫咪",
		"/v1/moderation/reports": "举报审核", "/v1/moderation/quotes": "一言管理",
		"/v1/moderation/encouragement-ai":   "AI 一言配置",
		"/v1/moderation/email":              "邮件服务配置",
		"/v1/moderation/moderation-appeals": "内容申诉",
		"/v1/moderation/ads":                "广告管理", "/v1/moderation/media": "媒体管理",
		"/v1/moderation/versions": "版本管理", "/v1/moderation/adoptions": "领养申请",
		"/v1/moderation/notifications/broadcast": "发布公告",
		"/v1/moderation/notifications/campaigns": "公告活动",
		"/v1/moderation/settings":                "系统设置",
		"/v1/moderation/logs":                    "操作日志",
		"/v1/moderation/metrics":                 "运行指标",
		"/v1/moderation/retention":               "数据保留策略",
		"/v1/moderation/retention/preview":       "保留策略清理预览",
		"/v1/moderation/identity-badges":         "身份认证",
	}
	for prefix, label := range labels {
		if path == prefix || strings.HasPrefix(path, prefix+"/") {
			return label + " " + method
		}
	}
	return "CatLink 管理 " + method
}

func catAdminApiGroup(path string) string {
	switch {
	case strings.Contains(path, "/posts"), strings.Contains(path, "/reports"):
		return "社区治理"
	case strings.Contains(path, "/cats"), strings.Contains(path, "/adoptions"):
		return "猫咪与领养"
	case strings.Contains(path, "/quotes"), strings.Contains(path, "/ads"):
		return "内容运营"
	case strings.Contains(path, "/versions"), strings.Contains(path, "/notifications"):
		return "发布与通知"
	default:
		return "运营概览"
	}
}

func catAppApiGroup(path string) string {
	switch {
	case strings.Contains(path, "/auth/"), strings.HasPrefix(path, "/v1/account"):
		return "App 账号与会话"
	case strings.Contains(path, "/community/"):
		return "App 社区"
	case strings.Contains(path, "/public/cats"), strings.Contains(path, "/share/"):
		return "App 猫咪与分享"
	case strings.Contains(path, "/media/"):
		return "App 媒体"
	case strings.Contains(path, "/conversation"), strings.Contains(path, "/notifications"):
		return "App 消息通知"
	case strings.Contains(path, "/vault/"):
		return "App 加密同步"
	default:
		return "App 公共能力"
	}
}

func pruneUnusedMenus(db *gorm.DB) {
	names := []string{
		"catlinkDashboard", "example", "customer",
		"plugin-email", "anInfo", "https://www.gin-vue-admin.com", "about",
		"system", "sysVersion",
	}
	var menus []system.SysBaseMenu
	db.Unscoped().Where("name IN ?", names).Find(&menus)
	ids := make([]uint, 0, len(menus))
	for _, menu := range menus {
		ids = append(ids, menu.ID)
	}
	if len(ids) == 0 {
		return
	}
	db.Where("sys_base_menu_id IN ?", ids).Delete(&system.SysAuthorityMenu{})
	db.Unscoped().Where("sys_base_menu_id IN ?", ids).Delete(&system.SysBaseMenuParameter{})
	db.Unscoped().Where("sys_base_menu_id IN ?", ids).Delete(&system.SysBaseMenuBtn{})
	db.Unscoped().Where("id IN ?", ids).Delete(&system.SysBaseMenu{})
}

func ensureCatLinkBusinessMenus(db *gorm.DB) {
	menus := []system.SysBaseMenu{
		{ParentId: 0, Path: "community-admin", Name: "communityAdmin", Component: "view/catlink/community.vue", Sort: 2, Meta: system.Meta{Title: "社区治理", Icon: "chat-dot-round", KeepAlive: true}},
		{ParentId: 0, Path: "cat-admin", Name: "catAdmin", Component: "view/catlink/cats.vue", Sort: 3, Meta: system.Meta{Title: "猫咪与领养", Icon: "collection", KeepAlive: true}},
		{ParentId: 0, Path: "content-admin", Name: "contentAdmin", Component: "view/catlink/content.vue", Sort: 4, Meta: system.Meta{Title: "内容运营", Icon: "document", KeepAlive: true}},
		{ParentId: 0, Path: "release-admin", Name: "releaseAdmin", Component: "view/catlink/release.vue", Sort: 5, Meta: system.Meta{Title: "发布与通知", Icon: "promotion", KeepAlive: true}},
		{ParentId: 0, Path: "operations-admin", Name: "operationsAdmin", Component: "view/catlink/operations.vue", Sort: 6, Meta: system.Meta{Title: "运行与治理", Icon: "monitor", KeepAlive: true}},
	}
	for _, seed := range menus {
		db.Where("name = ?", seed.Name).Assign(seed).FirstOrCreate(&system.SysBaseMenu{})
	}
	db.Model(&system.SysBaseMenu{}).Where("name = ?", "dashboard").Updates(map[string]any{
		"sort": 1, "title": "运营概览", "icon": "odometer",
	})
}

func managedAdminPath(path string) bool {
	if strings.HasPrefix(path, "/base/") || strings.HasPrefix(path, "/init/") || strings.HasPrefix(path, "/swagger/") {
		return false
	}
	return !strings.HasPrefix(path, "/v1/") || strings.HasPrefix(path, "/v1/moderation/")
}

// FinalizeCatAdminAccess reconciles API metadata and policies with routes actually enabled by CatLink.
func FinalizeCatAdminAccess() error {
	db := global.GVA_DB
	prefix := strings.TrimSuffix(global.GVA_CONFIG.System.RouterPrefix, "/")
	registered := make(map[string]system.SysApi)
	for _, route := range global.GVA_ROUTERS {
		if !strings.HasPrefix(route.Path, prefix+"/") {
			continue
		}
		path := strings.TrimPrefix(route.Path, prefix)
		group := "系统能力"
		description := route.Method + " " + path
		if strings.HasPrefix(path, "/v1/") {
			group = catAppApiGroup(path)
			description = "CatLink " + group + " " + route.Method
		}
		registered[route.Method+" "+path] = system.SysApi{Path: path, Method: route.Method, ApiGroup: group, Description: description, Source: system.InferApiSource(path, group)}
	}
	err := db.Transaction(func(tx *gorm.DB) error {
		for _, api := range registered {
			if err := tx.Where("path = ? AND method = ?", api.Path, api.Method).Attrs(api).FirstOrCreate(&system.SysApi{}).Error; err != nil {
				return err
			}
		}
		var apis []system.SysApi
		if err := tx.Find(&apis).Error; err != nil {
			return err
		}
		for _, api := range apis {
			if _, exists := registered[api.Method+" "+api.Path]; exists {
				continue
			}
			if err := tx.Where("v1 = ? AND v2 = ?", api.Path, api.Method).Delete(&gormadapter.CasbinRule{}).Error; err != nil {
				return err
			}
			if err := tx.Unscoped().Delete(&api).Error; err != nil {
				return err
			}
		}
		if err := tx.Where("ptype = ? AND v0 = ?", "p", "888").Delete(&gormadapter.CasbinRule{}).Error; err != nil {
			return err
		}
		for _, api := range registered {
			if !managedAdminPath(api.Path) {
				continue
			}
			policy := gormadapter.CasbinRule{Ptype: "p", V0: "888", V1: api.Path, V2: api.Method}
			if err := tx.Where("ptype = ? AND v0 = ? AND v1 = ? AND v2 = ?", policy.Ptype, policy.V0, policy.V1, policy.V2).FirstOrCreate(&policy).Error; err != nil {
				return err
			}
		}
		return nil
	})
	if err == nil {
		if enforcer := utils.GetCasbin(); enforcer != nil {
			_ = enforcer.LoadPolicy()
		}
	}
	return err
}

// BootstrapCatLinkAccess 初始化最小 GVA 权限基线；幂等，不覆盖已存在的管理员密码。
func BootstrapCatLinkAccess() {
	if global.GVA_DB == nil {
		return
	}
	db := global.GVA_DB
	now := time.Now()
	rootParent := uint(0)
	adminParent := CatAdminAuthority
	roles := []system.SysAuthority{
		{AuthorityId: CatAdminAuthority, AuthorityName: "CatLink 超级管理员", ParentId: &rootParent, DataScope: 1, DefaultRouter: "dashboard", CreatedAt: now, UpdatedAt: now},
		{AuthorityId: CatAppAuthority, AuthorityName: "CatLink App 用户", ParentId: &adminParent, DataScope: 4, DefaultRouter: "dashboard", CreatedAt: now, UpdatedAt: now},
	}
	for i := range roles {
		var existing system.SysAuthority
		db.Unscoped().Where("authority_id = ?", roles[i].AuthorityId).Assign(map[string]any{
			"authority_name": roles[i].AuthorityName,
			"parent_id":      roles[i].ParentId,
			"data_scope":     roles[i].DataScope,
			"default_router": roles[i].DefaultRouter,
			"deleted_at":     nil,
		}).FirstOrCreate(&existing, system.SysAuthority{AuthorityId: roles[i].AuthorityId})
	}
	// GVA 默认把 9528 当测试后台角色；在 CatLink 中它只能是 App 用户。
	db.Where("sys_authority_authority_id = ?", CatAppAuthority).Delete(&system.SysAuthorityMenu{})
	db.Where("ptype = ? AND v0 = ?", "p", "9528").Delete(&gormadapter.CasbinRule{})

	// 管理员只在显式设置强密码时创建；生产环境绝不生成固定默认密码。
	var admin system.SysUser
	if err := db.Where("username = ?", "admin").First(&admin).Error; err == gorm.ErrRecordNotFound {
		password := strings.TrimSpace(os.Getenv("CATLINK_BOOTSTRAP_ADMIN_PASSWORD"))
		if len(password) >= 12 {
			admin = system.SysUser{
				UUID: uuid.New(), Username: "admin", Password: utils.BcryptHash(password),
				NickName: "CatLink 管理员", AuthorityId: CatAdminAuthority, Enable: 1,
				Email: "admin@catlink.local", MustChangePassword: true,
			}
			db.Create(&admin)
		}
	}

	// 只补当前管理员的角色关联；不降权用户、不删除任何既有多角色关系。
	if admin.ID > 0 {
		db.Exec("INSERT IGNORE INTO sys_user_authority(sys_user_id, sys_authority_authority_id) VALUES (?, ?)", admin.ID, CatAdminAuthority)
	}

	// 最小后台权限基线。只追加 CatLink 与首次改密所需规则，不覆盖后台后续配置。
	// 清理早期错误地包含全局 /api 前缀的 CatLink 规则；Casbin 中间件匹配时会剥离 RouterPrefix。
	db.Where("ptype = ? AND v0 = ? AND v1 LIKE ?", "p", "888", "/api/%").Delete(&gormadapter.CasbinRule{})
	db.Where("ptype = ? AND v0 = ?", "p", "888").Delete(&gormadapter.CasbinRule{})
	adminRules := []struct{ path, method string }{
		{"/user/changePassword", "POST"}, {"/user/getUserInfo", "GET"},
		{"/menu/getMenu", "POST"}, {"/base/captcha", "POST"},
		{"/v1/moderation/stats", "GET"},
		{"/v1/moderation/posts", "GET"},
		{"/v1/moderation/posts/:id/lock", "PUT"}, {"/v1/moderation/posts/:id", "DELETE"},
		{"/v1/moderation/cats", "GET"}, {"/v1/moderation/cats/:id/state", "PUT"}, {"/v1/moderation/cats/:id/status", "PUT"},
		{"/v1/moderation/reports", "GET"}, {"/v1/moderation/reports/:id/resolve", "PUT"},
		{"/v1/moderation/moderation-appeals", "GET"}, {"/v1/moderation/moderation-appeals/:id", "PUT"},
		{"/v1/moderation/quotes", "GET"}, {"/v1/moderation/quotes", "POST"},
		{"/v1/moderation/quotes/import", "POST"},
		{"/v1/moderation/quotes/:id", "PUT"}, {"/v1/moderation/quotes/:id", "DELETE"},
		{"/v1/moderation/encouragement-ai/config", "GET"}, {"/v1/moderation/encouragement-ai/config", "PUT"},
		{"/v1/moderation/encouragement-ai/test", "POST"},
		{"/v1/moderation/email/config", "GET"}, {"/v1/moderation/email/config", "PUT"},
		{"/v1/moderation/email/test", "POST"},
		{"/v1/moderation/ads", "GET"}, {"/v1/moderation/ads", "POST"},
		{"/v1/moderation/ads/:id", "PUT"}, {"/v1/moderation/ads/:id", "DELETE"},
		{"/v1/moderation/versions", "GET"},
		{"/v1/moderation/versions", "POST"}, {"/v1/moderation/versions/:id", "DELETE"},
		{"/v1/moderation/adoptions", "GET"}, {"/v1/moderation/adoptions/:id/status", "PUT"},
		{"/v1/moderation/notifications/broadcast", "POST"},
		{"/v1/moderation/notifications/campaigns", "GET"},
		{"/v1/moderation/notifications/campaigns/:id/withdraw", "PUT"},
		{"/v1/moderation/metrics", "GET"},
		{"/v1/moderation/metrics/prometheus", "GET"},
		{"/v1/moderation/retention", "GET"}, {"/v1/moderation/retention", "POST"},
		{"/v1/moderation/retention/preview", "GET"},
		{"/v1/moderation/identity-badges", "GET"}, {"/v1/moderation/identity-badges/:id", "PUT"},
	}
	for _, rule := range adminRules {
		row := gormadapter.CasbinRule{Ptype: "p", V0: "888", V1: rule.path, V2: rule.method}
		db.Where("ptype = ? AND v0 = ? AND v1 = ? AND v2 = ?", row.Ptype, row.V0, row.V1, row.V2).FirstOrCreate(&row)
		apiRow := system.SysApi{Path: rule.path, Method: rule.method, ApiGroup: catAdminApiGroup(rule.path), Description: catAdminApiDescription(rule.path, rule.method), Source: system.ApiSourceBusiness}
		db.Where("path = ? AND method = ?", rule.path, rule.method).FirstOrCreate(&apiRow)
	}
	pruneUnusedMenus(db)
	ensureCatLinkBusinessMenus(db)
	db.Where("sys_authority_authority_id = ?", CatAdminAuthority).Delete(&system.SysAuthorityMenu{})
	var allMenus []system.SysBaseMenu
	db.Select("id").Find(&allMenus)
	for _, item := range allMenus {
		db.Exec("INSERT IGNORE INTO sys_authority_menus(sys_base_menu_id, sys_authority_authority_id) VALUES (?, ?)", item.ID, CatAdminAuthority)
	}
	db.Model(&system.SysAuthority{}).Where("authority_id = ?", CatAdminAuthority).Update("default_router", "dashboard")
	if enforcer := utils.GetCasbin(); enforcer != nil {
		_ = enforcer.LoadPolicy()
	}
}
