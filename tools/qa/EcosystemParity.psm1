Set-StrictMode -Version Latest

function Assert-EcosystemMatrix {
    param([Parameter(Mandatory)] $Matrix)
    if ($Matrix.schemaVersion -ne 1) { throw 'Unsupported ecosystem matrix schema.' }
    $targets = @($Matrix.targets | ForEach-Object minecraft | Sort-Object)
    if (($targets -join ',') -cne '26.1.2,26.2,26.3') {
        throw 'Matrix must account for all three supported Minecraft targets exactly once.'
    }
    # Identity anchors, independent of editable release version expectations.
    $projectIds = @{automobility='B6d5S1wQ'; momentum='pOgpsBUs'; riautomobility='OZ1lM8wy'}
    foreach ($project in @('automobility', 'momentum', 'riautomobility')) {
        if ($Matrix.projects.$project -cne $projectIds.$project) { throw "Unexpected project ID: $project" }
        foreach ($target in $Matrix.targets) {
            if ($target.$project -notmatch '^\d+\.\d+\.\d+-unofficial\.\d+$') {
                throw "Invalid expected version for $project / $($target.minecraft)"
            }
        }
    }
}

function Get-EcosystemParity {
    [CmdletBinding()]
    param([Parameter(Mandatory)] $Matrix, [Parameter(Mandatory)] [hashtable] $Snapshots)
    Assert-EcosystemMatrix $Matrix
    $projectNames = @('automobility', 'momentum', 'riautomobility')
    $versionIndex = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    $projectById = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach ($project in $projectNames) {
        $projectById.Add($Matrix.projects.$project, $project)
        if (!$Snapshots.ContainsKey($project) -or $null -eq $Snapshots[$project]) { throw "Missing API snapshot: $project" }
        foreach ($version in $Snapshots[$project]) {
            if ($null -eq $version -or $version.project_id -cne $Matrix.projects.$project) {
                throw "API snapshot contains an unexpected project: $project"
            }
            if ([string]::IsNullOrWhiteSpace($version.id) -or [string]::IsNullOrWhiteSpace($version.version_number)) {
                throw "API snapshot contains an invalid version identity: $project"
            }
            $published = [DateTimeOffset]::MinValue
            # Newer PowerShell JSON readers materialize ISO dates; older hosts retain strings.
            if ($version.date_published -is [DateTimeOffset]) {
                $published = $version.date_published
            } elseif ($version.date_published -is [DateTime] -and $version.date_published.Kind -ne [DateTimeKind]::Unspecified) {
                $published = [DateTimeOffset]$version.date_published
            } elseif ($version.date_published -is [string] -and
                $version.date_published -match '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,7})?(?:Z|[+-]\d{2}:\d{2})$' -and
                [DateTimeOffset]::TryParse($version.date_published, [Globalization.CultureInfo]::InvariantCulture,
                    [Globalization.DateTimeStyles]::None, [ref]$published)) {
                # Parsed the explicit timezone above.
            } else {
                throw "API snapshot contains an invalid publication timestamp: $($version.id)"
            }
            if ($versionIndex.ContainsKey($version.id)) { throw "Ambiguous API version identity: $($version.id)" }
            $versionIndex.Add($version.id, [pscustomobject]@{version=$version; project=$project; published=$published})
        }
    }
    $rows = [Collections.Generic.List[object]]::new()
    foreach ($target in $Matrix.targets) {
        $selected = @{}
        $ambiguous = @{}
        foreach ($project in $projectNames) {
            $matching = @($Snapshots[$project] | Where-Object {
                $_.status -ceq 'listed' -and $_.loaders -ccontains 'fabric' -and
                $_.game_versions -ccontains $target.minecraft
            } | Sort-Object { $versionIndex[$_.id].published } -Descending)
            $selected[$project] = if ($matching.Count) { $matching[0] } else { $null }
            $ambiguous[$project] = $matching.Count -gt 1 -and
                $versionIndex[$matching[0].id].published -eq $versionIndex[$matching[1].id].published
        }
        foreach ($project in $projectNames) {
            $current = $selected[$project]
            $expected = "$($target.$project)+$($target.minecraft)"
            $issues = [Collections.Generic.List[string]]::new()
            if ($null -eq $current) { $issues.Add('MISSING_PUBLIC_VERSION') }
            else {
                if ($ambiguous[$project]) { $issues.Add('AMBIGUOUS_LATEST_PUBLIC_VERSION') }
                if ($current.version_number -cne $expected) { $issues.Add('VERSION_DIFFERS_FROM_MATRIX') }
                $primary = @($current.files | Where-Object { $_.primary -is [bool] -and $_.primary })
                $size = 0L
                if ($primary.Count -ne 1 -or ![long]::TryParse([string]$primary[0].size, [ref]$size) -or $size -le 0 -or
                    $primary[0].hashes.sha512 -notmatch '^[a-fA-F0-9]{128}$') {
                    $issues.Add('INVALID_PRIMARY_ARTIFACT')
                }
                $coreRequirements = 0
                foreach ($dependency in $current.dependencies) {
                    $dependencyProject = [string]$dependency.project_id
                    $dependencyVersion = [string]$dependency.version_id
                    if ($dependency.dependency_type -cnotin @('required','optional','embedded','incompatible')) {
                        $issues.Add('INVALID_DEPENDENCY_TYPE')
                        continue
                    }
                    if ($dependencyVersion -and $versionIndex.ContainsKey($dependencyVersion)) {
                        $resolvedProject = $versionIndex[$dependencyVersion].version.project_id
                        if ($dependencyProject -and $dependencyProject -cne $resolvedProject) {
                            $issues.Add('DEPENDENCY_PROJECT_VERSION_MISMATCH')
                        }
                        $dependencyProject = $resolvedProject
                    } elseif ($dependencyVersion -and !$dependencyProject) {
                        # Without an identity this may hide a conflicting ecosystem requirement.
                        $issues.Add('UNRESOLVED_VERSION_ONLY_DEPENDENCY')
                    }
                    if (!$dependencyProject) {
                        if (!$dependencyVersion -and $dependency.dependency_type -ceq 'required') {
                            $issues.Add('UNRESOLVED_REQUIRED_DEPENDENCY')
                        }
                        continue
                    }
                    if (!$projectById.ContainsKey($dependencyProject)) { continue }
                    $dependencyName = $projectById[$dependencyProject]
                    $dependencySelected = $selected[$dependencyName]
                    if ($dependencyVersion -and !$versionIndex.ContainsKey($dependencyVersion)) {
                        $issues.Add('UNRESOLVED_ECOSYSTEM_DEPENDENCY')
                    }
                    if ($dependency.dependency_type -ceq 'incompatible' -and $null -ne $dependencySelected -and
                        (!$dependencyVersion -or $dependencyVersion -ceq $dependencySelected.id)) {
                        $issues.Add('INCOMPATIBLE_SELECTED_PROJECT')
                    }
                    if ($dependency.dependency_type -ceq 'required') {
                        if ($dependencyName -ceq 'automobility') { $coreRequirements++ }
                        if ($null -eq $dependencySelected) { $issues.Add('MISSING_REQUIRED_ECOSYSTEM_PROJECT') }
                        if ($dependencyVersion -and ($null -eq $dependencySelected -or $dependencyVersion -cne $dependencySelected.id)) {
                            $issues.Add($(if ($project -cne 'automobility' -and $dependencyName -ceq 'automobility') {
                                'ADDON_PINS_DIFFERENT_CORE'
                            } else { 'REQUIRED_DEPENDENCY_PINS_DIFFERENT_PROJECT_VERSION' }))
                        }
                    }
                }
                if ($project -ne 'automobility') {
                    if ($coreRequirements -ne 1) { $issues.Add('MISSING_OR_AMBIGUOUS_CORE_DEPENDENCY') }
                }
            }
            $rows.Add([pscustomobject]@{
                minecraft = $target.minecraft; project = $project; expected = $expected
                actual = if ($current) { $current.version_number } else { $null }
                versionId = if ($current) { $current.id } else { $null }
                issues = @($issues | Select-Object -Unique); aligned = $issues.Count -eq 0
            })
        }
    }
    return @($rows)
}

Export-ModuleMember -Function Assert-EcosystemMatrix, Get-EcosystemParity
