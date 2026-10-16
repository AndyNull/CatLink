package catlink

import (
	"net/http"
	"testing"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	catmodel "github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/flipped-aurora/gin-vue-admin/server/model/system"
	systemReq "github.com/flipped-aurora/gin-vue-admin/server/model/system/request"
	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

func adoptionTestDB(t *testing.T) {
	t.Helper()
	integrityTestDB(t,
		&system.SysUser{}, &catmodel.CatPublicCat{}, &catmodel.CatApply{}, &catmodel.CatAdoptionEvent{},
		&catmodel.CatTimelineEvent{}, &catmodel.CatNotification{}, &catmodel.CatNotificationOutbox{}, &catmodel.CatMedia{},
	)
}

func TestApplyAdoptionCreatesTimelineNotificationAndRejectsDuplicate(t *testing.T) {
	adoptionTestDB(t)
	require.NoError(t, global.GVA_DB.Create(&catmodel.CatPublicCat{OwnerID: 2, Nickname: "团团", Status: catStatusAdoptable, State: "published"}).Error)
	body := `{"publicCatId":"1","message":"有稳定住所","contactSummary":"微信 cat-home"}`
	created := invokeJSON(t, http.MethodPost, "/api/v1/community/adoption-applications", body, 1, CatApiGroup.ApplyAdoption)
	require.Equal(t, float64(0), created["code"])
	duplicate := invokeJSON(t, http.MethodPost, "/api/v1/community/adoption-applications", body, 1, CatApiGroup.ApplyAdoption)
	require.Equal(t, float64(7), duplicate["code"])

	var application catmodel.CatApply
	require.NoError(t, global.GVA_DB.First(&application).Error)
	require.Equal(t, "微信 cat-home", application.ContactSummary)
	var events, notifications, outboxes int64
	require.NoError(t, global.GVA_DB.Model(&catmodel.CatAdoptionEvent{}).Count(&events).Error)
	require.NoError(t, global.GVA_DB.Model(&catmodel.CatNotification{}).Count(&notifications).Error)
	require.NoError(t, global.GVA_DB.Model(&catmodel.CatNotificationOutbox{}).Count(&outboxes).Error)
	require.EqualValues(t, 1, events)
	require.EqualValues(t, 1, notifications)
	require.EqualValues(t, 1, outboxes)
}

func TestAdoptionApplicantCanWithdrawButCannotApprove(t *testing.T) {
	adoptionTestDB(t)
	require.NoError(t, global.GVA_DB.Create(&catmodel.CatPublicCat{OwnerID: 2, Nickname: "团团", Status: catStatusAdoptable, State: "published"}).Error)
	require.NoError(t, global.GVA_DB.Create(&catmodel.CatApply{CatID: 1, ApplicantID: 1, OwnerID: 2, Status: "pending", CreatedAt: time.Now(), UpdatedAt: time.Now()}).Error)

	denied := invokeJSON(t, http.MethodPost, "/api/v1/adoption-applications/1/timeline", `{"status":"approved"}`, 1, func(c *gin.Context) {
		c.Params = gin.Params{{Key: "applicationId", Value: "1"}}
		CatApiGroup.AddAdoptionEvent(c)
	})
	require.Equal(t, float64(7), denied["code"])
	withdrawn := invokeJSON(t, http.MethodPost, "/api/v1/adoption-applications/1/timeline", `{"status":"withdrawn","note":"计划有变"}`, 1, func(c *gin.Context) {
		c.Params = gin.Params{{Key: "applicationId", Value: "1"}}
		CatApiGroup.AddAdoptionEvent(c)
	})
	require.Equal(t, float64(0), withdrawn["code"])
	reopen := invokeJSON(t, http.MethodPost, "/api/v1/adoption-applications/1/timeline", `{"status":"pending"}`, 2, func(c *gin.Context) {
		c.Params = gin.Params{{Key: "applicationId", Value: "1"}}
		CatApiGroup.AddAdoptionEvent(c)
	})
	require.Equal(t, float64(7), reopen["code"])
}

func TestApprovingApplicationAdoptsCatAndRejectsCompetitors(t *testing.T) {
	adoptionTestDB(t)
	require.NoError(t, global.GVA_DB.Create(&catmodel.CatPublicCat{OwnerID: 2, Nickname: "团团", Status: catStatusAdoptable, State: "published"}).Error)
	applications := []catmodel.CatApply{
		{CatID: 1, ApplicantID: 1, OwnerID: 2, Status: "home_visit", CreatedAt: time.Now(), UpdatedAt: time.Now()},
		{CatID: 1, ApplicantID: 3, OwnerID: 2, Status: "contact", CreatedAt: time.Now(), UpdatedAt: time.Now()},
	}
	require.NoError(t, global.GVA_DB.Create(&applications).Error)
	approved := invokeJSON(t, http.MethodPost, "/api/v1/adoption-applications/1/timeline", `{"status":"approved","note":"审核通过"}`, 2, func(c *gin.Context) {
		c.Params = gin.Params{{Key: "applicationId", Value: "1"}}
		CatApiGroup.AddAdoptionEvent(c)
	})
	require.Equal(t, float64(0), approved["code"])

	var cat catmodel.CatPublicCat
	require.NoError(t, global.GVA_DB.First(&cat, 1).Error)
	require.Equal(t, catStatusAdopted, cat.Status)
	var rows []catmodel.CatApply
	require.NoError(t, global.GVA_DB.Order("id").Find(&rows).Error)
	require.Equal(t, "approved", rows[0].Status)
	require.Equal(t, "rejected", rows[1].Status)
	require.NotNil(t, rows[0].DecidedAt)
	require.NotNil(t, rows[1].DecidedAt)
	var publicEvents int64
	require.NoError(t, global.GVA_DB.Model(&catmodel.CatTimelineEvent{}).Where("cat_id = ? AND kind = ?", 1, "adoption").Count(&publicEvents).Error)
	require.EqualValues(t, 1, publicEvents)
}

func TestApplyAdoptionRequiresAdoptableCatAndContact(t *testing.T) {
	adoptionTestDB(t)
	require.NoError(t, global.GVA_DB.Create(&catmodel.CatPublicCat{OwnerID: 2, Nickname: "团团", Status: catStatusTreating, State: "published"}).Error)
	missingContact := invokeJSON(t, http.MethodPost, "/api/v1/community/adoption-applications", `{"publicCatId":"1"}`, 1, CatApiGroup.ApplyAdoption)
	require.Equal(t, float64(7), missingContact["code"])
	notAdoptable := invokeJSON(t, http.MethodPost, "/api/v1/community/adoption-applications", `{"publicCatId":"1","contactSummary":"微信 cat-home"}`, 1, CatApiGroup.ApplyAdoption)
	require.Equal(t, float64(7), notAdoptable["code"])
}

func TestAdoptionOwnerAndAdminStateMachineRejectsUnauthorizedAndTerminalChanges(t *testing.T) {
	adoptionTestDB(t)
	require.NoError(t, global.GVA_DB.Create(&catmodel.CatPublicCat{OwnerID: 2, Nickname: "团团", Status: catStatusAdoptable, State: "published"}).Error)
	require.NoError(t, global.GVA_DB.Create(&catmodel.CatApply{CatID: 1, ApplicantID: 1, OwnerID: 2, Status: "pending", CreatedAt: time.Now(), UpdatedAt: time.Now()}).Error)

	unauthorized := invokeJSON(t, http.MethodPost, "/api/v1/adoption-applications/1/timeline", `{"status":"contact"}`, 3, func(c *gin.Context) {
		c.Params = gin.Params{{Key: "applicationId", Value: "1"}}
		CatApiGroup.AddAdoptionEvent(c)
	})
	require.Equal(t, float64(7), unauthorized["code"])

	adminTransition := func(status string) map[string]any {
		return invokeJSON(t, http.MethodPut, "/api/v1/moderation/adoptions/1/status", `{"status":"`+status+`","note":"后台复核"}`, 0, func(c *gin.Context) {
			c.Set("claims", &systemReq.CustomClaims{BaseClaims: systemReq.BaseClaims{ID: 99}})
			c.Params = gin.Params{{Key: "id", Value: "1"}}
			CatApiGroup.SetAdoptionStatus(c)
		})
	}
	require.Equal(t, float64(7), adminTransition("withdrawn")["code"])
	require.Equal(t, float64(0), adminTransition("contact")["code"])
	require.Equal(t, float64(0), adminTransition("rejected")["code"])
	require.Equal(t, float64(7), adminTransition("approved")["code"])
}

func TestAdminAdoptionListQualifiesStatusWithJoinedCatTable(t *testing.T) {
	adoptionTestDB(t)
	require.NoError(t, global.GVA_DB.Create(&system.SysUser{GVA_MODEL: global.GVA_MODEL{ID: 1}, NickName: "申请人"}).Error)
	require.NoError(t, global.GVA_DB.Create(&system.SysUser{GVA_MODEL: global.GVA_MODEL{ID: 2}, NickName: "发布者"}).Error)
	require.NoError(t, global.GVA_DB.Create(&catmodel.CatPublicCat{ID: 1, OwnerID: 2, Nickname: "团团", Status: catStatusAdoptable, State: "published"}).Error)
	require.NoError(t, global.GVA_DB.Create(&catmodel.CatApply{CatID: 1, ApplicantID: 1, OwnerID: 2, Status: "pending", CreatedAt: time.Now(), UpdatedAt: time.Now()}).Error)

	response := invokeJSON(t, http.MethodGet, "/api/v1/moderation/adoptions?status=pending", "", 0, CatApiGroup.Adoptions)
	require.Equal(t, float64(0), response["code"])
	require.Equal(t, float64(1), response["data"].(map[string]any)["total"])
}

func TestAdoptionMigrationIsIdempotent(t *testing.T) {
	adoptionTestDB(t)
	createdAt := time.Date(2026, time.August, 22, 10, 30, 0, 0, time.Local)
	require.NoError(t, global.GVA_DB.Create(&catmodel.CatPublicCat{ID: 1, OwnerID: 2, Nickname: "团团", Status: catStatusAdoptable, State: "published"}).Error)
	require.NoError(t, global.GVA_DB.Session(&gorm.Session{SkipHooks: true}).Create(&catmodel.CatApply{ID: 1, CatID: 1, ApplicantID: 1, OwnerID: 2, Status: "completed", CreatedAt: createdAt}).Error)

	require.NoError(t, migrateAdoptionApplications())
	require.NoError(t, migrateAdoptionApplications())

	var application catmodel.CatApply
	require.NoError(t, global.GVA_DB.First(&application, 1).Error)
	require.Equal(t, "approved", application.Status)
	require.False(t, application.UpdatedAt.IsZero())
	require.NotNil(t, application.DecidedAt)
	var cat catmodel.CatPublicCat
	require.NoError(t, global.GVA_DB.First(&cat, 1).Error)
	require.Equal(t, catStatusAdopted, cat.Status)
	var events int64
	require.NoError(t, global.GVA_DB.Model(&catmodel.CatAdoptionEvent{}).Where("apply_id = ?", 1).Count(&events).Error)
	require.EqualValues(t, 1, events)
}

func TestAdoptionNotificationDistinguishesTransitionFromSupplement(t *testing.T) {
	adoptionTestDB(t)
	require.NoError(t, global.GVA_DB.Create(&catmodel.CatPublicCat{OwnerID: 2, Nickname: "团团", Status: catStatusAdoptable, State: "published"}).Error)
	require.NoError(t, global.GVA_DB.Create(&catmodel.CatApply{CatID: 1, ApplicantID: 1, OwnerID: 2, Status: "pending", CreatedAt: time.Now(), UpdatedAt: time.Now()}).Error)

	transition := invokeJSON(t, http.MethodPost, "/api/v1/adoption-applications/1/timeline", `{"status":"contact","note":"开始沟通"}`, 2, func(c *gin.Context) {
		c.Params = gin.Params{{Key: "applicationId", Value: "1"}}
		CatApiGroup.AddAdoptionEvent(c)
	})
	require.Equal(t, float64(0), transition["code"])
	supplement := invokeJSON(t, http.MethodPost, "/api/v1/adoption-applications/1/timeline", `{"status":"contact","note":"补充居住信息"}`, 2, func(c *gin.Context) {
		c.Params = gin.Params{{Key: "applicationId", Value: "1"}}
		CatApiGroup.AddAdoptionEvent(c)
	})
	require.Equal(t, float64(0), supplement["code"])

	var notifications []catmodel.CatNotification
	require.NoError(t, global.GVA_DB.Order("id asc").Find(&notifications).Error)
	require.Equal(t, "领养申请状态更新", notifications[0].Title)
	require.Equal(t, "领养申请有新补充", notifications[1].Title)
}

func TestCatStatusChangeClosesActiveAdoptionApplications(t *testing.T) {
	adoptionTestDB(t)
	require.NoError(t, global.GVA_DB.Create(&catmodel.CatPublicCat{OwnerID: 2, Nickname: "团团", Status: catStatusAdoptable, State: "published"}).Error)
	rows := []catmodel.CatApply{
		{CatID: 1, ApplicantID: 3, OwnerID: 2, Status: "pending", CreatedAt: time.Now(), UpdatedAt: time.Now()},
		{CatID: 1, ApplicantID: 4, OwnerID: 2, Status: "contact", CreatedAt: time.Now(), UpdatedAt: time.Now()},
	}
	require.NoError(t, global.GVA_DB.Create(&rows).Error)

	response := invokeJSON(t, http.MethodPut, "/api/v1/moderation/cats/1/status", `{"status":"treating","statusReason":"继续治疗"}`, 99, func(c *gin.Context) {
		c.Set("claims", &systemReq.CustomClaims{BaseClaims: systemReq.BaseClaims{ID: 99}})
		c.Params = gin.Params{{Key: "id", Value: "1"}}
		CatApiGroup.SetCatStatus(c)
	})
	require.Equal(t, float64(0), response["code"])

	require.NoError(t, global.GVA_DB.Order("id").Find(&rows).Error)
	for _, row := range rows {
		require.Equal(t, "rejected", row.Status)
		require.NotNil(t, row.DecidedAt)
	}
	var events, notifications, outboxes int64
	require.NoError(t, global.GVA_DB.Model(&catmodel.CatAdoptionEvent{}).Count(&events).Error)
	require.NoError(t, global.GVA_DB.Model(&catmodel.CatNotification{}).Count(&notifications).Error)
	require.NoError(t, global.GVA_DB.Model(&catmodel.CatNotificationOutbox{}).Count(&outboxes).Error)
	require.EqualValues(t, 2, events)
	require.EqualValues(t, 2, notifications)
	require.EqualValues(t, 2, outboxes)
}

func TestAdoptionTimelineRejectsPrivateContractMaterial(t *testing.T) {
	adoptionTestDB(t)
	require.NoError(t, global.GVA_DB.Create(&catmodel.CatPublicCat{OwnerID: 2, Nickname: "团团", Status: catStatusAdoptable, State: "published"}).Error)
	require.NoError(t, global.GVA_DB.Create(&catmodel.CatApply{CatID: 1, ApplicantID: 1, OwnerID: 2, Status: "pending", CreatedAt: time.Now(), UpdatedAt: time.Now()}).Error)
	require.NoError(t, global.GVA_DB.Create(&catmodel.CatMedia{OwnerID: 1, Category: "contract", URL: "https://private.example/contract.pdf"}).Error)

	response := invokeJSON(t, http.MethodPost, "/api/v1/adoption-applications/1/timeline", `{"status":"pending","mediaUrls":"https://private.example/contract.pdf"}`, 1, func(c *gin.Context) {
		c.Params = gin.Params{{Key: "applicationId", Value: "1"}}
		CatApiGroup.AddAdoptionEvent(c)
	})
	require.Equal(t, float64(7), response["code"])
	require.Contains(t, response["msg"], "私有合同")
}
