[CmdletBinding()]
param(
    [string] $RepositoryRoot = (Resolve-Path (Join-Path (Join-Path $PSScriptRoot '..') '..')).Path,
    [string] $JavaHome,
    [switch] $Rebuild,
    [switch] $PrepareReleaseRecord,
    [switch] $CiBuildVerification
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

function Get-JavaMajorVersion {
    param([Parameter(Mandatory)] [string] $JdkHome)

    $executableName = if ($env:OS -eq 'Windows_NT') { 'java.exe' } else { 'java' }
    $java = Join-Path (Join-Path $JdkHome 'bin') $executableName
    if (-not (Test-Path -LiteralPath $java -PathType Leaf)) {
        return $null
    }

    $versionOutput = (& $java -version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $versionOutput -notmatch 'version\s+"(?<version>[^"]+)"') {
        return $null
    }

    $version = $Matches.version
    if ($version -match '^1\.(?<major>\d+)') {
        return [int] $Matches.major
    }
    if ($version -match '^(?<major>\d+)') {
        return [int] $Matches.major
    }
    return $null
}

function Resolve-Java25Home {
    param([string] $RequestedHome)

    if (-not [string]::IsNullOrWhiteSpace($RequestedHome)) {
        $requested = [System.IO.Path]::GetFullPath($RequestedHome)
        $requestedMajor = Get-JavaMajorVersion -JdkHome $requested
        Assert-True ($requestedMajor -eq 25) "-JavaHome must point to JDK 25; found major version '$requestedMajor' at $requested."
        return $requested
    }

    $candidates = [System.Collections.Generic.List[string]]::new()
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $candidates.Add($env:JAVA_HOME)
    }

    if (-not [string]::IsNullOrWhiteSpace($env:USERPROFILE)) {
        $knownFallback = Join-Path $env:USERPROFILE '.jdks/temurin-25-20260619175454/jdk-25.0.3+9'
        $candidates.Add($knownFallback)

        $jdksRoot = Join-Path $env:USERPROFILE '.jdks'
        if (Test-Path -LiteralPath $jdksRoot -PathType Container) {
            $javaExecutables = Get-ChildItem -LiteralPath $jdksRoot -File -Recurse -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -in @('java', 'java.exe') -and $_.Directory.Name -eq 'bin' }
            foreach ($javaExecutable in $javaExecutables) {
                $candidates.Add($javaExecutable.Directory.Parent.FullName)
            }
        }
    }

    $pathJava = Get-Command java -ErrorAction SilentlyContinue
    if ($null -ne $pathJava -and -not [string]::IsNullOrWhiteSpace($pathJava.Source)) {
        $candidates.Add((Split-Path -Parent (Split-Path -Parent $pathJava.Source)))
    }

    foreach ($candidate in @($candidates | Select-Object -Unique)) {
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }
        $fullCandidate = [System.IO.Path]::GetFullPath($candidate)
        if ((Get-JavaMajorVersion -JdkHome $fullCandidate) -eq 25) {
            return $fullCandidate
        }
    }

    throw 'JDK 25 was not found. Pass it explicitly, for example: -JavaHome C:\path\to\jdk-25.'
}

function Invoke-Gradle {
    param(
        [Parameter(Mandatory)] [string] $Root,
        [Parameter(Mandatory)] [string[]] $Arguments
    )

    Push-Location $Root
    try {
        if ($env:OS -eq 'Windows_NT') {
            & (Join-Path $Root 'gradlew.bat') @Arguments
        } else {
            & bash (Join-Path $Root 'gradlew') @Arguments
        }
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle failed with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
}

function Get-ZipEntryBytes {
    param([Parameter(Mandatory)] [System.IO.Compression.ZipArchiveEntry] $Entry)

    $stream = $Entry.Open()
    $memory = [System.IO.MemoryStream]::new()
    try {
        $stream.CopyTo($memory)
        return $memory.ToArray()
    } finally {
        $memory.Dispose()
        $stream.Dispose()
    }
}

function Get-BytesSha256 {
    param([Parameter(Mandatory)] [byte[]] $Bytes)

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        return [System.BitConverter]::ToString($sha256.ComputeHash($Bytes)).Replace('-', '')
    } finally {
        $sha256.Dispose()
    }
}

