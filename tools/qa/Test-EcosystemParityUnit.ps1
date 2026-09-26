#requires -Version 7.2
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'EcosystemParity.psm1') -Force
$matrix = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'ecosystem-matrix.json') -Raw | ConvertFrom-Json
$script:passed = 0
function Check([bool] $Condition, [string] $Name) {
    if (!$Condition) { throw "FAIL: $Name" }
    $script:passed++
    Write-Host "PASS: $Name"
}
function New-Snapshots {
    $snapshots = @{}
    foreach ($project in @('automobility','momentum','riautomobility')) {
        $snapshots[$project] = @($matrix.targets | ForEach-Object {
            $target = $_
            [pscustomobject]@{
                id = "$project-$($target.minecraft)"; project_id = $matrix.projects.$project
                status = 'listed'; loaders = @('fabric'); game_versions = @($target.minecraft)
                version_number = "$($target.$project)+$($target.minecraft)"
                date_published = '2026-09-26T10:00:00Z'
                files = @([pscustomobject]@{primary=$true; size=100; hashes=@{sha512=('a'*128)}})
                dependencies = @(if ($project -ne 'automobility') {
                    [pscustomobject]@{project_id=$matrix.projects.automobility; dependency_type='required'; version_id="automobility-$($target.minecraft)"}
                })
            }
        })
    }
    return $snapshots
}
function Issues($Snapshots) { return @((Get-EcosystemParity $matrix $Snapshots) | Where-Object { !$_.aligned }) }
function Has-Issue($Snapshots, [string] $Issue) {
    return @(@(Issues $Snapshots) | ForEach-Object { $_.issues }) -contains $Issue
}
function Check-Throws([scriptblock] $Action, [string] $Name) {
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    Check $rejected $Name
}
function Copy-Value($Value) { return ($Value | ConvertTo-Json -Depth 20) | ConvertFrom-Json }
function Dependency($Project, $Version, $Type='required') {
    return [pscustomobject]@{project_id=$Project; version_id=$Version; dependency_type=$Type}
}
$s = New-Snapshots
Check (@(Issues $s).Count -eq 0) 'All targets and exact dependency pins aligned'
$s.riautomobility[0].dependencies[0].version_id = $null
Check (@(Issues $s).Count -eq 0) 'Project-only required core dependency accepted'
$s = New-Snapshots
$s.automobility[2].version_number = '0.5.0-unofficial.34+26.3'
Check (((Issues $s).issues -contains 'VERSION_DIFFERS_FROM_MATRIX')) 'Missing26.3 core parity rejected'
$s = New-Snapshots
$s.momentum[2].version_number = '0.1.11-unofficial.10+26.3'
Check (((Issues $s).issues -contains 'VERSION_DIFFERS_FROM_MATRIX')) 'Old26.3 Momentum rejected'
$s = New-Snapshots
$s.riautomobility[2].dependencies[0].version_id = 'WgNM3r8h'
Check (((Issues $s).issues -contains 'ADDON_PINS_DIFFERENT_CORE')) 'Stale RIA exact-core pin rejected'
$s = New-Snapshots
$s.momentum[0].dependencies[0].version_id = 'automobility-26.2'
Check (((Issues $s).issues -contains 'ADDON_PINS_DIFFERENT_CORE')) 'Cross-Minecraft pin rejected'
$s = New-Snapshots
$s.momentum[1].dependencies = @()
Check (((Issues $s).issues -contains 'MISSING_OR_AMBIGUOUS_CORE_DEPENDENCY')) 'Missing required core rejected'
$s = New-Snapshots
$s.momentum[1].dependencies[0].dependency_type = 'optional'
Check (((Issues $s).issues -contains 'MISSING_OR_AMBIGUOUS_CORE_DEPENDENCY')) 'Optional-only core rejected'
$s = New-Snapshots
$s.momentum[1].dependencies += $s.momentum[1].dependencies[0]
Check (((Issues $s).issues -contains 'MISSING_OR_AMBIGUOUS_CORE_DEPENDENCY')) 'Duplicate core dependency rejected'
$s = New-Snapshots
$s.automobility[2].status = 'unlisted'
Check (((Issues $s).issues -contains 'MISSING_PUBLIC_VERSION')) 'Unlisted does not count as public'
$s = New-Snapshots
$s.automobility[2].loaders = @('neoforge')
Check (((Issues $s).issues -contains 'MISSING_PUBLIC_VERSION')) 'Wrong loader rejected'
$s = New-Snapshots
$s.riautomobility[2].project_id = 'unrelated'
Check-Throws { Get-EcosystemParity $matrix $s } 'Wrong project in API snapshot fails closed'
$s = New-Snapshots
$s.riautomobility[2].files[0].hashes.sha512 = 'invalid'
Check (((Issues $s).issues -contains 'INVALID_PRIMARY_ARTIFACT')) 'Malformed artifact hash rejected'
$s = New-Snapshots
$s.riautomobility[2].files = @()
Check (((Issues $s).issues -contains 'INVALID_PRIMARY_ARTIFACT')) 'Missing primary file rejected'
$s = New-Snapshots
$s.automobility[2].version_number = '0.5.0-unofficial.37+26.3'
Check (((Issues $s).issues -contains 'VERSION_DIFFERS_FROM_MATRIX')) 'Unexpected newer release forces matrix review'
$s = New-Snapshots
$s.Remove('momentum')
$rejected = $false
try { Get-EcosystemParity $matrix $s | Out-Null } catch { $rejected = $true }
Check $rejected 'Absent API snapshot fails closed'
$badMatrix = ($matrix | ConvertTo-Json -Depth 12) | ConvertFrom-Json
$badMatrix.targets = @($badMatrix.targets | Select-Object -First 2)
$rejected = $false
try { Assert-EcosystemMatrix $badMatrix } catch { $rejected = $true }
Check $rejected 'Dropping26.3 from policy fails closed'

