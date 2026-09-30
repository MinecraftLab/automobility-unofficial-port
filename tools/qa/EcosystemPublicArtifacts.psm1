#requires -Version 7.2
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'EcosystemParity.psm1') -Force

function Assert-EcosystemArtifactManifest {
    param([Parameter(Mandatory)] $Manifest, [Parameter(Mandatory)] $Matrix)
    Assert-EcosystemMatrix $Matrix
    if ($Manifest.schemaVersion -ne 1 -or $Manifest.milestone -cne $Matrix.milestone -or
        @($Manifest.artifacts).Count -ne 9) { throw 'Artifact manifest must pin this milestone and exactly nine public artifacts.' }
    $seenRows = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $seenIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($artifact in $Manifest.artifacts) {
        if ($artifact.project -cnotin @('automobility','momentum','riautomobility')) { throw 'Unexpected artifact project.' }
        $target = @($Matrix.targets | Where-Object minecraft -CEQ $artifact.minecraft)
        if ($target.Count -ne 1) { throw 'Unexpected artifact Minecraft target.' }
        $expectedVersion = "$($target[0].$($artifact.project))+$($artifact.minecraft)"
        $suffix = if ($artifact.project -ceq 'riautomobility') { '.jar' } else { '-fabric.jar' }
        $expectedFilename = "$($artifact.project)-$expectedVersion$suffix"
        $size = 0L
        if ($artifact.projectId -cne $Matrix.projects.$($artifact.project) -or
            $artifact.version -cne $expectedVersion -or $artifact.filename -cne $expectedFilename -or
            $artifact.versionId -cnotmatch '^[A-Za-z0-9]{8}$' -or
            !$seenRows.Add("$($artifact.project)|$($artifact.minecraft)") -or !$seenIds.Add($artifact.versionId) -or
            ![long]::TryParse([string]$artifact.size, [ref]$size) -or $size -le 0 -or $size -gt 64MB -or
            $artifact.sha256 -notmatch '^[A-Fa-f0-9]{64}$' -or $artifact.sha512 -notmatch '^[A-Fa-f0-9]{128}$' -or
            $artifact.versionType -cnotin @('release','beta','alpha')) { throw 'Artifact manifest identity, size or hash is invalid or differs from the matrix.' }
        $games = @($artifact.gameVersions)
        $allowedGames = if ($artifact.minecraft -ceq '26.1.2') { @('26.1','26.1.1','26.1.2') } else { @($artifact.minecraft) }
        if ($games -cnotcontains $artifact.minecraft -or @($games | Select-Object -Unique).Count -ne $games.Count -or
            @($games | Where-Object { $_ -cnotin $allowedGames }).Count) { throw 'Artifact game-version policy is invalid.' }
    }
}

