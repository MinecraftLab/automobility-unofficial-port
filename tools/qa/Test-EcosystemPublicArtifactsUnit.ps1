#requires -Version 7.2
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'EcosystemPublicArtifacts.psm1') -Force
$matrix = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'ecosystem-matrix.json') | ConvertFrom-Json
$manifest = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'ecosystem-public-artifacts.json') | ConvertFrom-Json
$script:passed = 0
function Check([bool] $Condition, [string] $Name) {
    if (!$Condition) { throw "FAIL: $Name" }
    $script:passed++
    Write-Host "PASS: $Name"
}
function Reject([scriptblock] $Action, [string] $Name) {
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    Check $rejected $Name
}
function Copy-Value($Value) { ($Value | ConvertTo-Json -Depth 30) | ConvertFrom-Json }
function New-Snapshots {
    $snapshots = @{automobility=@(); momentum=@(); riautomobility=@()}
    foreach ($artifact in $manifest.artifacts) {
        $core = @($manifest.artifacts | Where-Object { $_.project -ceq 'automobility' -and $_.minecraft -ceq $artifact.minecraft })[0]
        $snapshots[$artifact.project] += [pscustomobject]@{
            id=$artifact.versionId; project_id=$artifact.projectId; version_number=$artifact.version;
            status='listed'; version_type=$artifact.versionType; environment='client_and_server';
            date_published='2026-09-26T20:00:00Z'; loaders=@('fabric'); game_versions=@($artifact.gameVersions);
            files=@([pscustomobject]@{filename=$artifact.filename; primary=$true; size=$artifact.size;
                hashes=@{sha512=$artifact.sha512};
                url="https://cdn.modrinth.com/data/$($artifact.projectId)/versions/$($artifact.versionId)/$([Uri]::EscapeDataString($artifact.filename))"});
            dependencies=@(if ($artifact.project -cne 'automobility') {
                [pscustomobject]@{project_id=$core.projectId;version_id=$core.versionId;dependency_type='required'}
            })
        }
    }
    return $snapshots
}
Assert-EcosystemArtifactManifest $manifest $matrix
Check $true 'Reviewed nine-artifact manifest agrees with matrix'
$snapshots = New-Snapshots
Assert-EcosystemPublicSnapshot $manifest $matrix $snapshots
Check $true 'All latest public identities and dependency pins accepted'
foreach ($change in @(
    @{name='Missing artifact'; edit={param($m) $m.artifacts=@($m.artifacts | Select-Object -First 8)}},
    @{name='Extra artifact'; edit={param($m) $m.artifacts += (Copy-Value $m.artifacts[0])}},
    @{name='Duplicate row'; edit={param($m) $m.artifacts[1]=Copy-Value $m.artifacts[0]}},
    @{name='Duplicate version ID'; edit={param($m) $m.artifacts[1].versionId=$m.artifacts[0].versionId}},
    @{name='Wrong project ID'; edit={param($m) $m.artifacts[0].projectId='Other123'}},
    @{name='Unknown project'; edit={param($m) $m.artifacts[0].project='other'}},
    @{name='Unknown target'; edit={param($m) $m.artifacts[0].minecraft='27.1'}},
    @{name='Version differs from matrix'; edit={param($m) $m.artifacts[0].version='0.5.0-unofficial.37+26.1.2'}},
    @{name='Unsafe version ID'; edit={param($m) $m.artifacts[0].versionId='../wrong'}},
    @{name='Unsafe filename'; edit={param($m) $m.artifacts[0].filename='../stolen.jar'}},
    @{name='Wrong milestone'; edit={param($m) $m.milestone='other'}},
    @{name='Wrong schema'; edit={param($m) $m.schemaVersion=2}},
    @{name='Malformed SHA256'; edit={param($m) $m.artifacts[0].sha256='bad'}},
    @{name='Malformed SHA512'; edit={param($m) $m.artifacts[0].sha512='bad'}},
    @{name='Negative size'; edit={param($m) $m.artifacts[0].size=-1}},
    @{name='Excessive size'; edit={param($m) $m.artifacts[0].size=67108865}},
    @{name='Fractional size'; edit={param($m) $m.artifacts[0].size=1.5}},
    @{name='Missing target in games'; edit={param($m) $m.artifacts[0].gameVersions=@('26.1')}},
    @{name='Unexpected supported game'; edit={param($m) $m.artifacts[0].gameVersions+= '27.1'}},
    @{name='Duplicate supported game'; edit={param($m) $m.artifacts[0].gameVersions+= '26.1.2'}}
)) {
    $changed = Copy-Value $manifest
    & $change.edit $changed
    Reject { Assert-EcosystemArtifactManifest $changed $matrix } $change.name
}
$changedMatrix = Copy-Value $matrix
$changedMatrix.targets[2].momentum='0.1.11-unofficial.12'
Reject { Assert-EcosystemArtifactManifest $manifest $changedMatrix } 'Updating matrix without reviewed artifact pins fails'
$artifact = $manifest.artifacts[0]
$canonical = (New-Snapshots).automobility[0].files[0].url
foreach ($url in @(
    $canonical.Replace('https:', 'http:'),
    $canonical.Replace('cdn.modrinth.com', 'cdn.modrinth.com.evil.invalid'),
    $canonical.Replace('cdn.modrinth.com', 'evil.invalid'),
    $canonical.Replace('cdn.modrinth.com', 'cdn.modrinth.com:444'),
    $canonical.Replace('https://', 'https://user:password@'),
    ($canonical + '?token=secret'), ($canonical + '#fragment'),
    $canonical.Replace('/data/', '/other/../data/'),
    $canonical.Replace('/data/', '/%2e%2e/data/'),
    $canonical.Replace($artifact.versionId, 'Other123'),
    $canonical.Replace($artifact.projectId, 'Other123'),
    $canonical.Replace('%2B', '%252B'),
    'file:///tmp/example.jar', '//cdn.modrinth.com/example.jar'
)) { Reject { Assert-EcosystemCdnUri $url $artifact } "Unsafe or unpinned CDN URL rejected: $url" }
Assert-EcosystemCdnUri $canonical $artifact
Check $true 'Exact HTTPS pinned CDN URL accepted'
foreach ($change in @(
    @{name='Metadata version ID'; edit={param($v) $v.id='Other123'}},
    @{name='Metadata project ID'; edit={param($v) $v.project_id='Other123'}},
    @{name='Metadata version number'; edit={param($v) $v.version_number='wrong'}},
    @{name='Unlisted version'; edit={param($v) $v.status='unlisted'}},
    @{name='Release channel drift'; edit={param($v) $v.version_type='release'}},
    @{name='Environment drift'; edit={param($v) $v.environment='client_only'}},
    @{name='Loader drift'; edit={param($v) $v.loaders=@('fabric','neoforge')}},
    @{name='Game target drift'; edit={param($v) $v.game_versions=@('26.2')}},
    @{name='Extra artifact'; edit={param($v) $v.files+= Copy-Value $v.files[0]}},
    @{name='Filename drift'; edit={param($v) $v.files[0].filename='wrong.jar'}},
    @{name='Metadata size drift'; edit={param($v) $v.files[0].size++}},
    @{name='Metadata hash drift'; edit={param($v) $v.files[0].hashes.sha512='f'*128}},
    @{name='Non-primary artifact'; edit={param($v) $v.files[0].primary=$false}},
    @{name='Truthy primary string'; edit={param($v) $v.files[0].primary='true'}}
)) {
    $changed = (New-Snapshots).automobility[0]
    & $change.edit $changed
    Reject { Assert-EcosystemArtifactMetadata $artifact $changed } $change.name
}
$snapshots = New-Snapshots
$snapshots.automobility=@($snapshots.automobility | Select-Object -Skip 1)
Reject { Assert-EcosystemPublicSnapshot $manifest $matrix $snapshots } 'Missing pinned public version fails'
$snapshots = New-Snapshots
$snapshots.automobility += Copy-Value $snapshots.automobility[0]
Reject { Assert-EcosystemPublicSnapshot $manifest $matrix $snapshots } 'Duplicate public version identity fails'
$snapshots = New-Snapshots
$snapshots.automobility[0].id='Other123'
$snapshots.momentum[0].dependencies[0].version_id='Other123'
$snapshots.riautomobility[0].dependencies[0].version_id='Other123'
Reject { Assert-EcosystemPublicSnapshot $manifest $matrix $snapshots } 'Same version number under new public ID does not replace accepted artifact'
$snapshots = New-Snapshots
$snapshots.momentum[0].dependencies[0].version_id=$snapshots.automobility[1].id
Reject { Assert-EcosystemPublicSnapshot $manifest $matrix $snapshots } 'Stale cross-target core dependency fails completion'
$snapshots = New-Snapshots
$newer = Copy-Value $snapshots.automobility[0]
$newer.id='Newer123'
$newer.version_number='0.5.0-unofficial.37+26.1.2'
$newer.date_published='2026-09-27T20:00:00Z'
$snapshots.automobility += $newer
Reject { Assert-EcosystemPublicSnapshot $manifest $matrix $snapshots } 'Newer unreviewed public version cannot be hidden by a pinned old version'

