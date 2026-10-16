param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [switch]$RequireComplete
)

$ErrorActionPreference = 'Stop'
$baseline = Get-Content (Join-Path $Root 'baseline/routes.json') -Raw | ConvertFrom-Json
$native = Get-Content (Join-Path $Root 'baseline/native-routes.json') -Raw | ConvertFrom-Json
$replaced = Get-Content (Join-Path $Root 'baseline/replaced-routes.json') -Raw | ConvertFrom-Json
$nativeKeys = @{}
foreach ($route in $native) { $nativeKeys["$($route.method) $($route.path)"] = $true }
$sourceKeys = @{}
$source = Get-Content (Join-Path $Root 'backend/cmd/api/app_native.go') -Raw
foreach ($match in [regex]::Matches($source, 'engine\.(GET|POST|PUT|PATCH|DELETE)\("([^"]+)",\s*(?:catapi\.AppAuth,\s*)?(?:catapi\.AppLogoutAuth,\s*)?catapi\.([A-Za-z0-9_]+)')) {
    $sourceKeys["$($match.Groups[1].Value) $($match.Groups[2].Value)"] = $true
    $handler = $match.Groups[3].Value
    $found = rg -l --glob '*.go' "func\s+$handler\s*\(" (Join-Path $Root 'backend/app/catlink/apis')
    if (-not $found) { throw "Native route handler is not implemented: $handler" }
}
foreach ($key in $nativeKeys.Keys) {
    if (-not $sourceKeys.ContainsKey($key)) { throw "Native route is not registered in app_native.go: $key" }
}

$rows = foreach ($route in $baseline) {
    $key = "$($route.method) $($route.path)"
    [pscustomobject]@{
        scope = $route.scope
        method = $route.method
        path = $route.path
        status = if ($nativeKeys.ContainsKey($key)) {
            'native'
        } elseif (@($replaced | Where-Object { $route.scope -eq $_.scope -and $route.path.StartsWith($_.pathPrefix) }).Count -gt 0) {
            'replaced'
        } else {
            'missing'
        }
    }
}
$jsonPath = Join-Path $Root 'baseline/migration-matrix.json'
$rows | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 $jsonPath

$groups = $rows | Group-Object status | ForEach-Object { [pscustomobject]@{ status = $_.Name; count = $_.Count } }
$nativeCount = @($rows | Where-Object status -eq 'native').Count
$replacedCount = @($rows | Where-Object status -eq 'replaced').Count
$missingCount = @($rows | Where-Object status -eq 'missing').Count
Write-Output "routes=$($rows.Count) native=$nativeCount replaced=$replacedCount missing=$missingCount"
if ($RequireComplete -and $missingCount -ne 0) {
    throw "App API migration is incomplete: $missingCount routes are missing"
}
