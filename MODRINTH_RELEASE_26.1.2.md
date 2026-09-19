# Modrinth Release - Automobility 0.5.0-unofficial.34 for 26.1.2

> **Target-scoped release record:** Use this file only when its filename target matches
> `minecraft_version` in `gradle.properties` and the default QA gate passes. Other target
> files in this branch are retained only as historical snapshots.

**Status: PUBLISHED BETA ON 2026-09-19; ANONYMOUS API/CDN HASH VERIFICATION PASS.**

Public version: [0.5.0-unofficial.34+26.1.2](https://modrinth.com/mod/automobility-unofficial-port/version/X8JzLZr8).

## Project

- Project: `https://modrinth.com/mod/automobility-unofficial-port`
- Environment: `Client and server, required on both`
- License: MIT
- Source: `https://git.offchunk.com/Offchunk/automobility-unofficial-port`
- Issues: `https://git.offchunk.com/Offchunk/automobility-unofficial-port/issues`

## Version Form

- Version type: `beta`
- Version number: `0.5.0-unofficial.34+26.1.2`
- Version subtitle: `for Minecraft 26.1.x`
- Loaders: `Fabric`
- Game versions: `26.1`, `26.1.1`, `26.1.2`
- Environment: `Client and server, required on both`

## Dependencies

- Fabric API: required
- Controlify (Controller support): optional
- Momentum for Automobility: Unofficial Port: optional

The loader minimum remains in JAR metadata; do not add it as a project dependency.
Pumps & Pistons/Roadworks candidates pinned to `.33` require separately verified
dependency updates before combining them with `.34`.

## Upload File

- Local JAR: `dist\release-candidate-0.5.0-unofficial.34\automobility-0.5.0-unofficial.34+26.1.2-fabric.jar`
- Size: `1399123` bytes
- SHA-256: `7301339B4A1C350D03C3247E69840FDC82114ED18ECA44362F7FC7F3BA452BBE`
- SHA-512: `F1C230E781398D82E5B65352FD023B1EFFAB0620D87273DF88B5EE9544AEC5915592A7135A8805137393A6C253A4CAED0464FCD5652BE614A7EE069CC730A964`

## Version Changelog

```markdown
Animals no longer occupy automobile seats.

- Prevented animals from boarding automatically or through normal mounting, including
  rear passenger seats, aquatic animals and bats.
- Animals already seated in an older save dismount safely on the next server tick.
- Players and non-animal mobs keep their existing boarding behavior.

Also includes the previously unpublished security, data-safety and position-sync repairs:

- Hardened vehicle input handling and attachment requests; clients cannot supply
  arbitrary physics, collision-damage or position values through the legacy packet.
- Added world-border and vanilla spawn-protection checks for harvester/plow changes.
- Preserved missing-addon component IDs and attachment inventory data across saves.
- Bounded datapack physics values, fixed raw automobile placement and stale menus.
- Repaired automobile/attachment rendering after server position corrections.
- Updated source and issue links to git.offchunk.com.

Beta: automated unit and dedicated-server animal-boarding tests are covered.
Fresh manual driving, two-client observation and full modpack acceptance are not
yet confirmed for this build. Back up worlds and match addon dependency ranges;
older Pumps & Pistons/Roadworks candidates pinned to .33 require an addon update.
```

## Verification Record

- Base: `.33` at `5d91f9c22c615f723e9180ea59f8470ed977dbb6`.
- Offchunk rebuild and final default `Test-Automobility.ps1 -Rebuild`: **PASS** on
  2026-09-19 with Temurin Java 25.0.3+9; 107 tests passed, 0 failed, 0 skipped.
  Exact canonical staged files match the clean builds by size, SHA-256 and SHA-512.
- Both production Automobility archives contain 761 entries; comparing decompressed
  bytes to the 2026-09-12 artifact found only `fabric.mod.json` changed. Its only
  changes are the source/issues URL replacements. No QA fixture is packaged.
  Original Automobility and Momentum artifacts were SHA-512-verified before archival
  in `dist\archive-20260912-pre-offchunk`. The rebuilt Momentum file is local QA only
  and must not replace a published `.10` release.
- Final Offchunk artifact dedicated-server fixture: **PASS**, 22 scenario groups on
  2026-09-19 in a fresh isolated world; clean save/stop and exit 0. Installed JAR
  matches the final candidate. Evidence: `animal-boarding-offchunk-20260919-fresh.log`
  under `C:\Users\david\Automobility-34-Animal-Boarding-QA\26.1.2`, SHA-256
  `E26AE1A4A9B050CD86664593A029A2BC55109F5EE467EEDA96C27BC007933797`.
  A preceding reused-world run failed the villager pickup check; its log is retained
  and is not a pass. See fixture documentation for the fresh-world prerequisite.
- New `.34` clean build, unit suites and JAR integrity: **PASS** on 2026-09-12;
  107 tests passed, 0 failed and 0 skipped (Fabric 73; Momentum 34).
- Exact staged-JAR and release-record hash gate: **PASS** on 2026-09-12.
  A clean no-cache rebuild reproduced size, SHA-256 and SHA-512 for both modules.
- Dedicated-server animal boarding, existing animal dismount and non-animal
  passenger control: **PASS**, 22 scenario groups on 2026-09-12, including 17
  animal types and NBT save/load with mixed passengers. Server stopped with exit 0.
  See `tools/qa/ANIMAL_BOARDING_QA.md` for coverage and reproduction.
- Prior `.33` runtime results apply only to its original hashes, retained in the
  `.33` worktree and Git history.
- Held-key/manual driving, two-client observer, horizontal/dimension transfer,
  full persistence/missing-addon/spawn-protection and ecosystem acceptance:
  **NOT PROVEN for .34**.
- Owner explicitly requested Modrinth publication on 2026-09-19. Manual acceptance
  is not implied by that request; the unproven coverage above remains disclosed.
- Anonymous Offchunk source/issues and final artifact checks must pass before upload.

## Before Upload

- Run `tools\qa\Test-Automobility.ps1 -Rebuild` against the final staged artifacts.
- Keep limited beta coverage explicit; do not claim full ecosystem/player acceptance.
- Confirm canonical source and issue URLs are anonymously reachable; preserve the
  upstream attribution and MIT notices.
- Upload only after approval, using the exact JAR and metadata in this record.

## After Upload

- 2026-09-19: anonymous API metadata and downloaded CDN size/SHA-256/SHA-512 all
  matched the artifact above. Receipt: `dist/publication-X8JzLZr8.json`.
- Matching source: Offchunk branch `fix/automobility-34-no-animal-passengers-26.1.2`.
- Download the public file and compare size, SHA-256 and SHA-512.
- Verify anonymous Modrinth metadata, CDN and Maven availability before dependants.
