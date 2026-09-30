# One release policy for three Minecraft targets

`tools/qa/ecosystem-matrix.json` is the desired release manifest, not a success
receipt. Keep its identical copies and all files listed in
`tools/qa/ecosystem-sources.json` synchronized across the three active lanes.
Source validation, public verification and human acceptance are separate gates.

| Minecraft | Automobility | Momentum | RIA compatibility baseline |
| --- | --- | --- | --- |
| 26.1.2 | .36 | .10 | .6 |
| 26.2 | .36 | .10 | .6 |
| 26.3 | .36 | .11 | .6 |

This milestone was published, including the 26.3 core .36 and Momentum .11 on
2026-09-26. Same functionality is the goal; do not republish unchanged binaries
merely to equalize numbers. Momentum .11+26.3 contains the modern Controlify code
already present in older-target .10 builds. The accepted Momentum core minimum
remains .35 on 26.1.2/26.2 and .36 on 26.3; a minimum dependency is not the current
core release number. RIA .8 seat-key candidates for older targets remain a
separate player-acceptance milestone; 26.3 .6 already uses G.

## Reviewed source lanes and public identities

The only source remote is
`https://git.offchunk.com/Offchunk/automobility-unofficial-port.git`.
Each run resolves all three branches to exact commits before validation/builds,
then creates separate detached clones without changing developer checkouts.

| Minecraft | Source ref |
| --- | --- |
| 26.1.2 | `refs/heads/release/rosenvik-auto36-26.1.2` |
| 26.2 | `refs/heads/release/rosenvik-auto36-26.2` |
| 26.3 | `refs/heads/port/26.3` |

The nine reviewed public version IDs are pinned in
`tools/qa/ecosystem-public-artifacts.json`, together with project identity,
version/type, game-version list, filename, byte length, SHA-256 and SHA-512.
These are reviewed expectations, not a claim that a new verification has run.

| Minecraft | Automobility .36 | Momentum .10 / .11 | RIA .6 |
| --- | --- | --- | --- |
| 26.1.2 | `8qn3OqtD` | `p5PDrnXt` (.10) | `ONmNDSAc` |
| 26.2 | `x3KzLznK` | `ls11xw68` (.10) | `baq2kYJI` |
| 26.3 | `yPZ9P5d8` | `uIpEAR2D` (.11) | `8hM0nOi2` |

RIA is checked as an external public binary. This repository builds Automobility
and Momentum only; passing the matrix never means RIA was compiled here.

## Required checks and evidence

Use PowerShell 7.2 or newer and JDK 25 for builds. Windows PowerShell 5.1 is not
supported. Every evidence output directory must be fresh; source/CI evidence
must also be outside the controller checkout. Existing evidence is not replaced.

```powershell
./tools/qa/Test-Automobility.ps1 -Rebuild
./tools/qa/Test-EcosystemParity.ps1

# Read-only source policy validation: no Gradle build or publication claim.
$sourceEvidence = Join-Path ([IO.Path]::GetTempPath()) ('auto-sources-' + [guid]::NewGuid())
./tools/qa/Test-EcosystemSources.ps1 -OutputDirectory $sourceEvidence

# Full source build plus nine-artifact public verification, equivalent to CI.
$env:CI = 'true'
$ciEvidence = Join-Path ([IO.Path]::GetTempPath()) ('auto-ci-' + [guid]::NewGuid())
./tools/qa/Invoke-EcosystemCi.ps1 -OutputDirectory $ciEvidence -JavaHome '<JDK25 directory>'

# Public finish gate alone; never a substitute for the source/runtime gates.
$publicEvidence = Join-Path ([IO.Path]::GetTempPath()) ('auto-public-' + [guid]::NewGuid())
./tools/qa/Test-EcosystemPublicRelease.ps1 -OutputDirectory $publicEvidence
```