function Assert-JarEntryMatchesFile {
    param(
        [Parameter(Mandatory)] [System.IO.Compression.ZipArchive] $Archive,
        [Parameter(Mandatory)] [string] $EntryName,
        [Parameter(Mandatory)] [string] $FilePath,
        [Parameter(Mandatory)] [string] $ArtifactName
    )

    $entries = @($Archive.Entries | Where-Object { $_.FullName -ceq $EntryName })
    Assert-True ($entries.Count -eq 1) "$ArtifactName must contain exactly one $EntryName entry."
    $entryHash = Get-BytesSha256 -Bytes (Get-ZipEntryBytes -Entry $entries[0])
    $fileHash = (Get-FileHash -LiteralPath $FilePath -Algorithm SHA256).Hash
    Assert-True ($entryHash -eq $fileHash) "$ArtifactName contains a modified $EntryName; it must match $FilePath byte-for-byte."
}

function Read-ReleaseArtifactRecord {
    param(
        [Parameter(Mandatory)] [string] $Path,
        [switch] $RequireSha512,
        [switch] $RequireLocalJar
    )

    $content = Get-Content -LiteralPath $Path -Raw
    $localJarMatches = [regex]::Matches($content, '(?im)^\s*-\s*Local JAR:\s*`(?<path>[^`\r\n]+)`\s*$')
    $sizeMatches = [regex]::Matches($content, '(?im)^\s*-\s*Size:\s*`(?<size>\d+)`\s*bytes\s*$')
    $shaMatches = [regex]::Matches($content, '(?im)^\s*-\s*SHA-256:\s*`(?<sha>[0-9a-f]{64})`\s*$')
    $sha512Matches = [regex]::Matches($content, '(?im)^\s*-\s*SHA-512:\s*`(?<sha>[0-9a-f]{128})`\s*$')
    Assert-True ($sizeMatches.Count -eq 1) "Release record must contain exactly one numeric Size entry: $Path"
    Assert-True ($shaMatches.Count -eq 1) "Release record must contain exactly one 64-digit SHA-256 entry: $Path"
    Assert-True ($sha512Matches.Count -le 1) "Release record must not contain more than one SHA-512 entry: $Path"
    if ($RequireSha512) {
        Assert-True ($sha512Matches.Count -eq 1) "Release record must contain exactly one 128-digit SHA-512 entry: $Path"
    }
    Assert-True ($localJarMatches.Count -le 1) "Release record must not contain more than one Local JAR entry: $Path"
    if ($RequireLocalJar) {
        Assert-True ($localJarMatches.Count -eq 1) "Release record must contain exactly one Local JAR entry: $Path"
    }

    return [pscustomobject] @{
        Path = $Path
        LocalJar = if ($localJarMatches.Count -eq 1) { $localJarMatches[0].Groups['path'].Value } else { $null }
        Size = [long] $sizeMatches[0].Groups['size'].Value
        Sha256 = $shaMatches[0].Groups['sha'].Value.ToUpperInvariant()
        Sha512 = if ($sha512Matches.Count -eq 1) { $sha512Matches[0].Groups['sha'].Value.ToUpperInvariant() } else { $null }
    }
}

function Resolve-ReleaseLocalJarPath {
    param(
        [Parameter(Mandatory)] [string] $Root,
        [Parameter(Mandatory)] $Record,
        [switch] $AllowMissing
    )

    Assert-True (-not [string]::IsNullOrWhiteSpace($Record.LocalJar)) "Release record has no Local JAR path: $($Record.Path)"
    $recordedPath = [string] $Record.LocalJar
    Assert-True `
        -Condition ($recordedPath -notmatch '^(?:[\\/]|[A-Za-z]:[\\/])') `
        -Message "Local JAR must be a repository-relative path: $recordedPath"

    $separator = [System.IO.Path]::DirectorySeparatorChar
    $portablePath = $recordedPath -replace '[\\/]', [string] $separator
    $rootFullPath = [System.IO.Path]::GetFullPath($Root).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    )
    $rootPrefix = $rootFullPath + $separator
    $resolvedPath = [System.IO.Path]::GetFullPath((Join-Path $rootFullPath $portablePath))
    $comparison = if ($env:OS -eq 'Windows_NT') {
        [System.StringComparison]::OrdinalIgnoreCase
    } else {
        [System.StringComparison]::Ordinal
    }
    Assert-True `
        -Condition $resolvedPath.StartsWith($rootPrefix, $comparison) `
        -Message "Local JAR path escapes the repository: $recordedPath"
    if (-not $AllowMissing) {
        Assert-True (Test-Path -LiteralPath $resolvedPath -PathType Leaf) "Release-candidate Local JAR does not exist: $resolvedPath"
    }
    return $resolvedPath
}

