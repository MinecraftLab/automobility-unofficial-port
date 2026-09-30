#requires -Version 7.2
Set-StrictMode -Version Latest

function Assert-EcosystemSourcesManifest {
    param([Parameter(Mandatory)] $Manifest, [Parameter(Mandatory)] $Matrix)
    $official = 'https://git.offchunk.com/Offchunk/automobility-unofficial-port.git'
    if ($Manifest.schemaVersion -ne 1 -or $Manifest.repository -cne $official) {
        throw 'Unsupported source manifest or non-official repository.'
    }
    $refs = @{
        '26.1.2' = 'refs/heads/release/rosenvik-auto36-26.1.2'
        '26.2' = 'refs/heads/release/rosenvik-auto36-26.2'
        '26.3' = 'refs/heads/port/26.3'
    }
    if ((@($Manifest.targets.minecraft | Sort-Object) -join ',') -cne '26.1.2,26.2,26.3' -or
        (@($Matrix.targets.minecraft | Sort-Object) -join ',') -cne '26.1.2,26.2,26.3') {
        throw 'Both manifests must contain all three Minecraft targets exactly once.'
    }
    foreach ($target in $Manifest.targets) {
        if ($target.ref -cne $refs[$target.minecraft]) { throw "Unexpected ref for $($target.minecraft)." }
        if ($target.momentumAutomobilityMinVersion -cnotmatch '^\d+\.\d+\.\d+-unofficial\.\d+$') {
            throw "Invalid reviewed Momentum core minimum for $($target.minecraft)."
        }
    }
    $required = @(
        'tools/qa/ecosystem-matrix.json', 'tools/qa/EcosystemParity.psm1',
        'tools/qa/Test-EcosystemParity.ps1', 'tools/qa/Test-EcosystemParityUnit.ps1',
        'docs/release/ECOSYSTEM_PARITY.md', 'tools/qa/ecosystem-sources.json',
        'tools/qa/EcosystemSources.psm1', 'tools/qa/Test-EcosystemSources.ps1',
        'tools/qa/Test-EcosystemSourcesUnit.ps1', 'tools/qa/ecosystem-public-artifacts.json',
        'tools/qa/EcosystemPublicArtifacts.psm1', 'tools/qa/Test-EcosystemPublicRelease.ps1',
        'tools/qa/Test-EcosystemPublicArtifactsUnit.ps1', 'tools/qa/Invoke-EcosystemCi.ps1',
        'tools/qa/EcosystemCi.psm1', 'tools/qa/Test-EcosystemCiUnit.ps1',
        '.forgejo/workflows/ecosystem.yml'
    )
    if ((@($Manifest.sharedFiles | Sort-Object) -join '|') -cne (@($required | Sort-Object) -join '|')) {
        throw 'Shared policy coverage must include every required file exactly once.'
    }
}

function Invoke-EcosystemProcess {
    param(
        [Parameter(Mandatory)] [string] $Executable,
        [Parameter(Mandatory)] [string[]] $Arguments,
        [Parameter(Mandatory)] [string] $WorkingDirectory,
        [hashtable] $Environment = @{},
        [string] $LogPath
    )
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $Executable
    $info.WorkingDirectory = $WorkingDirectory
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    foreach ($argument in $Arguments) { $info.ArgumentList.Add($argument) }
    foreach ($key in $Environment.Keys) {
        if ($null -eq $Environment[$key]) { $info.Environment.Remove($key) | Out-Null }
        else { $info.Environment[$key] = [string]$Environment[$key] }
    }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    try {
        if (-not $process.Start()) { throw "Could not start $Executable." }
        # Read both streams concurrently so a verbose Gradle child cannot deadlock.
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        $result = [pscustomobject]@{ exitCode=$process.ExitCode; stdout=$stdout; stderr=$stderr }
        if ($LogPath) {
            Write-EcosystemNewText -Path $LogPath -Text ($stdout + "`n--- stderr ---`n" + $stderr)
        }
        return $result
    } finally { $process.Dispose() }
}

function Invoke-EcosystemGit {
    param([Parameter(Mandatory)] [string] $Root, [Parameter(Mandatory)] [string[]] $Arguments,
        [switch] $AllowFileTransport)
    $git = (Get-Command git -ErrorAction Stop).Source
    $environment = @{
        GIT_TERMINAL_PROMPT='0'; GIT_CONFIG_NOSYSTEM='1'
        GIT_CONFIG_GLOBAL=$(if ($IsWindows) { 'NUL' } else { '/dev/null' })
        GIT_CONFIG_COUNT='0'; GIT_CONFIG_PARAMETERS=$null; GIT_DIR=$null
        GIT_WORK_TREE=$null; GIT_INDEX_FILE=$null; GIT_OBJECT_DIRECTORY=$null
        GIT_ALTERNATE_OBJECT_DIRECTORIES=$null; GIT_ASKPASS=$null
    }
    $filePolicy = if ($AllowFileTransport) { 'always' } else { 'never' }
    $prefix = @('-c','credential.helper=', '-c','credential.interactive=false',
        '-c','core.askPass=', '-c','core.autocrlf=false', '-c','core.hooksPath=',
        '-c','http.extraHeader=', '-c','http.followRedirects=false', '-c',"protocol.file.allow=$filePolicy")
    $result = Invoke-EcosystemProcess -Executable $git -Arguments ($prefix + $Arguments) -WorkingDirectory $Root -Environment $environment
    if ($result.exitCode -ne 0) { throw "Git failed (exit $($result.exitCode)): $($result.stderr.Trim())" }
    return $result.stdout.TrimEnd("`r", "`n")
}

