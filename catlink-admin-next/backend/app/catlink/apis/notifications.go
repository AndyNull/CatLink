package apis

import (
	"go-admin/app/catlink/service"

	"github.com/gin-gonic/gin"
	"github.com/go-admin-team/go-admin-core/v2/sdk/pkg"
)

func AppNotifications(c *gin.Context) {
	db, err := pkg.GetOrm(c)
	if err != nil {
		appFailure(c, "通知服务暂不可用")
		return
	}
	items, err := service.AppNotifications(db, c.GetUint("catlink_uid"))
	if err != nil {
		appFailure(c, "通知加载失败，请稍后重试")
		return
	}
	appSuccess(c, gin.H{"items": items})
}

func AppMarkNotificationsRead(c *gin.Context) {
	var request struct {
		ID string `json:"id"`
	}
	if c.Request.ContentLength > 0 && c.ShouldBindJSON(&request) != nil {
		appFailure(c, "请求格式错误")
		return
	}
	db, err := pkg.GetOrm(c)
	if err != nil {
		appFailure(c, "通知服务暂不可用")
		return
	}
	if err := service.MarkAppNotificationsRead(db, c.GetUint("catlink_uid"), request.ID); err != nil {
		appFailure(c, err.Error())
		return
	}
	appSuccess(c, gin.H{"ok": true})
}