function Get-FileArtifactIdentity {
    param([Parameter(Mandatory)] [string] $Path)

    return [pscustomobject] @{
        Name = Split-Path -Leaf $Path
        Path = $Path
        Size = (Get-Item -LiteralPath $Path).Length
        Sha256 = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
        Sha512 = (Get-FileHash -LiteralPath $Path -Algorithm SHA512).Hash
    }
}

function Assert-ArtifactMatchesReleaseRecord {
    param(
        [Parameter(Mandatory)] $Artifact,
        [Parameter(Mandatory)] $Record
    )

    $matches = $Artifact.Size -eq $Record.Size -and $Artifact.Sha256 -eq $Record.Sha256
    $builtDescription = "$($Artifact.Size) bytes, SHA-256 $($Artifact.Sha256)"
    $recordedDescription = "$($Record.Size) bytes, SHA-256 $($Record.Sha256)"
    if ($null -ne $Record.Sha512) {
        $matches = $matches -and $Artifact.Sha512 -eq $Record.Sha512
        $builtDescription += ", SHA-512 $($Artifact.Sha512)"
        $recordedDescription += ", SHA-512 $($Record.Sha512)"
    }
    Assert-True `
        -Condition $matches `
        -Message "$($Artifact.Name) does not match $($Record.Path). Built: $builtDescription. Recorded: $recordedDescription. Do not replace a published version; bump its version. For a new unpublished version, run -PrepareReleaseRecord, update the record, then rerun the final gate."
}

function Assert-ArtifactsMatch {
    param(
        [Parameter(Mandatory)] $BuiltArtifact,
        [Parameter(Mandatory)] $LocalArtifact
    )

    Assert-True `
        -Condition (
            $BuiltArtifact.Size -eq $LocalArtifact.Size -and
            $BuiltArtifact.Sha256 -eq $LocalArtifact.Sha256 -and
            $BuiltArtifact.Sha512 -eq $LocalArtifact.Sha512
        ) `
        -Message "Release-candidate Local JAR '$($LocalArtifact.Path)' is not byte-identical to the build artifact '$($BuiltArtifact.Name)'. Copy the current build artifact to the exact Local JAR path recorded for the release, then rerun the default gate."
}

