[CmdletBinding()]
param(
    [string] $RepositoryRoot = (Resolve-Path (Join-Path (Join-Path $PSScriptRoot '..') '..')).Path,
    [string] $PumpsAndPistonsRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-True {
    param(
        [Parameter(Mandatory)] [bool] $Condition,
        [Parameter(Mandatory)] [string] $Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Read-GradleProperties {
    param([Parameter(Mandatory)] [string] $Path)

    $properties = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*([^#!\s][^=]*?)\s*=\s*(.*?)\s*$') {
            $properties[$Matches[1].Trim()] = $Matches[2].Trim()
        }
    }
    return $properties
}

function Read-UInt32BigEndian {
    param(
        [Parameter(Mandatory)] [byte[]] $Bytes,
        [Parameter(Mandatory)] [int] $Offset
    )

    return [uint32](
        ([uint32] $Bytes[$Offset] -shl 24) -bor
        ([uint32] $Bytes[$Offset + 1] -shl 16) -bor
        ([uint32] $Bytes[$Offset + 2] -shl 8) -bor
        [uint32] $Bytes[$Offset + 3]
    )
}

function Read-PngInfo {
    param([Parameter(Mandatory)] [string] $Path)

    $bytes = [System.IO.File]::ReadAllBytes($Path)
    $signature = [byte[]] (137, 80, 78, 71, 13, 10, 26, 10)
    Assert-True ($bytes.Length -ge 33) "PNG is too short: $Path"
    for ($index = 0; $index -lt $signature.Length; $index++) {
        Assert-True ($bytes[$index] -eq $signature[$index]) "Invalid PNG signature: $Path"
    }

    $offset = 8
    $width = $null
    $height = $null
    $bitDepth = $null
    $colorType = $null
    $chunks = [System.Collections.Generic.List[string]]::new()

    while ($offset -lt $bytes.Length) {
        Assert-True (($offset + 12) -le $bytes.Length) "Truncated PNG chunk header: $Path"
        $lengthValue = Read-UInt32BigEndian -Bytes $bytes -Offset $offset
        Assert-True ($lengthValue -le [int]::MaxValue) "PNG chunk is too large: $Path"
        $length = [int] $lengthValue
        Assert-True (($offset + 12 + $length) -le $bytes.Length) "Truncated PNG chunk payload: $Path"
        $chunkType = [System.Text.Encoding]::ASCII.GetString($bytes, $offset + 4, 4)
        $chunks.Add($chunkType)

        if ($chunkType -eq 'IHDR') {
            Assert-True ($length -eq 13) "Invalid IHDR length: $Path"
            $width = Read-UInt32BigEndian -Bytes $bytes -Offset ($offset + 8)
            $height = Read-UInt32BigEndian -Bytes $bytes -Offset ($offset + 12)
            $bitDepth = $bytes[$offset + 16]
            $colorType = $bytes[$offset + 17]
        }

        $offset += 12 + $length
        if ($chunkType -eq 'IEND') {
            break
        }
    }

    Assert-True ($null -ne $width -and $null -ne $height) "PNG has no IHDR chunk: $Path"
    Assert-True ($chunks.Contains('IEND')) "PNG has no IEND chunk: $Path"

    return [pscustomobject] @{
        Path = $Path
        Width = [uint32] $width
        Height = [uint32] $height
        BitDepth = [byte] $bitDepth
        ColorType = [byte] $colorType
        Chunks = @($chunks)
        Sha256 = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    }
}

function Assert-CanonicalContact {
    param(
        [Parameter(Mandatory)] $Manifest,
        [Parameter(Mandatory)] [string] $Homepage
    )

    Assert-True ($null -ne $Manifest.contact) "Missing contact block for $($Manifest.id)."
    Assert-True ($Manifest.contact.homepage -eq $Homepage) "Unexpected homepage for $($Manifest.id)."
    Assert-True ($Manifest.contact.sources -eq 'https://git.offchunk.com/Offchunk/automobility-unofficial-port') "Unexpected source URL for $($Manifest.id)."
    Assert-True ($Manifest.contact.issues -eq 'https://git.offchunk.com/Offchunk/automobility-unofficial-port/issues') "Unexpected issue URL for $($Manifest.id)."
}

function Assert-LocalMarkdownImages {
    param([Parameter(Mandatory)] [string[]] $MarkdownPaths)

    $repositoryFullPath = [System.IO.Path]::GetFullPath($RepositoryRoot).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    )
    $repositoryPrefix = $repositoryFullPath + [System.IO.Path]::DirectorySeparatorChar
    $localImages = [System.Collections.Generic.List[string]]::new()

    foreach ($markdownPath in $MarkdownPaths) {
        $content = Get-Content -LiteralPath $markdownPath -Raw
        $matches = [regex]::Matches($content, '!\[[^\]]*\]\(\s*(?<target><[^>]+>|[^\s\)]+)')
        foreach ($match in $matches) {
            $target = $match.Groups['target'].Value.Trim('<', '>')
            if ($target -match '^(?i:https?:|data:|mailto:|#)') {
                continue
            }

            $target = ($target -split '[?#]', 2)[0]
            $target = [System.Uri]::UnescapeDataString($target)
            $resolved = [System.IO.Path]::GetFullPath((Join-Path (Split-Path -Parent $markdownPath) $target))
            Assert-True `
                -Condition $resolved.StartsWith($repositoryPrefix, [System.StringComparison]::OrdinalIgnoreCase) `
                -Message "Local image reference escapes the repository: $markdownPath -> $target"
            Assert-True (Test-Path -LiteralPath $resolved -PathType Leaf) "Broken local image reference: $markdownPath -> $target"
            $localImages.Add($resolved)
        }
    }

    return @($localImages | Sort-Object -Unique)
}

$root = [System.IO.Path]::GetFullPath($RepositoryRoot)
$publicMarkdown = @(
    'README.md',
    'CHANGELOG.md',
    'NOTICE.md',
    'LICENSE',
    'MODRINTH_DESCRIPTION.md',
    'MODRINTH_RELEASE_26.1.2.md',
    'MODRINTH_RELEASE_26.2.md',
    'MOMENTUM_MODRINTH_DESCRIPTION.md',
    'MOMENTUM_MODRINTH_RELEASE_26.1.2.md',
    'MOMENTUM_MODRINTH_RELEASE_26.2.md',
    'momentum/README.md'
) | ForEach-Object { Join-Path $root $_ }

foreach ($path in $publicMarkdown) {
    Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "Missing release-facing document: $path"
}
$linkedImages = @(Assert-LocalMarkdownImages -MarkdownPaths $publicMarkdown)

$releaseRecordPaths = @(
    'MODRINTH_RELEASE_26.1.2.md',
    'MODRINTH_RELEASE_26.2.md',
    'MOMENTUM_MODRINTH_RELEASE_26.1.2.md',
    'MOMENTUM_MODRINTH_RELEASE_26.2.md'
) | ForEach-Object { Join-Path $root $_ }
$targetScopeGuard = 'Target-scoped release record:'
foreach ($path in $releaseRecordPaths) {
    $content = Get-Content -LiteralPath $path -Raw
    Assert-True ($content.Contains($targetScopeGuard)) "Release record lacks the target-scope guard: $path"
}

$disallowedReleaseCopy = '(?i)(\bAI\b|\bAI[- ]generated\b|\bartificial intelligence\b|\bChatGPT\b|\bOpenAI\b|\bMidjourney\b|\bDALL(?:-?E|·E)\b|\bStable Diffusion\b|\bimage prompt\b|\bprompt metadata\b)'
foreach ($path in $publicMarkdown) {
    $content = Get-Content -LiteralPath $path -Raw
    Assert-True (-not [regex]::IsMatch($content, $disallowedReleaseCopy)) "Disallowed generation-tool copy found in release-facing document: $path"
}

$properties = Read-GradleProperties -Path (Join-Path $root 'gradle.properties')
foreach ($requiredProperty in @('minecraft_version', 'mod_version', 'momentum_version', 'momentum_automobility_min_version')) {
    Assert-True ($properties.ContainsKey($requiredProperty)) "Missing Gradle property: $requiredProperty"
}

$automobilityManifestPath = Join-Path $root 'fabric/src/main/resources/fabric.mod.json'
$momentumManifestPath = Join-Path $root 'momentum/src/main/resources/fabric.mod.json'
$automobilityManifest = Get-Content -LiteralPath $automobilityManifestPath -Raw | ConvertFrom-Json
$momentumManifest = Get-Content -LiteralPath $momentumManifestPath -Raw | ConvertFrom-Json

Assert-True ($automobilityManifest.id -eq 'automobility') 'Unexpected Automobility mod id.'
Assert-True ($momentumManifest.id -eq 'momentum') 'Unexpected Momentum mod id.'
Assert-True ($automobilityManifest.name -eq 'Automobility: Unofficial Port') 'Automobility display name must identify the unofficial port.'
Assert-True ($momentumManifest.name -eq 'Momentum for Automobility: Unofficial Port') 'Momentum display name must identify the unofficial port.'
Assert-True ($automobilityManifest.description -match '(?i)unofficial') 'Automobility description must identify the unofficial port.'
Assert-True ($momentumManifest.description -match '(?i)unofficial') 'Momentum description must identify the unofficial port.'
Assert-True ($automobilityManifest.license -eq 'MIT' -and $momentumManifest.license -eq 'MIT') 'Both Fabric manifests must use the repository MIT license.'
$expectedMinecraftDependency = if ($properties.minecraft_version -eq '26.1.2') {
    '26.1.x'
} else {
    $properties.minecraft_version
}
Assert-True ($automobilityManifest.depends.minecraft -eq $expectedMinecraftDependency) 'Automobility Minecraft dependency does not match the release target.'
Assert-True ($momentumManifest.depends.minecraft -eq $expectedMinecraftDependency) 'Momentum Minecraft dependency does not match the release target.'
Assert-True ($momentumManifest.depends.automobility -eq '>=${automobility_version}') 'Momentum manifest must retain its expanded minimum-version placeholder.'
Assert-True ($automobilityManifest.authors -contains 'FoundationGames') 'Automobility must retain upstream author credit.'
Assert-True ($momentumManifest.authors -contains 'milkucha') 'Momentum must retain upstream author credit.'
Assert-CanonicalContact -Manifest $automobilityManifest -Homepage 'https://modrinth.com/mod/automobility-unofficial-port'
Assert-CanonicalContact -Manifest $momentumManifest -Homepage 'https://modrinth.com/mod/momentum-for-automobility-unofficial-port'

$automobilityRelease = Get-Content -LiteralPath (Join-Path $root "MODRINTH_RELEASE_$($properties.minecraft_version).md") -Raw
$momentumRelease = Get-Content -LiteralPath (Join-Path $root "MOMENTUM_MODRINTH_RELEASE_$($properties.minecraft_version).md") -Raw
$expectedAutomobilityVersion = "$($properties.mod_version)+$($properties.minecraft_version)"
$expectedMomentumVersion = "$($properties.momentum_version)+$($properties.minecraft_version)"
Assert-True ($automobilityRelease.Contains("Version number: ``$expectedAutomobilityVersion``")) 'Current Automobility release record does not match Gradle versions.'
Assert-True ($momentumRelease.Contains("Version number: ``$expectedMomentumVersion``")) 'Current Momentum release record does not match Gradle versions.'
Assert-True ($automobilityRelease.Contains("automobility-$expectedAutomobilityVersion-fabric.jar")) 'Current Automobility release artifact name does not match Gradle versions.'
Assert-True ($momentumRelease.Contains("momentum-$expectedMomentumVersion-fabric.jar")) 'Current Momentum release artifact name does not match Gradle versions.'
$expectedAutomobilityLocalJar = "dist\release-candidate-$($properties.mod_version)\automobility-$expectedAutomobilityVersion-fabric.jar"
$automobilityLocalJarMatches = [regex]::Matches($automobilityRelease, '(?im)^\s*-\s*Local JAR:\s*`(?<path>[^`\r\n]+)`\s*$')
Assert-True ($automobilityLocalJarMatches.Count -eq 1) 'Current Automobility release record must contain exactly one Local JAR entry.'
Assert-True ($automobilityLocalJarMatches[0].Groups['path'].Value -ceq $expectedAutomobilityLocalJar) 'Current Automobility Local JAR path does not match Gradle versions.'
$expectedMomentumLocalJar = "dist\release-candidate-$($properties.momentum_version)\momentum-$expectedMomentumVersion-fabric.jar"
$momentumLocalJarMatches = [regex]::Matches($momentumRelease, '(?im)^\s*-\s*Local JAR:\s*`(?<path>[^`\r\n]+)`\s*$')
Assert-True ($momentumLocalJarMatches.Count -eq 1) 'Current Momentum release record must contain exactly one Local JAR entry.'
Assert-True ($momentumLocalJarMatches[0].Groups['path'].Value -ceq $expectedMomentumLocalJar) 'Current Momentum Local JAR path does not match Gradle versions.'
Assert-True ($momentumRelease.Contains("Automobility Unofficial Port ``$($properties.momentum_automobility_min_version)``: required")) 'Momentum release record does not match its declared minimum Automobility version.'
$momentumBuildScript = Get-Content -LiteralPath (Join-Path $root 'momentum/build.gradle.kts') -Raw
Assert-True ($momentumBuildScript.Contains('gradleProperty("momentum_automobility_min_version")')) 'Momentum resource processing must use its explicit minimum Automobility version.'

$automobilityIconPath = Join-Path $root 'common/src/main/resources/automobility.png'
$momentumIconPath = Join-Path $root 'momentum/src/main/resources/momentum_icon.png'
$automobilityIcon = Read-PngInfo -Path $automobilityIconPath
$momentumIcon = Read-PngInfo -Path $momentumIconPath
foreach ($icon in @($automobilityIcon, $momentumIcon)) {
    Assert-True ($icon.Width -eq 128 -and $icon.Height -eq 128) "Runtime icon must be 128 x 128: $($icon.Path)"
    Assert-True ($icon.BitDepth -eq 8 -and $icon.ColorType -eq 6) "Runtime icon must be an 8-bit RGBA PNG: $($icon.Path)"
    foreach ($metadataChunk in @('tEXt', 'zTXt', 'iTXt', 'eXIf')) {
        Assert-True (-not ($icon.Chunks -contains $metadataChunk)) "Runtime icon contains textual or EXIF metadata ($metadataChunk): $($icon.Path)"
    }
}
Assert-True ($automobilityIcon.Sha256 -ne $momentumIcon.Sha256) 'Automobility and Momentum runtime icons must be distinct.'

if ([string]::IsNullOrWhiteSpace($PumpsAndPistonsRoot)) {
    $sibling = Join-Path (Split-Path -Parent $root.TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)) 'Pumps-and-Pistons'
    if (Test-Path -LiteralPath $sibling -PathType Container) {
        $PumpsAndPistonsRoot = $sibling
    }
}

$familyIconsChecked = 0
if (-not [string]::IsNullOrWhiteSpace($PumpsAndPistonsRoot)) {
    $familyRoot = [System.IO.Path]::GetFullPath($PumpsAndPistonsRoot)
    $familyPaths = @(
        'src/main/resources/assets/pumps_and_pistons/icon.png',
        'automobility-compat/src/main/resources/assets/pumps_and_pistons_automobility/icon.png',
        'roadworks/src/main/resources/assets/pumps_and_pistons_roadworks/icon.png',
        'artwork/pumps-and-pistons-icon-source.png',
        'artwork/pumps-and-pistons-automobility-icon-source.png',
        'artwork/pumps-and-pistons-roadworks-icon-source.png'
    )
    foreach ($relativePath in $familyPaths) {
        $familyPath = Join-Path $familyRoot $relativePath
        Assert-True (Test-Path -LiteralPath $familyPath -PathType Leaf) "Missing Pumps & Pistons comparison icon: $familyPath"
        $familyHash = (Get-FileHash -LiteralPath $familyPath -Algorithm SHA256).Hash
        Assert-True ($familyHash -ne $automobilityIcon.Sha256) "Automobility icon duplicates Pumps & Pistons artwork: $relativePath"
        $familyIconsChecked++
    }
}

Write-Host "Release-facing QA passed."
Write-Host "  Release-facing documents: $($publicMarkdown.Count)"
Write-Host "  Release-copy provenance guard: passed"
Write-Host "  Linked local images: $($linkedImages.Count) (all present)"
Write-Host "  Runtime icons: 2 (distinct, 128 x 128 RGBA, no textual/EXIF metadata)"
if ($familyIconsChecked -gt 0) {
    Write-Host "  Pumps & Pistons comparison icons: $familyIconsChecked (all distinct from Automobility)"
} else {
    Write-Host "  Pumps & Pistons comparison: skipped (sibling repository not present)"
}
Write-Host "  Active versions: Automobility $expectedAutomobilityVersion; Momentum $expectedMomentumVersion"
Write-Host "  Momentum minimum Automobility version: $($properties.momentum_automobility_min_version)"
