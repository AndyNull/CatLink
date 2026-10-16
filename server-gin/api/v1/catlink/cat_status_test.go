package catlink

import (
	"net/http"
	"testing"
	"time"

	catmodel "github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/require"
)

func TestCalendarEpochDayUsesLocalDate(t *testing.T) {
	shanghai := time.FixedZone("Asia/Shanghai", 8*60*60)
	value := time.Date(2026, 8, 22, 0, 30, 0, 0, shanghai)
	require.Equal(t, time.Date(2026, 8, 22, 0, 0, 0, 0, time.UTC).Unix()/86400, calendarEpochDay(value))
}

func TestCatStatusModelHasFiveCanonicalValues(t *testing.T) {
	require.Equal(t, map[string]string{
		"rescue": "救助中", "treating": "治疗中", "adoptable": "待领养", "adopted": "已领养", "archived": "已归档",
	}, catStatusLabels)
	status, reason, ok := normalizeCatStatus("LOST_CONTACT")
	require.True(t, ok)
	require.Equal(t, catStatusArchived, status)
	require.Equal(t, archiveReasonLost, reason)
	status, reason, ok = normalizeCatStatus("DECEASED")
	require.True(t, ok)
	require.Equal(t, catStatusArchived, status)
	require.Equal(t, archiveReasonDeceased, reason)
}

func TestAdminCatStatusUpdateEnforcesTransitionAndArchiveReason(t *testing.T) {
	db := integrityTestDB(t, &catmodel.CatPublicCat{}, &catmodel.CatApply{})
	require.NoError(t, db.Create(&catmodel.CatPublicCat{LocalID: "cat-1", Status: catStatusRescue}).Error)
	invoke := func(body string) map[string]any {
		return invokeJSON(t, http.MethodPut, "/v1/moderation/cats/1/status", body, 0, func(c *gin.Context) {
			c.Params = gin.Params{{Key: "id", Value: "1"}}
			CatApiGroup.SetCatStatus(c)
		})
	}
	require.Equal(t, float64(7), invoke(`{"status":"adopted"}`)["code"])
	require.Equal(t, float64(0), invoke(`{"status":"treating","statusReason":"住院治疗"}`)["code"])
	require.Equal(t, float64(7), invoke(`{"status":"archived"}`)["code"])
	archived := invoke(`{"status":"archived","archiveReason":"lost"}`)
	require.Equal(t, float64(0), archived["code"])
	require.Equal(t, "lost", archived["data"].(map[string]any)["archiveReason"])
}

func TestCatStatusTransitionRules(t *testing.T) {
	require.True(t, canChangeCatStatus(catStatusRescue, catStatusTreating, ""))
	require.True(t, canChangeCatStatus(catStatusAdoptable, catStatusAdopted, ""))
	require.False(t, canChangeCatStatus(catStatusRescue, catStatusAdopted, ""))
	require.False(t, canChangeCatStatus(catStatusAdopted, catStatusTreating, ""))
	require.True(t, canChangeCatStatus(catStatusArchived, catStatusAdoptable, archiveReasonLost))
	require.False(t, canChangeCatStatus(catStatusArchived, catStatusAdoptable, archiveReasonDeceased))
	_, _, err := validateCatStatusInput(catStatusArchived, "")
	require.EqualError(t, err, "归档必须选择失踪、离世或其他原因")
}

func TestCatStatusMigrationKeepsRowsAndLegacyValue(t *testing.T) {
	db := integrityTestDB(t, &catmodel.CatPublicCat{}, &catmodel.CatShare{})
	rows := []catmodel.CatPublicCat{
		{LocalID: "intake", Status: "INTAKE"},
		{LocalID: "lost", Status: "LOST_CONTACT"},
		{LocalID: "deceased", Status: "DECEASED"},
		{LocalID: "custom", Status: "CUSTOM_STAGE"},
	}
	require.NoError(t, db.Create(&rows).Error)
	require.NoError(t, migrateCatStatuses())
	var count int64
	require.NoError(t, db.Model(&catmodel.CatPublicCat{}).Count(&count).Error)
	require.EqualValues(t, 4, count)
	var migrated []catmodel.CatPublicCat
	require.NoError(t, db.Order("id").Find(&migrated).Error)
	require.Equal(t, catStatusRescue, migrated[0].Status)
	require.Equal(t, archiveReasonLost, migrated[1].ArchiveReason)
	require.Equal(t, archiveReasonDeceased, migrated[2].ArchiveReason)
	require.Equal(t, catStatusRescue, migrated[3].Status)
	require.Equal(t, "CUSTOM_STAGE", migrated[3].LegacyStatus)
	require.NoError(t, db.Create(&catmodel.CatShare{Code: "custom", Status: "CUSTOM_STAGE"}).Error)
	require.NoError(t, migrateCatStatuses())
	var share catmodel.CatShare
	require.NoError(t, db.First(&share, "code = ?", "custom").Error)
	require.Equal(t, catStatusRescue, share.Status)
}
