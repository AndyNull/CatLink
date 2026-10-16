param([string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path)
$ErrorActionPreference = 'Stop'
$matrixRows = Get-Content (Join-Path $Root 'baseline/migration-matrix.json') -Raw | ConvertFrom-Json
$matrix = @($matrixRows | Where-Object status -eq 'replaced')
$mapping = Get-Content (Join-Path $Root 'baseline/replacement-route-map.json') -Raw | ConvertFrom-Json
$expected = @{}
foreach ($row in $matrix) { $expected["$($row.method) $($row.path)"] = $true }
$actual = @{}
$registered = @{}
$registeredRoutes = @()
$routeSources = @(
    @{ File = 'backend/app/catlink/router/routes.go'; Prefix = '/admin-api/catlink' },
    @{ File = 'backend/app/admin/router/bricks.go'; Prefix = '/admin-api/bricks' }
)
foreach ($routeSource in $routeSources) {
    $source = Get-Content (Join-Path $Root $routeSource.File) -Raw
    foreach ($match in [regex]::Matches($source, 'group\.(GET|POST|PUT|PATCH|DELETE)\("([^"]*)"')) {
        $method = $match.Groups[1].Value
        $path = "$($routeSource.Prefix)$($match.Groups[2].Value)"
        $registered["$method $path"] = $true
        $registeredRoutes += [pscustomobject]@{ Method = $method; Path = $path }
    }
}
foreach ($row in $mapping) {
    $key = "$($row.method) $($row.path)"
    if ($actual.ContainsKey($key)) { throw "Duplicate replacement mapping: $key" }
    if (-not $expected.ContainsKey($key)) { throw "Replacement mapping is outside baseline: $key" }
    if (-not $row.targetPath.StartsWith('/admin-api/') -or -not $row.targetMethod) { throw "Invalid replacement target: $key" }
    $targetKey = "$($row.targetMethod) $($row.targetPath)"
    $targetRegistered = $registered.ContainsKey($targetKey)
    if (-not $targetRegistered) {
        foreach ($route in $registeredRoutes | Where-Object Method -eq $row.targetMethod) {
            $pattern = '^' + [regex]::Replace([regex]::Escape($route.Path), ':[A-Za-z0-9_]+', '[^/]+') + '$'
            if ($row.targetPath -match $pattern) { $targetRegistered = $true; break }
        }
    }
    if (-not $targetRegistered) { throw "Replacement target is not registered: $key -> $targetKey" }
    $actual[$key] = $true
}
foreach ($key in $expected.Keys) { if (-not $actual.ContainsKey($key)) { throw "Missing replacement mapping: $key" } }
Write-Output "replacement semantics: $($mapping.Count)/$($matrix.Count) mapped"