$s = New-Snapshots
$s.riautomobility[2].dependencies += (Dependency $matrix.projects.automobility 'automobility-26.3' 'incompatible')
Check (Has-Issue $s 'INCOMPATIBLE_SELECTED_PROJECT') 'Contradictory required and incompatible selected core rejected'
$s = New-Snapshots
$s.riautomobility[2].dependencies += (Dependency $matrix.projects.automobility $null 'incompatible')
Check (Has-Issue $s 'INCOMPATIBLE_SELECTED_PROJECT') 'Project-wide core incompatibility rejected'
$s = New-Snapshots
$s.riautomobility[2].dependencies += (Dependency $null 'automobility-26.3' 'incompatible')
Check (Has-Issue $s 'INCOMPATIBLE_SELECTED_PROJECT') 'Version-only selected core incompatibility resolved and rejected'
$s = New-Snapshots
$s.automobility[2].dependencies += (Dependency $matrix.projects.riautomobility 'riautomobility-26.3' 'incompatible')
Check (Has-Issue $s 'INCOMPATIBLE_SELECTED_PROJECT') 'Reverse core-to-addon incompatibility rejected'
$s = New-Snapshots
$s.momentum[2].dependencies += (Dependency $matrix.projects.riautomobility $null 'incompatible')
Check (Has-Issue $s 'INCOMPATIBLE_SELECTED_PROJECT') 'Addon-to-addon incompatibility rejected'
$s = New-Snapshots
$s.riautomobility[2].dependencies += (Dependency $matrix.projects.automobility 'automobility-26.2' 'incompatible')
Check (@(Issues $s).Count -eq 0) 'Incompatibility with an unselected known version is harmless'
$s = New-Snapshots
$s.riautomobility[2].dependencies[0].project_id = $null
Check (@(Issues $s).Count -eq 0) 'Version-only required selected core is resolved'
$s = New-Snapshots
$s.riautomobility[2].dependencies[0].project_id = $null
$s.riautomobility[2].dependencies[0].version_id = 'automobility-26.2'
Check (Has-Issue $s 'ADDON_PINS_DIFFERENT_CORE') 'Version-only cross-target core pin rejected'
$s = New-Snapshots
$s.riautomobility[2].dependencies += (Dependency $null 'automobility-26.2')
Check (Has-Issue $s 'MISSING_OR_AMBIGUOUS_CORE_DEPENDENCY') 'Additional hidden version-only core dependency rejected'
Check (Has-Issue $s 'ADDON_PINS_DIFFERENT_CORE') 'Additional hidden version-only pin cannot evade selected-core check'
$s = New-Snapshots
$s.riautomobility[2].dependencies[0].project_id = $matrix.projects.momentum
Check (Has-Issue $s 'DEPENDENCY_PROJECT_VERSION_MISMATCH') 'Dependency project cannot disagree with resolved version identity'
$s = New-Snapshots
$s.riautomobility[2].dependencies += (Dependency $null 'unknown-version')
Check (Has-Issue $s 'UNRESOLVED_VERSION_ONLY_DEPENDENCY') 'Unresolved version-only dependency fails closed'
$s = New-Snapshots
$s.riautomobility[2].dependencies += (Dependency $null $null)
Check (Has-Issue $s 'UNRESOLVED_REQUIRED_DEPENDENCY') 'Identity-free required dependency fails closed'
$s = New-Snapshots
$s.riautomobility[2].dependencies += (Dependency $matrix.projects.automobility 'unknown-version' 'incompatible')
Check (Has-Issue $s 'UNRESOLVED_ECOSYSTEM_DEPENDENCY') 'Unknown ecosystem version incompatibility cannot be silently waived'
$s = New-Snapshots
$s.riautomobility[2].dependencies += (Dependency 'external-project' 'external-version')
Check (@(Issues $s).Count -eq 0) 'Explicit external dependencies remain outside this matrix scope'
$s = New-Snapshots
$s.momentum[2].dependencies += (Dependency $matrix.projects.riautomobility 'riautomobility-26.2')
Check (Has-Issue $s 'REQUIRED_DEPENDENCY_PINS_DIFFERENT_PROJECT_VERSION') 'Wrong selected addon pin rejected symmetrically'
$s = New-Snapshots
$s.momentum[2].dependencies[0].dependency_type = 'requred'
Check (Has-Issue $s 'INVALID_DEPENDENCY_TYPE') 'Unknown dependency kind fails closed'
$s = New-Snapshots
$s.automobility = @($s.automobility | Where-Object { $_.game_versions -notcontains '26.3' })
Check (Has-Issue $s 'MISSING_REQUIRED_ECOSYSTEM_PROJECT') 'Missing selected core also invalidates dependent addon rows'

