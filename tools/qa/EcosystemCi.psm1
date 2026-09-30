#requires -Version 7.2
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'EcosystemPublicArtifacts.psm1') -Force

function Assert-EcosystemCiSourceReceipt {
    param([Parameter(Mandatory)] $Source, [Parameter(Mandatory)] $Matrix)
    if ($Source.schemaVersion -ne 1 -or $Source.status -cne 'SOURCE_BUILDS_PASSED' -or
        $Source.mode -cne 'source-and-build' -or $Source.repository -cne 'https://git.offchunk.com/Offchunk/automobility-unofficial-port.git' -or
        $Source.fixtureMode -isnot [bool] -or $Source.fixtureMode -or
        $Source.sourceValidated -isnot [bool] -or !$Source.sourceValidated -or
        $Source.allBuildsPassed -isnot [bool] -or !$Source.allBuildsPassed -or
        $Source.error -or $Source.controllerCommit -cnotmatch '^[0-9a-f]{40}$' -or @($Source.targets).Count -ne 3) {
        throw 'Source receipt must prove all three official non-fixture source builds, not merely contain a success label.'
    }
    $refs = @{'26.1.2'='refs/heads/release/rosenvik-auto36-26.1.2'; '26.2'='refs/heads/release/rosenvik-auto36-26.2'; '26.3'='refs/heads/port/26.3'}
    foreach ($target in $Matrix.targets) {
        $rows = @($Source.targets | Where-Object minecraft -CEQ $target.minecraft)
        if ($rows.Count -ne 1) { throw 'Source receipt has a missing or duplicate target.' }
        $row = $rows[0]
        if ($row.ref -cne $refs[$target.minecraft] -or $row.commit -cnotmatch '^[0-9a-f]{40}$' -or
            $row.sourceValidated -isnot [bool] -or !$row.sourceValidated -or
            $row.buildAttempted -isnot [bool] -or !$row.buildAttempted -or
            $row.buildPassed -isnot [bool] -or !$row.buildPassed -or
            ($row.buildExitCode -isnot [int] -and $row.buildExitCode -isnot [long]) -or $row.buildExitCode -ne 0 -or
            @($row.artifacts).Count -ne 2) { throw 'Every source target must have one successful exit-zero build and exactly two artifacts.' }
        foreach ($state in @($row.before,$row.after)) {
            if ($null -eq $state -or $state.commit -cne $row.commit -or $state.clean -isnot [bool] -or !$state.clean -or $state.status -cne '') {
                throw 'Source receipt must preserve the same clean pinned commit before and after each build.'
            }
        }
        foreach ($project in @('automobility','momentum')) {
            $folder = if ($project -ceq 'automobility') { 'fabric' } else { 'momentum' }
            $path = "$folder/build/libs/$project-$($target.$project)+$($target.minecraft)-fabric.jar"
            $artifacts = @($row.artifacts | Where-Object path -CEQ $path)
            if ($artifacts.Count -ne 1) { throw 'Missing, duplicate or unexpected source artifact path.' }
            $artifact = $artifacts[0]
            if (($artifact.size -isnot [int] -and $artifact.size -isnot [long]) -or $artifact.size -le 0 -or
                $artifact.sha256 -notmatch '^[A-Fa-f0-9]{64}$' -or $artifact.sha512 -notmatch '^[A-Fa-f0-9]{128}$') {
                throw 'Source artifact has invalid byte identity evidence.'
            }
        }
    }
}

function Get-EcosystemCiArtifactLinks {
    param([Parameter(Mandatory)] $Source, [Parameter(Mandatory)] $Public,
        [Parameter(Mandatory)] $Matrix, [Parameter(Mandatory)] $Manifest)
    Assert-EcosystemArtifactManifest -Manifest $Manifest -Matrix $Matrix
    Assert-EcosystemCiSourceReceipt -Source $Source -Matrix $Matrix
    if ($Public.schemaVersion -ne 1 -or $Public.status -cne 'PUBLIC_RELEASE_VERIFIED' -or
        $Public.milestone -cne $Matrix.milestone -or $Public.publicAligned -isnot [bool] -or !$Public.publicAligned -or
        $Public.anonymousDownloadedBytesVerified -isnot [bool] -or !$Public.anonymousDownloadedBytesVerified -or
        @($Public.verifiedArtifacts).Count -ne 9) { throw 'Public receipt must prove all nine anonymously downloaded, aligned artifacts.' }
    foreach ($pin in $Manifest.artifacts) {
        $matches = @($Public.verifiedArtifacts | Where-Object { $_.project -ceq $pin.project -and $_.minecraft -ceq $pin.minecraft })
        if ($matches.Count -ne 1) { throw 'Public receipt has a missing or duplicate artifact.' }
        $actual = $matches[0]
        if ($actual.result -cne 'PASS' -or $actual.projectId -cne $pin.projectId -or $actual.versionId -cne $pin.versionId -or
            $actual.version -cne $pin.version -or $actual.filename -cne $pin.filename) { throw 'Public receipt identity differs from the reviewed artifact pin.' }
        Assert-EcosystemArtifactIdentity -Artifact $pin -Identity $actual
        Assert-EcosystemCdnUri -Url $actual.url -Artifact $pin
    }
    $links = [Collections.Generic.List[object]]::new()
    foreach ($target in $Matrix.targets) {
        $row = @($Source.targets | Where-Object minecraft -CEQ $target.minecraft)[0]
        foreach ($project in @('automobility','momentum')) {
            $publicArtifact = @($Public.verifiedArtifacts | Where-Object { $_.project -ceq $project -and $_.minecraft -ceq $target.minecraft })[0]
            $folder = if ($project -ceq 'automobility') { 'fabric' } else { 'momentum' }
            $path = "$folder/build/libs/$($publicArtifact.filename)"
            $built = @($row.artifacts | Where-Object path -CEQ $path)
            if ($built.Count -ne 1) { throw 'Source/public filename or artifact path mismatch.' }
            Assert-EcosystemArtifactIdentity -Artifact $publicArtifact -Identity $built[0]
            $links.Add([pscustomobject]@{project=$project; minecraft=$target.minecraft; sourceCommit=$row.commit;
                sourceArtifact=$path; publicVersionId=$publicArtifact.versionId; filename=$publicArtifact.filename;
                size=$built[0].size; sha256=$built[0].sha256; sha512=$built[0].sha512; exactBytesMatch=$true})
        }
    }
    return $links.ToArray()
}

Export-ModuleMember -Function Assert-EcosystemCiSourceReceipt, Get-EcosystemCiArtifactLinks