function Assert-FabricJar {
    param(
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [string] $ExpectedId,
        [Parameter(Mandatory)] [string] $ExpectedVersion,
        [Parameter(Mandatory)] [string] $ExpectedMinecraftVersion,
        [Parameter(Mandatory)] [hashtable] $Properties,
        [Parameter(Mandatory)] [string] $Root
    )

    Assert-True (Test-Path -LiteralPath $Path -PathType Leaf) "Missing production artifact: $Path"
    $artifactName = Split-Path -Leaf $Path
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $entryNames = @($archive.Entries | ForEach-Object { $_.FullName })
        $duplicates = @($entryNames | Group-Object | Where-Object { $_.Count -gt 1 } | ForEach-Object { $_.Name })
        Assert-True ($duplicates.Count -eq 0) "$artifactName contains duplicate ZIP entries: $($duplicates -join ', ')"

        $unsafeEntries = @($entryNames | Where-Object {
            $_ -match '\\' -or
            $_ -match '^(?:/|[A-Za-z]:)' -or
            $_ -match '(^|/)\.\.(/|$)'
        })
        Assert-True ($unsafeEntries.Count -eq 0) "$artifactName contains unsafe ZIP paths: $($unsafeEntries -join ', ')"

        $forbiddenEntries = @($entryNames | Where-Object {
            $_ -match '(?i)(^|/)(\.git|\.github|gradle|build|run|dist)(/|$)' -or
            $_ -match '(?i)\.(java|kt|kts|ps1|bat|cmd|gradle)$' -or
            $_ -match '(?i)(^|/)(gradle\.properties|gradlew)$' -or
            $_ -match '(AnimalBoardingQa|Ecosystem263Qa|Showcase263Qa|Client263Qa|ClientDrivingQa|RemoteInterpolationQa)'
        })
        Assert-True ($forbiddenEntries.Count -eq 0) "$artifactName contains development-only files: $($forbiddenEntries -join ', ')"

        $manifestEntries = @($archive.Entries | Where-Object { $_.FullName -ceq 'fabric.mod.json' })
        Assert-True ($manifestEntries.Count -eq 1) "$artifactName must contain exactly one fabric.mod.json."
        $manifestBytes = Get-ZipEntryBytes -Entry $manifestEntries[0]
        $manifestText = [System.Text.Encoding]::UTF8.GetString($manifestBytes)
        Assert-True ($manifestText -notmatch '\$\{[^}]+\}') "$artifactName contains an unexpanded placeholder in fabric.mod.json."
        $manifest = $manifestText | ConvertFrom-Json

        Assert-True ($manifest.id -eq $ExpectedId) "$artifactName has unexpected mod id '$($manifest.id)'."
        Assert-True ($manifest.version -eq $ExpectedVersion) "$artifactName has version '$($manifest.version)', expected '$ExpectedVersion'."
        Assert-True ($manifest.license -eq 'MIT') "$artifactName must declare the MIT license."
        Assert-True ($manifest.environment -eq '*') "$artifactName must load on both client and server."
        Assert-True ($manifest.depends.minecraft -eq $ExpectedMinecraftVersion) "$artifactName targets Minecraft '$($manifest.depends.minecraft)', expected '$ExpectedMinecraftVersion'."
        Assert-True ($manifest.depends.java -eq '>=25') "$artifactName must require Java 25 or newer."
        Assert-True ($manifest.depends.fabricloader -eq ">=$($Properties.fabric_version)") "$artifactName has an unexpected Fabric Loader dependency."
        Assert-True ($manifest.depends.'fabric-api' -eq ">=$($Properties.fabric_api_version)") "$artifactName has an unexpected Fabric API dependency."

        if ($ExpectedId -eq 'automobility') {
            Assert-True ($manifest.name -eq 'Automobility: Unofficial Port') "$artifactName has an unexpected display name."
            Assert-True ($manifest.icon -eq 'automobility.png') "$artifactName has an unexpected icon path."
            $objEntries = @($archive.Entries | Where-Object { $_.FullName -ceq 'META-INF/jars/obj-0.4.0.jar' })
            Assert-True ($objEntries.Count -eq 1) "$artifactName must contain exactly one embedded Obj 0.4.0 JAR."
            Assert-JarEntryMatchesFile `
                -Archive $archive `
                -EntryName 'THIRD_PARTY_LICENSES/obj-0.4.0-LICENSE.txt' `
                -FilePath (Join-Path $Root 'THIRD_PARTY_LICENSES/obj-0.4.0-LICENSE.txt') `
                -ArtifactName $artifactName
        } else {
            Assert-True ($manifest.name -eq 'Momentum for Automobility: Unofficial Port') "$artifactName has an unexpected display name."
            Assert-True ($manifest.icon -eq 'momentum_icon.png') "$artifactName has an unexpected icon path."
            Assert-True ($manifest.depends.automobility -eq ">=$($Properties.momentum_automobility_min_version)") "$artifactName has an unexpected Automobility dependency."
        }

        $requiredResources = [System.Collections.Generic.List[string]]::new()
        $requiredResources.Add([string] $manifest.icon)
        foreach ($mixin in @($manifest.mixins)) {
            if ($mixin -is [string]) {
                $requiredResources.Add($mixin)
            }
        }
        if ($null -ne $manifest.PSObject.Properties['accessWidener']) {
            $requiredResources.Add([string] $manifest.accessWidener)
        }
        if ($null -ne $manifest.PSObject.Properties['jars']) {
            foreach ($nestedJar in @($manifest.jars)) {
                if ($null -ne $nestedJar -and $null -ne $nestedJar.PSObject.Properties['file']) {
                    $requiredResources.Add([string] $nestedJar.file)
                }
            }
        }
        foreach ($resource in $requiredResources) {
            Assert-True ($entryNames -ccontains $resource) "$artifactName references missing JAR resource '$resource'."
        }

        foreach ($entrypointGroup in $manifest.entrypoints.PSObject.Properties) {
            foreach ($entrypoint in @($entrypointGroup.Value)) {
                if ($entrypoint -isnot [string]) {
                    continue
                }
                $classEntry = $entrypoint.Replace('.', '/') + '.class'
                Assert-True ($entryNames -ccontains $classEntry) "$artifactName references missing entrypoint class '$entrypoint'."
            }
        }

        Assert-JarEntryMatchesFile -Archive $archive -EntryName 'LICENSE' -FilePath (Join-Path $Root 'LICENSE') -ArtifactName $artifactName
        Assert-JarEntryMatchesFile -Archive $archive -EntryName 'NOTICE.md' -FilePath (Join-Path $Root 'NOTICE.md') -ArtifactName $artifactName
    } finally {
        $archive.Dispose()
    }

    return [pscustomobject] @{
        Name = $artifactName
        Size = (Get-Item -LiteralPath $Path).Length
        Sha256 = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
        Sha512 = (Get-FileHash -LiteralPath $Path -Algorithm SHA512).Hash
    }
}