function Write-EcosystemNewText {
    param([Parameter(Mandatory)] [string] $Path, [Parameter(Mandatory)] [AllowEmptyString()] [string] $Text)
    $stream = [IO.File]::Open([IO.Path]::GetFullPath($Path), [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    $writer = [IO.StreamWriter]::new($stream, [Text.UTF8Encoding]::new($false))
    try { $writer.Write($Text) } finally { $writer.Dispose() }
}

function Get-EcosystemCheckoutState {
    param([Parameter(Mandatory)] [string] $Root)
    $commit = Invoke-EcosystemGit -Root $Root -Arguments @('rev-parse','--verify','HEAD')
    $status = Invoke-EcosystemGit -Root $Root -Arguments @('status','--porcelain=v1','--untracked-files=normal')
    return [pscustomobject]@{ commit=$commit; clean=[string]::IsNullOrEmpty($status); status=$status }
}

function Assert-EcosystemCheckoutState {
    param([Parameter(Mandatory)] $State, [Parameter(Mandatory)] [string] $ExpectedCommit)
    if ($ExpectedCommit -cnotmatch '^[0-9a-f]{40}$' -or $State.commit -cne $ExpectedCommit -or -not $State.clean) {
        throw 'Checkout commit changed or checkout is not clean; source evidence is invalid.'
    }
}

function Get-EcosystemPolicyIdentity {
    param([Parameter(Mandatory)] [string] $Root, [Parameter(Mandatory)] [string[]] $Files)
    $identities = [ordered]@{}
    foreach ($file in $Files) {
        # Committed blob identities compare exact Git bytes, unaffected by Windows CRLF checkout conversion.
        $tree = Invoke-EcosystemGit -Root $Root -Arguments @('ls-tree','HEAD','--',$file)
        if ($tree -cnotmatch '^(100644|100755) blob ([0-9a-f]{40})\t') { throw "Missing or non-regular policy file: $file" }
        $blob = $Matches[2]
        $item = Get-Item -LiteralPath (Join-Path $Root $file) -Force
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Policy symlink is not allowed: $file" }
        $identities[$file] = $blob
    }
    return $identities
}

function Assert-EcosystemPolicyIdentity {
    param([Parameter(Mandatory)] $Expected, [Parameter(Mandatory)] $Actual)
    if ($Expected.Count -ne $Actual.Count) { throw 'Shared policy file count differs.' }
    foreach ($file in $Expected.Keys) {
        if (-not $Actual.Contains($file) -or $Actual[$file] -cne $Expected[$file]) { throw "Shared policy drift: $file" }
    }
}

function Assert-EcosystemSourceProperties {
    param([Parameter(Mandatory)] [string] $Path, [Parameter(Mandatory)] $Target,
        [Parameter(Mandatory)] [string] $ExpectedMomentumCoreMinimum)
    $properties = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*([^#!\s=]+)\s*=\s*(.*?)\s*$') {
            if ($properties.ContainsKey($Matches[1])) { throw "Duplicate Gradle property: $($Matches[1])" }
            $properties[$Matches[1]] = $Matches[2]
        }
    }
    $expected = @{ minecraft_version=$Target.minecraft; mod_version=$Target.automobility;
        momentum_version=$Target.momentum; momentum_automobility_min_version=$ExpectedMomentumCoreMinimum }
    foreach ($key in $expected.Keys) {
        if (-not $properties.ContainsKey($key) -or $properties[$key] -cne $expected[$key]) {
            throw "Source property $key does not match the matrix for $($Target.minecraft)."
        }
    }
}

function Assert-EcosystemBuildResult {
    param([Parameter(Mandatory)] [int] $ExitCode)
    if ($ExitCode -ne 0) { throw "Source build gate failed (exit $ExitCode); no source-build success may be recorded." }
}

Export-ModuleMember -Function Assert-EcosystemSourcesManifest, Invoke-EcosystemProcess,
    Invoke-EcosystemGit, Write-EcosystemNewText, Get-EcosystemCheckoutState,
    Assert-EcosystemCheckoutState, Get-EcosystemPolicyIdentity, Assert-EcosystemPolicyIdentity,
    Assert-EcosystemSourceProperties, Assert-EcosystemBuildResult
