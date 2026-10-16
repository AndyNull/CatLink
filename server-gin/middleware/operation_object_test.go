package middleware

import (
	"testing"

	"github.com/stretchr/testify/require"
)

func TestOperationObject(t *testing.T) {
	tests := []struct{ path, kind, id string }{
		{"/api/v1/community/posts/42/comments", "post", "42"},
		{"/api/v1/community/comments/9/like", "comment", "9"},
		{"/api/v1/public/cats/7/timeline", "cat", "7"},
		{"/api/storageManager/migrate/3/rollback", "storage_migration", "3"},
	}
	for _, test := range tests {
		kind, id := operationObject(test.path)
		require.Equal(t, test.kind, kind)
		require.Equal(t, test.id, id)
	}
}
