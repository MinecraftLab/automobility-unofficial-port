#requires -Version 7.2
[CmdletBinding()]
param(
    [string] $RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path,
    [Parameter(Mandatory)] [string] $OutputDirectory
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'EcosystemPublicArtifacts.psm1') -Force
$matrixPath = Join-Path $PSScriptRoot 'ecosystem-matrix.json'
$manifestPath = Join-Path $PSScriptRoot 'ecosystem-public-artifacts.json'
$matrix = Get-Content -Raw -LiteralPath $matrixPath | ConvertFrom-Json
$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
Assert-EcosystemArtifactManifest -Manifest $manifest -Matrix $matrix
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $output) { throw 'OutputDirectory must be a new path; previous verification evidence is immutable.' }
[IO.Directory]::CreateDirectory($output) | Out-Null
$parityPath = Join-Path $output 'public-parity.json'
# This existing gate fails closed; never pass PendingMinecraft, ReportOnly or LocalOnly.
& (Join-Path $PSScriptRoot 'Test-EcosystemParity.ps1') -RepositoryRoot $RepositoryRoot -OutputPath $parityPath
$parity = Get-Content -Raw -LiteralPath $parityPath | ConvertFrom-Json
if (!$parity.publicAligned -or !$parity.preflightAllowed -or $parity.pendingMinecraft) { throw 'Full public metadata parity did not pass.' }
$snapshots = @{}
foreach ($project in @('automobility','momentum','riautomobility')) { $snapshots[$project] = @($parity.snapshots.$project) }
Assert-EcosystemPublicSnapshot -Manifest $manifest -Matrix $matrix -Snapshots $snapshots
$handler = [Net.Http.HttpClientHandler]::new()
$handler.AllowAutoRedirect = $false
$handler.UseCookies = $false
$handler.UseDefaultCredentials = $false
$http = [Net.Http.HttpClient]::new($handler)
$http.DefaultRequestHeaders.UserAgent.ParseAdd('Offchunk-Ecosystem-Public-Verification/1.0')
$results = [Collections.Generic.List[object]]::new()
try {
    foreach ($artifact in $manifest.artifacts) {
        $version = @($snapshots[$artifact.project] | Where-Object id -CEQ $artifact.versionId)[0]
        $url = $version.files[0].url
        Assert-EcosystemCdnUri -Url $url -Artifact $artifact
        $timeout = [Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds(60))
        $response = $null
        $downloadStream = $null
        $destination = $null
        try {
            $response = $http.GetAsync($url, [Net.Http.HttpCompletionOption]::ResponseHeadersRead, $timeout.Token).GetAwaiter().GetResult()
            if ([int]$response.StatusCode -ne 200) { throw "Anonymous CDN request failed (redirects refused): HTTP $([int]$response.StatusCode)." }
            $length = $response.Content.Headers.ContentLength
            if ($null -ne $length -and $length -ne [long]$artifact.size) { throw 'CDN Content-Length differs from the pinned size.' }
            $downloadStream = $response.Content.ReadAsStreamAsync($timeout.Token).GetAwaiter().GetResult()
            $destination = [IO.File]::Open((Join-Path $output $artifact.filename), [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
            $identity = Get-EcosystemStreamIdentity -InputStream $downloadStream -OutputStream $destination -ExpectedSize $artifact.size -CancellationToken $timeout.Token
            Assert-EcosystemArtifactIdentity -Artifact $artifact -Identity $identity
            $results.Add([pscustomobject]@{project=$artifact.project; minecraft=$artifact.minecraft; projectId=$artifact.projectId;
                versionId=$artifact.versionId; version=$artifact.version; filename=$artifact.filename; url=$url;
                size=$identity.size; sha256=$identity.sha256; sha512=$identity.sha512; result='PASS'})
            Write-Host "Anonymous artifact PASS: $($artifact.project) / $($artifact.minecraft) / $($artifact.versionId)"
        } finally {
            if ($null -ne $destination) { $destination.Dispose() }
            if ($null -ne $downloadStream) { $downloadStream.Dispose() }
            if ($null -ne $response) { $response.Dispose() }
            $timeout.Dispose()
        }
    }
} finally { $http.Dispose(); $handler.Dispose() }
if ($results.Count -ne 9) { throw 'Incomplete public artifact verification.' }
$receipt = [ordered]@{
    schemaVersion=1; checkedAtUtc=[DateTime]::UtcNow.ToString('o'); milestone=$matrix.milestone;
    status='PUBLIC_RELEASE_VERIFIED'; publicAligned=$true; anonymousDownloadedBytesVerified=$true;
    matrixSha256=(Get-FileHash -LiteralPath $matrixPath -Algorithm SHA256).Hash;
    artifactManifestSha256=(Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash;
    parityEvidenceSha256=(Get-FileHash -LiteralPath $parityPath -Algorithm SHA256).Hash;
    verifiedArtifacts=@($results.ToArray());
    limits='Read-only public metadata and pinned downloaded-byte verification, not authorization to publish or proof of gameplay, controller, multiplayer, source-build or installation acceptance.'
}
# No completion receipt exists on any earlier failure. Preserve partial downloads for diagnosis.
$receiptPath = Join-Path $output 'public-result.json'
$stream = [IO.File]::Open($receiptPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
$writer = [IO.StreamWriter]::new($stream, [Text.UTF8Encoding]::new($false))
try { $writer.Write(($receipt | ConvertTo-Json -Depth 20)) } finally { $writer.Dispose() }
Write-Host "Public ecosystem finish gate PASS: all nine pinned artifacts verified. Receipt: $receiptPath"
