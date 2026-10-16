package catlink

import (
	"testing"

	catmodel "github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"github.com/stretchr/testify/require"
)

func TestCoreQueryCompositeIndexesAreMigrated(t *testing.T) {
	db := integrityTestDB(t,
		&catmodel.CatPublicCat{}, &catmodel.CatPost{}, &catmodel.CatComment{},
		&catmodel.CatNotification{}, &catmodel.CatMessage{}, &catmodel.CatMedia{}, &catmodel.CatMissingCase{}, &catmodel.CatReport{},
	)
	want := map[any][]string{
		&catmodel.CatPublicCat{}:    {"idx_cat_public_state_status_updated", "idx_cat_public_owner_state_updated"},
		&catmodel.CatPost{}:         {"idx_cat_posts_author_created", "idx_cat_posts_locked_created"},
		&catmodel.CatComment{}:      {"idx_cat_comments_post_created", "idx_cat_comments_author_created"},
		&catmodel.CatReport{}:       {"idx_cat_reports_reporter_created"},
		&catmodel.CatNotification{}: {"idx_cat_notifications_user_created"},
		&catmodel.CatMessage{}:      {"idx_cat_messages_conv_created", "idx_cat_messages_sender_created"},
		&catmodel.CatMedia{}:        {"idx_cat_media_owner_created"},
		&catmodel.CatMissingCase{}:  {"idx_cat_missing_status_created"},
	}
	for model, indexes := range want {
		for _, index := range indexes {
			require.True(t, db.Migrator().HasIndex(model, index), index)
		}
	}
}
