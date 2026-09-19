#requires -Version 7.2
<#
.SYNOPSIS
Prepares an isolated, packaged-JAR Minecraft 26.3 Fabric client; never launches unless -Launch is explicit.
.EXAMPLE
pwsh -File .\tools\qa\Start-Client263Qa.ps1 -CacheOnly
.EXAMPLE
& .\tools\qa\Start-Client263Qa.ps1 -RunId base-001 -ModJarPaths @('C:\path\fabric-api.jar', 'C:\path\automobility.jar')
.EXAMPLE
& .\tools\qa\Start-Client263Qa.ps1 -RunId base-001 -Launch
#>
[CmdletBinding()]
param(
    [ValidatePattern('^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$')][string]$RunId,
    [string[]]$ModJarPaths = @(),
    [switch]$PrepareOnly,
    [switch]$CacheOnly,
    [switch]$Launch,
    [string]$QuickPlayMultiplayer,
    [string]$JavaHome = 'C:\Users\david\.jdks\temurin-25-20260619175454\jdk-25.0.3+9',
    [string]$MinecraftCache = 'C:\Users\david\AppData\Roaming\.minecraft',
    [string]$MojangMetadata = 'C:\Users\david\.gradle\caches\fabric-loom\26.3\mojang_minecraft_info.json',
    [string]$CachedClientJar = 'C:\Users\david\.gradle\caches\fabric-loom\26.3\minecraft-client.jar'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $IsWindows -or [Runtime.InteropServices.RuntimeInformation]::OSArchitecture -ne [Runtime.InteropServices.Architecture]::X64) {
    throw 'This isolated launcher is intended for Windows x64 only.'
}
if ($Launch -and ($PrepareOnly -or $CacheOnly)) { throw '-Launch cannot be combined with a prepare-only mode.' }
if (-not $CacheOnly -and [string]::IsNullOrWhiteSpace($RunId)) { throw '-RunId is required, except with -CacheOnly.' }
if ($QuickPlayMultiplayer -and $QuickPlayMultiplayer -notmatch '^(127\.0\.0\.1|localhost):[0-9]{1,5}$') {
    throw 'This offline QA launcher only supports loopback multiplayer targets.'
}
$qaRoot = 'C:\Users\david\Automobility-26.3-Client-QA'
$cacheRoot = Join-Path $qaRoot 'runtime-cache'
$assetsRoot = Join-Path $cacheRoot 'assets'
$librariesRoot = Join-Path $cacheRoot 'libraries'
$javaExe = Join-Path $JavaHome 'bin\javaw.exe'
if (-not (Test-Path -LiteralPath $javaExe -PathType Leaf)) { throw "Java runtime missing: $javaExe" }
$javaRelease = Get-Content -LiteralPath (Join-Path $JavaHome 'release') -Raw
if ($javaRelease -notmatch '(?m)^JAVA_VERSION="25(?:\.|"|\+)') { throw 'Minecraft 26.3 requires Java 25.' }
New-Item -ItemType Directory -Path $cacheRoot -Force | Out-Null

