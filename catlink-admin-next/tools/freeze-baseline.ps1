param(
    [string]$Workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'
$output = Join-Path $PSScriptRoot '..\baseline'
New-Item -ItemType Directory -Force $output | Out-Null

$router = Join-Path $Workspace 'server-gin\router\catlink.go'
$routes = foreach ($line in Get-Content $router) {
    $match = [regex]::Match($line, '^\s*(v1|v1s|v1logout|admin)\.(GET|POST|PUT|PATCH|DELETE)\("([^"]+)"')
    if (!$match.Success) { continue }
    $scope = switch ($match.Groups[1].Value) {
        'v1' { 'app-public' }
        'v1s' { 'app-private' }
        'v1logout' { 'app-logout' }
        'admin' { 'admin' }
    }
    $prefix = if ($scope -eq 'admin') { '/api/v1/moderation' } else { '/api/v1' }
    [ordered]@{
        scope = $scope
        method = $match.Groups[2].Value
        path = $prefix + $match.Groups[3].Value
    }
}
$routes | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $output 'routes.json') -Encoding utf8

$sqlFiles = Get-ChildItem (Join-Path $Workspace 'sql-init\gin-vue-admin') -File -Filter '*.sql' | Sort-Object Name
$schema = [ordered]@{
    generatedAt = (Get-Date).ToString('o')
    migrationFiles = @($sqlFiles | ForEach-Object {
        [ordered]@{
            name = $_.Name
            sha256 = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    })
    catModels = @([regex]::Matches(
        (Get-Content (Join-Path $Workspace 'server-gin\model\catlink\catlink.go') -Raw),
        '(?m)^type (Cat[A-Za-z0-9_]+) struct'
    ) | ForEach-Object { $_.Groups[1].Value })
}
$schema | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $output 'schema-source.json') -Encoding utf8

$source = [ordered]@{
    generatedAt = (Get-Date).ToString('o')
    workspaceCommit = (git -C $Workspace rev-parse HEAD)
    workspaceDirty = [bool](git -C $Workspace status --porcelain)
    goAdminCommit = (git -C (Join-Path $Workspace '.tmp\go-admin-reference') rev-parse HEAD)
    goAdminUiCommit = (git -C (Join-Path $Workspace '.tmp\go-admin-ui-reference') rev-parse HEAD)
    routeCount = @($routes).Count
    routeScopes = [ordered]@{
        appPublic = @($routes | Where-Object scope -eq 'app-public').Count
        appPrivate = @($routes | Where-Object scope -eq 'app-private').Count
        appLogout = @($routes | Where-Object scope -eq 'app-logout').Count
        admin = @($routes | Where-Object scope -eq 'admin').Count
    }
}
$source | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $output 'source.json') -Encoding utf8

if ($source.routeCount -ne 134) {
    throw "CatLink route baseline changed: expected 134, got $($source.routeCount)"
}

Write-Output "Baseline frozen: $output"