function Assert-ProjectTestSummary {
    param(
        [Parameter(Mandatory)] [string] $ProjectName,
        [Parameter(Mandatory)] [int] $ReportCount,
        [Parameter(Mandatory)] $Summary
    )

    Assert-True ($ReportCount -gt 0) "$ProjectName produced no unit-test reports."
    Assert-True (($Summary.Failures + $Summary.Errors) -eq 0) "$ProjectName unit-test reports contain failures or errors."
    Assert-True (($Summary.Tests - $Summary.Skipped) -gt 0) "$ProjectName produced no non-skipped unit tests."
}

function Get-ProjectTestSummary {
    param(
        [Parameter(Mandatory)] [string] $Root,
        [Parameter(Mandatory)] [string] $ProjectName,
        [Parameter(Mandatory)] [string] $ProjectDirectory
    )

    $summary = [ordered] @{
        Project = $ProjectName
        Tests = 0
        Failures = 0
        Errors = 0
        Skipped = 0
    }
    $resultsPath = Join-Path $Root "$ProjectDirectory/build/test-results/test"
    $results = @(Get-ChildItem -LiteralPath $resultsPath -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue)
    foreach ($result in $results) {
        [xml] $xml = Get-Content -LiteralPath $result.FullName -Raw
        $summary.Tests += [int] $xml.testsuite.tests
        $summary.Failures += [int] $xml.testsuite.failures
        $summary.Errors += [int] $xml.testsuite.errors
        $summary.Skipped += [int] $xml.testsuite.skipped
    }
    $projectSummary = [pscustomobject] $summary
    Assert-ProjectTestSummary -ProjectName $ProjectName -ReportCount $results.Count -Summary $projectSummary
    return $projectSummary
}

function Get-TestSummary {
    param([Parameter(Mandatory)] [string] $Root)

    $projects = @(
        Get-ProjectTestSummary -Root $Root -ProjectName 'Fabric' -ProjectDirectory 'fabric'
        Get-ProjectTestSummary -Root $Root -ProjectName 'Momentum' -ProjectDirectory 'momentum'
    )
    return [pscustomobject] @{
        Projects = $projects
        Tests = ($projects | Measure-Object -Property Tests -Sum).Sum
        Failures = ($projects | Measure-Object -Property Failures -Sum).Sum
        Errors = ($projects | Measure-Object -Property Errors -Sum).Sum
        Skipped = ($projects | Measure-Object -Property Skipped -Sum).Sum
    }
}

$root = [System.IO.Path]::GetFullPath($RepositoryRoot)
Assert-True (Test-Path -LiteralPath (Join-Path $root 'settings.gradle.kts') -PathType Leaf) "Not an Automobility repository root: $root"
Assert-True (Test-Path -LiteralPath (Join-Path $root 'tools/qa/Test-ReleaseFacingAssets.ps1') -PathType Leaf) 'Missing release-facing QA script.'
$isCiEnvironment = $env:CI -eq 'true' -or $env:GITHUB_ACTIONS -eq 'true'
Assert-True `
    -Condition (-not ($PrepareReleaseRecord -and $isCiEnvironment)) `
    -Message '-PrepareReleaseRecord is a local bootstrap mode and is disabled in CI.'
