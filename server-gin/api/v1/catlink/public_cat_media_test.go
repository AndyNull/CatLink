package catlink

import (
	"net/http"
	"testing"
	"time"

	catmodel "github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/require"
)

func TestPublishPublicCatConfirmsTemporaryAlbumMedia(t *testing.T) {
	db := integrityTestDB(t, &catmodel.CatPublicCat{}, &catmodel.CatMedia{})
	expires := time.Now().Add(time.Hour)
	media := catmodel.CatMedia{OwnerID: 7, Category: "cat", URL: "/uploads/catlink/cat/photo.webp", Temporary: true, ExpiresAt: &expires}
	require.NoError(t, db.Create(&media).Error)

	envelope := invokeJSON(t, http.MethodPost, "/api/v1/public/cats",
		`{"id":"local-cat","nickname":"小猫","coverUrl":"/uploads/catlink/cat/photo.webp","photoUrls":["/uploads/catlink/cat/photo.webp"]}`,
		7, CatApiGroup.Publish)
	require.Equal(t, float64(0), envelope["code"])

	var confirmed catmodel.CatMedia
	require.NoError(t, db.First(&confirmed, media.ID).Error)
	require.False(t, confirmed.Temporary)
	require.Nil(t, confirmed.ExpiresAt)
	var count int64
	require.NoError(t, db.Model(&catmodel.CatPublicCat{}).Count(&count).Error)
	require.EqualValues(t, 1, count)
	var publicCat catmodel.CatPublicCat
	require.NoError(t, db.First(&publicCat).Error)
	require.Equal(t, media.ID, publicCat.CoverMediaID)
	require.Equal(t, []uint{media.ID}, jsonUintList(publicCat.PhotoMediaIDs))
	require.NoError(t, db.Model(&media).Update("url", "/uploads/catlink/cat/migrated.webp").Error)
	detail := invokeJSON(t, http.MethodGet, "/api/v1/public/cats/1", "", 0, func(c *gin.Context) {
		c.Params = gin.Params{{Key: "id", Value: "1"}}
		CatApiGroup.Detail(c)
	})
	require.Equal(t, "/uploads/catlink/cat/migrated.webp", detail["data"].(map[string]any)["coverUrl"])
}

func TestPublishPublicCatRollsBackWhenAlbumMediaIsNotOwned(t *testing.T) {
	db := integrityTestDB(t, &catmodel.CatPublicCat{}, &catmodel.CatMedia{})
	expires := time.Now().Add(time.Hour)
	media := catmodel.CatMedia{OwnerID: 8, Category: "cat", URL: "/uploads/catlink/cat/foreign.webp", Temporary: true, ExpiresAt: &expires}
	require.NoError(t, db.Create(&media).Error)

	envelope := invokeJSON(t, http.MethodPost, "/api/v1/public/cats",
		`{"id":"local-cat","nickname":"小猫","photoUrls":["/uploads/catlink/cat/foreign.webp"]}`,
		7, CatApiGroup.Publish)
	require.Equal(t, float64(7), envelope["code"])

	var count int64
	require.NoError(t, db.Model(&catmodel.CatPublicCat{}).Count(&count).Error)
	require.Zero(t, count)
	var remaining catmodel.CatMedia
	require.NoError(t, db.First(&remaining, media.ID).Error)
	require.True(t, remaining.Temporary)
}