# Pure in-memory fixtures: no live network or filesystem writes.
$bytes = [Text.Encoding]::UTF8.GetBytes('reviewed-public-artifact-fixture')
$memory = [IO.MemoryStream]::new($bytes, $false)
$destination = [IO.MemoryStream]::new()
try {
    $identity = Get-EcosystemStreamIdentity -InputStream $memory -ExpectedSize $bytes.Length -OutputStream $destination
    Check ($identity.size -eq $bytes.Length -and $identity.sha256 -ceq [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)) -and
        $identity.sha512 -ceq [Convert]::ToHexString([Security.Cryptography.SHA512]::HashData($bytes))) 'Stream computes size and both hashes correctly'
    Check ([Convert]::ToHexString($destination.ToArray()) -ceq [Convert]::ToHexString($bytes)) 'Bounded stream preserves downloaded bytes'
    Assert-EcosystemArtifactIdentity $identity $identity
    Check $true 'Matching reviewed byte identity accepted'
    foreach ($field in @('size','sha256','sha512')) {
        $changed = Copy-Value $identity
        $changed.$field = if ($field -eq 'size') { $identity.size + 1 } else { 'bad' }
        Reject { Assert-EcosystemArtifactIdentity $identity $changed } "Downloaded $field mismatch fails"
    }
    $memory.Position=0
    Reject { Get-EcosystemStreamIdentity $memory ($bytes.Length+1) } 'Truncated stream fails'
    $memory.Position=0
    Reject { Get-EcosystemStreamIdentity $memory ($bytes.Length-1) } 'Oversized stream fails'
    $memory.Position=0
    $cancellation = [Threading.CancellationTokenSource]::new()
    try {
        $cancellation.Cancel()
        Reject { Get-EcosystemStreamIdentity -InputStream $memory -ExpectedSize $bytes.Length -CancellationToken $cancellation.Token } 'Cancelled or timed-out body read fails'
    } finally { $cancellation.Dispose() }
} finally { $memory.Dispose(); $destination.Dispose() }
Write-Host "Ecosystem public artifact tests: $script:passed passed."
