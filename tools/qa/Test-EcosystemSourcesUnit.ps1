#requires -Version 7.2
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'EcosystemSources.psm1') -Force
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'ecosystem-sources.json') -Raw | ConvertFrom-Json
$matrix = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'ecosystem-matrix.json') -Raw | ConvertFrom-Json
$script:passed = 0
function Check([bool] $Condition, [string] $Name) {
    if (-not $Condition) { throw "FAIL: $Name" }
    $script:passed++
    Write-Host "PASS: $Name"
}
function Check-Throws([scriptblock] $Action, [string] $Name) {
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    Check $rejected $Name
}
function Copy-Value($Value) { return ($Value | ConvertTo-Json -Depth 20 | ConvertFrom-Json) }
function Write-Fixture([string] $Path, [string] $Text) {
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($Path)) | Out-Null
    [IO.File]::WriteAllText($Path, $Text, [Text.UTF8Encoding]::new($false))
}
Assert-EcosystemSourcesManifest $manifest $matrix
Check $true 'Exactly three official targets and complete shared-policy coverage accepted'
$bad = Copy-Value $manifest
$bad.repository = 'https://example.com/untrusted.git'
Check-Throws { Assert-EcosystemSourcesManifest $bad $matrix } 'Non-official remote rejected'
$bad = Copy-Value $manifest
$bad.targets[2].ref = 'refs/heads/main'
Check-Throws { Assert-EcosystemSourcesManifest $bad $matrix } 'Wrong branch mapping rejected'
$bad = Copy-Value $manifest
$bad.targets = @($bad.targets[0],$bad.targets[1])
Check-Throws { Assert-EcosystemSourcesManifest $bad $matrix } 'Missing Minecraft target rejected'
$bad = Copy-Value $manifest
$bad.targets += $bad.targets[0]
Check-Throws { Assert-EcosystemSourcesManifest $bad $matrix } 'Duplicate Minecraft target rejected'
$bad = Copy-Value $manifest
$bad.sharedFiles = @($bad.sharedFiles | Where-Object { $_ -ne '.forgejo/workflows/ecosystem.yml' })
Check-Throws { Assert-EcosystemSourcesManifest $bad $matrix } 'Omitted workflow policy rejected'
$bad = Copy-Value $manifest
$bad.sharedFiles += $bad.sharedFiles[0]
Check-Throws { Assert-EcosystemSourcesManifest $bad $matrix } 'Duplicate policy path rejected'
$bad = Copy-Value $manifest
$bad.sharedFiles[0] = '../outside.json'
Check-Throws { Assert-EcosystemSourcesManifest $bad $matrix } 'Policy traversal path rejected'
$badMatrix = Copy-Value $matrix
$badMatrix.targets = @($badMatrix.targets[0],$badMatrix.targets[1])
Check-Throws { Assert-EcosystemSourcesManifest $manifest $badMatrix } 'Release matrix cannot omit a source target'
$policy = [ordered]@{'tools/qa/one.ps1'=('a'*40); 'tools/qa/two.ps1'=('b'*40)}
Assert-EcosystemPolicyIdentity $policy ([ordered]@{'tools/qa/one.ps1'=('a'*40); 'tools/qa/two.ps1'=('b'*40)})
Check $true 'Identical committed policy bytes accepted'
Check-Throws { Assert-EcosystemPolicyIdentity $policy ([ordered]@{'tools/qa/one.ps1'=('a'*40); 'tools/qa/two.ps1'=('c'*40)}) } 'Shared policy drift rejected'
Check-Throws { Assert-EcosystemPolicyIdentity $policy ([ordered]@{'tools/qa/one.ps1'=('a'*40)}) } 'Missing policy identity rejected'
$goodState = [pscustomobject]@{commit=('a'*40); clean=$true; status=''}
Assert-EcosystemCheckoutState $goodState ('a'*40)
Check $true 'Pinned clean checkout accepted'
Check-Throws { Assert-EcosystemCheckoutState $goodState ('b'*40) } 'Moved commit rejected even if clean'
Check-Throws { Assert-EcosystemCheckoutState ([pscustomobject]@{commit=('a'*40); clean=$false; status=' M source.java'}) ('a'*40) } 'Dirty source rejected even when HEAD is unchanged'
Assert-EcosystemBuildResult 0
Check $true 'Zero child exit accepted'
Check-Throws { Assert-EcosystemBuildResult 1 } 'Build failure cannot produce success'

