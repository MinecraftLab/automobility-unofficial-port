#requires -Version 7.2
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'EcosystemCi.psm1') -Force
$matrix = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'ecosystem-matrix.json') | ConvertFrom-Json
$manifest = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'ecosystem-public-artifacts.json') | ConvertFrom-Json
$script:passed = 0
function Check([bool]$Condition, [string]$Name) {
    if (!$Condition) { throw "FAIL: $Name" }
    $script:passed++
    Write-Host "PASS: $Name"
}
function Reject([scriptblock]$Action, [string]$Name) {
    $failed = $false
    try { & $Action | Out-Null } catch { $failed = $true }
    Check $failed $Name
}
function Copy-Value($Value) { ($Value | ConvertTo-Json -Depth 30) | ConvertFrom-Json }
function New-PublicReceipt {
    [pscustomobject]@{schemaVersion=1; status='PUBLIC_RELEASE_VERIFIED'; milestone=$matrix.milestone;
        publicAligned=$true; anonymousDownloadedBytesVerified=$true;
        verifiedArtifacts=@(foreach ($pin in $manifest.artifacts) {
            [pscustomobject]@{project=$pin.project; minecraft=$pin.minecraft; projectId=$pin.projectId;
                versionId=$pin.versionId; version=$pin.version; filename=$pin.filename; size=$pin.size;
                sha256=$pin.sha256; sha512=$pin.sha512; result='PASS';
                url="https://cdn.modrinth.com/data/$($pin.projectId)/versions/$($pin.versionId)/$([Uri]::EscapeDataString($pin.filename))"}
        })}
}
function New-SourceReceipt {
    $refs = @{'26.1.2'='refs/heads/release/rosenvik-auto36-26.1.2'; '26.2'='refs/heads/release/rosenvik-auto36-26.2'; '26.3'='refs/heads/port/26.3'}
    [pscustomobject]@{schemaVersion=1;status='SOURCE_BUILDS_PASSED';mode='source-and-build';
        repository='https://git.offchunk.com/Offchunk/automobility-unofficial-port.git'; fixtureMode=$false;
        sourceValidated=$true; allBuildsPassed=$true; error=$null; controllerCommit=('a'*40);
        targets=@(foreach ($target in $matrix.targets) {
            [pscustomobject]@{minecraft=$target.minecraft; ref=$refs[$target.minecraft]; commit=('b'*40);
                sourceValidated=$true; buildAttempted=$true; buildPassed=$true; buildExitCode=0;
                before=[pscustomobject]@{commit=('b'*40);clean=$true;status=''};
                after=[pscustomobject]@{commit=('b'*40);clean=$true;status=''};
                artifacts=@(foreach ($pin in $manifest.artifacts | Where-Object { $_.minecraft -ceq $target.minecraft -and $_.project -cne 'riautomobility' }) {
                    $folder=if ($pin.project -ceq 'automobility') {'fabric'} else {'momentum'}
                    [pscustomobject]@{path="$folder/build/libs/$($pin.filename)";size=$pin.size;sha256=$pin.sha256;sha512=$pin.sha512}
                })}
        })}
}
$source = New-SourceReceipt
$public = New-PublicReceipt
$links = @(Get-EcosystemCiArtifactLinks $source $public $matrix $manifest)
Check ($links.Count -eq 6 -and @($links | Where-Object { !$_.exactBytesMatch }).Count -eq 0) 'Six source-owned artifact identities exactly join nine accepted public artifacts'
Check (@($links | Where-Object project -CEQ 'riautomobility').Count -eq 0) 'Public-only RIA never becomes a source-build claim'
$source.targets = @($source.targets[2],$source.targets[0],$source.targets[1])
$public.verifiedArtifacts = @($public.verifiedArtifacts | Sort-Object filename -Descending)
Check (@(Get-EcosystemCiArtifactLinks $source $public $matrix $manifest).Count -eq 6) 'Join uses explicit project and Minecraft identity, not array order'
foreach ($change in @(
    @{name='Source-only status';edit={param($s) $s.status='SOURCE_VALIDATION_ONLY'}},
    @{name='Source-only mode';edit={param($s) $s.mode='source-only'}},
    @{name='Fixture source';edit={param($s) $s.fixtureMode=$true}},
    @{name='False-like fixture string';edit={param($s) $s.fixtureMode='false'}},
    @{name='Unofficial repository';edit={param($s) $s.repository='https://example.invalid/repo.git'}},
    @{name='Missing source validation';edit={param($s) $s.sourceValidated=$false}},
    @{name='Incomplete builds';edit={param($s) $s.allBuildsPassed=$false}},
    @{name='Truthy build string';edit={param($s) $s.allBuildsPassed='true'}},
    @{name='Missing source target';edit={param($s) $s.targets=@($s.targets | Select-Object -First 2)}},
    @{name='Duplicate source target';edit={param($s) $s.targets[1]=Copy-Value $s.targets[0]}},
    @{name='Wrong source ref';edit={param($s) $s.targets[0].ref='refs/heads/main'}},
    @{name='Malformed controller commit';edit={param($s) $s.controllerCommit='bad'}},
    @{name='Malformed source commit';edit={param($s) $s.targets[0].commit='bad'}},
    @{name='Recorded source error';edit={param($s) $s.error='build failed'}},
    @{name='Target source unvalidated';edit={param($s) $s.targets[0].sourceValidated=$false}},
    @{name='Build not attempted';edit={param($s) $s.targets[0].buildAttempted=$false}},
    @{name='Target build failed';edit={param($s) $s.targets[0].buildPassed=$false}},
    @{name='Truthy target build';edit={param($s) $s.targets[0].buildPassed='true'}},
    @{name='Actual nonzero build exit';edit={param($s) $s.targets[0].buildExitCode=17}},
    @{name='Absent build exit';edit={param($s) $s.targets[0].buildExitCode=$null}},
    @{name='String build exit';edit={param($s) $s.targets[0].buildExitCode='0'}},
    @{name='Dirty before build';edit={param($s) $s.targets[0].before.clean=$false}},
    @{name='Dirty after build';edit={param($s) $s.targets[0].after.status=' M source.java'}},
    @{name='Changed after commit';edit={param($s) $s.targets[0].after.commit='c'*40}},
    @{name='Missing built artifact';edit={param($s) $s.targets[0].artifacts=@($s.targets[0].artifacts[0])}},
    @{name='Duplicate built artifact';edit={param($s) $s.targets[0].artifacts[1]=Copy-Value $s.targets[0].artifacts[0]}},
    @{name='Unexpected artifact path';edit={param($s) $s.targets[0].artifacts[0].path='../outside.jar'}},
    @{name='Built filename mismatch';edit={param($s) $s.targets[0].artifacts[0].path=$s.targets[0].artifacts[0].path.Replace('.jar','-new.jar')}},
    @{name='Built size differs from public';edit={param($s) $s.targets[0].artifacts[0].size++}},
    @{name='Built SHA256 differs from public';edit={param($s) $s.targets[0].artifacts[0].sha256='f'*64}},
    @{name='Built SHA512 differs from public';edit={param($s) $s.targets[0].artifacts[0].sha512='f'*128}}
)) {
    $source=New-SourceReceipt
    & $change.edit $source
    Reject { Get-EcosystemCiArtifactLinks $source (New-PublicReceipt) $matrix $manifest } $change.name
}
foreach ($change in @(
    @{name='Public status failed';edit={param($p) $p.status='FAILED'}},
    @{name='Public pending target';edit={param($p) $p.publicAligned=$false}},
    @{name='Truthy public alignment';edit={param($p) $p.publicAligned='true'}},
    @{name='Download verification missing';edit={param($p) $p.anonymousDownloadedBytesVerified=$false}},
    @{name='Wrong public milestone';edit={param($p) $p.milestone='other'}},
    @{name='Missing public artifact';edit={param($p) $p.verifiedArtifacts=@($p.verifiedArtifacts | Select-Object -First 8)}},
    @{name='Duplicate public artifact';edit={param($p) $p.verifiedArtifacts[1]=Copy-Value $p.verifiedArtifacts[0]}},
    @{name='Wrong public version ID';edit={param($p) $p.verifiedArtifacts[0].versionId='Other123'}},
    @{name='Wrong public project ID';edit={param($p) $p.verifiedArtifacts[0].projectId='Other123'}},
    @{name='Wrong public filename';edit={param($p) $p.verifiedArtifacts[0].filename='wrong.jar'}},
    @{name='Wrong public version';edit={param($p) $p.verifiedArtifacts[0].version='wrong'}},
    @{name='Wrong public size';edit={param($p) $p.verifiedArtifacts[0].size++}},
    @{name='Wrong public SHA256';edit={param($p) $p.verifiedArtifacts[0].sha256='f'*64}},
    @{name='Wrong public SHA512';edit={param($p) $p.verifiedArtifacts[0].sha512='f'*128}},
    @{name='Wrong public URL';edit={param($p) $p.verifiedArtifacts[0].url='https://example.invalid/file.jar'}},
    @{name='Single public row failed';edit={param($p) $p.verifiedArtifacts[0].result='FAIL'}}
)) {
    $public=New-PublicReceipt
    & $change.edit $public
    Reject { Get-EcosystemCiArtifactLinks (New-SourceReceipt) $public $matrix $manifest } $change.name
}
Write-Host "Ecosystem CI completion tests: $script:passed passed."