Assert-True `
    -Condition (-not $CiBuildVerification -or $isCiEnvironment) `
    -Message '-CiBuildVerification may only be used in a CI environment.'

$properties = Read-GradleProperties -Path (Join-Path $root 'gradle.properties')
foreach ($requiredProperty in @('minecraft_version', 'fabric_version', 'fabric_api_version', 'mod_version', 'momentum_version', 'momentum_automobility_min_version', 'archives_base_name')) {
    Assert-True ($properties.ContainsKey($requiredProperty)) "Missing Gradle property: $requiredProperty"
}

$resolvedJavaHome = Resolve-Java25Home -RequestedHome $JavaHome
$javaExecutableName = if ($env:OS -eq 'Windows_NT') { 'java.exe' } else { 'java' }
$javaExecutable = Join-Path (Join-Path $resolvedJavaHome 'bin') $javaExecutableName
$originalJavaHome = $env:JAVA_HOME
$originalPath = $env:PATH

try {
    $env:JAVA_HOME = $resolvedJavaHome
    $env:PATH = (Join-Path $resolvedJavaHome 'bin') + [System.IO.Path]::PathSeparator + $originalPath

    Write-Host 'Automobility QA gate'
    Write-Host "  Repository: $root"
    Write-Host "  JDK: $resolvedJavaHome"
    Write-Host "  Java: $(& $javaExecutable -version 2>&1 | Select-Object -First 1)"
    Write-Host '  Publishing: disabled (this script has no publish or upload step)'

    Write-Host "`n[1/3] Release-facing metadata and artwork"
    & (Join-Path $root 'tools/qa/Test-ReleaseFacingAssets.ps1') -RepositoryRoot $root

    Write-Host "`n[2/3] Clean unit tests and production build"
    $gradleArguments = @('clean', 'test', 'build', '--warning-mode', 'all')
    if ($Rebuild) {
        $gradleArguments += @('--no-build-cache', '--rerun-tasks')
    }
    Invoke-Gradle -Root $root -Arguments $gradleArguments
    $testSummary = Get-TestSummary -Root $root

    Write-Host "`n[3/3] Production JAR integrity"
    $minecraftVersion = $properties.minecraft_version
    $expectedMinecraftDependency = if ($minecraftVersion -eq '26.1.2') { '26.1.x' } else { $minecraftVersion }
    $automobilityFileName = "$($properties.archives_base_name)-$($properties.mod_version)+$minecraftVersion-fabric.jar"
    $momentumFileName = "momentum-$($properties.momentum_version)+$minecraftVersion-fabric.jar"
    $automobilityOutput = Join-Path $root 'fabric/build/libs'
    $momentumOutput = Join-Path $root 'momentum/build/libs'

    $automobilityJars = @(Get-ChildItem -LiteralPath $automobilityOutput -Filter '*.jar' -File)
    $momentumJars = @(Get-ChildItem -LiteralPath $momentumOutput -Filter '*.jar' -File)
    Assert-True ($automobilityJars.Count -eq 1) "Expected exactly one Fabric production JAR, found $($automobilityJars.Count)."
    Assert-True ($momentumJars.Count -eq 1) "Expected exactly one Momentum production JAR, found $($momentumJars.Count)."
    Assert-True ($automobilityJars[0].Name -ceq $automobilityFileName) "Unexpected Automobility artifact name '$($automobilityJars[0].Name)'."
    Assert-True ($momentumJars[0].Name -ceq $momentumFileName) "Unexpected Momentum artifact name '$($momentumJars[0].Name)'."

    $automobilityArtifact = Assert-FabricJar `
        -Path $automobilityJars[0].FullName `
        -ExpectedId 'automobility' `
        -ExpectedVersion $properties.mod_version `
        -ExpectedMinecraftVersion $expectedMinecraftDependency `
        -Properties $properties `
        -Root $root
    $momentumArtifact = Assert-FabricJar `
        -Path $momentumJars[0].FullName `
        -ExpectedId 'momentum' `
        -ExpectedVersion "$($properties.momentum_version)+$minecraftVersion-fabric" `
        -ExpectedMinecraftVersion $expectedMinecraftDependency `
        -Properties $properties `
        -Root $root

    $automobilityReleaseRecordPath = Join-Path $root "MODRINTH_RELEASE_$minecraftVersion.md"
    $momentumReleaseRecordPath = Join-Path $root "MOMENTUM_MODRINTH_RELEASE_$minecraftVersion.md"
    if ($PrepareReleaseRecord) {
        Write-Warning 'NON-FINAL MODE: the Automobility and Momentum release-record sizes and hashes were not verified.'
    } else {
        $automobilityReleaseRecord = Read-ReleaseArtifactRecord -Path $automobilityReleaseRecordPath -RequireSha512 -RequireLocalJar
        $momentumReleaseRecord = Read-ReleaseArtifactRecord -Path $momentumReleaseRecordPath -RequireSha512 -RequireLocalJar
        Assert-ArtifactMatchesReleaseRecord -Artifact $automobilityArtifact -Record $automobilityReleaseRecord
        Assert-ArtifactMatchesReleaseRecord -Artifact $momentumArtifact -Record $momentumReleaseRecord
        $automobilityLocalJarPath = Resolve-ReleaseLocalJarPath -Root $root -Record $automobilityReleaseRecord -AllowMissing:$CiBuildVerification
        $momentumLocalJarPath = Resolve-ReleaseLocalJarPath -Root $root -Record $momentumReleaseRecord -AllowMissing:$CiBuildVerification
        if (-not $CiBuildVerification) {
            $automobilityLocalArtifact = Get-FileArtifactIdentity -Path $automobilityLocalJarPath
            $momentumLocalArtifact = Get-FileArtifactIdentity -Path $momentumLocalJarPath
            Assert-ArtifactMatchesReleaseRecord -Artifact $automobilityLocalArtifact -Record $automobilityReleaseRecord
            Assert-ArtifactMatchesReleaseRecord -Artifact $momentumLocalArtifact -Record $momentumReleaseRecord
            Assert-ArtifactsMatch -BuiltArtifact $automobilityArtifact -LocalArtifact $automobilityLocalArtifact
            Assert-ArtifactsMatch -BuiltArtifact $momentumArtifact -LocalArtifact $momentumLocalArtifact
        }
    }

    if ($PrepareReleaseRecord) {
        Write-Host "`nPreparation checks passed; this build is NOT release-ready until the default gate verifies the release record."
    } else {
        if ($CiBuildVerification) {
            Write-Host "`nCI build verification passed."
            Write-Host '  Fresh Automobility and Momentum builds match their committed size, SHA-256 and SHA-512 records.'
            Write-Host '  Ignored local staging JARs were intentionally not required; run the default gate locally before upload.'
        } else {
            Write-Host "`nQA passed."
            Write-Host '  Automobility release record and Local JAR: exact path, size, SHA-256 and SHA-512 verified against the build'
            Write-Host '  Momentum release record and Local JAR: exact path, size, SHA-256 and SHA-512 verified against the build'
        }
    }
    Write-Host "  Unit tests: $($testSummary.Tests - $testSummary.Skipped) passed, $($testSummary.Failures + $testSummary.Errors) failed, $($testSummary.Skipped) skipped"
    foreach ($projectSummary in $testSummary.Projects) {
        Write-Host "    $($projectSummary.Project): $($projectSummary.Tests - $projectSummary.Skipped) passed, $($projectSummary.Failures + $projectSummary.Errors) failed, $($projectSummary.Skipped) skipped"
    }
    foreach ($artifact in @($automobilityArtifact, $momentumArtifact)) {
        Write-Host "  $($artifact.Name): $($artifact.Size) bytes"
        Write-Host "    SHA-256 $($artifact.Sha256)"
        Write-Host "    SHA-512 $($artifact.Sha512)"
    }
    Write-Host '  JAR checks: metadata, referenced resources/entrypoints, exact project and Obj licenses + NOTICE.md, duplicate/unsafe/development files'
    Write-Host '  Runtime: not automated; full-stack dedicated-server and two-client driving/persistence tests remain release-candidate gates'
} finally {
    if ($null -eq $originalJavaHome) {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
    } else {
        $env:JAVA_HOME = $originalJavaHome
    }
    $env:PATH = $originalPath
}
