# Automobility: Unofficial Port

Fabric vehicles for Minecraft26.x, based on [FoundationGames' Automobility](https://github.com/FoundationGames/Automobility).
The current published1.3.0 release includes Momentum driving, cruise control,
the vehicle HUD, garage/tuning, headlights and the MC-5, Offroader 90 and W4.

## Install

Use Java25 and matching Minecraft, Fabric Loader and Fabric API versions.
Download the matching file from [Modrinth](https://modrinth.com/mod/automobility-unofficial-port/versions)
and install on the client and server. Exact loader/API minima are in each
release's loader metadata. Momentum is integrated in1.3.0: do not add a separate
Momentum JAR to that installation. Controlify, Mod Menu, YACL and shaders are
optional. Report the Minecraft version and mod version with issues.

## Stable source and development

| Minecraft | Published release source | Development branch |
|---|---|---|
| 26.1.2 | [1.3.0](https://git.offchunk.com/Offchunk/automobility-unofficial-port/src/tag/automobility-1.3.0+26.1.2-fabric) | `dev/26.1.2` |
| 26.2 | [1.3.0](https://git.offchunk.com/Offchunk/automobility-unofficial-port/src/tag/automobility-1.3.0+26.2-fabric) | `dev/26.2` |
| 26.3 | [1.3.0](https://git.offchunk.com/Offchunk/automobility-unofficial-port/src/tag/automobility-1.3.0+26.3-fabric) | `dev/26.3` |

`main` provides source navigation and the release index. Build a release from
its immutable version/MC tag. Development checkpoints belong on the explicit
`dev/*` branches; tags never move. The prepared dev branches start at1.3.0
with source-workflow additions, without importing other unfinished lanes.

[`releases/source-index.json`](releases/source-index.json) maps all49 public
version records:45 have a source commit/tag mapping and4 historical source
gaps are identified explicitly. This includes20 historical releases of
[standalone Momentum](https://modrinth.com/mod/momentum-for-automobility-unofficial-port/versions)
in this same repository. Existing public source tags remain unchanged.

## Build

Check out a version/MC tag and follow its `release/BUILD.md`. For1.3.0 use
Java25 and the included Gradle wrapper:

```sh
git switch --detach automobility-1.3.0+26.2-fabric
./gradlew :fabric:jar --no-daemon --max-workers=1 -x test
```

Use `gradlew.bat` on Windows. Build repositories/versions and optional SDK
properties are checked into the release tree. The source export preserves
the original narrow release overlays and artifact hash inventory; it was not
rebuilt during source publication and does not promise byte-identical compiler
debug/constant-pool output. Runtime caches, worlds, private configuration,
audio masters and QA outputs are absent from the new release snapshots.

## Keeping sources current

See [the shared source workflow](docs/SOURCE-WORKFLOW.md). In the curated
checkout, an explicit completed source commit on `dev/*` triggers the installed
hook to push that branch without force and verify the remote hash. It never
stages files, builds, starts Minecraft, publishes Modrinth, or moves tags.
Release publication records source commit/tag and exact artifact hashes first.
The old build workflow now runs only manually or for pull requests; the
trusted ecosystem runner is manual-only. Source pushes do not run those suites.

## Licence and support

Automobility code retains MIT and upstream notices; integrated Momentum keeps
milkucha's MIT notices. Owned new assets use CC BY4.0 under the release's asset
notices; inherited/third-party content retains its original terms. See each
release tree's `LICENSE`, `NOTICE.md`, asset licences and `THIRD_PARTY_LICENSES`.

[Issues](https://git.offchunk.com/Offchunk/automobility-unofficial-port/issues)
· [Modrinth](https://modrinth.com/mod/automobility-unofficial-port)
· [Original Automobility](https://github.com/FoundationGames/Automobility)
