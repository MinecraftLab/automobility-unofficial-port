#requires -Version 7.2
[CmdletBinding()]
param(
    [string] $RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path,
    [switch] $LocalOnly,
    [switch] $ReportOnly,
    [ValidateSet('26.1.2','26.2','26.3')] [string] $PendingMinecraft,
    [string] $OutputPath
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'EcosystemParity.psm1') -Force
$matrix = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'ecosystem-matrix.json') | ConvertFrom-Json
Assert-EcosystemMatrix $matrix
$properties = @{}
foreach ($line in Get-Content -LiteralPath (Join-Path $RepositoryRoot 'gradle.properties')) {
    if ($line -match '^\s*([^#\s=]+)\s*=\s*(.*?)\s*$') { $properties[$Matches[1]] = $Matches[2] }
}
$lane = @($matrix.targets | Where-Object minecraft -CEQ $properties.minecraft_version)
if ($lane.Count -ne 1 -or $properties.mod_version -cne $lane[0].automobility -or
    $properties.momentum_version -cne $lane[0].momentum) {
    throw 'Local release versions differ from the shared matrix. Account for all supported targets before releasing.'
}
if ($PendingMinecraft -and $PendingMinecraft -cne $properties.minecraft_version) {
    throw 'Only this checkout target can be marked pending. Another Minecraft lane must not be silently waived.'
}
if ($LocalOnly) {
    if ($ReportOnly -or $PendingMinecraft -or $OutputPath) { throw 'LocalOnly cannot be combined with public audit options.' }
    Write-Host "Local ecosystem policy PASS: $($properties.minecraft_version) / $($matrix.milestone)"
    return
}
$snapshots = @{}
foreach ($project in @('automobility','momentum','riautomobility')) {
    $id = $matrix.projects.$project
    $snapshots[$project] = @(Invoke-RestMethod -Uri ("https://api.modrinth.com/v2/project/$id/version?parity=" + [DateTime]::UtcNow.Ticks) `
        -Headers @{'User-Agent'='Offchunk-Ecosystem-Parity/1.0'} -TimeoutSec 45 -MaximumRedirection 0 | ForEach-Object { $_ })
}
$rows = @(Get-EcosystemParity -Matrix $matrix -Snapshots $snapshots)
$gaps = @($rows | Where-Object { !$_.aligned })
$blockers = @($gaps | Where-Object { !$PendingMinecraft -or $_.minecraft -cne $PendingMinecraft })
$report = [ordered]@{
    generatedAtUtc = [DateTime]::UtcNow.ToString('o'); milestone = $matrix.milestone
    matrixSha256 = (Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'ecosystem-matrix.json') -Algorithm SHA256).Hash
    publicAligned = $gaps.Count -eq 0; pendingMinecraft = $PendingMinecraft
    preflightAllowed = $blockers.Count -eq 0; rows = $rows; snapshots = $snapshots
    limits = 'Metadata/dependency alignment only. Does not prove downloaded bytes, API/ABI compatibility, runtime or player acceptance. Pending-target preflight is never a completed-publication result.'
}
if ($OutputPath) {
    # Do not replace historical audit evidence.
    $stream = [IO.File]::Open([IO.Path]::GetFullPath($OutputPath), [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    $writer = [IO.StreamWriter]::new($stream, [Text.UTF8Encoding]::new($false))
    try { $writer.Write(($report | ConvertTo-Json -Depth 40)) } finally { $writer.Dispose() }
}
$rows | Select-Object minecraft,project,actual,expected,@{N='issues';E={$_.issues -join ','}} | Format-Table -AutoSize | Out-Host
if ($PendingMinecraft -and $gaps.Count) { Write-Warning "Public ecosystem has unresolved rows. Only this checkout's $PendingMinecraft rows may be pending; other targets still block. Run again without PendingMinecraft after publication and dependency updates." }
if ($blockers.Count -and !$ReportOnly) { throw "Ecosystem parity FAILED: $($blockers.Count) unresolved public row(s)." }
if ($gaps.Count) { Write-Warning 'Ecosystem NOT ALIGNED; no publication or metadata mutation was performed.' }
else { Write-Host 'Public ecosystem metadata/dependency parity PASS (not a runtime acceptance).' }
