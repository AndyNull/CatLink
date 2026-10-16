package router

import (
	"context"
	"go-admin/app/admin/apis"
	"mime"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/go-admin-team/go-admin-core/v2/sdk"
	"github.com/go-admin-team/go-admin-core/v2/sdk/config"

	"github.com/gin-gonic/gin"
	jwt "github.com/go-admin-team/go-admin-core/v2/jwtauth"
	"github.com/go-admin-team/go-admin-core/v2/sdk/pkg/ws"
	ginSwagger "github.com/swaggo/gin-swagger"

	swaggerfiles "github.com/swaggo/files"

	"go-admin/common/middleware"
	"go-admin/common/middleware/handler"
	_ "go-admin/docs/admin"
)

func InitSysRouter(r *gin.Engine, authMiddleware *jwt.GinJWTMiddleware) *gin.RouterGroup {
	g := r.Group("")
	sysBaseRouter(g)
	sysStaticFileRouter(g, authMiddleware)
	if config.ApplicationConfig.Mode != "prod" && strings.EqualFold(strings.TrimSpace(os.Getenv("CATLINK_ENABLE_SWAGGER")), "true") {
		sysSwaggerRouter(g, authMiddleware)
	}
	// 需要认证
	sysCheckRoleRouterInit(g, authMiddleware)
	return g
}

func sysBaseRouter(r *gin.RouterGroup) {

	go ws.WebsocketManager.Start()
	go ws.WebsocketManager.SendService()
	go ws.WebsocketManager.SendAllService()

	registerProbeRoutes(r)
	r.GET("/info", handler.Ping)
}

func registerProbeRoutes(r *gin.RouterGroup) {
	r.GET("/healthz", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "ok"})
	})
	readiness := func(c *gin.Context) {
		db := sdk.Runtime.GetDbByTenant(c.Request.Host)
		if db == nil {
			c.JSON(http.StatusServiceUnavailable, gin.H{"status": "not_ready"})
			return
		}
		sqlDB, err := db.DB()
		if err == nil {
			ctx, cancel := context.WithTimeout(c.Request.Context(), time.Second)
			defer cancel()
			err = sqlDB.PingContext(ctx)
		}
		if err != nil {
			c.JSON(http.StatusServiceUnavailable, gin.H{"status": "not_ready"})
			return
		}
		c.JSON(http.StatusOK, gin.H{"status": "ready"})
	}
	r.GET("/readyz", readiness)
	// Kept for older 1Panel scripts; new deployments should use /readyz.
	r.GET("/api/ready", readiness)
}

func sysStaticFileRouter(r *gin.RouterGroup, authMiddleware *jwt.GinJWTMiddleware) {
	err := mime.AddExtensionType(".js", "application/javascript")
	if err != nil {
		return
	}
	r.Static("/static/uploadfile", "./static/uploadfile")
	if config.ApplicationConfig.Mode != "prod" && strings.EqualFold(strings.TrimSpace(os.Getenv("CATLINK_ENABLE_DEV_TOOLS")), "true") {
		r.Group("/form-generator").Use(authMiddleware.MiddlewareFunc()).Static("", "./static/form-generator")
	}
}

func sysSwaggerRouter(r *gin.RouterGroup, authMiddleware *jwt.GinJWTMiddleware) {
	r.Group("/swagger/admin").Use(authMiddleware.MiddlewareFunc()).GET("/*any", ginSwagger.WrapHandler(swaggerfiles.NewHandler(), ginSwagger.InstanceName("admin")))
}

func sysCheckRoleRouterInit(r *gin.RouterGroup, authMiddleware *jwt.GinJWTMiddleware) {
	wss := r.Group("").Use(authMiddleware.MiddlewareFunc())
	{
		wss.GET("/ws/:id/:channel", ws.WebsocketManager.WsClient)
		wss.GET("/wslogout/:id/:channel", ws.WebsocketManager.UnWsClient)
	}

	v1 := r.Group("/admin-api")
	{
		v1.POST("/login", authMiddleware.LoginHandler)
		// GET /admin-api/refresh_token 已移除，原因见 issue #820：
		// 该接口用业务 token 即可换取新 token，而续期上限 MaxRefresh 依据的
		// orig_iat 在每次续期时被一并重置，上限永远无法到达 —— token 一旦泄
		// 露即等同于永久访问权。它此前还位于 CasbinExclude 中，任何角色的已
		// 登录用户都能调用，不受权限约束。
		//
		// 官方前端从未调用该接口（store 中的 refreshToken action 无人 dispatch），
		// 移除不影响正常使用。若确需无感续期，应在 go-admin-core 中区分
		// access token 与 refresh token 后重新实现，而非沿用此路由。
	}
	registerBaseRouter(v1, authMiddleware)
}

func registerBaseRouter(v1 *gin.RouterGroup, authMiddleware *jwt.GinJWTMiddleware) {
	api := apis.SysMenu{}
	api2 := apis.SysDept{}
	v1auth := v1.Group("").Use(authMiddleware.MiddlewareFunc()).Use(middleware.AuthCheckRole())
	{
		v1auth.GET("/roleMenuTreeselect/:roleId", api.GetMenuTreeSelect)
		//v1.GET("/menuTreeselect", api.GetMenuTreeSelect)
		v1auth.GET("/roleDeptTreeselect/:roleId", api2.GetDeptTreeRoleSelect)
		v1auth.POST("/logout", handler.LogOut)
	}
}