# Generated local Git fixtures only; no network, user checkout mutation or Gradle execution.
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('automobility-source-unit-' + [Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($fixtureRoot) | Out-Null
$properties = Join-Path $fixtureRoot 'gradle.properties'
$target = $matrix.targets[2]
$minimum = $manifest.targets[2].momentumAutomobilityMinVersion
$validProperties = "minecraft_version=$($target.minecraft)`nmod_version=$($target.automobility)`nmomentum_version=$($target.momentum)`nmomentum_automobility_min_version=$minimum`n"
Write-Fixture $properties $validProperties
Assert-EcosystemSourceProperties $properties $target $minimum
Check $true 'Matching source Gradle properties accepted'
Write-Fixture $properties ($validProperties.Replace('minecraft_version=26.3','minecraft_version=26.2'))
Check-Throws { Assert-EcosystemSourceProperties $properties $target $minimum } 'Wrong local Minecraft property rejected'
Write-Fixture $properties ($validProperties.Replace("momentum_version=$($target.momentum)",'momentum_version=0.1.11-unofficial.10'))
Check-Throws { Assert-EcosystemSourceProperties $properties $target $minimum } 'Wrong local Momentum property rejected'
Write-Fixture $properties ($validProperties + "mod_version=$($target.automobility)`n")
Check-Throws { Assert-EcosystemSourceProperties $properties $target $minimum } 'Ambiguous duplicate Gradle property rejected'
foreach ($olderIndex in @(0,1)) {
    $olderTarget = $matrix.targets[$olderIndex]
    $olderMinimum = $manifest.targets[$olderIndex].momentumAutomobilityMinVersion
    Check ($olderMinimum -ceq '0.5.0-unofficial.35') "Reviewed existing Momentum minimum remains .35 on $($olderTarget.minecraft)"
    $olderProperties = "minecraft_version=$($olderTarget.minecraft)`nmod_version=$($olderTarget.automobility)`nmomentum_version=$($olderTarget.momentum)`nmomentum_automobility_min_version=$olderMinimum`n"
    Write-Fixture $properties $olderProperties
    Assert-EcosystemSourceProperties $properties $olderTarget $olderMinimum
    Check $true "Core .36 with accepted Momentum .35 minimum is valid on $($olderTarget.minecraft)"
    Write-Fixture $properties ($olderProperties.Replace("momentum_automobility_min_version=$olderMinimum",'momentum_automobility_min_version=0.5.0-unofficial.36'))
    Check-Throws { Assert-EcosystemSourceProperties $properties $olderTarget $olderMinimum } "Unreviewed tightening of Momentum minimum rejected on $($olderTarget.minecraft)"
}
$immutable = Join-Path $fixtureRoot 'immutable.json'
Write-EcosystemNewText $immutable 'first receipt'
Check-Throws { Write-EcosystemNewText $immutable 'replacement' } 'Existing receipt cannot be overwritten'
Check ((Get-Content -LiteralPath $immutable -Raw) -ceq 'first receipt') 'Immutable receipt retains original bytes'

$pwsh = Join-Path $PSHOME $(if ($IsWindows) { 'pwsh.exe' } else { 'pwsh' })
$failingChild = Join-Path $fixtureRoot 'fails-after-pass-text.ps1'
Write-Fixture $failingChild "Write-Output 'PASS (not an exit code)'`n[Console]::Error.WriteLine('retained diagnostic')`nexit 17`n"
$childLog = Join-Path $fixtureRoot 'failed-child.log'
$child = Invoke-EcosystemProcess -Executable $pwsh -Arguments @('-NoProfile','-NonInteractive','-File',$failingChild) -WorkingDirectory $fixtureRoot -LogPath $childLog
Check ($child.exitCode -eq 17) 'Actual child process exit code is retained'
Check-Throws { Assert-EcosystemBuildResult $child.exitCode } 'Printed PASS cannot hide nonzero child exit'
Check ((Get-Content -LiteralPath $childLog -Raw).Contains('retained diagnostic')) 'Failed child stderr retained in immutable log'

$seed = Join-Path $fixtureRoot 'seed'
[IO.Directory]::CreateDirectory($seed) | Out-Null
Invoke-EcosystemGit $seed @('init','--initial-branch=fixture') | Out-Null
Invoke-EcosystemGit $seed @('config','user.name','Source Gate Fixture') | Out-Null
Invoke-EcosystemGit $seed @('config','user.email','fixture@example.invalid') | Out-Null
foreach ($file in $manifest.sharedFiles) { Write-Fixture (Join-Path $seed $file) "identical fixture policy: $file`n" }
foreach ($source in $manifest.targets) {
    $lane = @($matrix.targets | Where-Object minecraft -CEQ $source.minecraft)[0]
    Write-Fixture (Join-Path $seed 'gradle.properties') "minecraft_version=$($lane.minecraft)`nmod_version=$($lane.automobility)`nmomentum_version=$($lane.momentum)`nmomentum_automobility_min_version=$($source.momentumAutomobilityMinVersion)`n"
    Invoke-EcosystemGit $seed @('add','--all') | Out-Null
    Invoke-EcosystemGit $seed @('commit','--message',"fixture $($lane.minecraft)") | Out-Null
    Invoke-EcosystemGit $seed @('branch',$source.ref.Substring('refs/heads/'.Length)) | Out-Null
}
$orchestrator = Join-Path $PSScriptRoot 'Test-EcosystemSources.ps1'
$output = Join-Path $fixtureRoot 'pass'
$runArguments = @('-NoProfile','-NonInteractive','-File',$orchestrator,'-RepositoryRoot',$seed,'-LocalTestRepository',$seed,'-OutputDirectory',$output)
$result = Invoke-EcosystemProcess -Executable $pwsh -Arguments $runArguments -WorkingDirectory $fixtureRoot -LogPath (Join-Path $fixtureRoot 'source-only.log')
Check ($result.exitCode -eq 0) "All three local snapshot clones validate: $($result.stderr)"
$receipt = Get-Content -LiteralPath (Join-Path $output 'source-result.json') -Raw | ConvertFrom-Json
Check ($receipt.status -ceq 'SOURCE_VALIDATION_ONLY' -and $receipt.sourceValidated -and -not $receipt.allBuildsPassed) 'Source-only receipt never claims builds passed'
Check ($receipt.targets.Count -eq 3 -and @($receipt.targets | Where-Object { -not $_.sourceValidated -or $_.buildAttempted }).Count -eq 0) 'All three targets validated without executing fixture build code'
Check (-not $receipt.publicVerified -and -not $receipt.releaseApproved) 'Source receipt never implies public release acceptance'
foreach ($row in $receipt.targets) {
    Check ($row.commit -ceq $row.before.commit -and $row.commit -ceq $row.after.commit -and $row.after.clean) "Exact immutable commit and clean checkout: $($row.minecraft)"
}
$rerun = Invoke-EcosystemProcess -Executable $pwsh -Arguments $runArguments -WorkingDirectory $fixtureRoot
Check ($rerun.exitCode -ne 0) 'A rerun cannot replace an existing output directory'
$denied = Invoke-EcosystemProcess -Executable $pwsh -Arguments ($runArguments + @('-Build')) -WorkingDirectory $fixtureRoot -Environment @{CI='true'}
Check ($denied.exitCode -ne 0) 'Local fixture builds are forbidden even in CI'

# Drift in a committed policy must fail an end-to-end source run, not just a helper assertion.
Invoke-EcosystemGit $seed @('checkout','release/rosenvik-auto36-26.2') | Out-Null
Write-Fixture (Join-Path $seed 'tools/qa/ecosystem-matrix.json') "drifted policy`n"
Invoke-EcosystemGit $seed @('add','--all') | Out-Null
Invoke-EcosystemGit $seed @('commit','--message','intentional fixture drift') | Out-Null
Invoke-EcosystemGit $seed @('checkout','fixture') | Out-Null
$driftOutput = Join-Path $fixtureRoot 'drift'
$driftResult = Invoke-EcosystemProcess -Executable $pwsh -Arguments @('-NoProfile','-NonInteractive','-File',$orchestrator,'-RepositoryRoot',$seed,'-LocalTestRepository',$seed,'-OutputDirectory',$driftOutput) -WorkingDirectory $fixtureRoot -LogPath (Join-Path $fixtureRoot 'drift.log')
Check ($driftResult.exitCode -ne 0) 'Committed branch-policy drift fails the complete orchestration'
$failedReceipt = Get-Content -LiteralPath (Join-Path $driftOutput 'source-result.json') -Raw | ConvertFrom-Json
Check ($failedReceipt.status -ceq 'FAILED' -and -not $failedReceipt.sourceValidated -and -not $failedReceipt.allBuildsPassed) 'Failure receipt cannot claim source or build success'
Check ($failedReceipt.error -like '*Shared policy drift*') 'Failure receipt explains the mismatching policy'
$snapshot = Get-Content -LiteralPath (Join-Path $driftOutput 'source-snapshot.json') -Raw | ConvertFrom-Json
Check ($snapshot.Count -eq 3) 'All exact commit identities are retained before validation starts'
Write-Fixture (Join-Path $seed 'gradle.properties') 'dirty fixture'
$dirtyState = Get-EcosystemCheckoutState $seed
Check-Throws { Assert-EcosystemCheckoutState $dirtyState $dirtyState.commit } 'Real tracked source modification detected after HEAD snapshot'
Write-Host "Source policy tests: $script:passed passed. Generated fixtures retained at $fixtureRoot"