The source gate compares the exact committed Git blob identities of every shared
policy file, avoiding Windows/Linux checkout line-ending differences. It checks
each lane's Gradle versions and reviewed Momentum minimum, plus clean checkout
and pinned HEAD before/after the gates. `-Build` additionally runs each lane's
`Test-Automobility.ps1 -CiBuildVerification -Rebuild` sequentially. CI build mode
still verifies production JAR size, SHA-256 and SHA-512 against committed release
records; it does not require ignored local staging JARs. The default local gate
still requires those staging JARs before uploading.

The public gate anonymously checks the latest listed Fabric versions and required
core dependencies, rejecting stale/cross-target exact pins. It then downloads all
nine pinned files from their exact HTTPS Modrinth CDN addresses and verifies byte
length and both hashes. An old expected release cannot hide a newer unreviewed
release. Redirects, metadata/hash mismatches, missing targets and API failures
fail closed. Never automatically replace expected hashes with current CDN bytes.

| Evidence | Meaning |
| --- | --- |
| `source-result.json`: `SOURCE_VALIDATION_ONLY` | Three source/policy lanes checked; no builds claimed |
| `source-result.json`: `SOURCE_BUILDS_PASSED` | All three pinned lanes built; includes child exits and built artifact hashes |
| `public-result.json`: `PUBLIC_RELEASE_VERIFIED` | All nine pinned public artifacts passed metadata and fresh downloaded-byte checks |
| `ci-result.json`: `SOURCE_AND_PUBLIC_GATES_PASSED` | Both complete gate receipts passed; no upload or new gameplay acceptance |
| Publisher receipt: `CORE_ONLY_NOT_COMPLETE` | Only this core release was checked/published; addons and full ecosystem remain outside that receipt |

The public finish gate and coordinator have no pending-target waiver. A partial
or failed run must not be reported as ecosystem completion. Source-only fixture
testing cannot execute arbitrary local repository build scripts.

## Future candidates and publication order

A new candidate cannot pass a post-publication gate before it exists publicly.
Keep these phases distinct rather than weakening the finish gate:

1. Review new versions and all affected lanes, update shared source policy and
   exact release records, and commit/push the intended source snapshots. Run the
   source gate with `-Build` in an explicit CI environment. During preparation,
   source checks can pass while the public finish gate is correctly incomplete.
2. Complete the relevant runtime and human acceptance on the exact candidate
   hashes. CI is not permission to publish.
3. With separate authorization, publish the qualified core first, then affected
   addons and their required core dependencies. Preserve unchanged accepted
   binaries. The current publisher recipe remains scoped to the reviewed .36
   release and must not be assumed to support a future release unchanged.
4. Review the resulting version IDs against the already qualified artifact
   bytes, update the nine public pins and shared policy identically in every
   source lane, then run the full public finish gate and combined coordinator.
   Missing lanes, stale dependencies or partial publication remain failures.

Only the legacy publication *preflight* may use `-PendingMinecraft` for its own
checkout during an authorized sequential upload; other targets must pass.
`publicAligned` stays false for unresolved rows. Neither that allowance nor
`-ReportOnly` constitutes completion or authorization, and neither is passed to
the public finish gate. The publisher does not publish Momentum/RIA automatically.

## Forgejo workflow boundary

`.forgejo/workflows/ecosystem.yml` specifies trusted owner pushes to the three
source lanes (and the `main` bootstrap) plus manual dispatch. It has no pull-request
or fork-code trigger, no schedule, no release/upload step and no Modrinth token.
It snapshots the reviewed controller from `port/26.3`, runs all source builds and
public verification, and requests evidence upload even after failure. Source
commit identities are recorded independently of the triggering branch.

Workflow configuration is not proof that a runner is installed, isolated, online,
or has completed an actual job. Infrastructure activation and a real end-to-end
run must be verified separately; this document makes no deployment-success claim.

These checks do not establish binary/API compatibility under every modpack,
runtime gameplay, installation success, multiplayer, physical-controller support,
measured performance or new player acceptance. Record those separately and retain
NOT PROVEN for unrun coverage. P&P, its adapter, Autoradio and Roadworks remain
outside this milestone; no automatic future API compatibility is promised.