function Assert-EcosystemCdnUri {
    param([Parameter(Mandatory)] [string] $Url, [Parameter(Mandatory)] $Artifact)
    $uri = $null
    $canonical = "https://cdn.modrinth.com/data/$($Artifact.projectId)/versions/$($Artifact.versionId)/$([Uri]::EscapeDataString($Artifact.filename))"
    if (![Uri]::TryCreate($Url, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -cne 'https' -or
        $uri.Host -cne 'cdn.modrinth.com' -or !$uri.IsDefaultPort -or $uri.UserInfo -or $uri.Query -or $uri.Fragment -or
        $Url -cne $canonical) { throw 'CDN URL must exactly identify the pinned artifact on HTTPS cdn.modrinth.com without credentials, query or redirects.' }
}

function Assert-EcosystemArtifactMetadata {
    param([Parameter(Mandatory)] $Artifact, [Parameter(Mandatory)] $Version)
    if ($Version.id -cne $Artifact.versionId -or $Version.project_id -cne $Artifact.projectId -or
        $Version.version_number -cne $Artifact.version -or $Version.status -cne 'listed' -or
        $Version.version_type -cne $Artifact.versionType -or $Version.environment -cne 'client_and_server' -or
        (@($Version.loaders) -join ',') -cne 'fabric' -or
        ((@($Version.game_versions) | Sort-Object) -join ',') -cne ((@($Artifact.gameVersions) | Sort-Object) -join ',') -or
        @($Version.files).Count -ne 1) { throw 'Public version metadata differs from its reviewed artifact manifest.' }
    $file = $Version.files[0]
    $size = 0L
    if ($file.primary -isnot [bool] -or !$file.primary -or $file.filename -cne $Artifact.filename -or
        ![long]::TryParse([string]$file.size, [ref]$size) -or $size -ne [long]$Artifact.size -or
        $file.hashes.sha512 -ine $Artifact.sha512) { throw 'Public primary artifact differs from its reviewed manifest.' }
    Assert-EcosystemCdnUri -Url $file.url -Artifact $Artifact
}

function Assert-EcosystemPublicSnapshot {
    param([Parameter(Mandatory)] $Manifest, [Parameter(Mandatory)] $Matrix,
        [Parameter(Mandatory)] [hashtable] $Snapshots)
    Assert-EcosystemArtifactManifest -Manifest $Manifest -Matrix $Matrix
    $rows = @(Get-EcosystemParity -Matrix $Matrix -Snapshots $Snapshots)
    if ($rows.Count -ne 9 -or @($rows | Where-Object { !$_.aligned }).Count) { throw 'The full latest public ecosystem is not aligned; no pending-target or report-only exemption is allowed.' }
    foreach ($artifact in $Manifest.artifacts) {
        $row = @($rows | Where-Object { $_.project -ceq $artifact.project -and $_.minecraft -ceq $artifact.minecraft })
        if ($row.Count -ne 1 -or $row[0].versionId -cne $artifact.versionId) { throw 'Latest public version ID differs from the pinned accepted artifact.' }
        $matches = @($Snapshots[$artifact.project] | Where-Object id -CEQ $artifact.versionId)
        if ($matches.Count -ne 1) { throw 'Missing or duplicate pinned public version identity.' }
        Assert-EcosystemArtifactMetadata -Artifact $artifact -Version $matches[0]
    }
}

function Get-EcosystemStreamIdentity {
    param([Parameter(Mandatory)] [IO.Stream] $InputStream,
        [Parameter(Mandatory)] [ValidateRange(1,67108864)] [long] $ExpectedSize,
        [IO.Stream] $OutputStream,
        [Threading.CancellationToken] $CancellationToken = [Threading.CancellationToken]::None)
    $sha256 = [Security.Cryptography.IncrementalHash]::CreateHash([Security.Cryptography.HashAlgorithmName]::SHA256)
    $sha512 = [Security.Cryptography.IncrementalHash]::CreateHash([Security.Cryptography.HashAlgorithmName]::SHA512)
    $buffer = [byte[]]::new(65536)
    $size = 0L
    try {
        while ($true) {
            # Read at most one byte beyond the declared size. Never persist excess bytes.
            $count = [int][Math]::Min($buffer.Length, $ExpectedSize - $size + 1)
            $read = $InputStream.ReadAsync($buffer, 0, $count, $CancellationToken).GetAwaiter().GetResult()
            if ($read -eq 0) { break }
            $size += $read
            if ($size -gt $ExpectedSize) { throw 'Downloaded artifact exceeds its pinned size.' }
            if ($null -ne $OutputStream) { $OutputStream.Write($buffer, 0, $read) }
            $sha256.AppendData($buffer, 0, $read)
            $sha512.AppendData($buffer, 0, $read)
        }
        if ($size -ne $ExpectedSize) { throw 'Downloaded artifact is truncated.' }
        return [pscustomobject]@{size=$size; sha256=[Convert]::ToHexString($sha256.GetHashAndReset()); sha512=[Convert]::ToHexString($sha512.GetHashAndReset())}
    } finally { $sha256.Dispose(); $sha512.Dispose() }
}

function Assert-EcosystemArtifactIdentity {
    param([Parameter(Mandatory)] $Artifact, [Parameter(Mandatory)] $Identity)
    if ($Identity.size -ne $Artifact.size -or $Identity.sha256 -ine $Artifact.sha256 -or $Identity.sha512 -ine $Artifact.sha512) {
        throw 'Anonymous downloaded bytes differ from the pinned reviewed artifact.'
    }
}

Export-ModuleMember -Function Assert-EcosystemArtifactManifest, Assert-EcosystemCdnUri, Assert-EcosystemArtifactMetadata,
    Assert-EcosystemPublicSnapshot, Get-EcosystemStreamIdentity, Assert-EcosystemArtifactIdentity
