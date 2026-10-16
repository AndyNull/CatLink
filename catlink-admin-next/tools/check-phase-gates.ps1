param([string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path)

$ErrorActionPreference = 'Stop'
$baseline = Get-Content (Join-Path $Root 'baseline/source.json') -Raw | ConvertFrom-Json
if ($baseline.routeCount -ne 134) { throw "Route baseline must contain 134 routes" }
& (Join-Path $Root 'tools/check-route-migration.ps1') -Root $Root -RequireComplete
& (Join-Path $Root 'tools/check-replacement-semantics.ps1') -Root $Root

$observationDir = Join-Path $Root 'observations'
$rows = @(Get-ChildItem $observationDir -File -Filter '*.json' -ErrorAction SilentlyContinue |
    Sort-Object Name |
    ForEach-Object { Get-Content $_.FullName -Raw | ConvertFrom-Json })
if ($rows.Count -lt 14) { throw "Phase 7 requires 14 observation days; found $($rows.Count)" }

$last14 = @($rows | Select-Object -Last 14)
$dates = @($last14 | ForEach-Object { [datetime]$_.date } | Sort-Object)
for ($i = 1; $i -lt $dates.Count; $i++) {
    if (($dates[$i] - $dates[$i - 1]).Days -ne 1) { throw 'Observation dates are not consecutive' }
}
foreach ($row in $last14) {
    if ($row.routeDrift -ne 0 -or $row.authErrors -ne 0 -or $row.dataLoss -ne 0 -or $row.duplicateBroadcast -ne 0) {
        throw "Observation gate failed: $($row.date)"
    }
    if (-not $row.rollbackReady) { throw "Rollback is not ready: $($row.date)" }
}

$last7 = @($last14 | Select-Object -Last 7)
foreach ($row in $last7) {
    if ($row.readMismatch -ne 0 -or [double]$row.p95Ratio -gt 1.2) { throw "Phase 3 shadow-read gate failed: $($row.date)" }
}

Write-Output 'Phase 3/7 production gates passed. Legacy archival may proceed after a final backup.'