function Get-SafeCachePath([string]$Root, [string]$Relative) {
    $prefix = [IO.Path]::GetFullPath($Root).TrimEnd('\') + '\'
    $candidate = [IO.Path]::GetFullPath((Join-Path $Root $Relative.Replace('/', '\')))
    if (-not $candidate.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { throw "Unsafe artifact path: $Relative" }
    return $candidate
}
function Test-Artifact([string]$Path, [string]$Sha1, [long]$Size = -1) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $false }
    if ($Size -ge 0 -and (Get-Item -LiteralPath $Path).Length -ne $Size) { return $false }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA1).Hash -eq $Sha1
}
function Get-VerifiedArtifact([string]$Destination, [string]$Url, [string]$Sha1, [long]$Size = -1, [string[]]$Sources = @()) {
    if ($Sha1 -notmatch '^[a-fA-F0-9]{40}$') { throw "Missing or invalid official SHA-1 for $Url" }
    $uri = [Uri]$Url
    if ($uri.Scheme -ne 'https' -or $uri.Host -notin @('piston-data.mojang.com', 'piston-meta.mojang.com', 'libraries.minecraft.net', 'resources.download.minecraft.net', 'maven.fabricmc.net')) {
        throw "Unexpected artifact host: $Url"
    }
    if (Test-Artifact $Destination $Sha1 $Size) { return $Destination }
    if (Test-Path -LiteralPath $Destination) { throw "Existing QA cache artifact failed verification: $Destination" }
    New-Item -ItemType Directory -Path (Split-Path -Parent $Destination) -Force | Out-Null
    $temporary = "$Destination.$([Guid]::NewGuid().ToString('N')).download"
    $cached = $Sources | Where-Object { $_ -and (Test-Artifact $_ $Sha1 $Size) } | Select-Object -First 1
    if ($cached) { Copy-Item -LiteralPath $cached -Destination $temporary }
    else { Invoke-WebRequest -Uri $Url -OutFile $temporary -MaximumRetryCount 3 -RetryIntervalSec 2 }
    if (-not (Test-Artifact $temporary $Sha1 $Size)) { throw "Downloaded artifact failed verification: $temporary" }
    Move-Item -LiteralPath $temporary -Destination $Destination
    return $Destination
}
function Test-WindowsLibrary($Library) {
    # Mojang's rules mark both Windows architectures alike. This launcher is explicitly x64.
    if ([string]$Library.name -match '(natives-windows-arm64|natives-windows-x86|linux|osx|natives-macos)') { return $false }
    if (-not $Library.PSObject.Properties['rules']) { return $true }
    $allowed = $false
    foreach ($rule in $Library.rules) {
        $matchesRule = $true
        if ($rule.PSObject.Properties['features']) { $matchesRule = $false }
        if ($rule.PSObject.Properties['os']) {
            if ($rule.os.PSObject.Properties['name'] -and $rule.os.name -ne 'windows') { $matchesRule = $false }
            if ($rule.os.PSObject.Properties['arch'] -and $rule.os.arch -notin @('amd64', 'x86_64')) { $matchesRule = $false }
            if ($rule.os.PSObject.Properties['version'] -and [Environment]::OSVersion.Version.ToString() -notmatch $rule.os.version) { $matchesRule = $false }
        }
        if ($matchesRule) { $allowed = $rule.action -eq 'allow' }
    }
    return $allowed
}
function Get-JarMetadata([string]$Path) {
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $entry = $zip.GetEntry('fabric.mod.json')
        if ($null -eq $entry) { throw "Not a packaged Fabric mod: $Path" }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { return $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
    } finally { $zip.Dispose() }
}
function Get-FileRecord([string]$Path) {
    return [ordered]@{ path = $Path; bytes = (Get-Item -LiteralPath $Path).Length; sha256 = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash }
}

$minecraft = Get-Content -LiteralPath $MojangMetadata -Raw | ConvertFrom-Json
if ($minecraft.id -ne '26.3' -or $minecraft.javaVersion.majorVersion -ne 25) { throw 'Unexpected Minecraft metadata.' }
$metadataPath = Join-Path $cacheRoot 'mojang-26.3.json'
if (-not (Test-Path -LiteralPath $metadataPath)) { Copy-Item -LiteralPath $MojangMetadata -Destination $metadataPath }
elseif ((Get-FileHash -LiteralPath $metadataPath).Hash -ne (Get-FileHash -LiteralPath $MojangMetadata).Hash) { throw 'Cached Mojang metadata changed; inspect before proceeding.' }
$profilePath = Join-Path $cacheRoot 'fabric-loader-0.19.5-26.3.json'
if (-not (Test-Path -LiteralPath $profilePath)) {
    Invoke-WebRequest -Uri 'https://meta.fabricmc.net/v2/versions/loader/26.3/0.19.5/profile/json' -OutFile $profilePath
}
$fabric = Get-Content -LiteralPath $profilePath -Raw | ConvertFrom-Json
if ($fabric.inheritsFrom -ne '26.3' -or $fabric.mainClass -ne 'net.fabricmc.loader.impl.launch.knot.KnotClient') { throw 'Unexpected Fabric profile.' }
$client = $minecraft.downloads.client
$clientPath = Get-VerifiedArtifact (Join-Path $cacheRoot 'minecraft-client-26.3.jar') $client.url $client.sha1 $client.size @($CachedClientJar)
$libraryPaths = [Collections.Generic.List[string]]::new()
$nativeJars = [Collections.Generic.List[string]]::new()
$libraryKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($library in @($fabric.libraries) + @($minecraft.libraries)) {
    if (-not (Test-WindowsLibrary $library)) { continue }
    $parts = ([string]$library.name).Split(':')
    $key = $parts[0] + ':' + $parts[1] + $(if ($parts.Count -gt 3) { ':' + $parts[3] } else { '' })
    if (-not $libraryKeys.Add($key)) { continue }
    if ($library.PSObject.Properties['downloads']) {
        $artifact = $library.downloads.artifact
        $relative = [string]$artifact.path; $url = [string]$artifact.url; $sha1 = [string]$artifact.sha1; $size = [long]$artifact.size
    } else {
        $relative = $parts[0].Replace('.', '/') + '/' + $parts[1] + '/' + $parts[2] + '/' + $parts[1] + '-' + $parts[2] + '.jar'
        $url = ([string]$library.url).TrimEnd('/') + '/' + $relative
        $sha1 = if ($library.PSObject.Properties['sha1']) { [string]$library.sha1 } else { ([string](Invoke-RestMethod -Uri "$url.sha1")).Trim() }
        $size = if ($library.PSObject.Properties['size']) { [long]$library.size } else { -1 }
    }
    $path = Get-VerifiedArtifact (Get-SafeCachePath $librariesRoot $relative) $url $sha1 $size @((Get-SafeCachePath (Join-Path $MinecraftCache 'libraries') $relative))
    $libraryPaths.Add($path)
    if ([string]$library.name -match ':natives-windows$') { $nativeJars.Add($path) }
}
$index = $minecraft.assetIndex
$indexPath = Get-VerifiedArtifact (Join-Path $assetsRoot "indexes\$($index.id).json") $index.url $index.sha1 $index.size @((Join-Path $MinecraftCache "assets\indexes\$($index.id).json"))
$assetObjects = (Get-Content -LiteralPath $indexPath -Raw | ConvertFrom-Json).objects
$uniqueAssets = @($assetObjects.PSObject.Properties.Value | Group-Object hash | ForEach-Object { $_.Group[0] })
Write-Host "Verifying $($uniqueAssets.Count) unique asset objects (official index $($index.id))..."
$assetResults = @($uniqueAssets | ForEach-Object -ThrottleLimit 12 -Parallel {
    $ErrorActionPreference = 'Stop'
    $hash = [string]$_.hash; $size = [long]$_.size
    if ($hash -notmatch '^[a-f0-9]{40}$') { throw "Invalid asset SHA-1: $hash" }
    $relative = 'objects\' + $hash.Substring(0, 2) + '\' + $hash
    $destination = Join-Path $using:assetsRoot $relative
    $source = Join-Path (Join-Path $using:MinecraftCache 'assets') $relative
    if (Test-Path -LiteralPath $destination) {
        if ((Get-Item -LiteralPath $destination).Length -ne $size -or (Get-FileHash -LiteralPath $destination -Algorithm SHA1).Hash -ne $hash) { throw "Cached asset corrupted: $destination" }
        return 'verified'
    }
    New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
    $temp = "$destination.$([Guid]::NewGuid().ToString('N')).download"
    $reused = (Test-Path -LiteralPath $source) -and (Get-Item -LiteralPath $source).Length -eq $size -and (Get-FileHash -LiteralPath $source -Algorithm SHA1).Hash -eq $hash
    if ($reused) { Copy-Item -LiteralPath $source -Destination $temp }
    else { Invoke-WebRequest -Uri "https://resources.download.minecraft.net/$($hash.Substring(0, 2))/$hash" -OutFile $temp -MaximumRetryCount 3 -RetryIntervalSec 2 }
    if ((Get-Item -LiteralPath $temp).Length -ne $size -or (Get-FileHash -LiteralPath $temp -Algorithm SHA1).Hash -ne $hash) { throw "Asset download failed hash verification: $temp" }
    Move-Item -LiteralPath $temp -Destination $destination
    if ($reused) { 'copied' } else { 'downloaded' }
})
if ($assetResults.Count -ne $uniqueAssets.Count) { throw 'Asset verification was incomplete.' }
$logging = $minecraft.logging.client.file
$loggingPath = Get-VerifiedArtifact (Join-Path $assetsRoot "log_configs\$($logging.id)") $logging.url $logging.sha1 $logging.size @((Join-Path $MinecraftCache "assets\log_configs\$($logging.id)"))
$runtimeRecords = @(@($metadataPath, $profilePath, $clientPath, $indexPath, $loggingPath, $javaExe) + @($libraryPaths) | ForEach-Object { Get-FileRecord $_ })
$runtimeManifest = [ordered]@{ schemaVersion = 1; verifiedAtUtc = [DateTime]::UtcNow.ToString('o'); minecraft = '26.3'; fabricLoader = '0.19.5'; assets = [ordered]@{ index = '34'; indexSha1 = $index.sha1; uniqueCount = $uniqueAssets.Count; counts = @($assetResults | Group-Object | Select-Object Name, Count) }; files = $runtimeRecords }
$runtimeManifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $cacheRoot 'runtime-manifest.json') -Encoding utf8
Write-Host "Runtime cache verified: $cacheRoot ($($libraryPaths.Count) libraries, $($nativeJars.Count) native JARs)."
if ($CacheOnly) { return }

$runRoot = Join-Path $qaRoot "runs\$RunId"
$manifestPath = Join-Path $runRoot 'launch-manifest.json'
if (Test-Path -LiteralPath $runRoot) {
    if (-not $Launch) { throw "Run already exists; choose a new RunId. Explicit -Launch may resume an unchanged prepared run: $runRoot" }
    if ($ModJarPaths.Count -gt 0) { throw 'An existing run cannot accept replacement mods; choose a new RunId.' }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($manifest.runDirectory -ne $runRoot) { throw 'Run manifest path mismatch.' }
    if ($QuickPlayMultiplayer) { throw 'QuickPlayMultiplayer must be chosen when preparing a new run.' }
    foreach ($record in @($manifest.runtimeFiles) + @($manifest.mods) + @($manifest.nativeFiles)) {
        if (-not (Test-Path -LiteralPath $record.path -PathType Leaf) -or (Get-FileHash -LiteralPath $record.path).Hash -ne $record.sha256) { throw "Prepared run changed: $($record.path)" }
    }
    if (@(Get-ChildItem -LiteralPath (Join-Path $runRoot 'mods') -Filter '*.jar' -File -Recurse).Count -ne @($manifest.mods).Count) { throw 'Unexpected additional mods in prepared run.' }
} else {
    if ($ModJarPaths.Count -eq 0) { throw 'New runs require an explicit nonempty -ModJarPaths list (include Fabric API).' }
    $modRecords = [Collections.Generic.List[object]]::new()
    $modNames = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $modIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($source in $ModJarPaths) {
        $item = Get-Item -LiteralPath $source
        if ($item.Extension -ne '.jar') { throw "Not a JAR: $source" }
        $metadata = Get-JarMetadata $item.FullName
        if (-not $modNames.Add($item.Name) -or -not $modIds.Add([string]$metadata.id)) { throw "Duplicate mod name or ID: $source" }
        $record = Get-FileRecord $item.FullName
        $record.source = $item.FullName; $record.path = Join-Path $runRoot "mods\$($item.Name)"; $record.id = $metadata.id; $record.version = $metadata.version
        $modRecords.Add($record)
    }
    New-Item -ItemType Directory -Path (Join-Path $runRoot 'mods') -Force | Out-Null
    foreach ($record in $modRecords) {
        Copy-Item -LiteralPath $record.source -Destination $record.path
        if ((Get-FileHash -LiteralPath $record.path).Hash -ne $record.sha256) { throw "Mod copy mismatch: $($record.path)" }
    }
    $nativeRoot = Join-Path $runRoot '.natives'
    New-Item -ItemType Directory -Path $nativeRoot -Force | Out-Null
    foreach ($nativeJar in $nativeJars) {
        $zip = [IO.Compression.ZipFile]::OpenRead($nativeJar)
        try {
            foreach ($entry in $zip.Entries) {
                if (-not $entry.Name.EndsWith('.dll', [StringComparison]::OrdinalIgnoreCase)) { continue }
                if ($entry.Name -notmatch '^[a-zA-Z0-9_.-]+\.dll$') { throw "Unsafe native filename: $($entry.FullName)" }
                $target = Get-SafeCachePath $nativeRoot $entry.Name
                if (Test-Path -LiteralPath $target) { throw "Conflicting native DLL: $target" }
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target)
            }
        } finally { $zip.Dispose() }
    }
    $nativeFiles = @(Get-ChildItem -LiteralPath $nativeRoot -Filter '*.dll' -File | ForEach-Object { Get-FileRecord $_.FullName })
    if ($nativeFiles.Count -eq 0 -or -not (Test-Path -LiteralPath (Join-Path $nativeRoot 'SDL3.dll'))) { throw 'Expected x64 SDL native DLLs were not extracted.' }
    $username = 'Auto263QA'
    $uuidBytes = [Security.Cryptography.MD5]::HashData([Text.Encoding]::UTF8.GetBytes("OfflinePlayer:$username"))
    $uuidBytes[6] = ($uuidBytes[6] -band 15) -bor 48; $uuidBytes[8] = ($uuidBytes[8] -band 63) -bor 128
    $offlineUuid = [Convert]::ToHexString($uuidBytes).ToLowerInvariant()
    $arguments = @('-Xms512m', '-Xmx4G', '-XX:StackShadowPages=32', '--sun-misc-unsafe-memory-access=allow', '--enable-native-access=ALL-UNNAMED', '--add-exports', 'java.base/jdk.internal.misc=ALL-UNNAMED', "-Djava.library.path=$nativeRoot", "-Djna.tmpdir=$nativeRoot", "-Dorg.lwjgl.system.SharedLibraryExtractPath=$nativeRoot", "-Dio.netty.native.workdir=$nativeRoot", '-Dminecraft.launcher.brand=automobility-packaged-qa', '-Dminecraft.launcher.version=1', "-Dlog4j.configurationFile=$loggingPath", '-DFabricMcEmu=net.minecraft.client.main.Main', '-cp', ((@($libraryPaths) + @($clientPath)) -join [IO.Path]::PathSeparator), [string]$fabric.mainClass, '--username', $username, '--version', [string]$fabric.id, '--gameDir', $runRoot, '--assetsDir', $assetsRoot, '--assetIndex', [string]$index.id, '--uuid', $offlineUuid, '--accessToken', '0', '--clientId', '0', '--xuid', '0', '--versionType', 'isolated-offline-qa', '--width', '1280', '--height', '720')
    if ($QuickPlayMultiplayer) { $arguments += @('--quickPlayMultiplayer', $QuickPlayMultiplayer) }
    $manifest = [ordered]@{ schemaVersion = 1; preparedAtUtc = [DateTime]::UtcNow.ToString('o'); minecraft = '26.3'; fabricLoader = '0.19.5'; runDirectory = $runRoot; username = $username; offlineUuid = $offlineUuid; javaExecutable = $javaExe; quickPlayMultiplayer = $QuickPlayMultiplayer; runtimeFiles = $runtimeRecords; mods = @($modRecords); nativeFiles = $nativeFiles; arguments = $arguments }
    $manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $manifestPath -Encoding utf8
}
Write-Host "Packaged-JAR QA run prepared: $runRoot"
Write-Host "Exact SHA-256 manifest: $manifestPath"
if (-not $Launch) { Write-Host 'Client not launched. Re-run with the same RunId and -Launch, omitting ModJarPaths.'; return }
$existingClients = @(Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains($runRoot, [StringComparison]::OrdinalIgnoreCase) })
if ($existingClients.Count -gt 0) { throw "This QA run is already running: $($existingClients.ProcessId -join ', ')" }
$startInfo = [Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $manifest.javaExecutable
$startInfo.WorkingDirectory = $runRoot
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
foreach ($argument in $manifest.arguments) { $startInfo.ArgumentList.Add([string]$argument) }
$process = [Diagnostics.Process]::Start($startInfo)
if ($null -eq $process) { throw 'Minecraft process did not start.' }
[ordered]@{ launchedAtUtc = [DateTime]::UtcNow.ToString('o'); processId = $process.Id; launchManifestSha256 = (Get-FileHash -LiteralPath $manifestPath).Hash } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runRoot "launch-$($process.Id).json") -Encoding utf8
Write-Host "Minecraft client started: PID $($process.Id)."
