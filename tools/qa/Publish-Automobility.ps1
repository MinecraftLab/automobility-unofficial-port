[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $RepositoryRoot,
    [switch] $Publish,
    [string] $TokenPath = (Join-Path $env:LOCALAPPDATA 'Rosenvik/Secrets/modrinth-release-token.dpapi')
)

# Default is read-only preflight. Never replaces/deletes an existing version.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$properties = @{}
Get-Content -LiteralPath (Join-Path $root 'gradle.properties') | ForEach-Object {
    if ($_ -match '^\s*([^#\s=]+)\s*=\s*(.*?)\s*$') { $properties[$Matches[1]] = $Matches[2] }
}
$target = $properties.minecraft_version
$version = "$($properties.mod_version)+$target"
if ($target -notin @('26.1.2', '26.2') -or $properties.mod_version -ne '0.5.0-unofficial.34') {
    throw 'This publication recipe is scoped to the reviewed .34 release targets only.'
}
$record = Get-Content -Raw -LiteralPath (Join-Path $root "MODRINTH_RELEASE_$target.md")
function Record-Field([string] $Name, [string] $Pattern) {
    $matchesFound = [regex]::Matches($record, $Pattern)
    if ($matchesFound.Count -ne 1) { throw "Expected exactly one $Name in release record." }
    return $matchesFound[0].Groups[1].Value
}
$relativeJar = Record-Field 'Local JAR' '(?m)^- Local JAR: `([^`]+)`\r?$'
$jar = [IO.Path]::GetFullPath((Join-Path $root $relativeJar))
if (-not $jar.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Artifact path escapes repository.'
}
$size = [long](Record-Field 'Size' '(?m)^- Size: `(\d+)` bytes\r?$')
$sha256 = Record-Field 'SHA256' '(?m)^- SHA-256: `([A-Fa-f0-9]{64})`\r?$'
$sha512 = Record-Field 'SHA512' '(?m)^- SHA-512: `([A-Fa-f0-9]{128})`\r?$'
$changelog = Record-Field 'changelog' '(?s)## Version Changelog\s+```markdown\r?\n(.*?)\r?\n```'
$file = Get-Item -LiteralPath $jar
if ($file.Name -cne "automobility-$version-fabric.jar" -or $file.Length -ne $size -or
    (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ne $sha256 -or
    (Get-FileHash -LiteralPath $jar -Algorithm SHA512).Hash -ne $sha512) { throw 'Artifact identity mismatch.' }

# Recheck the committed metadata/license/test/artifact gate, not just a supplied hash.
& (Join-Path $root 'tools/qa/Test-Automobility.ps1') -RepositoryRoot $root

$publicHeaders = @{ 'User-Agent' = 'Offchunk-Automobility-release/1.0' }
$api = 'https://api.modrinth.com/v2'
$project = Invoke-RestMethod "$api/project/B6d5S1wQ" -Headers $publicHeaders
$source = 'https://git.offchunk.com/Offchunk/automobility-unofficial-port'
if ($project.id -ne 'B6d5S1wQ' -or $project.status -ne 'approved' -or
    $project.source_url -ne $source -or $project.issues_url -ne "$source/issues") { throw 'Project identity/links mismatch.' }
foreach ($url in @($source, "$source/issues")) {
    if ((Invoke-WebRequest -Uri $url -Headers $publicHeaders).StatusCode -ne 200) { throw "Public link unavailable: $url" }
}
$gameVersions = if ($target -eq '26.1.2') { @('26.1', '26.1.1', '26.1.2') } else { @('26.2') }
$dependencies = @(
    @{ project_id = 'P7dR8mSH'; dependency_type = 'required' },
    @{ project_id = 'DOUdJVEm'; dependency_type = 'optional' },
    @{ project_id = 'pOgpsBUs'; dependency_type = 'optional' }
)
$body = @{
    project_id = 'B6d5S1wQ'; name = "for Minecraft $target"; version_number = $version
    changelog = $changelog; dependencies = $dependencies; game_versions = $gameVersions
    version_type = 'beta'; loaders = @('fabric'); featured = $false; status = 'listed'
    environment = 'client_and_server'; file_parts = @('file'); primary_file = 'file'
}
function Assert-PublicVersion($value) {
    $expectedDependencies = @($dependencies | ForEach-Object { "$($_.project_id)|$($_.dependency_type)" } | Sort-Object)
    $actualDependencies = @($value.dependencies | ForEach-Object { "$($_.project_id)|$($_.dependency_type)" } | Sort-Object)
    if ($value.project_id -ne 'B6d5S1wQ' -or $value.version_number -ne $version -or
        $value.status -ne 'listed' -or $value.version_type -ne 'beta' -or
        ($value.loaders -join ',') -ne 'fabric' -or
        (($value.game_versions | Sort-Object) -join ',') -ne (($gameVersions | Sort-Object) -join ',') -or
        ($expectedDependencies -join ',') -ne ($actualDependencies -join ',') -or
        $value.changelog -ne $changelog -or $value.files.Count -ne 1 -or
        $value.files[0].filename -cne $file.Name -or $value.files[0].size -ne $size -or
        $value.files[0].hashes.sha512 -ne $sha512) { throw 'Existing/public version does not match the exact release; never overwrite it.' }
}

$secure = (Get-Content -Raw -LiteralPath $TokenPath).Trim() | ConvertTo-SecureString
$token = [Net.NetworkCredential]::new('', $secure).Password
$handler = [Net.Http.HttpClientHandler]::new()
$handler.AllowAutoRedirect = $false
$http = [Net.Http.HttpClient]::new($handler)
try {
    $http.DefaultRequestHeaders.TryAddWithoutValidation('Authorization', $token) | Out-Null
    $http.DefaultRequestHeaders.TryAddWithoutValidation('User-Agent', $publicHeaders['User-Agent']) | Out-Null
    function Get-Authenticated([string] $Path) {
        $response = $http.GetAsync("$api/$Path").GetAwaiter().GetResult()
        try {
            if (-not $response.IsSuccessStatusCode) { throw "Authenticated read failed: HTTP $([int]$response.StatusCode)." }
            return ($response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json)
        } finally { $response.Dispose() }
    }
    $user = Get-Authenticated 'user'
    $members = @(Get-Authenticated "team/$($project.team)/members")
    if ($user.id -ne 'IHeWKTbh' -or @($members | Where-Object {
        $_.user.id -eq $user.id -and $_.accepted -and ([int]$_.permissions -band 1)
    }).Count -ne 1) { throw 'Unexpected account or no upload permission.' }
    $existing = @(Get-Authenticated 'project/B6d5S1wQ/version' | Where-Object { $_.version_number -eq $version })
    if ($existing.Count -gt 1) { throw 'Duplicate version numbers already exist.' }
    if ($existing.Count -eq 1) {
        Assert-PublicVersion $existing[0]
        $releaseId = $existing[0].id
        Write-Host "Exact version already exists; no write: $releaseId"
    } elseif (-not $Publish) {
        Write-Host "Preflight PASS: $version; $($file.Length) bytes; owner $($user.username). Publishing disabled."
        return
    } else {
        $form = [Net.Http.MultipartFormDataContent]::new()
        try {
            $form.Add([Net.Http.StringContent]::new(($body | ConvertTo-Json -Depth 6), [Text.Encoding]::UTF8, 'application/json'), 'data')
            $content = [Net.Http.StreamContent]::new([IO.File]::OpenRead($jar))
            $content.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new('application/java-archive')
            $form.Add($content, 'file', $file.Name)
            $response = $http.PostAsync("$api/version", $form).GetAwaiter().GetResult()
            try {
                if (-not $response.IsSuccessStatusCode) { throw "Upload failed: HTTP $([int]$response.StatusCode). Check current versions before retrying." }
                $created = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
                Assert-PublicVersion $created
                $releaseId = $created.id
            } finally { $response.Dispose() }
        } finally { $form.Dispose() }
    }
} finally { $token = $null; $http.Dispose(); $handler.Dispose() }

# Verify without credentials, and hash freshly downloaded CDN bytes.
$published = Invoke-RestMethod "$api/version/$releaseId" -Headers $publicHeaders
Assert-PublicVersion $published
$download = Join-Path $root "dist/public-verification-$releaseId-$($file.Name)"
Invoke-WebRequest -Uri $published.files[0].url -Headers $publicHeaders -OutFile $download
if ((Get-Item -LiteralPath $download).Length -ne $size -or
    (Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash -ne $sha256 -or
    (Get-FileHash -LiteralPath $download -Algorithm SHA512).Hash -ne $sha512) { throw 'Anonymous CDN download differs from exact candidate.' }
$receipt = [ordered]@{
    checked_at = [DateTimeOffset]::UtcNow.ToString('o'); version_id = $releaseId
    version_number = $version; url = "https://modrinth.com/mod/automobility-unofficial-port/version/$releaseId"
    artifact = $file.Name; size = $size; sha256 = $sha256; sha512 = $sha512
    anonymous_api_and_cdn = 'PASS'; manual_gameplay = 'NOT PROVEN for .34'
}
$receipt | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $root "dist/publication-$releaseId.json") -Encoding utf8
$receipt | ConvertTo-Json
