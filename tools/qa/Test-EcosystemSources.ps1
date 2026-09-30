#requires -Version 7.2
[CmdletBinding()]
param(
    [string] $RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path,
    [Parameter(Mandatory)] [string] $OutputDirectory,
    [switch] $Build,
    [string] $JavaHome,
    [string] $LocalTestRepository
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'EcosystemSources.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'EcosystemParity.psm1') -Force
$controller = (Resolve-Path -LiteralPath $RepositoryRoot).Path
if (-not $LocalTestRepository -and $controller -cne (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path) {
    throw 'The official controller must be the checkout containing this script.'
}
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'ecosystem-sources.json') -Raw | ConvertFrom-Json
$matrix = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'ecosystem-matrix.json') -Raw | ConvertFrom-Json
Assert-EcosystemMatrix $matrix
Assert-EcosystemSourcesManifest $manifest $matrix
if ($Build -and $env:CI -ne 'true' -and $env:GITHUB_ACTIONS -ne 'true') { throw '-Build requires an explicit CI environment.' }
if ($LocalTestRepository -and $Build) { throw 'Local fixture repositories are source-only; executing their build scripts is forbidden.' }
$remote = $manifest.repository
if ($LocalTestRepository) {
    $remote = (Resolve-Path -LiteralPath $LocalTestRepository).Path
    if (-not [IO.Path]::IsPathFullyQualified($remote) -or -not (Test-Path -LiteralPath $remote -PathType Container)) {
        throw 'LocalTestRepository must resolve to an existing local directory.'
    }
}
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $output) { throw 'OutputDirectory must be fresh; historical evidence is never replaced.' }
if ($output.StartsWith($controller.TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'OutputDirectory must be outside the controller checkout.'
}
[IO.Directory]::CreateDirectory($output) | Out-Null
$receipt = [ordered]@{
    schemaVersion=1; startedAtUtc=[DateTime]::UtcNow.ToString('o'); completedAtUtc=$null
    mode=$(if ($Build) { 'source-and-build' } else { 'source-only' })
    fixtureMode=[bool]$LocalTestRepository; repository=$remote; controllerCommit=$null
    sourceValidated=$false; allBuildsPassed=$false; publicVerified=$false; releaseApproved=$false
    status='FAILED'; targets=@(); sharedPolicy=$null; error=$null
    limits='Source/build evidence only. No publication, RIA source build, runtime, multiplayer, controller or manual acceptance. Public artifacts require a separate gate.'
}
$rows = [Collections.Generic.List[object]]::new()
try {
    $controllerState = Get-EcosystemCheckoutState -Root $controller
    Assert-EcosystemCheckoutState $controllerState $controllerState.commit
    $receipt.controllerCommit = $controllerState.commit
    $policy = Get-EcosystemPolicyIdentity -Root $controller -Files $manifest.sharedFiles
    $receipt.sharedPolicy = $policy
    # Resolve ALL moving branch names in one anonymous request before any build begins.
    $refArguments = @('ls-remote','--refs',$remote) + @($manifest.targets.ref)
    $remoteLines = (Invoke-EcosystemGit -Root $output -Arguments $refArguments -AllowFileTransport:([bool]$LocalTestRepository)) -split '\r?\n'
    foreach ($source in $manifest.targets) {
        $matchesForRef = @($remoteLines | Where-Object { $_ -cmatch ('^[0-9a-f]{40}\t' + [regex]::Escape($source.ref) + '$') })
        if ($matchesForRef.Count -ne 1) { throw "Missing or ambiguous remote ref: $($source.ref)" }
        $rows.Add([pscustomobject]@{
            minecraft=$source.minecraft; ref=$source.ref; commit=$matchesForRef[0].Split("`t")[0]
            momentumAutomobilityMinVersion=$source.momentumAutomobilityMinVersion
            checkout=(Join-Path $output $source.minecraft); sourceValidated=$false
            buildAttempted=$false; buildPassed=$false; buildExitCode=$null; artifacts=@(); before=$null; after=$null
        })
    }
    Write-EcosystemNewText -Path (Join-Path $output 'source-snapshot.json') -Text (($rows.ToArray() | ConvertTo-Json -Depth 10))
    foreach ($row in $rows) {
        Write-Host "Preparing $($row.minecraft) at $($row.commit)"
        # A new independent clone is never a worktree or a mutation of a user's existing checkout.
        $branch = $row.ref.Substring('refs/heads/'.Length)
        Invoke-EcosystemGit -Root $output -Arguments @('clone','--no-checkout','--no-tags','--single-branch','--depth','1','--no-local','--branch',$branch,'--',$remote,$row.checkout) -AllowFileTransport:([bool]$LocalTestRepository) | Out-Null
        Invoke-EcosystemGit -Root $row.checkout -Arguments @('fetch','--no-tags','--depth','1','origin',$row.commit) -AllowFileTransport:([bool]$LocalTestRepository) | Out-Null
        Invoke-EcosystemGit -Root $row.checkout -Arguments @('checkout','--detach',$row.commit) | Out-Null
        $row.before = Get-EcosystemCheckoutState -Root $row.checkout
        Assert-EcosystemCheckoutState $row.before $row.commit
        Assert-EcosystemPolicyIdentity $policy (Get-EcosystemPolicyIdentity -Root $row.checkout -Files $manifest.sharedFiles)
        $target = @($matrix.targets | Where-Object minecraft -CEQ $row.minecraft)[0]
        Assert-EcosystemSourceProperties -Path (Join-Path $row.checkout 'gradle.properties') -Target $target `
            -ExpectedMomentumCoreMinimum $row.momentumAutomobilityMinVersion
        $row.sourceValidated = $true
    }
    $receipt.sourceValidated = $true
    if ($Build) {
        foreach ($row in $rows) {
            Assert-EcosystemCheckoutState (Get-EcosystemCheckoutState $row.checkout) $row.commit
            $row.buildAttempted = $true
            Write-Host "Building $($row.minecraft); immutable log will be $($row.minecraft)-build.log"
            $arguments = @('-NoProfile','-NonInteractive','-File',(Join-Path $row.checkout 'tools/qa/Test-Automobility.ps1'),
                '-RepositoryRoot',$row.checkout,'-CiBuildVerification','-Rebuild')
            if ($JavaHome) { $arguments += @('-JavaHome',$JavaHome) }
            $child = Invoke-EcosystemProcess -Executable (Join-Path $PSHOME $(if ($IsWindows) { 'pwsh.exe' } else { 'pwsh' })) `
                -Arguments $arguments -WorkingDirectory $row.checkout -Environment @{CI='true'} `
                -LogPath (Join-Path $output "$($row.minecraft)-build.log")
            $row.buildExitCode = $child.exitCode
            $row.after = Get-EcosystemCheckoutState $row.checkout
            Assert-EcosystemCheckoutState $row.after $row.commit
            Assert-EcosystemBuildResult $child.exitCode
            $target = @($matrix.targets | Where-Object minecraft -CEQ $row.minecraft)[0]
            $artifactPaths = @(
                "fabric/build/libs/automobility-$($target.automobility)+$($row.minecraft)-fabric.jar",
                "momentum/build/libs/momentum-$($target.momentum)+$($row.minecraft)-fabric.jar"
            )
            $row.artifacts = @(foreach ($relative in $artifactPaths) {
                $artifact = Get-Item -LiteralPath (Join-Path $row.checkout $relative)
                [pscustomobject]@{ path=$relative; size=$artifact.Length
                    sha256=(Get-FileHash -LiteralPath $artifact.FullName -Algorithm SHA256).Hash
                    sha512=(Get-FileHash -LiteralPath $artifact.FullName -Algorithm SHA512).Hash }
            })
            $row.buildPassed = $true
        }
        $receipt.allBuildsPassed = $true
    }
    foreach ($row in $rows) {
        $row.after = Get-EcosystemCheckoutState $row.checkout
        Assert-EcosystemCheckoutState $row.after $row.commit
    }
    Assert-EcosystemCheckoutState (Get-EcosystemCheckoutState $controller) $receipt.controllerCommit
    $receipt.status = if ($Build) { 'SOURCE_BUILDS_PASSED' } else { 'SOURCE_VALIDATION_ONLY' }
} catch {
    $receipt.error = $_.Exception.Message
    throw
} finally {
    $receipt.targets = $rows.ToArray()
    $receipt.completedAtUtc = [DateTime]::UtcNow.ToString('o')
    Write-EcosystemNewText -Path (Join-Path $output 'source-result.json') -Text ($receipt | ConvertTo-Json -Depth 20)
}
Write-Host "$($receipt.status): all three pinned source lanes checked. Public/release/runtime acceptance remains unproven."
