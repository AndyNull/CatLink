param([string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path)

$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $Root '..')).Path
$clientRoot = Join-Path $workspace 'app/src/main/java/com/catlink/app'
$serverSource = Get-Content (Join-Path $Root 'backend/cmd/api/app_native.go') -Raw
$registered = [regex]::Matches($serverSource, 'engine\.(GET|POST|PUT|PATCH|DELETE)\("([^"]+)"') |
    ForEach-Object { [pscustomobject]@{ Method = $_.Groups[1].Value; Path = $_.Groups[2].Value } }
$methodMap = @{
    get = 'GET'; getLarge = 'GET'; streamSse = 'GET'; post = 'POST'; postForUnit = 'POST'; postFile = 'POST'
    put = 'PUT'; putForUnit = 'PUT'; patch = 'PATCH'; delete = 'DELETE'
}
$calls = Get-ChildItem $clientRoot -Recurse -File -Filter '*.kt' | ForEach-Object {
    $source = Get-Content $_.FullName -Raw
    [regex]::Matches($source, 'api\.(get|getLarge|streamSse|post|postForUnit|postFile|put|putForUnit|patch|delete)\(\s*(?:path\s*=\s*)?"([^"]+)"') | ForEach-Object {
        $path = $_.Groups[2].Value.Split('?')[0]
        if (-not $path.StartsWith('/')) { return }
        [pscustomobject]@{ Method = $methodMap[$_.Groups[1].Value]; Path = '/api/v1' + $path; File = $_.Path }
    }
} | Sort-Object Method, Path -Unique
$missing = foreach ($call in $calls) {
    $clientPattern = [regex]::Escape($call.Path)
    $clientPattern = [regex]::Replace($clientPattern, '\\\$\{[^}]+\}', '[^/]+')
    $clientPattern = [regex]::Replace($clientPattern, '\\\$[A-Za-z_][A-Za-z0-9_]*', '[^/]+')
    $matched = $registered | Where-Object {
        if ($_.Method -ne $call.Method) { return $false }
        $serverPattern = '^' + [regex]::Replace([regex]::Escape($_.Path), ':[A-Za-z0-9_]+', '[^/]+') + '$'
        $call.Path -match $serverPattern -or $_.Path -match ('^' + $clientPattern + '$')
    }
    if (-not $matched) { "$($call.Method) $($call.Path)" }
}
if ($missing) { throw "App calls missing server routes: $($missing -join ', ')" }
Write-Output "app client contract: $($calls.Count) call sites are registered"
