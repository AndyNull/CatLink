package api

import (
	catapi "go-admin/app/catlink/apis"

	"github.com/gin-gonic/gin"
	"github.com/go-admin-team/go-admin-core/v2/sdk"
)

func init() {
	AppRouters = append(AppRouters, mountAppNativeRoutes)
}

func mountAppNativeRoutes() {
	engine, ok := sdk.Runtime.GetEngine().(*gin.Engine)
	if !ok {
		panic("unsupported HTTP engine")
	}
	engine.POST("/api/v1/vault/sync", catapi.AppAuth, catapi.VaultSync)
	engine.GET("/api/v1/auth/policy", catapi.AppAuthPolicy)
	engine.POST("/api/v1/auth/captcha", catapi.AppCaptcha)
	engine.POST("/api/v1/auth/login", catapi.AppLogin)
	engine.POST("/api/v1/auth/refresh", catapi.AppRefresh)
	engine.POST("/api/v1/auth/logout", catapi.AppLogoutAuth, catapi.AppLogout)
	engine.GET("/api/v1/auth/getUserInfo", catapi.AppAuth, catapi.AppUserInfo)
	engine.GET("/api/v1/account", catapi.AppAuth, catapi.AppAccount)
	engine.PATCH("/api/v1/account", catapi.AppAuth, catapi.AppUpdateAccount)
	engine.GET("/api/v1/account/devices", catapi.AppAuth, catapi.AppDeviceList)
	engine.DELETE("/api/v1/account/devices/:deviceId", catapi.AppAuth, catapi.AppDeviceRevoke)
	engine.GET("/api/v1/account/notifications", catapi.AppAuth, catapi.AppNotifications)
	engine.POST("/api/v1/account/notifications/read", catapi.AppAuth, catapi.AppMarkNotificationsRead)
	engine.GET("/api/v1/auth/notifications", catapi.AppAuth, catapi.AppNotifications)
	engine.PUT("/api/v1/auth/notifications/read", catapi.AppAuth, catapi.AppMarkNotificationsRead)
	engine.POST("/api/v1/vault/enable", catapi.AppAuth, catapi.AppVaultEnable)
	engine.POST("/api/v1/vault/session-key", catapi.AppAuth, catapi.AppVaultOpenSession)
	engine.GET("/api/v1/vault/session-key", catapi.AppAuth, catapi.AppVaultCurrentSession)
	engine.PUT("/api/v1/vault/recovery-key", catapi.AppAuth, catapi.AppVaultRecoveryKey)
}