$s = New-Snapshots
$newer = Copy-Value $s.automobility[2]
$newer.id = 'newer-core'
$newer.version_number = '0.5.0-unofficial.37+26.3'
$newer.date_published = '2026-09-26T11:00:00Z'
$s.automobility += $newer
Check (Has-Issue $s 'VERSION_DIFFERS_FROM_MATRIX') 'Latest public wrong version fails despite older expected version'
Check ((@(Get-EcosystemParity $matrix $s | Where-Object { $_.minecraft -eq '26.3' -and $_.project -eq 'automobility' }))[0].versionId -eq 'newer-core') 'Published timestamp determines current version'
$newer.status = 'unlisted'
Check (@(Issues $s).Count -eq 0) 'A newer unlisted candidate does not replace the listed version'
$newer.status = 'listed'
$newer.loaders = @('neoforge')
Check (@(Issues $s).Count -eq 0) 'A newer wrong-loader version does not replace Fabric'
$newer.loaders = @('fabric')
$newer.game_versions = @('27.1')
Check (@(Issues $s).Count -eq 0) 'A newer other-game version does not replace a supported target'
$newer.game_versions = @('26.3')
$newer.date_published = '2026-09-26T12:00:00+02:00'
Check (Has-Issue $s 'AMBIGUOUS_LATEST_PUBLIC_VERSION') 'Equal instants with different UTC offsets are an ambiguous latest release'
$s = New-Snapshots
$s.automobility[2].date_published = 'not-a-date'
Check-Throws { Get-EcosystemParity $matrix $s } 'Malformed publication timestamp fails closed'
$s.automobility[2].date_published = '2026-09-26'
Check-Throws { Get-EcosystemParity $matrix $s } 'Timezone-free date cannot establish release ordering'
$s = New-Snapshots
$s.automobility[2].date_published = [DateTime]::new(2026, 9, 26, 10, 0, 0, [DateTimeKind]::Utc)
Check (@(Issues $s).Count -eq 0) 'PowerShell JSON-materialized UTC date is accepted'
$s.automobility[2].date_published = [DateTimeOffset]::Parse('2026-09-26T12:00:00+02:00')
Check (@(Issues $s).Count -eq 0) 'Explicit DateTimeOffset publication instant is accepted'
$s.automobility[2].date_published = [DateTime]::new(2026, 9, 26, 10, 0, 0, [DateTimeKind]::Unspecified)
Check-Throws { Get-EcosystemParity $matrix $s } 'Timezone-free materialized DateTime fails closed'
$s = New-Snapshots
$s.riautomobility[2].id = $s.momentum[2].id
Check-Throws { Get-EcosystemParity $matrix $s } 'Duplicate version identity across projects fails closed'
$s = New-Snapshots
$s.riautomobility[2].id = ''
Check-Throws { Get-EcosystemParity $matrix $s } 'Empty version identity fails closed'
$s = New-Snapshots
$s.riautomobility = @()
Check (Has-Issue $s 'MISSING_PUBLIC_VERSION') 'An empty valid snapshot is a public gap'
$s.riautomobility = $null
Check-Throws { Get-EcosystemParity $matrix $s } 'A null snapshot is not treated as valid API data'

foreach ($badSize in @(0, -1, 'not-a-number', '1.5')) {
    $s = New-Snapshots
    $s.riautomobility[2].files[0].size = $badSize
    Check (Has-Issue $s 'INVALID_PRIMARY_ARTIFACT') "Invalid primary file size rejected: $badSize"
}
$s = New-Snapshots
$s.riautomobility[2].files += (Copy-Value $s.riautomobility[2].files[0])
Check (Has-Issue $s 'INVALID_PRIMARY_ARTIFACT') 'Ambiguous primary artifacts rejected'
$s = New-Snapshots
$s.riautomobility[2].files[0].primary = 'false'
Check (Has-Issue $s 'INVALID_PRIMARY_ARTIFACT') 'Truthy string cannot masquerade as a primary boolean'
$badMatrix = Copy-Value $matrix
$badMatrix.projects.momentum = $matrix.projects.riautomobility
Check-Throws { Assert-EcosystemMatrix $badMatrix } 'Project identity swap fails closed before API access'
$badMatrix = Copy-Value $matrix
$badMatrix.targets += (Copy-Value $badMatrix.targets[2])
Check-Throws { Assert-EcosystemMatrix $badMatrix } 'Duplicated target fails closed'
$badMatrix = Copy-Value $matrix
$badMatrix.targets[2].momentum = 'latest'
Check-Throws { Assert-EcosystemMatrix $badMatrix } 'Non-exact version expectation fails closed'
Write-Host "Ecosystem parity tests: $script:passed passed."
