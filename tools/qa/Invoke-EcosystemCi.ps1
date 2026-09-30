#requires -Version 7.2
[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$JavaHome
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'EcosystemCi.psm1') -Force
$root = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$matrix = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'ecosystem-matrix.json') | ConvertFrom-Json
$manifest = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'ecosystem-public-artifacts.json') | ConvertFrom-Json
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $output) { throw 'CI evidence directory must be new; never replace earlier evidence.' }
if ($env:CI -ne 'true') { throw 'This full coordinator is intended for CI; set CI=true explicitly for a local rehearsal.' }
[void][IO.Directory]::CreateDirectory($output)
$shell = (Get-Process -Id $PID).Path
function Write-Evidence([string]$Name, $Value) {
    $stream = [IO.File]::Open((Join-Path $output $Name), 'CreateNew', 'Write')
    $writer = [IO.StreamWriter]::new($stream, [Text.UTF8Encoding]::new($false))
    try { $writer.Write(($Value | ConvertTo-Json -Depth 30)) } finally { $writer.Dispose() }
}
function Run-Gate([string]$Name, [string[]]$Arguments) {
    $scriptPath = Join-Path $root ('tools/qa/' + $Name)
    & $shell -NoLogo -NoProfile -NonInteractive -File $scriptPath @Arguments 2>&1 |
        Tee-Object -FilePath (Join-Path $output ($Name + '.log')) | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "$Name failed (exit $LASTEXITCODE); no ecosystem completion receipt will be written." }
}
try {
    Write-Evidence 'invocation.json' ([ordered]@{
        schemaVersion=1; startedUtc=[DateTime]::UtcNow.ToString('o'); repository=$root
        workflowRun=$env:FORGEJO_RUN_ID; triggeringRef=$env:FORGEJO_REF; triggeringCommit=$env:FORGEJO_SHA
        validationScope='Current official release-ref snapshot resolved by the source gate; the event triggering commit is contextual, not necessarily a tested source commit.'
        publishingEnabled=$false; credentialsRequired=$false
    })
    Run-Gate 'Test-EcosystemParityUnit.ps1' @()
    Run-Gate 'Test-EcosystemSourcesUnit.ps1' @()
    Run-Gate 'Test-EcosystemPublicArtifactsUnit.ps1' @()
    Run-Gate 'Test-EcosystemCiUnit.ps1' @()
    $sourceArgs = @('-OutputDirectory', (Join-Path $output 'sources'), '-Build')
    if ($JavaHome) { $sourceArgs += @('-JavaHome', $JavaHome) }
    Run-Gate 'Test-EcosystemSources.ps1' $sourceArgs
    $source = Get-Content -LiteralPath (Join-Path $output 'sources/source-result.json') -Raw | ConvertFrom-Json
    Assert-EcosystemCiSourceReceipt -Source $source -Matrix $matrix
    Run-Gate 'Test-EcosystemPublicRelease.ps1' @('-RepositoryRoot', $root, '-OutputDirectory', (Join-Path $output 'public'))
    $public = Get-Content -LiteralPath (Join-Path $output 'public/public-result.json') -Raw | ConvertFrom-Json
    $artifactLinks = @(Get-EcosystemCiArtifactLinks -Source $source -Public $public -Matrix $matrix -Manifest $manifest)
    if ($artifactLinks.Count -ne 6) { throw 'All six source-owned artifacts must exactly match accepted public bytes.' }
    Write-Evidence 'ci-result.json' ([ordered]@{
        schemaVersion=1; status='SOURCE_AND_PUBLIC_GATES_PASSED'; completedUtc=[DateTime]::UtcNow.ToString('o')
        sourceReceipt='sources/source-result.json'; sourceReceiptSha256=(Get-FileHash -LiteralPath (Join-Path $output 'sources/source-result.json') -Algorithm SHA256).Hash
        publicReceipt='public/public-result.json'; publicReceiptSha256=(Get-FileHash -LiteralPath (Join-Path $output 'public/public-result.json') -Algorithm SHA256).Hash
        sourcePublicArtifactLinks=$artifactLinks
        validationScope='Current official release-ref snapshot; consult the source receipt for the exact three tested commits. Does not certify a different event-triggering commit.'
        publishingPerformed=$false
        limits='Build, committed-policy and public verification, with exact byte equality between six source-built core/Momentum JARs and accepted anonymous downloads. RIA is verified only as public artifacts. No new human, controller, multiplayer, performance or RIA source-build acceptance.'
    })
    Write-Host 'All three snapshotted source lanes and all nine public artifacts verified; six source-owned JARs match public bytes exactly. No publication performed.'
} catch {
    Write-Evidence 'failure.json' @{failedUtc=[DateTime]::UtcNow.ToString('o'); status='NOT_COMPLETE'; error=$_.Exception.Message}
    throw
}
