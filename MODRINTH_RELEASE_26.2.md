# Modrinth Release - Automobility 0.5.0-unofficial.34 for 26.2

> **Target-scoped release record:** Use this file only when its filename target matches
> `minecraft_version` in `gradle.properties` and the default QA gate passes. Other target
> files in this branch are retained only as historical snapshots.

**Status: PUBLISHED BETA ON 2026-09-19; ANONYMOUS API/CDN HASH VERIFICATION PASS.**

Public version: [0.5.0-unofficial.34+26.2](https://modrinth.com/mod/automobility-unofficial-port/version/NwM5Z0cU).

## Project

- Project: `https://modrinth.com/mod/automobility-unofficial-port`
- Environment: `Client and server, required on both`
- License: MIT
- Source: `https://git.offchunk.com/Offchunk/automobility-unofficial-port`
- Issues: `https://git.offchunk.com/Offchunk/automobility-unofficial-port/issues`

## Version Form

- Version type: `beta`
- Version number: `0.5.0-unofficial.34+26.2`
- Version subtitle: `for Minecraft 26.2`
- Loaders: `Fabric`
- Game versions: `26.2`
- Environment: `Client and server, required on both`

## Dependencies

- Fabric API: required
- Controlify (Controller support): optional
- Momentum for Automobility: Unofficial Port: optional

The loader minimum remains in JAR metadata; do not add it as a project dependency.
Pumps & Pistons/Roadworks candidates pinned to `.33` require separately verified
dependency updates before combining them with `.34`.

## Upload File

- Local JAR: `dist\release-candidate-0.5.0-unofficial.34\automobility-0.5.0-unofficial.34+26.2-fabric.jar`
- Size: `1399097` bytes
- SHA-256: `87DC331C2F3216381A7EB588BA2259420762603650CE14A60458704244B87D33`
- SHA-512: `C99F9B68C42E7EE04B4671C606520A1E0E7CB22FAC74FF43C54145B84BD651437C76A089705F249EC40318B35FE90A3466654E3BCD1261504E09559670723D7C`

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

- Offchunk final artifact, 2026-09-19: clean no-cache default gate **PASS**, 107 tests,
  exact staged size/SHA-256/SHA-512 and licenses/metadata verified. Comparing all 761
  uncompressed JAR entries against the 2026-09-12 candidate found only
  `fabric.mod.json` changed (source/issues URLs); no test fixture is in production.
- Fresh isolated 26.2 server with the final staged JAR: **PASS checks=22**, normal
  save/stop and exit 0. Log `animal-boarding-offchunk-20260919.log` under the documented
  QA directory, SHA-256 `A6D50587815773CEC560EAE81698FA09DFD8E60A8EE07FA80B5E93E0AA98CCF3`.
- Original pre-Offchunk artifacts remain in `dist/archive-20260912-pre-offchunk`;
  the following 2026-09-12 results describe those historical bytes, not the new hashes.
- Base: `.33` at `dea66f3b7b36a23ce3358dfdd483814bdffd6f41`.
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
  matched the artifact above. Receipt: `dist/publication-NwM5Z0cU.json`.
- Matching source: Offchunk branch `fix/automobility-34-no-animal-passengers-26.2`.
- Download the public file and compare size, SHA-256 and SHA-512.
- Verify anonymous Modrinth metadata, CDN and Maven availability before dependants.
