param([string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path)

$compose = Get-Content (Join-Path $Root 'docker-compose.yml') -Raw
$entrypoint = Get-Content (Join-Path $Root 'backend/docker-entrypoint.sh') -Raw
$installer = Get-Content (Join-Path $Root 'install.sh') -Raw
$dockerfile = Get-Content (Join-Path $Root 'backend/Dockerfile') -Raw
$examples = (Get-Content (Join-Path $Root '.env.example') -Raw) + (Get-Content (Join-Path $Root 'env.example') -Raw)

if (($compose + $entrypoint + $examples).Contains('CATLINK_BOOTSTRAP_ADMIN_PASSWORD')) {
    throw 'Admin password must not enter Compose, backend runtime environment, or the .env template'
}
if (-not $installer.Contains('backend bootstrap-admin --username admin --allow-create --reset-existing')) {
    throw 'Install script does not initialize the default admin account'
}
if (-not $installer.Contains('-e CATLINK_BOOTSTRAP_ADMIN_PASSWORD')) {
    throw 'Install script does not pass the admin password through one-time environment input'
}
if (-not $compose.Contains('pull_policy: never')) {
    throw 'Worker may still pull a remote backend image unexpectedly'
}
if (-not $dockerfile.Contains('COPY config/db-begin-mysql.sql config/db.sql config/db-end-mysql.sql /app/config/')) {
    throw 'Backend image is missing MySQL initialization SQL'
}
Write-Output 'install contract: database admin bootstrap passed'
