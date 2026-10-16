param([string]$OutputDirectory = "$(Split-Path $PSScriptRoot -Parent)\..\build")

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression

$project = Split-Path $PSScriptRoot -Parent
$output = [IO.Path]::GetFullPath($OutputDirectory)
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$stage = Join-Path $output ".catlink-admin-next-$stamp"
$archive = Join-Path $output "catlink-admin-next-$stamp.zip"

New-Item -ItemType Directory -Force $output, $stage | Out-Null
$stagedProject = $stage
foreach ($directory in @('backend', 'web')) {
    robocopy (Join-Path $project $directory) (Join-Path $stagedProject $directory) /E /XD node_modules dist test tests test-results playwright-report plop-templates temp .git .claude .github static\uploadfile /XF .env '.env.*' '*.exe' '*.test' '*_test.go' '*.spec.js' '*.spec.ts' '*.log' '*.db' '*.db-*' '*.db.*' '*.sqlite' '*.sqlite3' '*.bak' 'settings*.yml' 'playwright.config.ts' 'vitest.config.mjs' 'test-vue3.js' 'plopfile.js' | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "Failed to copy deployment source: robocopy exit $LASTEXITCODE" }
}
$legacyUploadDirectory = Join-Path $stagedProject 'backend/static/uploadfile'
if (Test-Path -LiteralPath $legacyUploadDirectory) {
    Remove-Item -LiteralPath $legacyUploadDirectory -Recurse -Force
}
Copy-Item -LiteralPath (Join-Path $project 'backend/config/settings.docker.yml') -Destination (Join-Path $stagedProject 'backend/config/settings.docker.yml')
foreach ($file in @('docker-compose.yml', 'install.sh', 'update.sh', 'env.example', 'README.md', 'UPDATE.md', 'CLEAN_DEPLOY_1PANEL.md', 'OPENLIST_STORAGE.md')) {
    Copy-Item -LiteralPath (Join-Path $project $file) -Destination (Join-Path $stagedProject $file)
}

$requiredFiles = @(
    'web/src/views/catlink/logs/index.vue',
    'backend/cmd/migrate/migration/version/1788600012000_catlink_navigation_cleanup.go',
    'install.sh',
    'update.sh',
    'UPDATE.md'
)
foreach ($relativePath in $requiredFiles) {
    if (-not (Test-Path (Join-Path $stagedProject $relativePath))) {
        throw "Deployment package is missing required file: $relativePath"
    }
}

$compression = [IO.Compression.CompressionLevel]::Optimal
$zip = [IO.Compression.ZipFile]::Open($archive, [IO.Compression.ZipArchiveMode]::Create)
try {
    Get-ChildItem -LiteralPath $stagedProject -Recurse -File | ForEach-Object {
        $relative = $_.FullName.Substring($stagedProject.Length).TrimStart('\', '/') -replace '\\', '/'
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, $relative, $compression) | Out-Null
    }
} finally {
    $zip.Dispose()
}
Remove-Item -LiteralPath $stage -Recurse -Force
$hash = (Get-FileHash -Algorithm SHA256 $archive).Hash.ToLowerInvariant()
"$archive`nSHA256 $hash"
