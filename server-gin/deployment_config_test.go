package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/stretchr/testify/require"
	"gopkg.in/yaml.v3"
)

func TestProductionComposeUsesExternalDependenciesAndPort58888(t *testing.T) {
	root := filepath.Clean("..")
	compose := readYAMLMap(t, filepath.Join(root, "docker-compose.yml"))
	services := compose["services"].(map[string]any)
	require.Contains(t, services, "backend")
	require.Contains(t, services, "web")
	require.NotContains(t, services, "mysql")
	require.NotContains(t, services, "redis")

	backend := services["backend"].(map[string]any)
	environment := backend["environment"].(map[string]any)
	require.Equal(t, "${CATLINK_DB_HOST:?set CATLINK_DB_HOST}", environment["CATLINK_DB_HOST"])
	require.Equal(t, "${CATLINK_REDIS_ADDR:-}", environment["CATLINK_REDIS_ADDR"])
	require.Equal(t, "${CATLINK_TRUSTED_PROXY_CIDRS:-172.30.88.0/24}", environment["CATLINK_TRUSTED_PROXY_CIDRS"])

	web := services["web"].(map[string]any)
	ports := web["ports"].([]any)
	require.Equal(t, "${CATLINK_BIND_ADDRESS:-0.0.0.0}:${CATLINK_WEB_PORT:-58888}:80", ports[0])

	content, err := os.ReadFile(filepath.Join(root, ".env.production.example"))
	require.NoError(t, err)
	template := string(content)
	require.Contains(t, template, "CATLINK_DB_HOST=host.docker.internal")
	require.Contains(t, template, "CATLINK_REDIS_ADDR=host.docker.internal:6379")
	require.Contains(t, template, "CATLINK_WEB_PORT=58888")
	require.NotContains(t, template, "CATLINK_DB_ROOT_PASSWORD")
	for _, key := range []string{"CATLINK_DB_PASSWORD", "CATLINK_REDIS_PASSWORD", "CATLINK_JWT_SIGNING_KEY", "CATLINK_SMTP_PASSWORD"} {
		require.Contains(t, template, key+"=\n", key)
	}
}

func TestDeploymentScriptsDoNotManageDatabaseContainers(t *testing.T) {
	root := filepath.Clean("..")
	for _, file := range []string{"docker-compose.yml", "deploy/deploy-linux.sh"} {
		content, err := os.ReadFile(filepath.Join(root, filepath.FromSlash(file)))
		require.NoError(t, err)
		text := strings.ToLower(string(content))
		require.NotContains(t, text, "pull mysql")
		require.NotContains(t, text, "pull redis")
		require.NotContains(t, text, "8088")
	}
	readYAMLMap(t, filepath.Join(root, "deploy", "docker-compose.onepanel-network.yml"))
	dockerConfig := readYAMLMap(t, filepath.Join(root, "server-gin", "config.docker.yaml"))
	require.Empty(t, dockerConfig["redis"].(map[string]any)["addr"])

	entrypoint, err := os.ReadFile(filepath.Join(root, "deploy", "gin-vue-admin-entrypoint.sh"))
	require.NoError(t, err)
	require.Contains(t, string(entrypoint), ".redis-list[0].addr = strenv(CATLINK_REDIS_ADDR)")
	require.Contains(t, string(entrypoint), "chmod 600 \"$CONFIG\"")

	dockerIgnore, err := os.ReadFile(filepath.Join(root, ".dockerignore"))
	require.NoError(t, err)
	for _, excluded := range []string{"server-gin/config.yaml", "server-gin/local-data/", "server-gin/log/", "server-gin/uploads/"} {
		require.Contains(t, string(dockerIgnore), excluded)
	}

	dockerfile, err := os.ReadFile(filepath.Join(root, "Dockerfile"))
	require.NoError(t, err)
	require.Contains(t, string(dockerfile), "COPY deploy/gin-vue-admin-entrypoint.sh")
	require.Contains(t, string(dockerfile), "COPY deploy/nginx-catlink.conf")
	require.NotContains(t, string(dockerfile), "COPY --from=web-build /src/dist /app/admin-web/dist")
}

func readYAMLMap(t *testing.T, file string) map[string]any {
	t.Helper()
	content, err := os.ReadFile(file)
	require.NoError(t, err)
	var parsed map[string]any
	require.NoError(t, yaml.Unmarshal(content, &parsed))
	return parsed
}
