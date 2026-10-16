param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [string]$Database = (Join-Path $Root 'backend/catlink-admin-debug-v20.db')
)

$ErrorActionPreference = 'Stop'
$icons = @{}
Get-ChildItem (Join-Path $Root 'web/src/icons/svg') -File -Filter '*.svg' | ForEach-Object {
    $icons[$_.BaseName] = $true
}
$sqlite = (Get-Command sqlite3 -ErrorAction Stop).Source
$rows = & $sqlite -separator '|' $Database "SELECT menu_id, icon FROM ga_menu WHERE menu_type IN ('M','C') AND visible='0' AND deleted_at=0"
$missing = $rows | ForEach-Object {
    $id, $icon = $_.Split('|', 2)
    if (-not $icon -or -not $icons.ContainsKey($icon)) { "menu $id -> '$icon'" }
}
if ($missing) { throw "Menu icons are missing: $($missing -join ', ')" }
Write-Output "menu icons: all visible page and directory icons exist"
