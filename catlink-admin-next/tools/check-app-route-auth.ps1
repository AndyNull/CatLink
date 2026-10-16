param([string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path)
$ErrorActionPreference = 'Stop'
$routes = @(Get-Content (Join-Path $Root 'baseline/routes.json') -Raw | ConvertFrom-Json)
$source = Get-Content (Join-Path $Root 'backend/cmd/api/app_native.go')
$failed = @()
foreach ($route in $routes | Where-Object { $_.scope -in @('app-private', 'app-authenticated', 'app-logout') }) {
    $escaped = [regex]::Escape($route.path)
    $line = @($source | Where-Object { $_ -match ('engine\.' + $route.method + '\("' + $escaped + '"') })
    if ($line.Count -ne 1) { $failed += "$($route.method) $($route.path): route missing"; continue }
    $required = if ($route.scope -eq 'app-logout') { 'catapi.AppLogoutAuth' } else { 'catapi.AppAuth' }
    if ($line[0] -notmatch [regex]::Escape($required)) { $failed += "$($route.method) $($route.path): $required missing" }
}
if ($failed.Count) { throw ($failed -join [Environment]::NewLine) }
Write-Output 'app private routes: auth middleware complete'
